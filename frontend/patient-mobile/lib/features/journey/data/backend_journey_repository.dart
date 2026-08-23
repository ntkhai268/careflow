import 'package:dio/dio.dart';

import '../../../models/appointment.dart';
import '../../../models/queue.dart' as queue_models;
import '../../../services/appointment_service.dart';
import '../../../services/consultation_service.dart';
import '../../../services/lab_service.dart';
import '../../../services/notification_service.dart';
import '../../../services/prescription_service.dart';
import '../../../services/queue_service.dart';
import '../domain/journey_models.dart';
import '../domain/journey_transition.dart';
import 'journey_repository.dart';

class JourneyOwnershipMismatch implements Exception {
  const JourneyOwnershipMismatch();
}

class BackendJourneyActionUnavailable implements Exception {
  const BackendJourneyActionUnavailable(this.message);

  final String message;

  @override
  String toString() => message;
}

class BackendJourneyResources {
  const BackendJourneyResources({
    required this.appointment,
    required this.patientId,
    this.ticket,
    this.clinicQueue,
    this.resultReviewQueue,
    this.consultation,
    this.labOrders = const [],
    this.prescriptions = const [],
    this.notifications = const [],
    this.patientAppointments = const [],
  });

  final Appointment appointment;
  final String patientId;
  final Map<String, dynamic>? ticket;
  final Map<String, dynamic>? clinicQueue;
  final Map<String, dynamic>? resultReviewQueue;
  final Map<String, dynamic>? consultation;
  final List<Map<String, dynamic>> labOrders;
  final List<Map<String, dynamic>> prescriptions;
  final List<Map<String, dynamic>> notifications;
  final List<Appointment> patientAppointments;
}

class BackendJourneyRepository implements JourneyRepository {
  BackendJourneyRepository({
    required AppointmentService appointmentService,
    required QueueService queueService,
    required ConsultationGateway consultationService,
    required LabGateway labService,
    required PrescriptionGateway prescriptionService,
    required NotificationGateway notificationService,
    BackendJourneyMapper? mapper,
  }) : _appointmentService = appointmentService,
       _queueService = queueService,
       _consultationService = consultationService,
       _labService = labService,
       _prescriptionService = prescriptionService,
       _notificationService = notificationService,
       _mapper = mapper ?? BackendJourneyMapper();

  final AppointmentService _appointmentService;
  final QueueService _queueService;
  final ConsultationGateway _consultationService;
  final LabGateway _labService;
  final PrescriptionGateway _prescriptionService;
  final NotificationGateway _notificationService;
  final BackendJourneyMapper _mapper;

  @override
  Future<PatientJourney> bootstrap({
    required Appointment appointment,
    required String patientId,
  }) {
    return _load(appointment: appointment, patientId: patientId);
  }

  @override
  Future<PatientJourney> advance(PatientJourney journey, JourneyEvent event) =>
      _unavailable();

  @override
  Future<PatientJourney> acknowledgePayment(
    PatientJourney journey,
    PaymentMethod method,
  ) => _unavailable();

  @override
  Future<PatientJourney> markNotificationRead(
    PatientJourney journey,
    String notificationId,
  ) async {
    await _notificationService.markRead(notificationId);
    return _load(
      appointment: await _appointmentService.getAppointmentById(
        journey.appointmentId,
      ),
      patientId: journey.patientId,
    );
  }

  @override
  Future<void> reset(PatientJourney journey) async {}

  Future<PatientJourney> _load({
    required Appointment appointment,
    required String patientId,
  }) async {
    final latestAppointment = await _appointmentService.getAppointmentById(
      appointment.id,
    );
    if (latestAppointment.patientId != patientId) {
      throw const JourneyOwnershipMismatch();
    }

    final ticket = await _optional(
      () => _queueService.getTicket(appointment.id),
    );
    final currentQueue = ticket == null
        ? null
        : await _optional(() => _queueService.getCurrent(ticket.patientId));
    final resultReview =
        currentQueue != null &&
        currentQueue.consultationPhase == 'RESULT_REVIEW';
    final clinicQueue = resultReview ? null : currentQueue;

    final consultations = await _consultationService.getByAppointment(
      appointment.id,
    );
    final consultation = _latest(consultations);
    final consultationId =
        _string(consultation?['id']) ?? appointment.sourceConsultationId;
    final labOrders = consultationId == null
        ? const <Map<String, dynamic>>[]
        : await _labService.getByConsultation(consultationId);
    final prescriptions = consultationId == null
        ? const <Map<String, dynamic>>[]
        : await _prescriptionService.getByConsultation(consultationId);
    final notifications = await _notificationService.inbox();
    final patientAppointments = await _appointmentService
        .getAppointmentsByPatientId(patientId);

    return _mapper.mapResources(
      BackendJourneyResources(
        appointment: latestAppointment,
        patientId: patientId,
        ticket: ticket == null ? null : _ticketMap(ticket),
        clinicQueue: clinicQueue == null ? null : _queueMap(clinicQueue),
        resultReviewQueue: resultReview ? _queueMap(currentQueue) : null,
        consultation: consultation,
        labOrders: labOrders,
        prescriptions: prescriptions,
        notifications: notifications,
        patientAppointments: patientAppointments,
      ),
    );
  }

  Future<T?> _optional<T>(Future<T> Function() load) async {
    try {
      return await load();
    } on QueueServiceException catch (error) {
      // Queue state is supplementary to the appointment/clinical journey.
      // A transient queue-service failure must not hide an otherwise valid
      // active appointment from the patient.
      if (error.statusCode == 404 || (error.statusCode ?? 0) >= 500) {
        return null;
      }
      rethrow;
    } on DioException catch (error) {
      if (error.response?.statusCode == 404) return null;
      rethrow;
    }
  }

  Future<T> _unavailable<T>() => Future<T>.error(
    const BackendJourneyActionUnavailable(
      'Tính năng này chỉ khả dụng khi backend cung cấp thao tác tương ứng.',
    ),
  );
}

class BackendJourneyMapper {
  BackendJourneyMapper({DateTime Function()? now}) : _now = now ?? DateTime.now;

  final DateTime Function() _now;

  PatientNotification? mapNotification(Map<String, dynamic> json) =>
      _notification(json);

  PatientJourney mapResources(BackendJourneyResources resources) {
    final appointment = resources.appointment;
    if (appointment.patientId != resources.patientId) {
      throw const JourneyOwnershipMismatch();
    }
    final consultation = resources.consultation;
    final prescriptions = resources.prescriptions
        .map(_prescription)
        .whereType<Prescription>()
        .toList(growable: false);
    final prescription = prescriptions.isEmpty ? null : prescriptions.first;
    final prescriptionJson = prescription == null
        ? null
        : resources.prescriptions.firstWhere(
            (json) => _nonEmpty(json['id']) == prescription.id,
            orElse: () => const {},
          );
    final labOrders = resources.labOrders
        .expand(_labOrders)
        .toList(growable: false);
    final updatedAt =
        _dateTime(consultation?['updatedAt']) ??
        _dateTime(consultation?['completedAt']) ??
        appointment.updatedAt ??
        _now().toUtc();

    return PatientJourney(
      appointmentId: appointment.id,
      patientId: resources.patientId,
      status: _status(
        appointment: appointment,
        ticket: resources.ticket,
        clinicQueue: resources.clinicQueue,
        consultation: consultation,
        labOrders: resources.labOrders,
        prescription: prescription,
      ),
      doctorName:
          _nonEmpty(consultation?['doctorName']) ?? appointment.doctorName,
      ticket: _ticket(resources, appointment),
      clinicQueue: _queue(resources.clinicQueue, resources.ticket),
      laboratoryOrders: labOrders,
      payment: _payment(resources.labOrders),
      resultReviewQueue: _queue(resources.resultReviewQueue, resources.ticket),
      diagnosis: _diagnosis(consultation, prescriptionJson),
      prescription: prescription,
      followUp: _followUp(
        resources.prescriptions,
        appointment,
        consultationId: _string(consultation?['id']),
        patientAppointments: resources.patientAppointments,
      ),
      timeline: _timeline(
        appointment,
        resources.clinicQueue,
        consultation,
        resources.labOrders,
      ),
      notifications: resources.notifications
          .map(_notification)
          .whereType<PatientNotification>()
          .toList(growable: false),
      updatedAt: updatedAt,
    );
  }

  JourneyStatus _status({
    required Appointment appointment,
    required Map<String, dynamic>? ticket,
    required Map<String, dynamic>? clinicQueue,
    required Map<String, dynamic>? consultation,
    required List<Map<String, dynamic>> labOrders,
    required Prescription? prescription,
  }) {
    final consultationStatus = _statusText(consultation?['status']);
    final queueStatus = _statusText(
      clinicQueue?['queueStatus'] ?? clinicQueue?['status'],
    );
    final ticketStatus = _statusText(ticket?['status']);
    final labStatus = _strongestLabStatus(labOrders);
    final appointmentStatus = _statusText(appointment.status);

    if (consultationStatus == 'COMPLETED' || appointmentStatus == 'COMPLETED') {
      return JourneyStatus.completed;
    }
    if (prescription != null) return JourneyStatus.prescribed;
    if (consultationStatus == 'READY_TO_COMPLETE') {
      return JourneyStatus.resultReview;
    }
    if (consultationStatus == 'AWAITING_REVIEW') {
      return labStatus == JourneyStatus.labResultReady
          ? JourneyStatus.waitingResultReview
          : JourneyStatus.waitingResultReview;
    }
    if (labStatus != null) return labStatus;
    if (consultationStatus == 'AWAITING_CLS') return JourneyStatus.labOrdered;
    if (consultationStatus == 'IN_PROGRESS') {
      return JourneyStatus.inConsultation;
    }
    if (queueStatus == 'IN_PROGRESS') return JourneyStatus.inConsultation;
    if (queueStatus == 'CALLED') return JourneyStatus.called;
    if (queueStatus == 'CHECKED_IN') return JourneyStatus.checkedIn;
    if (queueStatus == 'WAITING' &&
        (ticketStatus == 'TICKET_ISSUED' ||
            (ticketStatus.isEmpty && appointmentStatus == 'CONFIRMED'))) {
      return JourneyStatus.ticketIssued;
    }
    if (_isWaitingQueueStatus(queueStatus)) return JourneyStatus.waiting;
    if (appointmentStatus == 'IN_PROGRESS') return JourneyStatus.inConsultation;
    if (appointmentStatus == 'CHECKED_IN') return JourneyStatus.checkedIn;
    return ticket == null ? JourneyStatus.booked : JourneyStatus.ticketIssued;
  }

  JourneyStatus? _strongestLabStatus(List<Map<String, dynamic>> orders) {
    var pendingPayment = false;
    var ordered = false;
    var waiting = false;
    var inProgress = false;
    var resultReady = false;
    for (final order in orders) {
      final status = _statusText(order['status']);
      final paymentStatus = _statusText(order['paymentStatus']);
      if (paymentStatus == 'PENDING' || status == 'PAYMENT_PENDING') {
        pendingPayment = true;
      }
      if (status == 'ORDERED') ordered = true;
      if (status == 'QUEUED') waiting = true;
      if (status == 'CALLED' || status == 'IN_PROGRESS') inProgress = true;
      if (status == 'RESULT_AVAILABLE' || status == 'REVIEWED') {
        resultReady = true;
      }
    }
    if (resultReady) return JourneyStatus.labResultReady;
    if (inProgress) return JourneyStatus.labInProgress;
    if (waiting) return JourneyStatus.waitingLab;
    if (pendingPayment) return JourneyStatus.paymentPending;
    if (ordered) return JourneyStatus.labOrdered;
    return null;
  }

  VisitTicket? _ticket(
    BackendJourneyResources resources,
    Appointment appointment,
  ) {
    final ticket = resources.ticket;
    if (ticket == null) return null;
    return VisitTicket(
      code:
          _nonEmpty(ticket['ticketCode']) ??
          _nonEmpty(ticket['code']) ??
          _nonEmpty(ticket['ticketId']) ??
          appointment.id,
      qrPayload:
          _nonEmpty(ticket['qrToken']) ??
          _nonEmpty(ticket['qrPayload']) ??
          'careflow://visit/${appointment.id}',
      queueNumber:
          _nonEmpty(ticket['queueNumber']) ??
          _nonEmpty(appointment.queueNumber) ??
          '',
      hospitalName: _nonEmpty(ticket['hospitalName']) ?? 'Bệnh viện CareFlow',
      specialtyName:
          _nonEmpty(ticket['departmentDisplayName']) ??
          _nonEmpty(ticket['specialtyName']) ??
          appointment.departmentDisplayName,
      room:
          _nonEmpty(ticket['roomDisplayName']) ??
          _nonEmpty(ticket['room']) ??
          _nonEmpty(ticket['roomId']) ??
          appointment.roomDisplayName ??
          '',
      expectedWindow:
          _nonEmpty(ticket['timeSlot']) ??
          _nonEmpty(ticket['expectedWindow']) ??
          appointment.timeSlot,
    );
  }

  QueueSnapshot? _queue(
    Map<String, dynamic>? queue,
    Map<String, dynamic>? ticket,
  ) {
    if (queue == null) return null;
    final position = _int(queue['effectivePosition'] ?? queue['position']);
    final waitMinutes = _int(queue['estimatedWaitMinutes']);
    return QueueSnapshot(
      room:
          _nonEmpty(queue['roomCode']) ??
          _nonEmpty(queue['room']) ??
          _nonEmpty(queue['servicePointId']) ??
          _nonEmpty(ticket?['roomDisplayName']) ??
          '',
      peopleAhead: position == null || position <= 0 ? 0 : position - 1,
      expectedWait: waitMinutes == null
          ? _nonEmpty(queue['expectedWait']) ?? ''
          : '$waitMinutes phút',
    );
  }

  List<LaboratoryOrder> _labOrders(Map<String, dynamic> order) {
    final rawItems = order['items'];
    if (rawItems is! List) return const [];
    final orderStatus = _statusText(order['status']);
    return rawItems
        .whereType<Map>()
        .map((rawItem) {
          final item = Map<String, dynamic>.from(rawItem);
          return LaboratoryOrder(
            id:
                _nonEmpty(item['id']) ??
                _nonEmpty(item['serviceCode']) ??
                _nonEmpty(order['id']) ??
                '',
            name:
                _nonEmpty(item['serviceName']) ??
                _nonEmpty(item['name']) ??
                'Xét nghiệm',
            department:
                _nonEmpty(order['departmentName']) ??
                _nonEmpty(order['department']) ??
                'Khoa Xét nghiệm',
            destination:
                _nonEmpty(item['servicePointId']) ??
                _nonEmpty(order['servicePointId']) ??
                '',
            preparationNote:
                _nonEmpty(item['preparationNote']) ??
                _nonEmpty(order['clinicalNote']) ??
                '',
            price: _int(item['price'] ?? item['amount'] ?? order['price']) ?? 0,
            result: _releasedResult(item, orderStatus),
          );
        })
        .toList(growable: false);
  }

  LaboratoryResult? _releasedResult(
    Map<String, dynamic> item,
    String orderStatus,
  ) {
    final itemStatus = _statusText(item['status']);
    final value = _nonEmpty(item['resultValue'] ?? item['value']);
    final released =
        value != null &&
        itemStatus == 'COMPLETED' &&
        (orderStatus == 'RESULT_AVAILABLE' || orderStatus == 'REVIEWED');
    if (!released) return null;
    return LaboratoryResult(
      id: _nonEmpty(item['resultId']) ?? _nonEmpty(item['id']) ?? '',
      value: value,
      unit: _nonEmpty(item['unit'] ?? item['resultUnit']) ?? '',
      referenceRange: _nonEmpty(item['referenceRange']) ?? '',
      source: _nonEmpty(item['comment']) ?? 'Lab Service',
      reportedAt:
          _dateTime(item['performedAt']) ??
          _dateTime(item['reportedAt']) ??
          _now().toUtc(),
    );
  }

  VisitPayment? _payment(List<Map<String, dynamic>> labOrders) {
    for (final order in labOrders) {
      final amount = _int(
        order['settlementAmount'] ??
            order['paymentAmount'] ??
            order['totalAmount'] ??
            order['amount'],
      );
      if (amount == null) continue;
      final paymentStatus = _statusText(order['paymentStatus']);
      final method = switch (paymentStatus) {
        'PAID_ONLINE_MOCK' || 'PAID_ONLINE' => PaymentMethod.online,
        'PAID_CASH' => PaymentMethod.cash,
        _ => null,
      };
      if (method == null) continue;
      return VisitPayment(
        method: method,
        amount: amount,
        acknowledgedAt:
            _dateTime(order['paidAt']) ??
            _dateTime(order['updatedAt']) ??
            _now().toUtc(),
      );
    }
    return null;
  }

  DiagnosisSummary? _diagnosis(
    Map<String, dynamic>? consultation,
    Map<String, dynamic>? prescriptionJson,
  ) {
    final diagnosis = _nonEmpty(consultation?['diagnosis']);
    final icd10Code = _nonEmpty(consultation?['icd10Code']);
    final icd10Name = _nonEmpty(consultation?['icd10Name']);
    final prescriptionDiagnosis = _nonEmpty(prescriptionJson?['diagnosis']);
    final title = icd10Code != null && icd10Name != null
        ? '$icd10Code - $icd10Name'
        : icd10Code ?? icd10Name ?? '';
    if (title.isEmpty && diagnosis == null && prescriptionDiagnosis == null) {
      return null;
    }
    return DiagnosisSummary(
      title: title.isEmpty ? (diagnosis ?? prescriptionDiagnosis!) : title,
      detail: diagnosis ?? prescriptionDiagnosis ?? '',
    );
  }

  Prescription? _prescription(Map<String, dynamic> json) {
    final status = _statusText(json['status']);
    if (status != 'CONFIRMED' && status != 'DISPENSED') return null;
    final rawItems = json['items'];
    final items = rawItems is List
        ? rawItems
              .whereType<Map>()
              .map((rawItem) {
                final item = Map<String, dynamic>.from(rawItem);
                return PrescriptionItem(
                  medicationName:
                      _nonEmpty(item['medicineName']) ??
                      _nonEmpty(item['medicationName']) ??
                      '',
                  dosage: _nonEmpty(item['dosage']) ?? '',
                  route:
                      _nonEmpty(item['timing']) ??
                      _nonEmpty(item['route']) ??
                      _nonEmpty(item['unit']) ??
                      '',
                  frequency: _nonEmpty(item['frequency']) ?? '',
                  duration: _duration(item['duration']),
                  caution:
                      _nonEmpty(item['notes']) ??
                      _nonEmpty(item['caution']) ??
                      '',
                );
              })
              .toList(growable: false)
        : const <PrescriptionItem>[];
    if (items.isEmpty) return null;
    return Prescription(
      id: _nonEmpty(json['id']) ?? '',
      issuedAt:
          _dateTime(json['confirmedAt']) ??
          _dateTime(json['createdAt']) ??
          _now().toUtc(),
      items: items,
    );
  }

  FollowUpAppointment? _followUp(
    List<Map<String, dynamic>> prescriptions,
    Appointment appointment, {
    String? consultationId,
    List<Appointment> patientAppointments = const [],
  }) {
    for (final json in prescriptions) {
      final date = _date(json['followUpDate']);
      if (date == null) continue;
      return FollowUpAppointment(
        scheduledAt: date,
        room:
            _nonEmpty(json['followUpRoom']) ??
            _nonEmpty(json['roomDisplayName']) ??
            appointment.roomDisplayName ??
            '',
        note: _nonEmpty(json['followUpNote']) ?? _nonEmpty(json['notes']) ?? '',
      );
    }
    if (consultationId != null) {
      for (final followUp in patientAppointments) {
        if (followUp.id == appointment.id ||
            followUp.sourceConsultationId != consultationId) {
          continue;
        }
        return FollowUpAppointment(
          scheduledAt: followUp.appointmentDate,
          room: followUp.roomDisplayName ?? appointment.roomDisplayName ?? '',
          note: followUp.reason ?? followUp.notes ?? '',
        );
      }
    }
    return null;
  }

  PatientNotification? _notification(Map<String, dynamic> json) {
    final id = _nonEmpty(json['id']);
    final title = _nonEmpty(json['title']);
    final body = _nonEmpty(json['body']);
    final createdAt = _dateTime(json['createdAt']);
    if (id == null || title == null || body == null || createdAt == null) {
      return null;
    }
    final status = _statusText(json['status']);
    final action = json['action'] is Map
        ? Map<String, dynamic>.from(json['action'] as Map)
        : null;
    return PatientNotification(
      id: id,
      title: title,
      body: body,
      createdAt: createdAt,
      isRead: status == 'READ' || json['readAt'] != null,
      actionType: _nonEmpty(action?['type']) ?? _nonEmpty(json['actionType']),
      resourceId:
          _nonEmpty(action?['resourceId']) ?? _nonEmpty(json['resourceId']),
    );
  }

  List<JourneyTimelineEvent> _timeline(
    Appointment appointment,
    Map<String, dynamic>? clinicQueue,
    Map<String, dynamic>? consultation,
    List<Map<String, dynamic>> labOrders,
  ) {
    final events = <JourneyTimelineEvent>[
      JourneyTimelineEvent(
        id: 'appointment-${appointment.id}',
        title: 'Đã đặt lịch khám',
        detail: appointment.departmentDisplayName,
        occurredAt: appointment.createdAt ?? appointment.appointmentDate,
      ),
    ];
    final checkedInAt = _dateTime(clinicQueue?['checkedInAt']);
    if (checkedInAt != null) {
      events.add(
        JourneyTimelineEvent(
          id: 'checked-in-${clinicQueue?['entryId'] ?? appointment.id}',
          title: 'Đã xác nhận check-in',
          detail: 'Bạn đã có mặt tại bệnh viện và được đưa vào hàng đợi.',
          occurredAt: checkedInAt,
        ),
      );
    }
    final startedAt = _dateTime(consultation?['startedAt']);
    if (startedAt != null) {
      events.add(
        JourneyTimelineEvent(
          id: 'consultation-started-${consultation?['id'] ?? appointment.id}',
          title: 'Bắt đầu khám',
          detail: _nonEmpty(consultation?['doctorName']) ?? '',
          occurredAt: startedAt,
        ),
      );
    }
    for (final order in labOrders) {
      final createdAt = _dateTime(order['createdAt']);
      if (createdAt == null) continue;
      events.add(
        JourneyTimelineEvent(
          id: 'lab-order-${order['id'] ?? createdAt.microsecondsSinceEpoch}',
          title: 'Đã chỉ định xét nghiệm',
          detail: _nonEmpty(order['clinicalNote']) ?? '',
          occurredAt: createdAt,
        ),
      );
    }
    final completedAt = _dateTime(consultation?['completedAt']);
    if (completedAt != null) {
      events.add(
        JourneyTimelineEvent(
          id: 'consultation-completed-${consultation?['id'] ?? appointment.id}',
          title: 'Hoàn tất lượt khám',
          detail: _nonEmpty(consultation?['diagnosis']) ?? '',
          occurredAt: completedAt,
        ),
      );
    }
    return events
      ..sort((left, right) => left.occurredAt.compareTo(right.occurredAt));
  }

  bool _isWaitingQueueStatus(String status) =>
      status == 'WAITING' || status == 'QUEUED' || status == 'CHECKED_IN';
}

Map<String, dynamic> _ticketMap(queue_models.VisitTicket ticket) => {
  'ticketId': ticket.ticketId,
  'ticketCode': ticket.ticketCode,
  'appointmentId': ticket.appointmentId,
  'patientId': ticket.patientId,
  'queueNumber': ticket.queueNumber,
  'department': ticket.department,
  'departmentDisplayName': ticket.departmentDisplayName,
  'roomId': ticket.roomId,
  'roomDisplayName': ticket.roomDisplayName,
  'appointmentDate': ticket.appointmentDate.toIso8601String(),
  'timeSlot': ticket.timeSlot,
  'qrToken': ticket.qrToken,
  'status': ticket.status,
};

Map<String, dynamic> _queueMap(queue_models.PatientQueueStatus queue) => {
  'entryId': queue.entryId,
  'patientId': queue.patientId,
  'roomCode': queue.roomCode,
  'queueNumber': queue.queueNumber,
  'queueStatus': queue.status,
  'effectivePosition': queue.position,
  'estimatedWaitMinutes': queue.estimatedWaitMinutes,
  'checkedInAt': queue.checkedInAt?.toIso8601String(),
  'calledAt': queue.calledAt?.toIso8601String(),
  'startedAt': queue.startedAt?.toIso8601String(),
  'type': queue.type,
  'consultationPhase': queue.consultationPhase,
  'servicePointId': queue.servicePointId,
};

Map<String, dynamic>? _latest(List<Map<String, dynamic>> values) {
  if (values.isEmpty) return null;
  final copy = [...values];
  copy.sort((left, right) {
    final leftDate =
        _dateTime(left['updatedAt']) ??
        _dateTime(left['createdAt']) ??
        DateTime.fromMillisecondsSinceEpoch(0, isUtc: true);
    final rightDate =
        _dateTime(right['updatedAt']) ??
        _dateTime(right['createdAt']) ??
        DateTime.fromMillisecondsSinceEpoch(0, isUtc: true);
    return rightDate.compareTo(leftDate);
  });
  return copy.first;
}

String _statusText(Object? value) => _nonEmpty(value)?.toUpperCase() ?? '';

String? _string(Object? value) => _nonEmpty(value);

String? _nonEmpty(Object? value) {
  final text = value?.toString().trim();
  return text == null || text.isEmpty ? null : text;
}

int? _int(Object? value) {
  if (value is int) return value;
  if (value is num) return value.toInt();
  return int.tryParse(value?.toString() ?? '');
}

String _duration(Object? value) {
  final text = _nonEmpty(value);
  if (text == null) return '';
  final days = _int(value);
  return days == null ? text : '$days ngày';
}

DateTime? _dateTime(Object? value) {
  final text = _nonEmpty(value);
  if (text == null) return null;
  return DateTime.tryParse(text)?.toUtc();
}

DateTime? _date(Object? value) {
  final text = _nonEmpty(value);
  if (text == null) return null;
  final dateOnly = RegExp(r'^(\d{4})-(\d{2})-(\d{2})$').firstMatch(text);
  if (dateOnly != null) {
    return DateTime.utc(
      int.parse(dateOnly.group(1)!),
      int.parse(dateOnly.group(2)!),
      int.parse(dateOnly.group(3)!),
    );
  }
  return DateTime.tryParse(text)?.toUtc();
}
