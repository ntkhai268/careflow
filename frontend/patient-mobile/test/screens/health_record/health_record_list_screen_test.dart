import 'package:careflow_patient/models/health_record.dart';
import 'package:careflow_patient/providers/health_record_provider.dart';
import 'package:careflow_patient/screens/health_record/health_record_form_screen.dart';
import 'package:careflow_patient/screens/health_record/health_record_list_screen.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:go_router/go_router.dart';
import 'package:careflow_patient/models/patient.dart';
import 'package:careflow_patient/providers/auth_provider.dart';
import 'package:careflow_patient/services/api_service.dart';
import 'package:careflow_patient/services/auth_service.dart';

class ListTestAuth extends AuthNotifier {
  ListTestAuth() : super(AuthService(ApiService())) {
    state = const AuthState(
      status: AuthStatus.authenticated,
      userId: 'synthetic-owner',
    );
  }
}

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
          authProvider.overrideWith((ref) => ListTestAuth()),
          healthRecordPatientProvider('patient-1').overrideWith(
            (ref) async => Patient(
              id: 'patient-1',
              userId: 'synthetic-owner',
              fullName: 'SYNTHETIC OWNER',
            ),
          ),
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
