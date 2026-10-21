import 'dart:convert';
import 'package:careflow_patient/config/api_config.dart';
import 'package:careflow_patient/services/api_service.dart';
import 'package:careflow_patient/services/auth_service.dart';
import 'package:careflow_patient/services/biometric_session_store.dart';
import 'package:careflow_patient/services/device_request_proof.dart';
import 'package:dio/dio.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:jose/jose.dart';
import 'biometric_session_store_test.dart' show TestVault;
import 'device_request_proof_test.dart' show toDer;

class MigrationApi extends ApiService {
  MigrationApi(BiometricSessionStore sessions) : super(sessions: sessions);
  bool loseResponse = false, unavailable = false, migrated = false;
  Map<String, dynamic>? migration;
  final calls = <String>[];
  @override
  Future<Response> post(
    String endpoint, {
    dynamic data,
    Options? options,
  }) async {
    calls.add(endpoint);
    if (endpoint.endsWith('/rotate')) {
      migration = Map<String, dynamic>.from(data);
      migrated = true;
      if (loseResponse) {
        throw DioException(
          requestOptions: RequestOptions(path: endpoint),
          type: DioExceptionType.connectionError,
        );
      }
    } else if (endpoint.endsWith('/biometric')) {
      if (unavailable) {
        throw DioException(
          requestOptions: RequestOptions(path: endpoint),
          type: DioExceptionType.connectionError,
        );
      }
      if (migrated && await deviceKeyProtection() == 'legacy_software') {
        throw DioException(
          requestOptions: RequestOptions(path: endpoint),
          response: Response(requestOptions: RequestOptions(), statusCode: 401),
        );
      }
    }
    return Response(
      requestOptions: RequestOptions(path: endpoint),
      data: {
        'data': {
          'accessToken': 'hardware-access',
          'refreshToken': 'hardware-refresh',
          'user': {
            'id': 'patient',
            'username': 'patient',
            'email': 'test@example.test',
            'role': 'PATIENT',
          },
        },
      },
    );
  }
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  final hardware = JsonWebKey.generate('ES256');
  late MigrationApi api;
  late TestVault vault;
  const storage = FlutterSecureStorage();
  final legacyKey = 'dpop_key_v1:${ApiConfig.baseUrl}';
  setUp(() async {
    FlutterSecureStorage.setMockInitialValues({
      legacyKey: jsonEncode(JsonWebKey.generate('ES256').toJson()),
    });
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(HardwareRequestProof.channel, (call) async {
          if (call.method == 'publicKey') {
            return {
              'jwk': {
                for (final entry in hardware.toJson().entries)
                  if (['kty', 'crv', 'x', 'y'].contains(entry.key))
                    entry.key: entry.value,
              },
              'protection': 'secure_enclave',
            };
          }
          return toDer(
            hardware.sign(
              (call.arguments as Map)['message'],
              algorithm: 'ES256',
            ),
          );
        });
    vault = TestVault();
    api = MigrationApi(BiometricSessionStore(vault: vault));
    await api.sessions.enable(
      'patient',
      'legacy-access',
      'legacy-refresh',
      credentialId: 'device-id',
      deviceToken: 'd' * 43,
    );
  });
  tearDown(
    () => TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(HardwareRequestProof.channel, null),
  );
  test(
    'migration proves the new key and preserves the OS biometric enrollment',
    () async {
      final result = await api.upgradeDeviceKey(
        credentialId: 'device-id',
        deviceToken: 'd' * 43,
      );
      expect(result?['accessToken'], 'hardware-access');
      final proof = JsonWebSignature.fromCompactSerialization(
        api.migration!['newKeyProof'],
      );
      expect(await proof.verify(JsonWebKeyStore()..addKey(hardware)), isTrue);
      expect(await api.deviceKeyProtection(), 'secure_enclave');
      expect(await storage.read(key: legacyKey), isNull);
      expect(await api.sessions.enabled(), isTrue);
      expect(vault.secret, contains('d' * 43));
    },
  );
  test(
    'lost migration response recovers the same Face ID credential with the new binding',
    () async {
      api.loseResponse = true;
      expect(await api.upgradeDeviceKey(password: 'TestPassword1!'), isNull);
      expect(await storage.read(key: legacyKey), isNotNull);
      await AuthService(api).biometricLogin();
      expect(await api.getToken(), 'hardware-access');
      expect(await api.sessions.enabled(), isTrue);
      expect(vault.secret, contains('d' * 43));
      expect(await storage.read(key: legacyKey), isNull);
    },
  );
  test(
    'network failure during pending recovery retains the enrollment for retry',
    () async {
      api.loseResponse = true;
      await api.upgradeDeviceKey(password: 'TestPassword1!');
      api.unavailable = true;
      await expectLater(
        AuthService(api).biometricLogin(),
        throwsA(isA<DioException>()),
      );
      expect(await api.sessions.enabled(), isTrue);
      expect(vault.secret, contains('d' * 43));
      expect(await storage.read(key: legacyKey), isNotNull);
    },
  );
}
