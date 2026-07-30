import 'package:careflow_patient/features/journey/application/journey_controller.dart';
import 'package:careflow_patient/features/journey/application/journey_providers.dart';
import 'package:careflow_patient/features/journey/data/journey_repository.dart';
import 'package:careflow_patient/features/journey/domain/journey_models.dart';
import 'package:careflow_patient/features/journey/domain/journey_transition.dart';
import 'package:careflow_patient/features/journey/presentation/visit_ticket_screen.dart';
import 'package:careflow_patient/models/appointment.dart';
import 'package:careflow_patient/models/patient.dart';
import 'package:careflow_patient/providers/auth_provider.dart';
import 'package:careflow_patient/providers/patient_provider.dart';
import 'package:careflow_patient/screens/appointment/appointment_detail_screen.dart';
import 'package:careflow_patient/screens/appointment/appointment_screen.dart';
import 'package:careflow_patient/screens/appointment/booking_step4_screen.dart';
import 'package:careflow_patient/services/api_service.dart';
import 'package:careflow_patient/services/appointment_service.dart';
import 'package:careflow_patient/services/auth_service.dart';
import 'package:careflow_patient/services/patient_service.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:go_router/go_router.dart';
import 'package:intl/date_symbol_data_local.dart';

void main() {
  setUpAll(() => initializeDateFormatting('vi'));

  testWidgets(
    'successful real booking bootstraps once before opening the visit ticket',
    (tester) async {
      final service = FakeAppointmentService(createdAppointment: appointment);
      final repository = RecordingJourneyRepository();
      final router = testRouter(
        BookingStep4Screen(
          patient: patient,
          department: Department(code: 'NOI_TONG_QUAT', name: 'Nội tổng quát'),
          date: appointment.appointmentDate,
          timeSlot: appointment.timeSlot,
        ),
      );
      addTearDown(router.dispose);

      await tester.pumpWidget(
        integrationApp(
          router: router,
          service: service,
          repository: repository,
        ),
      );

      await tester.tap(
        find.widgetWithText(ElevatedButton, 'Xác nhận đặt khám'),
      );
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 500));

      expect(service.createCalls, 1);
      expect(repository.bootstrapAppointments, [appointment]);
      expect(repository.bootstrapPatientIds, ['patient-1']);
      expect(find.text('Đặt khám thành công!'), findsOneWidget);

      await tester.tap(find.text('Xem phiếu khám'));
      await tester.pumpAndSettle();

      expect(
        router.routeInformationProvider.value.uri.path,
        '/journey/apt-1/ticket',
      );
      expect(find.text('ticket apt-1'), findsOneWidget);
    },
  );

  testWidgets('an appointment list can lazily open and bootstrap its journey', (
    tester,
  ) async {
    final service = FakeAppointmentService(appointments: [appointment]);
    final repository = RecordingJourneyRepository();
    final router = testRouter(const AppointmentScreen());
    addTearDown(router.dispose);

    await tester.pumpWidget(
      integrationApp(
        router: router,
        service: service,
        repository: repository,
        patientOverride: patient,
      ),
    );
    await tester.pumpAndSettle();

    await tester.tap(find.byKey(const Key('open-journey-apt-1')));
    await tester.pumpAndSettle();

    expect(repository.bootstrapAppointments, [appointment]);
    expect(find.text('journey apt-1'), findsOneWidget);
  });

  testWidgets('appointment detail reuses an already active journey', (
    tester,
  ) async {
    final service = FakeAppointmentService(detailAppointment: appointment);
    final repository = RecordingJourneyRepository();
    final controller = JourneyController(
      repository: repository,
      demoMode: true,
    );
    await controller.bootstrap(
      appointment: appointment,
      patientId: appointment.patientId,
    );
    final router = testRouter(
      const AppointmentDetailScreen(appointmentId: 'apt-1'),
    );
    addTearDown(router.dispose);

    await tester.pumpWidget(
      integrationApp(
        router: router,
        service: service,
        repository: repository,
        controller: controller,
      ),
    );
    await tester.pumpAndSettle();

    await tester.tap(find.byKey(const Key('open-journey-detail')));
    await tester.pumpAndSettle();

    expect(repository.bootstrapAppointments, [appointment]);
    expect(find.text('journey apt-1'), findsOneWidget);
  });

  testWidgets(
    'a real booking API failure shows the real error and creates no journey',
    (tester) async {
      final service = FakeAppointmentService(createError: 'Gateway timeout');
      final repository = RecordingJourneyRepository();
      final router = testRouter(
        BookingStep4Screen(
          patient: patient,
          department: Department(code: 'NOI_TONG_QUAT', name: 'Nội tổng quát'),
          date: appointment.appointmentDate,
          timeSlot: appointment.timeSlot,
        ),
      );
      addTearDown(router.dispose);

      await tester.pumpWidget(
        integrationApp(
          router: router,
          service: service,
          repository: repository,
        ),
      );

      await tester.tap(
        find.widgetWithText(ElevatedButton, 'Xác nhận đặt khám'),
      );
      await tester.pump();

      expect(find.textContaining('Gateway timeout'), findsOneWidget);
      expect(repository.bootstrapAppointments, isEmpty);
      expect(
        router.routeInformationProvider.value.uri.path,
        isNot(contains('/journey/')),
      );
    },
  );

  testWidgets(
    'production booking opens backend-unavailable without creating demo data',
    (tester) async {
      final service = FakeAppointmentService(createdAppointment: appointment);
      final repository = RecordingJourneyRepository();
      final controller = JourneyController(
        repository: repository,
        demoMode: false,
      );
      final router = testRouter(
        BookingStep4Screen(
          patient: patient,
          department: Department(code: 'NOI_TONG_QUAT', name: 'Nội tổng quát'),
          date: appointment.appointmentDate,
          timeSlot: appointment.timeSlot,
        ),
        useRealTicketScreen: true,
      );
      addTearDown(router.dispose);

      await tester.pumpWidget(
        integrationApp(
          router: router,
          service: service,
          repository: repository,
          controller: controller,
        ),
      );
      await tester.tap(
        find.widgetWithText(ElevatedButton, 'Xác nhận đặt khám'),
      );
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 500));
      await tester.tap(find.text('Xem phiếu khám'));
      await tester.pumpAndSettle();

      expect(repository.bootstrapAppointments, isEmpty);
      expect(
        find.text('Hành trình khám đang chờ backend triển khai.'),
        findsOneWidget,
      );
    },
  );
}

Widget integrationApp({
  required GoRouter router,
  required AppointmentService service,
  required RecordingJourneyRepository repository,
  JourneyController? controller,
  Patient? patientOverride,
}) {
  final journeyController =
      controller ?? JourneyController(repository: repository, demoMode: true);
  final scopedPatient = patientOverride ?? patient;
  return ProviderScope(
    overrides: [
      authProvider.overrideWith(
        (ref) => SeededAuthNotifier(scopedPatient.userId),
      ),
      appointmentServiceProvider.overrideWithValue(service),
      journeyControllerProvider.overrideWith((ref) => journeyController),
      patientProvider.overrideWith(
        (ref) => SeededPatientNotifier(scopedPatient, ref),
      ),
    ],
    child: MaterialApp.router(routerConfig: router),
  );
}

GoRouter testRouter(Widget initialScreen, {bool useRealTicketScreen = false}) =>
    GoRouter(
      initialLocation: '/',
      routes: [
        GoRoute(path: '/', builder: (_, _) => initialScreen),
        GoRoute(
          path: '/journey/:appointmentId',
          builder: (_, state) =>
              Text('journey ${state.pathParameters['appointmentId']}'),
          routes: [
            GoRoute(
              path: 'ticket',
              builder: (_, state) => useRealTicketScreen
                  ? VisitTicketScreen(
                      appointmentId: state.pathParameters['appointmentId']!,
                    )
                  : Text('ticket ${state.pathParameters['appointmentId']}'),
            ),
          ],
        ),
      ],
    );

final patient = Patient(
  id: 'patient-1',
  userId: 'user-1',
  fullName: 'Nguyễn An',
);

final appointment = Appointment(
  id: 'apt-1',
  patientId: patient.id,
  patientName: patient.fullName,
  department: 'NOI_TONG_QUAT',
  departmentDisplayName: 'Nội tổng quát',
  appointmentDate: DateTime(2026, 8, 18),
  timeSlot: '08:00 - 08:30',
  status: 'CONFIRMED',
  statusDisplayName: 'Đã xác nhận',
);

class FakeAppointmentService extends AppointmentService {
  FakeAppointmentService({
    this.createdAppointment,
    this.appointments = const [],
    this.detailAppointment,
    this.createError,
  }) : super(ApiService());

  final Appointment? createdAppointment;
  final List<Appointment> appointments;
  final Appointment? detailAppointment;
  final String? createError;
  int createCalls = 0;

  @override
  Future<Appointment> createAppointment(Map<String, dynamic> data) async {
    createCalls++;
    if (createError case final error?) throw Exception(error);
    return createdAppointment!;
  }

  @override
  Future<List<Appointment>> getAppointmentsByPatientId(String patientId) async {
    return appointments;
  }

  @override
  Future<Appointment> getAppointmentById(String id) async {
    return detailAppointment!;
  }
}

class RecordingJourneyRepository implements JourneyRepository {
  final List<Appointment> bootstrapAppointments = [];
  final List<String> bootstrapPatientIds = [];

  @override
  Future<PatientJourney> bootstrap({
    required Appointment appointment,
    required String patientId,
  }) async {
    bootstrapAppointments.add(appointment);
    bootstrapPatientIds.add(patientId);
    return PatientJourney(
      appointmentId: appointment.id,
      patientId: patientId,
      status: JourneyStatus.ticketIssued,
      laboratoryOrders: const [],
      timeline: const [],
      notifications: const [],
      updatedAt: DateTime.utc(2026, 8, 18),
    );
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

class SeededPatientNotifier extends PatientNotifier {
  SeededPatientNotifier(Patient patient, Ref ref)
    : super(PatientService(ApiService()), ref) {
    state = PatientState(patient: patient);
  }
}

class SeededAuthNotifier extends AuthNotifier {
  SeededAuthNotifier(String userId)
    : super(AuthService(ApiService(), useMock: true)) {
    state = AuthState(status: AuthStatus.authenticated, userId: userId);
  }
}
