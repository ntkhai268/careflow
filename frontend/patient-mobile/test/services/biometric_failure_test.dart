import 'package:biometric_storage/biometric_storage.dart';
import 'package:careflow_patient/services/auth_service.dart';
import 'package:careflow_patient/providers/auth_provider.dart';
import 'package:careflow_patient/services/biometric_session_store.dart';
import 'package:dio/dio.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:flutter_test/flutter_test.dart';
import 'biometric_session_store_test.dart' show TestVault;
import 'native_login_methods_test.dart' show RecordingApi;

class OtherAccountApi extends RecordingApi {
  OtherAccountApi(super.sessions);
  @override
  Future<Response> post(
    String endpoint, {
    dynamic data,
    Options? options,
  }) async {
    if (endpoint != '/auth/login/biometric') {
      return super.post(endpoint, data: data, options: options);
    }
    calls.add(endpoint);
    return Response(
      requestOptions: RequestOptions(path: endpoint),
      data: {
        'data': {
          'accessToken': 'other-access',
          'refreshToken': 'other-refresh',
          'user': {
            'id': 'other-id',
            'username': 'other-account',
            'role': 'PATIENT',
          },
        },
      },
    );
  }
}

class LogoutFailureApi extends RecordingApi {
  LogoutFailureApi(super.sessions);
  @override
  Future<Response> post(
    String endpoint, {
    dynamic data,
    Options? options,
  }) async {
    throw DioException(
      requestOptions: RequestOptions(path: endpoint),
      type: DioExceptionType.connectionError,
    );
  }
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  setUp(() => FlutterSecureStorage.setMockInitialValues({}));

  test(
    'wrong returned identity revokes and clears the biometric session',
    () async {
      final vault = TestVault();
      final sessions = BiometricSessionStore(vault: vault);
      await sessions.enable(
        'patient',
        'access',
        'refresh',
        credentialId: 'device-id',
        deviceToken: 'd' * 43,
      );
      sessions.lock();
      final api = OtherAccountApi(sessions);
      await expectLater(AuthService(api).biometricLogin(), throwsException);
      expect(await sessions.enabled(), isFalse);
      expect(await sessions.accessToken(), isNull);
      expect(vault.secret, isNull);
      expect(api.calls, contains('/auth/logout'));
    },
  );

  test(
    'canceling enrollment keeps original password session and cleans new vault',
    () async {
      final vault = TestVault()..canceled = true;
      final sessions = BiometricSessionStore(vault: vault);
      await sessions.saveAccess('original-access');
      await sessions.saveRefresh('original-refresh');
      await expectLater(
        sessions.enable(
          'patient',
          'new-access',
          'new-refresh',
          credentialId: 'device-id',
          deviceToken: 'd' * 43,
        ),
        throwsA(isA<AuthException>()),
      );
      expect(await sessions.enabled(), isFalse);
      expect(await sessions.accessToken(), 'original-access');
      expect(await sessions.refreshToken(), 'original-refresh');
      expect(vault.secret, isNull);
    },
  );

  test(
    'offline logout removes session but keeps OS-gated device credential',
    () async {
      final vault = TestVault();
      final sessions = BiometricSessionStore(vault: vault);
      await sessions.enable(
        'patient',
        'access',
        'refresh',
        credentialId: 'device-id',
        deviceToken: 'd' * 43,
      );
      final api = LogoutFailureApi(sessions);
      await expectLater(
        AuthService(api).logout(),
        throwsA(isA<DioException>()),
      );
      expect(await sessions.enabled(), isTrue);
      expect(await sessions.accessToken(), isNull);
      expect(await sessions.refreshToken(), isNull);
      expect(vault.secret, contains('d' * 43));
    },
  );
  test(
    'offline logout returns UI to unauthenticated state without throwing',
    () async {
      final sessions = BiometricSessionStore(vault: TestVault());
      await sessions.enable(
        'patient',
        'access',
        'refresh',
        credentialId: 'device-id',
        deviceToken: 'd' * 43,
      );
      final notifier = AuthNotifier(AuthService(LogoutFailureApi(sessions)));
      addTearDown(notifier.dispose);
      await notifier.logout();
      expect(notifier.state.status, AuthStatus.unauthenticated);
      expect(notifier.state.userId, isNull);
      expect(await sessions.accessToken(), isNull);
    },
  );

  test(
    'canceled sensor maps to a retryable patient message without platform details',
    () async {
      final vault = TestVault();
      final sessions = BiometricSessionStore(vault: vault);
      await sessions.enable(
        'patient',
        'access',
        'refresh',
        credentialId: 'device-id',
        deviceToken: 'd' * 43,
      );
      sessions.lock();
      vault.canceled = true;
      final api = RecordingApi(sessions);
      final notifier = AuthNotifier(AuthService(api));
      addTearDown(notifier.dispose);
      await notifier.alternativeLogin('biometric', '', '', '');
      expect(notifier.state.status, AuthStatus.error);
      expect(notifier.state.errorMessage, contains('Đã hủy'));
      expect(notifier.state.errorMessage, isNot(contains('AuthException')));
      expect(api.calls, isEmpty);
    },
  );
}
