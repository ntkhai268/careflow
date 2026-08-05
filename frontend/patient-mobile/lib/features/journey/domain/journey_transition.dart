import 'journey_models.dart';

enum JourneyEvent {
  issueTicket,
  staffScannedQr,
  admittedToClinicQueue,
  doctorCalled,
  consultationStarted,
  laboratoryOrdered,
  laboratoryQueued,
  // Legacy replay events only. New lab orders never wait for payment.
  paymentRequested,
  directPrescriptionIssued,
  paymentAcknowledged,
  laboratoryStarted,
  laboratoryResultsPublished,
  admittedToResultReviewQueue,
  resultReviewCalled,
  finalPrescriptionIssued,
  // Visit-level settlement events.
  settlementCalculated,
  settlementPaymentRequested,
  settlementAcknowledged,
  settlementRefundRequested,
  refundAcknowledged,
  // Legacy pharmacy-payment events kept for old snapshots.
  prescriptionPaymentRequested,
  prescriptionPaymentAcknowledged,
  medicationDispensed,
  visitCompleted,
}

class InvalidJourneyTransition implements Exception {
  const InvalidJourneyTransition(this.status, this.event);

  final JourneyStatus status;
  final JourneyEvent event;

  @override
  String toString() => 'Không thể chuyển từ ${status.name} bằng ${event.name}.';
}

class JourneyTransition {
  const JourneyTransition._();

  static PatientJourney apply(
    PatientJourney current,
    JourneyEvent event, {
    DateTime? now,
  }) {
    final nextStatus = _transitions[current.status]?[event];
    if (nextStatus == null) {
      throw InvalidJourneyTransition(current.status, event);
    }

    final timestamp = (now ?? DateTime.now()).toUtc();
    final message = _messages[event]!;
    final suffix = '${event.name}-${timestamp.microsecondsSinceEpoch}';

    return current.copyWith(
      status: nextStatus,
      timeline: [
        ...current.timeline,
        JourneyTimelineEvent(
          id: 'timeline-$suffix',
          title: message.timelineTitle,
          detail: message.detail,
          occurredAt: timestamp,
        ),
      ],
      notifications: [
        ...current.notifications,
        PatientNotification(
          id: 'notification-$suffix',
          title: message.notificationTitle,
          body: message.detail,
          createdAt: timestamp,
          isRead: false,
        ),
      ],
      updatedAt: timestamp,
    );
  }
}

const Map<JourneyStatus, Map<JourneyEvent, JourneyStatus>> _transitions = {
  JourneyStatus.booked: {JourneyEvent.issueTicket: JourneyStatus.ticketIssued},
  JourneyStatus.ticketIssued: {
    JourneyEvent.staffScannedQr: JourneyStatus.checkedIn,
  },
  JourneyStatus.checkedIn: {
    JourneyEvent.admittedToClinicQueue: JourneyStatus.waiting,
  },
  JourneyStatus.waiting: {JourneyEvent.doctorCalled: JourneyStatus.called},
  JourneyStatus.called: {
    JourneyEvent.consultationStarted: JourneyStatus.inConsultation,
  },
  JourneyStatus.inConsultation: {
    JourneyEvent.laboratoryOrdered: JourneyStatus.labOrdered,
    JourneyEvent.directPrescriptionIssued: JourneyStatus.prescribed,
  },
  JourneyStatus.labOrdered: {
    JourneyEvent.laboratoryQueued: JourneyStatus.waitingLab,
    // Legacy snapshots may still replay the old payment gate.
    JourneyEvent.paymentRequested: JourneyStatus.paymentPending,
  },
  JourneyStatus.paymentPending: {
    JourneyEvent.paymentAcknowledged: JourneyStatus.waitingLab,
  },
  JourneyStatus.waitingLab: {
    JourneyEvent.laboratoryStarted: JourneyStatus.labInProgress,
  },
  JourneyStatus.labInProgress: {
    JourneyEvent.laboratoryResultsPublished: JourneyStatus.labResultReady,
  },
  JourneyStatus.labResultReady: {
    JourneyEvent.admittedToResultReviewQueue: JourneyStatus.waitingResultReview,
  },
  JourneyStatus.waitingResultReview: {
    JourneyEvent.resultReviewCalled: JourneyStatus.resultReview,
  },
  JourneyStatus.resultReview: {
    JourneyEvent.finalPrescriptionIssued: JourneyStatus.prescribed,
  },
  JourneyStatus.prescribed: {
    JourneyEvent.settlementCalculated: JourneyStatus.settlementPending,
    // Legacy pharmacy payment path retained for old snapshots only.
    JourneyEvent.prescriptionPaymentRequested:
        JourneyStatus.prescriptionPaymentPending,
    JourneyEvent.visitCompleted: JourneyStatus.completed,
  },
  JourneyStatus.settlementPending: {
    JourneyEvent.settlementPaymentRequested: JourneyStatus.paymentDue,
    JourneyEvent.settlementRefundRequested: JourneyStatus.refundPending,
    // A zero-balance settlement can be acknowledged directly.
    JourneyEvent.settlementAcknowledged: JourneyStatus.settled,
  },
  JourneyStatus.paymentDue: {
    JourneyEvent.settlementAcknowledged: JourneyStatus.settled,
  },
  JourneyStatus.settled: {
    JourneyEvent.medicationDispensed: JourneyStatus.medicationReady,
  },
  JourneyStatus.refundPending: {
    JourneyEvent.refundAcknowledged: JourneyStatus.refunded,
  },
  JourneyStatus.refunded: {
    JourneyEvent.medicationDispensed: JourneyStatus.medicationReady,
  },
  JourneyStatus.prescriptionPaymentPending: {
    JourneyEvent.prescriptionPaymentAcknowledged: JourneyStatus.prescriptionPaid,
  },
  JourneyStatus.prescriptionPaid: {
    JourneyEvent.medicationDispensed: JourneyStatus.medicationReady,
  },
  JourneyStatus.medicationReady: {
    JourneyEvent.visitCompleted: JourneyStatus.completed,
  },
};

const Map<JourneyEvent, _JourneyMessage> _messages = {
  JourneyEvent.issueTicket: _JourneyMessage(
    'Đã phát hành phiếu khám',
    'Phiếu khám đã sẵn sàng',
    'Phiếu khám điện tử của bạn đã sẵn sàng.',
  ),
  JourneyEvent.staffScannedQr: _JourneyMessage(
    'Đã xác nhận check-in',
    'Đã xác nhận check-in',
    'Nhân viên đã quét mã QR và xác nhận bạn đến khám.',
  ),
  JourneyEvent.admittedToClinicQueue: _JourneyMessage(
    'Đã vào hàng đợi khám',
    'Đã vào hàng đợi khám',
    'Bạn đã được thêm vào hàng đợi của phòng khám.',
  ),
  JourneyEvent.doctorCalled: _JourneyMessage(
    'Bác sĩ đã gọi',
    'Đã đến lượt bạn',
    'Vui lòng đến phòng khám khi được gọi.',
  ),
  JourneyEvent.consultationStarted: _JourneyMessage(
    'Đang khám bệnh',
    'Bác sĩ đang khám',
    'Bác sĩ đã bắt đầu buổi khám của bạn.',
  ),
  JourneyEvent.laboratoryOrdered: _JourneyMessage(
    'Đã chỉ định xét nghiệm',
    'Có chỉ định xét nghiệm',
    'Bác sĩ đã chỉ định các xét nghiệm cần thực hiện.',
  ),
  JourneyEvent.laboratoryQueued: _JourneyMessage(
    'Đã vào hàng đợi xét nghiệm',
    'Đã tiếp nhận chỉ định',
    'Chỉ định đã được đưa vào hàng đợi xét nghiệm. Vui lòng đến đúng nơi thực hiện.',
  ),
  JourneyEvent.paymentRequested: _JourneyMessage(
    'Chờ thanh toán xét nghiệm',
    'Cần xác nhận thanh toán',
    'Vui lòng chọn phương thức thanh toán cho xét nghiệm.',
  ),
  JourneyEvent.directPrescriptionIssued: _JourneyMessage(
    'Đã kê đơn thuốc',
    'Đơn thuốc đã sẵn sàng',
    'Bác sĩ đã phát hành đơn thuốc cho bạn.',
  ),
  JourneyEvent.paymentAcknowledged: _JourneyMessage(
    'Đã xác nhận thanh toán',
    'Đã xác nhận thanh toán',
    'Thanh toán xét nghiệm đã được ghi nhận.',
  ),
  JourneyEvent.laboratoryStarted: _JourneyMessage(
    'Đang thực hiện xét nghiệm',
    'Xét nghiệm đang được thực hiện',
    'Phòng xét nghiệm đã bắt đầu thực hiện chỉ định của bạn.',
  ),
  JourneyEvent.laboratoryResultsPublished: _JourneyMessage(
    'Đã có kết quả xét nghiệm',
    'Kết quả xét nghiệm đã sẵn sàng',
    'Kết quả xét nghiệm đã được công bố.',
  ),
  JourneyEvent.admittedToResultReviewQueue: _JourneyMessage(
    'Đã vào hàng đợi đọc kết quả',
    'Chờ bác sĩ đọc kết quả',
    'Vui lòng quay lại phòng khám để chờ bác sĩ đọc kết quả.',
  ),
  JourneyEvent.resultReviewCalled: _JourneyMessage(
    'Bác sĩ đang đọc kết quả',
    'Đã đến lượt đọc kết quả',
    'Bác sĩ đã bắt đầu đọc kết quả xét nghiệm của bạn.',
  ),
  JourneyEvent.finalPrescriptionIssued: _JourneyMessage(
    'Đã kê đơn sau đọc kết quả',
    'Đơn thuốc đã sẵn sàng',
    'Bác sĩ đã phát hành đơn thuốc sau khi đọc kết quả.',
  ),
  JourneyEvent.settlementCalculated: _JourneyMessage(
    'Đã lập quyết toán lượt khám',
    'Đã có tổng chi phí lượt khám',
    'Hệ thống đã tổng hợp phí khám, xét nghiệm và thuốc sau khi bác sĩ kết luận.',
  ),
  JourneyEvent.settlementPaymentRequested: _JourneyMessage(
    'Cần thanh toán phần còn lại',
    'Cần thanh toán phần còn lại',
    'Vui lòng chọn thanh toán trực tuyến mô phỏng hoặc tiền mặt tại bệnh viện.',
  ),
  JourneyEvent.settlementAcknowledged: _JourneyMessage(
    'Đã quyết toán lượt khám',
    'Đã ghi nhận thanh toán',
    'Khoản phải trả của lượt khám đã được ghi nhận.',
  ),
  JourneyEvent.settlementRefundRequested: _JourneyMessage(
    'Đang chờ hoàn tiền',
    'Khoản dư đang chờ hoàn',
    'Số tiền trả trước cao hơn tổng chi phí; khoản dư đang chờ bệnh viện hoàn.',
  ),
  JourneyEvent.refundAcknowledged: _JourneyMessage(
    'Đã hoàn khoản dư',
    'Đã hoàn khoản dư',
    'Khoản dư của lượt khám đã được ghi nhận hoàn tất.',
  ),
  JourneyEvent.prescriptionPaymentRequested: _JourneyMessage(
    'Chờ thanh toán tiền thuốc',
    'Cần thanh toán tiền thuốc',
    'Vui lòng chọn phương thức thanh toán để nhà thuốc chuẩn bị thuốc.',
  ),
  JourneyEvent.prescriptionPaymentAcknowledged: _JourneyMessage(
    'Đã thanh toán tiền thuốc',
    'Nhà thuốc đang chuẩn bị thuốc',
    'Thanh toán tiền thuốc đã được ghi nhận. Nhà thuốc đang chuẩn bị đơn.',
  ),
  JourneyEvent.medicationDispensed: _JourneyMessage(
    'Thuốc đã sẵn sàng',
    'Đã sẵn sàng nhận thuốc',
    'Vui lòng đến quầy thuốc để nhận thuốc theo đơn.',
  ),
  JourneyEvent.visitCompleted: _JourneyMessage(
    'Đã hoàn tất lượt khám',
    'Lượt khám đã hoàn tất',
    'Cảm ơn bạn đã sử dụng CareFlow.',
  ),
};

class _JourneyMessage {
  const _JourneyMessage(
    this.timelineTitle,
    this.notificationTitle,
    this.detail,
  );

  final String timelineTitle;
  final String notificationTitle;
  final String detail;
}
