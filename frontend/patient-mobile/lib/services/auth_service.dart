import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../config/api_config.dart';
import 'api_service.dart';

/// Auth response model
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
    return AuthResponse(
      token: json['token'] ?? '',
      refreshToken: json['refreshToken'],
      userId: json['userId'] ?? '',
      email: json['email'] ?? '',
      fullName: json['fullName'] ?? '',
    );
  }
}

/// Authentication service — handles login, register, logout.
/// Falls back to mock when API is unavailable.
class AuthService {
  final ApiService _apiService;
  bool _useMock = true; // Set to false when Identity Service API is ready

  AuthService(this._apiService);

  /// Toggle mock mode
  void setUseMock(bool value) => _useMock = value;

  /// Login with email and password
  Future<AuthResponse> login(String email, String password) async {
    if (_useMock) {
      return _mockLogin(email, password);
    }

    try {
      final response = await _apiService.post(
        ApiConfig.authLogin,
        data: {'email': email, 'password': password},
      );

      final authResponse = AuthResponse.fromJson(response.data['data']);
      await _apiService.saveToken(authResponse.token);
      if (authResponse.refreshToken != null) {
        await _apiService.saveRefreshToken(authResponse.refreshToken!);
      }
      return authResponse;
    } catch (e) {
      // Fallback to mock if API unreachable
      return _mockLogin(email, password);
    }
  }

  /// Register new account
  Future<AuthResponse> register({
    required String fullName,
    required String email,
    required String phone,
    required String password,
  }) async {
    if (_useMock) {
      return _mockRegister(fullName, email);
    }

    try {
      final response = await _apiService.post(
        ApiConfig.authRegister,
        data: {
          'fullName': fullName,
          'email': email,
          'phone': phone,
          'password': password,
        },
      );

      final authResponse = AuthResponse.fromJson(response.data['data']);
      await _apiService.saveToken(authResponse.token);
      if (authResponse.refreshToken != null) {
        await _apiService.saveRefreshToken(authResponse.refreshToken!);
      }
      return authResponse;
    } catch (e) {
      return _mockRegister(fullName, email);
    }
  }

  /// Logout
  Future<void> logout() async {
    await _apiService.clearTokens();
  }

  /// Check if user is authenticated
  Future<bool> isAuthenticated() async {
    return await _apiService.hasToken();
  }

  // --- Mock implementations ---

  Future<AuthResponse> _mockLogin(String email, String password) async {
    // Simulate network delay
    await Future.delayed(const Duration(milliseconds: 800));

    if (email.isEmpty || password.isEmpty) {
      throw Exception('Email và mật khẩu không được để trống');
    }

    final mockResponse = AuthResponse(
      token: 'mock_jwt_token_${DateTime.now().millisecondsSinceEpoch}',
      refreshToken: 'mock_refresh_token',
      userId: 'mock-user-001',
      email: email,
      fullName: 'Nguyễn Văn A',
    );

    await _apiService.saveToken(mockResponse.token);
    return mockResponse;
  }

  Future<AuthResponse> _mockRegister(String fullName, String email) async {
    await Future.delayed(const Duration(milliseconds: 1000));

    final mockResponse = AuthResponse(
      token: 'mock_jwt_token_${DateTime.now().millisecondsSinceEpoch}',
      refreshToken: 'mock_refresh_token',
      userId: 'mock-user-${DateTime.now().millisecondsSinceEpoch}',
      email: email,
      fullName: fullName,
    );

    await _apiService.saveToken(mockResponse.token);
    return mockResponse;
  }
}

/// Riverpod provider for AuthService
final authServiceProvider = Provider<AuthService>((ref) {
  final apiService = ref.read(apiServiceProvider);
  return AuthService(apiService);
});
