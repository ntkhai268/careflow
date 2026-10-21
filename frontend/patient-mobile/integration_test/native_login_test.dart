import 'package:careflow_patient/config/api_config.dart';
import 'package:careflow_patient/services/api_service.dart';
import 'package:careflow_patient/services/auth_service.dart';
import 'package:careflow_patient/services/native_transport.dart';
import 'package:careflow_patient/services/patient_service.dart';
import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:integration_test/integration_test.dart';

void main() {
  IntegrationTestWidgetsFlutterBinding.ensureInitialized();
  const username = String.fromEnvironment('TEST_USERNAME');
  const password = String.fromEnvironment('TEST_PASSWORD');

  testWidgets(
    'native HTTPS login, Keychain, proof binding, refresh and profiles',
    (tester) async {
      await tester.pumpWidget(
        const MaterialApp(
          home: Scaffold(
            body: Center(child: Text('Đang kiểm tra kết nối CareFlow…')),
          ),
        ),
      );
      final healthClient = Dio(
        BaseOptions(connectTimeout: const Duration(seconds: 15)),
      );
      await configureNativeTransport(healthClient, Dio());
      final health = await healthClient.get(
        Uri.parse(ApiConfig.baseUrl).resolve('/health').toString(),
      );
      expect(health.statusCode, 200);
      healthClient.close();
      final api = ApiService();
      final auth = AuthService(api);
      await api.clearTokens();
      try {
        await auth.login(username, password);
        expect((await api.getToken())?.isNotEmpty, isTrue);
        expect((await api.getRefreshToken())?.isNotEmpty, isTrue);
        final current = await auth.fetchCurrentUser();
        expect(current?.username, username);
        expect(current?.role, 'PATIENT');

        // A rejected access token must rotate using a fresh proof of the same key.
        await api.saveToken('expired-native-audit-token');
        final restored = await auth.fetchCurrentUser();
        expect(restored?.id, current!.id);
        final profiles = await PatientService(
          api,
        ).getPatientsByUserId(current.id);
        expect(profiles.isNotEmpty, isTrue);
      } finally {
        await auth.logout();
      }
      expect(await api.hasToken(), isFalse);
    },
    skip: username.isEmpty || password.isEmpty,
  );
}
