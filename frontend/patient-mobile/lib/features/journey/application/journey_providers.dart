import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../providers/auth_provider.dart';
import '../../../providers/patient_provider.dart';
import '../../../services/notification_service.dart';
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
    return const UnavailableJourneyRepository();
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

final unreadJourneyNotificationCountProvider = Provider<int>(
  (ref) {
    final journey = ref.watch(activeJourneyProvider);
    if (journey != null) {
      return journey.notifications.where((item) => !item.isRead).length;
    }
    final inbox = ref.watch(notificationInboxProvider).valueOrNull;
    return inbox?.where((item) => !item.isRead).length ?? 0;
  },
);
