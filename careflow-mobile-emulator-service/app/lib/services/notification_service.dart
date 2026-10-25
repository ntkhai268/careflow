import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../config/api_config.dart';
import '../models/app_notification.dart';
import 'api_service.dart';

class NotificationServiceException implements Exception {
  const NotificationServiceException(this.message);

  final String message;

  @override
  String toString() => message;
}

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

  Future<List<AppNotification>> getInbox({int limit = 50}) async {
    try {
      final raw = await inbox(limit: limit);
      return raw.map(AppNotification.fromJson).toList(growable: false);
    } on DioException catch (error) {
      throw NotificationServiceException(_messageFor(error));
    }
  }

  Future<void> markAllRead() async {
    try {
      await _api.post('${ApiConfig.notifications}/read-all');
    } on DioException catch (error) {
      throw NotificationServiceException(_messageFor(error));
    }
  }

  String _messageFor(DioException error) {
    final data = error.response?.data;
    final serverMessage = data is Map
        ? data['message']?.toString().trim()
        : null;
    if (serverMessage != null && serverMessage.isNotEmpty) {
      return serverMessage;
    }
    return switch (error.response?.statusCode) {
      401 => 'Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.',
      500 || 502 || 503 || 504 =>
        'Hệ thống thông báo đang bận. Vui lòng thử lại sau.',
      _ => 'Không thể tải thông báo. Vui lòng thử lại.',
    };
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

final notificationInboxProvider =
    FutureProvider.autoDispose<List<AppNotification>>(
      (ref) => ref.watch(notificationServiceProvider).getInbox(),
    );
