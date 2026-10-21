import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'api_service.dart';

class EmailRegistrationService {
  EmailRegistrationService(this.api);
  final ApiService api;
  Future<String> request(String username, String email, String password) async {
    final result = await api.post(
      '/auth/registration/email/request',
      data: {
        'username': username.trim(),
        'email': email.trim().toLowerCase(),
        'password': password,
      },
      options: Options(extra: {'anonymous': true}),
    );
    return result.data['data']['requestId'] as String;
  }

  Future<void> verify(String requestId, String code) async {
    await api.post(
      '/auth/registration/email/verify',
      data: {'requestId': requestId, 'code': code},
      options: Options(extra: {'anonymous': true}),
    );
  }
}

final emailRegistrationServiceProvider = Provider(
  (ref) => EmailRegistrationService(ref.read(apiServiceProvider)),
);
