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

part 'backend_journey_mapper.dart';

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
