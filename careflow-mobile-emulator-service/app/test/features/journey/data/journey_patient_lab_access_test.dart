import 'package:careflow_patient/features/journey/data/backend_journey_repository.dart';
import 'package:careflow_patient/features/journey/domain/journey_models.dart';
import 'package:careflow_patient/models/appointment.dart';
import 'package:careflow_patient/services/appointment_service.dart';
import 'package:careflow_patient/services/consultation_service.dart';
import 'package:careflow_patient/services/lab_service.dart';
import 'package:careflow_patient/services/notification_service.dart';
import 'package:careflow_patient/services/prescription_service.dart';
import 'package:careflow_patient/services/queue_service.dart';
import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';

import 'journey_notification_outage_test.dart'
    show JourneyApi, appointmentJson, failure;

void main() {
  late PatientLabApi api;
  late BackendJourneyRepository repository;

  setUp(() {
    api = PatientLabApi();
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

  test(
    'ongoing consultation with no published labs restores the journey',
    () async {
      final journey = await load();
      expect(journey.status, JourneyStatus.inConsultation);
      expect(journey.laboratoryOrders, isEmpty);
      expect(api.requests, contains('/labs/orders/patient/patient-1'));
      expect(
        api.requests,
        isNot(contains('/labs/orders/consultation/consultation-1')),
      );
    },
  );

  test(
    'patient lab history is restricted to the current patient and consultation',
    () async {
      api.results = [
        publishedOrder('current', 'patient-1', 'consultation-1'),
        publishedOrder('other-visit', 'patient-1', 'consultation-2'),
        publishedOrder('other-patient', 'patient-2', 'consultation-1'),
        {'id': 'missing-scope', 'status': 'RESULT_AVAILABLE'},
      ];
      final journey = await load();
      expect(journey.laboratoryOrders.map((order) => order.id), [
        'current-item',
      ]);
      expect(journey.laboratoryOrders.single.result?.value, '5.2');
    },
  );

  for (final status in [401, 403, 503]) {
    test('patient Lab HTTP $status is propagated instead of hidden', () async {
      final error = failure('/labs/orders/patient/patient-1', status);
      api.labFailure = error;
      await expectLater(load(), throwsA(same(error)));
    });
  }

  test(
    'booking without a consultation does not query clinical lab resources',
    () async {
      api.hasConsultation = false;
      final journey = await load();
      expect(journey.status, JourneyStatus.booked);
      expect(api.requests.where((path) => path.startsWith('/labs/')), isEmpty);
    },
  );
}

// Contract fixtures are confined to this test. Production still calls real APIs.
class PatientLabApi extends JourneyApi {
  final requests = <String>[];
  List<Map<String, dynamic>> results = [];
  DioException? labFailure;
  bool hasConsultation = true;

  @override
  Future<Response> get(
    String endpoint, {
    Map<String, dynamic>? queryParams,
    Options? options,
  }) async {
    requests.add(endpoint);
    Object data;
    switch (endpoint) {
      case '/consultations/appointment/appointment-1':
        data = hasConsultation
            ? [
                {
                  'id': 'consultation-1',
                  'patientId': 'patient-1',
                  'status': 'IN_PROGRESS',
                },
              ]
            : [];
      case '/labs/orders/consultation/consultation-1':
        throw failure(endpoint, 403); // This endpoint is assigned-doctor only.
      case '/labs/orders/patient/patient-1':
        if (labFailure != null) throw labFailure!;
        data = results;
      case '/prescriptions/consultation/consultation-1':
        data = [];
      default:
        return super.get(endpoint, queryParams: queryParams, options: options);
    }
    return Response(
      requestOptions: RequestOptions(path: endpoint),
      statusCode: 200,
      data: {'status': 200, 'data': data},
    );
  }
}

Map<String, dynamic> publishedOrder(
  String id,
  String patient,
  String consultation,
) => {
  'id': id,
  'patientId': patient,
  'consultationId': consultation,
  'status': 'RESULT_AVAILABLE',
  'items': [
    {
      'id': '$id-item',
      'serviceName': 'Glucose',
      'status': 'COMPLETED',
      'resultValue': '5.2',
    },
  ],
};
