import 'package:careflow_patient/services/api_service.dart';
import 'package:careflow_patient/services/auth_service.dart';
import 'package:careflow_patient/services/biometric_session_store.dart';
import 'package:dio/dio.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:flutter_test/flutter_test.dart';
import 'biometric_session_store_test.dart' show TestVault;

class DeviceListApi extends ApiService {
  DeviceListApi(BiometricSessionStore sessions) : super(sessions: sessions);
  bool active = false, offline = false;
  @override
  Future<Response> get(
    String endpoint, {
    Map<String, dynamic>? queryParams,
    Options? options,
  }) async {
    expect(endpoint, '/auth/devices');
    if (offline) {
      throw DioException(
        requestOptions: RequestOptions(path: endpoint),
        type: DioExceptionType.connectionError,
      );
    }
    return Response(
      requestOptions: RequestOptions(path: endpoint),
      data: {
        'data': active
            ? [
                {'id': 'device-id'},
              ]
            : [],
      },
    );
  }
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  late DeviceListApi api;
  late TestVault vault;
  setUp(() async {
    FlutterSecureStorage.setMockInitialValues({});
    vault = TestVault();
    api = DeviceListApi(BiometricSessionStore(vault: vault));
    await api.sessions.enable(
      'patient',
      'current-access',
      'current-refresh',
      credentialId: 'device-id',
      deviceToken: 'test-device-token',
    );
  });
  test(
    'revoked registration is removed while preserving the current password session',
    () async {
      await AuthService(api).reconcileBiometrics();
      expect(await api.sessions.enabled(), isFalse);
      expect(vault.secret, isNull);
      expect(await api.getToken(), 'current-access');
      expect(await api.getRefreshToken(), 'current-refresh');
    },
  );
  test(
    'active registration remains enabled with no extra biometric prompt',
    () async {
      api.active = true;
      final reads = vault.reads;
      await AuthService(api).reconcileBiometrics();
      expect(await api.sessions.enabled(), isTrue);
      expect(vault.secret, isNotNull);
      expect(vault.reads, reads);
    },
  );
  test('network error never erases an existing registration', () async {
    api.offline = true;
    await expectLater(
      AuthService(api).reconcileBiometrics(),
      throwsA(isA<DioException>()),
    );
    expect(await api.sessions.enabled(), isTrue);
    expect(vault.secret, isNotNull);
    expect(await api.getToken(), 'current-access');
  });
}
