import 'dart:convert';

import 'package:dio/dio.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:jose/jose.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';
import '../config/api_config.dart';
import 'dpop_proof.dart';
import 'native_transport.dart';
import 'biometric_session_store.dart';
import 'medical_file_cache.dart';
import 'device_request_proof.dart';
import 'secure_storage_cleanup.dart';

/// Core API service using Dio with JWT interceptor.
class ApiService {
  late final Dio _dio;
  late final Dio _refreshDio;
  late final Future<void> _transportReady = configureNativeTransport(
    _dio,
    _refreshDio,
  );
  Future<DeviceRequestProof>? _proof;
  final FlutterSecureStorage _storage = const FlutterSecureStorage(
    iOptions: IOSOptions(
      accessibility: KeychainAccessibility.first_unlock_this_device,
    ),
  );

  final BiometricSessionStore sessions;
  final VoidCallback? onSessionExpired;
  Future<bool>? _refreshing;

  ApiService({BiometricSessionStore? sessions, this.onSessionExpired})
    : sessions = sessions ?? BiometricSessionStore() {
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
    _refreshDio.interceptors.add(
      InterceptorsWrapper(
        onRequest: (options, handler) async {
          try {
            await _transportReady;
            options.headers['DPoP'] = await (await _deviceProof()).sign(
              options.method,
              options.uri,
            );
            handler.next(options);
          } catch (error) {
            handler.reject(DioException(requestOptions: options, error: error));
          }
        },
      ),
    );

    _dio.interceptors.add(
      InterceptorsWrapper(
        onRequest: (options, handler) async {
          try {
            await _transportReady;
            final token = options.extra['anonymous'] == true
                ? null
                : await getToken();
            options.headers.remove('Authorization');
            if (token != null) {
              options.headers['Authorization'] = 'Bearer $token';
            }
            options.headers['DPoP'] = await (await _deviceProof()).sign(
              options.method,
              options.uri,
              accessToken: token,
            );
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
              request.path.startsWith('/auth/login/') ||
              request.path.startsWith('/auth/recovery/') ||
              request.path.startsWith('/auth/registration/') ||
              request.path.startsWith('/auth/staff-passkeys/enroll/') ||
              request.path.startsWith('/auth/login-methods') ||
              (request.method == 'POST' &&
                  request.path == '/auth/biometric-devices');
          final alreadyRetried = request.extra['authRetried'] == true;
          if (error.response?.statusCode == 401 &&
              !isAuthEndpoint &&
              !alreadyRetried) {
            if (await (_refreshing ??= _refreshSession()).whenComplete(
              () => _refreshing = null,
            )) {
              request.extra['authRetried'] = true;
              if (request.data is FormData) {
                request.data = (request.data as FormData).clone();
              }
              handler.resolve(await _dio.fetch(request));
              return;
            }
          }
          handler.next(error);
        },
      ),
    );
  }

  Future<DeviceRequestProof> _deviceProof() async {
    try {
      return await (_proof ??= _loadDeviceProof());
    } catch (_) {
      _proof =
          null; // A locked keychain can be retried after the phone is unlocked.
      rethrow;
    }
  }

  Future<DeviceRequestProof> _loadDeviceProof() async {
    final storageKey = 'dpop_key_v1:${ApiConfig.baseUrl}';
    final saved = await _storage.read(key: storageKey);
    const origin = String.fromEnvironment('DPOP_ORIGIN');
    if (saved == null) {
      try {
        return await HardwareRequestProof.create(
          Uri.parse(ApiConfig.baseUrl),
          proofOrigin: origin.isEmpty ? null : Uri.parse(origin),
        );
      } on MissingPluginException {
        if (kReleaseMode) rethrow;
      } on PlatformException {
        if (kReleaseMode) rethrow;
      }
    }
    final key = saved == null
        ? JsonWebKey.generate('ES256')
        : JsonWebKey.fromJson(jsonDecode(saved) as Map<String, dynamic>);
    if (saved == null) {
      await _storage.write(key: storageKey, value: jsonEncode(key.toJson()));
    }
    return SoftwareRequestProof(
      DpopProof(
        key,
        apiBase: Uri.parse(ApiConfig.baseUrl),
        proofOrigin: origin.isEmpty ? null : Uri.parse(origin),
      ),
    );
  }

  String get _migrationKey => 'dpop_migration_pending:${ApiConfig.baseUrl}';

  Future<Map<String, dynamic>?> upgradeDeviceKey({
    String? password,
    String? credentialId,
    String? deviceToken,
  }) async {
    if (await _deviceProof() is! SoftwareRequestProof || kIsWeb) return null;
    HardwareRequestProof next;
    try {
      const origin = String.fromEnvironment('DPOP_ORIGIN');
      next = await HardwareRequestProof.create(
        Uri.parse(ApiConfig.baseUrl),
        proofOrigin: origin.isEmpty ? null : Uri.parse(origin),
      );
    } on MissingPluginException {
      if (kReleaseMode) rethrow;
      return null;
    } on PlatformException {
      if (kReleaseMode) rethrow;
      return null;
    }
    final access = await getToken();
    if (access == null) return null;
    await _storage.write(key: _migrationKey, value: 'pending');
    try {
      final proof = await next.sign(
        'POST',
        Uri.parse('${ApiConfig.baseUrl}/auth/device-key/rotate'),
        accessToken: access,
      );
      final response = await post(
        '/auth/device-key/rotate',
        data: {
          'newKeyProof': proof,
          'password': ?password,
          'credentialId': ?credentialId,
          'deviceToken': ?deviceToken,
        },
        options: Options(extra: {'authRetried': true}),
      );
      _proof = Future.value(next);
      await deleteStoredSecret(
        _storage,
        key: 'dpop_key_v1:${ApiConfig.baseUrl}',
      );
      await deleteStoredSecret(_storage, key: _migrationKey);
      return Map<String, dynamic>.from(response.data['data']);
    } on DioException {
      // Keep legacy enrollment and pending state when a response may have been lost.
      return null;
    }
  }

  Future<Map<String, dynamic>?> recoverPendingDeviceKey(
    String credentialId,
    String deviceToken,
  ) async {
    if (await _storage.read(key: _migrationKey) != 'pending') return null;
    final previous = _proof;
    try {
      const origin = String.fromEnvironment('DPOP_ORIGIN');
      final next = await HardwareRequestProof.create(
        Uri.parse(ApiConfig.baseUrl),
        proofOrigin: origin.isEmpty ? null : Uri.parse(origin),
      );
      _proof = Future.value(next);
      final response = await post(
        '/auth/login/biometric',
        data: {'credentialId': credentialId, 'deviceToken': deviceToken},
        options: Options(extra: {'anonymous': true}),
      );
      await deleteStoredSecret(
        _storage,
        key: 'dpop_key_v1:${ApiConfig.baseUrl}',
      );
      await deleteStoredSecret(_storage, key: _migrationKey);
      return Map<String, dynamic>.from(response.data['data']);
    } on DioException catch (error) {
      _proof = previous;
      if (error.response?.statusCode == 401 ||
          error.response?.statusCode == 403) {
        return null;
      }
      rethrow; // A network failure cannot invalidate an enrollment that may have migrated.
    } catch (_) {
      _proof = previous;
      rethrow;
    }
  }

  Future<String> deviceKeyProtection() async {
    final proof = await _deviceProof();
    return proof is HardwareRequestProof ? proof.protection : 'legacy_software';
  }

  Future<bool> _refreshSession() async {
    final token = await getRefreshToken();
    if (token == null || token.isEmpty) return false;
    try {
      await refreshSession(token);
      return true;
    } on DioException catch (error) {
      if (error.response?.statusCode == 401 ||
          error.response?.statusCode == 403) {
        await clearSession();
        onSessionExpired?.call();
      }
      return false;
    } catch (_) {
      return false;
    }
  }

  /// Uses a transport without access-token injection or automatic refresh.
  Future<Map<String, dynamic>> refreshSession(String token) async {
    final response = await _refreshDio.post(
      ApiConfig.authRefresh,
      data: {'refreshToken': token},
    );
    final data = Map<String, dynamic>.from(response.data['data']);
    await saveRefreshToken(data['refreshToken'] as String);
    await saveToken(data['accessToken'] as String);
    return data;
  }

  // Token management
  Future<void> saveToken(String token) => sessions.saveAccess(token);
  Future<void> saveRefreshToken(String token) => sessions.saveRefresh(token);
  Future<String?> getToken() => sessions.accessToken();
  Future<String?> getRefreshToken() => sessions.refreshToken();
  Future<void> clearTokens() async {
    try {
      await MedicalFileCache.instance.clear();
    } finally {
      await sessions.clear();
    }
  }

  Future<void> clearSession() async {
    try {
      await MedicalFileCache.instance.clear();
    } finally {
      await sessions.clearSession();
    }
  }

  Future<bool> hasToken() async {
    final token = await getToken();
    return token != null && token.isNotEmpty;
  }

  /// STOMP CONNECT uses a fresh proof for the socket path, never a token URL.
  Future<({String userId, Map<String,String> headers})> notificationSocketCredentials() async {
    final me=await get('/auth/me');final user=me.data['data'];
    final token=await getToken();
    if(user is! Map || user['id'] is! String || token==null)throw const FormatException('Cần xác minh phiên thông báo.');
    final uri=Uri.parse(ApiConfig.baseUrl).replace(path:'/ws/notifications',query:null,fragment:null);
    return (userId:user['id'] as String,headers:{'Authorization':'Bearer $token','DPoP':await (await _deviceProof()).sign('GET',uri,accessToken:token)});
  }

  // HTTP methods
  Future<Response> stream(
    String endpoint, {
    required CancelToken cancelToken,
  }) => _dio.get(
    endpoint,
    cancelToken: cancelToken,
    options: Options(
      responseType: ResponseType.stream,
      followRedirects: false,
      headers: {'Accept': 'application/octet-stream'},
    ),
  );

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

  Future<Response> upload(
    String endpoint, {
    required FormData data,
    required CancelToken cancelToken,
    void Function(int, int)? onSendProgress,
  }) => _dio.post(
    endpoint,
    data: data,
    cancelToken: cancelToken,
    onSendProgress: onSendProgress,
  );

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
final sessionInvalidationProvider = StateProvider<int>((ref) => 0);
final apiServiceProvider = Provider<ApiService>((ref) {
  return ApiService(
    onSessionExpired: () =>
        ref.read(sessionInvalidationProvider.notifier).state++,
  );
});
