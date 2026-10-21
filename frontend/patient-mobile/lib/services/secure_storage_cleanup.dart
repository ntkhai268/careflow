import 'package:flutter_secure_storage/flutter_secure_storage.dart';

/// Reads in flutter_secure_storage ignore accessibility; deletes otherwise match
/// it. Remove only this app-owned key across its older iOS accessibility levels.
Future<void> deleteStoredSecret(
  FlutterSecureStorage storage, {
  required String key,
}) => storage.delete(key: key, iOptions: const IOSOptions(accessibility: null));
