import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../config/api_config.dart';
import 'api_service.dart';

abstract interface class NotificationGateway {
  Future<List<Map<String, dynamic>>> inbox({int limit = 50});

  Future<Map<String, dynamic>?> markRead(String notificationId);
}

class NotificationService implements NotificationGateway {
  NotificationService(this._api);

  final ApiService _api;

  @override
  Future<List<Map<String, dynamic>>> inbox({int limit = 50}) async {
    final response = await _api.get(
      ApiConfig.notifications,
      queryParams: {'limit': limit},
    );
    return _dataList(response.data);
  }

  @override
  Future<Map<String, dynamic>?> markRead(String notificationId) async {
    final response = await _api.post(
      '${ApiConfig.notifications}/$notificationId/read',
    );
    final data = response.data is Map ? response.data['data'] : response.data;
    if (data is! Map) return null;
    return Map<String, dynamic>.from(data);
  }
}

final notificationServiceProvider = Provider<NotificationService>(
  (ref) => NotificationService(ref.watch(apiServiceProvider)),
);

List<Map<String, dynamic>> _dataList(Object? responseData) {
  final data = responseData is Map ? responseData['data'] : responseData;
  if (data is! List) return const [];
  return data
      .whereType<Map>()
      .map((item) => Map<String, dynamic>.from(item))
      .toList(growable: false);
}
