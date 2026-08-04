import 'dart:async';
import 'dart:convert';

import 'package:firebase_core/firebase_core.dart';
import 'package:firebase_messaging/firebase_messaging.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter_local_notifications/flutter_local_notifications.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:package_info_plus/package_info_plus.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:uuid/uuid.dart';

import '../config/api_config.dart';
import '../config/router.dart';
import 'api_service.dart';

class FirebaseBootstrap {
  static const enabled = bool.fromEnvironment(
    'FIREBASE_ENABLED',
    defaultValue: false,
  );
  static bool isReady = false;

  static Future<void> initialize() async {
    if (!enabled) return;
    try {
      await Firebase.initializeApp();
      isReady = true;
    } catch (error) {
      debugPrint(
        'Firebase chưa được cấu hình; app tiếp tục bằng inbox/WebSocket: $error',
      );
    }
  }
}

class PushNotificationService {
  static const _deviceIdKey = 'careflow_device_id';
  static const _channel = AndroidNotificationChannel(
    'careflow_notifications',
    'Thông báo CareFlow',
    description: 'Lượt khám, chỉ định, kết quả và toa thuốc',
    importance: Importance.high,
  );

  final ApiService _api;
  FirebaseMessaging? _messaging;
  final FlutterLocalNotificationsPlugin _localNotifications;
  bool _handlersInitialized = false;
  StreamSubscription<String>? _tokenRefreshSubscription;

  PushNotificationService(
    this._api, {
    FirebaseMessaging? messaging,
    FlutterLocalNotificationsPlugin? localNotifications,
  }) : _messaging = messaging,
       _localNotifications =
           localNotifications ?? FlutterLocalNotificationsPlugin();

  FirebaseMessaging get _firebaseMessaging =>
      _messaging ??= FirebaseMessaging.instance;

  Future<void> initializeHandlers() async {
    if (!FirebaseBootstrap.isReady || _handlersInitialized) return;
    _handlersInitialized = true;

    const settings = InitializationSettings(
      android: AndroidInitializationSettings('@mipmap/ic_launcher'),
      iOS: DarwinInitializationSettings(),
    );
    await _localNotifications.initialize(
      settings: settings,
      onDidReceiveNotificationResponse: (response) {
        if (response.payload == null) return;
        final data = Map<String, dynamic>.from(
          jsonDecode(response.payload!) as Map,
        );
        _open(data);
      },
    );
    await _localNotifications
        .resolvePlatformSpecificImplementation<
          AndroidFlutterLocalNotificationsPlugin
        >()
        ?.createNotificationChannel(_channel);

    FirebaseMessaging.onMessage.listen(_showForegroundNotification);
    FirebaseMessaging.onMessageOpenedApp.listen(
      (message) => _open(message.data),
    );
    final initialMessage = await _firebaseMessaging.getInitialMessage();
    if (initialMessage != null) _open(initialMessage.data);
  }

  Future<void> bindAuthenticatedSession() async {
    if (!FirebaseBootstrap.isReady) return;
    try {
      await initializeHandlers();
      final permission = await _firebaseMessaging.requestPermission(
        alert: true,
        badge: true,
        sound: true,
      );
      if (permission.authorizationStatus == AuthorizationStatus.denied) return;

      final token = await _firebaseMessaging.getToken();
      if (token != null && token.isNotEmpty) await _register(token);
      _tokenRefreshSubscription ??= _firebaseMessaging.onTokenRefresh.listen(
        (token) => _register(token).catchError(
          (Object error) => debugPrint('Không thể gửi FCM token mới: $error'),
        ),
        onError: (Object error) =>
            debugPrint('Không thể làm mới FCM token: $error'),
      );
    } catch (error) {
      debugPrint('Không thể đăng ký nhận push; inbox vẫn hoạt động: $error');
    }
  }

  Future<void> unbindAuthenticatedSession() async {
    if (!FirebaseBootstrap.isReady) return;
    final deviceId = await _deviceId();
    try {
      await _api.delete('${ApiConfig.notifications}/devices/$deviceId');
    } catch (error) {
      debugPrint('Không thể hủy đăng ký push trên server: $error');
    }
    await _tokenRefreshSubscription?.cancel();
    _tokenRefreshSubscription = null;
  }

  Future<void> _register(String token) async {
    final package = await PackageInfo.fromPlatform();
    await _api.put(
      '${ApiConfig.notifications}/devices',
      data: {
        'deviceId': await _deviceId(),
        'registrationToken': token,
        'platform': _platform,
        'appVersion': '${package.version}+${package.buildNumber}',
      },
    );
  }

  Future<String> _deviceId() async {
    final preferences = await SharedPreferences.getInstance();
    final current = preferences.getString(_deviceIdKey);
    if (current != null && current.isNotEmpty) return current;
    final generated = const Uuid().v4();
    await preferences.setString(_deviceIdKey, generated);
    return generated;
  }

  String get _platform {
    if (kIsWeb) return 'WEB';
    if (defaultTargetPlatform == TargetPlatform.iOS) return 'IOS';
    return 'ANDROID';
  }

  Future<void> _showForegroundNotification(RemoteMessage message) async {
    final notification = message.notification;
    if (notification == null) return;
    await _localNotifications.show(
      id: notification.hashCode,
      title: notification.title,
      body: notification.body,
      notificationDetails: const NotificationDetails(
        android: AndroidNotificationDetails(
          'careflow_notifications',
          'Thông báo CareFlow',
          channelDescription: 'Lượt khám, chỉ định, kết quả và toa thuốc',
          importance: Importance.high,
          priority: Priority.high,
        ),
        iOS: DarwinNotificationDetails(),
      ),
      payload: jsonEncode(message.data),
    );
  }

  void _open(Map<String, dynamic> data) {
    final actionType = data['actionType']?.toString();
    final resourceId = data['resourceId']?.toString();
    if (actionType == 'OPEN_APPOINTMENT' && resourceId?.isNotEmpty == true) {
      appRouter.go('/appointment/$resourceId');
      return;
    }
    if (actionType == 'OPEN_QUEUE' || actionType == 'OPEN_TICKET') {
      // Queue notifications currently carry a queue-entry ID. The Mobile
      // contract exposes queue details through the appointment list, so take
      // the patient there instead of treating the entry ID as an appointment.
      appRouter.go('/?tab=1');
      return;
    }
    appRouter.go('/');
  }
}

final pushNotificationServiceProvider = Provider<PushNotificationService>((
  ref,
) {
  return PushNotificationService(ref.read(apiServiceProvider));
});
