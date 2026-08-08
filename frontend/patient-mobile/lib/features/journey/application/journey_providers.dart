import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../models/appointment.dart';
import '../../../providers/auth_provider.dart';
import '../../../providers/patient_provider.dart';
import '../../../services/appointment_service.dart';
import '../../../services/consultation_service.dart';
import '../../../services/lab_service.dart';
import '../../../services/notification_service.dart';
import '../../../services/prescription_service.dart';
import '../../../services/queue_service.dart';
import '../data/backend_journey_repository.dart';
import '../data/demo_journey_repository.dart';
import '../data/journey_repository.dart';
import '../data/journey_store.dart';
import '../data/shared_preferences_journey_store.dart';
import '../domain/journey_models.dart';
import 'journey_controller.dart';

final demoModeProvider = Provider<bool>(
  (ref) => const bool.fromEnvironment('DEMO_MODE', defaultValue: false),
);

/// Queue can move to the real backend before the remaining demo journey.
/// Production always uses Queue Service; hybrid demo opts in with REAL_QUEUE.
final realQueueEnabledProvider = Provider<bool>(
  (ref) =>
      !ref.watch(demoModeProvider) ||
      const bool.fromEnvironment('REAL_QUEUE', defaultValue: false),
);

final journeyStoreProvider = Provider<JourneyStore>(
  (ref) => SharedPreferencesJourneyStore(),
);

final journeyRepositoryProvider = Provider<JourneyRepository>((ref) {
  if (!ref.watch(demoModeProvider)) {
    return BackendJourneyRepository(
      appointmentService: ref.watch(appointmentServiceProvider),
      queueService: ref.watch(queueServiceProvider),
      consultationService: ref.watch(consultationServiceProvider),
      labService: ref.watch(labServiceProvider),
      prescriptionService: ref.watch(prescriptionServiceProvider),
      notificationService: ref.watch(notificationServiceProvider),
    );
  }
  return DemoJourneyRepository(store: ref.watch(journeyStoreProvider));
});

final journeyActionErrorProvider = StateProvider<String?>((ref) => null);

final journeyControllerProvider =
    StateNotifierProvider<JourneyController, AsyncValue<PatientJourney?>>((
      ref,
    ) {
      final controller = JourneyController(
        repository: ref.watch(journeyRepositoryProvider),
        demoMode: ref.watch(demoModeProvider),
        onActionError: (error) =>
            ref.read(journeyActionErrorProvider.notifier).state = error,
      );
      ref.listen(authProvider.select((auth) => (auth.status, auth.userId)), (
        previous,
        next,
      ) {
        if (previous != next) controller.invalidateAccountScope();
      });
      return controller;
    });

typedef JourneyAccountScope = ({String userId, String patientId});

final journeyAccountScopeProvider = Provider<JourneyAccountScope?>((ref) {
  final auth = ref.watch(authProvider);
  if (auth.status != AuthStatus.authenticated ||
      auth.userId == null ||
      auth.userId!.isEmpty) {
    return null;
  }
  final patient = ref.watch(patientProvider).patient;
  if (patient == null || patient.id.isEmpty || patient.userId != auth.userId) {
    return null;
  }
  return (userId: auth.userId!, patientId: patient.id);
});

final journeyForAppointmentProvider =
    Provider.family<AsyncValue<PatientJourney?>, String>((ref, appointmentId) {
      final scope = ref.watch(journeyAccountScopeProvider);
      if (scope == null) return const AsyncData(null);
      final current = ref.watch(journeyControllerProvider);
      return current.whenData(
        (journey) =>
            journey != null &&
                journey.appointmentId == appointmentId &&
                journey.patientId == scope.patientId
            ? journey
            : null,
      );
    });

final activeJourneyProvider = Provider<PatientJourney?>((ref) {
  final journey = ref.watch(journeyControllerProvider).valueOrNull;
  final scope = ref.watch(journeyAccountScopeProvider);
  return journey != null &&
          scope != null &&
          journey.patientId == scope.patientId
      ? journey
      : null;
});

/// Restores the active backend journey after the app is restarted or the
/// patient returns to the home tab. Booking currently bootstraps the journey
/// in memory, so without this reconciliation the home card disappears even
/// though the appointment and visit ticket still exist on the server.
final activeJourneyBootstrapProvider = FutureProvider.autoDispose<void>((
  ref,
) async {
  if (ref.watch(demoModeProvider)) return;

  final scope = ref.watch(journeyAccountScopeProvider);
  if (scope == null) return;

  final current = ref.read(journeyControllerProvider).valueOrNull;
  if (current != null && current.patientId == scope.patientId) return;

  final appointments = await ref
      .watch(appointmentServiceProvider)
      .getAppointmentsByPatientId(scope.patientId);
  final candidates = appointments
      .where(
        (appointment) =>
            appointment.patientId == scope.patientId &&
            appointment.allowsActiveJourney &&
            appointment.status != 'COMPLETED' &&
            appointment.status != 'CANCELLED',
      )
      .toList();
  if (candidates.isEmpty) return;

  candidates.sort((left, right) {
    final leftPriority = _activeAppointmentPriority(left);
    final rightPriority = _activeAppointmentPriority(right);
    final priorityComparison = leftPriority.compareTo(rightPriority);
    if (priorityComparison != 0) return priorityComparison;
    return left.appointmentDate.compareTo(right.appointmentDate);
  });

  await ref
      .read(journeyControllerProvider.notifier)
      .bootstrap(appointment: candidates.first, patientId: scope.patientId);
});

int _activeAppointmentPriority(Appointment appointment) =>
    switch (appointment.status.toUpperCase()) {
      'CHECKED_IN' || 'IN_PROGRESS' => 0,
      'CONFIRMED' => 1,
      _ => 2,
    };

/// Completed visit outcomes available to the signed-in patient.
///
/// The backend journey repository already composes consultation, laboratory,
/// prescription, and follow-up data for one appointment. Reusing that
/// composition here keeps the history screen aligned with the detail screen
/// and prevents the mobile app from inventing a second result contract.
final visitResultsProvider = FutureProvider.autoDispose<List<VisitResult>>((
  ref,
) async {
  final scope = ref.watch(journeyAccountScopeProvider);
  if (scope == null) return const [];

  final appointments = await ref
      .watch(appointmentServiceProvider)
      .getAppointmentsByPatientId(scope.patientId);
  final completedAppointments = appointments
      .where(
        (appointment) =>
            appointment.patientId == scope.patientId &&
            appointment.status.toUpperCase() == 'COMPLETED',
      )
      .toList(growable: false);
  final repository = ref.watch(journeyRepositoryProvider);
  final journeys = await Future.wait(
    completedAppointments.map(
      (appointment) async => VisitResult(
        appointment: appointment,
        journey: await repository.bootstrap(
          appointment: appointment,
          patientId: scope.patientId,
        ),
      ),
    ),
  );

  journeys.sort((left, right) {
    final leftDate = left.journey.updatedAt;
    final rightDate = right.journey.updatedAt;
    return rightDate.compareTo(leftDate);
  });
  return journeys;
});

class VisitResult {
  const VisitResult({required this.appointment, required this.journey});

  final Appointment appointment;
  final PatientJourney journey;
}

final unreadJourneyNotificationCountProvider = Provider<int>(
  (ref) =>
      ref
          .watch(activeJourneyProvider)
          ?.notifications
          .where((notification) => !notification.isRead)
          .length ??
      0,
);
