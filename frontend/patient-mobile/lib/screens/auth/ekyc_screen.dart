import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:uuid/uuid.dart';
import '../../services/ekyc_service.dart';
import '../../services/local_document_reader.dart';
import '../../utils/api_error_message.dart';

class EkycScreen extends ConsumerStatefulWidget {
  const EkycScreen({super.key});
  @override
  ConsumerState<EkycScreen> createState() => _EkycScreenState();
}

class _EkycScreenState extends ConsumerState<EkycScreen> {
  final _form = GlobalKey<FormState>();
  final _number = TextEditingController(),
      _name = TextEditingController(),
      _birth = TextEditingController();
  EkycStatus? _status;
  String? _error;
  String? _readNotice;
  bool _loading = true, _busy = false, _consent = false;
  String _requestKey = const Uuid().v4();
  @override
  void initState() {
    super.initState();
    _load();
  }

  @override
  void dispose() {
    _number.dispose();
    _name.dispose();
    _birth.dispose();
    super.dispose();
  }

  Future<void> _load() async {
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final status = await ref.read(ekycServiceProvider).status();
      if (mounted) setState(() => _status = status);
    } catch (e) {
      if (mounted) setState(() => _error = ApiErrorMessage.from(e));
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  void _edited(String _) {
    setState(() {
      _consent = false;
      _requestKey = const Uuid().v4();
    });
  }

  Future<void> _read() async {
    setState(() {
      _busy = true;
      _error = null;
    });
    _readNotice = null;
    try {
      final fields = await ref.read(localDocumentReaderProvider).read();
      if (mounted && fields != null) {
        setState(() {
          _number.text = fields.number;
          _name.text = fields.name;
          _birth.text = fields.birth;
          _readNotice =
              [
                fields.number,
                fields.name,
                fields.birth,
              ].every((value) => value.isEmpty)
              ? 'Ảnh đã được xử lý trên điện thoại nhưng chưa đọc rõ thông tin. Hãy chụp lại rõ hơn hoặc nhập các ô bên dưới.'
              : 'Đã đọc thông tin trên điện thoại. Hãy kiểm tra và bổ sung các ô còn thiếu trước khi gửi.';
          _consent = false;
          _requestKey = const Uuid().v4();
        });
      }
    } catch (_) {
      if (mounted) {
        setState(
          () => _error =
              'Chưa đọc được giấy tờ trên máy. Bạn có thể thử lại, kiểm tra quyền camera hoặc tự nhập thông tin bên dưới.',
        );
      }
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<void> _submit() async {
    if (_busy || !_consent || !(_form.currentState?.validate() ?? false)) {
      return;
    }
    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      final result = await ref
          .read(ekycServiceProvider)
          .submit(
            requestKey: _requestKey,
            documentNumber: _number.text.trim(),
            fullName: _name.text.trim(),
            birthDate: DocumentFields.isoBirth(_birth.text)!,
          );
      _number.clear();
      _name.clear();
      _birth.clear();
      if (mounted) {
        setState(() => _status = EkycStatus(available: true, latest: result));
      }
    } catch (e) {
      if (mounted) setState(() => _error = ApiErrorMessage.from(e));
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<void> _withdraw() async {
    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      await ref.read(ekycServiceProvider).withdraw(_status!.latest!.id);
      await _load();
    } catch (e) {
      if (mounted) setState(() => _error = ApiErrorMessage.from(e));
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final latest = _status?.latest;
    final canSubmit =
        _status?.available == true &&
        latest?.status != 'CONFIRMED' &&
        latest?.status != 'PENDING_REVIEW';
    return PopScope(
      canPop: !_busy,
      child: Scaffold(
        appBar: AppBar(title: const Text('Đối chiếu danh tính')),
        body: ListView(
          padding: const EdgeInsets.all(20),
          children: [
            const Text(
              'Đọc giấy tờ trên điện thoại',
              style: TextStyle(fontSize: 23, fontWeight: FontWeight.w700),
            ),
            const SizedBox(height: 12),
            const Text(
              'Ảnh được đọc ngay trên điện thoại, không tải lên máy chủ và không gửi tới dịch vụ bên ngoài. Không cần chụp selfie. Bạn kiểm tra thông tin trước khi gửi.',
            ),
            const SizedBox(height: 12),
            const Text(
              'Mang căn cước bản gốc đến cơ sở y tế để nhân viên đối chiếu. Đọc chữ hoặc QR chưa xác nhận giấy tờ là thật và chưa cấp quyền vào bệnh án.',
            ),
            if (_loading)
              const Padding(
                padding: EdgeInsets.all(20),
                child: Center(child: CircularProgressIndicator()),
              ),
            if (_error != null)
              Padding(
                padding: const EdgeInsets.symmetric(vertical: 12),
                child: Text(
                  _error!,
                  style: TextStyle(color: Theme.of(context).colorScheme.error),
                ),
              ),
            if (!_loading && _status?.available != true) ...[
              const SizedBox(height: 16),
              const Text('Dịch vụ tiếp nhận đối chiếu chưa sẵn sàng.'),
              TextButton(
                onPressed: _busy ? null : _load,
                child: const Text('Tải lại trạng thái'),
              ),
            ],
            if (!_loading && latest != null)
              Card(
                child: Padding(
                  padding: const EdgeInsets.all(16),
                  child: Column(
                    children: [
                      Icon(
                        latest.status == 'CONFIRMED'
                            ? Icons.verified_user
                            : Icons.badge_outlined,
                        size: 44,
                      ),
                      const SizedBox(height: 12),
                      Text(
                        latest.message,
                        textAlign: TextAlign.center,
                        style: const TextStyle(fontSize: 18),
                      ),
                      if (latest.maskedDocumentNumber != null)
                        Text('Căn cước: ${latest.maskedDocumentNumber}'),
                      TextButton(
                        onPressed: _busy ? null : _load,
                        child: const Text('Tải lại trạng thái'),
                      ),
                      if (latest.status == 'PENDING_REVIEW')
                        TextButton(
                          onPressed: _busy ? null : _withdraw,
                          child: const Text('Hủy yêu cầu và xóa thông tin'),
                        ),
                    ],
                  ),
                ),
              ),
            if (!_loading && canSubmit)
              Form(
                key: _form,
                child: AutofillGroup(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.stretch,
                    children: [
                      const SizedBox(height: 20),
                      OutlinedButton.icon(
                        onPressed: _busy ? null : _read,
                        icon: const Icon(Icons.document_scanner_outlined),
                        label: Text(
                          _busy ? 'Đang xử lý…' : 'Đọc căn cước trên máy',
                        ),
                      ),
                      const Text(
                        'Chụp mặt có số căn cước, họ tên và ngày sinh, đủ sáng và rõ chữ. Nếu đọc thiếu hoặc sai, hãy sửa các ô bên dưới.',
                      ),
                      const SizedBox(height: 20),
                      if (_readNotice != null)
                        Padding(
                          padding: const EdgeInsets.symmetric(vertical: 12),
                          child: Text(
                            _readNotice!,
                            style: TextStyle(
                              color: Theme.of(context).colorScheme.primary,
                            ),
                          ),
                        ),
                      TextFormField(
                        key: const Key('document-number'),
                        controller: _number,
                        enabled: !_busy,
                        maxLength: 12,
                        keyboardType: TextInputType.number,
                        inputFormatters: [
                          FilteringTextInputFormatter.digitsOnly,
                        ],
                        onChanged: _edited,
                        decoration: const InputDecoration(
                          labelText: 'Số căn cước',
                        ),
                        validator: (v) => RegExp(r'^\d{12}$').hasMatch(v ?? '')
                            ? null
                            : 'Nhập đúng 12 chữ số',
                      ),
                      TextFormField(
                        key: const Key('document-name'),
                        controller: _name,
                        enabled: !_busy,
                        maxLength: 100,
                        onChanged: _edited,
                        enableSuggestions: false,
                        autocorrect: false,
                        decoration: const InputDecoration(
                          labelText: 'Họ tên trên giấy tờ',
                        ),
                        validator: (v) => (v?.trim().length ?? 0) >= 2
                            ? null
                            : 'Nhập họ tên trên giấy tờ',
                      ),
                      TextFormField(
                        key: const Key('document-birth'),
                        controller: _birth,
                        enabled: !_busy,
                        maxLength: 10,
                        keyboardType: TextInputType.datetime,
                        onChanged: _edited,
                        decoration: const InputDecoration(
                          labelText: 'Ngày sinh',
                          hintText: 'dd/MM/yyyy',
                        ),
                        validator: (v) =>
                            DocumentFields.isoBirth(v ?? '') != null
                            ? null
                            : 'Nhập ngày sinh đúng định dạng dd/MM/yyyy',
                      ),
                      CheckboxListTile(
                        contentPadding: EdgeInsets.zero,
                        value: _consent,
                        onChanged: _busy
                            ? null
                            : (v) => setState(() => _consent = v == true),
                        title: const Text(
                          'Tôi đã kiểm tra thông tin và đồng ý gửi số căn cước, họ tên, ngày sinh tới CareFlow để đối chiếu trực tiếp.',
                        ),
                        subtitle: const Text(
                          'Không gửi ảnh. Thông tin chờ đối chiếu được mã hóa và xóa sau khi xử lý, khi bạn hủy hoặc hết hạn 7 ngày.',
                        ),
                      ),
                      FilledButton(
                        onPressed: !_busy && _consent ? _submit : null,
                        child: const Text('Gửi yêu cầu đối chiếu'),
                      ),
                    ],
                  ),
                ),
              ),
          ],
        ),
      ),
    );
  }
}
