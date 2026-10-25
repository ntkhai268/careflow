import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../platform/browser_bridge.dart';
import 'api_service.dart';

/// Web uses authenticated STOMP and the real inbox, without fake FCM devices.
class PushNotificationService {
  PushNotificationService(this._api);
  final ApiService _api;
  Future<void> bindAuthenticatedSession() async {
    await browserCall('session', {'token': await _api.getToken() ?? ''});
  }

  Future<void> unbindAuthenticatedSession() async {
    await browserCall('session', {'token': ''});
  }
}

final pushNotificationServiceProvider = Provider<PushNotificationService>(
  (ref) => PushNotificationService(ref.read(apiServiceProvider)),
);
