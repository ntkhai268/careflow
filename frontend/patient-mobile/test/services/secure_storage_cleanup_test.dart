import 'package:careflow_patient/services/secure_storage_cleanup.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:flutter_test/flutter_test.dart';

// Model the installed iOS plugin's asymmetry on any host OS: reads ignore the
// accessibility attribute, but a delete with the newer attribute misses an old item.
class LegacyKeychainStorage extends FlutterSecureStorage {
  LegacyKeychainStorage()
    : super(
        iOptions: const IOSOptions(
          accessibility: KeychainAccessibility.first_unlock_this_device,
        ),
      );
  final values = <String, String>{};
  final queries = <Map<String, String>>[];
  @override
  Future<String?> read({
    required String key,
    IOSOptions? iOptions,
    AndroidOptions? aOptions,
    LinuxOptions? lOptions,
    WebOptions? webOptions,
    MacOsOptions? mOptions,
    WindowsOptions? wOptions,
  }) async => values[key];
  @override
  Future<void> delete({
    required String key,
    IOSOptions? iOptions,
    AndroidOptions? aOptions,
    LinuxOptions? lOptions,
    WebOptions? webOptions,
    MacOsOptions? mOptions,
    WindowsOptions? wOptions,
  }) async {
    final options = (iOptions ?? this.iOptions).toMap();
    queries.add(options);
    if (!options.containsKey('accessibility') ||
        options['accessibility'] == 'unlocked') {
      values.remove(key);
    }
  }
}

void main() {
  test(
    'cleanup removes legacy item despite changed accessibility without clearing other keys',
    () async {
      final storage = LegacyKeychainStorage();
      storage.values.addAll({
        'legacy-device-key': 'fixture',
        'unrelated-key': 'preserved',
      });
      await storage.delete(key: 'legacy-device-key');
      expect(await storage.read(key: 'legacy-device-key'), 'fixture');
      await deleteStoredSecret(storage, key: 'legacy-device-key');
      expect(await storage.read(key: 'legacy-device-key'), isNull);
      expect(storage.values['unrelated-key'], 'preserved');
      expect(storage.queries.last.containsKey('accessibility'), isFalse);
      expect(
        storage.queries.last['accountName'],
        AppleOptions.defaultAccountName,
      );
    },
  );
  test('missing key remains an idempotent cleanup', () async {
    final storage = LegacyKeychainStorage();
    await deleteStoredSecret(storage, key: 'missing');
    await deleteStoredSecret(storage, key: 'missing');
    expect(storage.values, isEmpty);
  });
}
