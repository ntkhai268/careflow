import 'dart:convert';
import 'dart:async';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:go_router/go_router.dart';
import 'package:dio/dio.dart';
import 'package:careflow_patient/models/patient.dart';
import 'package:careflow_patient/providers/auth_provider.dart';
import 'package:careflow_patient/providers/health_record_provider.dart';
import 'package:careflow_patient/services/api_service.dart';
import 'package:careflow_patient/services/auth_service.dart';
import 'package:careflow_patient/services/health_record_service.dart';
import 'package:careflow_patient/screens/health_record/health_record_form_screen.dart';

const patientId = 'f0000001-0000-0000-0000-000000000001';

class FormAuth extends AuthNotifier {
  FormAuth() : super(AuthService(ApiService())) {
    state = const AuthState(
      status: AuthStatus.authenticated,
      userId: 'owner-a',
    );
  }
  void change() {
    state = const AuthState(
      status: AuthStatus.authenticated,
      userId: 'owner-b',
    );
  }
}

class FormApi extends ApiService {
  Map<String, dynamic>? submitted;
  Completer<void>? paused;
  @override
  Future<Response> upload(
    String endpoint, {
    required FormData data,
    required CancelToken cancelToken,
    void Function(int, int)? onSendProgress,
  }) async {
    final request = data.files
        .singleWhere((part) => part.key == 'request')
        .value;
    final bytes = await request.finalize().fold<List<int>>(
      [],
      (bytes, chunk) => bytes..addAll(chunk),
    );
    submitted = Map<String, dynamic>.from(
      jsonDecode(utf8.decode(bytes)) as Map,
    );
    await paused?.future;
    return Response(
      requestOptions: RequestOptions(path: endpoint),
      data: {
        'data': {
          ...submitted!,
          'id': 'record',
          'patientId': patientId,
          'files': [],
        },
      },
    );
  }
}

void main() {
  Future<void> open(WidgetTester tester, FormAuth auth, FormApi api) async {
    final router = GoRouter(
      routes: [
        GoRoute(
          path: '/',
          builder: (context, state) => Scaffold(
            body: TextButton(
              onPressed: () => context.push('/new'),
              child: const Text('Tạo hồ sơ'),
            ),
          ),
        ),
        GoRoute(
          path: '/new',
          builder: (context, state) =>
              const HealthRecordFormScreen(patientId: patientId),
        ),
      ],
    );
    addTearDown(router.dispose);
    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          authProvider.overrideWith((ref) => auth),
          healthRecordPatientProvider.overrideWith(
            (ref, id) async => Patient(
              id: id,
              userId: 'owner-a',
              fullName: 'SYNTHETIC TRUSTED OWNER',
            ),
          ),
          healthRecordServiceProvider.overrideWithValue(
            HealthRecordService(api),
          ),
        ],
        child: MaterialApp.router(routerConfig: router),
      ),
    );
    await tester.tap(find.text('Tạo hồ sơ'));
    await tester.pumpAndSettle();
    expect(find.text('SYNTHETIC TRUSTED OWNER'), findsOneWidget);
    await tester.enterText(find.byType(TextFormField).at(0), 'SYNTHETIC FORM');
    await tester.enterText(
      find.byType(TextFormField).at(2),
      'SYNTHETIC FACILITY',
    );
    await tester.tap(find.byType(TextFormField).at(1));
    await tester.pumpAndSettle();
    await tester.tap(find.text('OK'));
    await tester.pumpAndSettle();
    await tester.scrollUntilVisible(
      find.text('Hoàn tất'),
      400,
      scrollable: find.byType(Scrollable).first,
    );
    await tester.tap(find.text('Hoàn tất'));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 50));
  }

  testWidgets(
    'form shows the verified owner and submits an actual date-only JSON field',
    (tester) async {
      final api = FormApi();
      await open(tester, FormAuth(), api);
      await tester.pumpAndSettle();
      final now = DateTime.now();
      expect(
        api.submitted!['recordDate'],
        '${now.year.toString().padLeft(4, '0')}-${now.month.toString().padLeft(2, '0')}-${now.day.toString().padLeft(2, '0')}',
      );
      expect(find.text('Tạo hồ sơ sức khỏe thành công!'), findsOneWidget);
    },
  );
  testWidgets(
    'changing account while uploading hides prior identity and discards late success',
    (tester) async {
      final auth = FormAuth(), api = FormApi()..paused = Completer<void>();
      await open(tester, auth, api);
      expect(api.submitted, isNotNull);
      auth.change();
      await tester.pump();
      expect(find.text('SYNTHETIC TRUSTED OWNER'), findsNothing);
      api.paused!.complete();
      await tester.pumpAndSettle();
      expect(find.text('Tạo hồ sơ sức khỏe thành công!'), findsNothing);
      expect(find.textContaining('Phiên đã thay đổi'), findsOneWidget);
    },
  );
}
