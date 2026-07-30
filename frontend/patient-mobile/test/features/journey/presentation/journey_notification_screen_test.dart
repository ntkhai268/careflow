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

    await tester.pumpWidget(
      UncontrolledProviderScope(
        container: container,
        child: const MaterialApp(
          home: JourneyNotificationScreen(appointmentId: 'apt-1'),
        ),
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

    await tester.pumpWidget(
      UncontrolledProviderScope(
        container: container,
        child: const MaterialApp(
          home: JourneyNotificationScreen(appointmentId: 'apt-1'),
        ),
      ),
    );

    expect(find.text('Bạn chưa có thông báo nào.'), findsOneWidget);
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
      body: 'Nhân viên đã xác nhận bạn đến khám.',
      createdAt: DateTime.utc(2026, 7, 30, 8),
      isRead: false,
    ),
    PatientNotification(
      id: 'result',
      title: 'Kết quả xét nghiệm đã sẵn sàng',
      body: 'Bác sĩ sẽ đọc kết quả cho bạn.',
      createdAt: DateTime.utc(2026, 7, 30, 9),
      isRead: false,
    ),
  ],
  updatedAt: DateTime.utc(2026, 7, 30),
);

class NotificationRepository implements JourneyRepository {
  final List<String> readNotificationIds = [];

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
  Future<PatientJourney> bootstrap({
    required Appointment appointment,
    required String patientId,
  }) => Future<PatientJourney>.error(UnimplementedError());

  @override
  Future<void> reset(PatientJourney journey) =>
      Future<void>.error(UnimplementedError());
}
