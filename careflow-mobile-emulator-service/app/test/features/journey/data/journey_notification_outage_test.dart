import 'package:careflow_patient/features/journey/data/backend_journey_repository.dart';
import 'package:careflow_patient/models/appointment.dart';
import 'package:careflow_patient/features/journey/domain/journey_models.dart';
import 'package:careflow_patient/services/api_service.dart';
import 'package:careflow_patient/services/appointment_service.dart';
import 'package:careflow_patient/services/consultation_service.dart';
import 'package:careflow_patient/services/lab_service.dart';
import 'package:careflow_patient/services/notification_service.dart';
import 'package:careflow_patient/services/prescription_service.dart';
import 'package:careflow_patient/services/queue_service.dart';
import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  late JourneyApi api;
  late BackendJourneyRepository repository;
  setUp(() {
    api = JourneyApi();
    repository = BackendJourneyRepository(
      appointmentService: AppointmentService(api),
      queueService: QueueService(api),
      consultationService: ConsultationService(api),
      labService: LabService(api),
      prescriptionService: PrescriptionService(api),
      notificationService: NotificationService(api),
    );
  });

  Future<PatientJourney> load() => repository.bootstrap(
    appointment: Appointment.fromJson(appointmentJson),
    patientId: 'patient-1',
  );

  for (final status in [404, 500, 503]) {
    test(
      'notification HTTP $status does not hide the verified appointment',
      () async {
        api.notificationError = failure('/notifications', status);
        final journey = await load();
        expect(journey.appointmentId, 'appointment-1');
        expect(journey.patientId, 'patient-1');
        expect(journey.notifications, isEmpty);
      },
    );
  }

  for (final type in [
    DioExceptionType.connectionError,
    DioExceptionType.connectionTimeout,
    DioExceptionType.receiveTimeout,
  ]) {
    test('notification $type does not hide the appointment', () async {
      api.notificationError = DioException(
        requestOptions: RequestOptions(path: '/notifications'),
        type: type,
      );
      expect((await load()).appointmentId, 'appointment-1');
    });
  }

  for (final status in [401, 403]) {
    test('notification authorization failure $status is not hidden', () async {
      final error = failure('/notifications', status);
      api.notificationError = error;
      await expectLater(load(), throwsA(same(error)));
    });
  }

  for (final path in [
    '/appointments/appointment-1',
    '/consultations/appointment/appointment-1',
  ]) {
    test('required journey failure at $path still fails the load', () async {
      api.requiredFailurePath = path;
      await expectLater(
        load(),
        throwsA(
          isA<DioException>().having(
            (e) => e.response?.statusCode,
            'status',
            503,
          ),
        ),
      );
    });
  }

  test('notifications are loaded again after recovery', () async {
    api.notificationError = failure('/notifications', 404);
    expect((await load()).notifications, isEmpty);
    api.notificationError = null;
    final recovered = await load();
    expect(recovered.notifications.single.id, 'notification-1');
  });
}

const appointmentJson = <String, dynamic>{
  'id': 'appointment-1',
  'patientId': 'patient-1',
  'department': 'NOI_TONG_QUAT',
  'appointmentDate': '2026-10-16',
  'timeSlot': '09:00-09:30',
  'status': 'CONFIRMED',
};

DioException failure(String path, int status) {
  final request = RequestOptions(path: path);
  return DioException(
    requestOptions: request,
    type: DioExceptionType.badResponse,
    response: Response(requestOptions: request, statusCode: status),
  );
}

class JourneyApi extends ApiService {
  DioException? notificationError;
  String? requiredFailurePath;

  @override
  Future<Response> get(
    String endpoint, {
    Map<String, dynamic>? queryParams,
    Options? options,
  }) async {
    if (endpoint == requiredFailurePath) throw failure(endpoint, 503);
    Object data;
    switch (endpoint) {
      case '/appointments/appointment-1':
        data = appointmentJson;
      case '/appointments/patient/patient-1':
        data = [appointmentJson];
      case '/queues/tickets/appointment/appointment-1':
        throw failure(endpoint, 404);
      case '/consultations/appointment/appointment-1':
        data = <Object>[];
      case '/notifications':
        if (notificationError != null) throw notificationError!;
        data = [
          {
            'id': 'notification-1',
            'title': 'Lịch khám',
            'body': 'Thông báo thật đã tải',
            'createdAt': '2026-10-15T12:00:00Z',
          },
        ];
      default:
        throw StateError('Unexpected request: $endpoint');
    }
    return Response(
      requestOptions: RequestOptions(path: endpoint),
      statusCode: 200,
      data: {'status': 200, 'data': data},
    );
  }
}
