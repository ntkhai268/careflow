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
  completed,
}

enum PaymentMethod { online, cash, insurance }

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

class VisitTicket {
  const VisitTicket({
    required this.code,
    required this.qrPayload,
    required this.queueNumber,
    required this.hospitalName,
    required this.specialtyName,
    required this.room,
    required this.expectedWindow,
  });

  final String code;
  final String qrPayload;
  final String queueNumber;
  final String hospitalName;
  final String specialtyName;
  final String room;
  final String expectedWindow;

  Map<String, dynamic> toJson() => {
    'code': code,
    'qrPayload': qrPayload,
    'queueNumber': queueNumber,
    'hospitalName': hospitalName,
    'specialtyName': specialtyName,
    'room': room,
    'expectedWindow': expectedWindow,
  };

  factory VisitTicket.fromJson(Map<String, dynamic> json) {
    final room = json['room'] as String;
    return VisitTicket(
      code: json['code'] as String,
      qrPayload: json['qrPayload'] as String,
      queueNumber: json['queueNumber'] as String,
      hospitalName:
          json['hospitalName'] as String? ??
          'Bệnh viện CareFlow (dữ liệu mô phỏng)',
      specialtyName:
          json['specialtyName'] as String? ?? _legacySpecialtyName(room),
      room: room,
      expectedWindow: json['expectedWindow'] as String,
    );
  }

  @override
  bool operator ==(Object other) =>
      other is VisitTicket &&
      code == other.code &&
      qrPayload == other.qrPayload &&
      queueNumber == other.queueNumber &&
      hospitalName == other.hospitalName &&
      specialtyName == other.specialtyName &&
      room == other.room &&
      expectedWindow == other.expectedWindow;

  @override
  int get hashCode => Object.hash(
    code,
    qrPayload,
    queueNumber,
    hospitalName,
    specialtyName,
    room,
    expectedWindow,
  );
}

String _legacySpecialtyName(String room) {
  const roomMarker = ' - Phòng';
  final roomMarkerIndex = room.indexOf(roomMarker);
  if (roomMarkerIndex > 0) return room.substring(0, roomMarkerIndex);
  return 'Chuyên khoa chưa xác định (dữ liệu mô phỏng)';
}

class QueueSnapshot {
  const QueueSnapshot({
    required this.room,
    required this.peopleAhead,
    required this.expectedWait,
  });

  final String room;
  final int peopleAhead;
  final String expectedWait;

  Map<String, dynamic> toJson() => {
    'room': room,
    'peopleAhead': peopleAhead,
    'expectedWait': expectedWait,
  };

  factory QueueSnapshot.fromJson(Map<String, dynamic> json) => QueueSnapshot(
    room: json['room'] as String,
    peopleAhead: (json['peopleAhead'] as num).toInt(),
    expectedWait: json['expectedWait'] as String,
  );

  @override
  bool operator ==(Object other) =>
      other is QueueSnapshot &&
      room == other.room &&
      peopleAhead == other.peopleAhead &&
      expectedWait == other.expectedWait;

  @override
  int get hashCode => Object.hash(room, peopleAhead, expectedWait);
}

class LaboratoryResult {
  LaboratoryResult({
    required this.id,
    required this.value,
    required this.unit,
    required this.referenceRange,
    required this.source,
    required DateTime reportedAt,
  }) : reportedAt = reportedAt.toUtc();

  final String id;
  final String value;
  final String unit;
  final String referenceRange;
  final String source;
  final DateTime reportedAt;

  Map<String, dynamic> toJson() => {
    'id': id,
    'value': value,
    'unit': unit,
    'referenceRange': referenceRange,
    'source': source,
    'reportedAt': _iso(reportedAt),
  };

  factory LaboratoryResult.fromJson(Map<String, dynamic> json) =>
      LaboratoryResult(
        id: json['id'] as String,
        value: json['value'] as String,
        unit: json['unit'] as String,
        referenceRange: json['referenceRange'] as String,
        source: json['source'] as String,
        reportedAt: _utc(json['reportedAt'] as String),
      );

  @override
  bool operator ==(Object other) =>
      other is LaboratoryResult &&
      id == other.id &&
      value == other.value &&
      unit == other.unit &&
      referenceRange == other.referenceRange &&
      source == other.source &&
      reportedAt == other.reportedAt;

  @override
  int get hashCode =>
      Object.hash(id, value, unit, referenceRange, source, reportedAt);
}

class LaboratoryOrder {
  LaboratoryOrder({
    required this.id,
    required this.name,
    required this.department,
    required this.destination,
    required this.preparationNote,
    required this.price,
    this.result,
  });

  final String id;
  final String name;
  final String department;
  final String destination;
  final String preparationNote;
  final int price;
  final LaboratoryResult? result;

  Map<String, dynamic> toJson() => {
    'id': id,
    'name': name,
    'department': department,
    'destination': destination,
    'preparationNote': preparationNote,
    'price': price,
    if (result != null) 'result': result!.toJson(),
  };

  factory LaboratoryOrder.fromJson(Map<String, dynamic> json) =>
      LaboratoryOrder(
        id: json['id'] as String,
        name: json['name'] as String,
        department: json['department'] as String,
        destination: json['destination'] as String,
        preparationNote: json['preparationNote'] as String,
        price: (json['price'] as num).toInt(),
        result: json['result'] == null
            ? null
            : LaboratoryResult.fromJson(_map(json['result'])),
      );

  @override
  bool operator ==(Object other) =>
      other is LaboratoryOrder &&
      id == other.id &&
      name == other.name &&
      department == other.department &&
      destination == other.destination &&
      preparationNote == other.preparationNote &&
      price == other.price &&
      result == other.result;

  @override
  int get hashCode => Object.hash(
    id,
    name,
    department,
    destination,
    preparationNote,
    price,
    result,
  );
}

class VisitPayment {
  VisitPayment({
    required this.method,
    required this.amount,
    required DateTime acknowledgedAt,
  }) : acknowledgedAt = acknowledgedAt.toUtc();

  final PaymentMethod method;
  final int amount;
  final DateTime acknowledgedAt;

  Map<String, dynamic> toJson() => {
    'method': method.name,
    'amount': amount,
    'acknowledgedAt': _iso(acknowledgedAt),
  };

  factory VisitPayment.fromJson(Map<String, dynamic> json) => VisitPayment(
    method: PaymentMethod.values.byName(json['method'] as String),
    amount: (json['amount'] as num).toInt(),
    acknowledgedAt: _utc(json['acknowledgedAt'] as String),
  );

  @override
  bool operator ==(Object other) =>
      other is VisitPayment &&
      method == other.method &&
      amount == other.amount &&
      acknowledgedAt == other.acknowledgedAt;

  @override
  int get hashCode => Object.hash(method, amount, acknowledgedAt);
}

class DiagnosisSummary {
  const DiagnosisSummary({required this.title, required this.detail});

  final String title;
  final String detail;

  Map<String, dynamic> toJson() => {'title': title, 'detail': detail};

  factory DiagnosisSummary.fromJson(Map<String, dynamic> json) =>
      DiagnosisSummary(
        title: json['title'] as String,
        detail: json['detail'] as String,
      );

  @override
  bool operator ==(Object other) =>
      other is DiagnosisSummary &&
      title == other.title &&
      detail == other.detail;

  @override
  int get hashCode => Object.hash(title, detail);
}

class PrescriptionItem {
  const PrescriptionItem({
    required this.medicationName,
    required this.dosage,
    required this.route,
    required this.frequency,
    required this.duration,
    required this.caution,
  });

  final String medicationName;
  final String dosage;
  final String route;
  final String frequency;
  final String duration;
  final String caution;

  Map<String, dynamic> toJson() => {
    'medicationName': medicationName,
    'dosage': dosage,
    'route': route,
    'frequency': frequency,
    'duration': duration,
    'caution': caution,
  };

  factory PrescriptionItem.fromJson(Map<String, dynamic> json) =>
      PrescriptionItem(
        medicationName: json['medicationName'] as String,
        dosage: json['dosage'] as String,
        route: json['route'] as String,
        frequency: json['frequency'] as String,
        duration: json['duration'] as String,
        caution: json['caution'] as String,
      );

  @override
  bool operator ==(Object other) =>
      other is PrescriptionItem &&
      medicationName == other.medicationName &&
      dosage == other.dosage &&
      route == other.route &&
      frequency == other.frequency &&
      duration == other.duration &&
      caution == other.caution;

  @override
  int get hashCode =>
      Object.hash(medicationName, dosage, route, frequency, duration, caution);
}

class Prescription {
  Prescription({
    required this.id,
    required DateTime issuedAt,
    required List<PrescriptionItem> items,
  }) : issuedAt = issuedAt.toUtc(),
       items = List.unmodifiable(items);

  final String id;
  final DateTime issuedAt;
  final List<PrescriptionItem> items;

  Map<String, dynamic> toJson() => {
    'id': id,
    'issuedAt': _iso(issuedAt),
    'items': items.map((item) => item.toJson()).toList(),
  };

  factory Prescription.fromJson(Map<String, dynamic> json) => Prescription(
    id: json['id'] as String,
    issuedAt: _utc(json['issuedAt'] as String),
    items: _objects(json['items'], PrescriptionItem.fromJson),
  );

  @override
  bool operator ==(Object other) =>
      other is Prescription &&
      id == other.id &&
      issuedAt == other.issuedAt &&
      _sameList(items, other.items);

  @override
  int get hashCode => Object.hash(id, issuedAt, Object.hashAll(items));
}

class FollowUpAppointment {
  FollowUpAppointment({
    required DateTime scheduledAt,
    required this.room,
    required this.note,
  }) : scheduledAt = scheduledAt.toUtc();

  final DateTime scheduledAt;
  final String room;
  final String note;

  Map<String, dynamic> toJson() => {
    'scheduledAt': _iso(scheduledAt),
    'room': room,
    'note': note,
  };

  factory FollowUpAppointment.fromJson(Map<String, dynamic> json) =>
      FollowUpAppointment(
        scheduledAt: _utc(json['scheduledAt'] as String),
        room: json['room'] as String,
        note: json['note'] as String,
      );

  @override
  bool operator ==(Object other) =>
      other is FollowUpAppointment &&
      scheduledAt == other.scheduledAt &&
      room == other.room &&
      note == other.note;

  @override
  int get hashCode => Object.hash(scheduledAt, room, note);
}

class JourneyTimelineEvent {
  JourneyTimelineEvent({
    required this.id,
    required this.title,
    required this.detail,
    required DateTime occurredAt,
  }) : occurredAt = occurredAt.toUtc();

  final String id;
  final String title;
  final String detail;
  final DateTime occurredAt;

  Map<String, dynamic> toJson() => {
    'id': id,
    'title': title,
    'detail': detail,
    'occurredAt': _iso(occurredAt),
  };

  factory JourneyTimelineEvent.fromJson(Map<String, dynamic> json) =>
      JourneyTimelineEvent(
        id: json['id'] as String,
        title: json['title'] as String,
        detail: json['detail'] as String,
        occurredAt: _utc(json['occurredAt'] as String),
      );

  @override
  bool operator ==(Object other) =>
      other is JourneyTimelineEvent &&
      id == other.id &&
      title == other.title &&
      detail == other.detail &&
      occurredAt == other.occurredAt;

  @override
  int get hashCode => Object.hash(id, title, detail, occurredAt);
}

class PatientNotification {
  PatientNotification({
    required this.id,
    required this.title,
    required this.body,
    required DateTime createdAt,
    required this.isRead,
    this.actionType,
    this.resourceId,
  }) : createdAt = createdAt.toUtc();

  final String id;
  final String title;
  final String body;
  final DateTime createdAt;
  final bool isRead;
  final String? actionType;
  final String? resourceId;

  PatientNotification copyWith({bool? isRead}) => PatientNotification(
    id: id,
    title: title,
    body: body,
    createdAt: createdAt,
    isRead: isRead ?? this.isRead,
    actionType: actionType,
    resourceId: resourceId,
  );

  Map<String, dynamic> toJson() => {
    'id': id,
    'title': title,
    'body': body,
    'createdAt': _iso(createdAt),
    'isRead': isRead,
    if (actionType != null) 'actionType': actionType,
    if (resourceId != null) 'resourceId': resourceId,
  };

  factory PatientNotification.fromJson(Map<String, dynamic> json) =>
      PatientNotification(
        id: json['id'] as String,
        title: json['title'] as String,
        body: json['body'] as String,
        createdAt: _utc(json['createdAt'] as String),
        isRead: json['isRead'] as bool,
        actionType: json['actionType'] as String?,
        resourceId: json['resourceId'] as String?,
      );

  @override
  bool operator ==(Object other) =>
      other is PatientNotification &&
      id == other.id &&
      title == other.title &&
      body == other.body &&
      createdAt == other.createdAt &&
      isRead == other.isRead &&
      actionType == other.actionType &&
      resourceId == other.resourceId;

  @override
  int get hashCode =>
      Object.hash(id, title, body, createdAt, isRead, actionType, resourceId);
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
    this.payment,
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
  final VisitPayment? payment;
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
    Object? payment = _unset,
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
    payment: identical(payment, _unset)
        ? this.payment
        : payment as VisitPayment?,
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
    if (payment != null) 'payment': payment!.toJson(),
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
    status: JourneyStatus.values.byName(json['status'] as String),
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
    payment: json['payment'] == null
        ? null
        : VisitPayment.fromJson(_map(json['payment'])),
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
      payment == other.payment &&
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
    payment,
    resultReviewQueue,
    diagnosis,
    prescription,
    followUp,
    Object.hashAll(timeline),
    Object.hashAll(notifications),
    updatedAt,
  ]);
}
