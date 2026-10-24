import 'package:careflow_patient/config/theme.dart';
import 'package:careflow_patient/screens/auth/forgot_password_screen.dart';
import 'package:careflow_patient/services/api_service.dart';
import 'package:careflow_patient/services/auth_service.dart';
import 'package:careflow_patient/providers/auth_provider.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:go_router/go_router.dart';
import 'package:google_fonts/google_fonts.dart';

class RecoveryAuth extends AuthService {
  RecoveryAuth() : super(ApiService());
  final requests = <String>[];
  final verifications = <List<String>>[];
  final confirmations = <List<String>>[];
  bool rejectCode = false;
  int resendSeconds = 60;
  @override
  Future<Map<String, dynamic>> requestEmailRecovery(String email) async {
    requests.add(email);
    return {
      'requestId': 'id-${requests.length}',
      'resendAfterSeconds': resendSeconds,
      'expiresInSeconds': 300,
      'message': 'Nếu email đã đăng ký, mã sẽ được gửi.',
    };
  }

  @override
  Future<Map<String, dynamic>> verifyEmailRecovery(
    String id,
    String code,
  ) async {
    verifications.add([id, code]);
    if (rejectCode) {
      throw Exception('Mã không hợp lệ, đã hết hạn hoặc đã sử dụng.');
    }
    return {'requestId': id, 'resetToken': 'r' * 43, 'expiresInSeconds': 300};
  }

  @override
  Future<void> confirmEmailRecovery(
    String id,
    String resetToken,
    String password,
  ) async {
    confirmations.add([id, resetToken, password]);
  }
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  late RecoveryAuth auth;
  setUp(() {
    GoogleFonts.config.allowRuntimeFetching = false;
    auth = RecoveryAuth();
  });
  Future<void> open(WidgetTester tester) async {
    tester.view.physicalSize = const Size(360, 740);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    final router = GoRouter(
      routes: [
        GoRoute(path: '/', builder: (_, state) => const ForgotPasswordScreen()),
        GoRoute(
          path: '/login',
          builder: (_, state) => const Scaffold(body: Text('Password login')),
        ),
      ],
    );
    addTearDown(router.dispose);
    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          authServiceProvider.overrideWithValue(auth),
          authProvider.overrideWith((ref) => AuthNotifier(auth)),
        ],
        child: MaterialApp.router(
          theme: AppTheme.lightTheme,
          routerConfig: router,
        ),
      ),
    );
    await tester.pumpAndSettle();
  }

  Future<void> request(WidgetTester tester) async {
    await tester.enterText(
      find.byKey(const ValueKey('recovery-email')),
      ' Patient@Example.com ',
    );
    await tester.tap(find.text('Gửi mã xác nhận'));
    await tester.pumpAndSettle();
  }

  Future<void> verifyCode(WidgetTester tester) async {
    await tester.enterText(
      find.byKey(const ValueKey('recovery-code')),
      '012345',
    );
    await tester.ensureVisible(find.text('Xác thực mã'));
    await tester.tap(find.text('Xác thực mã'));
    await tester.pumpAndSettle();
  }

  Future<void> password(WidgetTester tester, {bool mismatch = false}) async {
    await tester.enterText(
      find.descendant(
        of: find.byKey(const ValueKey('recovery-password')),
        matching: find.byType(TextFormField),
      ),
      'NewPassword123@',
    );
    await tester.enterText(
      find.descendant(
        of: find.byKey(const ValueKey('recovery-confirmation')),
        matching: find.byType(TextFormField),
      ),
      mismatch ? 'OtherPassword123@' : 'NewPassword123@',
    );
    await tester.ensureVisible(find.text('Đặt lại mật khẩu'));
    await tester.tap(find.text('Đặt lại mật khẩu'));
    await tester.pumpAndSettle();
  }

  testWidgets(
    'password fields appear only after successful server code verification',
    (tester) async {
      await open(tester);
      await request(tester);
      expect(auth.requests, ['patient@example.com']);
      expect(find.text('Câu hỏi bí mật'), findsNothing);
      expect(find.textContaining('60 giây'), findsOneWidget);
      expect(find.byKey(const ValueKey('recovery-password')), findsNothing);
      expect(find.text('Đặt lại mật khẩu'), findsNothing);
      await verifyCode(tester);
      expect(auth.verifications, [
        ['id-1', '012345'],
      ]);
      expect(auth.confirmations, isEmpty);
      expect(find.text('Tạo mật khẩu mới'), findsOneWidget);
      expect(find.byKey(const ValueKey('recovery-code')), findsNothing);
      await password(tester);
      expect(auth.confirmations, [
        ['id-1', 'r' * 43, 'NewPassword123@'],
      ]);
      expect(find.text('Đã đặt lại mật khẩu'), findsOneWidget);
      await tester.ensureVisible(find.text('Đăng nhập lại'));
      await tester.tap(find.text('Đăng nhập lại'));
      await tester.pumpAndSettle();
      expect(find.text('Password login'), findsOneWidget);
      expect(tester.takeException(), isNull);
    },
  );
  testWidgets('resend clears old code and verifies the new request id', (
    tester,
  ) async {
    auth.resendSeconds = 0;
    await open(tester);
    await request(tester);
    await tester.enterText(
      find.byKey(const ValueKey('recovery-code')),
      '111111',
    );
    await tester.ensureVisible(find.text('Gửi lại mã'));
    await tester.tap(find.text('Gửi lại mã'));
    await tester.pumpAndSettle();
    expect(auth.requests.length, 2);
    expect(
      tester
          .widget<TextFormField>(find.byKey(const ValueKey('recovery-code')))
          .controller!
          .text,
      isEmpty,
    );
    await verifyCode(tester);
    await password(tester);
    expect(auth.confirmations.single.first, 'id-2');
  });
  testWidgets(
    'wrong code stays on verification screen with no password controls',
    (tester) async {
      auth.rejectCode = true;
      await open(tester);
      await request(tester);
      await verifyCode(tester);
      expect(find.textContaining('Mã không hợp lệ'), findsOneWidget);
      expect(find.byKey(const ValueKey('recovery-password')), findsNothing);
      expect(auth.confirmations, isEmpty);
      await tester.pumpWidget(const SizedBox());
    },
  );
  testWidgets('password mismatch after verification never submits reset', (
    tester,
  ) async {
    await open(tester);
    await request(tester);
    await verifyCode(tester);
    await password(tester, mismatch: true);
    expect(find.text('Xác nhận mật khẩu chưa khớp.'), findsOneWidget);
    expect(auth.confirmations, isEmpty);
    await tester.pumpWidget(const SizedBox());
  });
}
