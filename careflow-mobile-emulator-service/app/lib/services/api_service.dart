import 'package:dio/dio.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../config/api_config.dart';
import '../config/device_config.dart';
import '../platform/browser_bridge.dart';

/// Core API service using Dio with JWT interceptor.
class ApiService {
  late final Dio _dio;
  late final Dio _refreshDio;
  Future<void>? _refreshing;
  final FlutterSecureStorage _storage = DeviceConfig.namespace == null
      ? const FlutterSecureStorage()
      : FlutterSecureStorage(
          webOptions: WebOptions(
            publicKey: '${DeviceConfig.namespace}.secure',
            dbName: '${DeviceConfig.namespace}.secure',
          ),
        );

  static const String _tokenKey = 'jwt_token';
  static const String _refreshTokenKey = 'refresh_token';

  ApiService({Dio? dio, Dio? refreshDio}) {
    final options = BaseOptions(
      baseUrl: ApiConfig.baseUrl,
      connectTimeout: const Duration(seconds: 15),
      receiveTimeout: const Duration(seconds: 15),
      headers: {
        'Content-Type': 'application/json',
        'Accept': 'application/json',
        if (DeviceConfig.deviceId != null)
          'X-Emulator-Device': DeviceConfig.deviceId!,
      },
    );
    _dio = dio ?? Dio(options);
    _refreshDio = refreshDio ?? Dio(options);
    _refreshDio.interceptors.add(InterceptorsWrapper(onRequest: (options, handler) async {
      try {
        options.headers['DPoP'] = await browserCall('dpop', {'method': options.method, 'path': options.uri.path, 'token': ''});
        handler.next(options);
      } catch (error) { handler.reject(DioException(requestOptions: options, error: error)); }
    }));

    _dio.interceptors.add(
      InterceptorsWrapper(
        onRequest: (options, handler) async {
          final token = await getToken();
          options.headers.remove('Authorization');
          if (token != null) {
            options.headers['Authorization'] = 'Bearer $token';
            await browserCall('session', {'token': token});
          }
          try {
            options.headers['DPoP'] = await browserCall('dpop', {'method': options.method, 'path': options.uri.path, 'token': token ?? ''});
            handler.next(options);
          } catch (error) {
            handler.reject(DioException(requestOptions: options, error: error));
          }
        },
        onError: (error, handler) async {
          final request = error.requestOptions;
          final isAuthEndpoint =
              request.path == ApiConfig.authLogin ||
              request.path == ApiConfig.authRegister ||
              request.path == ApiConfig.authLogout ||
              request.path == ApiConfig.authRefresh ||
              (request.method == 'POST' && (request.path.startsWith('/auth/login/') ||
              request.path.startsWith('/auth/recovery/') ||
              request.path.startsWith('/auth/passkeys/') ||
              request.path.startsWith('/auth/login-methods')));
          final alreadyRetried = request.extra['authRetried'] == true;
          if (error.response?.statusCode == 401 &&
              !isAuthEndpoint &&
              !alreadyRetried) {
            try {
              final failedAuthorization = request.headers['Authorization'];
              final currentToken = await getToken();
              if (currentToken == null) {
                handler.next(error);
                return;
              }
              // A later request may fail with the pre-rotation token after another
              // request has already completed refresh. Reuse the new pair.
              if (failedAuthorization == 'Bearer $currentToken') {
                final pending = _refreshing ??= _rotateTokens();
                try {
                  await pending;
                } finally {
                  if (identical(_refreshing, pending)) _refreshing = null;
                }
              }
            } catch (_) {
              handler.next(error);
              return;
            }
            request.extra['authRetried'] = true;
            try {
              handler.resolve(await _dio.fetch(request));
            } on DioException catch (retryError) {
              // A resource failure must not erase a successfully refreshed pair.
              handler.next(retryError);
            }
            return;
          }
          handler.next(error);
        },
      ),
    );
  }

  Future<void> _rotateTokens() async {
    final refreshToken = await getRefreshToken();
    if (refreshToken == null || refreshToken.isEmpty) {
      await clearTokens();
      throw StateError('Missing refresh token');
    }
    try {
      final response = await _refreshDio.post(
        ApiConfig.authRefresh,
        data: {'refreshToken': refreshToken},
      );
      final data = response.data['data'];
      await saveToken(data['accessToken'] as String);
      await saveRefreshToken(data['refreshToken'] as String);
    } on DioException catch (error) {
      if (error.response?.statusCode == 401 || error.response?.statusCode == 403) {
        await clearTokens();
      }
      rethrow;
    }
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
    await browserCall('session', {'token': ''});
  }

  Future<bool> hasToken() async {
    final token = await getToken();
    return token != null && token.isNotEmpty;
  }

  // HTTP methods
  Future<Response> get(
    String endpoint, {
    Map<String, dynamic>? queryParams,
    Options? options,
  }) async {
    return await _dio.get(
      endpoint,
      queryParameters: queryParams,
      options: options,
    );
  }

  Future<Response> post(
    String endpoint, {
    dynamic data,
    Options? options,
  }) async {
    return await _dio.post(endpoint, data: data, options: options);
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
