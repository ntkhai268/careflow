import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../services/auth_service.dart';

/// Authentication state
enum AuthStatus { initial, loading, authenticated, unauthenticated, error }

class AuthState {
  final AuthStatus status;
  final String? userId;
  final String? email;
  final String? fullName;
  final String? errorMessage;

  const AuthState({
    this.status = AuthStatus.initial,
    this.userId,
    this.email,
    this.fullName,
    this.errorMessage,
  });

  AuthState copyWith({
    AuthStatus? status,
    String? userId,
    String? email,
    String? fullName,
    String? errorMessage,
  }) {
    return AuthState(
      status: status ?? this.status,
      userId: userId ?? this.userId,
      email: email ?? this.email,
      fullName: fullName ?? this.fullName,
      errorMessage: errorMessage,
    );
  }
}

/// Auth state notifier using Riverpod
class AuthNotifier extends StateNotifier<AuthState> {
  final AuthService _authService;

  AuthNotifier(this._authService) : super(const AuthState());

  /// Check if user is already logged in (app startup)
  Future<void> checkAuthStatus() async {
    state = state.copyWith(status: AuthStatus.loading);
    try {
      final isAuth = await _authService.isAuthenticated();
      if (isAuth) {
        state = state.copyWith(
          status: AuthStatus.authenticated,
          fullName: 'Người dùng', // Will be fetched from API later
        );
      } else {
        state = state.copyWith(status: AuthStatus.unauthenticated);
      }
    } catch (e) {
      state = state.copyWith(status: AuthStatus.unauthenticated);
    }
  }

  /// Login
  Future<void> login(String email, String password) async {
    state = state.copyWith(status: AuthStatus.loading);
    try {
      final response = await _authService.login(email, password);
      state = state.copyWith(
        status: AuthStatus.authenticated,
        userId: response.userId,
        email: response.email,
        fullName: response.fullName,
      );
    } catch (e) {
      state = state.copyWith(
        status: AuthStatus.error,
        errorMessage: e.toString(),
      );
    }
  }

  /// Register
  Future<void> register({
    required String fullName,
    required String email,
    required String phone,
    required String password,
  }) async {
    state = state.copyWith(status: AuthStatus.loading);
    try {
      final response = await _authService.register(
        fullName: fullName,
        email: email,
        phone: phone,
        password: password,
      );
      state = state.copyWith(
        status: AuthStatus.authenticated,
        userId: response.userId,
        email: response.email,
        fullName: response.fullName,
      );
    } catch (e) {
      state = state.copyWith(
        status: AuthStatus.error,
        errorMessage: e.toString(),
      );
    }
  }

  /// Logout
  Future<void> logout() async {
    await _authService.logout();
    state = const AuthState(status: AuthStatus.unauthenticated);
  }

  /// Clear error
  void clearError() {
    state = state.copyWith(status: AuthStatus.unauthenticated);
  }
}

/// Auth state provider
final authProvider = StateNotifierProvider<AuthNotifier, AuthState>((ref) {
  final authService = ref.read(authServiceProvider);
  return AuthNotifier(authService);
});
