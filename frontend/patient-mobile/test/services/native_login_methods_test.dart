import 'package:biometric_storage/biometric_storage.dart';
import 'package:careflow_patient/services/api_service.dart';
import 'package:careflow_patient/services/auth_service.dart';
import 'package:careflow_patient/services/biometric_session_store.dart';
import 'package:dio/dio.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:flutter_test/flutter_test.dart';
import 'biometric_session_store_test.dart' show TestVault;

class RecordingApi extends ApiService {
  RecordingApi(BiometricSessionStore sessions) : super(sessions: sessions);
  // Widget/service fixtures do not invoke native hardware; bridge/migration has separate tests.
  @override
  Future<Map<String, dynamic>?> upgradeDeviceKey({
    String? password,
    String? credentialId,
    String? deviceToken,
  }) async => null;
  @override
  Future<Map<String, dynamic>?> recoverPendingDeviceKey(
    String credentialId,
    String deviceToken,
  ) async => null;
  final calls = <String>[];
  Map<String, dynamic>? body;
  int? refreshFailure;
  @override
  Future<Response> post(
    String endpoint, {
    dynamic data,
    Options? options,
  }) async {
    calls.add(endpoint);
    body = Map<String, dynamic>.from(data);
    if (endpoint == '/auth/login/biometric' && refreshFailure != null) {
      throw DioException(
        requestOptions: RequestOptions(path: endpoint),
        type: refreshFailure == 0
            ? DioExceptionType.connectionError
            : DioExceptionType.badResponse,
        response: refreshFailure == 0
            ? null
            : Response(
                requestOptions: RequestOptions(path: endpoint),
                statusCode: refreshFailure,
              ),
      );
    }
    if (endpoint == '/auth/biometric-devices') {
      return Response(
        requestOptions: RequestOptions(path: endpoint),
        data: {
          'data': {'credentialId': 'device-id', 'deviceToken': 'd' * 43},
        },
      );
    }
    return Response(
      requestOptions: RequestOptions(path: endpoint),
      data: {
        'data': {
          'accessToken': endpoint == '/auth/login/biometric'
              ? 'new-access'
              : 'question-access',
          'refreshToken': endpoint == '/auth/login/biometric'
              ? 'new-refresh'
              : 'question-refresh',
          'user': {
            'role': 'PATIENT',
            'id': 'patient-id',
            'username': 'patient',
            'email': 'patient@example.com',
          },
        },
      },
    );
  }

  @override
  Future<Response> delete(String endpoint) async {
    calls.add(endpoint);
    return Response(
      requestOptions: RequestOptions(path: endpoint),
      data: {
        'data': {'disabled': true},
      },
    );
  }

  @override
  Future<Response> get(
    String endpoint, {
    Map<String, dynamic>? queryParams,
    Options? options,
  }) async {
    calls.add(endpoint);
    return Response(
      requestOptions: RequestOptions(path: endpoint),
      data: {
        'data': {
          'id': 'patient-id',
          'username': 'patient',
          'email': 'patient@example.com',
          'role': 'PATIENT',
        },
      },
    );
  }

  @override
  Future<Map<String, dynamic>> refreshSession(String token) async {
    calls.add('refresh:$token');
    if (refreshFailure != null) {
      throw DioException(
        requestOptions: RequestOptions(path: '/auth/refresh'),
        type: refreshFailure == 0
            ? DioExceptionType.connectionError
            : DioExceptionType.badResponse,
        response: refreshFailure == 0
            ? null
            : Response(
                requestOptions: RequestOptions(),
                statusCode: refreshFailure,
              ),
      );
    }
    await saveRefreshToken('rotated');
    await saveToken('new-access');
    return {};
  }
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  late TestVault vault;
  late RecordingApi api;
  late AuthService auth;
  setUp(() {
    FlutterSecureStorage.setMockInitialValues({});
    vault = TestVault();
    api = RecordingApi(BiometricSessionStore(vault: vault));
    auth = AuthService(api);
  });

  test(
    'secret-question login uses backend contract and accepts issued session',
    () async {
      await auth.questionLogin('patient', 'memory', 'private answer');
      expect(api.calls, ['/auth/login/question']);
      expect(api.body, {
        'usernameOrEmail': 'patient',
        'questionId': 'memory',
        'answer': 'private answer',
      });
      expect(await api.getToken(), 'question-access');
      expect(await api.getRefreshToken(), 'question-refresh');
    },
  );
  test(
    'question setup requires current password; answer is never stored locally',
    () async {
      await auth.saveQuestion('current-password', 'place', 'private answer');
      expect(api.calls, ['/auth/login-methods/question']);
      expect(api.body?['password'], 'current-password');
      expect(await api.sessions.storage.readAll(), isEmpty);
    },
  );
  test(
    'email confirmation uses backend reset and clears local session',
    () async {
      await api.saveToken('old');
      await auth.confirmEmailRecovery(
        'request-id',
        'r' * 43,
        'NewPassword123@',
      );
      expect(api.calls, ['/auth/recovery/email/confirm']);
      expect(api.body?['requestId'], 'request-id');
      expect(api.body?['resetToken'], 'r' * 43);
      expect(api.body?['newPassword'], 'NewPassword123@');
      expect(await api.getToken(), isNull);
    },
  );
  test('canceling biometrics performs no network call', () async {
    await api.sessions.enable(
      'patient',
      'access',
      'refresh',
      credentialId: 'device-id',
      deviceToken: 'd' * 43,
    );
    api.sessions.lock();
    vault.canceled = true;
    await expectLater(auth.biometricLogin(), throwsA(isA<AuthException>()));
    expect(api.calls, isEmpty);
    expect(await api.getToken(), isNull);
  });
  test(
    'approved biometrics request a fresh server session for the matching account',
    () async {
      await api.sessions.enable(
        'patient',
        'access',
        'refresh',
        credentialId: 'device-id',
        deviceToken: 'd' * 43,
      );
      api.sessions.lock();
      await auth.biometricLogin();
      expect(api.calls, ['/auth/login/biometric']);
      expect(await api.getToken(), 'new-access');
      expect(vault.secret, contains('d' * 43));
    },
  );
  test(
    'network failure stays locked and preserves retryable enrollment',
    () async {
      await api.sessions.enable(
        'patient',
        'access',
        'refresh',
        credentialId: 'device-id',
        deviceToken: 'd' * 43,
      );
      api.sessions.lock();
      api.refreshFailure = 0;
      await expectLater(auth.biometricLogin(), throwsA(isA<DioException>()));
      expect(await api.getToken(), isNull);
      expect(await api.sessions.enabled(), isTrue);
      expect(vault.secret, contains('d' * 43));
    },
  );
  test(
    'revoked device credential removes stale biometric enrollment',
    () async {
      await api.sessions.enable(
        'patient',
        'access',
        'refresh',
        credentialId: 'device-id',
        deviceToken: 'd' * 43,
      );
      api.sessions.lock();
      api.refreshFailure = 401;
      await expectLater(auth.biometricLogin(), throwsException);
      expect(await api.sessions.enabled(), isFalse);
      expect(await api.getToken(), isNull);
      expect(vault.secret, isNull);
    },
  );
  test(
    'logout revokes active refresh but preserves independent biometric credential',
    () async {
      await api.sessions.enable(
        'patient',
        'access',
        'refresh',
        credentialId: 'device-id',
        deviceToken: 'd' * 43,
      );
      await auth.logout();
      expect(api.calls, ['/auth/logout']);
      expect(api.body, {'refreshToken': 'refresh'});
      expect(await api.sessions.enabled(), isTrue);
      expect(vault.secret, contains('d' * 43));
      expect(await api.getToken(), isNull);
      await auth.biometricLogin();
      expect(await api.getToken(), 'new-access');
    },
  );
  test(
    'password login to same account preserves enrollment; another account forgets it',
    () async {
      await api.sessions.enable(
        'patient',
        'access',
        'refresh',
        credentialId: 'device-id',
        deviceToken: 'd' * 43,
      );
      await auth.login('patient', 'Password123!');
      expect(await api.sessions.enabled(), isTrue);
      await api.sessions.clear();
      await api.sessions.enable(
        'other',
        'access',
        'refresh',
        credentialId: 'other-id',
        deviceToken: 'o' * 43,
      );
      await auth.login('patient', 'Password123!');
      expect(await api.sessions.enabled(), isFalse);
      expect(vault.secret, isNull);
    },
  );
  test(
    'disable revokes server enrollment and preserves password session',
    () async {
      await api.sessions.enable(
        'patient',
        'access',
        'refresh',
        credentialId: 'device-id',
        deviceToken: 'd' * 43,
      );
      await auth.disableBiometrics();
      expect(api.calls, ['/auth/biometric-devices/device-id']);
      expect(await api.sessions.enabled(), isFalse);
      expect(await api.getToken(), 'access');
    },
  );
}
