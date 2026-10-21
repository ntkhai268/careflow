import 'dart:convert';
import 'dart:io';
import 'package:careflow_patient/config/api_config.dart';
import 'package:careflow_patient/models/health_record.dart';
import 'package:careflow_patient/services/api_service.dart';
import 'package:careflow_patient/services/health_attachment_service.dart';
import 'package:careflow_patient/services/medical_file_cache.dart';
import 'package:dio/dio.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:flutter_test/flutter_test.dart';

class DownloadHttp extends HttpOverrides {}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  const enabled = ApiConfig.baseUrl == 'http://127.0.0.1:19844/api';
  test(
    'medical stream carries bound authorization without putting credentials in the URL',
    () async {
      FlutterSecureStorage.setMockInitialValues({});
      HttpOverrides.global = DownloadHttp();
      final root = await Directory.systemTemp.createTemp(
        'careflow-stream-http-',
      );
      final server = await HttpServer.bind(InternetAddress.loopbackIPv4, 19844);
      final observed = <String, Object?>{};
      final payload = '%PDF-1.4\nHTTP file fixture'.codeUnits;
      server.listen((request) async {
        observed['authorization'] = request.headers.value('Authorization');
        final proof = request.headers.value('DPoP')!;
        observed['proof'] = jsonDecode(
          utf8.decode(
            base64Url.decode(base64Url.normalize(proof.split('.')[1])),
          ),
        );
        observed['query'] = request.uri.query;
        request.response.headers.contentType = ContentType.binary;
        request.response.contentLength = payload.length;
        request.response.add(payload);
        await request.response.close();
      });
      final cache = MedicalFileCache(temporaryDirectory: () async => root);
      try {
        final api = ApiService();
        await api.saveToken('download-test-access');
        final file = await HealthAttachmentService(api, cache: cache).download(
          'f0000001-0000-0000-0000-000000000001',
          HealthRecordFileInfo(
            id: 'f0000002-0000-0000-0000-000000000001',
            fileName: 'medical.pdf',
            fileUrl: '',
          ),
          cancel: CancelToken(),
        );
        expect(await file.readAsBytes(), payload);
        expect(observed['authorization'], 'Bearer download-test-access');
        final proof = observed['proof'] as Map;
        expect(proof['htm'], 'GET');
        expect(proof['ath'], isNotEmpty);
        expect(observed['query'], isEmpty);
      } finally {
        await cache.clear();
        await root.delete(recursive: true);
        await server.close(force: true);
        HttpOverrides.global = null;
      }
    },
    skip: enabled
        ? false
        : 'Requires isolated HTTP fixture API_BASE_URL=http://127.0.0.1:19844/api',
  );
}
