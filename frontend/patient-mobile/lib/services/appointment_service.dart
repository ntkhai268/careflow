import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../config/api_config.dart';
import '../models/appointment.dart';
import 'api_service.dart';

/// Service for communicating with the Appointment Service via API Gateway.
/// Uses the shared ApiService (with JWT interceptor) so all requests
/// carry the authenticated user's Bearer token automatically.
class AppointmentService {
  final ApiService _apiService;

  AppointmentService(this._apiService);

  /// Create a new appointment
  Future<Appointment> createAppointment(Map<String, dynamic> data) async {
    final response = await _apiService.post(
      ApiConfig.appointments,
      data: data,
    );
    final apiResponse = response.data as Map<String, dynamic>;
    return Appointment.fromJson(apiResponse['data'] as Map<String, dynamic>);
  }

  /// Get appointments by patient ID
  Future<List<Appointment>> getAppointmentsByPatientId(
      String patientId) async {
    final response =
        await _apiService.get('${ApiConfig.appointments}/patient/$patientId');
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
    final response =
        await _apiService.put('${ApiConfig.appointments}/$id/cancel');
    final apiResponse = response.data as Map<String, dynamic>;
    return Appointment.fromJson(apiResponse['data'] as Map<String, dynamic>);
  }

  /// Get all departments
  Future<List<Department>> getDepartments() async {
    final response =
        await _apiService.get('${ApiConfig.appointments}/departments');
    final apiResponse = response.data as Map<String, dynamic>;
    final list = apiResponse['data'] as List<dynamic>;
    return list
        .map((e) => Department.fromJson(e as Map<String, dynamic>))
        .toList();
  }

  /// Get available time slots
  Future<List<String>> getTimeSlots() async {
    final response =
        await _apiService.get('${ApiConfig.appointments}/time-slots');
    final apiResponse = response.data as Map<String, dynamic>;
    final list = apiResponse['data'] as List<dynamic>;
    return list.map((e) => e as String).toList();
  }
}

/// Global provider for AppointmentService — uses the shared ApiService
/// so JWT Bearer token is automatically attached to all requests.
final appointmentServiceProvider = Provider<AppointmentService>((ref) {
  final apiService = ref.read(apiServiceProvider);
  return AppointmentService(apiService);
});
