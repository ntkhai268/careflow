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

class NotificationService {
  NotificationService(this._api);

  final ApiService _api;

  Future<List<AppNotification>> getInbox({int limit = 50}) async {
    try {
      final response = await _api.get(
        ApiConfig.notifications,
        queryParams: {'limit': limit},
      );
      final data = response.data['data'];
      if (data is! List) {
        throw const NotificationServiceException(
          'Dữ liệu thông báo không hợp lệ.',
        );
      }
      return data
          .map(
            (item) => AppNotification.fromJson(
              Map<String, dynamic>.from(item as Map),
            ),
          )
          .toList();
    } on NotificationServiceException {
      rethrow;
    } on DioException catch (error) {
      throw NotificationServiceException(_messageFor(error));
    }
  }

  Future<AppNotification> markRead(String id) async {
    try {
      final response = await _api.post('${ApiConfig.notifications}/$id/read');
      return AppNotification.fromJson(
        Map<String, dynamic>.from(response.data['data'] as Map),
      );
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
    if (serverMessage != null &&
        serverMessage.isNotEmpty &&
        !serverMessage.contains('DioException')) {
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

final notificationInboxProvider =
    FutureProvider.autoDispose<List<AppNotification>>(
      (ref) => ref.watch(notificationServiceProvider).getInbox(),
    );
