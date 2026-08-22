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
import 'package:careflow_patient/services/api_service.dart';
import 'package:careflow_patient/services/auth_service.dart';
import 'package:careflow_patient/services/patient_service.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:go_router/go_router.dart';

void main() {
  testWidgets('shows notifications newest first and marks an unread one read', (
    tester,
  ) async {
    final repository = NotificationRepository();
    final controller = JourneyController(repository: repository, demoMode: true)
      ..state = AsyncData(notificationJourney());
    final container = ProviderContainer(
      overrides: authenticatedJourneyOverrides(controller),
    );
    addTearDown(container.dispose);

    final router = notificationRouter();
    addTearDown(router.dispose);
    await tester.pumpWidget(
      UncontrolledProviderScope(
        container: container,
        child: MaterialApp.router(routerConfig: router),
      ),
    );

    expect(
      tester.getTopLeft(find.text('Kết quả xét nghiệm đã sẵn sàng')).dy,
      lessThan(tester.getTopLeft(find.text('Đã xác nhận check-in')).dy),
    );
    expect(find.byKey(const Key('unread-marker-result')), findsOneWidget);
    expect(find.byKey(const Key('unread-marker-checkin')), findsOneWidget);
    expect(find.text('16:00 • 30/07/2026'), findsOneWidget);
    expect(container.read(unreadJourneyNotificationCountProvider), 2);

    await tester.tap(find.text('Kết quả xét nghiệm đã sẵn sàng'));
    await tester.pumpAndSettle();

    expect(repository.readNotificationIds, ['result']);
    expect(find.byKey(const Key('unread-marker-result')), findsNothing);
    expect(container.read(unreadJourneyNotificationCountProvider), 1);
    expect(find.byKey(const Key('notification-destination')), findsOneWidget);
  });

  testWidgets('reloads notifications from the server when refresh is tapped', (
    tester,
  ) async {
    final repository = NotificationRepository();
    final controller = JourneyController(
      repository: repository,
      demoMode: true,
    );
    await controller.bootstrap(
      appointment: notificationAppointment(),
      patientId: 'patient-1',
    );
    final container = ProviderContainer(
      overrides: authenticatedJourneyOverrides(controller),
    );
    addTearDown(container.dispose);

    final router = notificationRouter();
    addTearDown(router.dispose);
    await tester.pumpWidget(
      UncontrolledProviderScope(
        container: container,
        child: MaterialApp.router(routerConfig: router),
      ),
    );

    await tester.tap(find.byTooltip('Tải lại thông báo'));
    await tester.pumpAndSettle();

    expect(repository.bootstrapCalls, 2);
  });

  testWidgets('shows a neutral empty state without journey notifications', (
    tester,
  ) async {
    final controller =
        JourneyController(repository: NotificationRepository(), demoMode: true)
          ..state = AsyncData(
            notificationJourney().copyWith(notifications: const []),
          );
    final container = ProviderContainer(
      overrides: authenticatedJourneyOverrides(controller),
    );
    addTearDown(container.dispose);

    final router = notificationRouter();
    addTearDown(router.dispose);
    await tester.pumpWidget(
      UncontrolledProviderScope(
        container: container,
        child: MaterialApp.router(routerConfig: router),
      ),
    );

    expect(find.text('Bạn chưa có thông báo nào.'), findsOneWidget);
  });

  testWidgets('loads the account inbox when there is no active journey', (
    tester,
  ) async {
    final inbox = GlobalInboxTestController();
    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          authProvider.overrideWith((ref) => NotificationAuthNotifier()),
          patientProvider.overrideWith(
            (ref) => NotificationPatientNotifier(ref),
          ),
          patientNotificationInboxProvider.overrideWith(() => inbox),
        ],
        child: const MaterialApp(
          home: JourneyNotificationScreen(appointmentId: '', useInbox: true),
        ),
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('Thông báo toàn tài khoản'), findsOneWidget);
    await tester.tap(find.byTooltip('Tải lại thông báo'));
    await tester.pumpAndSettle();
    expect(inbox.refreshCalls, 1);
  });

  testWidgets('opens an already-read notification without marking it again', (
    tester,
  ) async {
    final repository = NotificationRepository();
    final source = notificationJourney();
    final controller = JourneyController(repository: repository, demoMode: true)
      ..state = AsyncData(
        source.copyWith(
          notifications: source.notifications
              .map((notification) => notification.copyWith(isRead: true))
              .toList(),
        ),
      );
    final container = ProviderContainer(
      overrides: authenticatedJourneyOverrides(controller),
    );
    addTearDown(container.dispose);
    final router = notificationRouter();
    addTearDown(router.dispose);

    await tester.pumpWidget(
      UncontrolledProviderScope(
        container: container,
        child: MaterialApp.router(routerConfig: router),
      ),
    );
    await tester.pumpAndSettle();

    await tester.tap(find.byKey(const Key('notification-result')));
    await tester.pumpAndSettle();

    expect(repository.readNotificationIds, isEmpty);
    expect(find.byKey(const Key('notification-destination')), findsOneWidget);
  });
}

List<Override> authenticatedJourneyOverrides(JourneyController controller) => [
  authProvider.overrideWith((ref) => NotificationAuthNotifier()),
  patientProvider.overrideWith((ref) => NotificationPatientNotifier(ref)),
  journeyControllerProvider.overrideWith((ref) => controller),
];

class NotificationAuthNotifier extends AuthNotifier {
  NotificationAuthNotifier() : super(AuthService(ApiService(), useMock: true)) {
    state = const AuthState(status: AuthStatus.authenticated, userId: 'user-1');
  }
}

class NotificationPatientNotifier extends PatientNotifier {
  NotificationPatientNotifier(Ref ref)
    : super(PatientService(ApiService()), ref) {
    state = PatientState(
      patient: Patient(
        id: 'patient-1',
        userId: 'user-1',
        fullName: 'Nguyễn An',
      ),
    );
  }
}

PatientJourney notificationJourney() => PatientJourney(
  appointmentId: 'apt-1',
  patientId: 'patient-1',
  status: JourneyStatus.labResultReady,
  laboratoryOrders: const [],
  timeline: const [],
  notifications: [
    PatientNotification(
      id: 'checkin',
      title: 'Đã xác nhận check-in',
      body: 'Bệnh nhân đã được xác nhận trong vùng geofence.',
      createdAt: DateTime.utc(2026, 7, 30, 8),
      isRead: false,
    ),
    PatientNotification(
      id: 'result',
      title: 'Kết quả xét nghiệm đã sẵn sàng',
      body: 'Bác sĩ sẽ đọc kết quả cho bạn.',
      createdAt: DateTime.utc(2026, 7, 30, 9),
      isRead: false,
      actionType: 'OPEN_LAB_RESULT',
      resourceId: 'order-1',
    ),
  ],
  updatedAt: DateTime.utc(2026, 7, 30),
);

Appointment notificationAppointment() => Appointment(
  id: 'apt-1',
  patientId: 'patient-1',
  department: 'NOI_TONG_QUAT',
  departmentDisplayName: 'Nội tổng quát',
  appointmentDate: DateTime.utc(2026, 7, 30),
  timeSlot: '08:00 - 09:00',
  status: 'CONFIRMED',
  statusDisplayName: 'Đã xác nhận',
);

class NotificationRepository implements JourneyRepository {
  final List<String> readNotificationIds = [];
  int bootstrapCalls = 0;

  @override
  Future<PatientJourney> bootstrap({
    required Appointment appointment,
    required String patientId,
  }) async {
    bootstrapCalls++;
    return notificationJourney();
  }

  @override
  Future<PatientJourney> markNotificationRead(
    PatientJourney journey,
    String notificationId,
  ) async {
    readNotificationIds.add(notificationId);
    return journey.copyWith(
      notifications: journey.notifications
          .map(
            (notification) => notification.id == notificationId
                ? notification.copyWith(isRead: true)
                : notification,
          )
          .toList(),
    );
  }

  @override
  Future<PatientJourney> acknowledgePayment(
    PatientJourney journey,
    PaymentMethod method,
  ) => Future<PatientJourney>.error(UnimplementedError());

  @override
  Future<PatientJourney> advance(PatientJourney journey, JourneyEvent event) =>
      Future<PatientJourney>.error(UnimplementedError());

  @override
  Future<void> reset(PatientJourney journey) =>
      Future<void>.error(UnimplementedError());
}

class GlobalInboxTestController extends PatientNotificationInboxController {
  int refreshCalls = 0;

  @override
  Future<List<PatientNotification>> build() async => [
    PatientNotification(
      id: 'global',
      title: 'Thông báo toàn tài khoản',
      body: 'Nội dung thông báo.',
      createdAt: DateTime.utc(2026, 7, 30, 10),
      isRead: false,
    ),
  ];

  @override
  Future<bool> refresh() async {
    refreshCalls++;
    return true;
  }
}

GoRouter notificationRouter() => GoRouter(
  initialLocation: '/',
  routes: [
    GoRoute(
      path: '/',
      builder: (_, _) =>
          const JourneyNotificationScreen(appointmentId: 'apt-1'),
    ),
    GoRoute(
      path: '/journey/:appointmentId/laboratory',
      builder: (_, _) => const Scaffold(
        key: Key('notification-destination'),
        body: Text('Laboratory destination'),
      ),
    ),
  ],
);
