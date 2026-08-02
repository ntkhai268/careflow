import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../config/api_config.dart';
import '../models/queue.dart';
import 'api_service.dart';

class QueueServiceException implements Exception {
  const QueueServiceException(this.message, {this.statusCode});

  final String message;
  final int? statusCode;

  @override
  String toString() => message;
}

class QueueService {
  QueueService(this._api);

  final ApiService _api;

  Future<VisitTicket> getTicket(String appointmentId) async {
    try {
      final response = await _api.get(
        '${ApiConfig.queues}/tickets/appointment/$appointmentId',
      );
      return VisitTicket.fromJson(
        Map<String, dynamic>.from(response.data['data'] as Map),
      );
    } on DioException catch (error) {
      throw _mapError(error, notFound: 'Phiếu khám đang được hệ thống cấp.');
    }
  }

  Future<PatientQueueStatus> getCurrent(String patientId) async {
    try {
      final response = await _api.get(
        '${ApiConfig.queues}/patients/$patientId/current',
      );
      return PatientQueueStatus.fromJson(
        Map<String, dynamic>.from(response.data['data'] as Map),
      );
    } on DioException catch (error) {
      throw _mapError(error, notFound: 'Bạn chưa ở trong hàng đợi phòng khám.');
    }
  }

  QueueServiceException _mapError(
    DioException error, {
    required String notFound,
  }) {
    final status = error.response?.statusCode;
    final data = error.response?.data;
    final serverMessage = data is Map
        ? data['message']?.toString().trim()
        : null;
    if (status == 404) {
      return QueueServiceException(notFound, statusCode: status);
    }
    if (serverMessage != null && serverMessage.isNotEmpty) {
      return QueueServiceException(serverMessage, statusCode: status);
    }
    return QueueServiceException(
      'Không thể kết nối hệ thống hàng đợi. Vui lòng thử lại.',
      statusCode: status,
    );
  }
}

final queueServiceProvider = Provider<QueueService>(
  (ref) => QueueService(ref.watch(apiServiceProvider)),
);

final queueTicketProvider = FutureProvider.autoDispose
    .family<VisitTicket, String>(
      (ref, appointmentId) =>
          ref.watch(queueServiceProvider).getTicket(appointmentId),
    );

final appointmentQueueStatusProvider = FutureProvider.autoDispose
    .family<PatientQueueStatus, String>((ref, appointmentId) async {
      final ticket = await ref.watch(queueTicketProvider(appointmentId).future);
      return ref.watch(queueServiceProvider).getCurrent(ticket.patientId);
    });
