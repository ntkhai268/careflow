import 'package:careflow_patient/features/journey/data/demo_journey_repository.dart';
import 'package:careflow_patient/features/journey/data/shared_preferences_journey_store.dart';
import 'package:careflow_patient/features/journey/domain/journey_models.dart';
import 'package:careflow_patient/features/journey/domain/journey_transition.dart';
import 'package:careflow_patient/models/appointment.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';

void main() {
  setUp(() => SharedPreferences.setMockInitialValues({}));

  test(
    'bootstraps an appointment from booked to a stable issued ticket',
    () async {
      final repository = buildRepository();
      final appointment = appointmentFor('apt-47');

      final first = await repository.bootstrap(
        appointment: appointment,
        patientId: 'patient-1',
      );
      final second = await repository.bootstrap(
        appointment: appointment,
        patientId: 'patient-1',
      );

      expect(first.status, JourneyStatus.ticketIssued);
      expect(first.ticket!.code, 'CF-APT-47');
      expect(first.ticket!.qrPayload, 'careflow://visit/apt-47');
      expect(first.ticket!.queueNumber, '57');
      expect(second.ticket, first.ticket);
    },
  );

  test('creates the deterministic clinic queue after staff check-in', () async {
    final repository = buildRepository();
    var journey = await repository.bootstrap(
      appointment: appointmentFor('apt-47'),
      patientId: 'patient-1',
    );

    journey = await repository.advance(journey, JourneyEvent.staffScannedQr);
    journey = await repository.advance(
      journey,
      JourneyEvent.admittedToClinicQueue,
    );

    expect(journey.status, JourneyStatus.waiting);
    expect(journey.clinicQueue!.peopleAhead, 3);
  });

  for (final method in PaymentMethod.values) {
    test('acknowledges ${method.name} payment for laboratory orders', () async {
      final repository = buildRepository();
      var journey = await journeyInConsultation(repository);

      journey = await repository.advance(
        journey,
        JourneyEvent.laboratoryOrdered,
      );
      expect(journey.status, JourneyStatus.labOrdered);
      expect(journey.laboratoryOrders, hasLength(2));
      expect(journey.laboratoryOrders.first.name, 'Xét nghiệm công thức máu');
      expect(journey.laboratoryOrders.last.name, 'X-quang ngực thẳng');

      journey = await repository.advance(
        journey,
        JourneyEvent.paymentRequested,
      );
      journey = await repository.acknowledgePayment(journey, method);

      expect(journey.status, JourneyStatus.waitingLab);
      expect(journey.payment!.method, method);
      expect(journey.payment!.amount, 300000);
    });
  }

  test(
    'places completed laboratory work behind one new initial patient',
    () async {
      final repository = buildRepository();
      var journey = await journeyInConsultation(repository);
      for (final event in [
        JourneyEvent.laboratoryOrdered,
        JourneyEvent.paymentRequested,
      ]) {
        journey = await repository.advance(journey, event);
      }
      journey = await repository.acknowledgePayment(
        journey,
        PaymentMethod.online,
      );
      for (final event in [
        JourneyEvent.laboratoryStarted,
        JourneyEvent.laboratoryResultsPublished,
        JourneyEvent.admittedToResultReviewQueue,
      ]) {
        journey = await repository.advance(journey, event);
      }

      expect(journey.status, JourneyStatus.waitingResultReview);
      expect(journey.resultReviewQueue!.peopleAhead, 1);
      expect(
        journey.laboratoryOrders.first.result!.source,
        contains('Dữ liệu mô phỏng'),
      );
    },
  );
}

DemoJourneyRepository buildRepository() => DemoJourneyRepository(
  store: SharedPreferencesJourneyStore(),
  now: () => DateTime.utc(2026, 8, 18, 3, 30),
);

Future<PatientJourney> journeyInConsultation(
  DemoJourneyRepository repository,
) async {
  var journey = await repository.bootstrap(
    appointment: appointmentFor('apt-47'),
    patientId: 'patient-1',
  );
  for (final event in [
    JourneyEvent.staffScannedQr,
    JourneyEvent.admittedToClinicQueue,
    JourneyEvent.doctorCalled,
    JourneyEvent.consultationStarted,
  ]) {
    journey = await repository.advance(journey, event);
  }
  return journey;
}

Appointment appointmentFor(String id) => Appointment(
  id: id,
  patientId: 'patient-1',
  department: 'NOI_TONG_QUAT',
  departmentDisplayName: 'Nội tổng quát',
  doctorName: 'BS. An',
  appointmentDate: DateTime.utc(2026, 8, 18, 3),
  timeSlot: '10:30 - 11:30',
  status: 'CONFIRMED',
  statusDisplayName: 'Đã xác nhận',
);
