import 'dart:convert';
import 'dart:io';
import 'package:careflow_patient/config/api_config.dart';
import 'package:careflow_patient/services/api_service.dart';
import 'package:careflow_patient/services/auth_service.dart';
import 'package:dio/dio.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:flutter_test/flutter_test.dart';

class RecoveryHttpOverrides extends HttpOverrides {}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  const base = 'http://127.0.0.1:19844/api';
  final enabled = ApiConfig.baseUrl == base;
  final skip = enabled ? false : 'Run with --dart-define=API_BASE_URL=$base';
  late HttpServer server;
  late ApiService api;
  late AuthService auth;
  final requests = <Map<String, dynamic>>[];
  var rejectConfirmation = false;
  setUpAll(() async {
    if (!enabled) return;
    HttpOverrides.global = RecoveryHttpOverrides();
    server = await HttpServer.bind(InternetAddress.loopbackIPv4, 19844);
    server.listen((request) async {
      final body = jsonDecode(await utf8.decoder.bind(request).join());
      final proof = request.headers.value('DPoP')!;
      final claims = jsonDecode(
        utf8.decode(base64Url.decode(base64Url.normalize(proof.split('.')[1]))),
      );
      requests.add({
        'path': request.uri.path,
        'body': body,
        'authorization': request.headers.value('Authorization'),
        'claims': claims,
      });
      request.response.headers.contentType = ContentType.json;
      if (request.uri.path.endsWith('/request')) {
        request.response.statusCode = 202;
        request.response.write(
          jsonEncode({
            'data': {
              'requestId': 'test-request',
              'expiresInSeconds': 300,
              'resendAfterSeconds': 60,
              'message': 'generic',
            },
          }),
        );
      } else if (request.uri.path.endsWith('/verify')) {
        request.response.write(
          jsonEncode({
            'data': {
              'requestId': 'test-request',
              'resetToken': 'r' * 43,
              'expiresInSeconds': 300,
            },
          }),
        );
      } else if (rejectConfirmation) {
        request.response.statusCode = 400;
        request.response.write(jsonEncode({'message': 'Mã không hợp lệ'}));
      } else {
        request.response.write(
          jsonEncode({
            'data': {'reset': true},
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
    api = ApiService();
    auth = AuthService(api);
    await api.saveToken('stale-access');
    await api.saveRefreshToken('stale-refresh');
    requests.clear();
    rejectConfirmation = false;
  });
  test(
    'email request is anonymous with a signed proof even when old tokens exist',
    () async {
      final result = await auth.requestEmailRecovery(' PATIENT@EXAMPLE.COM ');
      expect(result['requestId'], 'test-request');
      expect(requests.single['body'], {'email': 'patient@example.com'});
      expect(requests.single['authorization'], isNull);
      expect((requests.single['claims'] as Map)['ath'], isNull);
      expect(
        (requests.single['claims'] as Map)['htu'],
        '$base/auth/recovery/email/request',
      );
      expect(await api.getToken(), 'stale-access');
    },
    skip: skip,
  );
  test(
    'successful confirmation sends exact contract and deletes old local session',
    () async {
      await auth.confirmEmailRecovery(
        'test-request',
        'r' * 43,
        'NewPassword123!',
      );
      expect(requests.single['body'], {
        'requestId': 'test-request',
        'resetToken': 'r' * 43,
        'newPassword': 'NewPassword123!',
      });
      expect(requests.single['authorization'], isNull);
      expect(await api.getToken(), isNull);
      expect(await api.getRefreshToken(), isNull);
    },
    skip: skip,
  );
  test(
    'bad code is submitted once with no token refresh and preserves local state',
    () async {
      rejectConfirmation = true;
      await expectLater(
        auth.confirmEmailRecovery('test-request', 'r' * 43, 'NewPassword123!'),
        throwsA(isA<DioException>()),
      );
      expect(requests.length, 1);
      expect(await api.getToken(), 'stale-access');
    },
    skip: skip,
  );
  test(
    'code verification exchanges only the code for a reset permission',
    () async {
      final result = await auth.verifyEmailRecovery('test-request', '012345');
      expect(result['resetToken'], 'r' * 43);
      expect(requests.single['body'], {
        'requestId': 'test-request',
        'code': '012345',
      });
      expect(requests.single['authorization'], isNull);
      expect(await api.getToken(), 'stale-access');
    },
    skip: skip,
  );
}
