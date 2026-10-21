import 'package:careflow_patient/config/theme.dart';
import 'package:careflow_patient/screens/auth/login_methods_screen.dart';
import 'package:careflow_patient/screens/auth/forgot_password_screen.dart';
import 'package:careflow_patient/services/api_service.dart';
import 'package:careflow_patient/services/biometric_session_store.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:go_router/go_router.dart';
import 'package:google_fonts/google_fonts.dart';
import '../services/biometric_session_store_test.dart' show TestVault;
import '../services/native_login_methods_test.dart' show RecordingApi;

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  late TestVault vault;
  late RecordingApi api;
  setUp(() {
    FlutterSecureStorage.setMockInitialValues({});
    GoogleFonts.config.allowRuntimeFetching = false;
    vault = TestVault();
    api = RecordingApi(BiometricSessionStore(vault: vault));
  });
  Future<void> open(WidgetTester tester, {bool recovery = false}) async {
    final router = GoRouter(
      routes: [
        GoRoute(
          path: '/',
          builder: (context, state) => recovery
              ? const ForgotPasswordScreen()
              : const LoginMethodsScreen(),
        ),
        GoRoute(
          path: '/login',
          builder: (context, state) =>
              const Scaffold(body: Text('Password login')),
        ),
      ],
    );
    addTearDown(router.dispose);
    await tester.pumpWidget(
      ProviderScope(
        overrides: [apiServiceProvider.overrideWithValue(api)],
        child: MaterialApp.router(
          theme: AppTheme.lightTheme,
          routerConfig: router,
        ),
      ),
    );
    await tester.pumpAndSettle();
  }

  testWidgets(
    'uses actual phone biometric label and never offers unconfigured login',
    (tester) async {
      await open(tester);
      expect(find.text('Face ID'), findsOneWidget);
      expect(find.textContaining('Chưa bật trên điện thoại'), findsOneWidget);
      await tester.tap(find.text('Face ID'));
      await tester.pumpAndSettle();
      expect(find.text('Cách đăng nhập'), findsOneWidget);
      expect(api.calls, isEmpty);
    },
  );

  testWidgets('retired security questions are never offered on small screens', (
    tester,
  ) async {
    tester.view.physicalSize = const Size(360, 640);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    await open(tester);
    expect(find.text('Câu hỏi bí mật'), findsNothing);
    expect(tester.takeException(), isNull);
    expect(api.calls, isEmpty);
  });

  testWidgets(
    'unsupported devices retain password login without a question fallback',
    (tester) async {
      vault.available = false;
      await open(tester);
      expect(find.text('Chưa thiết lập Face ID'), findsOneWidget);
      expect(find.text('Câu hỏi bí mật'), findsNothing);
    },
  );

  testWidgets('recovery asks for registered email and never offers questions', (
    tester,
  ) async {
    await open(tester, recovery: true);
    expect(find.text('Face ID'), findsNothing);
    expect(find.text('Câu hỏi bí mật'), findsNothing);
    expect(find.text('Email đã đăng ký'), findsOneWidget);
    await tester.tap(find.text('Gửi mã xác nhận'));
    await tester.pumpAndSettle();
    expect(find.text('Vui lòng nhập email hợp lệ.'), findsOneWidget);
    expect(api.calls, isEmpty);
  });
}
