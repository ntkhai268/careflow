import 'dart:async';
import 'package:careflow_patient/config/theme.dart';
import 'package:careflow_patient/providers/auth_provider.dart';
import 'package:careflow_patient/screens/auth/login_screen.dart';
import 'package:careflow_patient/services/api_service.dart';
import 'package:careflow_patient/services/auth_service.dart';
import 'package:careflow_patient/services/biometric_session_store.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:go_router/go_router.dart';
import 'package:google_fonts/google_fonts.dart';
import '../services/biometric_session_store_test.dart' show TestVault;
import '../services/native_login_methods_test.dart' show RecordingApi;

class DelayedVault extends TestVault {
  Completer<BiometricCapability>? pendingCapability;
  String label = 'Face ID';

  @override
  Future<BiometricCapability> capability() =>
      pendingCapability?.future ??
      Future.value(BiometricCapability(available, label, 'Unavailable'));
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  late DelayedVault vault;
  late RecordingApi api;
  late AuthService auth;
  setUp(() {
    GoogleFonts.config.allowRuntimeFetching = false;
    FlutterSecureStorage.setMockInitialValues({});
    vault = DelayedVault();
    api = RecordingApi(BiometricSessionStore(vault: vault));
    auth = AuthService(api);
  });
  Future<void> open(WidgetTester tester) async {
    final router = GoRouter(
      initialLocation: '/login',
      routes: [
        GoRoute(path: '/login', builder: (_, state) => const LoginScreen()),
        GoRoute(
          path: '/',
          builder: (_, state) => const Scaffold(body: Text('Signed in')),
        ),
      ],
    );
    addTearDown(router.dispose);
    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          apiServiceProvider.overrideWithValue(api),
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

  Future<void> enrolledLogout() async {
    await api.sessions.enable(
      'patient',
      'access',
      'refresh',
      credentialId: 'device-id',
      deviceToken: 'd' * 43,
    );
    await auth.logout();
    api.calls.clear();
  }

  final accountField = find.byType(TextFormField).at(0);
  final passwordField = find.byType(TextFormField).at(1);
  final biometricButton = find.byTooltip('Đăng nhập bằng Face ID');
  String accountText(WidgetTester tester) =>
      tester.widget<TextFormField>(accountField).controller!.text;

  testWidgets(
    'remembered account is prefilled and compact Face ID icon logs in without password',
    (tester) async {
      await enrolledLogout();
      await open(tester);
      expect(accountText(tester), 'patient');
      expect(find.text('Đăng nhập bằng Face ID'), findsNothing);
      expect(find.byIcon(Icons.face_outlined), findsOneWidget);
      expect(
        tester.getSize(passwordField).width,
        lessThan(tester.getSize(accountField).width),
      );
      await tester.ensureVisible(biometricButton);
      await tester.tap(biometricButton);
      await tester.pumpAndSettle();
      expect(find.text('Signed in'), findsOneWidget);
      expect(api.calls.first, '/auth/login/biometric');
      expect(await api.sessions.enabled(), isTrue);
    },
  );
  testWidgets(
    'canceling Face ID keeps remembered account and stays signed out',
    (tester) async {
      await enrolledLogout();
      vault.canceled = true;
      await open(tester);
      await tester.ensureVisible(biometricButton);
      await tester.tap(biometricButton);
      await tester.pumpAndSettle();
      expect(find.text('Signed in'), findsNothing);
      expect(api.calls, isEmpty);
      expect(biometricButton, findsOneWidget);
      expect(accountText(tester), 'patient');
      expect(await api.getToken(), isNull);
    },
  );

  testWidgets('editing username expands password and hides remembered login', (
    tester,
  ) async {
    await enrolledLogout();
    await open(tester);
    await tester.enterText(passwordField, 'PreviousPassword123!');
    await tester.enterText(accountField, 'another.patient');
    await tester.pumpAndSettle();
    expect(biometricButton, findsNothing);
    expect(
      tester.getSize(passwordField).width,
      tester.getSize(accountField).width,
    );
    expect(
      tester.widget<TextFormField>(passwordField).controller!.text,
      isEmpty,
    );
    expect(api.calls, isEmpty);
    expect(await api.sessions.enabled(), isTrue);
    await tester.enterText(accountField, ' PATIENT ');
    await tester.pumpAndSettle();
    expect(biometricButton, findsOneWidget);
  });

  testWidgets('switch account clears form and focuses editable username', (
    tester,
  ) async {
    await enrolledLogout();
    await open(tester);
    await tester.enterText(passwordField, 'PreviousPassword123!');
    await tester.ensureVisible(find.text('Dùng tài khoản khác'));
    await tester.tap(find.text('Dùng tài khoản khác'));
    await tester.pumpAndSettle();
    expect(accountText(tester), isEmpty);
    expect(
      tester
          .widget<EditableText>(
            find.descendant(
              of: accountField,
              matching: find.byType(EditableText),
            ),
          )
          .focusNode
          .hasFocus,
      isTrue,
    );
    expect(
      tester.widget<TextFormField>(passwordField).controller!.text,
      isEmpty,
    );
    expect(biometricButton, findsNothing);
    expect(await api.sessions.enabled(), isTrue);
    await tester.enterText(accountField, 'another.patient');
    await tester.enterText(passwordField, 'AnotherPassword123!');
    final login = find.widgetWithText(ElevatedButton, 'Đăng nhập');
    await tester.ensureVisible(login);
    await tester.tap(login);
    await tester.pumpAndSettle();
    expect(api.calls.first, '/auth/login');
    expect(api.body?['usernameOrEmail'], 'another.patient');
    expect(api.calls, isNot(contains('/auth/login/biometric')));
  });

  testWidgets('loading saved account never overwrites text already entered', (
    tester,
  ) async {
    await enrolledLogout();
    final pending = Completer<BiometricCapability>();
    vault.pendingCapability = pending;
    await open(tester);
    await tester.enterText(accountField, 'another.patient');
    pending.complete(const BiometricCapability(true, 'Face ID', ''));
    await tester.pumpAndSettle();
    expect(accountText(tester), 'another.patient');
    expect(biometricButton, findsNothing);
  });

  testWidgets('no enrollment shows normal empty password form', (tester) async {
    await open(tester);
    expect(accountText(tester), isEmpty);
    expect(biometricButton, findsNothing);
    expect(
      tester.getSize(passwordField).width,
      tester.getSize(accountField).width,
    );
  });

  testWidgets('unavailable sensor retains password fallback', (tester) async {
    await enrolledLogout();
    vault.available = false;
    await open(tester);
    expect(accountText(tester), 'patient');
    expect(biometricButton, findsNothing);
    expect(
      tester.getSize(passwordField).width,
      tester.getSize(accountField).width,
    );
  });

  testWidgets('Touch ID uses fingerprint icon and correct accessible label', (
    tester,
  ) async {
    await enrolledLogout();
    vault.label = 'Touch ID';
    await open(tester);
    expect(find.byTooltip('Đăng nhập bằng Touch ID'), findsOneWidget);
    expect(find.byIcon(Icons.face_outlined), findsNothing);
    expect(
      find.descendant(
        of: find.byKey(const ValueKey('biometric-login')),
        matching: find.byIcon(Icons.fingerprint_rounded),
      ),
      findsOneWidget,
    );
  });

  testWidgets('compact form fits small phone with keyboard and enlarged text', (
    tester,
  ) async {
    tester.view.physicalSize = const Size(360, 640);
    tester.view.devicePixelRatio = 1;
    tester.platformDispatcher.textScaleFactorTestValue = 1.5;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    addTearDown(tester.platformDispatcher.clearTextScaleFactorTestValue);
    await enrolledLogout();
    await open(tester);
    await tester.ensureVisible(passwordField);
    await tester.tap(passwordField);
    tester.view.viewInsets = const FakeViewPadding(bottom: 280);
    addTearDown(tester.view.resetViewInsets);
    await tester.pumpAndSettle();
    await tester.ensureVisible(biometricButton);
    expect(tester.getSize(biometricButton), const Size(56, 56));
    expect(tester.takeException(), isNull);
    await tester.tap(biometricButton);
    await tester.pumpAndSettle();
    expect(find.text('Signed in'), findsOneWidget);
  });
}
