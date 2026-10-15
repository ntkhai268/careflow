import 'package:careflow_patient/features/journey/application/journey_providers.dart';
import 'package:careflow_patient/features/journey/domain/journey_models.dart';
import 'package:careflow_patient/providers/auth_provider.dart';
import 'package:careflow_patient/providers/patient_provider.dart';
import 'package:careflow_patient/models/patient.dart';
import 'package:careflow_patient/services/patient_service.dart';
import '../data/journey_notification_outage_test.dart' show JourneyApi, failure;
import 'package:careflow_patient/screens/home/home_screen.dart';
import 'package:careflow_patient/services/api_service.dart';
import 'package:careflow_patient/services/auth_service.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  testWidgets('real journey bootstrap survives the notification outage', (
    tester,
  ) async {
    final api = JourneyApi()
      ..notificationError = failure('/notifications', 404);
    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          authProvider.overrideWith((ref) => FixtureAuth()),
          patientProvider.overrideWith((ref) => FixturePatient(ref)),
          demoModeProvider.overrideWithValue(false),
          apiServiceProvider.overrideWithValue(api),
        ],
        child: const MaterialApp(home: HomeScreen()),
      ),
    );
    await tester.pumpAndSettle();
    expect(find.byKey(const Key('active-journey-card')), findsOneWidget);
    expect(find.text('Không thể tải hành trình khám.'), findsNothing);
  });

  testWidgets(
    'notification outage shows its own notice and keeps journey card',
    (tester) async {
      await tester.pumpWidget(
        ProviderScope(
          overrides: [
            authProvider.overrideWith((ref) => FixtureAuth()),
            demoModeProvider.overrideWithValue(false),
            activeJourneyBootstrapProvider.overrideWith((ref) async {}),
            activeJourneyProvider.overrideWithValue(
              PatientJourney(
                appointmentId: 'appointment-1',
                patientId: 'patient-1',
                status: JourneyStatus.booked,
                laboratoryOrders: const [],
                timeline: const [],
                notifications: const [],
                updatedAt: DateTime.utc(2026, 10, 15),
              ),
            ),
            patientNotificationInboxProvider.overrideWith(UnavailableInbox.new),
          ],
          child: const MaterialApp(home: HomeScreen()),
        ),
      );
      await tester.pumpAndSettle();
      expect(find.byKey(const Key('active-journey-card')), findsOneWidget);
      expect(
        find.byKey(const Key('home-notification-unavailable')),
        findsOneWidget,
      );
      expect(find.text('Không thể tải hành trình khám.'), findsNothing);
    },
  );
}

class UnavailableInbox extends PatientNotificationInboxController {
  @override
  Future<List<PatientNotification>> build() async =>
      throw Exception('Notification unavailable');
}

class FixtureAuth extends AuthNotifier {
  FixtureAuth() : super(AuthService(ApiService(), useMock: true)) {
    state = const AuthState(status: AuthStatus.authenticated, userId: 'user-1');
  }
}

class FixturePatient extends PatientNotifier {
  FixturePatient(Ref ref) : super(PatientService(ApiService()), ref) {
    state = PatientState(
      patient: Patient(
        id: 'patient-1',
        userId: 'user-1',
        fullName: 'Synthetic patient',
      ),
    );
  }
}
