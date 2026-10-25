import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../config/api_config.dart';
import '../models/queue.dart';
import '../utils/api_error_message.dart';
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
    // Ticket creation follows AppointmentConfirmed through the outbox/broker.
    // Brief 404s mean the real consumer has not committed yet; never invent a ticket.
    for (var attempt = 0; ; attempt++) {
      try {
        final response = await _api.get(
          '${ApiConfig.queues}/tickets/appointment/$appointmentId',
        );
        return VisitTicket.fromJson(
          Map<String, dynamic>.from(response.data['data'] as Map),
        );
      } on DioException catch (error) {
        if (error.response?.statusCode == 404 && attempt < 5) {
          await Future<void>.delayed(const Duration(milliseconds: 300));
          continue;
        }
        throw _mapError(error, notFound: 'Phiếu khám đang được hệ thống cấp. Vui lòng tải lại.');
      }
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

  Future<void> checkInAtHospital({
    required String appointmentId,
    required String checkInQrToken,
    required double latitude,
    required double longitude,
    required double accuracyMeters,
  }) async {
    try {
      await _api.post(
        '${ApiConfig.queues}/check-in',
        data: {
          'appointmentId': appointmentId,
          'checkInQrToken': checkInQrToken,
          'latitude': latitude,
          'longitude': longitude,
          'accuracyMeters': accuracyMeters,
        },
      );
    } on DioException catch (error) {
      throw _mapError(
        error,
        notFound: 'Không tìm thấy lịch hẹn để check-in.',
        fallback: 'Không thể check-in. Hãy đảm bảo bạn đang ở trong khuôn viên bệnh viện.',
      );
    }
  }

  QueueServiceException _mapError(
    DioException error, {
    required String notFound,
    String fallback = 'Không thể tải trạng thái hàng đợi. Vui lòng thử lại.',
  }) {
    final status = error.response?.statusCode;
    if (status == 404) {
      return QueueServiceException(notFound, statusCode: status);
    }
    return QueueServiceException(
      ApiErrorMessage.from(
        error,
        fallback: fallback,
      ),
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
