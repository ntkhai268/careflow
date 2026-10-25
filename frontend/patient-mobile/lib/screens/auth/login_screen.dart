import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../../config/theme.dart';
import '../../providers/auth_provider.dart';
import '../../services/api_service.dart';
import '../../services/biometric_session_store.dart';
import '../../widgets/careflow_button.dart';
import '../../widgets/careflow_text_field.dart';

/// Login screen with username/email and password.
class LoginScreen extends ConsumerStatefulWidget {
  const LoginScreen({super.key});

  @override
  ConsumerState<LoginScreen> createState() => _LoginScreenState();
}

class _LoginScreenState extends ConsumerState<LoginScreen> {
  final _formKey = GlobalKey<FormState>();
  final _emailController = TextEditingController();
  final _passwordController = TextEditingController();
  final _accountFocus = FocusNode();
  final _passwordFocus = FocusNode();

  String? _biometricAccount;
  BiometricCapability? _biometricCapability;
  bool _allowAccountPrefill = true;

  bool get _canUseBiometrics =>
      _biometricAccount != null &&
      _biometricCapability?.available == true &&
      _emailController.text.trim().toLowerCase() ==
          _biometricAccount!.trim().toLowerCase();

  @override
  void initState() {
    super.initState();
    _loadBiometrics();
  }

  Future<void> _loadBiometrics() async {
    final sessions = ref.read(apiServiceProvider).sessions;
    try {
      final account = await sessions.account();
      final capability = await sessions.vault.capability();
      if (mounted) {
        setState(() {
          _biometricAccount = account;
          _biometricCapability = capability;
          if (_allowAccountPrefill && _emailController.text.isEmpty) {
            _emailController.text = account ?? '';
          }
          _allowAccountPrefill = false;
        });
      }
    } catch (_) {
      /* Password and question login remain available. */
    }
  }

  @override
  void dispose() {
    _emailController.dispose();
    _passwordController.dispose();
    _accountFocus.dispose();
    _passwordFocus.dispose();
    super.dispose();
  }

  void _handleLogin() {
    if (_formKey.currentState?.validate() ?? false) {
      ref
          .read(authProvider.notifier)
          .login(_emailController.text.trim(), _passwordController.text);
    }
  }

  void _changeAccount(String value) {
    setState(() {
      _allowAccountPrefill = false;
      _passwordController.clear();
    });
  }

  void _useAnotherAccount() {
    _emailController.clear();
    _changeAccount('');
    _accountFocus.requestFocus();
  }

  void _handleBiometricLogin() {
    if (!_canUseBiometrics ||
        ref.read(authProvider).status == AuthStatus.loading) {
      return;
    }
    FocusScope.of(context).unfocus();
    ref.read(authProvider.notifier).alternativeLogin('biometric', '', '', '');
  }

  @override
  Widget build(BuildContext context) {
    final authState = ref.watch(authProvider);
    final isLoading = authState.status == AuthStatus.loading;
    final showBiometrics = _canUseBiometrics;

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
        _loadBiometrics();
      }
    });

    return Scaffold(
      backgroundColor: AppColors.surface,
      body: SafeArea(
        child: SingleChildScrollView(
          padding: const EdgeInsets.symmetric(horizontal: 24),
          child: Form(
            key: _formKey,
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.center,
              children: [
                const SizedBox(height: 60),

                // Logo
                Container(
                  width: 80,
                  height: 80,
                  decoration: BoxDecoration(
                    gradient: AppColors.primaryGradient,
                    borderRadius: BorderRadius.circular(AppRadius.xl),
                    boxShadow: [
                      BoxShadow(
                        color: AppColors.primary.withValues(alpha: 0.3),
                        blurRadius: 16,
                        offset: const Offset(0, 6),
                      ),
                    ],
                  ),
                  child: const Icon(
                    Icons.local_hospital_rounded,
                    size: 42,
                    color: Colors.white,
                  ),
                ),
                const SizedBox(height: 20),

                // App name
                const Text(
                  'CareFlow',
                  style: TextStyle(
                    fontSize: 28,
                    fontWeight: FontWeight.w700,
                    color: AppColors.primary,
                    letterSpacing: 0.5,
                  ),
                ),
                const SizedBox(height: 4),
                Text(
                  'Đặt lịch khám bệnh',
                  style: TextStyle(
                    fontSize: 14,
                    color: AppColors.textSecondary,
                  ),
                ),
                const SizedBox(height: 48),

                // Title
                const Align(
                  alignment: Alignment.centerLeft,
                  child: Text(
                    'Đăng nhập',
                    style: TextStyle(
                      fontSize: 24,
                      fontWeight: FontWeight.w700,
                      color: AppColors.textPrimary,
                    ),
                  ),
                ),
                const SizedBox(height: 8),
                Align(
                  alignment: Alignment.centerLeft,
                  child: Text(
                    'Chào mừng bạn trở lại!',
                    style: TextStyle(
                      fontSize: 14,
                      color: AppColors.textSecondary,
                    ),
                  ),
                ),
                const SizedBox(height: 32),

                // Username or email
                CareFlowTextField(
                  label: 'Tên đăng nhập hoặc email',
                  hint: 'Nhập tên đăng nhập hoặc email',
                  controller: _emailController,
                  focusNode: _accountFocus,
                  enabled: !isLoading,
                  onChanged: _changeAccount,
                  keyboardType: TextInputType.emailAddress,
                  prefixIcon: Icons.email_outlined,
                  textInputAction: TextInputAction.next,
                  onFieldSubmitted: (_) => _passwordFocus.requestFocus(),
                  validator: (value) {
                    if (value == null || value.isEmpty) {
                      return 'Vui lòng nhập tên đăng nhập hoặc email';
                    }
                    return null;
                  },
                ),
                if (showBiometrics)
                  Align(
                    alignment: Alignment.centerRight,
                    child: TextButton(
                      onPressed: isLoading ? null : _useAnotherAccount,
                      child: const Text('Dùng tài khoản khác'),
                    ),
                  )
                else
                  const SizedBox(height: 20),

                // Password
                Align(
                  alignment: Alignment.centerLeft,
                  child: Text(
                    'Mật khẩu',
                    style: Theme.of(context).textTheme.titleMedium?.copyWith(
                      fontWeight: FontWeight.w500,
                      color: AppColors.textPrimary,
                      fontSize: 14,
                    ),
                  ),
                ),
                const SizedBox(height: 8),
                Row(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Expanded(
                      child: CareFlowTextField(
                        hint: 'Nhập mật khẩu',
                        controller: _passwordController,
                        enabled: !isLoading,
                        obscureText: true,
                        prefixIcon: Icons.lock_outlined,
                        focusNode: _passwordFocus,
                        textInputAction: TextInputAction.done,
                        onFieldSubmitted: (_) => _handleLogin(),
                        validator: (value) {
                          if (value == null || value.isEmpty) {
                            return 'Vui lòng nhập mật khẩu';
                          }
                          if (value.length < 8) {
                            return 'Mật khẩu phải có ít nhất 8 ký tự';
                          }
                          return null;
                        },
                      ),
                    ),
                    if (showBiometrics) ...[
                      const SizedBox(width: 12),
                      IconButton.filledTonal(
                        key: const ValueKey('biometric-login'),
                        tooltip:
                            'Đăng nhập bằng ${_biometricCapability!.label}',
                        onPressed: isLoading ? null : _handleBiometricLogin,
                        style: IconButton.styleFrom(
                          fixedSize: const Size(56, 56),
                          backgroundColor: AppColors.primarySurface,
                          foregroundColor: AppColors.primary,
                          side: BorderSide(
                            color: AppColors.primary.withValues(alpha: 0.2),
                          ),
                          shape: RoundedRectangleBorder(
                            borderRadius: BorderRadius.circular(AppRadius.md),
                          ),
                        ),
                        icon: Icon(
                          _biometricCapability!.label == 'Face ID'
                              ? Icons.face_outlined
                              : Icons.fingerprint_rounded,
                          size: 30,
                        ),
                      ),
                    ],
                  ],
                ),
                const SizedBox(height: 12),

                // Forgot password
                Align(
                  alignment: Alignment.centerRight,
                  child: TextButton(
                    onPressed: () {
                      context.go('/login/recovery');
                    },
                    child: const Text('Quên mật khẩu?'),
                  ),
                ),
                const SizedBox(height: 24),

                // Login button
                CareFlowButton(
                  text: 'Đăng nhập',
                  useGradient: true,
                  isLoading: authState.status == AuthStatus.loading,
                  onPressed: _handleLogin,
                ),
                const SizedBox(height: 24),

                // Register link
                Wrap(
                  alignment: WrapAlignment.center,
                  crossAxisAlignment: WrapCrossAlignment.center,
                  children: [
                    Text(
                      'Chưa có tài khoản? ',
                      style: TextStyle(
                        color: AppColors.textSecondary,
                        fontSize: 14,
                      ),
                    ),
                    GestureDetector(
                      onTap: () => context.go('/register'),
                      child: const Text(
                        'Đăng ký',
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
