import 'package:careflow_patient/screens/auth/ekyc_screen.dart';
import 'package:careflow_patient/services/ekyc_service.dart';
import 'package:careflow_patient/services/local_document_reader.dart';
import 'package:careflow_patient/services/api_service.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:google_fonts/google_fonts.dart';

class LocalReaderFixture extends LocalDocumentReader {
  int reads = 0;
  @override
  Future<DocumentFields?> read() async {
    reads++;
    return const DocumentFields(
      number: '000000000001',
      name: 'TEST PERSON',
      birth: '02/01/1990',
    );
  }
}

class EmptyReaderFixture extends LocalReaderFixture {
  @override
  Future<DocumentFields?> read() async => const DocumentFields();
}

class ReviewFixture extends EkycService {
  ReviewFixture() : super(ApiService());
  EkycStatus current = const EkycStatus(available: true);
  int submissions = 0;
  bool fail = false;
  final keys = <String>[];
  @override
  Future<EkycStatus> status() async => current;
  @override
  Future<EkycResult> submit({
    required String requestKey,
    required String documentNumber,
    required String fullName,
    required String birthDate,
  }) async {
    submissions++;
    keys.add(requestKey);
    expect(documentNumber, '000000000001');
    expect(fullName, 'TEST PERSON');
    expect(birthDate, '1990-01-02');
    if (fail) throw const FormatException('fixture');
    current = const EkycStatus(
      available: true,
      latest: EkycResult(
        id: 'test',
        status: 'PENDING_REVIEW',
        maskedDocumentNumber: '********0001',
      ),
    );
    return current.latest!;
  }

  @override
  Future<void> withdraw(String id) async {
    current = const EkycStatus(
      available: true,
      latest: EkycResult(id: 'test', status: 'WITHDRAWN'),
    );
  }
}

void main() {
  late ReviewFixture service;
  late LocalReaderFixture reader;
  setUp(() {
    GoogleFonts.config.allowRuntimeFetching = false;
    service = ReviewFixture();
    reader = LocalReaderFixture();
  });
  Future<void> open(WidgetTester tester) async {
    tester.view.physicalSize = const Size(1000, 2000);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          ekycServiceProvider.overrideWithValue(service),
          localDocumentReaderProvider.overrideWithValue(reader),
        ],
        child: const MaterialApp(home: EkycScreen()),
      ),
    );
    await tester.pumpAndSettle();
  }

  Future<void> read(WidgetTester tester) async {
    await tester.tap(find.text('Đọc căn cước trên máy'));
    await tester.pumpAndSettle();
  }

  testWidgets(
    'local read fills editable fields but cannot confirm identity or submit without consent',
    (tester) async {
      await open(tester);
      await read(tester);
      expect(reader.reads, 1);
      expect(find.text('TEST PERSON'), findsOneWidget);
      expect(
        tester
            .widget<FilledButton>(
              find.widgetWithText(FilledButton, 'Gửi yêu cầu đối chiếu'),
            )
            .onPressed,
        isNull,
      );
      expect(service.submissions, 0);
      expect(find.byType(Image), findsNothing);
      await tester.tap(find.byType(CheckboxListTile));
      await tester.pumpAndSettle();
      await tester.tap(find.text('Gửi yêu cầu đối chiếu'));
      await tester.pumpAndSettle();
      expect(service.submissions, 1);
      expect(
        find.text('Chờ đối chiếu giấy tờ gốc tại cơ sở y tế'),
        findsOneWidget,
      );
      expect(find.text('Đã đối chiếu trực tiếp tại cơ sở y tế'), findsNothing);
      expect(find.byType(TextFormField), findsNothing);
    },
  );
  testWidgets(
    'editing extracted data clears consent and requires another review',
    (tester) async {
      await open(tester);
      await read(tester);
      await tester.tap(find.byType(CheckboxListTile));
      await tester.pumpAndSettle();
      await tester.enterText(
        find.byKey(const Key('document-name')),
        'OTHER PERSON',
      );
      await tester.pumpAndSettle();
      expect(
        tester.widget<CheckboxListTile>(find.byType(CheckboxListTile)).value,
        isFalse,
      );
      expect(service.submissions, 0);
    },
  );
  testWidgets(
    'pending request can be withdrawn and only staff can produce confirmation',
    (tester) async {
      service.current = const EkycStatus(
        available: true,
        latest: EkycResult(id: 'test', status: 'PENDING_REVIEW'),
      );
      await open(tester);
      expect(find.text('Đọc căn cước trên máy'), findsNothing);
      await tester.tap(find.text('Hủy yêu cầu và xóa thông tin'));
      await tester.pumpAndSettle();
      expect(
        find.text('Đã hủy yêu cầu và xóa thông tin chờ đối chiếu'),
        findsOneWidget,
      );
      expect(find.text('Đọc căn cước trên máy'), findsOneWidget);
    },
  );
  testWidgets(
    'transport failure keeps request key; no duplicate submission on retry',
    (tester) async {
      service.fail = true;
      await open(tester);
      await read(tester);
      await tester.tap(find.byType(CheckboxListTile));
      await tester.pumpAndSettle();
      for (var i = 0; i < 2; i++) {
        await tester.tap(find.text('Gửi yêu cầu đối chiếu'));
        await tester.pumpAndSettle();
      }
      expect(service.keys, hasLength(2));
      expect(service.keys[0], service.keys[1]);
    },
  );
  testWidgets(
    'unreadable image explains local processing and keeps manual input available',
    (tester) async {
      reader = EmptyReaderFixture();
      await open(tester);
      await read(tester);
      expect(
        find.textContaining('Ảnh đã được xử lý trên điện thoại'),
        findsOneWidget,
      );
      expect(find.byKey(const Key('document-number')), findsOneWidget);
      expect(service.submissions, 0);
    },
  );
}
