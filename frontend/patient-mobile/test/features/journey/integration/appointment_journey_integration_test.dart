import 'package:careflow_patient/features/journey/application/journey_controller.dart';
import 'package:careflow_patient/features/journey/application/journey_providers.dart';
import 'package:careflow_patient/features/journey/data/journey_repository.dart';
import 'package:careflow_patient/features/journey/domain/journey_models.dart';
import 'package:careflow_patient/features/journey/domain/journey_transition.dart';
import 'package:careflow_patient/features/journey/presentation/visit_ticket_screen.dart';
import 'package:careflow_patient/models/appointment.dart';
import 'package:careflow_patient/models/appointment_payment.dart';
import 'package:careflow_patient/models/patient.dart';
import 'package:careflow_patient/providers/auth_provider.dart';
import 'package:careflow_patient/providers/patient_provider.dart';
import 'package:careflow_patient/screens/appointment/appointment_detail_screen.dart';
import 'package:careflow_patient/screens/appointment/appointment_screen.dart';
import 'package:careflow_patient/screens/appointment/booking_step4_screen.dart';
import 'package:careflow_patient/services/api_service.dart';
import 'package:careflow_patient/services/appointment_service.dart';
import 'package:careflow_patient/services/appointment_payment_store.dart';
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
    'successful real booking bootstraps once before opening appointment detail',
    (tester) async {
      final service = FakeAppointmentService(createdAppointment: appointment);
      final repository = RecordingJourneyRepository();
      final paymentStore = InMemoryAppointmentPaymentStore();
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
          paymentStore: paymentStore,
        ),
      );

      expect(find.text('Thanh toán trực tuyến'), findsOneWidget);
      expect(find.text('Thanh toán tại bệnh viện'), findsNothing);

      await tester.tap(
        find.widgetWithText(ElevatedButton, 'Xác nhận đặt khám'),
      );
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 500));

      expect(service.createCalls, 1);
      expect(repository.bootstrapAppointments, [appointment]);
      expect(repository.bootstrapPatientIds, ['patient-1']);
      expect(
        paymentStore.receipts['apt-1']?.status,
        AppointmentPaymentStatus.paid,
      );
      expect(find.text('Đặt khám thành công!'), findsOneWidget);

      await tester.tap(find.text('Xem phiếu khám'));
      await tester.pumpAndSettle();

      expect(
        router.routeInformationProvider.value.uri.path,
        '/appointment/apt-1',
      );
      expect(find.text('appointment apt-1'), findsOneWidget);
      expect(
        router.routeInformationProvider.value.uri.path,
        isNot(contains('/journey/')),
      );
    },
  );

  testWidgets('appointment list no longer exposes a journey action', (
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

    expect(find.byKey(const Key('open-journey-apt-1')), findsNothing);
    expect(repository.bootstrapAppointments, isEmpty);
  });

  testWidgets(
    'appointment list reloads after returning from appointment detail',
    (tester) async {
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

      expect(service.getAppointmentsCalls, 1);
      service.appointments.add(
        appointmentWith(status: 'CONFIRMED', id: 'apt-2'),
      );

      await tester.tap(find.text('Nội tổng quát').first);
      await tester.pumpAndSettle();
      expect(find.text('appointment apt-1'), findsOneWidget);

      router.pop();
      await tester.pumpAndSettle();

      expect(service.getAppointmentsCalls, 2);
      expect(find.text('08:00 - 08:30'), findsNWidgets(2));
    },
  );

  testWidgets('appointment detail opens the visit ticket screen', (
    tester,
  ) async {
    final service = FakeAppointmentService(detailAppointment: appointment);
    final repository = RecordingJourneyRepository();
    final router = testRouter(
      const AppointmentDetailScreen(appointmentId: 'apt-1'),
    );
    addTearDown(router.dispose);

    await tester.pumpWidget(
      integrationApp(router: router, service: service, repository: repository),
    );
    await tester.pumpAndSettle();

    expect(find.byKey(const Key('open-journey-detail')), findsOneWidget);
    await tester.ensureVisible(find.byKey(const Key('open-journey-detail')));
    await tester.tap(find.byKey(const Key('open-journey-detail')));
    await tester.pumpAndSettle();

    expect(find.text('ticket apt-1'), findsOneWidget);
    expect(repository.bootstrapAppointments, isEmpty);
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

  testWidgets('duplicate-slot 409 shows a concise Vietnamese message', (
    tester,
  ) async {
    const conflictMessage = 'Bệnh nhân đã có lịch khám vào ca này';
    final service = FakeAppointmentService(createError: conflictMessage);
    final repository = RecordingJourneyRepository();
    final router = testRouter(
      BookingStep4Screen(
        patient: patient,
        department: Department(code: 'NHI', name: 'Nhi'),
        date: appointment.appointmentDate,
        timeSlot: appointment.timeSlot,
      ),
    );
    addTearDown(router.dispose);

    await tester.pumpWidget(
      integrationApp(router: router, service: service, repository: repository),
    );

    await tester.tap(find.widgetWithText(ElevatedButton, 'Xác nhận đặt khám'));
    await tester.pump();

    expect(find.text(conflictMessage), findsOneWidget);
    expect(find.textContaining('DioException'), findsNothing);
    expect(repository.bootstrapAppointments, isEmpty);
  });

  testWidgets(
    'production booking bootstraps the backend journey without demo data',
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

      expect(repository.bootstrapAppointments, [appointment]);
      expect(repository.bootstrapPatientIds, [patient.id]);
      expect(
        find.text('Hành trình khám đang chờ backend triển khai.'),
        findsNothing,
      );
    },
  );

  testWidgets(
    'pending booking never bootstraps or offers an active visit ticket',
    (tester) async {
      final service = FakeAppointmentService(
        createdAppointment: appointmentWith(status: 'PENDING'),
      );
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

      expect(repository.bootstrapAppointments, isEmpty);
      expect(find.text('Lịch khám đang chờ xác nhận.'), findsOneWidget);
      expect(find.text('Xem phiếu khám'), findsNothing);
    },
  );

  for (final status in ['PENDING', 'CANCELLED']) {
    testWidgets('$status appointment detail cannot create or open a journey', (
      tester,
    ) async {
      final service = FakeAppointmentService(
        detailAppointment: appointmentWith(status: status),
      );
      final repository = RecordingJourneyRepository();
      final router = testRouter(
        const AppointmentDetailScreen(appointmentId: 'apt-1'),
      );
      addTearDown(router.dispose);

      await tester.pumpWidget(
        integrationApp(
          router: router,
          service: service,
          repository: repository,
        ),
      );
      await tester.pumpAndSettle();

      expect(find.byKey(const Key('open-journey-detail')), findsNothing);
      expect(repository.bootstrapAppointments, isEmpty);
    });
  }

  testWidgets('completed appointment does not expose a journey action', (
    tester,
  ) async {
    final completed = appointmentWith(status: 'COMPLETED');
    final service = FakeAppointmentService(detailAppointment: completed);
    final repository = RecordingJourneyRepository();
    final router = testRouter(
      const AppointmentDetailScreen(appointmentId: 'apt-1'),
    );
    addTearDown(router.dispose);

    await tester.pumpWidget(
      integrationApp(router: router, service: service, repository: repository),
    );
    await tester.pumpAndSettle();

    expect(find.byKey(const Key('open-journey-detail')), findsNothing);
    expect(repository.bootstrapAppointments, isEmpty);
  });

  testWidgets(
    'appointment list does not expose journey actions for any status',
    (tester) async {
      final appointments = [
        appointmentWith(status: 'PENDING', id: 'apt-pending'),
        appointmentWith(status: 'CANCELLED', id: 'apt-cancelled'),
      ];
      final service = FakeAppointmentService(appointments: appointments);
      final repository = RecordingJourneyRepository();
      final router = testRouter(const AppointmentScreen());
      addTearDown(router.dispose);

      await tester.pumpWidget(
        integrationApp(
          router: router,
          service: service,
          repository: repository,
        ),
      );
      await tester.pumpAndSettle();

      for (final appointment in appointments) {
        expect(find.byKey(Key('open-journey-${appointment.id}')), findsNothing);
      }
      expect(repository.bootstrapAppointments, isEmpty);
    },
  );

  testWidgets('appointment detail fails closed for another patient', (
    tester,
  ) async {
    final service = FakeAppointmentService(
      detailAppointment: appointmentForPatient('patient-2'),
    );
    final repository = RecordingJourneyRepository();
    final router = testRouter(
      const AppointmentDetailScreen(appointmentId: 'apt-1'),
    );
    addTearDown(router.dispose);

    await tester.pumpWidget(
      integrationApp(router: router, service: service, repository: repository),
    );
    await tester.pumpAndSettle();

    expect(find.text('Bạn không có quyền xem phiếu khám này.'), findsOneWidget);
    expect(find.text('Nội tổng quát'), findsNothing);
    expect(find.byKey(const Key('open-journey-detail')), findsNothing);
    expect(repository.bootstrapAppointments, isEmpty);
  });

  testWidgets('cancelling an appointment retires its active journey', (
    tester,
  ) async {
    final service = FakeAppointmentService(
      detailAppointment: appointment,
      cancelledAppointment: appointmentWith(status: 'CANCELLED'),
    );
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
    await tester.tap(find.text('Hủy lịch khám'));
    await tester.pumpAndSettle();
    await tester.tap(find.widgetWithText(ElevatedButton, 'Hủy lịch'));
    await tester.pumpAndSettle();

    expect(repository.resetJourneys, hasLength(1));
    expect(controller.state.valueOrNull, isNull);
    expect(find.byKey(const Key('open-journey-detail')), findsNothing);
  });

  testWidgets(
    'successful backend cancellation stays cancelled when local cleanup fails',
    (tester) async {
      final service = FakeAppointmentService(
        detailAppointment: appointment,
        cancelledAppointment: appointmentWith(status: 'CANCELLED'),
      );
      final repository = RecordingJourneyRepository()..resetFails = true;
      String? actionError;
      final controller = JourneyController(
        repository: repository,
        demoMode: true,
        onActionError: (value) => actionError = value,
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
      await tester.tap(find.text('Hủy lịch khám'));
      await tester.pumpAndSettle();
      await tester.tap(find.widgetWithText(ElevatedButton, 'Hủy lịch'));
      await tester.pumpAndSettle();

      expect(controller.state.valueOrNull, isNull);
      expect(
        actionError,
        'Không thể dọn dữ liệu hành trình đã hủy. Vui lòng thử lại.',
      );
      expect(find.text('CANCELLED'), findsOneWidget);
      expect(find.byKey(const Key('open-journey-detail')), findsNothing);
      expect(
        find.text('Đã hủy lịch khám, nhưng chưa thể dọn dữ liệu cục bộ.'),
        findsOneWidget,
      );
      expect(find.text('Thử lại'), findsOneWidget);
    },
  );
}

Widget integrationApp({
  required GoRouter router,
  required AppointmentService service,
  required RecordingJourneyRepository repository,
  JourneyController? controller,
  Patient? patientOverride,
  AppointmentPaymentStore? paymentStore,
}) {
  final journeyController =
      controller ?? JourneyController(repository: repository, demoMode: true);
  final scopedPatient = patientOverride ?? patient;
  return ProviderScope(
    overrides: [
      demoModeProvider.overrideWithValue(true),
      realQueueEnabledProvider.overrideWithValue(false),
      authProvider.overrideWith(
        (ref) => SeededAuthNotifier(scopedPatient.userId),
      ),
      appointmentServiceProvider.overrideWithValue(service),
      appointmentPaymentStoreProvider.overrideWithValue(
        paymentStore ?? InMemoryAppointmentPaymentStore(),
      ),
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
          path: '/appointment/:id',
          builder: (_, state) =>
              Text('appointment ${state.pathParameters['id']}'),
        ),
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

Appointment appointmentWith({required String status, String? id}) =>
    Appointment(
      id: id ?? appointment.id,
      patientId: appointment.patientId,
      patientName: appointment.patientName,
      department: appointment.department,
      departmentDisplayName: appointment.departmentDisplayName,
      appointmentDate: appointment.appointmentDate,
      timeSlot: appointment.timeSlot,
      status: status,
      statusDisplayName: status,
    );

Appointment appointmentForPatient(String patientId) => Appointment(
  id: appointment.id,
  patientId: patientId,
  patientName: 'Dữ liệu bệnh nhân khác',
  department: appointment.department,
  departmentDisplayName: appointment.departmentDisplayName,
  doctorName: 'BS. Không được phép xem',
  appointmentDate: appointment.appointmentDate,
  timeSlot: appointment.timeSlot,
  status: 'CONFIRMED',
  statusDisplayName: 'Đã xác nhận',
);

class FakeAppointmentService extends AppointmentService {
  FakeAppointmentService({
    this.createdAppointment,
    this.appointments = const [],
    this.detailAppointment,
    this.cancelledAppointment,
    this.createError,
  }) : super(ApiService());

  final Appointment? createdAppointment;
  final List<Appointment> appointments;
  final Appointment? detailAppointment;
  final Appointment? cancelledAppointment;
  final String? createError;
  int createCalls = 0;
  int getAppointmentsCalls = 0;

  @override
  Future<Appointment> createAppointment(
    Map<String, dynamic> data, {
    String? idempotencyKey,
  }) async {
    createCalls++;
    if (createError case final error?) {
      throw AppointmentBookingException(error);
    }
    return createdAppointment!;
  }

  @override
  Future<List<Appointment>> getAppointmentsByPatientId(String patientId) async {
    getAppointmentsCalls++;
    return appointments;
  }

  @override
  Future<Appointment> getAppointmentById(String id) async {
    return detailAppointment!;
  }

  @override
  Future<Appointment> cancelAppointment(String id) async {
    return cancelledAppointment!;
  }
}

class InMemoryAppointmentPaymentStore implements AppointmentPaymentStore {
  final receipts = <String, AppointmentPaymentReceipt>{};

  @override
  Future<AppointmentPaymentReceipt?> load(String appointmentId) async =>
      receipts[appointmentId];

  @override
  Future<void> save(AppointmentPaymentReceipt receipt) async {
    receipts[receipt.appointmentId] = receipt;
  }

  @override
  Future<void> delete(String appointmentId) async {
    receipts.remove(appointmentId);
  }
}

class RecordingJourneyRepository implements JourneyRepository {
  final List<Appointment> bootstrapAppointments = [];
  final List<String> bootstrapPatientIds = [];
  final List<PatientJourney> resetJourneys = [];
  bool resetFails = false;

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
  Future<void> reset(PatientJourney journey) async {
    resetJourneys.add(journey);
    if (resetFails) throw StateError('local cleanup unavailable');
  }
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
