import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'dart:async';
import 'package:flutter/services.dart';
import '../../config/theme.dart';
import '../../providers/auth_provider.dart';
import '../../widgets/careflow_button.dart';
import '../../widgets/careflow_text_field.dart';
import '../../services/email_registration_service.dart';
import '../../utils/api_error_message.dart';

/// Register screen aligned with the Identity Service contract.
class RegisterScreen extends ConsumerStatefulWidget {
  const RegisterScreen({super.key});

  @override
  ConsumerState<RegisterScreen> createState() => _RegisterScreenState();
}

class _RegisterScreenState extends ConsumerState<RegisterScreen> {
  final _formKey = GlobalKey<FormState>();
  final _usernameController = TextEditingController();
  final _emailController = TextEditingController();
  final _passwordController = TextEditingController();
  final _confirmPasswordController = TextEditingController();
  final _codeController = TextEditingController();
  String? _requestId, _error;
  bool _busy = false;
  DateTime? _resendAt;
  Timer? _timer;

  final _emailFocus = FocusNode();
  final _passwordFocus = FocusNode();
  final _confirmPasswordFocus = FocusNode();

  @override
  void dispose() {
    _timer?.cancel();
    _codeController.dispose();
    _usernameController.dispose();
    _emailController.dispose();
    _passwordController.dispose();
    _confirmPasswordController.dispose();
    _emailFocus.dispose();
    _passwordFocus.dispose();
    _confirmPasswordFocus.dispose();
    super.dispose();
  }

  Future<void> _handleRegister() async {
    if (_busy || !(_formKey.currentState?.validate() ?? false)) return;
    if (_requestId == null) {
      await _requestCode();
      return;
    }
    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      await ref
          .read(emailRegistrationServiceProvider)
          .verify(_requestId!, _codeController.text.trim());
      if (!mounted) return;
      await ref
          .read(authProvider.notifier)
          .login(_usernameController.text.trim(), _passwordController.text);
    } catch (e) {
      if (mounted) setState(() => _error = ApiErrorMessage.from(e));
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<void> _requestCode() async {
    if (_busy) return;
    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      final id = await ref
          .read(emailRegistrationServiceProvider)
          .request(
            _usernameController.text,
            _emailController.text,
            _passwordController.text,
          );
      if (!mounted) return;
      setState(() {
        _requestId = id;
        _codeController.clear();
        _resendAt = DateTime.now().add(const Duration(seconds: 60));
      });
      _timer?.cancel();
      _timer = Timer.periodic(const Duration(seconds: 1), (_) {
        if (mounted) setState(() {});
        if (_resendAt != null && !DateTime.now().isBefore(_resendAt!)) {
          _timer?.cancel();
        }
      });
    } catch (e) {
      if (mounted) setState(() => _error = ApiErrorMessage.from(e));
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final authState = ref.watch(authProvider);

    // Navigate on success
    ref.listen<AuthState>(authProvider, (prev, next) {
      if (next.status == AuthStatus.authenticated) {
        context.go('/');
      }
      if (next.status == AuthStatus.error && next.errorMessage != null) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(next.errorMessage!),
            backgroundColor: AppColors.error,
            behavior: SnackBarBehavior.floating,
            shape: RoundedRectangleBorder(
              borderRadius: BorderRadius.circular(AppRadius.sm),
            ),
          ),
        );
        ref.read(authProvider.notifier).clearError();
      }
    });

    return Scaffold(
      backgroundColor: AppColors.surface,
      appBar: AppBar(
        backgroundColor: Colors.transparent,
        elevation: 0,
        leading: IconButton(
          icon: const Icon(
            Icons.arrow_back_ios_rounded,
            color: AppColors.textPrimary,
          ),
          onPressed: () => context.go('/login'),
        ),
      ),
      body: SafeArea(
        child: SingleChildScrollView(
          padding: const EdgeInsets.symmetric(horizontal: 24),
          child: Form(
            key: _formKey,
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const SizedBox(height: 8),

                // Title
                const Text(
                  'Tạo tài khoản',
                  style: TextStyle(
                    fontSize: 28,
                    fontWeight: FontWeight.w700,
                    color: AppColors.textPrimary,
                  ),
                ),
                const SizedBox(height: 8),
                Text(
                  _requestId == null
                      ? 'Điền thông tin và xác minh email để tạo tài khoản'
                      : 'Nếu thông tin phù hợp, mã 6 số sẽ được gửi tới ${_emailController.text.trim()}. Mã có hiệu lực 5 phút.',
                  style: TextStyle(
                    fontSize: 14,
                    color: AppColors.textSecondary,
                  ),
                ),
                const SizedBox(height: 32),

                if (_requestId == null) ...[
                  // Username
                  CareFlowTextField(
                    label: 'Tên đăng nhập',
                    hint: '3-50 ký tự: chữ, số, . _ -',
                    controller: _usernameController,
                    prefixIcon: Icons.person_outlined,
                    textInputAction: TextInputAction.next,
                    onFieldSubmitted: (_) => _emailFocus.requestFocus(),
                    validator: (value) {
                      if (value == null || value.isEmpty) {
                        return 'Vui lòng nhập tên đăng nhập';
                      }
                      if (!RegExp(r'^[A-Za-z0-9._-]{3,50}$').hasMatch(value)) {
                        return 'Tên đăng nhập không hợp lệ';
                      }
                      return null;
                    },
                  ),
                  const SizedBox(height: 16),

                  // Email
                  CareFlowTextField(
                    label: 'Email',
                    hint: 'Nhập email của bạn',
                    controller: _emailController,
                    keyboardType: TextInputType.emailAddress,
                    prefixIcon: Icons.email_outlined,
                    focusNode: _emailFocus,
                    textInputAction: TextInputAction.next,
                    onFieldSubmitted: (_) => _passwordFocus.requestFocus(),
                    validator: (value) {
                      if (value == null || value.isEmpty) {
                        return 'Vui lòng nhập email';
                      }
                      if (!RegExp(
                        r'^[^\s@]+@[^\s@]+\.[^\s@]+$',
                      ).hasMatch(value)) {
                        return 'Email không hợp lệ';
                      }
                      return null;
                    },
                  ),
                  const SizedBox(height: 16),

                  // Password
                  CareFlowTextField(
                    label: 'Mật khẩu',
                    hint: 'Tối thiểu 8 ký tự, đủ hoa/thường/số/ký tự đặc biệt',
                    controller: _passwordController,
                    obscureText: true,
                    prefixIcon: Icons.lock_outlined,
                    focusNode: _passwordFocus,
                    textInputAction: TextInputAction.next,
                    onFieldSubmitted: (_) =>
                        _confirmPasswordFocus.requestFocus(),
                    validator: (value) {
                      if (value == null || value.isEmpty) {
                        return 'Vui lòng nhập mật khẩu';
                      }
                      if (value.length < 8 ||
                          !RegExp(
                            r'(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[^A-Za-z0-9])',
                          ).hasMatch(value)) {
                        return 'Mật khẩu chưa đạt yêu cầu bảo mật';
                      }
                      return null;
                    },
                  ),
                  const SizedBox(height: 16),

                  // Confirm Password
                  CareFlowTextField(
                    label: 'Xác nhận mật khẩu',
                    hint: 'Nhập lại mật khẩu',
                    controller: _confirmPasswordController,
                    obscureText: true,
                    prefixIcon: Icons.lock_outlined,
                    focusNode: _confirmPasswordFocus,
                    textInputAction: TextInputAction.done,
                    onFieldSubmitted: (_) => _handleRegister(),
                    validator: (value) {
                      if (value == null || value.isEmpty) {
                        return 'Vui lòng xác nhận mật khẩu';
                      }
                      if (value != _passwordController.text) {
                        return 'Mật khẩu không khớp';
                      }
                      return null;
                    },
                  ),
                  const SizedBox(height: 32),
                ] else ...[
                  TextFormField(
                    controller: _codeController,
                    decoration: const InputDecoration(
                      labelText: 'Mã xác minh email',
                    ),
                    keyboardType: TextInputType.number,
                    autofillHints: const [AutofillHints.oneTimeCode],
                    inputFormatters: [
                      FilteringTextInputFormatter.digitsOnly,
                      LengthLimitingTextInputFormatter(6),
                    ],
                    validator: (value) =>
                        RegExp(r'^[0-9]{6}$').hasMatch(value ?? '')
                        ? null
                        : 'Nhập mã xác minh gồm 6 số',
                  ),
                  const SizedBox(height: 16),
                  TextButton(
                    onPressed:
                        _busy ||
                            (_resendAt != null &&
                                DateTime.now().isBefore(_resendAt!))
                        ? null
                        : _requestCode,
                    child: Text(
                      _resendAt != null && DateTime.now().isBefore(_resendAt!)
                          ? 'Gửi lại mã sau ${_resendAt!.difference(DateTime.now()).inSeconds + 1}s'
                          : 'Gửi lại mã',
                    ),
                  ),
                  TextButton(
                    onPressed: _busy
                        ? null
                        : () => setState(() {
                            _requestId = null;
                            _error = null;
                            _codeController.clear();
                          }),
                    child: const Text('Sửa thông tin đăng ký'),
                  ),
                ],
                if (_error != null)
                  Padding(
                    padding: const EdgeInsets.only(bottom: 12),
                    child: Text(
                      _error!,
                      style: const TextStyle(color: AppColors.error),
                    ),
                  ),
                // Register button
                CareFlowButton(
                  text: _requestId == null
                      ? 'Gửi mã xác minh email'
                      : 'Xác minh và tạo tài khoản',
                  useGradient: true,
                  isLoading: _busy || authState.status == AuthStatus.loading,
                  onPressed: _handleRegister,
                ),
                const SizedBox(height: 24),

                // Login link
                Row(
                  mainAxisAlignment: MainAxisAlignment.center,
                  children: [
                    Text(
                      'Đã có tài khoản? ',
                      style: TextStyle(
                        color: AppColors.textSecondary,
                        fontSize: 14,
                      ),
                    ),
                    GestureDetector(
                      onTap: () => context.go('/login'),
                      child: const Text(
                        'Đăng nhập',
                        style: TextStyle(
                          color: AppColors.primary,
                          fontWeight: FontWeight.w600,
                          fontSize: 14,
                        ),
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 32),
              ],
            ),
          ),
        ),
      ),
    );
  }
}
