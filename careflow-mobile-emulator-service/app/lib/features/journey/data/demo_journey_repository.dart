import '../../../models/appointment.dart';
import '../domain/journey_models.dart';
import '../domain/journey_transition.dart';
import 'journey_repository.dart';
import 'journey_store.dart';

class DemoJourneyRepository
    implements
        JourneyRepository,
        PrescriptionPaymentRepository,
        VisitSettlementRepository,
        JourneySnapshotRepository,
        DemoJourneySource {
  DemoJourneyRepository({required JourneyStore store, DateTime Function()? now})
    : _store = store,
      _now = now ?? DateTime.now;

  final JourneyStore _store;
  final DateTime Function() _now;
  DateTime? _lastTimestamp;
  static const _demoDoctorName = 'BS. Nguyễn Minh Anh (dữ liệu mô phỏng)';
  static const _demoConsultationFee = 150000;
  static const _demoPrepaidAmount = 150000;
  static const _demoMedicationTotal = 85000;

  @override
  Future<PatientJourney> bootstrap({
    required Appointment appointment,
    required String patientId,
  }) async {
    final cached = await _store.load(patientId, appointment.id);
    if (cached.journey != null) return cached.journey!;

    final suffix = appointment.id.codeUnits.fold<int>(0, (a, b) => a + b);
    final createdAt = _timestamp();
    final initial = PatientJourney(
      appointmentId: appointment.id,
      patientId: patientId,
      status: JourneyStatus.booked,
      doctorName: appointment.doctorName ?? _demoDoctorName,
      ticket: VisitTicket(
        code: 'CF-${appointment.id.toUpperCase()}',
        qrPayload: 'careflow://visit/${appointment.id}',
        queueNumber: '${40 + (suffix % 20)}',
        hospitalName: 'Bệnh viện CareFlow (dữ liệu mô phỏng)',
        specialtyName: appointment.departmentDisplayName,
        room: _roomFor(appointment),
        expectedWindow: appointment.timeSlot,
      ),
      laboratoryOrders: const [],
      timeline: cached.wasCorrupted
          ? [
              JourneyTimelineEvent(
                id: 'timeline-recovered-${createdAt.microsecondsSinceEpoch}',
                title: 'Đã khôi phục hành trình',
                detail: 'Dữ liệu hành trình lỗi đã được tạo lại an toàn.',
                occurredAt: createdAt,
              ),
            ]
          : const [],
      notifications: const [],
      updatedAt: createdAt,
    );
    final journey = JourneyTransition.apply(
      initial,
      JourneyEvent.issueTicket,
      now: _timestamp(),
    );
    await _store.save(journey);
    return journey;
  }

  @override
  Future<PatientJourney> advance(
    PatientJourney journey,
    JourneyEvent event,
  ) async {
    // Previously persisted demos may replay the removed lab payment event
    // after the order has already entered the queue. Treat it as a no-op.
    if (event == JourneyEvent.paymentRequested &&
        journey.status == JourneyStatus.waitingLab) {
      return journey;
    }
    var next = JourneyTransition.apply(journey, event, now: _timestamp());
    if (event == JourneyEvent.staffScannedQr) {
      next = JourneyTransition.apply(
        next,
        JourneyEvent.admittedToClinicQueue,
        now: _timestamp(),
      ).copyWith(clinicQueue: _clinicQueue(next));
      next = _appendNotification(
        next,
        id: 'clinic-turn-soon',
        title: 'Sắp đến lượt khám',
        body: 'Còn 3 người phía trước. Vui lòng theo dõi hàng đợi.',
      );
      await _store.save(next);
      return next;
    }
    if (event == JourneyEvent.laboratoryResultsPublished) {
      next = next.copyWith(
        laboratoryOrders: next.laboratoryOrders.map(_withDemoResult).toList(),
      );
      next = JourneyTransition.apply(
        next,
        JourneyEvent.admittedToResultReviewQueue,
        now: _timestamp(),
      ).copyWith(resultReviewQueue: _resultReviewQueue(next));
      await _store.save(next);
      return next;
    }
    switch (event) {
      case JourneyEvent.admittedToClinicQueue:
        next = next.copyWith(clinicQueue: _clinicQueue(next));
        next = _appendNotification(
          next,
          id: 'clinic-turn-soon',
          title: 'Sắp đến lượt khám',
          body: 'Còn 3 người phía trước. Vui lòng theo dõi hàng đợi.',
        );
      case JourneyEvent.laboratoryOrdered:
        next = next.copyWith(laboratoryOrders: _laboratoryOrders(next));
        next = JourneyTransition.apply(
          next,
          JourneyEvent.laboratoryQueued,
          now: _timestamp(),
        );
      case JourneyEvent.admittedToResultReviewQueue:
        next = next.copyWith(resultReviewQueue: _resultReviewQueue(next));
      case JourneyEvent.directPrescriptionIssued:
      case JourneyEvent.finalPrescriptionIssued:
        next = _withDemoOutcome(next);
        next = _appendNotification(
          next,
          id: 'follow-up-scheduled',
          title: 'Tái khám đã lên lịch',
          body: 'Lịch tái khám của bạn đã được đặt sau 7 ngày.',
        );
        next = JourneyTransition.apply(
          next,
          JourneyEvent.settlementCalculated,
          now: _timestamp(),
        ).copyWith(settlement: _calculateSettlement(next));
      case JourneyEvent.settlementCalculated:
        next = next.copyWith(settlement: _calculateSettlement(next));
      case JourneyEvent.settlementPaymentRequested:
        next = next.copyWith(
          settlement: next.settlement?.copyWith(
            status: VisitSettlementStatus.paymentDue,
          ),
        );
      case JourneyEvent.settlementRefundRequested:
        next = next.copyWith(
          settlement: next.settlement?.copyWith(
            status: VisitSettlementStatus.refundPending,
          ),
        );
      case JourneyEvent.settlementAcknowledged:
        next = next.copyWith(
          settlement: next.settlement?.copyWith(
            status: VisitSettlementStatus.settled,
            acknowledgedAt: _timestamp(),
          ),
        );
      case JourneyEvent.refundAcknowledged:
        next = next.copyWith(
          settlement: next.settlement?.copyWith(
            status: VisitSettlementStatus.refunded,
            acknowledgedAt: _timestamp(),
          ),
        );
      default:
        break;
    }
    await _store.save(next);
    return next;
  }

  @override
  Future<PatientJourney> acknowledgePayment(
    PatientJourney journey,
    PaymentMethod method,
  ) async {
    if (journey.status == JourneyStatus.waitingLab) {
      final next = journey.copyWith(
        payment: VisitPayment(
          method: method,
          amount: journey.laboratoryOrders.fold(
            0,
            (sum, order) => sum + order.price,
          ),
          acknowledgedAt: _timestamp(),
        ),
      );
      await _store.save(next);
      return next;
    }
    final next =
        JourneyTransition.apply(
          journey,
          JourneyEvent.paymentAcknowledged,
          now: _timestamp(),
        ).copyWith(
          payment: VisitPayment(
            method: method,
            amount: journey.laboratoryOrders.fold(
              0,
              (sum, order) => sum + order.price,
            ),
            acknowledgedAt: _timestamp(),
          ),
        );
    await _store.save(next);
    return next;
  }

  @override
  Future<PatientJourney> acknowledgePrescriptionPayment(
    PatientJourney journey,
    PaymentMethod method,
  ) async {
    final next =
        JourneyTransition.apply(
          journey,
          JourneyEvent.prescriptionPaymentAcknowledged,
          now: _timestamp(),
        ).copyWith(
          prescriptionPayment: VisitPayment(
            method: method,
            amount: _demoMedicationTotal,
            acknowledgedAt: _timestamp(),
          ),
        );
    await _store.save(next);
    return next;
  }

  @override
  Future<PatientJourney> acknowledgeSettlement(
    PatientJourney journey,
    PaymentMethod method,
  ) async {
    final timestamp = _timestamp();
    final next = JourneyTransition.apply(
      journey,
      JourneyEvent.settlementAcknowledged,
      now: timestamp,
    ).copyWith(
      settlement: journey.settlement?.copyWith(
        status: VisitSettlementStatus.settled,
        method: method,
        acknowledgedAt: timestamp,
      ),
    );
    await _store.save(next);
    return next;
  }

  @override
  Future<PatientJourney> markNotificationRead(
    PatientJourney journey,
    String notificationId,
  ) async {
    final next = journey.copyWith(
      notifications: journey.notifications
          .map(
            (notification) => notification.id == notificationId
                ? notification.copyWith(isRead: true)
                : notification,
          )
          .toList(),
      updatedAt: _timestamp(),
    );
    await _store.save(next);
    return next;
  }

  @override
  Future<void> reset(PatientJourney journey) =>
      _store.delete(journey.patientId, journey.appointmentId);

  @override
  Future<void> restoreSnapshot(PatientJourney journey) => _store.save(journey);

  @override
  Future<void> retireJourney(String patientId, String appointmentId) =>
      _store.delete(patientId, appointmentId);

  DateTime _timestamp() {
    var timestamp = _now().toUtc();
    final last = _lastTimestamp;
    if (last != null && !timestamp.isAfter(last)) {
      timestamp = last.add(const Duration(microseconds: 1));
    }
    _lastTimestamp = timestamp;
    return timestamp;
  }

  String _roomFor(Appointment appointment) =>
      '${appointment.departmentDisplayName} - Phòng 21';

  QueueSnapshot _clinicQueue(PatientJourney journey) => QueueSnapshot(
    room: journey.ticket!.room,
    peopleAhead: 3,
    expectedWait: '15 phút',
  );

  List<LaboratoryOrder> _laboratoryOrders(PatientJourney journey) => [
    LaboratoryOrder(
      id: '${journey.appointmentId}-cbc',
      name: 'Xét nghiệm công thức máu',
      department: 'Khoa Xét nghiệm',
      destination: 'Phòng xét nghiệm tầng 1',
      preparationNote: 'Nhịn ăn 8 giờ nếu được bác sĩ dặn',
      price: 120000,
    ),
    LaboratoryOrder(
      id: '${journey.appointmentId}-xray',
      name: 'X-quang ngực thẳng',
      department: 'Khoa Chẩn đoán hình ảnh',
      destination: 'Phòng X-quang tầng 1',
      preparationNote: 'Tháo vật dụng kim loại vùng ngực',
      price: 180000,
    ),
  ];

  LaboratoryOrder _withDemoResult(LaboratoryOrder order) => LaboratoryOrder(
    id: order.id,
    name: order.name,
    department: order.department,
    destination: order.destination,
    preparationNote: order.preparationNote,
    price: order.price,
    result: LaboratoryResult(
      id: '${order.id}-result',
      value: order.id.endsWith('cbc') ? '5.2' : 'Không thấy tổn thương cấp',
      unit: order.id.endsWith('cbc') ? 'G/L' : '',
      referenceRange: order.id.endsWith('cbc')
          ? '4.0 - 10.0'
          : 'Hình ảnh trong giới hạn thường gặp',
      source: 'Dữ liệu mô phỏng',
      reportedAt: _timestamp(),
    ),
  );

  QueueSnapshot _resultReviewQueue(PatientJourney journey) => QueueSnapshot(
    room: journey.ticket!.room,
    peopleAhead: 1,
    expectedWait: 'Sau bệnh nhân khám mới tiếp theo',
  );

  PatientJourney _withDemoOutcome(PatientJourney journey) => journey.copyWith(
    diagnosis: const DiagnosisSummary(
      title: 'Viêm họng cấp',
      detail: 'Niêm mạc họng sung huyết, chưa ghi nhận biến chứng.',
    ),
    prescription: Prescription(
      id: '${journey.appointmentId}-prescription',
      issuedAt: journey.updatedAt,
      items: const [
        PrescriptionItem(
          medicationName: 'Paracetamol 500 mg',
          dosage: '1 viên',
          route: 'Uống',
          frequency: '3 lần/ngày',
          duration: '5 ngày',
          caution: 'Uống sau ăn; không dùng quá liều khuyến cáo.',
          unitPrice: 25000,
          quantity: 1,
        ),
        PrescriptionItem(
          medicationName: 'Amoxicillin 500 mg',
          dosage: '1 viên',
          route: 'Uống',
          frequency: '2 lần/ngày',
          duration: '7 ngày',
          caution: 'Uống đủ liệu trình theo chỉ định.',
          unitPrice: 60000,
          quantity: 1,
        ),
      ],
    ),
    followUp: FollowUpAppointment(
      scheduledAt: journey.updatedAt.add(const Duration(days: 7)),
      room: journey.ticket!.room,
      note: 'Tái khám nếu triệu chứng không cải thiện.',
    ),
  );

  VisitSettlement _calculateSettlement(PatientJourney journey) =>
      VisitSettlement.calculate(
        consultationFee: _demoConsultationFee,
        laboratoryTotal: journey.laboratoryOrders.fold(
          0,
          (sum, order) => sum + order.price,
        ),
        medicationTotal: journey.prescription == null
            ? 0
            : _medicationTotal(journey.prescription!),
        prepaidAmount: _demoPrepaidAmount,
        calculatedAt: journey.updatedAt,
      );

  int _medicationTotal(Prescription prescription) {
    final pricedTotal = prescription.items.fold(
      0,
      (sum, item) => sum + item.lineAmount,
    );
    return pricedTotal == 0 ? _demoMedicationTotal : pricedTotal;
  }

  PatientJourney _appendNotification(
    PatientJourney journey, {
    required String id,
    required String title,
    required String body,
  }) {
    final timestamp = _timestamp();
    return journey.copyWith(
      notifications: [
        ...journey.notifications,
        PatientNotification(
          id: 'notification-$id-${timestamp.microsecondsSinceEpoch}',
          title: title,
          body: body,
          createdAt: timestamp,
          isRead: false,
        ),
      ],
      updatedAt: timestamp,
    );
  }
}
