import 'dart:async';
import 'dart:convert';
import 'dart:typed_data';
import 'package:careflow_patient/services/api_service.dart';
import 'package:dio/dio.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:flutter_test/flutter_test.dart';

// Explicit transport fixture for concurrency/error unit tests; runtime uses real HTTP.
class ReplyAdapter implements HttpClientAdapter {
  ReplyAdapter(this.reply);
  final Future<ResponseBody> Function(RequestOptions) reply;
  @override
  Future<ResponseBody> fetch(RequestOptions options, Stream<Uint8List>? stream, Future<void>? cancel) => reply(options);
  @override
  void close({bool force = false}) {}
}
ResponseBody body(int status, [Map<String, dynamic> data = const {}]) =>
    ResponseBody.fromString(jsonEncode({'data': data}), status, headers: {Headers.contentTypeHeader: ['application/json']});

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  late Dio client, refresh;
  late ApiService api;
  setUp(() {
    FlutterSecureStorage.setMockInitialValues({'jwt_token': 'old', 'refresh_token': 'refresh-old'});
    client = Dio(BaseOptions(baseUrl: 'https://unit.invalid/api'));
    refresh = Dio(BaseOptions(baseUrl: 'https://unit.invalid/api'));
    api = ApiService(dio: client, refreshDio: refresh);
  });
  test('parallel expired requests rotate once and all retry with the new pair', () async {
    var rotations = 0;
    final sent = <String>[];
    client.httpClientAdapter = ReplyAdapter((request) async {
      final authorization = request.headers['Authorization'] as String;
      sent.add(authorization);
      return body(authorization == 'Bearer new' ? 200 : 401);
    });
    refresh.httpClientAdapter = ReplyAdapter((request) async {
      rotations++;
      await Future<void>.delayed(const Duration(milliseconds: 30));
      return body(200, {'accessToken': 'new', 'refreshToken': 'refresh-new'});
    });
    final results = await Future.wait(['/appointments', '/auth/me', '/auth/login-methods', '/patients', '/notifications'].map((path) => api.get(path)));
    expect(results.every((response) => response.statusCode == 200), isTrue);
    expect(rotations, 1);
    expect(sent.where((value) => value == 'Bearer new'), hasLength(5));
    expect(await api.getRefreshToken(), 'refresh-new');
  });
  test('late 401 from an old request reuses an already refreshed pair', () async {
    final delayed = Completer<void>();
    var rotations = 0;
    client.httpClientAdapter = ReplyAdapter((request) async {
      if (request.headers['Authorization'] == 'Bearer new') return body(200);
      if (request.path == '/slow') await delayed.future;
      return body(401);
    });
    refresh.httpClientAdapter = ReplyAdapter((_) async {
      rotations++;
      return body(200, {'accessToken': 'new', 'refreshToken': 'refresh-new'});
    });
    final slow = api.get('/slow');
    await api.get('/fast');
    delayed.complete();
    await slow;
    expect(rotations, 1);
  });
  test('resource 401 after successful refresh does not erase the rotated pair', () async {
    client.httpClientAdapter = ReplyAdapter((_) async => body(401));
    refresh.httpClientAdapter = ReplyAdapter((_) async => body(200, {'accessToken': 'new', 'refreshToken': 'refresh-new'}));
    await expectLater(api.get('/consultations/appointment/id'), throwsA(isA<DioException>()));
    expect(await api.getToken(), 'new');
    expect(await api.getRefreshToken(), 'refresh-new');
  });
  test('refresh outage preserves credentials for a later retry', () async {
    client.httpClientAdapter = ReplyAdapter((_) async => body(401));
    refresh.httpClientAdapter = ReplyAdapter((_) async => body(503));
    await expectLater(api.get('/appointments'), throwsA(isA<DioException>()));
    expect(await api.getToken(), 'old');
    expect(await api.getRefreshToken(), 'refresh-old');
  });
  test('invalid refresh removes credentials', () async {
    client.httpClientAdapter = ReplyAdapter((_) async => body(401));
    refresh.httpClientAdapter = ReplyAdapter((_) async => body(401));
    await expectLater(api.get('/appointments'), throwsA(isA<DioException>()));
    expect(await api.getToken(), isNull);
    expect(await api.getRefreshToken(), isNull);
  });
  test('Login method reauthentication rejection never attempts session rotation', () async {
    var rotations = 0;
    client.httpClientAdapter = ReplyAdapter((_) async => body(401));
    refresh.httpClientAdapter = ReplyAdapter((_) async {rotations++; return body(500);});
    await expectLater(api.post('/auth/login-methods/question', data: {}), throwsA(isA<DioException>()));
    expect(rotations, 0);
    expect(await api.getToken(), 'old');
  });
}
