import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../models/appointment.dart';
import '../data/journey_repository.dart';
import '../domain/journey_models.dart';
import '../domain/journey_transition.dart';

class JourneyController extends StateNotifier<AsyncValue<PatientJourney?>> {
  JourneyController({
    required JourneyRepository repository,
    required bool demoMode,
    void Function(String? error)? onActionError,
  }) : _repository = repository,
       _demoMode = demoMode,
       _onActionError = onActionError ?? _ignoreActionError,
       super(const AsyncData(null));

  final JourneyRepository _repository;
  final bool _demoMode;
  final void Function(String? error) _onActionError;
  String? _activePatientId;

  Future<PatientJourney> bootstrap({
    required Appointment appointment,
    required String patientId,
  }) async {
    if (_activePatientId != null && _activePatientId != patientId) {
      state = const AsyncData(null);
    }
    state = const AsyncLoading();
    _onActionError(null);
    try {
      final journey = await _repository.bootstrap(
        appointment: appointment,
        patientId: patientId,
      );
      _activePatientId = patientId;
      state = AsyncData(journey);
      return journey;
    } catch (error, stackTrace) {
      state = AsyncError(error, stackTrace);
      rethrow;
    }
  }

  Future<void> advance(JourneyEvent event) =>
      _runDemoAction((journey) => _repository.advance(journey, event));

  Future<void> acknowledgePayment(PaymentMethod method) => _runDemoAction(
    (journey) => _repository.acknowledgePayment(journey, method),
  );

  Future<void> markNotificationRead(String notificationId) async {
    final journey = state.valueOrNull;
    if (journey == null) return;
    await _run(
      () => _repository.markNotificationRead(journey, notificationId),
      retainOnInvalidTransition: false,
    );
  }

  Future<void> resetCurrentJourney() async {
    final journey = state.valueOrNull;
    try {
      if (journey != null) await _repository.reset(journey);
      _activePatientId = null;
      _onActionError(null);
      state = const AsyncData(null);
    } catch (error, stackTrace) {
      state = AsyncError(error, stackTrace);
    }
  }

  Future<void> _runDemoAction(
    Future<PatientJourney> Function(PatientJourney journey) action,
  ) async {
    if (!_demoMode) {
      _onActionError('Tính năng đang chờ backend triển khai');
      return;
    }
    final journey = state.valueOrNull;
    if (journey == null) return;
    await _run(() => action(journey), retainOnInvalidTransition: true);
  }

  Future<void> _run(
    Future<PatientJourney> Function() action, {
    required bool retainOnInvalidTransition,
  }) async {
    _onActionError(null);
    try {
      state = AsyncData(await action());
    } on InvalidJourneyTransition catch (error) {
      if (retainOnInvalidTransition) _onActionError(error.toString());
    } catch (error, stackTrace) {
      state = AsyncError(error, stackTrace);
    }
  }

  static void _ignoreActionError(String? _) {}
}
