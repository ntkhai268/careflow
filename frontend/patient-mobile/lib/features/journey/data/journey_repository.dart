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

/// Optional capability for repositories that can settle the pharmacy step.
/// Keeping this separate preserves compatibility with existing queue/lab
/// adapters until the backend pharmacy contract is implemented.
abstract interface class PrescriptionPaymentRepository {
  Future<PatientJourney> acknowledgePrescriptionPayment(
    PatientJourney journey,
    PaymentMethod method,
  );
}

abstract interface class JourneySnapshotRepository {
  Future<void> restoreSnapshot(PatientJourney journey);

  Future<void> retireJourney(String patientId, String appointmentId);
}

abstract interface class DemoJourneySource {}

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
