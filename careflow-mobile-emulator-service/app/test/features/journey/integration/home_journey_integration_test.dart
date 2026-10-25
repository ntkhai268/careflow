import 'package:careflow_patient/features/journey/application/journey_controller.dart';
import 'package:careflow_patient/features/journey/application/journey_providers.dart';
import 'package:careflow_patient/features/journey/data/journey_repository.dart';
import 'package:careflow_patient/features/journey/domain/journey_models.dart';
import 'package:careflow_patient/models/patient.dart';
import 'package:careflow_patient/providers/auth_provider.dart';
import 'package:careflow_patient/providers/patient_provider.dart';
import 'package:careflow_patient/screens/home/home_screen.dart';
import 'package:careflow_patient/services/api_service.dart';
import 'package:careflow_patient/services/auth_service.dart';
import 'package:careflow_patient/services/patient_service.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:go_router/go_router.dart';

void main() {
  testWidgets('home booking quick actions open the real booking flow', (
    tester,
  ) async {
    tester.view.physicalSize = const Size(1080, 2400);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);

    final controller = JourneyController(
      repository: const UnavailableJourneyRepository(),
      demoMode: true,
    );
    final router = GoRouter(
      initialLocation: '/',
      routes: [
        GoRoute(path: '/', builder: (_, _) => const HomeScreen()),
        GoRoute(
          path: '/booking/step1',
          builder: (_, _) => const Scaffold(body: Text('booking step 1')),
        ),
      ],
    );
    addTearDown(router.dispose);

    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          authProvider.overrideWith((ref) => HomeAuthNotifier()),
          patientProvider.overrideWith((ref) => HomePatientNotifier(ref)),
          journeyControllerProvider.overrideWith((ref) => controller),
        ],
        child: MaterialApp.router(routerConfig: router),
      ),
    );

    final bookingAction = find.descendant(
      of: find.byKey(const Key('home-quick-action-0')),
      matching: find.byType(GestureDetector),
    );
    tester.widget<GestureDetector>(bookingAction).onTap!();
    await tester.pumpAndSettle();

    expect(find.text('booking step 1'), findsOneWidget);
  });

  testWidgets(
    'home replaces the mock appointment with the active contextual journey',
    (tester) async {
      final controller = JourneyController(
        repository: const UnavailableJourneyRepository(),
        demoMode: true,
      )..state = AsyncData(activeJourney);
      final router = GoRouter(
        initialLocation: '/',
        routes: [
          GoRoute(path: '/', builder: (_, _) => const HomeScreen()),
          GoRoute(
            path: '/journey/:appointmentId/queue',
            builder: (_, state) =>
                Text('queue ${state.pathParameters['appointmentId']}'),
          ),
        ],
      );
      addTearDown(router.dispose);

      await tester.pumpWidget(
        ProviderScope(
          overrides: [
            authProvider.overrideWith((ref) => HomeAuthNotifier()),
            patientProvider.overrideWith((ref) => HomePatientNotifier(ref)),
            journeyControllerProvider.overrideWith((ref) => controller),
          ],
          child: MaterialApp.router(routerConfig: router),
        ),
      );
      await tester.scrollUntilVisible(
        find.byKey(const Key('active-journey-card')),
        300,
        scrollable: find.byType(Scrollable).first,
      );

      expect(find.text('Đang chờ khám'), findsOneWidget);
      expect(find.text('STT: A-042'), findsNothing);

      await tester.tap(find.byKey(const Key('active-journey-card')));
      await tester.pumpAndSettle();

      expect(find.text('queue apt-home'), findsOneWidget);
    },
  );
}

final activeJourney = PatientJourney(
  appointmentId: 'apt-home',
  patientId: 'patient-a',
  status: JourneyStatus.waiting,
  clinicQueue: const QueueSnapshot(
    room: 'Phòng 21',
    peopleAhead: 3,
    expectedWait: '15 phút',
  ),
  laboratoryOrders: const [],
  timeline: const [],
  notifications: const [],
  updatedAt: DateTime.utc(2026, 7, 30),
);

class HomeAuthNotifier extends AuthNotifier {
  HomeAuthNotifier() : super(AuthService(ApiService(), useMock: true)) {
    state = const AuthState(status: AuthStatus.authenticated, userId: 'user-a');
  }
}

class HomePatientNotifier extends PatientNotifier {
  HomePatientNotifier(Ref ref) : super(PatientService(ApiService()), ref) {
    state = PatientState(
      patient: Patient(
        id: 'patient-a',
        userId: 'user-a',
        fullName: 'Nguyễn An',
      ),
    );
  }
}
