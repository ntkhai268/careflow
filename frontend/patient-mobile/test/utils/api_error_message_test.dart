import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:careflow_patient/utils/api_error_message.dart';

void main() {
  test('keeps a clear business message returned by the API', () {
    final error = DioException(
      requestOptions: RequestOptions(path: '/patients'),
      response: Response(
        requestOptions: RequestOptions(path: '/patients'),
        statusCode: 409,
        data: <String, dynamic>{
          'message': 'Tài khoản này đã có hồ sơ dùng số CCCD đã nhập',
        },
      ),
    );

    expect(
      ApiErrorMessage.from(error),
      'Tài khoản này đã có hồ sơ dùng số CCCD đã nhập',
    );
  });

  test('maps a missing patient profile to an actionable message', () {
    final error = DioException(
      requestOptions: RequestOptions(path: '/patients/user/user-id'),
      response: Response(
        requestOptions: RequestOptions(path: '/patients/user/user-id'),
        statusCode: 404,
        data: <String, dynamic>{
          'message': 'Patient not found with userId user-id',
        },
      ),
    );

    expect(
      ApiErrorMessage.from(error),
      'Bạn chưa có hồ sơ bệnh nhân. Vui lòng tạo hồ sơ mới để tiếp tục.',
    );
  });

  test('maps connection failures without exposing Dio internals', () {
    final error = DioException(
      requestOptions: RequestOptions(path: '/patients'),
      type: DioExceptionType.connectionError,
    );

    final message = ApiErrorMessage.from(error);

    expect(message, contains('Không thể kết nối đến máy chủ'));
    expect(message, isNot(contains('DioException')));
  });
}
