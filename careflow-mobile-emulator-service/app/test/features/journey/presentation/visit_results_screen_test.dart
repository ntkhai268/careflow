import 'package:careflow_patient/features/journey/application/journey_providers.dart';
import 'package:careflow_patient/features/journey/domain/journey_models.dart';
import 'package:careflow_patient/features/journey/presentation/visit_outcome_screen.dart';
import 'package:careflow_patient/features/journey/presentation/visit_results_screen.dart';
import 'package:careflow_patient/models/appointment.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:go_router/go_router.dart';

void main() {
  testWidgets('lists a completed visit and opens its outcome details', (
    tester,
  ) async {
    final result = completedVisitResult();
    final router = GoRouter(
      initialLocation: '/visit-results',
      routes: [
        GoRoute(
          path: '/visit-results',
          builder: (_, _) => const VisitResultsScreen(),
        ),
        GoRoute(
          path: '/journey/:appointmentId/outcome',
          builder: (_, state) => VisitOutcomeScreen(
            appointmentId: state.pathParameters['appointmentId']!,
            initialJourney: state.extra as PatientJourney?,
          ),
        ),
      ],
    );
    addTearDown(router.dispose);

    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          visitResultsProvider.overrideWith((ref) async => [result]),
        ],
        child: MaterialApp.router(routerConfig: router),
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('Nội tổng quát'), findsOneWidget);
    expect(find.text('Viêm họng cấp'), findsOneWidget);
    expect(find.text('Toa thuốc'), findsOneWidget);
    expect(find.text('2 loại thuốc'), findsOneWidget);

    await tester.tap(find.text('Xem kết quả và toa thuốc'));
    await tester.pumpAndSettle();

    expect(find.text('Kết quả lượt khám'), findsOneWidget);
    expect(find.text('Paracetamol 500 mg'), findsOneWidget);
  });

  testWidgets('shows an actionable empty state when there are no results', (
    tester,
  ) async {
    await tester.pumpWidget(
      ProviderScope(
        overrides: [visitResultsProvider.overrideWith((ref) async => const [])],
        child: const MaterialApp(home: VisitResultsScreen()),
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('Chưa có kết quả khám'), findsOneWidget);
    expect(
      find.textContaining('Kết quả khám, xét nghiệm và toa thuốc'),
      findsOneWidget,
    );
  });
}

VisitResult completedVisitResult() => VisitResult(
  appointment: Appointment(
    id: 'apt-result-1',
    patientId: 'patient-1',
    department: 'NOI_TONG_QUAT',
    departmentDisplayName: 'Nội tổng quát',
    appointmentDate: DateTime.utc(2026, 8, 6),
    timeSlot: '08:00 - 09:00',
    status: 'COMPLETED',
    statusDisplayName: 'Hoàn thành',
  ),
  journey: PatientJourney(
    appointmentId: 'apt-result-1',
    patientId: 'patient-1',
    status: JourneyStatus.completed,
    diagnosis: const DiagnosisSummary(
      title: 'Viêm họng cấp',
      detail: 'Niêm mạc họng sung huyết.',
    ),
    prescription: Prescription(
      id: 'prescription-1',
      issuedAt: DateTime.utc(2026, 8, 6),
      items: const [
        PrescriptionItem(
          medicationName: 'Paracetamol 500 mg',
          dosage: '1 viên',
          route: 'Uống',
          frequency: '3 lần/ngày',
          duration: '3 ngày',
          caution: 'Uống sau ăn.',
        ),
        PrescriptionItem(
          medicationName: 'Amoxicillin 500 mg',
          dosage: '1 viên',
          route: 'Uống',
          frequency: '2 lần/ngày',
          duration: '5 ngày',
          caution: 'Dùng đủ liều.',
        ),
      ],
    ),
    laboratoryOrders: const [],
    timeline: const [],
    notifications: const [],
    updatedAt: DateTime.utc(2026, 8, 6),
  ),
);
