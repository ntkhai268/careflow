import 'dart:convert';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../../services/api_service.dart';
import '../../providers/auth_provider.dart';
import '../../platform/browser_bridge.dart';
import '../../utils/api_error_message.dart';
import '../../widgets/careflow_text_field.dart';
import '../../widgets/careflow_button.dart';
import '../../config/theme.dart';
import 'package:flutter_svg/flutter_svg.dart';

const loginQuestions = {
  'memory': 'Kỷ niệm riêng nào bạn luôn nhớ?',
  'phrase': 'Câu nói bí mật do bạn tự đặt là gì?',
  'place': 'Tên địa điểm đặc biệt chỉ bạn biết là gì?',
};

class LoginMethodsScreen extends ConsumerStatefulWidget {
  const LoginMethodsScreen({super.key, this.configure = false, this.recovery = false});
  final bool configure, recovery;
  @override
  ConsumerState<LoginMethodsScreen> createState() => _LoginMethodsScreenState();
}

class _LoginMethodsScreenState extends ConsumerState<LoginMethodsScreen> {
  final _formKey = GlobalKey<FormState>();
  final _scroll = ScrollController();
  final _username = TextEditingController();
  final _password = TextEditingController();
  final _answer = TextEditingController();
  final _confirmation = TextEditingController();
  String _question = 'memory', _method = 'passkey';
  bool _busy = false, _editing = false, _recovered = false, _loading = false;
  String? _error, _message;
  Map<String, dynamic>? _settings;
  @override
  void initState() { super.initState(); if (widget.configure) _load(); }
  @override
  void dispose() { _scroll.dispose(); _username.dispose(); _password.dispose(); _answer.dispose(); _confirmation.dispose(); super.dispose(); }
  Future<void> _load() async {
    setState(() { _loading = true; _error = null; });
    try {
      final r = await ref.read(apiServiceProvider).get('/auth/login-methods');
      if (mounted) setState(() { _settings = Map<String, dynamic>.from(r.data['data']); if ((_settings!['questionId'] as String).isNotEmpty) _question = _settings!['questionId']; });
    } catch (e) { if (mounted) setState(() => _error = ApiErrorMessage.from(e)); }
    finally { if (mounted) setState(() => _loading = false); }
  }
  Future<Map<String, dynamic>> _post(String path, Map<String, dynamic> data) async {
    final r = await ref.read(apiServiceProvider).post(path, data: data);
    return Map<String, dynamic>.from(r.data['data']);
  }
  Future<void> _action({String? remove}) async {
    if (remove == null && !(_formKey.currentState?.validate() ?? false)) return;
    setState(() { _busy = true; _error = null; _message = null; });
    try {
      if (widget.configure && _password.text.isEmpty) throw Exception('Nhập mật khẩu hiện tại để xác nhận.');
      if (!widget.configure && _username.text.trim().isEmpty) throw Exception('Nhập tên đăng nhập hoặc email.');
      if (widget.recovery && (_password.text != _confirmation.text || _password.text.length < 8)) throw Exception('Mật khẩu mới phải từ 8 ký tự và khớp xác nhận.');
      if (_method == 'question' && _answer.text.trim().isEmpty) throw Exception('Nhập câu trả lời bí mật.');
      if (remove != null) {
        await _post('/auth/passkeys/remove', {'password': _password.text, 'credentialId': remove});
        await ref.read(authProvider.notifier).logout();
        if (mounted) context.go('/login');
        return;
      }
      if (widget.configure) {
        if (_method == 'question') {
          await _post('/auth/login-methods/question', {'password': _password.text, 'questionId': _question, 'answer': _answer.text});
        } else {
          final start = await _post('/auth/passkeys/register/options', {'password': _password.text});
          final credential = await browserCall('passkeyCreate', {'options': start['options']});
          await _post('/auth/passkeys/register/finish', {'requestId': start['requestId'], 'credential': jsonEncode(credential)});
        }
        _password.clear(); _answer.clear();
        await _load();
        if (mounted) setState(() { _editing = false; _message = _method == 'question' ? 'Đã lưu câu hỏi bí mật.' : 'Đã thêm passkey cho tài khoản.'; });
      } else if (widget.recovery) {
        if (_method == 'question') {
          await _post('/auth/recovery/question', {'usernameOrEmail': _username.text.trim(), 'questionId': _question, 'answer': _answer.text, 'newPassword': _password.text});
        } else {
          final start = await _post('/auth/recovery/passkey/options', {'usernameOrEmail': _username.text.trim()});
          final credential = await browserCall('passkeyGet', {'options': start['options']});
          await _post('/auth/recovery/passkey/finish', {'requestId': start['requestId'], 'credential': jsonEncode(credential), 'newPassword': _password.text});
        }
        _password.clear(); _answer.clear(); _confirmation.clear();
        if (mounted) setState(() => _recovered = true);
      } else {
        await ref.read(authProvider.notifier).alternativeLogin(_method, _username.text.trim(), _question, _answer.text);
        final state = ref.read(authProvider);
        if (state.status != AuthStatus.authenticated) throw Exception(state.errorMessage ?? 'Không đăng nhập được.');
        if (mounted) context.go('/');
      }
    } catch (e) {
      if (mounted) {
        setState(() => _error = ApiErrorMessage.from(e));
        FocusManager.instance.primaryFocus?.unfocus();
        WidgetsBinding.instance.addPostFrameCallback((_) { if (mounted && _scroll.hasClients) _scroll.jumpTo(0); });
      }
    } finally { if (mounted) setState(() => _busy = false); }
  }
  void _choose(String method) {
    setState(() { _method = method; _editing = true; _error = null; _message = null; });
    _password.clear(); _confirmation.clear(); _answer.clear();
  }

  void _back() {
    if (_editing && !_recovered) {
      setState(() { _editing = false; _error = null; });
      _password.clear(); _confirmation.clear(); _answer.clear();
    } else { context.go(widget.configure ? '/?tab=4' : '/login'); }
  }

  Future<void> _pickQuestion() async {
    final value = await showModalBottomSheet<String>(context: context, showDragHandle: true, isScrollControlled: true,
      backgroundColor: AppColors.surface,
      shape: const RoundedRectangleBorder(borderRadius: BorderRadius.vertical(top: Radius.circular(24))),
      builder: (context) => SafeArea(child: SingleChildScrollView(child: Padding(padding: const EdgeInsets.fromLTRB(16, 0, 16, 24), child: Column(
        mainAxisSize: MainAxisSize.min, crossAxisAlignment: CrossAxisAlignment.stretch, children: [
          const Padding(padding: EdgeInsets.fromLTRB(8, 0, 8, 16), child: Text('Chọn câu hỏi bí mật', style: TextStyle(fontSize: 20, fontWeight: FontWeight.w700))),
          ...loginQuestions.entries.map((q) => Padding(padding: const EdgeInsets.only(bottom: 8), child: ListTile(
            shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
            tileColor: _question == q.key ? AppColors.primarySurface : AppColors.background,
            title: Text(q.value, style: const TextStyle(fontSize: 16, height: 1.5)),
            trailing: Icon(_question == q.key ? Icons.check_circle_rounded : Icons.radio_button_unchecked, color: AppColors.primary),
            onTap: () => Navigator.pop(context, q.key),
          ))),
        ])))),
    );
    if (mounted && value != null) setState(() => _question = value);
  }

  Widget _field(String label, TextEditingController controller, {bool secret = false, String? hint, IconData? icon}) => CareFlowTextField(
    label: label, hint: hint, controller: controller, obscureText: secret, prefixIcon: icon, enabled: !_busy,
    textInputAction: TextInputAction.next,
    validator: (value) {
      if (value == null || value.trim().isEmpty) return 'Vui lòng nhập ${label.toLowerCase()}';
      if (controller == _confirmation && value != _password.text) return 'Mật khẩu mới không khớp xác nhận.';
      return null;
    },
  );

  @override
  Widget build(BuildContext context) {
    final passkey = _method == 'passkey';
    final title = _recovered ? 'Đã đổi mật khẩu' : !_editing
        ? widget.configure ? 'Bảo mật đăng nhập' : widget.recovery ? 'Quên mật khẩu?' : 'Cách đăng nhập'
        : widget.configure ? passkey ? 'Thiết lập passkey' : 'Thiết lập câu hỏi bí mật'
        : widget.recovery ? 'Khôi phục tài khoản' : passkey ? 'Đăng nhập bằng passkey' : 'Đăng nhập bằng câu hỏi';
    final description = _recovered ? 'Mật khẩu mới đã sẵn sàng. Các phiên đăng nhập cũ đã được đăng xuất.'
        : !_editing ? widget.configure ? 'Thiết lập và quản lý các cách truy cập tài khoản.'
        : widget.recovery ? 'Chọn phương thức bạn đã thiết lập để đặt lại mật khẩu.' : 'Chọn phương thức đã thiết lập cho tài khoản của bạn.'
        : passkey ? 'Xác nhận bằng vân tay, khuôn mặt hoặc mã khóa trên thiết bị của bạn.'
        : widget.configure ? 'Chọn một câu hỏi và câu trả lời riêng mà bạn có thể ghi nhớ.' : 'Chọn đúng câu hỏi và nhập câu trả lời bạn đã lưu.';
    final keys = (_settings?['passkeys'] as List?) ?? [];
    return Scaffold(backgroundColor: AppColors.surface,
      appBar: AppBar(backgroundColor: AppColors.surface, surfaceTintColor: Colors.transparent,
        title: Text(widget.configure ? 'Tài khoản' : 'CareFlow', style: const TextStyle(fontSize: 16, fontWeight: FontWeight.w600)),
        leading: IconButton(tooltip: 'Quay lại', icon: const Icon(Icons.arrow_back_rounded), onPressed: _busy ? null : _back)),
      body: SafeArea(top: false, child: Align(alignment: Alignment.topCenter, child: ConstrainedBox(
        constraints: const BoxConstraints(maxWidth: 480),
        child: SingleChildScrollView(controller: _scroll, padding: const EdgeInsets.fromLTRB(24, 16, 24, 32), child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch, children: [
            if (!widget.configure && !_recovered) ...[
              Text('${_editing ? "02" : "01"} / 02 · ${_editing ? "Thông tin xác thực" : "Chọn phương thức"}', style: const TextStyle(fontSize: 12, color: AppColors.textSecondary)),
              const SizedBox(height: 24),
            ],
            Align(alignment: Alignment.centerLeft, child: Container(width: 64, height: 64,
              decoration: BoxDecoration(color: _recovered ? AppColors.successLight : AppColors.primarySurface, borderRadius: BorderRadius.circular(20)),
              child: _recovered ? const Icon(Icons.check_rounded, color: AppColors.success, size: 32)
                  : _editing && passkey ? const Center(child: _PasskeyMark(size: 32))
                  : Icon(widget.configure ? Icons.shield_outlined : widget.recovery ? Icons.lock_reset_rounded : Icons.login_rounded, color: AppColors.primary, size: 30))),
            const SizedBox(height: 24),
            Semantics(header: true, child: Text(title, style: const TextStyle(fontSize: 26, height: 1.25, fontWeight: FontWeight.w700, letterSpacing: -0.5))),
            const SizedBox(height: 12), Text(description, style: const TextStyle(fontSize: 15, height: 1.6, color: AppColors.textSecondary)),
            if (_loading) const Padding(padding: EdgeInsets.only(top: 20), child: LinearProgressIndicator(minHeight: 2)),
            if (_message != null) Padding(padding: const EdgeInsets.only(top: 20), child: _AuthNotice(text: _message!, success: true)),
            if (_error != null) ...[
              Padding(padding: const EdgeInsets.only(top: 20), child: _AuthNotice(text: _error!)),
              if (widget.configure && _settings == null) TextButton(onPressed: _loading ? null : _load, child: const Text('Thử lại')),
            ],
            const SizedBox(height: 28),
            if (_recovered) CareFlowButton(text: 'Đăng nhập lại', onPressed: () => context.go('/login'), icon: Icons.arrow_forward_rounded)
            else if (!_editing) ...[
              _MethodCard(passkey: true, title: 'Vân tay / Khuôn mặt', detail: widget.configure
                ? _settings == null ? 'Đang tải trạng thái…' : keys.isEmpty ? 'Chưa thiết lập passkey' : '${keys.length} passkey đã thiết lập'
                : 'Đăng nhập bằng passkey trên thiết bị.', onTap: widget.configure && _settings == null ? null : () => _choose('passkey')),
              const SizedBox(height: 12),
              _MethodCard(title: 'Câu hỏi bí mật', detail: widget.configure
                ? _settings == null ? 'Đang tải trạng thái…' : (_settings!['questionId'] as String).isEmpty ? 'Chưa thiết lập câu hỏi' : 'Đã thiết lập câu hỏi'
                : 'Sử dụng câu trả lời riêng bạn đã lưu.', onTap: widget.configure && _settings == null ? null : () => _choose('question')),
              const SizedBox(height: 24),
              if (widget.configure) const Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
                Icon(Icons.lock_outline_rounded, size: 16, color: AppColors.textSecondary), SizedBox(width: 8),
                Expanded(child: Text('Bạn cần mật khẩu hiện tại để thay đổi phương thức.', style: TextStyle(fontSize: 13, height: 1.5, color: AppColors.textSecondary))),
              ]),
              if (widget.recovery) const Text('Chưa thiết lập phương thức khôi phục? Liên hệ bộ phận hỗ trợ để được xác minh tài khoản.', style: TextStyle(fontSize: 13, height: 1.6, color: AppColors.textSecondary)),
            ] else Form(key: _formKey, child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
              if (!widget.configure) ...[_field('Tên đăng nhập hoặc email', _username, hint: 'Nhập tài khoản của bạn', icon: Icons.person_outline_rounded), const SizedBox(height: 20)],
              if (passkey) ...[
                Container(padding: const EdgeInsets.all(16), decoration: BoxDecoration(color: AppColors.background, borderRadius: BorderRadius.circular(12)), child: const Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                  Text('Thiết bị sẽ yêu cầu xác nhận', style: TextStyle(fontSize: 14, fontWeight: FontWeight.w600)), SizedBox(height: 6),
                  Text('Thao tác trong hộp thoại của trình duyệt khi bạn tiếp tục. Cách xác nhận tùy thiết bị.', style: TextStyle(fontSize: 14, height: 1.5, color: AppColors.textSecondary)),
                ])), const SizedBox(height: 20),
              ] else ...[
                const Text('Câu hỏi bí mật', style: TextStyle(fontSize: 14, fontWeight: FontWeight.w600, color: AppColors.textSecondary)), const SizedBox(height: 8),
                OutlinedButton(onPressed: _busy ? null : _pickQuestion, style: OutlinedButton.styleFrom(padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 16), side: const BorderSide(color: AppColors.cardBorder), shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)), foregroundColor: AppColors.textPrimary),
                  child: Row(children: [Expanded(child: Text(loginQuestions[_question]!, style: const TextStyle(fontSize: 15, height: 1.5))), const SizedBox(width: 8), const Icon(Icons.keyboard_arrow_down_rounded, color: AppColors.textSecondary)])),
                const SizedBox(height: 20), _field('Câu trả lời bí mật', _answer, secret: true, hint: 'Nhập câu trả lời của bạn', icon: Icons.chat_bubble_outline_rounded),
                if (widget.configure) const Padding(padding: EdgeInsets.only(top: 8), child: Text('Dùng câu trả lời dài, khó đoán và không công khai. Từ 8–72 ký tự.', style: TextStyle(fontSize: 13, height: 1.5, color: AppColors.textSecondary))), const SizedBox(height: 20),
              ],
              if (widget.configure || widget.recovery) ...[_field(widget.configure ? 'Mật khẩu hiện tại' : 'Mật khẩu mới', _password, secret: true, icon: Icons.lock_outline_rounded), const SizedBox(height: 20)],
              if (widget.recovery) ...[_field('Xác nhận mật khẩu mới', _confirmation, secret: true, icon: Icons.lock_outline_rounded), const SizedBox(height: 8), const Text('8–72 ký tự, gồm chữ hoa, chữ thường, số và ký tự đặc biệt.', style: TextStyle(fontSize: 13, height: 1.5, color: AppColors.textSecondary)), const SizedBox(height: 24)],
              CareFlowButton(text: widget.configure ? passkey ? 'Thêm passkey' : 'Lưu câu hỏi bí mật' : widget.recovery ? 'Đặt lại mật khẩu' : passkey ? 'Tiếp tục với passkey' : 'Đăng nhập', isLoading: _busy, onPressed: () => _action()),
              if (widget.configure && passkey && _settings != null) ...[
                const SizedBox(height: 28), const Divider(), const SizedBox(height: 16), const Text('Passkey của bạn', style: TextStyle(fontSize: 16, fontWeight: FontWeight.w600)), const SizedBox(height: 12),
                if (keys.isEmpty) const Text('Chưa có passkey. Thêm khóa đầu tiên để đăng nhập trên thiết bị.', style: TextStyle(fontSize: 14, height: 1.5, color: AppColors.textSecondary)),
                ...keys.asMap().entries.map((entry) {
                  final p = entry.value as Map; final date = DateTime.tryParse(p['created_at'].toString())?.toLocal();
                  return ListTile(contentPadding: EdgeInsets.zero, leading: const _PasskeyMark(), title: Text('Passkey ${entry.key + 1}'), subtitle: Text(date == null ? 'Đã thiết lập' : 'Đã thêm ${date.day}/${date.month}/${date.year}'), trailing: TextButton(onPressed: _busy ? null : () => _confirmRemove(p['id']), child: const Text('Gỡ', style: TextStyle(color: AppColors.error))));
                }),
              ],
              const SizedBox(height: 12), TextButton(onPressed: _busy ? null : _back, child: const Text('Chọn phương thức khác')),
            ])),
            if (!widget.configure && !_recovered) ...[const SizedBox(height: 20), TextButton(onPressed: _busy ? null : () => context.go('/login'), child: const Text('Đăng nhập bằng mật khẩu'))],
          ],
        )),
      ))),
    );
  }

  Future<void> _confirmRemove(String id) async {
    _password.clear(); final form = GlobalKey<FormState>();
    final confirmed = await showDialog<bool>(context: context, builder: (context) => AlertDialog(
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(20)), title: const Text('Gỡ passkey này?'),
      content: SingleChildScrollView(child: Form(key: form, child: Column(mainAxisSize: MainAxisSize.min, children: [
        const Text('Khóa này sẽ không còn dùng để đăng nhập. Mọi phiên hiện tại sẽ được đăng xuất.', style: TextStyle(height: 1.5)), const SizedBox(height: 20), _field('Mật khẩu hiện tại', _password, secret: true),
      ]))),
      actions: [TextButton(onPressed: () => Navigator.pop(context, false), child: const Text('Giữ passkey')), FilledButton(style: FilledButton.styleFrom(backgroundColor: AppColors.error), onPressed: () {if (form.currentState!.validate()) Navigator.pop(context, true);}, child: const Text('Gỡ passkey'))],
    ));
    if (confirmed == true) await _action(remove: id);
    if (mounted) _password.clear();
  }
}

class _MethodCard extends StatelessWidget {
  const _MethodCard({required this.title, required this.detail, required this.onTap, this.passkey = false});
  final String title, detail; final VoidCallback? onTap; final bool passkey;
  @override
  Widget build(BuildContext context) => Semantics(button: true, child: Material(
    color: AppColors.surface, shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16), side: const BorderSide(color: AppColors.cardBorder)),
    child: InkWell(onTap: onTap, borderRadius: BorderRadius.circular(16), child: Padding(padding: const EdgeInsets.all(16), child: Row(children: [
      Container(width: 44, height: 44, decoration: BoxDecoration(color: AppColors.primarySurface, borderRadius: BorderRadius.circular(12)), child: passkey ? const Center(child: _PasskeyMark()) : const Icon(Icons.chat_bubble_outline_rounded, color: AppColors.primary, size: 23)), const SizedBox(width: 14),
      Expanded(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [Text(title, style: const TextStyle(fontSize: 16, fontWeight: FontWeight.w600, height: 1.4)), const SizedBox(height: 5), Text(detail, style: const TextStyle(fontSize: 13, color: AppColors.textSecondary, height: 1.5))])), const SizedBox(width: 8), const Icon(Icons.chevron_right_rounded, color: AppColors.textSecondary, size: 20),
    ]))),
  ));
}

class _AuthNotice extends StatelessWidget {
  const _AuthNotice({required this.text, this.success = false}); final String text; final bool success;
  @override
  Widget build(BuildContext context) => Semantics(liveRegion: true, child: Container(padding: const EdgeInsets.all(14), decoration: BoxDecoration(color: success ? AppColors.successLight : AppColors.errorLight, borderRadius: BorderRadius.circular(12)), child: Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
    Icon(success ? Icons.check_circle_outline_rounded : Icons.error_outline_rounded, size: 20, color: success ? AppColors.success : AppColors.error), const SizedBox(width: 10), Expanded(child: Text(text, style: TextStyle(fontSize: 14, height: 1.5, color: success ? AppColors.success : AppColors.error))),
  ])));
}

class _PasskeyMark extends StatelessWidget {
  const _PasskeyMark({this.size = 24}); final double size;
  @override
  Widget build(BuildContext context) => SvgPicture.string('<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="#0277A8" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round"><circle cx="8" cy="7" r="3"/><path d="M2 20v-3a6 6 0 0 1 11-3M19 14v7m0-3h3"/><circle cx="19" cy="10" r="4"/></svg>', width: size, height: size, excludeFromSemantics: true);
}
