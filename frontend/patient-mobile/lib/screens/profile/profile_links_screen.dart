import 'dart:async';
import 'dart:convert';
import 'dart:math';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:uuid/uuid.dart';
import '../../providers/auth_provider.dart';
import '../../providers/patient_provider.dart';
import '../../services/profile_link_service.dart';
import '../../utils/api_error_message.dart';

class ProfileLinksScreen extends ConsumerStatefulWidget {
  const ProfileLinksScreen({super.key});
  @override
  ConsumerState<ProfileLinksScreen> createState() => _ProfileLinksScreenState();
}

class _ProfileLinksScreenState extends ConsumerState<ProfileLinksScreen>
    with WidgetsBindingObserver {
  List<ProfileLink> _links = [];
  String? _token, _requestId, _error, _message;
  DateTime? _expires;
  bool _busy = false, _foreground = true;
  int _generation = 0;
  Timer? _refresh;
  String? get _owner =>
      ref.read(authProvider).status == AuthStatus.authenticated
      ? ref.read(authProvider).userId
      : null;
  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    Future.microtask(_load);
    _refresh = Timer.periodic(const Duration(seconds: 10), (_) {
      if (_foreground && !_busy) unawaited(_load());
    });
  }

  @override
  void dispose() {
    ++_generation;
    _refresh?.cancel();
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    _foreground = state == AppLifecycleState.resumed;
    if (_foreground) unawaited(_load());
  }

  Future<void> _load() async {
    final owner = _owner;
    if (owner == null || _busy) return;
    final generation = ++_generation;
    try {
      final links = await ref.read(profileLinkServiceProvider).mine();
      if (mounted && generation == _generation && owner == _owner) {
        setState(() {
          _links = links;
          _error = null;
        });
      }
    } catch (error) {
      if (mounted && generation == _generation && owner == _owner) {
        setState(() {
          _error = ApiErrorMessage.from(error);
        });
      }
    }
  }

  Future<void> _run(Future<void> Function() action) async {
    if (_busy || _owner == null) return;
    final owner = _owner;
    ++_generation;
    setState(() {
      _busy = true;
      _error = null;
      _message = null;
    });
    try {
      await action();
    } catch (error) {
      if (mounted && owner == _owner) {
        setState(() {
          _error = ApiErrorMessage.from(error);
        });
      }
    } finally {
      if (mounted && owner == _owner) {
        setState(() {
          _busy = false;
        });
        await _load();
      }
    }
  }

  Future<bool> _confirm(String title, String content) async =>
      await showDialog<bool>(
        context: context,
        builder: (context) => AlertDialog(
          title: Text(title),
          content: Text(content),
          actions: [
            TextButton(
              onPressed: () => Navigator.pop(context, false),
              child: const Text('Hủy'),
            ),
            FilledButton(
              onPressed: () => Navigator.pop(context, true),
              child: const Text('Xác nhận'),
            ),
          ],
        ),
      ) ??
      false;
  @override
  Widget build(BuildContext context) {
    ref.listen(authProvider.select((auth) => (auth.userId, auth.status)), (
      previous,
      next,
    ) {
      if (previous != next) {
        ++_generation;
        setState(() {
          _links = [];
          _token = null;
          _requestId = null;
          _expires = null;
          _error = null;
          _message = null;
          _busy = false;
        });
      }
    });
    final auth = ref.watch(authProvider);
    final active =
        _token != null && _expires != null && _expires!.isAfter(DateTime.now());
    const labels = {
      'PENDING_OWNER': 'Chờ bạn xác nhận',
      'LINKED': 'Đã liên kết',
      'DUPLICATE_REVIEW': 'Hồ sơ trùng, chưa cấp quyền',
      'REJECTED': 'Đã từ chối',
      'REVOKED': 'Đã thu hồi',
      'EXPIRED': 'Đã hết hạn',
    };
    return Scaffold(
      appBar: AppBar(
        title: const Text('Liên kết hồ sơ tại viện'),
        actions: [
          IconButton(
            onPressed: _busy ? null : _load,
            icon: const Icon(Icons.refresh),
            tooltip: 'Tải lại',
          ),
        ],
      ),
      body: auth.status != AuthStatus.authenticated
          ? const Center(child: Text('Vui lòng đăng nhập lại.'))
          : RefreshIndicator(
              onRefresh: _load,
              child: ListView(
                padding: const EdgeInsets.all(20),
                children: [
                  const Text(
                    'Nhận hồ sơ tiếp nhận của chính bạn',
                    style: TextStyle(fontSize: 20, fontWeight: FontWeight.bold),
                  ),
                  const SizedBox(height: 12),
                  const Text(
                    'Cần hoàn tất đối chiếu giấy tờ trực tiếp tại quầy. Tạo mã và đưa cho nhân viên để yêu cầu liên kết. Chỉ xác nhận khi họ tên và ngày sinh dưới đây là của bạn. Chức năng này chưa hỗ trợ người đại diện.',
                  ),
                  const SizedBox(height: 16),
                  if (_error != null)
                    Padding(
                      padding: const EdgeInsets.only(bottom: 12),
                      child: Text(
                        _error!,
                        style: const TextStyle(color: Colors.red),
                      ),
                    ),
                  if (_message != null)
                    Padding(
                      padding: const EdgeInsets.only(bottom: 12),
                      child: Text(_message!),
                    ),
                  FilledButton.icon(
                    onPressed: _busy
                        ? null
                        : () => _run(() async {
                            final owner = _owner;
                            _token ??= base64UrlEncode(
                              List<int>.generate(
                                32,
                                (_) => Random.secure().nextInt(256),
                              ),
                            ).replaceAll('=', '');
                            _requestId ??= const Uuid().v4();
                            final expires = await ref
                                .read(profileLinkServiceProvider)
                                .authorize(_requestId!, _token!);
                            if (mounted && owner == _owner) {
                              setState(() {
                                _expires = expires;
                              });
                            }
                          }),
                    icon: const Icon(Icons.link),
                    label: Text(
                      _busy
                          ? 'Đang xử lý…'
                          : active
                          ? 'Mã liên kết đang hiệu lực'
                          : 'Tạo / thử lại mã liên kết',
                    ),
                  ),
                  if (active)
                    Card(
                      child: Padding(
                        padding: const EdgeInsets.all(16),
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            const Text(
                              'Mã chỉ dùng một lần; không gửi cho người lạ.',
                            ),
                            const SizedBox(height: 10),
                            SelectableText(
                              _token!,
                              style: const TextStyle(fontFamily: 'monospace'),
                            ),
                            Text(
                              'Hết hạn lúc ${_expires!.toLocal().hour.toString().padLeft(2, '0')}:${_expires!.toLocal().minute.toString().padLeft(2, '0')}',
                            ),
                            TextButton.icon(
                              onPressed: () async {
                                await Clipboard.setData(
                                  ClipboardData(text: _token!),
                                );
                              },
                              icon: const Icon(Icons.copy),
                              label: const Text('Sao chép mã cho nhân viên'),
                            ),
                          ],
                        ),
                      ),
                    ),
                  if (_token != null && !active)
                    TextButton(
                      onPressed: _busy
                          ? null
                          : () {
                              setState(() {
                                _token = null;
                                _requestId = null;
                                _expires = null;
                              });
                            },
                      child: const Text('Tạo mã mới'),
                    ),
                  const SizedBox(height: 24),
                  const Text(
                    'Yêu cầu và liên kết của bạn',
                    style: TextStyle(fontSize: 18, fontWeight: FontWeight.w600),
                  ),
                  if (_links.isEmpty)
                    const Padding(
                      padding: EdgeInsets.symmetric(vertical: 16),
                      child: Text(
                        'Chưa có yêu cầu liên kết. Sau khi nhân viên lập yêu cầu, bấm tải lại hoặc chờ cập nhật.',
                      ),
                    ),
                  ..._links.map(
                    (link) => Card(
                      child: Padding(
                        padding: const EdgeInsets.all(16),
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text(
                              link.patientName,
                              style: const TextStyle(
                                fontWeight: FontWeight.bold,
                              ),
                            ),
                            Text(
                              'Ngày sinh: ${link.dateOfBirth ?? 'Chưa có'} · ${link.roomId}',
                            ),
                            Text(labels[link.status] ?? link.status),
                            if (link.status == 'DUPLICATE_REVIEW')
                              const Text(
                                'Chưa có quyền bệnh án từ yêu cầu này. Nhân viên cần kiểm tra hồ sơ trùng; hệ thống không tự gộp dữ liệu.',
                              ),
                            if (link.status == 'PENDING_OWNER')
                              Wrap(
                                spacing: 8,
                                children: [
                                  FilledButton(
                                    onPressed: _busy
                                        ? null
                                        : () async {
                                            if (await _confirm(
                                              'Nhận quyền hồ sơ này?',
                                              '${link.patientName}, ngày sinh ${link.dateOfBirth}. Tôi xác nhận đây là hồ sơ của mình và đồng ý liên kết để xem dữ liệu khám bệnh.',
                                            )) {
                                              await _run(() async {
                                                final owner = _owner;
                                                final result = await ref
                                                    .read(
                                                      profileLinkServiceProvider,
                                                    )
                                                    .decide(link.id, true);
                                                if (mounted &&
                                                    owner == _owner) {
                                                  ref.invalidate(
                                                    patientProvider,
                                                  );
                                                  setState(() {
                                                    _message =
                                                        result.status ==
                                                            'LINKED'
                                                        ? 'Đã liên kết hồ sơ.'
                                                        : 'Yêu cầu đã chuyển sang kiểm tra hồ sơ trùng; chưa cấp quyền.';
                                                    _token = null;
                                                    _requestId = null;
                                                    _expires = null;
                                                  });
                                                }
                                              });
                                            }
                                          },
                                    child: const Text('Đây là hồ sơ của tôi'),
                                  ),
                                  TextButton(
                                    onPressed: _busy
                                        ? null
                                        : () => _run(() async {
                                            await ref
                                                .read(
                                                  profileLinkServiceProvider,
                                                )
                                                .decide(link.id, false);
                                          }),
                                    child: const Text('Từ chối'),
                                  ),
                                ],
                              ),
                            if (link.status == 'LINKED')
                              TextButton(
                                onPressed: _busy
                                    ? null
                                    : () async {
                                        if (await _confirm(
                                          'Thu hồi liên kết?',
                                          'Tài khoản sẽ không còn quyền truy cập hồ sơ này. Dữ liệu khám bệnh vẫn được cơ sở y tế lưu giữ.',
                                        )) {
                                          await _run(() async {
                                            final owner = _owner;
                                            await ref
                                                .read(
                                                  profileLinkServiceProvider,
                                                )
                                                .revoke(link.id);
                                            if (mounted && owner == _owner) {
                                              ref.invalidate(patientProvider);
                                            }
                                          });
                                        }
                                      },
                                child: const Text('Thu hồi liên kết'),
                              ),
                          ],
                        ),
                      ),
                    ),
                  ),
                ],
              ),
            ),
    );
  }
}
