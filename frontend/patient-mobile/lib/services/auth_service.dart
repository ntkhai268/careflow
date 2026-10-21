import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:dio/dio.dart';
import '../config/api_config.dart';
import '../utils/api_error_message.dart';
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

      var authResponse = AuthResponse.fromJson(response.data['data']);
      await _acceptSession(authResponse);
      final upgraded = await _apiService.upgradeDeviceKey(password: password);
      if (upgraded != null) {
        authResponse = AuthResponse.fromJson(upgraded);
        await _acceptSession(authResponse);
      }
      return authResponse;
    } catch (e) {
      throw Exception(_messageFrom(e, login: true));
    }
  }

  Future<Map<String, dynamic>> loginMethods() async {
    final response = await _apiService.get('/auth/login-methods');
    return Map<String, dynamic>.from(response.data['data']);
  }

  Future<void> saveQuestion(
    String password,
    String questionId,
    String answer,
  ) async {
    await _apiService.post(
      '/auth/login-methods/question',
      data: {'password': password, 'questionId': questionId, 'answer': answer},
    );
  }

  Future<void> questionLogin(
    String username,
    String questionId,
    String answer,
  ) async {
    final response = await _apiService.post(
      '/auth/login/question',
      data: {
        'usernameOrEmail': username,
        'questionId': questionId,
        'answer': answer,
      },
    );
    final auth = AuthResponse.fromJson(
      Map<String, dynamic>.from(response.data['data']),
    );
    await _acceptSession(auth);
  }

  Future<void> _acceptSession(AuthResponse auth) async {
    final account = await _apiService.sessions.account();
    if (account != null && account != auth.fullName) {
      await _apiService.clearTokens();
    } else {
      await _apiService.clearSession();
    }
    await _apiService.saveToken(auth.token);
    if (auth.refreshToken != null) {
      await _apiService.saveRefreshToken(auth.refreshToken!);
    }
  }

  Future<Map<String, dynamic>> requestEmailRecovery(String email) async {
    final response = await _apiService.post(
      '/auth/recovery/email/request',
      data: {'email': email.trim().toLowerCase()},
      options: Options(extra: {'anonymous': true}),
    );
    return Map<String, dynamic>.from(response.data['data']);
  }

  Future<Map<String, dynamic>> verifyEmailRecovery(
    String requestId,
    String code,
  ) async {
    final response = await _apiService.post(
      '/auth/recovery/email/verify',
      data: {'requestId': requestId, 'code': code},
      options: Options(extra: {'anonymous': true}),
    );
    return Map<String, dynamic>.from(response.data['data']);
  }

  Future<void> confirmEmailRecovery(
    String requestId,
    String resetToken,
    String newPassword,
  ) async {
    await _apiService.post(
      '/auth/recovery/email/confirm',
      data: {
        'requestId': requestId,
        'resetToken': resetToken,
        'newPassword': newPassword,
      },
      options: Options(extra: {'anonymous': true}),
    );
    await _apiService.clearTokens();
  }

  /// An authenticated settings visit can discard a registration the server has
  /// revoked, while preserving the current password session. Network failures
  /// must leave the registration untouched.
  Future<void> reconcileBiometrics() async {
    final id = await _apiService.sessions.credentialId();
    if (id == null) return;
    final response = await _apiService.get('/auth/devices');
    final devices = response.data['data'];
    if (devices is! List) throw const FormatException('Invalid device list');
    if (devices.any((device) => device is Map && device['id'] == id)) return;
    if (await _apiService.sessions.credentialId() == id) {
      await _apiService.sessions.disable();
    }
  }

  Future<void> enableBiometrics(String password) async {
    final user = await fetchCurrentUser();
    if (user == null) throw Exception('Vui lòng đăng nhập lại để thiết lập.');
    if (await _apiService.sessions.enabled()) {
      throw Exception('Sinh trắc học đã bật trên điện thoại này.');
    }
    final supported = await _apiService.sessions.vault.capability();
    if (!supported.available) throw Exception(supported.detail);
    final access = await _apiService.getToken();
    final refresh = await _apiService.getRefreshToken();
    if (access == null || refresh == null) {
      throw Exception('Vui lòng đăng nhập lại để thiết lập.');
    }
    final response = await _apiService.post(
      '/auth/biometric-devices',
      data: {'password': password},
    );
    final registration = Map<String, dynamic>.from(response.data['data']);
    try {
      await _apiService.sessions.enable(
        user.username,
        access,
        refresh,
        credentialId: registration['credentialId'] as String,
        deviceToken: registration['deviceToken'] as String,
      );
    } catch (_) {
      try {
        await _apiService.delete(
          '/auth/biometric-devices/${registration['credentialId']}',
        );
      } catch (_) {
        /* The unused random credential was not enabled locally. */
      }
      rethrow;
    }
  }

  Future<void> disableBiometrics() async {
    final id = await _apiService.sessions.credentialId();
    if (id != null) await _apiService.delete('/auth/biometric-devices/$id');
    await _apiService.sessions.disable();
  }

  Future<void> biometricLogin() async {
    final expected = await _apiService.sessions.account();
    _apiService.sessions.lock();
    try {
      final credential = await _apiService.sessions.unlockCredential();
      Map<String, dynamic> data;
      try {
        final response = await _apiService.post(
          '/auth/login/biometric',
          data: {
            'credentialId': credential.id,
            'deviceToken': credential.token,
          },
          options: Options(extra: {'anonymous': true}),
        );
        data = Map<String, dynamic>.from(response.data['data']);
      } on DioException catch (error) {
        final recovered = error.response?.statusCode == 401
            ? await _apiService.recoverPendingDeviceKey(
                credential.id,
                credential.token,
              )
            : null;
        if (recovered == null) rethrow;
        data = recovered;
      }
      final auth = AuthResponse.fromJson(data);
      if (auth.fullName != expected ||
          (data['user'] as Map)['role'] != 'PATIENT') {
        try {
          await _apiService.post(
            ApiConfig.authLogout,
            data: {'refreshToken': auth.refreshToken},
            options: Options(extra: {'anonymous': true}),
          );
        } finally {
          await _apiService.clearTokens();
        }
        throw Exception(
          'Tài khoản không khớp thiết lập trên điện thoại. Vui lòng đăng nhập lại.',
        );
      }
      await _acceptSession(auth);
      final upgraded = await _apiService.upgradeDeviceKey(
        credentialId: credential.id,
        deviceToken: credential.token,
      );
      if (upgraded != null) {
        await _acceptSession(AuthResponse.fromJson(upgraded));
      }
    } on DioException catch (error) {
      _apiService.sessions.lock();
      if (error.response?.statusCode == 401 ||
          error.response?.statusCode == 403) {
        await _apiService.clearTokens();
        throw Exception(
          'Thiết lập sinh trắc học đã hết hạn hoặc bị thu hồi. Đăng nhập bằng mật khẩu và bật lại.',
        );
      }
      rethrow;
    } catch (_) {
      _apiService.sessions.lock();
      rethrow;
    }
  }

  Future<void> lockSession() async {
    await _apiService.clearSession();
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

  /// Logout ends this session while retaining OS-protected device enrollment.
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
      await _apiService.clearSession();
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

  String _messageFrom(Object error, {bool login = false}) {
    return ApiErrorMessage.from(
      error,
      fallback: login
          ? 'Đăng nhập thất bại. Vui lòng kiểm tra thông tin và thử lại.'
          : 'Đăng ký thất bại. Vui lòng kiểm tra thông tin và thử lại.',
      unauthorized: login
          ? 'Tên đăng nhập hoặc mật khẩu không đúng.'
          : 'Tài khoản chưa được phép thực hiện thao tác này.',
    );
  }
}

/// Riverpod provider for AuthService
final authServiceProvider = Provider<AuthService>((ref) {
  final apiService = ref.read(apiServiceProvider);
  return AuthService(apiService);
});
