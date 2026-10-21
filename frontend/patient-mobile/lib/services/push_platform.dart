import 'dart:async';
import 'dart:convert';
import 'package:firebase_messaging/firebase_messaging.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter_local_notifications/flutter_local_notifications.dart';

abstract interface class PushPlatform {
  Future<void> initialize();
  Future<bool> allowed(bool request);
  Future<String?> token();
  Stream<String> get tokens;
  Stream<Map<String, dynamic>> get foreground;
  Stream<Map<String, dynamic>> get opened;
  Future<Map<String, dynamic>?> initial();
  Future<void> deleteToken();
  Future<void> clearVisible();
  Future<void> showGeneric(Map<String, dynamic> data);
}

class FirebasePushPlatform implements PushPlatform {
  final _local = FlutterLocalNotificationsPlugin();
  final _localTaps = StreamController<Map<String, dynamic>>.broadcast();
  FirebaseMessaging get _firebase => FirebaseMessaging.instance;
  @override
  Future<void> initialize() async {
    await _local.initialize(
      settings: const InitializationSettings(
        android: AndroidInitializationSettings('@mipmap/ic_launcher'),
        iOS: DarwinInitializationSettings(
          requestAlertPermission: false,
          requestBadgePermission: false,
          requestSoundPermission: false,
        ),
      ),
      onDidReceiveNotificationResponse: (response) {
        try {
          final data = jsonDecode(response.payload ?? '');
          if (data is Map) _localTaps.add(Map<String, dynamic>.from(data));
        } catch (_) {
          /* Invalid taps do not navigate. */
        }
      },
    );
    await _local
        .resolvePlatformSpecificImplementation<
          AndroidFlutterLocalNotificationsPlugin
        >()
        ?.createNotificationChannel(
          const AndroidNotificationChannel(
            'careflow_notifications',
            'Thông báo CareFlow',
            description: 'Cập nhật trong ứng dụng',
            importance: Importance.high,
          ),
        );
  }

  @override
  Future<bool> allowed(bool request) async {
    final permission = request
        ? await _firebase.requestPermission(
            alert: true,
            badge: true,
            sound: true,
          )
        : await _firebase.getNotificationSettings();
    return permission.authorizationStatus == AuthorizationStatus.authorized ||
        permission.authorizationStatus == AuthorizationStatus.provisional;
  }

  @override
  Future<String?> token() async {
    if (defaultTargetPlatform == TargetPlatform.iOS) {
      // FCM calls must wait for APNs; give the OS a bounded interval, then allow retry.
      String? apns;
      for (int attempt = 0; attempt < 5; attempt++) {
        apns = await _firebase.getAPNSToken();
        if (apns != null) break;
        await Future<void>.delayed(const Duration(milliseconds: 400));
      }
      if (apns == null) return null;
    }
    return _firebase.getToken();
  }

  @override
  Stream<String> get tokens => _firebase.onTokenRefresh;
  @override
  Stream<Map<String, dynamic>> get foreground =>
      FirebaseMessaging.onMessage.map((m) => m.data);
  @override
  Stream<Map<String, dynamic>> get opened =>
      Stream<Map<String, dynamic>>.multi((controller) {
        final a = FirebaseMessaging.onMessageOpenedApp.listen(
              (m) => controller.add(m.data),
            ),
            b = _localTaps.stream.listen(controller.add);
        controller.onCancel = () async {
          await a.cancel();
          await b.cancel();
        };
      });
  @override
  Future<Map<String, dynamic>?> initial() async {
    final remote = await _firebase.getInitialMessage();
    if (remote != null) return remote.data;
    final local = await _local.getNotificationAppLaunchDetails();
    try {
      final data = jsonDecode(local?.notificationResponse?.payload ?? '');
      return data is Map ? Map<String, dynamic>.from(data) : null;
    } catch (_) {
      return null;
    }
  }

  @override
  Future<void> deleteToken() => _firebase.deleteToken();
  @override
  Future<void> clearVisible() => _local.cancelAll();
  @override
  Future<void> showGeneric(Map<String, dynamic> data) => _local.show(
    id: data['notificationId'].hashCode,
    title: 'CareFlow',
    body: 'Bạn có thông báo mới. Mở CareFlow để xem chi tiết.',
    notificationDetails: const NotificationDetails(
      android: AndroidNotificationDetails(
        'careflow_notifications',
        'Thông báo CareFlow',
        importance: Importance.high,
        priority: Priority.high,
      ),
      iOS: DarwinNotificationDetails(),
    ),
    payload: jsonEncode(data),
  );
}
