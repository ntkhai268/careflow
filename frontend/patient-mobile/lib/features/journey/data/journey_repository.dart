import '../../../models/appointment.dart';
import '../domain/journey_models.dart';
import '../domain/journey_transition.dart';

abstract interface class JourneyRepository {
  Future<PatientJourney> bootstrap({
    required Appointment appointment,
    required String patientId,
  });

  Future<PatientJourney> advance(PatientJourney journey, JourneyEvent event);

  Future<PatientJourney> acknowledgePayment(
    PatientJourney journey,
    PaymentMethod method,
  );

  Future<PatientJourney> markNotificationRead(
    PatientJourney journey,
    String notificationId,
  );

  Future<void> reset(PatientJourney journey);
}

/// Legacy capability retained only so old persisted demo snapshots can be
/// replayed. New code uses [VisitSettlementRepository] for the whole visit.
abstract interface class PrescriptionPaymentRepository {
  Future<PatientJourney> acknowledgePrescriptionPayment(
    PatientJourney journey,
    PaymentMethod method,
  );
}

/// Optional capability for the visit-level settlement adapter.
///
/// The mobile demo implements this locally today; the production adapter will
/// call Visit Settlement Service when that backend slice is available.
abstract interface class VisitSettlementRepository {
  Future<PatientJourney> acknowledgeSettlement(
    PatientJourney journey,
    PaymentMethod method,
  );
}

abstract interface class JourneySnapshotRepository {
  Future<void> restoreSnapshot(PatientJourney journey);

  Future<void> retireJourney(String patientId, String appointmentId);
}

class JourneyBackendUnavailable implements Exception {
  const JourneyBackendUnavailable();
}

class UnavailableJourneyRepository implements JourneyRepository {
  const UnavailableJourneyRepository();

  @override
  Future<PatientJourney> acknowledgePayment(
    PatientJourney journey,
    PaymentMethod method,
  ) => _unavailable();

  @override
  Future<PatientJourney> advance(PatientJourney journey, JourneyEvent event) =>
      _unavailable();

  @override
  Future<PatientJourney> bootstrap({
    required Appointment appointment,
    required String patientId,
  }) => _unavailable();

  @override
  Future<PatientJourney> markNotificationRead(
    PatientJourney journey,
    String notificationId,
  ) => _unavailable();

  @override
  Future<void> reset(PatientJourney journey) => _unavailable();

  Future<T> _unavailable<T>() =>
      Future<T>.error(const JourneyBackendUnavailable());
}
