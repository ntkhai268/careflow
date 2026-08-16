import 'package:careflow_patient/models/health_record.dart';
import 'package:careflow_patient/providers/health_record_provider.dart';
import 'package:careflow_patient/screens/health_record/health_record_form_screen.dart';
import 'package:careflow_patient/screens/health_record/health_record_list_screen.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:go_router/go_router.dart';

void main() {
  testWidgets('upload action opens the registered health record form route', (
    tester,
  ) async {
    final router = GoRouter(
      initialLocation: '/',
      routes: [
        GoRoute(
          path: '/',
          builder: (_, _) => const HealthRecordListScreen(
            patientId: 'patient-1',
            patientName: 'Nguyễn An',
            patientGender: 'Nam',
            patientBirthYear: 1995,
          ),
        ),
        GoRoute(
          path: '/patient/:patientId/health-records/new',
          builder: (_, state) => HealthRecordFormScreen(
            patientId: state.pathParameters['patientId']!,
          ),
        ),
      ],
    );
    addTearDown(router.dispose);

    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          healthRecordsProvider(
            'patient-1',
          ).overrideWith((ref) async => <HealthRecord>[]),
        ],
        child: MaterialApp.router(routerConfig: router),
      ),
    );
    await tester.pumpAndSettle();

    final uploadButton = find.byKey(const Key('upload-health-record-button'));
    await tester.ensureVisible(uploadButton);
    await tester.tap(uploadButton);

    expect(
      router.routeInformationProvider.value.uri.path,
      '/patient/patient-1/health-records/new',
    );
  });
}
