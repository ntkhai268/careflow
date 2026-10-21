import 'dart:convert';

import 'package:biometric_storage/biometric_storage.dart';
import 'package:crypto/crypto.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:local_auth/local_auth.dart';

import '../config/api_config.dart';
import 'secure_storage_cleanup.dart';

class BiometricCapability {
  const BiometricCapability(this.available, this.label, this.detail);
  final bool available;
  final String label;
  final String detail;
}

abstract class BiometricVault {
  Future<BiometricCapability> capability();
  Future<String?> read();
  Future<void> write(String value);
  Future<void> delete();
}

class DeviceBiometricVault implements BiometricVault {
  final _auth = LocalAuthentication();
  Future<BiometricStorageFile>? _file;

  Future<BiometricStorageFile>
  _storage() => _file ??= BiometricStorage().getStorage(
    'careflow_session_${sha256.convert(utf8.encode(ApiConfig.baseUrl))}',
    options: StorageFileInitOptions(
      authenticationRequired: true,
      androidBiometricOnly: true,
      darwinBiometricOnly: true,
    ),
    promptInfo: const PromptInfo(
      androidPromptInfo: AndroidPromptInfo(
        title: 'Đăng nhập CareFlow',
        description:
            'Xác nhận sinh trắc học để sử dụng tài khoản trên điện thoại này.',
        negativeButton: 'Hủy',
      ),
      iosPromptInfo: IosPromptInfo(
        accessTitle: 'Xác nhận để đăng nhập CareFlow',
        saveTitle: 'Xác nhận để bật đăng nhập sinh trắc học',
      ),
    ),
  );

  @override
  Future<BiometricCapability> capability() async {
    if (kIsWeb ||
        (defaultTargetPlatform != TargetPlatform.iOS &&
            defaultTargetPlatform != TargetPlatform.android)) {
      return const BiometricCapability(
        false,
        'Sinh trắc học',
        'Chỉ khả dụng trên điện thoại hỗ trợ sinh trắc học.',
      );
    }
    try {
      final types = await _auth.getAvailableBiometrics();
      final ios = defaultTargetPlatform == TargetPlatform.iOS;
      final label = types.contains(BiometricType.face)
          ? (ios ? 'Face ID' : 'Khuôn mặt')
          : types.contains(BiometricType.fingerprint)
          ? (ios ? 'Touch ID' : 'Vân tay')
          : 'Sinh trắc học';
      final status = await BiometricStorage().canAuthenticate();
      return BiometricCapability(
        status == CanAuthenticateResponse.success,
        label,
        status == CanAuthenticateResponse.success
            ? 'Xác nhận bằng $label đã thiết lập trên điện thoại.'
            : 'Thiết bị chưa sẵn sàng. Hãy thiết lập Face ID hoặc vân tay trong Cài đặt, rồi thử lại.',
      );
    } catch (_) {
      return const BiometricCapability(
        false,
        'Sinh trắc học',
        'Không kiểm tra được sinh trắc học. Hãy thử lại hoặc dùng mật khẩu.',
      );
    }
  }

  @override
  Future<String?> read() async => (await _storage()).read();
  @override
  Future<void> write(String value) async => (await _storage()).write(value);
  @override
  Future<void> delete() async => (await _storage()).delete();
}

/// A device credential is OS-gated; authenticated session tokens are separate.
class BiometricCredential {
  const BiometricCredential(this.id, this.token);
  final String id, token;
}

class BiometricSessionStore {
  BiometricSessionStore({FlutterSecureStorage? storage, BiometricVault? vault})
    : storage =
          storage ??
          const FlutterSecureStorage(
            iOptions: IOSOptions(
              accessibility: KeychainAccessibility.first_unlock_this_device,
            ),
          ),
      vault = vault ?? DeviceBiometricVault();
  final FlutterSecureStorage storage;
  final BiometricVault vault;
  String get _accountKey => 'biometric_device_v2:${ApiConfig.baseUrl}';
  String get _legacyKey => 'biometric_account_v1:${ApiConfig.baseUrl}';
  String? _access, _refresh;

  Future<Map<String, dynamic>?> _registration() async {
    final saved = await storage.read(key: _accountKey);
    if (saved != null) {
      try {
        final data = jsonDecode(saved);
        if (data is Map<String, dynamic> &&
            data['username'] is String &&
            data['credentialId'] is String) {
          return data;
        }
      } on FormatException {
        /* Corrupt metadata cannot block password login. */
      }
      try {
        await vault.delete();
      } finally {
        await deleteStoredSecret(storage, key: _accountKey);
      }
      return null;
    }
    if (await storage.read(key: _legacyKey) != null) {
      // A logged-out refresh token must never become a device login credential.
      try {
        await vault.delete();
      } finally {
        await deleteStoredSecret(storage, key: _legacyKey);
      }
    }
    return null;
  }

  Future<String?> account() async =>
      (await _registration())?['username'] as String?;
  Future<String?> credentialId() async =>
      (await _registration())?['credentialId'] as String?;
  Future<bool> enabled() async => await account() != null;
  Future<String?> accessToken() async =>
      await enabled() ? _access : storage.read(key: 'jwt_token');
  Future<String?> refreshToken() async =>
      await enabled() ? _refresh : storage.read(key: 'refresh_token');
  Future<void> saveAccess(String token) async {
    if (await enabled()) {
      _access = token;
    } else {
      await storage.write(key: 'jwt_token', value: token);
    }
  }

  Future<void> saveRefresh(String token) async {
    if (await enabled()) {
      _refresh = token;
    } else {
      await storage.write(key: 'refresh_token', value: token);
    }
  }

  Future<void> enable(
    String username,
    String access,
    String refresh, {
    required String credentialId,
    required String deviceToken,
  }) async {
    final capability = await vault.capability();
    if (!capability.available) throw Exception(capability.detail);
    final protected = jsonEncode({
      'username': username,
      'credentialId': credentialId,
      'deviceToken': deviceToken,
    });
    try {
      await vault.write(protected);
      if (await vault.read() != protected) {
        throw Exception('Chưa xác nhận được sinh trắc học. Vui lòng thử lại.');
      }
    } catch (_) {
      try {
        await vault.delete();
      } catch (_) {
        /* No registration is enabled. */
      }
      rethrow;
    }
    await storage.write(
      key: _accountKey,
      value: jsonEncode({'username': username, 'credentialId': credentialId}),
    );
    await deleteStoredSecret(storage, key: 'jwt_token');
    await deleteStoredSecret(storage, key: 'refresh_token');
    _access = access;
    _refresh = refresh;
  }

  Future<BiometricCredential> unlockCredential() async {
    final registration = await _registration();
    if (registration == null) {
      throw Exception(
        'Đăng nhập bằng mật khẩu và bật sinh trắc học trong Tài khoản trước.',
      );
    }
    final capability = await vault.capability();
    if (!capability.available) throw Exception(capability.detail);
    final value = await vault.read();
    if (value != null && value.isNotEmpty) {
      dynamic data;
      try {
        data = jsonDecode(value);
      } on FormatException {
        /* Reject malformed protected data. */
      }
      if (data is Map<String, dynamic> &&
          data['credentialId'] == registration['credentialId'] &&
          data['username'] == registration['username'] &&
          data['deviceToken'] is String) {
        return BiometricCredential(
          data['credentialId'] as String,
          data['deviceToken'] as String,
        );
      }
    }
    await clear();
    throw Exception(
      'Thiết lập sinh trắc học không còn hợp lệ. Đăng nhập bằng mật khẩu và bật lại.',
    );
  }

  void lock() {
    _access = null;
    _refresh = null;
  }

  Future<void> clearSession() async {
    lock();
    await deleteStoredSecret(storage, key: 'jwt_token');
    await deleteStoredSecret(storage, key: 'refresh_token');
  }

  Future<void> disable() async {
    if (_access == null || _refresh == null) {
      throw Exception('Vui lòng đăng nhập trước khi tắt sinh trắc học.');
    }
    await storage.write(key: 'jwt_token', value: _access);
    await storage.write(key: 'refresh_token', value: _refresh);
    try {
      await vault.delete();
    } finally {
      await deleteStoredSecret(storage, key: _accountKey);
      lock();
    }
  }

  Future<void> clear() async {
    try {
      if (await enabled()) await vault.delete();
    } finally {
      await deleteStoredSecret(storage, key: _accountKey);
      await clearSession();
    }
  }
}
