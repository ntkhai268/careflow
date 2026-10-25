import 'package:careflow_patient/config/theme.dart';
import 'package:careflow_patient/providers/auth_provider.dart';
import 'package:careflow_patient/screens/auth/register_screen.dart';
import 'package:careflow_patient/services/api_service.dart';
import 'package:careflow_patient/services/auth_service.dart';
import 'package:careflow_patient/services/biometric_session_store.dart';
import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:go_router/go_router.dart';
import 'package:google_fonts/google_fonts.dart';
import '../services/native_login_methods_test.dart' show RecordingApi;
import '../services/biometric_session_store_test.dart' show TestVault;

class RegistrationApi extends RecordingApi {
  RegistrationApi() : super(BiometricSessionStore(vault: TestVault()));
  bool rejectCode = false;
  @override
  Future<Response> post(String path, {dynamic data, Options? options}) async {
    if (path.startsWith('/auth/registration/')) {
      calls.add(path);
      body = Map<String, dynamic>.from(data);
      expect(options?.extra?['anonymous'], isTrue);
      if (path.endsWith('/verify') && rejectCode) {
        throw DioException(
          requestOptions: RequestOptions(path: path),
          response: Response(
            requestOptions: RequestOptions(),
            statusCode: 401,
            data: {'message': 'Mã không hợp lệ'},
          ),
        );
      }
      return Response(
        requestOptions: RequestOptions(path: path),
        data: {
          'data': {'requestId': 'request-one', 'verified': true},
        },
      );
    }
    return super.post(path, data: data, options: options);
  }
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  late RegistrationApi api;
  setUp(() {
    GoogleFonts.config.allowRuntimeFetching = false;
    FlutterSecureStorage.setMockInitialValues({});
    api = RegistrationApi();
  });
  Future<void> open(WidgetTester tester) async {
    final auth = AuthService(api);
    final router = GoRouter(
      initialLocation: '/register',
      routes: [
        GoRoute(path: '/register', builder: (_, _) => const RegisterScreen()),
        GoRoute(
          path: '/',
          builder: (_, _) =>
              const Scaffold(body: Text('Verified account signed in')),
        ),
      ],
    );
    addTearDown(router.dispose);
    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          apiServiceProvider.overrideWithValue(api),
          authProvider.overrideWith((ref) => AuthNotifier(auth)),
        ],
        child: MaterialApp.router(
          theme: AppTheme.lightTheme,
          routerConfig: router,
        ),
      ),
    );
    await tester.pumpAndSettle();
    final fields = find.byType(TextFormField);
    await tester.enterText(fields.at(0), 'patient.registration');
    await tester.enterText(fields.at(1), 'Patient@example.technology');
    await tester.enterText(fields.at(2), 'TestPassword123!');
    await tester.enterText(fields.at(3), 'TestPassword123!');
    final send = find.text('Gửi mã xác minh email');
    await tester.ensureVisible(send);
    await tester.tap(send);
    await tester.pumpAndSettle();
  }

  testWidgets(
    'registration requests code first and signs in only after explicit verification',
    (tester) async {
      await open(tester);
      expect(api.calls, ['/auth/registration/email/request']);
      expect(api.body?['email'], 'patient@example.technology');
      expect(find.text('Verified account signed in'), findsNothing);
      expect(find.byType(TextFormField), findsOneWidget);
      final resend = find.widgetWithText(TextButton, 'Gửi lại mã sau 60s');
      expect(tester.widget<TextButton>(resend).onPressed, isNull);
      await tester.enterText(find.byType(TextFormField), '123456');
      final verify = find.text('Xác minh và tạo tài khoản');
      await tester.ensureVisible(verify);
      await tester.tap(verify);
      await tester.pumpAndSettle();
      expect(api.calls, [
        '/auth/registration/email/request',
        '/auth/registration/email/verify',
        '/auth/login',
      ]);
      expect(find.text('Verified account signed in'), findsOneWidget);
    },
  );
  testWidgets('invalid email code stays at verification and never logs in', (
    tester,
  ) async {
    await open(tester);
    api.rejectCode = true;
    await tester.enterText(find.byType(TextFormField), '000000');
    final verify = find.text('Xác minh và tạo tài khoản');
    await tester.ensureVisible(verify);
    await tester.tap(verify);
    await tester.pumpAndSettle();
    expect(api.calls, isNot(contains('/auth/login')));
    expect(find.text('Mã không hợp lệ'), findsOneWidget);
    await tester.pumpWidget(const SizedBox());
  });
}
