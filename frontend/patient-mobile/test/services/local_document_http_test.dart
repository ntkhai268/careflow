import 'dart:convert';
import 'dart:io';
import 'package:careflow_patient/config/api_config.dart';
import 'package:careflow_patient/services/api_service.dart';
import 'package:careflow_patient/services/ekyc_service.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:flutter_test/flutter_test.dart';

class DocumentHttpOverrides extends HttpOverrides {}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  const base = 'http://127.0.0.1:19846/api';
  final enabled = ApiConfig.baseUrl == base;
  final skip = enabled ? false : 'Requires isolated document HTTP fixture';
  late HttpServer server;
  late EkycService service;
  final requests = <Map<String, dynamic>>[];
  setUpAll(() async {
    if (!enabled) return;
    HttpOverrides.global = DocumentHttpOverrides();
    server = await HttpServer.bind(InternetAddress.loopbackIPv4, 19846);
    server.listen((request) async {
      requests.add({
        'path': request.uri.path,
        'type': request.headers.contentType?.mimeType,
        'key': request.headers.value('Idempotency-Key'),
        'proof': request.headers.value('DPoP'),
        'auth': request.headers.value('Authorization'),
        'body': jsonDecode(await utf8.decoder.bind(request).join()),
      });
      request.response.headers.contentType = ContentType.json;
      request.response.write(
        jsonEncode({
          'data': {
            'id': 'test',
            'status': 'PENDING_REVIEW',
            'maskedDocumentNumber': '********0001',
          },
        }),
      );
      await request.response.close();
    });
  });
  tearDownAll(() async {
    if (enabled) await server.close(force: true);
  });
  setUp(() async {
    FlutterSecureStorage.setMockInitialValues({});
    final api = ApiService();
    await api.saveToken('fixture-access');
    service = EkycService(api);
    requests.clear();
  });
  test(
    'only consented fields cross HTTP; no photo, selfie, OCR text or provider credential',
    () async {
      final result = await service.submit(
        requestKey: 'test-key',
        documentNumber: '000000000001',
        fullName: 'TEST PERSON',
        birthDate: '1990-01-02',
      );
      expect(result.status, 'PENDING_REVIEW');
      expect(requests.single['body'], {
        'documentNumber': '000000000001',
        'fullName': 'TEST PERSON',
        'birthDate': '1990-01-02',
        'consent': true,
      });
      expect(requests.single['type'], 'application/json');
      expect(requests.single['key'], 'test-key');
      expect(requests.single['auth'], 'Bearer fixture-access');
      expect(requests.single['proof'], isNotEmpty);
      expect(requests.single['path'], '/api/auth/identity-document/requests');
    },
    skip: skip,
  );
  test('withdrawal sends no identity fields', () async {
    await service.withdraw('test');
    expect(requests.single['body'], isEmpty);
    expect(
      requests.single['path'],
      '/api/auth/identity-document/requests/test/withdraw',
    );
  }, skip: skip);
}
