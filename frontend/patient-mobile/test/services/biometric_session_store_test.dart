import 'dart:convert';
import 'package:careflow_patient/config/api_config.dart';
import 'package:biometric_storage/biometric_storage.dart';
import 'package:careflow_patient/services/biometric_session_store.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:flutter_test/flutter_test.dart';

class TestVault implements BiometricVault {
  String? secret;
  bool available = true, canceled = false;
  int reads = 0;
  @override
  Future<BiometricCapability> capability() async =>
      BiometricCapability(available, 'Face ID', 'Chưa thiết lập Face ID');
  @override
  Future<String?> read() async {
    reads++;
    if (canceled) {
      throw AuthException(AuthExceptionCode.userCanceled, 'Canceled');
    }
    return secret;
  }

  @override
  Future<void> write(String value) async {
    secret = value;
  }

  @override
  Future<void> delete() async {
    secret = null;
  }
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  late TestVault vault;
  late BiometricSessionStore store;
  setUp(() {
    FlutterSecureStorage.setMockInitialValues({});
    vault = TestVault();
    store = BiometricSessionStore(vault: vault);
  });
  Future<void> enroll() => store.enable(
    'patient',
    'access',
    'refresh',
    credentialId: 'device-id',
    deviceToken: 'd' * 43,
  );
  test(
    'OS approval gates enrollment and only device credential enters vault',
    () async {
      await store.saveAccess('regular-access');
      await store.saveRefresh('regular-refresh');
      await enroll();
      expect(vault.reads, 1);
      expect(jsonDecode(vault.secret!)['deviceToken'], 'd' * 43);
      expect(vault.secret, isNot(contains('refresh')));
      final disk = await store.storage.readAll();
      expect(disk.toString(), isNot(contains('d' * 43)));
      expect(disk.keys, isNot(contains('jwt_token')));
      expect(disk.keys, isNot(contains('refresh_token')));
      expect(await store.accessToken(), 'access');
    },
  );
  test(
    'restart can unlock device credential but cannot resurrect any session',
    () async {
      await enroll();
      final restarted = BiometricSessionStore(vault: vault);
      expect(await restarted.enabled(), isTrue);
      expect(await restarted.accessToken(), isNull);
      expect(await restarted.refreshToken(), isNull);
      expect((await restarted.unlockCredential()).token, 'd' * 43);
      expect(await restarted.accessToken(), isNull);
    },
  );
  test('cancel keeps enrollment and exposes no session', () async {
    await enroll();
    store.lock();
    vault.canceled = true;
    await expectLater(store.unlockCredential(), throwsA(isA<AuthException>()));
    expect(await store.accessToken(), isNull);
    expect(await store.enabled(), isTrue);
  });
  test('unsupported device cannot enroll', () async {
    vault.available = false;
    await expectLater(enroll(), throwsException);
    expect(await store.enabled(), isFalse);
    expect(vault.secret, isNull);
  });
  test(
    'refresh rotation and ordinary logout preserve dedicated device secret',
    () async {
      await enroll();
      final original = vault.secret;
      await store.saveRefresh('rotated');
      expect(vault.secret, original);
      await store.clearSession();
      expect(await store.enabled(), isTrue);
      expect(await store.accessToken(), isNull);
      expect(await store.refreshToken(), isNull);
      expect((await store.unlockCredential()).token, 'd' * 43);
    },
  );
  test('explicit clear removes enrollment and every local session', () async {
    await enroll();
    await store.clear();
    expect(await store.enabled(), isFalse);
    expect(vault.secret, isNull);
    expect(await store.storage.readAll(), isEmpty);
  });
  test('disable retains current session in ordinary secure storage', () async {
    await enroll();
    await store.disable();
    final restarted = BiometricSessionStore(vault: vault);
    expect(await restarted.enabled(), isFalse);
    expect(await restarted.accessToken(), 'access');
    expect(await restarted.refreshToken(), 'refresh');
    expect(vault.secret, isNull);
  });
  test(
    'legacy refresh-based enrollment is never accepted as new device credential',
    () async {
      FlutterSecureStorage.setMockInitialValues({
        'biometric_account_v1:https://careflow-demo.online/api': 'patient',
      });
      // Use the current configured base, rather than depending on a deployment URL.
      final storage = const FlutterSecureStorage();
      await storage.deleteAll();
      await storage.write(
        key: 'biometric_account_v1:${ApiConfig.baseUrl}',
        value: 'patient',
      );
      vault.secret = 'old-refresh';
      expect(await store.enabled(), isFalse);
      expect(vault.secret, isNull);
    },
  );
}
