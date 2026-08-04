import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:uuid/uuid.dart';
import '../config/api_config.dart';
import '../models/appointment.dart';
import '../utils/api_error_message.dart';
import 'api_service.dart';

class AppointmentBookingException implements Exception {
  const AppointmentBookingException(this.message);

  final String message;

  @override
  String toString() => message;
}

String appointmentBookingErrorMessage(Object error) {
  if (error is AppointmentBookingException) return error.message;
  if (error is DioException && error.response?.statusCode == 409) {
    return ApiErrorMessage.from(
      error,
      fallback: 'Bạn đã có lịch khám vào khung giờ này. Vui lòng chọn ca khác.',
    );
  }
  return ApiErrorMessage.from(
    error,
    fallback: 'Không thể đặt khám lúc này. Vui lòng thử lại.',
  );
}

/// Converts appointment API failures into copy safe for patient-facing UI.
/// The raw Dio exception must never be shown to a patient.
String appointmentRequestErrorMessage(Object error) {
  if (error is AppointmentBookingException) return error.message;
  if (error is! DioException) {
    return 'Không thể tải dữ liệu lịch khám. Vui lòng thử lại.';
  }

  final responseData = error.response?.data;
  final serverMessage = responseData is Map
      ? responseData['message']?.toString().trim()
      : null;
  if (serverMessage != null &&
      serverMessage.isNotEmpty &&
      !serverMessage.contains('DioException')) {
    return serverMessage;
  }

  return switch (error.response?.statusCode) {
    401 => 'Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.',
    403 => 'Bạn không có quyền xem lịch khám này.',
    404 => 'Không tìm thấy lịch khám.',
    500 || 502 || 503 || 504 =>
      'Hệ thống lịch khám đang bận. Vui lòng thử lại sau.',
    _ => 'Không thể kết nối hệ thống lịch khám. Vui lòng thử lại.',
  };
}

/// Service for communicating with the Appointment Service via API Gateway.
/// Uses the shared ApiService (with JWT interceptor) so all requests
/// carry the authenticated user's Bearer token automatically.
class AppointmentService {
  final ApiService _apiService;

  AppointmentService(this._apiService);

  /// Create a new appointment
  Future<Appointment> createAppointment(
    Map<String, dynamic> data, {
    String? idempotencyKey,
  }) async {
    try {
      final requestKey = idempotencyKey ?? const Uuid().v4();
      final response = await _apiService.post(
        ApiConfig.appointments,
        data: data,
        options: Options(
          headers: <String, String>{'Idempotency-Key': requestKey},
        ),
      );
      final apiResponse = response.data as Map<String, dynamic>;
      return Appointment.fromJson(apiResponse['data'] as Map<String, dynamic>);
    } on DioException catch (error) {
      throw AppointmentBookingException(appointmentBookingErrorMessage(error));
    }
  }

  /// Get appointments by patient ID
  Future<List<Appointment>> getAppointmentsByPatientId(String patientId) async {
    final response = await _apiService.get(
      '${ApiConfig.appointments}/patient/$patientId',
    );
    final apiResponse = response.data as Map<String, dynamic>;
    final list = apiResponse['data'] as List<dynamic>;
    return list
        .map((e) => Appointment.fromJson(e as Map<String, dynamic>))
        .toList();
  }

  /// Get appointment by ID
  Future<Appointment> getAppointmentById(String id) async {
    final response = await _apiService.get('${ApiConfig.appointments}/$id');
    final apiResponse = response.data as Map<String, dynamic>;
    return Appointment.fromJson(apiResponse['data'] as Map<String, dynamic>);
  }

  /// Cancel appointment
  Future<Appointment> cancelAppointment(String id) async {
    final response = await _apiService.put(
      '${ApiConfig.appointments}/$id/cancel',
    );
    final apiResponse = response.data as Map<String, dynamic>;
    return Appointment.fromJson(apiResponse['data'] as Map<String, dynamic>);
  }

  /// Get all departments
  Future<List<Department>> getDepartments() async {
    final response = await _apiService.get(
      '${ApiConfig.appointments}/departments',
    );
    final apiResponse = response.data as Map<String, dynamic>;
    final list = apiResponse['data'] as List<dynamic>;
    return list
        .map((e) => Department.fromJson(e as Map<String, dynamic>))
        .toList();
  }

  /// Get available time slots
  Future<List<String>> getTimeSlots({
    String? department,
    DateTime? date,
  }) async {
    final queryParams = <String, dynamic>{};
    if (department != null && department.isNotEmpty) {
      queryParams['department'] = department;
    }
    if (date != null) {
      queryParams['date'] = date.toIso8601String().split('T').first;
    }
    final response = await _apiService.get(
      '${ApiConfig.appointments}/time-slots',
      queryParams: queryParams.isEmpty ? null : queryParams,
    );
    final apiResponse = response.data as Map<String, dynamic>;
    final list = apiResponse['data'] as List<dynamic>;
    return list.map((e) => e as String).toList();
  }

  /// Get every slot together with booked/remaining capacity and disable reason.
  Future<List<AppointmentTimeSlot>> getTimeSlotAvailability({
    required String department,
    required DateTime date,
  }) async {
    final response = await _apiService.get(
      '${ApiConfig.appointments}/time-slots/availability',
      queryParams: <String, dynamic>{
        'department': department,
        'date': date.toIso8601String().split('T').first,
      },
    );
    final apiResponse = response.data as Map<String, dynamic>;
    final list = apiResponse['data'] as List<dynamic>;
    return list
        .map((e) => AppointmentTimeSlot.fromJson(e as Map<String, dynamic>))
        .toList();
  }
}

/// Global provider for AppointmentService — uses the shared ApiService
/// so JWT Bearer token is automatically attached to all requests.
final appointmentServiceProvider = Provider<AppointmentService>((ref) {
  final apiService = ref.read(apiServiceProvider);
  return AppointmentService(apiService);
});
