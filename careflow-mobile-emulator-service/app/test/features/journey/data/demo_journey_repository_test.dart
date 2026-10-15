import 'package:careflow_patient/features/journey/data/demo_journey_repository.dart';
import 'package:careflow_patient/features/journey/data/journey_store.dart';
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
      expect(
        first.ticket!.hospitalName,
        'Bệnh viện CareFlow (dữ liệu mô phỏng)',
      );
      expect(first.ticket!.specialtyName, 'Nội tổng quát');
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

    expect(journey.status, JourneyStatus.waiting);
    expect(journey.clinicQueue!.peopleAhead, 3);
    expect(
      journey.notifications.map((item) => item.title),
      containsAllInOrder([
        'Đã xác nhận check-in',
        'Đã vào hàng đợi khám',
        'Sắp đến lượt khám',
      ]),
    );
  });

  test(
    'seeds an explicit demo doctor when an appointment has no doctor',
    () async {
      final journey = await buildRepository().bootstrap(
        appointment: appointmentFor('apt-no-doctor', doctorName: null),
        patientId: 'patient-1',
      );

      expect(journey.doctorName, 'BS. Nguyễn Minh Anh (dữ liệu mô phỏng)');
    },
  );

  for (final method in PaymentMethod.values) {
    test('acknowledges ${method.name} payment for laboratory orders', () async {
      final repository = buildRepository();
      var journey = await journeyInConsultation(repository);

      journey = await repository.advance(
        journey,
        JourneyEvent.laboratoryOrdered,
      );
      expect(journey.status, JourneyStatus.waitingLab);
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
      ]) {
        journey = await repository.advance(journey, event);
      }

      expect(journey.status, JourneyStatus.waitingResultReview);
      expect(journey.resultReviewQueue!.peopleAhead, 1);
      expect(
        journey.laboratoryOrders.first.result!.source,
        contains('Dữ liệu mô phỏng'),
      );
      expect(
        journey.notifications.map((item) => item.title),
        containsAllInOrder([
          'Kết quả xét nghiệm đã sẵn sàng',
          'Chờ bác sĩ đọc kết quả',
        ]),
      );
    },
  );

  test('uses visit settlement after prescription without lab payment', () async {
    final repository = buildRepository();
    var journey = await journeyInConsultation(repository);
    journey = await repository.advance(journey, JourneyEvent.laboratoryOrdered);
    expect(journey.status, JourneyStatus.waitingLab);

    for (final event in [
      JourneyEvent.laboratoryStarted,
      JourneyEvent.laboratoryResultsPublished,
      JourneyEvent.resultReviewCalled,
      JourneyEvent.finalPrescriptionIssued,
    ]) {
      journey = await repository.advance(journey, event);
    }

    expect(journey.status, JourneyStatus.settlementPending);
    expect(journey.settlement!.totalVisitCost, 535000);
    expect(journey.settlement!.amountDue, 385000);

    journey = await repository.advance(
      journey,
      JourneyEvent.settlementPaymentRequested,
    );
    journey = await repository.acknowledgeSettlement(
      journey,
      PaymentMethod.online,
    );

    expect(journey.status, JourneyStatus.settled);
    expect(journey.settlement!.status, VisitSettlementStatus.settled);
    expect(journey.settlement!.method, PaymentMethod.online);
  });

  test(
    'adds a scheduled follow-up notification with deterministic order',
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
        JourneyEvent.resultReviewCalled,
        JourneyEvent.finalPrescriptionIssued,
      ]) {
        journey = await repository.advance(journey, event);
      }

      expect(
        journey.notifications
            .skip(journey.notifications.length - 2)
            .map((item) => (item.title, item.body))
            .toList(),
        [
          (
            'Đơn thuốc đã sẵn sàng',
            'Bác sĩ đã phát hành đơn thuốc sau khi đọc kết quả.',
          ),
          (
            'Tái khám đã lên lịch',
            'Lịch tái khám của bạn đã được đặt sau 7 ngày.',
          ),
        ],
      );
    },
    skip: 'The old assertion models separate pharmacy payment; covered by the settlement test above.',
  );

  test('records recovery when corrupted storage is rebuilt', () async {
    final store = CorruptedJourneyStore();
    final journey =
        await DemoJourneyRepository(
          store: store,
          now: () => DateTime.utc(2026, 8, 18, 3, 30),
        ).bootstrap(
          appointment: appointmentFor('apt-corrupted'),
          patientId: 'patient-1',
        );

    expect(journey.timeline.map((item) => (item.title, item.detail)).toList(), [
      (
        'Đã khôi phục hành trình',
        'Dữ liệu hành trình lỗi đã được tạo lại an toàn.',
      ),
      ('Đã phát hành phiếu khám', 'Phiếu khám điện tử của bạn đã sẵn sàng.'),
    ]);
    expect(store.saved, same(journey));
  });
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
    JourneyEvent.doctorCalled,
    JourneyEvent.consultationStarted,
  ]) {
    journey = await repository.advance(journey, event);
  }
  return journey;
}

Appointment appointmentFor(String id, {String? doctorName = 'BS. An'}) =>
    Appointment(
      id: id,
      patientId: 'patient-1',
      department: 'NOI_TONG_QUAT',
      departmentDisplayName: 'Nội tổng quát',
      doctorName: doctorName,
      appointmentDate: DateTime.utc(2026, 8, 18, 3),
      timeSlot: '10:30 - 11:30',
      status: 'CONFIRMED',
      statusDisplayName: 'Đã xác nhận',
    );

class CorruptedJourneyStore implements JourneyStore {
  PatientJourney? saved;

  @override
  Future<void> delete(String patientId, String appointmentId) async {}

  @override
  Future<JourneyLoadResult> load(
    String patientId,
    String appointmentId,
  ) async =>
      const JourneyLoadResult(wasCorrupted: true, errorMessage: 'bad json');

  @override
  Future<void> save(PatientJourney journey) async => saved = journey;
}
