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
