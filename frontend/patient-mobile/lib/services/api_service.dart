import 'package:dio/dio.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../config/api_config.dart';

/// Core API service using Dio with JWT interceptor.
class ApiService {
  late final Dio _dio;
  late final Dio _refreshDio;
  final FlutterSecureStorage _storage = const FlutterSecureStorage();

  static const String _tokenKey = 'jwt_token';
  static const String _refreshTokenKey = 'refresh_token';

  ApiService() {
    final options = BaseOptions(
      baseUrl: ApiConfig.baseUrl,
      connectTimeout: const Duration(seconds: 15),
      receiveTimeout: const Duration(seconds: 15),
      headers: {
        'Content-Type': 'application/json',
        'Accept': 'application/json',
      },
    );
    _dio = Dio(options);
    _refreshDio = Dio(options);

    _dio.interceptors.add(
      InterceptorsWrapper(
        onRequest: (options, handler) async {
          final token = await getToken();
          if (token != null) {
            options.headers['Authorization'] = 'Bearer $token';
          }
          handler.next(options);
        },
        onError: (error, handler) async {
          final request = error.requestOptions;
          final isAuthEndpoint =
              request.path == ApiConfig.authLogin ||
              request.path == ApiConfig.authRegister ||
              request.path == ApiConfig.authLogout ||
              request.path == ApiConfig.authRefresh;
          final alreadyRetried = request.extra['authRetried'] == true;
          if (error.response?.statusCode == 401 &&
              !isAuthEndpoint &&
              !alreadyRetried) {
            final refreshToken = await getRefreshToken();
            if (refreshToken != null && refreshToken.isNotEmpty) {
              try {
                final refreshResponse = await _refreshDio.post(
                  ApiConfig.authRefresh,
                  data: {'refreshToken': refreshToken},
                );
                final data = refreshResponse.data['data'];
                final accessToken = data['accessToken'] as String;
                final rotatedRefreshToken = data['refreshToken'] as String;
                await saveToken(accessToken);
                await saveRefreshToken(rotatedRefreshToken);
                request.headers['Authorization'] = 'Bearer $accessToken';
                request.extra['authRetried'] = true;
                handler.resolve(await _dio.fetch(request));
                return;
              } catch (_) {
                await clearTokens();
              }
            } else {
              await clearTokens();
            }
          }
          handler.next(error);
        },
      ),
    );
  }

  // Token management
  Future<void> saveToken(String token) async {
    await _storage.write(key: _tokenKey, value: token);
  }

  Future<void> saveRefreshToken(String token) async {
    await _storage.write(key: _refreshTokenKey, value: token);
  }

  Future<String?> getToken() async {
    return await _storage.read(key: _tokenKey);
  }

  Future<String?> getRefreshToken() async {
    return await _storage.read(key: _refreshTokenKey);
  }

  Future<void> clearTokens() async {
    await _storage.delete(key: _tokenKey);
    await _storage.delete(key: _refreshTokenKey);
  }

  Future<bool> hasToken() async {
    final token = await getToken();
    return token != null && token.isNotEmpty;
  }

  // HTTP methods
  Future<Response> get(
    String endpoint, {
    Map<String, dynamic>? queryParams,
  }) async {
    return await _dio.get(endpoint, queryParameters: queryParams);
  }

  Future<Response> post(String endpoint, {dynamic data}) async {
    return await _dio.post(endpoint, data: data);
  }

  Future<Response> put(String endpoint, {dynamic data}) async {
    return await _dio.put(endpoint, data: data);
  }

  Future<Response> delete(String endpoint) async {
    return await _dio.delete(endpoint);
  }
}

/// Global provider for ApiService
final apiServiceProvider = Provider<ApiService>((ref) {
  return ApiService();
});
