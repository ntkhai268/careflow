import 'package:careflow_patient/services/appointment_service.dart';
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
}
