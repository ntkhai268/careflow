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
  int _bootstrapGeneration = 0;

  Future<PatientJourney> bootstrap({
    required Appointment appointment,
    required String patientId,
  }) async {
    final generation = ++_bootstrapGeneration;
    if (!_demoMode) {
      final error = const JourneyBackendUnavailable();
      _onActionError(null);
      state = AsyncError(error, StackTrace.current);
      throw error;
    }
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
      if (generation != _bootstrapGeneration) return journey;
      _activePatientId = patientId;
      state = AsyncData(journey);
      return journey;
    } catch (error, stackTrace) {
      if (generation == _bootstrapGeneration) {
        state = AsyncError(error, stackTrace);
      }
      rethrow;
    }
  }

  Future<void> advance(JourneyEvent event) async {
    await _runDemoAction((journey) => _repository.advance(journey, event));
  }

  Future<bool> acknowledgePayment(PaymentMethod method) => _runDemoAction(
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
    ++_bootstrapGeneration;
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

  /// Drops in-memory journey state without deleting persisted patient data.
  ///
  /// Auth identity changes call this synchronously so an old account's pending
  /// bootstrap cannot publish after logout or account switch.
  void invalidateAccountScope() {
    ++_bootstrapGeneration;
    _activePatientId = null;
    _onActionError(null);
    state = const AsyncData(null);
  }

  Future<bool> _runDemoAction(
    Future<PatientJourney> Function(PatientJourney journey) action,
  ) async {
    if (!_demoMode) {
      _onActionError('Tính năng đang chờ backend triển khai');
      return false;
    }
    final journey = state.valueOrNull;
    if (journey == null) return false;
    return _run(() => action(journey), retainOnInvalidTransition: true);
  }

  Future<bool> _run(
    Future<PatientJourney> Function() action, {
    required bool retainOnInvalidTransition,
  }) async {
    _onActionError(null);
    try {
      state = AsyncData(await action());
      return true;
    } on InvalidJourneyTransition catch (error) {
      if (retainOnInvalidTransition) _onActionError(error.toString());
      return false;
    } catch (error, stackTrace) {
      state = AsyncError(error, stackTrace);
      return false;
    }
  }

  static void _ignoreActionError(String? _) {}
}
