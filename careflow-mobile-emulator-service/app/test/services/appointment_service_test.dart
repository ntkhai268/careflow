import 'package:careflow_patient/services/appointment_service.dart';
import 'package:careflow_patient/services/api_service.dart';
import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  test('maps duplicate-slot 409 to the Vietnamese server message', () {
    final request = RequestOptions(path: '/appointments');
    final error = DioException.badResponse(
      statusCode: 409,
      requestOptions: request,
      response: Response<dynamic>(
        requestOptions: request,
        statusCode: 409,
        data: {
          'status': 409,
          'message': 'Bệnh nhân đã có lịch khám vào ca này',
        },
      ),
    );

    final message = appointmentBookingErrorMessage(error);

    expect(message, 'Bệnh nhân đã có lịch khám vào ca này');
    expect(message, isNot(contains('DioException')));
  });

  test('uses a concise fallback when a 409 body has no message', () {
    final request = RequestOptions(path: '/appointments');
    final error = DioException.badResponse(
      statusCode: 409,
      requestOptions: request,
      response: Response<dynamic>(requestOptions: request, statusCode: 409),
    );

    expect(
      appointmentBookingErrorMessage(error),
      'Bạn đã có lịch khám vào khung giờ này. Vui lòng chọn ca khác.',
    );
  });

  test('sends the mobile idempotency key when creating an appointment', () async {
    final api = RecordingApiService();
    final service = AppointmentService(api);

    await service.createAppointment(
      <String, dynamic>{},
      idempotencyKey: 'mobile-request-123',
    );

    expect(api.lastPostEndpoint, '/appointments');
    expect(api.lastPostOptions?.headers?['Idempotency-Key'], 'mobile-request-123');
  });

  test('requests time slots for the selected department and date', () async {
    final api = RecordingApiService();
    final service = AppointmentService(api);

    await service.getTimeSlots(
      department: 'NOI_TONG_QUAT',
      date: DateTime(2026, 8, 18),
    );

    expect(api.lastQueryParams, {
      'department': 'NOI_TONG_QUAT',
      'date': '2026-08-18',
    });
  });

  test('requests slot availability for the selected department and date', () async {
    final api = RecordingApiService();
    final service = AppointmentService(api);

    final slots = await service.getTimeSlotAvailability(
      department: 'NOI_TONG_QUAT',
      date: DateTime(2026, 8, 18),
    );

    expect(api.lastGetEndpoint, '/appointments/time-slots/availability');
    expect(api.lastQueryParams, {
      'department': 'NOI_TONG_QUAT',
      'date': '2026-08-18',
    });
    expect(slots.single.remaining, 4);
    expect(slots.single.available, isTrue);
  });
}

class RecordingApiService extends ApiService {
  String? lastPostEndpoint;
  String? lastGetEndpoint;
  Options? lastPostOptions;
  Map<String, dynamic>? lastQueryParams;

  @override
  Future<Response> post(
    String endpoint, {
    dynamic data,
    Options? options,
  }) async {
    lastPostEndpoint = endpoint;
    lastPostOptions = options;
    return Response<dynamic>(
      requestOptions: RequestOptions(path: endpoint),
      statusCode: 201,
      data: <String, dynamic>{
        'data': <String, dynamic>{
          'id': 'appointment-1',
          'patientId': 'patient-1',
          'department': 'NOI_TONG_QUAT',
          'departmentDisplayName': 'General Medicine',
          'appointmentDate': '2026-08-18',
          'timeSlot': '10:00-10:30',
          'status': 'CONFIRMED',
          'statusDisplayName': 'Confirmed',
        },
      },
    );
  }

  @override
  Future<Response> get(
    String endpoint, {
    Map<String, dynamic>? queryParams,
    Options? options,
  }) async {
    lastGetEndpoint = endpoint;
    lastQueryParams = queryParams;
    if (endpoint.endsWith('/availability')) {
      return Response<dynamic>(
        requestOptions: RequestOptions(path: endpoint),
        statusCode: 200,
        data: <String, dynamic>{
          'data': <Map<String, dynamic>>[
            <String, dynamic>{
              'timeSlot': '10:00-10:30',
              'bookedCount': 1,
              'capacity': 5,
              'remaining': 4,
              'available': true,
            },
          ],
        },
      );
    }
    return Response<dynamic>(
      requestOptions: RequestOptions(path: endpoint),
      statusCode: 200,
      data: <String, dynamic>{'data': <String>['10:00-10:30']},
    );
  }
}
