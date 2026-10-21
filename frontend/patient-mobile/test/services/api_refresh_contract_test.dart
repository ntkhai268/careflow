import 'dart:convert';
import 'dart:io';

import 'package:careflow_patient/config/api_config.dart';
import 'package:careflow_patient/services/api_service.dart';
import 'package:dio/dio.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:flutter_test/flutter_test.dart';

class RealTestHttp extends HttpOverrides {}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  const localBase = 'http://127.0.0.1:19843/api';
  final enabled = ApiConfig.baseUrl == localBase;
  late HttpServer server;
  late ApiService api;
  var refreshes = 0, questionRequests = 0;
  setUpAll(() async {
    if (!enabled) return;
    HttpOverrides.global = RealTestHttp();
    server = await HttpServer.bind(InternetAddress.loopbackIPv4, 19843);
    server.listen((request) async {
      await utf8.decoder.bind(request).join();
      request.response.headers.contentType = ContentType.json;
      if (request.uri.path.endsWith('/auth/refresh')) {
        refreshes++;
        await Future<void>.delayed(const Duration(milliseconds: 60));
        request.response.write(
          jsonEncode({
            'data': {'accessToken': 'new-access', 'refreshToken': 'rotated'},
          }),
        );
      } else if (request.uri.path.contains('/auth/login/question') ||
          request.uri.path.contains('/auth/login-methods/question')) {
        questionRequests++;
        request.response.statusCode = 401;
        request.response.write(
          jsonEncode({'message': 'Thông tin xác thực không hợp lệ'}),
        );
      } else if (request.headers.value('Authorization') ==
          'Bearer new-access') {
        request.response.write(
          jsonEncode({
            'data': {'ok': true},
          }),
        );
      } else {
        request.response.statusCode = 401;
        request.response.write('{}');
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
    refreshes = 0;
    questionRequests = 0;
    await api.saveToken('old-access');
    await api.saveRefreshToken('old-refresh');
  });
  final skip = enabled
      ? false
      : 'Run with --dart-define=API_BASE_URL=$localBase (local HTTP fixture only).';
  test('parallel unauthorized resources share one refresh rotation', () async {
    final responses = await Future.wait([
      api.get('/protected/a'),
      api.get('/protected/b'),
    ]);
    expect(responses.every((r) => r.statusCode == 200), isTrue);
    expect(refreshes, 1);
    expect(await api.getToken(), 'new-access');
    expect(await api.getRefreshToken(), 'rotated');
  }, skip: skip);
  test(
    'wrong question answer is submitted once without automatic retry',
    () async {
      await expectLater(
        api.post('/auth/login/question', data: {'answer': 'wrong'}),
        throwsA(isA<DioException>()),
      );
      expect(questionRequests, 1);
      expect(refreshes, 0);
    },
    skip: skip,
  );
  test(
    'wrong password in question settings cannot consume two lockout attempts',
    () async {
      await expectLater(
        api.post('/auth/login-methods/question', data: {'password': 'wrong'}),
        throwsA(isA<DioException>()),
      );
      expect(questionRequests, 1);
      expect(refreshes, 0);
    },
    skip: skip,
  );
}
