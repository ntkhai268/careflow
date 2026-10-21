import 'dart:convert';
import '../../services/auth_service.dart';
import '../../services/biometric_session_store.dart';
import 'package:biometric_storage/biometric_storage.dart' show AuthException;
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../../services/api_service.dart';
import '../../providers/auth_provider.dart';
import '../../utils/api_error_message.dart';
import '../../widgets/careflow_text_field.dart';
import '../../widgets/careflow_button.dart';
import '../../config/theme.dart';

const loginQuestions = {
  'memory': 'Kỷ niệm riêng nào bạn luôn nhớ?',
  'phrase': 'Câu nói bí mật do bạn tự đặt là gì?',
  'place': 'Tên địa điểm đặc biệt chỉ bạn biết là gì?',
};

class LoginMethodsScreen extends ConsumerStatefulWidget {
  const LoginMethodsScreen({
    super.key,
    this.configure = false,
    this.initialBiometric = false,
  });
  final bool configure, initialBiometric;
  @override
  ConsumerState<LoginMethodsScreen> createState() => _LoginMethodsScreenState();
}

class _LoginMethodsScreenState extends ConsumerState<LoginMethodsScreen> {
  final _formKey = GlobalKey<FormState>();
  final _scroll = ScrollController();
  final _username = TextEditingController();
  final _password = TextEditingController();
  final _answer = TextEditingController();
  String _question = 'memory', _method = 'biometric';
  BiometricCapability _capability = const BiometricCapability(
    false,
    'Sinh trắc học',
    'Đang kiểm tra điện thoại…',
  );
  String? _biometricAccount;
  bool _busy = false, _editing = false, _loading = false;
  String? _error, _message;
  Map<String, dynamic>? _settings;
  @override
  void initState() {
    super.initState();
    _editing = widget.initialBiometric;
    _load();
  }

  @override
  void dispose() {
    _scroll.dispose();
    _username.dispose();
    _password.dispose();
    _answer.dispose();
    super.dispose();
  }

  Future<void> _load() async {
    setState(() {
      _loading = true;
      _error = null;
    });
    final sessions = ref.read(apiServiceProvider).sessions;
    try {
      if (widget.configure) {
        await ref.read(authServiceProvider).reconcileBiometrics();
      }
      final capability = await sessions.vault.capability();
      final account = await sessions.account();
      if (!mounted) return;
      setState(() {
        _capability = capability;
        _biometricAccount = account;
      });
      if (widget.configure) {
        final settings = await ref.read(authServiceProvider).loginMethods();
        if (mounted) {
          setState(() {
            _settings = settings;
            if ((settings['questionId'] as String).isNotEmpty) {
              _question = settings['questionId'];
            }
          });
        }
      }
    } catch (e) {
      if (mounted) setState(() => _error = ApiErrorMessage.from(e));
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  Future<void> _action() async {
    if (!(_formKey.currentState?.validate() ?? false)) return;
    FocusManager.instance.primaryFocus?.unfocus();
    setState(() {
      _busy = true;
      _error = null;
      _message = null;
    });
    try {
      final auth = ref.read(authServiceProvider);
      if (widget.configure) {
        await auth.enableBiometrics(_password.text);
        _password.clear();
        _answer.clear();
        await _load();
        if (mounted) {
          setState(() {
            _editing = false;
            _message = _method == 'question'
                ? 'Đã lưu câu hỏi bí mật.'
                : 'Đã bật ${_capability.label} trên điện thoại này.';
          });
        }
      } else {
        await ref
            .read(authProvider.notifier)
            .alternativeLogin(
              _method,
              _username.text.trim(),
              _question,
              _answer.text,
            );
        final state = ref.read(authProvider);
        if (state.status != AuthStatus.authenticated) {
          throw Exception(state.errorMessage ?? 'Không đăng nhập được.');
        }
        if (mounted) context.go('/');
      }
    } catch (e) {
      if (mounted) {
        setState(
          () => _error = e is AuthException
              ? 'Đã hủy hoặc chưa xác thực được sinh trắc học. Bạn có thể thử lại hoặc dùng mật khẩu.'
              : ApiErrorMessage.from(e),
        );
        WidgetsBinding.instance.addPostFrameCallback((_) {
          if (mounted && _scroll.hasClients) _scroll.jumpTo(0);
        });
      }
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<void> _disableBiometrics() async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: Text('Tắt ${_capability.label}?'),
        content: const Text(
          'Lần mở app sau sẽ dùng mật khẩu. Phiên hiện tại vẫn tiếp tục.',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: const Text('Giữ lại'),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(context, true),
            child: const Text('Tắt'),
          ),
        ],
      ),
    );
    if (confirmed != true || !mounted) return;
    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      await ref.read(authServiceProvider).disableBiometrics();
      await _load();
      if (mounted) {
        setState(() {
          _editing = false;
          _message = 'Đã tắt đăng nhập sinh trắc học trên điện thoại này.';
        });
      }
    } catch (error) {
      if (mounted) setState(() => _error = ApiErrorMessage.from(error));
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  void _choose(String method) {
    setState(() {
      _method = method;
      _editing = true;
      _error = null;
      _message = null;
    });
    _password.clear();
    _answer.clear();
  }

  void _back() {
    if (_editing) {
      setState(() {
        _editing = false;
        _error = null;
      });
      _password.clear();
      _answer.clear();
    } else {
      context.go(widget.configure ? '/?tab=4' : '/login');
    }
  }

  Future<void> _pickQuestion() async {
    final value = await showModalBottomSheet<String>(
      context: context,
      showDragHandle: true,
      isScrollControlled: true,
      backgroundColor: AppColors.surface,
      shape: const RoundedRectangleBorder(
        borderRadius: BorderRadius.vertical(top: Radius.circular(24)),
      ),
      builder: (context) => SafeArea(
        child: SingleChildScrollView(
          child: Padding(
            padding: const EdgeInsets.fromLTRB(16, 0, 16, 24),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                const Padding(
                  padding: EdgeInsets.fromLTRB(8, 0, 8, 16),
                  child: Text(
                    'Chọn câu hỏi bí mật',
                    style: TextStyle(fontSize: 20, fontWeight: FontWeight.w700),
                  ),
                ),
                ...loginQuestions.entries.map(
                  (q) => Padding(
                    padding: const EdgeInsets.only(bottom: 8),
                    child: ListTile(
                      shape: RoundedRectangleBorder(
                        borderRadius: BorderRadius.circular(12),
                      ),
                      tileColor: _question == q.key
                          ? AppColors.primarySurface
                          : AppColors.background,
                      title: Text(
                        q.value,
                        style: const TextStyle(fontSize: 16, height: 1.5),
                      ),
                      trailing: Icon(
                        _question == q.key
                            ? Icons.check_circle_rounded
                            : Icons.radio_button_unchecked,
                        color: AppColors.primary,
                      ),
                      onTap: () => Navigator.pop(context, q.key),
                    ),
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
    if (mounted && value != null) setState(() => _question = value);
  }

  Widget _field(
    String label,
    TextEditingController controller, {
    bool secret = false,
    String? hint,
    IconData? icon,
  }) => CareFlowTextField(
    label: label,
    hint: hint,
    controller: controller,
    obscureText: secret,
    prefixIcon: icon,
    enabled: !_busy,
    textInputAction: TextInputAction.next,
    validator: (value) {
      if (value == null || value.trim().isEmpty) {
        return 'Vui lòng nhập ${label.toLowerCase()}';
      }
      if (controller == _answer &&
          widget.configure &&
          (value.trim().length < 8 || utf8.encode(value.trim()).length > 72)) {
        return 'Câu trả lời cần từ 8 ký tự, tối đa 72 byte UTF-8.';
      }
      return null;
    },
  );

  @override
  Widget build(BuildContext context) {
    final biometric = _method == 'biometric';
    final title = !_editing
        ? widget.configure
              ? 'Bảo mật đăng nhập'
              : 'Cách đăng nhập'
        : widget.configure
        ? biometric
              ? 'Thiết lập ${_capability.label}'
              : 'Thiết lập câu hỏi bí mật'
        : biometric
        ? 'Đăng nhập bằng ${_capability.label}'
        : 'Đăng nhập bằng câu hỏi';
    final description = !_editing
        ? widget.configure
              ? 'Thiết lập và quản lý các cách truy cập tài khoản.'
              : 'Chọn phương thức đã thiết lập cho tài khoản của bạn.'
        : biometric
        ? _capability.detail
        : widget.configure
        ? 'Chọn một câu hỏi và câu trả lời riêng mà bạn có thể ghi nhớ.'
        : 'Chọn đúng câu hỏi và nhập câu trả lời bạn đã lưu.';

    return Scaffold(
      backgroundColor: AppColors.surface,
      appBar: AppBar(
        backgroundColor: AppColors.surface,
        surfaceTintColor: Colors.transparent,
        title: Text(
          widget.configure ? 'Tài khoản' : 'CareFlow',
          style: const TextStyle(fontSize: 16, fontWeight: FontWeight.w600),
        ),
        leading: IconButton(
          tooltip: 'Quay lại',
          icon: const Icon(Icons.arrow_back_rounded),
          onPressed: _busy ? null : _back,
        ),
      ),
      body: SafeArea(
        top: false,
        child: Align(
          alignment: Alignment.topCenter,
          child: ConstrainedBox(
            constraints: const BoxConstraints(maxWidth: 480),
            child: SingleChildScrollView(
              controller: _scroll,
              padding: const EdgeInsets.fromLTRB(24, 16, 24, 32),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  if (!widget.configure) ...[
                    Text(
                      '${_editing ? "02" : "01"} / 02 · ${_editing ? "Thông tin xác thực" : "Chọn phương thức"}',
                      style: const TextStyle(
                        fontSize: 12,
                        color: AppColors.textSecondary,
                      ),
                    ),
                    const SizedBox(height: 24),
                  ],
                  Align(
                    alignment: Alignment.centerLeft,
                    child: Container(
                      width: 64,
                      height: 64,
                      decoration: BoxDecoration(
                        color: AppColors.primarySurface,
                        borderRadius: BorderRadius.circular(20),
                      ),
                      child: _editing && biometric
                          ? const Icon(
                              Icons.fingerprint_rounded,
                              color: AppColors.primary,
                              size: 32,
                            )
                          : Icon(
                              widget.configure
                                  ? Icons.shield_outlined
                                  : Icons.login_rounded,
                              color: AppColors.primary,
                              size: 30,
                            ),
                    ),
                  ),
                  const SizedBox(height: 24),
                  Semantics(
                    header: true,
                    child: Text(
                      title,
                      style: const TextStyle(
                        fontSize: 26,
                        height: 1.25,
                        fontWeight: FontWeight.w700,
                        letterSpacing: -0.5,
                      ),
                    ),
                  ),
                  const SizedBox(height: 12),
                  Text(
                    description,
                    style: const TextStyle(
                      fontSize: 15,
                      height: 1.6,
                      color: AppColors.textSecondary,
                    ),
                  ),
                  if (_loading)
                    const Padding(
                      padding: EdgeInsets.only(top: 20),
                      child: LinearProgressIndicator(minHeight: 2),
                    ),
                  if (_message != null)
                    Padding(
                      padding: const EdgeInsets.only(top: 20),
                      child: _AuthNotice(text: _message!, success: true),
                    ),
                  if (_error != null) ...[
                    Padding(
                      padding: const EdgeInsets.only(top: 20),
                      child: _AuthNotice(text: _error!),
                    ),
                    if (widget.configure && _settings == null)
                      TextButton(
                        onPressed: _loading ? null : _load,
                        child: const Text('Thử lại'),
                      ),
                  ],
                  const SizedBox(height: 28),
                  if (!_editing) ...[
                    _MethodCard(
                      biometric: true,
                      title: _capability.label,
                      detail: _loading
                          ? 'Đang kiểm tra điện thoại…'
                          : !_capability.available
                          ? _capability.detail
                          : _biometricAccount == null
                          ? 'Chưa bật trên điện thoại. Thiết lập trong Tài khoản sau khi đăng nhập.'
                          : 'Đã bật cho $_biometricAccount trên điện thoại này.',
                      onTap:
                          _loading ||
                              !_capability.available ||
                              (!widget.configure && _biometricAccount == null)
                          ? null
                          : () => _choose('biometric'),
                    ),
                    const SizedBox(height: 12),
                    const SizedBox(height: 24),
                    if (widget.configure)
                      const Row(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Icon(
                            Icons.lock_outline_rounded,
                            size: 16,
                            color: AppColors.textSecondary,
                          ),
                          SizedBox(width: 8),
                          Expanded(
                            child: Text(
                              'Bạn cần mật khẩu hiện tại để thay đổi phương thức.',
                              style: TextStyle(
                                fontSize: 13,
                                height: 1.5,
                                color: AppColors.textSecondary,
                              ),
                            ),
                          ),
                        ],
                      ),
                  ] else
                    Form(
                      key: _formKey,
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.stretch,
                        children: [
                          if (!widget.configure && !biometric) ...[
                            _field(
                              'Tên đăng nhập hoặc email',
                              _username,
                              hint: 'Nhập tài khoản của bạn',
                              icon: Icons.person_outline_rounded,
                            ),
                            const SizedBox(height: 20),
                          ],
                          if (biometric) ...[
                            Container(
                              padding: const EdgeInsets.all(16),
                              decoration: BoxDecoration(
                                color: AppColors.background,
                                borderRadius: BorderRadius.circular(12),
                              ),
                              child: const Column(
                                crossAxisAlignment: CrossAxisAlignment.start,
                                children: [
                                  Text(
                                    'Thiết bị sẽ yêu cầu xác nhận',
                                    style: TextStyle(
                                      fontSize: 14,
                                      fontWeight: FontWeight.w600,
                                    ),
                                  ),
                                  SizedBox(height: 6),
                                  Text(
                                    'Xác nhận trong hộp thoại của điện thoại. Khi đóng app hoặc khóa, lần truy cập tiếp theo cần xác thực lại. Đăng xuất sẽ xóa thiết lập này.',
                                    style: TextStyle(
                                      fontSize: 14,
                                      height: 1.5,
                                      color: AppColors.textSecondary,
                                    ),
                                  ),
                                ],
                              ),
                            ),
                            const SizedBox(height: 20),
                          ] else ...[
                            const Text(
                              'Câu hỏi bí mật',
                              style: TextStyle(
                                fontSize: 14,
                                fontWeight: FontWeight.w600,
                                color: AppColors.textSecondary,
                              ),
                            ),
                            const SizedBox(height: 8),
                            OutlinedButton(
                              onPressed: _busy ? null : _pickQuestion,
                              style: OutlinedButton.styleFrom(
                                padding: const EdgeInsets.symmetric(
                                  horizontal: 14,
                                  vertical: 16,
                                ),
                                side: const BorderSide(
                                  color: AppColors.cardBorder,
                                ),
                                shape: RoundedRectangleBorder(
                                  borderRadius: BorderRadius.circular(12),
                                ),
                                foregroundColor: AppColors.textPrimary,
                              ),
                              child: Row(
                                children: [
                                  Expanded(
                                    child: Text(
                                      loginQuestions[_question]!,
                                      style: const TextStyle(
                                        fontSize: 15,
                                        height: 1.5,
                                      ),
                                    ),
                                  ),
                                  const SizedBox(width: 8),
                                  const Icon(
                                    Icons.keyboard_arrow_down_rounded,
                                    color: AppColors.textSecondary,
                                  ),
                                ],
                              ),
                            ),
                            const SizedBox(height: 20),
                            _field(
                              'Câu trả lời bí mật',
                              _answer,
                              secret: true,
                              hint: 'Nhập câu trả lời của bạn',
                              icon: Icons.chat_bubble_outline_rounded,
                            ),
                            if (widget.configure)
                              const Padding(
                                padding: EdgeInsets.only(top: 8),
                                child: Text(
                                  'Dùng câu trả lời dài, khó đoán và không công khai. Từ 8 ký tự, tối đa 72 byte UTF-8.',
                                  style: TextStyle(
                                    fontSize: 13,
                                    height: 1.5,
                                    color: AppColors.textSecondary,
                                  ),
                                ),
                              ),
                            const SizedBox(height: 20),
                          ],
                          if (widget.configure &&
                              (!biometric || _biometricAccount == null)) ...[
                            _field(
                              'Mật khẩu hiện tại',
                              _password,
                              secret: true,
                              icon: Icons.lock_outline_rounded,
                            ),
                            const SizedBox(height: 20),
                          ],
                          if (widget.configure &&
                              biometric &&
                              _biometricAccount != null) ...[
                            CareFlowButton(
                              text: 'Khóa và thử ${_capability.label}',
                              isLoading: _busy,
                              onPressed: () async {
                                await ref
                                    .read(authProvider.notifier)
                                    .lockSession();
                                if (context.mounted) {
                                  context.go('/login/biometric');
                                }
                              },
                            ),
                            const SizedBox(height: 12),
                            TextButton(
                              onPressed: _busy ? null : _disableBiometrics,
                              child: Text(
                                'Tắt ${_capability.label} trên điện thoại này',
                                style: const TextStyle(color: AppColors.error),
                              ),
                            ),
                          ] else
                            CareFlowButton(
                              text: widget.configure
                                  ? biometric
                                        ? 'Bật ${_capability.label}'
                                        : 'Lưu câu hỏi bí mật'
                                  : biometric
                                  ? 'Tiếp tục với ${_capability.label}'
                                  : 'Đăng nhập',
                              isLoading: _busy,
                              onPressed:
                                  _loading ||
                                      (biometric &&
                                          (!_capability.available ||
                                              (!widget.configure &&
                                                  _biometricAccount == null)))
                                  ? null
                                  : _action,
                            ),
                          const SizedBox(height: 12),
                          TextButton(
                            onPressed: _busy ? null : _back,
                            child: const Text('Chọn phương thức khác'),
                          ),
                        ],
                      ),
                    ),
                  if (!widget.configure) ...[
                    const SizedBox(height: 20),
                    TextButton(
                      onPressed: _busy ? null : () => context.go('/login'),
                      child: const Text('Đăng nhập bằng mật khẩu'),
                    ),
                  ],
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }
}

class _MethodCard extends StatelessWidget {
  const _MethodCard({
    required this.title,
    required this.detail,
    required this.onTap,
    this.biometric = false,
  });
  final String title, detail;
  final VoidCallback? onTap;
  final bool biometric;
  @override
  Widget build(BuildContext context) => Semantics(
    button: true,
    child: Material(
      color: AppColors.surface,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(16),
        side: const BorderSide(color: AppColors.cardBorder),
      ),
      child: InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(16),
        child: Padding(
          padding: const EdgeInsets.all(16),
          child: Row(
            children: [
              Container(
                width: 44,
                height: 44,
                decoration: BoxDecoration(
                  color: AppColors.primarySurface,
                  borderRadius: BorderRadius.circular(12),
                ),
                child: biometric
                    ? const Icon(
                        Icons.fingerprint_rounded,
                        color: AppColors.primary,
                        size: 23,
                      )
                    : const Icon(
                        Icons.chat_bubble_outline_rounded,
                        color: AppColors.primary,
                        size: 23,
                      ),
              ),
              const SizedBox(width: 14),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      title,
                      style: const TextStyle(
                        fontSize: 16,
                        fontWeight: FontWeight.w600,
                        height: 1.4,
                      ),
                    ),
                    const SizedBox(height: 5),
                    Text(
                      detail,
                      style: const TextStyle(
                        fontSize: 13,
                        color: AppColors.textSecondary,
                        height: 1.5,
                      ),
                    ),
                  ],
                ),
              ),
              const SizedBox(width: 8),
              const Icon(
                Icons.chevron_right_rounded,
                color: AppColors.textSecondary,
                size: 20,
              ),
            ],
          ),
        ),
      ),
    ),
  );
}

class _AuthNotice extends StatelessWidget {
  const _AuthNotice({required this.text, this.success = false});
  final String text;
  final bool success;
  @override
  Widget build(BuildContext context) => Semantics(
    liveRegion: true,
    child: Container(
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: success ? AppColors.successLight : AppColors.errorLight,
        borderRadius: BorderRadius.circular(12),
      ),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Icon(
            success
                ? Icons.check_circle_outline_rounded
                : Icons.error_outline_rounded,
            size: 20,
            color: success ? AppColors.success : AppColors.error,
          ),
          const SizedBox(width: 10),
          Expanded(
            child: Text(
              text,
              style: TextStyle(
                fontSize: 14,
                height: 1.5,
                color: success ? AppColors.success : AppColors.error,
              ),
            ),
          ),
        ],
      ),
    ),
  );
}
