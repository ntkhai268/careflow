import 'package:careflow_patient/features/journey/domain/journey_models.dart';
import 'package:careflow_patient/features/journey/domain/journey_transition.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  final now = DateTime.utc(2026, 8, 18, 3, 30);

  test('accepts every legal direct-care transition', () {
    final transitions =
        <({JourneyStatus from, JourneyEvent event, JourneyStatus to})>[
          (
            from: JourneyStatus.booked,
            event: JourneyEvent.issueTicket,
            to: JourneyStatus.ticketIssued,
          ),
          (
            from: JourneyStatus.ticketIssued,
            event: JourneyEvent.staffScannedQr,
            to: JourneyStatus.checkedIn,
          ),
          (
            from: JourneyStatus.checkedIn,
            event: JourneyEvent.admittedToClinicQueue,
            to: JourneyStatus.waiting,
          ),
          (
            from: JourneyStatus.waiting,
            event: JourneyEvent.doctorCalled,
            to: JourneyStatus.called,
          ),
          (
            from: JourneyStatus.called,
            event: JourneyEvent.consultationStarted,
            to: JourneyStatus.inConsultation,
          ),
          (
            from: JourneyStatus.inConsultation,
            event: JourneyEvent.directPrescriptionIssued,
            to: JourneyStatus.prescribed,
          ),
          (
            from: JourneyStatus.prescribed,
            event: JourneyEvent.visitCompleted,
            to: JourneyStatus.completed,
          ),
        ];

    for (final transition in transitions) {
      final next = JourneyTransition.apply(
        journeyAt(transition.from),
        transition.event,
        now: now,
      );

      expect(next.status, transition.to);
      expect(next.updatedAt, now);
      expect(next.timeline, hasLength(1));
      expect(next.timeline.single.title, isNotEmpty);
      expect(next.notifications, hasLength(1));
      expect(next.notifications.single.isRead, isFalse);
    }
  });

  test('accepts every legal laboratory-care transition', () {
    final transitions =
        <({JourneyStatus from, JourneyEvent event, JourneyStatus to})>[
          (
            from: JourneyStatus.inConsultation,
            event: JourneyEvent.laboratoryOrdered,
            to: JourneyStatus.labOrdered,
          ),
          (
            from: JourneyStatus.labOrdered,
            event: JourneyEvent.paymentRequested,
            to: JourneyStatus.paymentPending,
          ),
          (
            from: JourneyStatus.paymentPending,
            event: JourneyEvent.paymentAcknowledged,
            to: JourneyStatus.waitingLab,
          ),
          (
            from: JourneyStatus.waitingLab,
            event: JourneyEvent.laboratoryStarted,
            to: JourneyStatus.labInProgress,
          ),
          (
            from: JourneyStatus.labInProgress,
            event: JourneyEvent.laboratoryResultsPublished,
            to: JourneyStatus.labResultReady,
          ),
          (
            from: JourneyStatus.labResultReady,
            event: JourneyEvent.admittedToResultReviewQueue,
            to: JourneyStatus.waitingResultReview,
          ),
          (
            from: JourneyStatus.waitingResultReview,
            event: JourneyEvent.resultReviewCalled,
            to: JourneyStatus.resultReview,
          ),
          (
            from: JourneyStatus.resultReview,
            event: JourneyEvent.finalPrescriptionIssued,
            to: JourneyStatus.prescribed,
          ),
        ];

    for (final transition in transitions) {
      final next = JourneyTransition.apply(
        journeyAt(transition.from),
        transition.event,
        now: now,
      );

      expect(next.status, transition.to);
      expect(next.timeline.single.occurredAt, now);
      expect(next.notifications.single.createdAt, now);
    }
  });

  test('accepts the pharmacy payment and dispensing transitions', () {
    final paymentPending = JourneyTransition.apply(
      journeyAt(JourneyStatus.prescribed),
      JourneyEvent.prescriptionPaymentRequested,
      now: now,
    );
    expect(paymentPending.status, JourneyStatus.prescriptionPaymentPending);

    final paid = JourneyTransition.apply(
      paymentPending,
      JourneyEvent.prescriptionPaymentAcknowledged,
      now: now.add(const Duration(minutes: 1)),
    );
    expect(paid.status, JourneyStatus.prescriptionPaid);

    final ready = JourneyTransition.apply(
      paid,
      JourneyEvent.medicationDispensed,
      now: now.add(const Duration(minutes: 2)),
    );
    expect(ready.status, JourneyStatus.medicationReady);

    final completed = JourneyTransition.apply(
      ready,
      JourneyEvent.visitCompleted,
      now: now.add(const Duration(minutes: 3)),
    );
    expect(completed.status, JourneyStatus.completed);
  });

  test('queues laboratory work without a separate payment transition', () {
    final queued = JourneyTransition.apply(
      journeyAt(JourneyStatus.labOrdered),
      JourneyEvent.laboratoryQueued,
      now: now,
    );

    expect(queued.status, JourneyStatus.waitingLab);
  });

  test('supports the final visit settlement and dispensing flow', () {
    final pending = JourneyTransition.apply(
      journeyAt(JourneyStatus.prescribed),
      JourneyEvent.settlementCalculated,
      now: now,
    );
    expect(pending.status, JourneyStatus.settlementPending);

    final due = JourneyTransition.apply(
      pending,
      JourneyEvent.settlementPaymentRequested,
      now: now.add(const Duration(minutes: 1)),
    );
    expect(due.status, JourneyStatus.paymentDue);

    final settled = JourneyTransition.apply(
      due,
      JourneyEvent.settlementAcknowledged,
      now: now.add(const Duration(minutes: 2)),
    );
    expect(settled.status, JourneyStatus.settled);

    final ready = JourneyTransition.apply(
      settled,
      JourneyEvent.medicationDispensed,
      now: now.add(const Duration(minutes: 3)),
    );
    expect(ready.status, JourneyStatus.medicationReady);
  });

  test('allows refund pending and refund completion without blocking dispense', () {
    final pending = JourneyTransition.apply(
      journeyAt(JourneyStatus.settlementPending),
      JourneyEvent.settlementRefundRequested,
      now: now,
    );
    expect(pending.status, JourneyStatus.refundPending);

    final refunded = JourneyTransition.apply(
      pending,
      JourneyEvent.refundAcknowledged,
      now: now.add(const Duration(minutes: 1)),
    );
    expect(refunded.status, JourneyStatus.refunded);

    final ready = JourneyTransition.apply(
      refunded,
      JourneyEvent.medicationDispensed,
      now: now.add(const Duration(minutes: 2)),
    );
    expect(ready.status, JourneyStatus.medicationReady);
  });

  test(
    'adds Vietnamese timeline and unread notification for a ticket event',
    () {
      final next = JourneyTransition.apply(
        journeyAt(JourneyStatus.booked),
        JourneyEvent.issueTicket,
        now: now,
      );

      expect(next.timeline.single.title, 'Đã phát hành phiếu khám');
      expect(next.notifications.single.title, 'Phiếu khám đã sẵn sàng');
      expect(next.notifications.single.isRead, isFalse);
    },
  );

  test('rejects doctor call before staff check-in', () {
    expect(
      () => JourneyTransition.apply(
        journeyAt(JourneyStatus.ticketIssued),
        JourneyEvent.doctorCalled,
      ),
      throwsA(isA<InvalidJourneyTransition>()),
    );
  });

  test('rejects consultation before doctor call', () {
    expect(
      () => JourneyTransition.apply(
        journeyAt(JourneyStatus.waiting),
        JourneyEvent.consultationStarted,
      ),
      throwsA(isA<InvalidJourneyTransition>()),
    );
  });

  test('rejects results before laboratory work starts', () {
    expect(
      () => JourneyTransition.apply(
        journeyAt(JourneyStatus.waitingLab),
        JourneyEvent.laboratoryResultsPublished,
      ),
      throwsA(isA<InvalidJourneyTransition>()),
    );
  });

  test('rejects prescribing from waiting without mutating the journey', () {
    final current = journeyAt(JourneyStatus.waiting);

    expect(
      () => JourneyTransition.apply(
        current,
        JourneyEvent.directPrescriptionIssued,
      ),
      throwsA(isA<InvalidJourneyTransition>()),
    );
    expect(current.status, JourneyStatus.waiting);
    expect(current.timeline, isEmpty);
    expect(current.notifications, isEmpty);
  });
}

PatientJourney journeyAt(JourneyStatus status) => PatientJourney(
  appointmentId: 'apt-47',
  patientId: 'patient-1',
  status: status,
  laboratoryOrders: const [],
  timeline: const [],
  notifications: const [],
  updatedAt: DateTime.utc(2026, 8, 18, 3),
);
