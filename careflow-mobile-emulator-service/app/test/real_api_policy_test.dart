import 'package:careflow_patient/services/api_service.dart';
import 'package:careflow_patient/services/auth_service.dart';
import 'package:dio/dio.dart';
import 'package:careflow_patient/features/journey/application/journey_providers.dart';
import 'package:careflow_patient/features/journey/data/backend_journey_repository.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  test(
    'auth still sends login to Identity when mock build flags are supplied',
    () async {
      final api = RecordingApi();
      final response = await AuthService(api).login('patient', 'Password123@');
      expect(api.loginCalls, 1);
      expect(response.token, 'from-identity');
    },
  );
  test(
    'runtime uses backend journey and real queue without demo configuration',
    () {
      final container = ProviderContainer();
      addTearDown(container.dispose);
      expect(container.read(demoModeProvider), isFalse);
      expect(container.read(realQueueEnabledProvider), isTrue);
      expect(
        container.read(journeyRepositoryProvider),
        isA<BackendJourneyRepository>(),
      );
    },
  );
}

class RecordingApi extends ApiService {
  int loginCalls = 0;
  @override
  Future<Response> post(
    String endpoint, {
    dynamic data,
    Options? options,
  }) async {
    loginCalls++;
    return Response(
      requestOptions: RequestOptions(path: endpoint),
      data: {
        'data': {
          'accessToken': 'from-identity',
          'refreshToken': 'from-identity-refresh',
          'user': {
            'id': 'user-1',
            'email': 'patient@example.test',
            'username': 'patient',
          },
        },
      },
    );
  }

  @override
  Future<void> saveToken(String token) async {}
  @override
  Future<void> saveRefreshToken(String token) async {}
}
