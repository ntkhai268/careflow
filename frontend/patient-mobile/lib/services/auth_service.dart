import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../config/api_config.dart';
import 'api_service.dart';

/// Auth response model — maps to Identity Service's LoginResponse.
class AuthResponse {
  final String token;
  final String? refreshToken;
  final String userId;
  final String email;
  final String fullName;

  AuthResponse({
    required this.token,
    this.refreshToken,
    required this.userId,
    required this.email,
    required this.fullName,
  });

  factory AuthResponse.fromJson(Map<String, dynamic> json) {
    final user = json['user'];
    if (json['accessToken'] is! String || user is! Map<String, dynamic>) {
      throw const FormatException('Identity API response không đúng contract');
    }
    return AuthResponse(
      token: json['accessToken'] as String,
      refreshToken: json['refreshToken'] as String?,
      userId: user['id'] as String? ?? '',
      email: user['email'] as String? ?? '',
      fullName: user['username'] as String? ?? '',
    );
  }
}

/// User info model — maps to Identity Service's UserResponse from /auth/me.
class UserInfo {
  final String id;
  final String username;
  final String email;
  final String role;

  UserInfo({
    required this.id,
    required this.username,
    required this.email,
    required this.role,
  });

  factory UserInfo.fromJson(Map<String, dynamic> json) {
    return UserInfo(
      id: json['id'] as String? ?? '',
      username: json['username'] as String? ?? '',
      email: json['email'] as String? ?? '',
      role: json['role'] as String? ?? 'PATIENT',
    );
  }
}

/// Authentication service — handles login, register, logout, token verification.
/// Mock mode is opt-in with --dart-define=USE_MOCK_AUTH=true.
class AuthService {
  final ApiService _apiService;
  final bool _useMock;

  AuthService(
    this._apiService, {
    bool useMock = const bool.fromEnvironment(
      'USE_MOCK_AUTH',
      defaultValue: false,
    ),
  }) : _useMock = useMock;

  /// Login with username/email and password.
  Future<AuthResponse> login(String usernameOrEmail, String password) async {
    if (_useMock) {
      return _mockLogin(usernameOrEmail, password);
    }

    try {
      final response = await _apiService.post(
        ApiConfig.authLogin,
        data: {'usernameOrEmail': usernameOrEmail, 'password': password},
      );

      final authResponse = AuthResponse.fromJson(response.data['data']);
      await _apiService.saveToken(authResponse.token);
      if (authResponse.refreshToken != null) {
        await _apiService.saveRefreshToken(authResponse.refreshToken!);
      }
      return authResponse;
    } catch (e) {
      throw Exception(_messageFrom(e));
    }
  }

  /// Register Identity account, then login to obtain the token pair.
  Future<AuthResponse> register({
    required String username,
    required String email,
    required String password,
  }) async {
    if (_useMock) {
      return _mockRegister(username, email);
    }

    try {
      await _apiService.post(
        ApiConfig.authRegister,
        data: {'username': username, 'email': email, 'password': password},
      );
      return await login(username, password);
    } catch (e) {
      throw Exception(_messageFrom(e));
    }
  }

  /// Fetch current user info by verifying the stored JWT via /auth/me.
  /// Returns null if token is invalid or expired (after refresh attempt).
  Future<UserInfo?> fetchCurrentUser() async {
    if (_useMock) {
      return UserInfo(
        id: 'mock-user-001',
        username: 'Người dùng',
        email: 'mock@careflow.vn',
        role: 'PATIENT',
      );
    }

    try {
      final response = await _apiService.get(ApiConfig.authMe);
      final data = response.data['data'];
      if (data == null) return null;
      return UserInfo.fromJson(data as Map<String, dynamic>);
    } catch (e) {
      // Token invalid or refresh failed — user must re-login
      return null;
    }
  }

  /// Logout — revoke refresh token on server then clear local storage.
  Future<void> logout() async {
    try {
      final refreshToken = await _apiService.getRefreshToken();
      if (!_useMock && refreshToken != null && refreshToken.isNotEmpty) {
        await _apiService.post(
          ApiConfig.authLogout,
          data: {'refreshToken': refreshToken},
        );
      }
    } finally {
      await _apiService.clearTokens();
    }
  }

  /// Check if user is authenticated (local token check only).
  Future<bool> isAuthenticated() async {
    return await _apiService.hasToken();
  }

  // --- Mock implementations ---

  Future<AuthResponse> _mockLogin(
    String usernameOrEmail,
    String password,
  ) async {
    await Future.delayed(const Duration(milliseconds: 800));

    if (usernameOrEmail.isEmpty || password.isEmpty) {
      throw Exception('Tên đăng nhập/email và mật khẩu không được để trống');
    }

    final mockResponse = AuthResponse(
      token: 'mock_jwt_token_${DateTime.now().millisecondsSinceEpoch}',
      refreshToken: 'mock_refresh_token',
      userId: 'mock-user-001',
      email: usernameOrEmail.contains('@')
          ? usernameOrEmail
          : '$usernameOrEmail@example.com',
      fullName: usernameOrEmail,
    );

    await _apiService.saveToken(mockResponse.token);
    return mockResponse;
  }

  Future<AuthResponse> _mockRegister(String username, String email) async {
    await Future.delayed(const Duration(milliseconds: 1000));

    final mockResponse = AuthResponse(
      token: 'mock_jwt_token_${DateTime.now().millisecondsSinceEpoch}',
      refreshToken: 'mock_refresh_token',
      userId: 'mock-user-${DateTime.now().millisecondsSinceEpoch}',
      email: email,
      fullName: username,
    );

    await _apiService.saveToken(mockResponse.token);
    return mockResponse;
  }

  String _messageFrom(Object error) {
    if (error is DioException) {
      final data = error.response?.data;
      if (data is Map<String, dynamic> && data['message'] is String) {
        return data['message'] as String;
      }
      return 'Không thể kết nối Identity Service';
    }
    return error.toString();
  }
}

/// Riverpod provider for AuthService
final authServiceProvider = Provider<AuthService>((ref) {
  final apiService = ref.read(apiServiceProvider);
  return AuthService(apiService);
});
