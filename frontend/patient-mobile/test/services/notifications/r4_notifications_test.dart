import 'dart:async';
import 'package:careflow_patient/services/api_service.dart';
import 'package:careflow_patient/services/notification_channel.dart';
import 'package:careflow_patient/services/notification_socket_types.dart';
import 'package:careflow_patient/services/notification_resource_service.dart';
import 'package:careflow_patient/services/push_notification_service.dart';
import 'package:careflow_patient/services/push_platform.dart';
import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:package_info_plus/package_info_plus.dart';
import 'package:shared_preferences/shared_preferences.dart';

const nid = '00000001-0000-0000-0000-000000000001';
const rid = '00000002-0000-0000-0000-000000000002';
const pid = '00000003-0000-0000-0000-000000000003';
Future<void> settle() async {
  for (var i = 0; i < 10; i++) {
    await Future<void>.delayed(Duration.zero);
  }
}

class FakeApi extends ApiService {
  String owner = 'A', binding = 'binding-A';
  final paths = <String>[];
  final registrations = <Map<String, dynamic>>[];
  final rows = <String, Map<String, dynamic>>{};
  Completer<void>? putGate;
  @override
  Future<({String userId, Map<String, String> headers})>
  notificationSocketCredentials() async => (
    userId: owner,
    headers: {
      'Authorization': 'Bearer synthetic-$owner',
      'DPoP': 'proof-$owner',
    },
  );
  @override
  Future<Response> get(
    String endpoint, {
    Map<String, dynamic>? queryParams,
    Options? options,
  }) async {
    paths.add(endpoint);
    return Response(
      requestOptions: RequestOptions(path: endpoint),
      data: {
        'data': endpoint == '/auth/me' ? {'id': owner} : rows[endpoint],
      },
    );
  }

  @override
  Future<Response> put(String endpoint, {dynamic data}) async {
    registrations.add(Map<String, dynamic>.from(data as Map));
    final snapshot = binding;
    await putGate?.future;
    return Response(
      requestOptions: RequestOptions(path: endpoint),
      data: {
        'data': {'bindingId': snapshot},
      },
    );
  }

  @override
  Future<Response> delete(String endpoint) async => Response(
    requestOptions: RequestOptions(path: endpoint),
    data: {},
  );
}

class FakeSocket implements NotificationSocket {
  final controller = StreamController<dynamic>.broadcast();
  final sent = <String>[];
  bool closed = false;
  @override
  Stream<dynamic> get messages => controller.stream;
  @override
  void send(String value) => sent.add(value);
  @override
  Future<void> close() async {
    closed = true;
  }

  void emit(String value) => controller.add(value);
}

class FakePush implements PushPlatform {
  final tokenEvents = StreamController<String>.broadcast(),
      messages = StreamController<Map<String, dynamic>>.broadcast(),
      opens = StreamController<Map<String, dynamic>>.broadcast();
  bool permission = true;
  String? nextToken = 'synthetic-token';
  int shown = 0, requests = 0, initializes = 0;
  Map<String, dynamic>? initialMessage;
  Completer<void>? initGate, initialGate;
  @override
  Future<void> initialize() async {
    initializes++;
    await initGate?.future;
  }

  @override
  Future<bool> allowed(bool request) async {
    if (request) requests++;
    return permission;
  }

  @override
  Future<String?> token() async => nextToken;
  @override
  Stream<String> get tokens => tokenEvents.stream;
  @override
  Stream<Map<String, dynamic>> get foreground => messages.stream;
  @override
  Stream<Map<String, dynamic>> get opened => opens.stream;
  @override
  Future<Map<String, dynamic>?> initial() async {
    await initialGate?.future;
    return initialMessage;
  }

  @override
  Future<void> deleteToken() async {}
  @override
  Future<void> clearVisible() async {}
  @override
  Future<void> showGeneric(Map<String, dynamic> data) async {
    shown++;
  }

  Future<void> close() async {
    await tokenEvents.close();
    await messages.close();
    await opens.close();
  }
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  group('R4 signed realtime channel', () {
    test(
      'fragmented frames subscribe only to own inbox; payload is an invalidation hint',
      () async {
        final api = FakeApi(), socket = FakeSocket();
        final channel = NotificationChannel(api, connect: (_) async => socket);
        var updates = 0;
        final sub = channel.updates.listen((_) => updates++);
        channel.activate('A');
        await settle();
        expect(
          socket.sent.single,
          contains('Authorization:Bearer synthetic-A'),
        );
        expect(socket.sent.single, contains('DPoP:proof-A'));
        socket.emit('CONNECTED\nversion:1.2\n\n');
        socket.emit('\u0000');
        await settle();
        expect(channel.state, NotificationConnection.online);
        expect(
          socket.sent.last,
          contains('destination:/user/queue/notifications'),
        );
        socket.emit(
          'MESSAGE\n\n{"id":"e1","status":"FORGED"}\u0000MESSAGE\n\n{"id":"e1"}\u0000',
        );
        await settle();
        expect(updates, 3);
        await channel.dispose();
        await sub.cancel();
        await socket.controller.close();
      },
    );
    test('late connection for A is discarded after switching to B', () async {
      final api = FakeApi(),
          gate = Completer<NotificationSocket>(),
          old = FakeSocket(),
          current = FakeSocket();
      int count = 0;
      final channel = NotificationChannel(
        api,
        connect: (_) => ++count == 1 ? gate.future : Future.value(current),
      );
      channel.activate('A');
      await settle();
      api.owner = 'B';
      channel.activate('B');
      await settle();
      gate.complete(old);
      await settle();
      expect(old.closed, isTrue);
      expect(old.sent, isEmpty);
      expect(current.sent.single, contains('synthetic-B'));
      await channel.dispose();
      await old.controller.close();
      await current.controller.close();
    });
    test(
      'oversized frame retries with fresh credentials and pause stops retry',
      () async {
        final api = FakeApi(), first = FakeSocket(), second = FakeSocket();
        int count = 0;
        final channel = NotificationChannel(
          api,
          retryBase: const Duration(milliseconds: 5),
          connect: (_) async => ++count == 1 ? first : second,
        );
        channel.activate('A');
        await settle();
        first.emit('x' * 131073);
        await Future<void>.delayed(const Duration(milliseconds: 30));
        expect(first.closed, isTrue);
        expect(count, 2);
        channel.pause();
        expect(channel.state, NotificationConnection.paused);
        channel.resume();
        await settle();
        expect(count, 3);
        await channel.dispose();
        await first.controller.close();
        await second.controller.close();
      },
    );
  });
  group('R4 push binding', () {
    late FakeApi api;
    late FakePush platform;
    late PushNotificationService service;
    setUp(() {
      SharedPreferences.setMockInitialValues({});
      PackageInfo.setMockInitialValues(
        appName: 'CareFlow',
        packageName: 'test.careflow',
        version: '1',
        buildNumber: '19',
        buildSignature: 'test',
      );
      FirebaseBootstrap.isReady = true;
      api = FakeApi();
      platform = FakePush();
      service = PushNotificationService(api, platform: platform);
      service.setAccount('A');
    });
    tearDown(() async {
      await service.dispose();
      await platform.close();
      FirebaseBootstrap.isReady = false;
    });
    test(
      'permission denied does not register and prompting requires explicit enable',
      () async {
        platform.permission = false;
        await service.bindAuthenticatedSession();
        expect(api.registrations, isEmpty);
        expect(platform.requests, 0);
        await service.enable();
        expect(platform.requests, 1);
        expect(service.status, contains('Chưa cho phép'));
      },
    );
    test('APNs/FCM token unavailable remains retryable', () async {
      platform.nextToken = null;
      await service.enable();
      expect(api.registrations, isEmpty);
      expect(service.status, contains('chưa có mã push'));
    });
    test(
      'only current binding alerts and duplicate messages alert once',
      () async {
        await service.bindAuthenticatedSession();
        var changes = 0;
        final sub = service.changes.listen((_) => changes++);
        platform.messages.add({
          'notificationId': nid,
          'bindingId': 'old-binding',
        });
        platform.messages.add({
          'notificationId': nid,
          'bindingId': 'binding-A',
        });
        platform.messages.add({
          'notificationId': nid,
          'bindingId': 'binding-A',
        });
        await settle();
        expect(platform.shown, 1);
        expect(changes, 2);
        await sub.cancel();
      },
    );
    test('late server bind from A cannot authorize a message in B', () async {
      api.putGate = Completer<void>();
      final pending = service.bindAuthenticatedSession();
      await settle();
      service.setAccount('B');
      api.owner = 'B';
      api.binding = 'binding-B';
      api.putGate!.complete();
      await pending;
      platform.messages.add({'notificationId': nid, 'bindingId': 'binding-A'});
      await settle();
      expect(platform.shown, 0);
      api.putGate = null;
      await service.bindAuthenticatedSession();
      platform.messages.add({'notificationId': nid, 'bindingId': 'binding-B'});
      await settle();
      expect(platform.shown, 1);
    });
    test('logout stops token refresh and drops old-account taps', () async {
      await service.bindAuthenticatedSession();
      final taps = <String>[];
      final sub = service.taps.listen(taps.add);
      await service.unbindAuthenticatedSession();
      platform.tokenEvents.add('rotated-old-token');
      platform.messages.add({'notificationId': nid, 'bindingId': 'binding-A'});
      platform.opens.add({'notificationId': nid, 'bindingId': 'binding-A'});
      await settle();
      expect(api.registrations, hasLength(1));
      expect(platform.shown, 0);
      expect(taps, isEmpty);
      await sub.cancel();
    });
    test(
      'cold start waits for one shared initialization before consuming tap',
      () async {
        platform.initGate = Completer<void>();
        platform.initialMessage = {
          'notificationId': nid,
          'bindingId': 'binding-A',
        };
        final taps = <String>[];
        final sub = service.taps.listen(taps.add);
        final init = service.initializeHandlers();
        final bind = service.bindAuthenticatedSession();
        await settle();
        expect(api.registrations, isEmpty);
        platform.initGate!.complete();
        await Future.wait([init, bind]);
        await settle();
        expect(platform.initializes, 1);
        expect(taps, [nid]);
        await sub.cancel();
      },
    );
    test(
      'token rotation registers new token within current owner epoch',
      () async {
        await service.bindAuthenticatedSession();
        platform.tokenEvents.add('rotated-token');
        await settle();
        expect(api.registrations.last['registrationToken'], 'rotated-token');
      },
    );
    test(
      'unacknowledged tap survives interactive lock and same-account rebind',
      () async {
        final taps = <String>[];
        final sub = service.taps.listen(taps.add);
        await service.bindAuthenticatedSession();
        platform.opens.add({'notificationId': nid, 'bindingId': 'binding-A'});
        await settle();
        expect(taps, [nid]);
        service.setAccount(null);
        service.setAccount('A');
        await service.bindAuthenticatedSession();
        await settle();
        expect(taps, [nid, nid]);
        service.acknowledgeTap(nid);
        await service.bindAuthenticatedSession();
        await settle();
        expect(taps, [nid, nid]);
        await sub.cancel();
      },
    );
    test(
      'pending old-account tap is rejected after another account binds',
      () async {
        final taps = <String>[];
        final sub = service.taps.listen(taps.add);
        await service.bindAuthenticatedSession();
        platform.opens.add({'notificationId': nid, 'bindingId': 'binding-A'});
        await settle();
        service.setAccount(null);
        api.owner = 'B';
        api.binding = 'binding-B';
        service.setAccount('B');
        await service.bindAuthenticatedSession();
        await settle();
        expect(taps, [nid]);
        await sub.cancel();
      },
    );
    test(
      'acknowledging an earlier tap does not discard a more recent tap',
      () async {
        final taps = <String>[];
        final sub = service.taps.listen(taps.add);
        await service.bindAuthenticatedSession();
        platform.opens.add({'notificationId': nid, 'bindingId': 'binding-A'});
        platform.opens.add({'notificationId': rid, 'bindingId': 'binding-A'});
        await settle();
        service.acknowledgeTap(nid);
        await service.bindAuthenticatedSession();
        await settle();
        expect(taps, [nid, rid, rid]);
        await sub.cancel();
      },
    );
    test(
      'OS callback during cold-start lookup is retained when lookup returns null',
      () async {
        platform.initialGate = Completer<void>();
        final taps = <String>[];
        final sub = service.taps.listen(taps.add);
        final init = service.initializeHandlers();
        await settle();
        platform.opens.add({'notificationId': nid, 'bindingId': 'binding-A'});
        await settle();
        platform.initialGate!.complete();
        await init;
        await service.bindAuthenticatedSession();
        await settle();
        expect(taps, [nid]);
        await sub.cancel();
      },
    );
    test(
      'locking during SDK initialization prevents a stale authenticated request',
      () async {
        platform.initGate = Completer<void>();
        final bind = service.bindAuthenticatedSession();
        await settle();
        service.setAccount(null);
        platform.initGate!.complete();
        await bind;
        expect(api.paths, isEmpty);
        expect(api.registrations, isEmpty);
      },
    );
  });
  group('R4 authorized destinations', () {
    late FakeApi api;
    late NotificationResourceService service;
    setUp(() {
      api = FakeApi();
      service = NotificationResourceService(api);
      api.rows['/notifications/$nid'] = {
        'id': nid,
        'action': {'type': 'OPEN_LAB_RESULT', 'resourceId': rid},
      };
      api.rows['/labs/orders/$rid'] = {'id': rid, 'patientId': pid};
      api.rows['/patients/$pid'] = {'id': pid};
    });
    test(
      'loads server-owned notification then exact resource and patient',
      () async {
        final resource = await service.load(nid);
        expect(resource.kind, 'lab');
        expect(api.paths, [
          '/notifications/$nid',
          '/labs/orders/$rid',
          '/patients/$pid',
        ]);
      },
    );
    test('forged URL is rejected without resource request', () async {
      api.rows['/notifications/$nid']!['action'] = {
        'type': 'OPEN_LAB_RESULT',
        'resourceId': 'https://evil.invalid',
      };
      await expectLater(service.load(nid), throwsFormatException);
      expect(api.paths, hasLength(1));
    });
    test('resource mismatch never proceeds to patient lookup', () async {
      api.rows['/labs/orders/$rid']!['id'] = nid;
      await expectLater(service.load(nid), throwsFormatException);
      expect(api.paths, hasLength(2));
    });
    test('unknown action cannot navigate to arbitrary route', () async {
      api.rows['/notifications/$nid']!['action'] = {
        'type': 'OPEN_ADMIN',
        'resourceId': rid,
      };
      await expectLater(service.load(nid), throwsFormatException);
      expect(api.paths, hasLength(1));
    });
    test('malformed notification ID makes no network call', () async {
      await expectLater(service.load('../users'), throwsFormatException);
      expect(api.paths, isEmpty);
    });
  });
}
