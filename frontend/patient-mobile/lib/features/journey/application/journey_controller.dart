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
  int _generation = 0;
  final Set<String> _retiredJourneyKeys = {};

  Future<PatientJourney> bootstrap({
    required Appointment appointment,
    required String patientId,
  }) async {
    final generation = ++_generation;
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
      if (generation != _generation) return journey;
      _activePatientId = patientId;
      _retiredJourneyKeys.remove(_journeyKey(patientId, appointment.id));
      state = AsyncData(journey);
      return journey;
    } catch (error, stackTrace) {
      if (generation == _generation) {
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
    await _runMutation(
      journey,
      (value) => _repository.markNotificationRead(value, notificationId),
      reportInvalidTransition: false,
    );
  }

  Future<void> resetCurrentJourney() async {
    final generation = ++_generation;
    final journey = state.valueOrNull;
    if (journey == null) {
      _activePatientId = null;
      _onActionError(null);
      state = const AsyncData(null);
      return;
    }
    final key = _journeyKey(journey.patientId, journey.appointmentId);
    _retiredJourneyKeys.add(key);
    _activePatientId = null;
    _onActionError(null);
    state = const AsyncData(null);
    try {
      await _repository.reset(journey);
      if (generation == _generation) {
        state = const AsyncData(null);
      }
    } catch (_) {
      _retiredJourneyKeys.remove(key);
      if (generation == _generation) {
        _activePatientId = journey.patientId;
        state = AsyncData(journey);
        _onActionError('Không thể đặt lại hành trình. Vui lòng thử lại.');
      }
    }
  }

  Future<bool> retireAppointment({
    required String patientId,
    required String appointmentId,
  }) async {
    final generation = ++_generation;
    final previous = state.valueOrNull;
    final key = _journeyKey(patientId, appointmentId);
    _retiredJourneyKeys.add(key);
    if (previous?.patientId == patientId &&
        previous?.appointmentId == appointmentId) {
      state = const AsyncData(null);
      _activePatientId = null;
    }
    _onActionError(null);
    try {
      final repository = _repository;
      if (repository is JourneySnapshotRepository) {
        await (repository as JourneySnapshotRepository).retireJourney(
          patientId,
          appointmentId,
        );
      } else if (previous != null) {
        await repository.reset(previous);
      }
      return generation == _generation;
    } catch (_) {
      _retiredJourneyKeys.remove(key);
      if (generation == _generation && previous != null) {
        _activePatientId = previous.patientId;
        state = AsyncData(previous);
        _onActionError('Không thể xóa hành trình đã hủy. Vui lòng thử lại.');
      }
      return false;
    }
  }

  /// Drops in-memory journey state without deleting persisted patient data.
  ///
  /// Auth identity changes call this synchronously so an old account's pending
  /// bootstrap cannot publish after logout or account switch.
  void invalidateAccountScope() {
    ++_generation;
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
    return _runMutation(journey, action, reportInvalidTransition: true);
  }

  Future<bool> _runMutation(
    PatientJourney journey,
    Future<PatientJourney> Function(PatientJourney journey) action, {
    required bool reportInvalidTransition,
  }) async {
    final generation = ++_generation;
    _onActionError(null);
    try {
      final next = await action(journey);
      if (generation != _generation) {
        await _rollbackStaleMutation(journey);
        return false;
      }
      _activePatientId = next.patientId;
      state = AsyncData(next);
      return true;
    } on InvalidJourneyTransition catch (error) {
      if (generation == _generation && reportInvalidTransition) {
        _onActionError(error.toString());
      }
      return false;
    } catch (_) {
      if (generation == _generation) {
        state = AsyncData(journey);
        _onActionError('Không thể cập nhật hành trình. Vui lòng thử lại.');
      }
      return false;
    }
  }

  Future<void> _rollbackStaleMutation(PatientJourney snapshot) async {
    final repository = _repository;
    if (repository is! JourneySnapshotRepository) return;
    final snapshotRepository = repository as JourneySnapshotRepository;
    final key = _journeyKey(snapshot.patientId, snapshot.appointmentId);
    final current = state.valueOrNull;
    if (current?.patientId == snapshot.patientId &&
        current?.appointmentId == snapshot.appointmentId) {
      await snapshotRepository.restoreSnapshot(current!);
    } else if (_retiredJourneyKeys.contains(key)) {
      await snapshotRepository.retireJourney(
        snapshot.patientId,
        snapshot.appointmentId,
      );
    } else {
      await snapshotRepository.restoreSnapshot(snapshot);
    }
  }

  String _journeyKey(String patientId, String appointmentId) =>
      '$patientId::$appointmentId';

  static void _ignoreActionError(String? _) {}
}
