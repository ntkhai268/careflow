import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../config/api_config.dart';
import '../models/appointment.dart';

/// Service for communicating with the Appointment Service backend API.
class AppointmentService {
  final Dio _dio;

  AppointmentService()
      : _dio = Dio(
          BaseOptions(
            baseUrl: ApiConfig.appointmentServiceUrl,
            connectTimeout: const Duration(seconds: 15),
            receiveTimeout: const Duration(seconds: 15),
            headers: {
              'Content-Type': 'application/json',
              'Accept': 'application/json',
            },
          ),
        );

  /// Create a new appointment
  Future<Appointment> createAppointment(Map<String, dynamic> data) async {
    final response = await _dio.post('/api/appointments', data: data);
    final apiResponse = response.data as Map<String, dynamic>;
    return Appointment.fromJson(apiResponse['data'] as Map<String, dynamic>);
  }

  /// Get appointments by patient ID
  Future<List<Appointment>> getAppointmentsByPatientId(String patientId) async {
    final response = await _dio.get('/api/appointments/patient/$patientId');
    final apiResponse = response.data as Map<String, dynamic>;
    final list = apiResponse['data'] as List<dynamic>;
    return list
        .map((e) => Appointment.fromJson(e as Map<String, dynamic>))
        .toList();
  }

  /// Get appointment by ID
  Future<Appointment> getAppointmentById(String id) async {
    final response = await _dio.get('/api/appointments/$id');
    final apiResponse = response.data as Map<String, dynamic>;
    return Appointment.fromJson(apiResponse['data'] as Map<String, dynamic>);
  }

  /// Cancel appointment
  Future<Appointment> cancelAppointment(String id) async {
    final response = await _dio.put('/api/appointments/$id/cancel');
    final apiResponse = response.data as Map<String, dynamic>;
    return Appointment.fromJson(apiResponse['data'] as Map<String, dynamic>);
  }

  /// Get all departments
  Future<List<Department>> getDepartments() async {
    final response = await _dio.get('/api/appointments/departments');
    final apiResponse = response.data as Map<String, dynamic>;
    final list = apiResponse['data'] as List<dynamic>;
    return list
        .map((e) => Department.fromJson(e as Map<String, dynamic>))
        .toList();
  }

  /// Get available time slots
  Future<List<String>> getTimeSlots() async {
    final response = await _dio.get('/api/appointments/time-slots');
    final apiResponse = response.data as Map<String, dynamic>;
    final list = apiResponse['data'] as List<dynamic>;
    return list.map((e) => e as String).toList();
  }
}

/// Global provider for AppointmentService
final appointmentServiceProvider = Provider<AppointmentService>((ref) {
  return AppointmentService();
});
