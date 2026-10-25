import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../providers/auth_provider.dart';
import 'api_service.dart';

class NotificationResource {
  const NotificationResource(
    this.notification,
    this.kind,
    this.data,
    this.patient,
  );
  final Map<String, dynamic> notification, data, patient;
  final String kind;
}

class NotificationResourceService {
  NotificationResourceService(this.api);
  final ApiService api;
  static final uuid = RegExp(
    r'^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$',
  );
  Future<Map<String, dynamic>> object(String path) async {
    final response = await api.get(path);
    final data = response.data['data'];
    if (data is! Map) {
      throw const FormatException('Chưa tải được dữ liệu thông báo.');
    }
    return Map<String, dynamic>.from(data);
  }

  Future<NotificationResource> load(String id) async {
    if (!uuid.hasMatch(id)) {
      throw const FormatException('Mã thông báo không hợp lệ.');
    }
    final notification = await object('/notifications/$id');
    if (notification['id'] != id) {
      throw const FormatException('Thông báo không khớp.');
    }
    final action = notification['action'];
    if (action is! Map ||
        action['resourceId'] is! String ||
        !uuid.hasMatch(action['resourceId'] as String)) {
      throw const FormatException('Thông báo chưa có tài nguyên có thể mở.');
    }
    final resource = action['resourceId'] as String;
    late String path, kind, idKey;
    switch (action['type']) {
      case 'OPEN_APPOINTMENT':
        path = '/appointments/$resource';
        kind = 'appointment';
        idKey = 'id';
      case 'OPEN_QUEUE':
      case 'OPEN_TICKET':
        path = '/queues/entries/$resource/patient-context';
        kind = 'queue';
        idKey = 'entryId';
      case 'OPEN_LAB_ORDER':
      case 'OPEN_LAB_RESULT':
        path = '/labs/orders/$resource';
        kind = 'lab';
        idKey = 'id';
      case 'OPEN_RESULT_REVIEW':
      case 'OPEN_CONSULTATION':
        path = '/consultations/$resource';
        kind = 'consultation';
        idKey = 'id';
      case 'OPEN_PRESCRIPTION':
        path = '/prescriptions/$resource';
        kind = 'prescription';
        idKey = 'id';
      default:
        throw const FormatException('Loại thông báo chưa được hỗ trợ.');
    }
    final data = await object(path);
    if (data[idKey] != resource ||
        data['patientId'] is! String ||
        !uuid.hasMatch(data['patientId'] as String)) {
      throw const FormatException('Tài nguyên không khớp thông báo.');
    }
    final patient = await object('/patients/${data['patientId']}');
    if (patient['id'] != data['patientId']) {
      throw const FormatException('Hồ sơ không khớp tài nguyên.');
    }
    return NotificationResource(notification, kind, data, patient);
  }
}

final notificationResourceServiceProvider = Provider(
  (ref) => NotificationResourceService(ref.read(apiServiceProvider)),
);
final notificationResourceProvider = FutureProvider.autoDispose
    .family<NotificationResource?, String>((ref, id) async {
      final auth = ref.watch(authProvider.select((a) => (a.status, a.userId)));
      if (auth.$1 != AuthStatus.authenticated || auth.$2 == null) return null;
      return ref.watch(notificationResourceServiceProvider).load(id);
    });
final notificationUnreadCountProvider = FutureProvider.autoDispose<int>((
  ref,
) async {
  final auth = ref.watch(authProvider.select((a) => (a.status, a.userId)));
  if (auth.$1 != AuthStatus.authenticated || auth.$2 == null) return 0;
  final data = await ref
      .watch(notificationResourceServiceProvider)
      .object('/notifications/unread-count');
  final count = data['unreadCount'];
  if (count is! num || count < 0) {
    throw const FormatException('Chưa tải được số thông báo.');
  }
  return count.toInt();
});
