import 'package:careflow_patient/screens/auth/devices_screen.dart';
import 'package:careflow_patient/services/api_service.dart';
import 'package:careflow_patient/services/auth_service.dart';
import 'package:careflow_patient/services/biometric_session_store.dart';
import 'package:careflow_patient/services/device_management_service.dart';
import 'package:careflow_patient/providers/auth_provider.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:go_router/go_router.dart';
import 'package:google_fonts/google_fonts.dart';
import '../services/biometric_session_store_test.dart' show TestVault;

class DeviceFixture extends DeviceManagementService {
  DeviceFixture(super.api);
  final revoked = <String>[];
  final devices = ['local-device', 'remote-device']
      .map(
        (id) => LoginDevice.fromJson({
          'id': id,
          'created_at': '2026-10-19T00:00:00Z',
          'expires_at': '2027-01-01T00:00:00Z',
          'last_used_at': null,
        }),
      )
      .toList();
  @override
  Future<List<LoginDevice>> list() async => devices;
  @override
  Future<void> revoke(String id) async {
    revoked.add(id);
    devices.removeWhere((d) => d.id == id);
  }
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  late ApiService api;
  late DeviceFixture fixture;
  late GoRouter router;
  setUp(() async {
    GoogleFonts.config.allowRuntimeFetching = false;
    FlutterSecureStorage.setMockInitialValues({});
    api = ApiService(sessions: BiometricSessionStore(vault: TestVault()));
    await api.sessions.enable(
      'patient',
      'access',
      'refresh',
      credentialId: 'local-device',
      deviceToken: 'x' * 43,
    );
    fixture = DeviceFixture(api);
    router = GoRouter(
      initialLocation: '/account/devices',
      routes: [
        GoRoute(
          path: '/account/devices',
          builder: (_, state) => const DevicesScreen(),
        ),
        GoRoute(
          path: '/login',
          builder: (_, state) =>
              const Scaffold(body: Text('Sign in with password')),
        ),
      ],
    );
  });
  tearDown(() => router.dispose());
  Future<void> open(WidgetTester tester) async {
    final auth = AuthService(api);
    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          apiServiceProvider.overrideWithValue(api),
          deviceManagementServiceProvider.overrideWithValue(fixture),
          authProvider.overrideWith((ref) => AuthNotifier(auth)),
        ],
        child: MaterialApp.router(routerConfig: router),
      ),
    );
    await tester.pumpAndSettle();
  }

  testWidgets('canceling revocation preserves both enrolled devices', (
    tester,
  ) async {
    await open(tester);
    expect(find.text('Điện thoại này'), findsOneWidget);
    await tester.tap(find.widgetWithText(TextButton, 'Thu hồi').first);
    await tester.pumpAndSettle();
    await tester.tap(find.text('Giữ lại'));
    await tester.pumpAndSettle();
    expect(fixture.revoked, isEmpty);
    expect(await api.sessions.enabled(), isTrue);
  });
  testWidgets(
    'inventory removes expired or remotely revoked local enrollment without logging out a new password session',
    (tester) async {
      fixture.devices.removeWhere((device) => device.id == 'local-device');
      await open(tester);
      expect(await api.sessions.enabled(), isFalse);
      expect(await api.getToken(), 'access');
      expect(find.text('Điện thoại này'), findsNothing);
    },
  );
  testWidgets(
    'remote revoke refreshes inventory without clearing this enrollment',
    (tester) async {
      await open(tester);
      await tester.tap(find.widgetWithText(TextButton, 'Thu hồi').last);
      await tester.pumpAndSettle();
      await tester.tap(find.widgetWithText(FilledButton, 'Thu hồi'));
      await tester.pumpAndSettle();
      expect(fixture.revoked, ['remote-device']);
      expect(await api.sessions.enabled(), isTrue);
      expect(find.text('Điện thoại này'), findsOneWidget);
    },
  );
  testWidgets(
    'revoking current device removes local credentials and returns to password login',
    (tester) async {
      await open(tester);
      await tester.tap(find.widgetWithText(TextButton, 'Thu hồi').first);
      await tester.pumpAndSettle();
      await tester.tap(find.widgetWithText(FilledButton, 'Thu hồi'));
      await tester.pumpAndSettle();
      expect(fixture.revoked, ['local-device']);
      expect(await api.sessions.enabled(), isFalse);
      expect(await api.getToken(), isNull);
      expect(find.text('Sign in with password'), findsOneWidget);
    },
  );
}
