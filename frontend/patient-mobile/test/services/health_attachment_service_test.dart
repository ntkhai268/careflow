import 'dart:async';
import 'dart:io';
import 'dart:typed_data';
import 'package:careflow_patient/models/health_record.dart';
import 'package:careflow_patient/services/api_service.dart';
import 'package:careflow_patient/services/medical_file_cache.dart';
import 'package:careflow_patient/services/health_attachment_service.dart';
import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';

class AttachmentApi extends ApiService {
  List<int> bytes = '%PDF-1.4\nfixture'.codeUnits;
  int? claimedLength;
  int status = 200;
  String? endpoint;
  Completer<void>? pause;
  @override
  Future<Response> stream(
    String endpoint, {
    required CancelToken cancelToken,
  }) async {
    this.endpoint = endpoint;
    if (status != 200) {
      throw DioException(
        requestOptions: RequestOptions(path: endpoint),
        response: Response(
          requestOptions: RequestOptions(path: endpoint),
          statusCode: status,
        ),
      );
    }
    Stream<Uint8List> chunks() async* {
      yield Uint8List.fromList(bytes.take(8).toList());
      await pause?.future;
      yield Uint8List.fromList(bytes.skip(8).toList());
    }

    return Response(
      requestOptions: RequestOptions(path: endpoint),
      data: ResponseBody(
        chunks(),
        200,
        headers: {
          'content-length': ['${claimedLength ?? bytes.length}'],
        },
      ),
    );
  }
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  late Directory temporary;
  late MedicalFileCache cache;
  late AttachmentApi api;
  late HealthAttachmentService service;
  const patient = 'f0000001-0000-0000-0000-000000000001';
  final attachment = HealthRecordFileInfo(
    id: 'f0000002-0000-0000-0000-000000000001',
    fileName: '../../medical.pdf',
    fileUrl: 'https://untrusted.invalid/file',
  );
  setUp(() async {
    temporary = await Directory.systemTemp.createTemp('careflow-medical-test-');
    cache = MedicalFileCache(temporaryDirectory: () async => temporary);
    api = AttachmentApi();
    service = HealthAttachmentService(api, cache: cache);
  });
  tearDown(() async {
    await cache.clear();
    if (await temporary.exists()) await temporary.delete(recursive: true);
  });
  test(
    'downloads exact bytes through owned patient/file route, ignores supplied URL and path',
    () async {
      final file = await service.download(
        patient,
        attachment,
        cancel: CancelToken(),
      );
      expect(await file.readAsBytes(), api.bytes);
      expect(
        api.endpoint,
        '/patients/$patient/health-records/files/${attachment.id}',
      );
      expect(file.path.startsWith(temporary.path), isTrue);
      expect(file.uri.pathSegments.last, 'ho-so.pdf');
      await cache.clear();
      expect(await file.exists(), isFalse);
    },
  );
  test(
    'rejects mismatched content and removes partially written file',
    () async {
      api.bytes = 'not a PDF'.codeUnits;
      await expectLater(
        service.download(patient, attachment, cancel: CancelToken()),
        throwsFormatException,
      );
      expect(
        await temporary.list(recursive: true).where((f) => f is File).length,
        0,
      );
    },
  );
  test('rejects excessive declared size before creating a file', () async {
    api.claimedLength = HealthAttachmentService.maxBytes + 1;
    await expectLater(
      service.download(patient, attachment, cancel: CancelToken()),
      throwsFormatException,
    );
    expect(
      await temporary.list(recursive: true).where((f) => f is File).length,
      0,
    );
  });
  test('truncated response never becomes an openable file', () async {
    api.claimedLength = api.bytes.length + 1;
    await expectLater(
      service.download(patient, attachment, cancel: CancelToken()),
      throwsFormatException,
    );
    expect(
      await temporary.list(recursive: true).where((f) => f is File).length,
      0,
    );
  });
  test(
    'another patient rejection preserves server failure and writes nothing',
    () async {
      api.status = 403;
      await expectLater(
        service.download(patient, attachment, cancel: CancelToken()),
        throwsA(isA<DioException>()),
      );
      expect(
        await temporary.list(recursive: true).where((f) => f is File).length,
        0,
      );
    },
  );
  test(
    'changing session cancels in-flight download and cannot recreate old data',
    () async {
      api.pause = Completer();
      final operation = service.download(
        patient,
        attachment,
        cancel: CancelToken(),
      );
      final rejected = expectLater(operation, throwsFormatException);
      await Future<void>.delayed(const Duration(milliseconds: 20));
      await cache.clear();
      api.pause!.complete();
      await rejected;
      expect(
        await temporary.list(recursive: true).where((f) => f is File).length,
        0,
      );
    },
  );
}
