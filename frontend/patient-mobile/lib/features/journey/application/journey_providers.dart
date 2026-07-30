import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../data/demo_journey_repository.dart';
import '../data/journey_repository.dart';
import '../data/journey_store.dart';
import '../data/shared_preferences_journey_store.dart';
import '../domain/journey_models.dart';
import 'journey_controller.dart';

final demoModeProvider = Provider<bool>(
  (ref) => const bool.fromEnvironment('DEMO_MODE', defaultValue: false),
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
    StateNotifierProvider<JourneyController, AsyncValue<PatientJourney?>>(
      (ref) => JourneyController(
        repository: ref.watch(journeyRepositoryProvider),
        demoMode: ref.watch(demoModeProvider),
        onActionError: (error) =>
            ref.read(journeyActionErrorProvider.notifier).state = error,
      ),
    );

final journeyForAppointmentProvider =
    Provider.family<AsyncValue<PatientJourney?>, String>((ref, appointmentId) {
      final current = ref.watch(journeyControllerProvider);
      return current.whenData(
        (journey) => journey?.appointmentId == appointmentId ? journey : null,
      );
    });

final activeJourneyProvider = Provider<PatientJourney?>(
  (ref) => ref.watch(journeyControllerProvider).valueOrNull,
);

final unreadJourneyNotificationCountProvider = Provider<int>(
  (ref) =>
      ref
          .watch(activeJourneyProvider)
          ?.notifications
          .where((notification) => !notification.isRead)
          .length ??
      0,
);
