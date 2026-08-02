import 'dart:async';

import 'package:careflow_patient/config/router.dart';
import 'package:careflow_patient/features/journey/application/journey_controller.dart';
import 'package:careflow_patient/features/journey/application/journey_providers.dart';
import 'package:careflow_patient/features/journey/data/journey_repository.dart';
import 'package:careflow_patient/features/journey/domain/journey_models.dart';
import 'package:careflow_patient/features/journey/domain/journey_transition.dart';
import 'package:careflow_patient/models/appointment.dart';
import 'package:careflow_patient/models/patient.dart';
import 'package:careflow_patient/providers/auth_provider.dart';
import 'package:careflow_patient/providers/patient_provider.dart';
import 'package:careflow_patient/services/api_service.dart';
import 'package:careflow_patient/services/auth_service.dart';
import 'package:careflow_patient/services/patient_service.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  test('journey providers deny every non-authenticated account state', () {
    final auth = SecurityAuthNotifier();
    final patient = SecurityPatientNotifier(patientA);
    final controller = JourneyController(
      repository: const UnavailableJourneyRepository(),
      demoMode: true,
    )..state = AsyncData(formerJourney);
    final container = ProviderContainer(
      overrides: [
        authProvider.overrideWith((ref) => auth),
        patientProvider.overrideWith((ref) => patient),
        journeyControllerProvider.overrideWith((ref) => controller),
      ],
    );
    addTearDown(container.dispose);

    for (final status in [
      AuthStatus.initial,
      AuthStatus.loading,
      AuthStatus.unauthenticated,
      AuthStatus.error,
    ]) {
      auth.setSession(status: status);

      expect(
        container.read(activeJourneyProvider),
        isNull,
        reason: '$status must hide the active journey',
      );
      expect(
        container
            .read(journeyForAppointmentProvider('former-appointment'))
            .valueOrNull,
        isNull,
        reason: '$status must hide route-level journey data',
      );
      expect(container.read(unreadJourneyNotificationCountProvider), 0);
    }
  });

  testWidgets(
    'old journey URLs expose no medical data after logout or account switch',
    (tester) async {
      final auth = SecurityAuthNotifier()
        ..setSession(status: AuthStatus.authenticated, userId: 'user-a');
      final patient = SecurityPatientNotifier(patientA);
      final controller = JourneyController(
        repository: const UnavailableJourneyRepository(),
        demoMode: true,
      )..state = AsyncData(formerJourney);

      await tester.pumpWidget(
        ProviderScope(
          overrides: [
            authProvider.overrideWith((ref) => auth),
            patientProvider.overrideWith((ref) => patient),
            journeyControllerProvider.overrideWith((ref) => controller),
          ],
          child: MaterialApp.router(routerConfig: appRouter),
        ),
      );

      appRouter.go('/journey/former-appointment/ticket');
      await tester.pumpAndSettle();
      expect(find.text('Former Hospital Secret'), findsOneWidget);

      auth.setSession(status: AuthStatus.unauthenticated);
      await tester.pump();
      await _assertOldUrlsHideMedicalData(tester);

      auth.setSession(status: AuthStatus.authenticated, userId: 'user-b');
      patient.switchTo(patientB);
      await tester.pump();
      await _assertOldUrlsHideMedicalData(tester);
    },
  );

  test(
    'auth identity change invalidates pending old-account bootstrap',
    () async {
      final auth = SecurityAuthNotifier()
        ..setSession(status: AuthStatus.authenticated, userId: 'user-a');
      final patient = SecurityPatientNotifier(patientA);
      final repository = PendingBootstrapRepository();
      final container = ProviderContainer(
        overrides: [
          authProvider.overrideWith((ref) => auth),
          patientProvider.overrideWith((ref) => patient),
          demoModeProvider.overrideWithValue(true),
          journeyRepositoryProvider.overrideWithValue(repository),
        ],
      );
      addTearDown(container.dispose);

      final pending = container
          .read(journeyControllerProvider.notifier)
          .bootstrap(appointment: formerAppointment, patientId: patientA.id);
      await repository.started.future;

      auth.setSession(status: AuthStatus.authenticated, userId: 'user-b');
      patient.switchTo(patientB);
      repository.result.complete(formerJourney);
      await pending;

      expect(container.read(journeyControllerProvider).valueOrNull, isNull);
      expect(
        container
            .read(journeyForAppointmentProvider('former-appointment'))
            .valueOrNull,
        isNull,
      );
    },
  );
}

Future<void> _assertOldUrlsHideMedicalData(WidgetTester tester) async {
  final pathsAndSecrets = <String, String>{
    '/journey/former-appointment': 'Đang khám bệnh',
    '/journey/former-appointment/ticket': 'Former Hospital Secret',
    '/journey/former-appointment/queue': 'Former Clinic Room Secret',
    '/journey/former-appointment/consultation': 'Former Doctor Secret',
    '/journey/former-appointment/laboratory': 'Former Lab Secret',
    '/journey/former-appointment/result-review': 'Former Review Room Secret',
    '/journey/former-appointment/outcome': 'Former Diagnosis Secret',
    '/journey/former-appointment/timeline': 'Former Timeline Secret',
  };

  for (final entry in pathsAndSecrets.entries) {
    appRouter.go(entry.key);
    await tester.pumpAndSettle();
    expect(
      find.textContaining(entry.value),
      findsNothing,
      reason: '${entry.key} leaked former-account medical data',
    );
  }
}

final patientA = Patient(
  id: 'patient-a',
  userId: 'user-a',
  fullName: 'Former Patient',
);

final patientB = Patient(
  id: 'patient-b',
  userId: 'user-b',
  fullName: 'Current Patient',
);

final formerAppointment = Appointment(
  id: 'former-appointment',
  patientId: patientA.id,
  department: 'NOI_TONG_QUAT',
  departmentDisplayName: 'Former Specialty',
  appointmentDate: DateTime.utc(2026, 7, 30),
  timeSlot: '08:00 - 08:30',
  status: 'CONFIRMED',
  statusDisplayName: 'Đã xác nhận',
);

final formerJourney = PatientJourney(
  appointmentId: formerAppointment.id,
  patientId: patientA.id,
  status: JourneyStatus.inConsultation,
  doctorName: 'Former Doctor Secret',
  ticket: const VisitTicket(
    code: 'FORMER-CODE',
    qrPayload: 'former-secret-qr',
    queueNumber: '42',
    hospitalName: 'Former Hospital Secret',
    specialtyName: 'Former Specialty Secret',
    room: 'Former Clinic Room Secret',
    expectedWindow: '08:00 - 08:30',
  ),
  clinicQueue: const QueueSnapshot(
    room: 'Former Clinic Room Secret',
    peopleAhead: 1,
    expectedWait: '5 phút',
  ),
  laboratoryOrders: [
    LaboratoryOrder(
      id: 'former-lab',
      name: 'Former Lab Secret',
      department: 'Former Lab Department',
      destination: 'Former Lab Destination',
      preparationNote: 'Former Lab Preparation',
      price: 100000,
    ),
  ],
  resultReviewQueue: const QueueSnapshot(
    room: 'Former Review Room Secret',
    peopleAhead: 1,
    expectedWait: '5 phút',
  ),
  diagnosis: const DiagnosisSummary(
    title: 'Former Diagnosis Secret',
    detail: 'Former Diagnosis Detail',
  ),
  prescription: Prescription(
    id: 'former-prescription',
    issuedAt: DateTime.utc(2026, 7, 30),
    items: const [
      PrescriptionItem(
        medicationName: 'Former Medication Secret',
        dosage: '1 viên',
        route: 'Uống',
        frequency: 'Mỗi ngày',
        duration: '7 ngày',
        caution: 'Former Caution',
      ),
    ],
  ),
  timeline: [
    JourneyTimelineEvent(
      id: 'former-event',
      title: 'Former Timeline Secret',
      detail: 'Former Timeline Detail',
      occurredAt: DateTime.utc(2026, 7, 30),
    ),
  ],
  notifications: [
    PatientNotification(
      id: 'former-notification',
      title: 'Former Notification Secret',
      body: 'Former Notification Body',
      createdAt: DateTime.utc(2026, 7, 30),
      isRead: false,
    ),
  ],
  updatedAt: DateTime.utc(2026, 7, 30),
);

class SecurityAuthNotifier extends AuthNotifier {
  SecurityAuthNotifier() : super(AuthService(ApiService(), useMock: true));

  void setSession({required AuthStatus status, String? userId}) {
    state = AuthState(status: status, userId: userId);
  }
}

class SecurityPatientNotifier extends PatientNotifier {
  SecurityPatientNotifier(Patient patient)
    : super(PatientService(ApiService()), _DetachedRef()) {
    state = PatientState(patient: patient);
  }

  void switchTo(Patient patient) {
    state = PatientState(patient: patient);
  }
}

class PendingBootstrapRepository implements JourneyRepository {
  final started = Completer<void>();
  final result = Completer<PatientJourney>();

  @override
  Future<PatientJourney> bootstrap({
    required Appointment appointment,
    required String patientId,
  }) {
    started.complete();
    return result.future;
  }

  @override
  Future<PatientJourney> acknowledgePayment(
    PatientJourney journey,
    PaymentMethod method,
  ) => throw UnimplementedError();

  @override
  Future<PatientJourney> advance(PatientJourney journey, JourneyEvent event) =>
      throw UnimplementedError();

  @override
  Future<PatientJourney> markNotificationRead(
    PatientJourney journey,
    String notificationId,
  ) => throw UnimplementedError();

  @override
  Future<void> reset(PatientJourney journey) => throw UnimplementedError();
}

class _DetachedRef implements Ref {
  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}
