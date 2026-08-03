import '../domain/journey_models.dart';

String journeyStorageKey(String patientId, String appointmentId) =>
    'careflow.journey.$patientId.$appointmentId';

class JourneyLoadResult {
  const JourneyLoadResult({
    this.journey,
    this.wasCorrupted = false,
    this.errorMessage,
  });

  final PatientJourney? journey;
  final bool wasCorrupted;
  final String? errorMessage;
}

abstract interface class JourneyStore {
  Future<JourneyLoadResult> load(String patientId, String appointmentId);

  Future<void> save(PatientJourney journey);

  Future<void> delete(String patientId, String appointmentId);
}
