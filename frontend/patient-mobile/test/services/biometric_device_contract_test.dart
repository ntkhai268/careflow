import 'dart:convert';
import 'dart:io';
import 'package:careflow_patient/config/api_config.dart';
import 'package:careflow_patient/services/api_service.dart';
import 'package:careflow_patient/services/auth_service.dart';
import 'package:careflow_patient/services/biometric_session_store.dart';
import 'package:dio/dio.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:flutter_test/flutter_test.dart';
import 'biometric_session_store_test.dart' show TestVault;

class DeviceHttpOverrides extends HttpOverrides {}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  const base = 'http://127.0.0.1:19845/api';
  final enabled = ApiConfig.baseUrl == base;
  final skip = enabled ? false : 'Run with --dart-define=API_BASE_URL=$base';
  late HttpServer server;
  late TestVault vault;
  late ApiService api;
  late AuthService auth;
  final calls = <Map<String, dynamic>>[];
  bool wrongPassword = false;
  const id = '80000000-0000-0000-0000-000000000001';
  setUpAll(() async {
    if (!enabled) return;
    HttpOverrides.global = DeviceHttpOverrides();
    server = await HttpServer.bind(InternetAddress.loopbackIPv4, 19845);
    server.listen((request) async {
      final text = await utf8.decoder.bind(request).join();
      calls.add({
        'path': request.uri.path,
        'body': text.isEmpty ? null : jsonDecode(text),
        'authorization': request.headers.value('Authorization'),
      });
      request.response.headers.contentType = ContentType.json;
      if (request.uri.path.endsWith('/biometric-devices') && wrongPassword) {
        request.response.statusCode = 401;
        request.response.write(
          jsonEncode({'message': 'Mật khẩu hiện tại không đúng.'}),
        );
      } else if (request.uri.path.endsWith('/biometric-devices')) {
        request.response.statusCode = 201;
        request.response.write(
          jsonEncode({
            'data': {'credentialId': id, 'deviceToken': 'd' * 43},
          }),
        );
      } else if (request.uri.path.endsWith('/login/biometric')) {
        request.response.write(
          jsonEncode({
            'data': {
              'accessToken': 'fresh-access',
              'refreshToken': 'fresh-refresh',
              'user': {
                'id': 'patient-id',
                'username': 'patient',
                'email': 'patient@example.test',
                'role': 'PATIENT',
              },
            },
          }),
        );
      } else if (request.uri.path.endsWith('/auth/me')) {
        request.response.write(
          jsonEncode({
            'data': {
              'id': 'patient-id',
              'username': 'patient',
              'role': 'PATIENT',
            },
          }),
        );
      } else if (request.uri.path.endsWith('/auth/refresh') ||
          request.uri.path.endsWith('/protected')) {
        request.response.statusCode = 401;
        request.response.write('{}');
      } else {
        request.response.write(
          jsonEncode({
            'data': {'ok': true},
          }),
        );
      }
      await request.response.close();
    });
  });
  tearDownAll(() async {
    if (enabled) await server.close(force: true);
  });
  setUp(() async {
    FlutterSecureStorage.setMockInitialValues({});
    vault = TestVault();
    api = ApiService(sessions: BiometricSessionStore(vault: vault));
    auth = AuthService(api);
    calls.clear();
    wrongPassword = false;
    await api.saveToken('password-access');
    await api.saveRefreshToken('password-refresh');
  });
  test(
    'logout then Face ID sends dedicated credential and receives fresh session',
    () async {
      await auth.enableBiometrics('Password123!');
      final protected = vault.secret;
      await auth.logout();
      expect(await api.getToken(), isNull);
      expect(await api.sessions.enabled(), isTrue);
      await auth.biometricLogin();
      final login = calls.singleWhere(
        (c) => (c['path'] as String).endsWith('/login/biometric'),
      );
      expect(login['authorization'], isNull);
      expect(login['body'], {'credentialId': id, 'deviceToken': 'd' * 43});
      expect(await api.getToken(), 'fresh-access');
      expect(await api.getRefreshToken(), 'fresh-refresh');
      expect(vault.secret, protected);
      expect(
        calls.where((c) => (c['path'] as String).endsWith('/auth/login')),
        isEmpty,
      );
    },
    skip: skip,
  );
  test(
    'wrong enrollment password is submitted once without refresh retry',
    () async {
      wrongPassword = true;
      await expectLater(
        auth.enableBiometrics('WrongPassword1!'),
        throwsA(isA<DioException>()),
      );
      expect(
        calls
            .where((c) => (c['path'] as String).endsWith('/biometric-devices'))
            .length,
        1,
      );
      expect(
        calls.where((c) => (c['path'] as String).endsWith('/auth/refresh')),
        isEmpty,
      );
      expect(await api.sessions.enabled(), isFalse);
    },
    skip: skip,
  );
  test(
    'expired refresh clears session but device can still establish new login',
    () async {
      await auth.enableBiometrics('Password123!');
      await expectLater(api.get('/protected'), throwsA(isA<DioException>()));
      expect(await api.getToken(), isNull);
      expect(await api.sessions.enabled(), isTrue);
      await auth.biometricLogin();
      expect(await api.getToken(), 'fresh-access');
    },
    skip: skip,
  );
  test('disable calls server and keeps active session usable', () async {
    await auth.enableBiometrics('Password123!');
    await auth.disableBiometrics();
    expect(
      calls.any((c) => c['path'] == '/api/auth/biometric-devices/$id'),
      isTrue,
    );
    expect(await api.sessions.enabled(), isFalse);
    expect(vault.secret, isNull);
    expect(await api.getToken(), 'password-access');
  }, skip: skip);
}
