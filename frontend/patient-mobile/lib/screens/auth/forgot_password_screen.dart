import 'dart:async';
import 'dart:convert';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../../config/theme.dart';
import '../../providers/auth_provider.dart';
import '../../services/auth_service.dart';
import '../../utils/api_error_message.dart';
import '../../widgets/careflow_button.dart';
import '../../widgets/careflow_text_field.dart';

class ForgotPasswordScreen extends ConsumerStatefulWidget {
  const ForgotPasswordScreen({super.key});
  @override
  ConsumerState<ForgotPasswordScreen> createState() =>
      _ForgotPasswordScreenState();
}

class _ForgotPasswordScreenState extends ConsumerState<ForgotPasswordScreen> {
  final _form = GlobalKey<FormState>();
  final _email = TextEditingController();
  final _code = TextEditingController();
  final _password = TextEditingController();
  final _confirmation = TextEditingController();
  Timer? _ticker;
  DateTime? _resendAt;
  String? _requestId, _resetToken, _error, _message;
  String _sentEmail = '';
  bool _busy = false, _done = false;
  int get _remaining {
    final milliseconds =
        _resendAt?.difference(DateTime.now()).inMilliseconds ?? 0;
    return milliseconds > 0 ? (milliseconds / 1000).ceil() : 0;
  }

  @override
  void dispose() {
    _ticker?.cancel();
    for (final field in [_email, _code, _password, _confirmation]) {
      field.dispose();
    }
    super.dispose();
  }

  Future<void> _request({bool resend = false}) async {
    if (_busy || (resend && _remaining > 0)) return;
    if (!resend && !(_form.currentState?.validate() ?? false)) return;
    FocusManager.instance.primaryFocus?.unfocus();
    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      final email = resend ? _sentEmail : _email.text.trim().toLowerCase();
      final result = await ref
          .read(authServiceProvider)
          .requestEmailRecovery(email);
      if (!mounted) return;
      _ticker?.cancel();
      setState(() {
        _sentEmail = email;
        _requestId = result['requestId'] as String;
        _resetToken = null;
        _message = result['message'] as String;
        _resendAt = DateTime.now().add(
          Duration(seconds: result['resendAfterSeconds'] as int),
        );
        _code.clear();
      });
      _ticker = Timer.periodic(const Duration(seconds: 1), (timer) {
        if (!mounted || _remaining == 0) timer.cancel();
        if (mounted) setState(() {});
      });
    } catch (error) {
      if (mounted) setState(() => _error = ApiErrorMessage.from(error));
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<void> _verify() async {
    if (_busy || !(_form.currentState?.validate() ?? false)) return;
    FocusManager.instance.primaryFocus?.unfocus();
    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      final result = await ref
          .read(authServiceProvider)
          .verifyEmailRecovery(_requestId!, _code.text);
      if (!mounted) return;
      _ticker?.cancel();
      setState(() {
        _resetToken = result['resetToken'] as String;
        _code.clear();
        _message = null;
      });
    } catch (error) {
      if (mounted) setState(() => _error = ApiErrorMessage.from(error));
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<void> _confirm() async {
    if (_busy || !(_form.currentState?.validate() ?? false)) return;
    FocusManager.instance.primaryFocus?.unfocus();
    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      await ref
          .read(authServiceProvider)
          .confirmEmailRecovery(_requestId!, _resetToken!, _password.text);
      ref.read(authProvider.notifier).clearError();
      if (!mounted) return;
      _ticker?.cancel();
      _code.clear();
      _password.clear();
      _confirmation.clear();
      setState(() {
        _done = true;
        _resetToken = null;
        _message = null;
      });
    } catch (error) {
      if (mounted) setState(() => _error = ApiErrorMessage.from(error));
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  void _changeEmail() {
    _ticker?.cancel();
    setState(() {
      _requestId = null;
      _resetToken = null;
      _error = null;
      _message = null;
    });
    _code.clear();
    _password.clear();
    _confirmation.clear();
  }

  @override
  Widget build(BuildContext context) {
    final verification = _requestId != null;
    final verified = _resetToken != null;
    return Scaffold(
      backgroundColor: AppColors.surface,
      appBar: AppBar(
        title: const Text('CareFlow'),
        backgroundColor: AppColors.surface,
        surfaceTintColor: Colors.transparent,
        leading: IconButton(
          tooltip: 'Quay lại',
          icon: const Icon(Icons.arrow_back_rounded),
          onPressed: _busy ? null : () => context.go('/login'),
        ),
      ),
      body: SafeArea(
        top: false,
        child: Align(
          alignment: Alignment.topCenter,
          child: ConstrainedBox(
            constraints: const BoxConstraints(maxWidth: 480),
            child: SingleChildScrollView(
              padding: const EdgeInsets.fromLTRB(24, 16, 24, 32),
              child: AutofillGroup(
                child: Form(
                  key: _form,
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.stretch,
                    children: [
                      Text(
                        _done
                            ? '04 / 04 · Hoàn tất'
                            : verification
                            ? verified
                                  ? '03 / 04 · Mật khẩu mới'
                                  : '02 / 04 · Xác thực email'
                            : '01 / 04 · Email khôi phục',
                        style: const TextStyle(
                          color: AppColors.textSecondary,
                          fontSize: 12,
                        ),
                      ),
                      const SizedBox(height: 24),
                      Align(
                        alignment: Alignment.centerLeft,
                        child: Container(
                          width: 64,
                          height: 64,
                          decoration: BoxDecoration(
                            color: _done
                                ? AppColors.successLight
                                : AppColors.primarySurface,
                            borderRadius: BorderRadius.circular(20),
                          ),
                          child: Icon(
                            _done
                                ? Icons.check_rounded
                                : Icons.mark_email_read_outlined,
                            size: 32,
                            color: _done
                                ? AppColors.success
                                : AppColors.primary,
                          ),
                        ),
                      ),
                      const SizedBox(height: 24),
                      Text(
                        _done
                            ? 'Đã đặt lại mật khẩu'
                            : verified
                            ? 'Tạo mật khẩu mới'
                            : verification
                            ? 'Kiểm tra email của bạn'
                            : 'Quên mật khẩu?',
                        style: const TextStyle(
                          fontSize: 26,
                          fontWeight: FontWeight.w700,
                          height: 1.3,
                        ),
                      ),
                      const SizedBox(height: 12),
                      Text(
                        _done
                            ? 'Mật khẩu mới đã sẵn sàng. Các phiên đăng nhập cũ đã kết thúc. Vui lòng đăng nhập lại.'
                            : verified
                            ? 'Email đã được xác thực. Tạo mật khẩu mới trong 5 phút để hoàn tất.'
                            : verification
                            ? 'Nhập mã 6 số gửi tới $_sentEmail. Xác thực thành công để tiếp tục. Mã có hiệu lực 5 phút, chỉ dùng một lần.'
                            : 'Nhập email đã đăng ký tài khoản CareFlow để nhận mã đặt lại mật khẩu.',
                        style: const TextStyle(
                          fontSize: 15,
                          height: 1.6,
                          color: AppColors.textSecondary,
                        ),
                      ),
                      if (_message != null) ...[
                        const SizedBox(height: 16),
                        Text(
                          '$_message Kiểm tra cả thư rác nếu chưa thấy email.',
                          style: const TextStyle(
                            color: AppColors.textSecondary,
                            height: 1.5,
                          ),
                        ),
                      ],
                      if (_error != null) ...[
                        const SizedBox(height: 16),
                        Semantics(
                          liveRegion: true,
                          child: Container(
                            padding: const EdgeInsets.all(14),
                            decoration: BoxDecoration(
                              color: AppColors.errorLight,
                              borderRadius: BorderRadius.circular(12),
                            ),
                            child: Text(
                              _error!,
                              style: const TextStyle(
                                color: AppColors.error,
                                height: 1.5,
                              ),
                            ),
                          ),
                        ),
                      ],
                      const SizedBox(height: 28),
                      if (_done)
                        CareFlowButton(
                          text: 'Đăng nhập lại',
                          onPressed: () => context.go('/login'),
                        )
                      else if (!verification) ...[
                        TextFormField(
                          key: const ValueKey('recovery-email'),
                          controller: _email,
                          enabled: !_busy,
                          keyboardType: TextInputType.emailAddress,
                          autofillHints: const [AutofillHints.email],
                          autocorrect: false,
                          textInputAction: TextInputAction.done,
                          decoration: const InputDecoration(
                            labelText: 'Email đã đăng ký',
                            hintText: 'ban@example.com',
                            prefixIcon: Icon(Icons.mail_outline),
                          ),
                          validator: (value) =>
                              value != null &&
                                  value.trim().length <= 255 &&
                                  RegExp(
                                    r'^[^\s@]+@[^\s@]+\.[^\s@]+$',
                                  ).hasMatch(value.trim())
                              ? null
                              : 'Vui lòng nhập email hợp lệ.',
                          onFieldSubmitted: (_) => _request(),
                        ),
                        const SizedBox(height: 24),
                        CareFlowButton(
                          text: 'Gửi mã xác nhận',
                          isLoading: _busy,
                          onPressed: _request,
                        ),
                      ] else if (!verified) ...[
                        TextFormField(
                          key: const ValueKey('recovery-code'),
                          controller: _code,
                          enabled: !_busy,
                          keyboardType: TextInputType.number,
                          autocorrect: false,
                          autofillHints: const [AutofillHints.oneTimeCode],
                          inputFormatters: [
                            FilteringTextInputFormatter.digitsOnly,
                            LengthLimitingTextInputFormatter(6),
                          ],
                          textInputAction: TextInputAction.next,
                          style: const TextStyle(
                            fontSize: 24,
                            letterSpacing: 8,
                            fontWeight: FontWeight.w600,
                          ),
                          decoration: const InputDecoration(
                            labelText: 'Mã xác nhận',
                            hintText: '000000',
                          ),
                          validator: (value) =>
                              RegExp(r'^\d{6}$').hasMatch(value ?? '')
                              ? null
                              : 'Nhập đủ 6 số trong email.',
                        ),
                        const SizedBox(height: 24),
                        CareFlowButton(
                          text: 'Xác thực mã',
                          isLoading: _busy,
                          onPressed: _verify,
                        ),
                        const SizedBox(height: 12),
                        TextButton(
                          onPressed: _busy || _remaining > 0
                              ? null
                              : () => _request(resend: true),
                          child: Text(
                            _remaining > 0
                                ? 'Gửi lại mã sau $_remaining giây'
                                : 'Gửi lại mã',
                          ),
                        ),
                        TextButton(
                          onPressed: _busy ? null : _changeEmail,
                          child: const Text('Dùng email khác'),
                        ),
                      ] else ...[
                        const SizedBox(height: 20),
                        CareFlowTextField(
                          key: const ValueKey('recovery-password'),
                          label: 'Mật khẩu mới',
                          controller: _password,
                          obscureText: true,
                          enabled: !_busy,
                          prefixIcon: Icons.lock_outline,
                          validator: (value) =>
                              value != null &&
                                  value.length >= 8 &&
                                  utf8.encode(value).length <= 72 &&
                                  RegExp(r'[A-Z]').hasMatch(value) &&
                                  RegExp(r'[a-z]').hasMatch(value) &&
                                  RegExp(r'[0-9]').hasMatch(value) &&
                                  RegExp(r'[^a-zA-Z0-9]').hasMatch(value)
                              ? null
                              : 'Dùng 8 ký tự trở lên, có chữ hoa, chữ thường, số và ký tự đặc biệt; tối đa 72 byte.',
                        ),
                        const SizedBox(height: 20),
                        CareFlowTextField(
                          key: const ValueKey('recovery-confirmation'),
                          label: 'Xác nhận mật khẩu mới',
                          controller: _confirmation,
                          obscureText: true,
                          enabled: !_busy,
                          prefixIcon: Icons.lock_outline,
                          validator: (value) =>
                              value != null &&
                                  value.isNotEmpty &&
                                  value == _password.text
                              ? null
                              : 'Xác nhận mật khẩu chưa khớp.',
                        ),
                        const SizedBox(height: 24),
                        CareFlowButton(
                          text: 'Đặt lại mật khẩu',
                          isLoading: _busy,
                          onPressed: _confirm,
                        ),
                        const SizedBox(height: 12),
                        TextButton(
                          onPressed: _busy ? null : _changeEmail,
                          child: const Text('Yêu cầu mã mới'),
                        ),
                      ],
                      if (!_done) ...[
                        const SizedBox(height: 16),
                        TextButton(
                          onPressed: _busy ? null : () => context.go('/login'),
                          child: const Text('Quay về đăng nhập'),
                        ),
                      ],
                    ],
                  ),
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }
}
