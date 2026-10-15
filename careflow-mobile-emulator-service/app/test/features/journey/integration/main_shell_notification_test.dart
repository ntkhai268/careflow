import 'package:careflow_patient/features/journey/application/journey_controller.dart';
import 'package:careflow_patient/features/journey/application/journey_providers.dart';
import 'package:careflow_patient/features/journey/data/journey_repository.dart';
import 'package:careflow_patient/features/journey/domain/journey_models.dart';
import 'package:careflow_patient/features/journey/domain/journey_transition.dart';
import 'package:careflow_patient/features/journey/presentation/journey_notification_screen.dart';
import 'package:careflow_patient/models/appointment.dart';
import 'package:careflow_patient/models/patient.dart';
import 'package:careflow_patient/providers/auth_provider.dart';
import 'package:careflow_patient/providers/patient_provider.dart';
import 'package:careflow_patient/screens/main_shell.dart';
import 'package:careflow_patient/services/api_service.dart';
import 'package:careflow_patient/services/appointment_service.dart';
import 'package:careflow_patient/services/auth_service.dart';
import 'package:careflow_patient/services/patient_service.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  testWidgets(
    'shell badge uses provider count, hides at zero, and opens alerts',
    (tester) async {
      final auth = SeededAuthNotifier()..authenticate('user-a');
      final controller = JourneyController(
        repository: const UnavailableJourneyRepository(),
        demoMode: true,
      )..state = AsyncData(journeyFor('patient-a', unreadCount: 2));

      await tester.pumpWidget(
        ProviderScope(
          overrides: [
            authProvider.overrideWith((ref) => auth),
            patientProvider.overrideWith(
              (ref) => SwitchablePatientNotifier(patientA, ref),
            ),
            appointmentServiceProvider.overrideWithValue(
              EmptyAppointmentService(),
            ),
            journeyControllerProvider.overrideWith((ref) => controller),
          ],
          child: const MaterialApp(home: MainShell()),
        ),
      );

      expect(
        find.descendant(of: find.byType(Badge), matching: find.text('2')),
        findsOneWidget,
      );
      expect(find.text('330'), findsNothing);

      await tester.tap(find.text('Thông báo'));
      await tester.pumpAndSettle();
      expect(find.byType(JourneyNotificationScreen), findsOneWidget);

      controller.state = AsyncData(journeyFor('patient-a', unreadCount: 0));
      await tester.pump();
      expect(find.byType(Badge), findsNothing);

      await tester.tap(find.text('Trang chủ'));
      await tester.pumpAndSettle();
      await tester.tap(find.byKey(const Key('home-notification-button')));
      await tester.pumpAndSettle();
      expect(find.byType(JourneyNotificationScreen), findsOneWidget);
    },
  );

  testWidgets(
    'fresh shell loads the patient before restoring an in-progress journey',
    (tester) async {
      final auth = SeededAuthNotifier()..authenticate('user-a');
      late RestoringPatientNotifier patient;
      final appointmentService = InProgressAppointmentService();
      final controller = JourneyController(
        repository: InProgressJourneyRepository(),
        demoMode: false,
      );

      await tester.pumpWidget(
        ProviderScope(
          overrides: [
            authProvider.overrideWith((ref) => auth),
            patientProvider.overrideWith(
              (ref) => patient = RestoringPatientNotifier(patientA, ref),
            ),
            appointmentServiceProvider.overrideWithValue(appointmentService),
            journeyControllerProvider.overrideWith((ref) => controller),
          ],
          child: const MaterialApp(home: MainShell()),
        ),
      );
      await tester.pumpAndSettle();

      expect(patient.loadCalls, 1);
      expect(find.byKey(const Key('active-journey-card')), findsOneWidget);
    },
  );

  test(
    'logout and patient switch hide the previous account journey and count',
    () {
      final auth = SeededAuthNotifier()..authenticate('user-a');
      late SwitchablePatientNotifier patient;
      final controller = JourneyController(
        repository: const UnavailableJourneyRepository(),
        demoMode: true,
      )..state = AsyncData(journeyFor('patient-a', unreadCount: 2));
      final container = ProviderContainer(
        overrides: [
          authProvider.overrideWith((ref) => auth),
          patientProvider.overrideWith(
            (ref) => patient = SwitchablePatientNotifier(patientA, ref),
          ),
          journeyControllerProvider.overrideWith((ref) => controller),
        ],
      );
      addTearDown(container.dispose);

      expect(container.read(activeJourneyProvider)?.patientId, 'patient-a');
      expect(container.read(unreadJourneyNotificationCountProvider), 2);

      auth.logOutLocally();

      expect(container.read(activeJourneyProvider), isNull);
      expect(container.read(unreadJourneyNotificationCountProvider), 0);

      auth.authenticate('user-b');
      expect(container.read(activeJourneyProvider), isNull);
      expect(container.read(unreadJourneyNotificationCountProvider), 0);

      patient.switchTo(patientB);

      expect(container.read(activeJourneyProvider), isNull);
      expect(container.read(unreadJourneyNotificationCountProvider), 0);
    },
  );
}

PatientJourney journeyFor(String patientId, {required int unreadCount}) =>
    PatientJourney(
      appointmentId: 'apt-$patientId',
      patientId: patientId,
      status: JourneyStatus.waiting,
      laboratoryOrders: const [],
      timeline: const [],
      notifications: [
        for (var index = 0; index < unreadCount; index++)
          PatientNotification(
            id: 'notification-$index',
            title: 'Thông báo $index',
            body: 'Nội dung',
            createdAt: DateTime.utc(2026, 7, 30, 8, index),
            isRead: false,
          ),
      ],
      updatedAt: DateTime.utc(2026, 7, 30),
    );

final patientA = Patient(
  id: 'patient-a',
  userId: 'user-a',
  fullName: 'Nguyễn An',
);
final patientB = Patient(
  id: 'patient-b',
  userId: 'user-b',
  fullName: 'Trần Bình',
);

class SeededAuthNotifier extends AuthNotifier {
  SeededAuthNotifier() : super(AuthService(ApiService(), useMock: true));

  void authenticate(String userId) {
    state = AuthState(status: AuthStatus.authenticated, userId: userId);
  }

  void logOutLocally() {
    state = const AuthState(status: AuthStatus.unauthenticated);
  }
}

class SwitchablePatientNotifier extends PatientNotifier {
  SwitchablePatientNotifier(this.initialPatient, Ref ref)
    : super(PatientService(ApiService()), ref) {
    state = PatientState(patient: initialPatient);
  }

  final Patient initialPatient;

  @override
  Future<void> loadPatient() async {}

  void switchTo(Patient patient) {
    state = PatientState(patient: patient);
  }
}

class EmptyAppointmentService extends AppointmentService {
  EmptyAppointmentService() : super(ApiService());

  @override
  Future<List<Appointment>> getAppointmentsByPatientId(String patientId) async {
    return const [];
  }
}

class InProgressAppointmentService extends AppointmentService {
  InProgressAppointmentService() : super(ApiService());

  @override
  Future<List<Appointment>> getAppointmentsByPatientId(
    String patientId,
  ) async => [
    Appointment(
      id: 'apt-in-progress',
      patientId: patientId,
      department: 'NOI_TONG_QUAT',
      departmentDisplayName: 'General Medicine',
      appointmentDate: DateTime.utc(2026, 8, 8),
      timeSlot: '08:00 - 08:30',
      status: 'IN_PROGRESS',
      statusDisplayName: 'IN_PROGRESS',
    ),
  ];
}

class RestoringPatientNotifier extends PatientNotifier {
  RestoringPatientNotifier(this.patient, Ref ref)
    : super(PatientService(ApiService()), ref);

  final Patient patient;
  int loadCalls = 0;

  @override
  Future<void> loadPatient() async {
    if (state.isLoading) return;
    loadCalls++;
    state = const PatientState(isLoading: true);
    await Future<void>.value();
    state = PatientState(patient: patient);
  }
}

class InProgressJourneyRepository implements JourneyRepository {
  @override
  Future<PatientJourney> bootstrap({
    required Appointment appointment,
    required String patientId,
  }) async => PatientJourney(
    appointmentId: appointment.id,
    patientId: patientId,
    status: JourneyStatus.inConsultation,
    doctorName: 'BS. Minh Anh',
    laboratoryOrders: const [],
    timeline: const [],
    notifications: const [],
    updatedAt: DateTime.utc(2026, 8, 8),
  );

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
