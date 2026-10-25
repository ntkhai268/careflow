import 'dart:async';
import 'package:careflow_patient/widgets/notification_coordinator.dart';
import 'package:careflow_patient/providers/auth_provider.dart';
import 'package:careflow_patient/services/api_service.dart';
import 'package:careflow_patient/services/auth_service.dart';
import 'package:careflow_patient/services/notification_channel.dart';
import 'package:careflow_patient/services/notification_refresh.dart';
import 'package:careflow_patient/services/notification_service.dart';
import 'package:careflow_patient/services/push_notification_service.dart';
import 'package:careflow_patient/features/journey/application/journey_providers.dart';
import 'package:careflow_patient/features/journey/application/journey_controller.dart';
import 'package:careflow_patient/features/journey/data/journey_repository.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

class ScopeAuth extends AuthNotifier {
  ScopeAuth() : super(AuthService(ApiService(), useMock: true));
  void enter(String id) {
    state = AuthState(status: AuthStatus.authenticated, userId: id);
  }

  void leave() {
    state = const AuthState(status: AuthStatus.unauthenticated);
  }
}

class Signals extends NotificationChannel {
  Signals() : super(ApiService());
  final events = StreamController<void>.broadcast();
  String? account;
  bool paused = false;
  @override
  Stream<void> get updates => events.stream;
  @override
  void activate(String? owner) {
    account = owner;
    paused = owner == null;
  }

  @override
  void pause() {
    paused = true;
  }

  @override
  void resume() {
    paused = false;
  }

  @override
  Future<void> dispose() async {
    await events.close();
    await super.dispose();
  }
}

class Inbox extends NotificationService {
  Inbox() : super(ApiService());
  int reads = 0;
  @override
  Future<List<Map<String, dynamic>>> inbox({int limit = 50}) async {
    reads++;
    return [];
  }
}

void main() {
  testWidgets(
    'global hints refresh on another page, coalesce bursts and resume catches up',
    (tester) async {
      FirebaseBootstrap.isReady = false;
      final auth = ScopeAuth()..enter('A'),
          signals = Signals(),
          inbox = Inbox();
      final container = ProviderContainer(
        overrides: [
          authProvider.overrideWith((ref) => auth),
          notificationChannelProvider.overrideWithValue(signals),
          notificationServiceProvider.overrideWithValue(inbox),
          journeyControllerProvider.overrideWith(
            (ref) => JourneyController(
              repository: const UnavailableJourneyRepository(),
              demoMode: false,
            ),
          ),
        ],
      );
      await tester.pumpWidget(
        UncontrolledProviderScope(
          container: container,
          child: const MaterialApp(
            home: NotificationCoordinator(child: Text('Một màn hình khác')),
          ),
        ),
      );
      await tester.pump(const Duration(milliseconds: 450));
      await tester.pump();
      final baseline = container.read(notificationRefreshRevisionProvider);
      expect(baseline, greaterThan(0));
      for (var i = 0; i < 5; i++) {
        signals.events.add(null);
      }
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 450));
      await tester.pump();
      expect(container.read(notificationRefreshRevisionProvider), baseline + 1);
      expect(inbox.reads, greaterThan(0));
      tester.binding.handleAppLifecycleStateChanged(AppLifecycleState.paused);
      await tester.pump();
      expect(signals.paused, isTrue);
      final paused = container.read(notificationRefreshRevisionProvider);
      signals.events.add(null);
      await tester.pump(const Duration(seconds: 31));
      expect(container.read(notificationRefreshRevisionProvider), paused);
      tester.binding.handleAppLifecycleStateChanged(AppLifecycleState.resumed);
      await tester.pump(const Duration(milliseconds: 450));
      await tester.pump();
      expect(container.read(notificationRefreshRevisionProvider), paused + 1);
      auth.leave();
      await tester.pump();
      expect(signals.account, isNull);
      signals.events.add(null);
      await tester.pump(const Duration(seconds: 1));
      expect(container.read(notificationRefreshRevisionProvider), paused + 1);
      await tester.pumpWidget(const SizedBox());
      container.dispose();
      await signals.dispose();
    },
  );
}
