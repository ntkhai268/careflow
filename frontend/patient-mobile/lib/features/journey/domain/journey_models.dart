part 'journey_ticket_queue_models.dart';
part 'journey_lab_payment_models.dart';
part 'journey_clinical_models.dart';

enum JourneyStatus {
  booked,
  ticketIssued,
  checkedIn,
  waiting,
  called,
  inConsultation,
  labOrdered,
  paymentPending,
  waitingLab,
  labInProgress,
  labResultReady,
  waitingResultReview,
  resultReview,
  prescribed,
  /// Final visit settlement is being assembled after the prescription.
  settlementPending,
  /// Legacy lab/pharmacy payment states kept only for old demo snapshots.
  prescriptionPaymentPending,
  prescriptionPaid,
  medicationReady,
  /// Final settlement outcomes. These map to the backend Visit Settlement
  /// statuses and are deliberately independent from the laboratory queue.
  paymentDue,
  settled,
  refundPending,
  refunded,
  completed,
}

/// Payment methods supported by the Mobile MVP.
///
/// These are intentionally limited to the two methods agreed in the payment
/// contract. Health-insurance information may still exist on the patient
/// profile, but it is not a payment method handled by CareFlow.
enum PaymentMethod { online, cash }

extension PaymentMethodContract on PaymentMethod {
  /// Stable wire value for persistence and future Laboratory Order APIs.
  String get wireValue => switch (this) {
    PaymentMethod.online => 'ONLINE_MOCK',
    PaymentMethod.cash => 'CASH_AT_HOSPITAL',
  };

  static PaymentMethod fromWireValue(Object? value) => switch (
    value?.toString().toUpperCase()
  ) {
    'ONLINE_MOCK' || 'ONLINE' => PaymentMethod.online,
    'CASH_AT_HOSPITAL' || 'CASH' => PaymentMethod.cash,
    _ => throw FormatException('Unsupported payment method: $value'),
  };
}

enum VisitSettlementStatus { paymentDue, settled, refundPending, refunded }

extension VisitSettlementStatusContract on VisitSettlementStatus {
  String get wireValue => switch (this) {
    VisitSettlementStatus.paymentDue => 'PAYMENT_DUE',
    VisitSettlementStatus.settled => 'SETTLED',
    VisitSettlementStatus.refundPending => 'REFUND_PENDING',
    VisitSettlementStatus.refunded => 'REFUNDED',
  };

  static VisitSettlementStatus fromWireValue(Object? value) => switch (
    value?.toString().toUpperCase()
  ) {
    'PAYMENT_DUE' || 'PAYMENTDUE' => VisitSettlementStatus.paymentDue,
    'SETTLED' => VisitSettlementStatus.settled,
    'REFUND_PENDING' || 'REFUNDPENDING' =>
      VisitSettlementStatus.refundPending,
    'REFUNDED' => VisitSettlementStatus.refunded,
    _ => throw FormatException('Unsupported visit settlement status: $value'),
  };
}

const Object _unset = Object();

DateTime _utc(String value) => DateTime.parse(value).toUtc();

String _iso(DateTime value) => value.toUtc().toIso8601String();

Map<String, dynamic> _map(Object? value) =>
    Map<String, dynamic>.from(value! as Map);

List<T> _objects<T>(Object? value, T Function(Map<String, dynamic>) parse) =>
    (value as List<dynamic>).map((item) => parse(_map(item))).toList();

bool _sameList<T>(List<T> left, List<T> right) {
  if (left.length != right.length) return false;
  for (var index = 0; index < left.length; index++) {
    if (left[index] != right[index]) return false;
  }
  return true;
}

JourneyStatus _journeyStatusFromJson(String value) {
  // Old snapshots used separate lab/pharmacy payment states. Keep loading
  // those snapshots without making them part of the new default journey.
  return switch (value) {
    'labPaymentPending' => JourneyStatus.waitingLab,
    'settlementPaymentDue' => JourneyStatus.paymentDue,
    _ => JourneyStatus.values.byName(value),
  };
}

class PatientJourney {
  PatientJourney({
    required this.appointmentId,
    required this.patientId,
    required this.status,
    this.doctorName,
    this.ticket,
    this.clinicQueue,
    required List<LaboratoryOrder> laboratoryOrders,
    this.settlement,
    this.payment,
    this.prescriptionPayment,
    this.resultReviewQueue,
    this.diagnosis,
    this.prescription,
    this.followUp,
    required List<JourneyTimelineEvent> timeline,
    required List<PatientNotification> notifications,
    required DateTime updatedAt,
  }) : laboratoryOrders = List.unmodifiable(laboratoryOrders),
       timeline = List.unmodifiable(timeline),
       notifications = List.unmodifiable(notifications),
       updatedAt = updatedAt.toUtc();

  final String appointmentId;
  final String patientId;
  final JourneyStatus status;
  final String? doctorName;
  final VisitTicket? ticket;
  final QueueSnapshot? clinicQueue;
  final List<LaboratoryOrder> laboratoryOrders;
  final VisitSettlement? settlement;
  final VisitPayment? payment;
  final VisitPayment? prescriptionPayment;
  final QueueSnapshot? resultReviewQueue;
  final DiagnosisSummary? diagnosis;
  final Prescription? prescription;
  final FollowUpAppointment? followUp;
  final List<JourneyTimelineEvent> timeline;
  final List<PatientNotification> notifications;
  final DateTime updatedAt;

  PatientJourney copyWith({
    String? appointmentId,
    String? patientId,
    JourneyStatus? status,
    Object? doctorName = _unset,
    Object? ticket = _unset,
    Object? clinicQueue = _unset,
    List<LaboratoryOrder>? laboratoryOrders,
    Object? settlement = _unset,
    Object? payment = _unset,
    Object? prescriptionPayment = _unset,
    Object? resultReviewQueue = _unset,
    Object? diagnosis = _unset,
    Object? prescription = _unset,
    Object? followUp = _unset,
    List<JourneyTimelineEvent>? timeline,
    List<PatientNotification>? notifications,
    DateTime? updatedAt,
  }) => PatientJourney(
    appointmentId: appointmentId ?? this.appointmentId,
    patientId: patientId ?? this.patientId,
    status: status ?? this.status,
    doctorName: identical(doctorName, _unset)
        ? this.doctorName
        : doctorName as String?,
    ticket: identical(ticket, _unset) ? this.ticket : ticket as VisitTicket?,
    clinicQueue: identical(clinicQueue, _unset)
        ? this.clinicQueue
        : clinicQueue as QueueSnapshot?,
    laboratoryOrders: laboratoryOrders ?? this.laboratoryOrders,
    settlement: identical(settlement, _unset)
        ? this.settlement
        : settlement as VisitSettlement?,
    payment: identical(payment, _unset)
        ? this.payment
        : payment as VisitPayment?,
    prescriptionPayment: identical(prescriptionPayment, _unset)
        ? this.prescriptionPayment
        : prescriptionPayment as VisitPayment?,
    resultReviewQueue: identical(resultReviewQueue, _unset)
        ? this.resultReviewQueue
        : resultReviewQueue as QueueSnapshot?,
    diagnosis: identical(diagnosis, _unset)
        ? this.diagnosis
        : diagnosis as DiagnosisSummary?,
    prescription: identical(prescription, _unset)
        ? this.prescription
        : prescription as Prescription?,
    followUp: identical(followUp, _unset)
        ? this.followUp
        : followUp as FollowUpAppointment?,
    timeline: timeline ?? this.timeline,
    notifications: notifications ?? this.notifications,
    updatedAt: updatedAt ?? this.updatedAt,
  );

  Map<String, dynamic> toJson() => {
    'appointmentId': appointmentId,
    'patientId': patientId,
    'status': status.name,
    if (doctorName != null) 'doctorName': doctorName,
    if (ticket != null) 'ticket': ticket!.toJson(),
    if (clinicQueue != null) 'clinicQueue': clinicQueue!.toJson(),
    'laboratoryOrders': laboratoryOrders
        .map((order) => order.toJson())
        .toList(),
    if (settlement != null) 'settlement': settlement!.toJson(),
    if (payment != null) 'payment': payment!.toJson(),
    if (prescriptionPayment != null)
      'prescriptionPayment': prescriptionPayment!.toJson(),
    if (resultReviewQueue != null)
      'resultReviewQueue': resultReviewQueue!.toJson(),
    if (diagnosis != null) 'diagnosis': diagnosis!.toJson(),
    if (prescription != null) 'prescription': prescription!.toJson(),
    if (followUp != null) 'followUp': followUp!.toJson(),
    'timeline': timeline.map((event) => event.toJson()).toList(),
    'notifications': notifications.map((item) => item.toJson()).toList(),
    'updatedAt': _iso(updatedAt),
  };

  factory PatientJourney.fromJson(Map<String, dynamic> json) => PatientJourney(
    appointmentId: json['appointmentId'] as String,
    patientId: json['patientId'] as String,
    status: _journeyStatusFromJson(json['status'] as String),
    doctorName: json['doctorName'] as String?,
    ticket: json['ticket'] == null
        ? null
        : VisitTicket.fromJson(_map(json['ticket'])),
    clinicQueue: json['clinicQueue'] == null
        ? null
        : QueueSnapshot.fromJson(_map(json['clinicQueue'])),
    laboratoryOrders: _objects(
      json['laboratoryOrders'],
      LaboratoryOrder.fromJson,
    ),
    settlement: json['settlement'] == null
        ? null
        : VisitSettlement.fromJson(_map(json['settlement'])),
    payment: json['payment'] == null
        ? null
        : VisitPayment.fromJson(_map(json['payment'])),
    prescriptionPayment: json['prescriptionPayment'] == null
        ? null
        : VisitPayment.fromJson(_map(json['prescriptionPayment'])),
    resultReviewQueue: json['resultReviewQueue'] == null
        ? null
        : QueueSnapshot.fromJson(_map(json['resultReviewQueue'])),
    diagnosis: json['diagnosis'] == null
        ? null
        : DiagnosisSummary.fromJson(_map(json['diagnosis'])),
    prescription: json['prescription'] == null
        ? null
        : Prescription.fromJson(_map(json['prescription'])),
    followUp: json['followUp'] == null
        ? null
        : FollowUpAppointment.fromJson(_map(json['followUp'])),
    timeline: _objects(json['timeline'], JourneyTimelineEvent.fromJson),
    notifications: _objects(
      json['notifications'],
      PatientNotification.fromJson,
    ),
    updatedAt: _utc(json['updatedAt'] as String),
  );

  @override
  bool operator ==(Object other) =>
      other is PatientJourney &&
      appointmentId == other.appointmentId &&
      patientId == other.patientId &&
      status == other.status &&
      doctorName == other.doctorName &&
      ticket == other.ticket &&
      clinicQueue == other.clinicQueue &&
      _sameList(laboratoryOrders, other.laboratoryOrders) &&
      settlement == other.settlement &&
      payment == other.payment &&
      prescriptionPayment == other.prescriptionPayment &&
      resultReviewQueue == other.resultReviewQueue &&
      diagnosis == other.diagnosis &&
      prescription == other.prescription &&
      followUp == other.followUp &&
      _sameList(timeline, other.timeline) &&
      _sameList(notifications, other.notifications) &&
      updatedAt == other.updatedAt;

  @override
  int get hashCode => Object.hashAll([
    appointmentId,
    patientId,
    status,
    doctorName,
    ticket,
    clinicQueue,
    Object.hashAll(laboratoryOrders),
    settlement,
    payment,
    prescriptionPayment,
    resultReviewQueue,
    diagnosis,
    prescription,
    followUp,
    Object.hashAll(timeline),
    Object.hashAll(notifications),
    updatedAt,
  ]);
}
