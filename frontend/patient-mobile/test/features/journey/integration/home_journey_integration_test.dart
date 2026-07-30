import 'package:careflow_patient/features/journey/application/journey_controller.dart';
import 'package:careflow_patient/features/journey/application/journey_providers.dart';
import 'package:careflow_patient/features/journey/data/journey_repository.dart';
import 'package:careflow_patient/features/journey/domain/journey_models.dart';
import 'package:careflow_patient/screens/home/home_screen.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:go_router/go_router.dart';

void main() {
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
