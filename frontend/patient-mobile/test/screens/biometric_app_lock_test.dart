import 'package:careflow_patient/services/api_service.dart';
import 'package:careflow_patient/services/auth_service.dart';
import 'package:careflow_patient/providers/auth_provider.dart';
import 'package:careflow_patient/config/router.dart';
import 'package:careflow_patient/services/biometric_session_store.dart';
import 'package:careflow_patient/widgets/biometric_app_lock.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:flutter_test/flutter_test.dart';
import '../services/biometric_session_store_test.dart' show TestVault;
import '../services/native_login_methods_test.dart' show RecordingApi;

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  late BiometricSessionStore sessions;
  Future<void> open(WidgetTester tester) async {
    FlutterSecureStorage.setMockInitialValues({});
    sessions = BiometricSessionStore(vault: TestVault());
    await sessions.enable(
      'patient',
      'access',
      'refresh',
      credentialId: 'device-id',
      deviceToken: 'd' * 43,
    );
    final api = RecordingApi(sessions);
    final auth = AuthService(api);
    final notifier = AuthNotifier(auth);
    await notifier.checkAuthStatus();
    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          apiServiceProvider.overrideWithValue(api),
          authProvider.overrideWith((ref) => notifier),
        ],
        child: const MaterialApp(
          home: BiometricAppLock(child: Text('Protected content')),
        ),
      ),
    );
    await tester.pumpAndSettle();
  }

  testWidgets(
    'inactive biometric system dialog hides UI without locking session',
    (tester) async {
      await open(tester);
      tester.binding.handleAppLifecycleStateChanged(AppLifecycleState.inactive);
      await tester.pumpAndSettle();
      expect(find.byIcon(Icons.lock_outline_rounded), findsOneWidget);
      tester.binding.handleAppLifecycleStateChanged(AppLifecycleState.resumed);
      await tester.pumpAndSettle();
      expect(await sessions.accessToken(), 'access');
      expect(find.byIcon(Icons.lock_outline_rounded), findsNothing);
      await tester.pumpWidget(const SizedBox());
    },
  );
  testWidgets('short background visit preserves session', (tester) async {
    await open(tester);
    tester.binding.handleAppLifecycleStateChanged(AppLifecycleState.paused);
    await tester.pump(const Duration(seconds: 30));
    tester.binding.handleAppLifecycleStateChanged(AppLifecycleState.resumed);
    await tester.pumpAndSettle();
    expect(await sessions.accessToken(), 'access');
    await tester.pumpWidget(const SizedBox());
  });
  testWidgets('patient idle lock does not require biometric enrollment', (
    tester,
  ) async {
    await open(tester);
    await sessions.disable();
    await tester.pump(const Duration(minutes: 30));
    await tester.pumpAndSettle();
    expect(await sessions.accessToken(), isNull);
  });
  testWidgets(
    'returning from background clears usable session and retains vault',
    (tester) async {
      await open(tester);
      tester.binding.handleAppLifecycleStateChanged(AppLifecycleState.paused);
      await tester.pump(const Duration(seconds: 61));
      tester.binding.handleAppLifecycleStateChanged(AppLifecycleState.resumed);
      await tester.pumpAndSettle();
      expect(await sessions.accessToken(), isNull);
      expect(await sessions.refreshToken(), isNull);
      expect(await sessions.enabled(), isTrue);
      expect((await sessions.unlockCredential()).token, 'd' * 43);
    },
  );
  testWidgets(
    'returning from Mail keeps recovery route while locking prior session',
    (tester) async {
      await open(tester);
      appRouter.go('/login/recovery');
      await tester.pumpAndSettle();
      tester.binding.handleAppLifecycleStateChanged(AppLifecycleState.paused);
      await tester.pump(const Duration(seconds: 61));
      tester.binding.handleAppLifecycleStateChanged(AppLifecycleState.resumed);
      await tester.pumpAndSettle();
      expect(
        appRouter.routeInformationProvider.value.uri.path,
        '/login/recovery',
      );
      expect(await sessions.accessToken(), isNull);
      expect(await sessions.enabled(), isTrue);
      appRouter.go('/splash');
      await tester.pumpWidget(const SizedBox());
    },
  );
  testWidgets(
    'returning while signed out keeps password login and registered Face ID',
    (tester) async {
      await open(tester);
      await sessions.clearSession();
      appRouter.go('/login');
      await tester.pumpAndSettle();
      tester.binding.handleAppLifecycleStateChanged(AppLifecycleState.paused);
      await tester.pumpAndSettle();
      tester.binding.handleAppLifecycleStateChanged(AppLifecycleState.resumed);
      await tester.pumpAndSettle();
      expect(appRouter.routeInformationProvider.value.uri.path, '/login');
      expect(await sessions.enabled(), isTrue);
      expect(await sessions.accessToken(), isNull);
      appRouter.go('/splash');
      await tester.pumpWidget(const SizedBox());
    },
  );
}
