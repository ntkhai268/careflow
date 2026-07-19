import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../config/api_config.dart';
import '../models/patient.dart';

/// Service for communicating with the Patient Service backend API.
class PatientService {
  final Dio _dio;

  PatientService()
      : _dio = Dio(
          BaseOptions(
            baseUrl: ApiConfig.patientServiceUrl,
            connectTimeout: const Duration(seconds: 15),
            receiveTimeout: const Duration(seconds: 15),
            headers: {
              'Content-Type': 'application/json',
              'Accept': 'application/json',
            },
          ),
        );

  /// Create a new patient profile
  Future<Patient> createPatient(Map<String, dynamic> data) async {
    final response = await _dio.post('/api/patients', data: data);
    final apiResponse = response.data as Map<String, dynamic>;
    return Patient.fromJson(apiResponse['data'] as Map<String, dynamic>);
  }

  /// Get patient by ID
  Future<Patient> getPatientById(String id) async {
    final response = await _dio.get('/api/patients/$id');
    final apiResponse = response.data as Map<String, dynamic>;
    return Patient.fromJson(apiResponse['data'] as Map<String, dynamic>);
  }

  /// Get patient by user ID
  Future<Patient?> getPatientByUserId(String userId) async {
    try {
      final response = await _dio.get('/api/patients/user/$userId');
      final apiResponse = response.data as Map<String, dynamic>;
      if (apiResponse['data'] == null) return null;
      return Patient.fromJson(apiResponse['data'] as Map<String, dynamic>);
    } on DioException catch (e) {
      if (e.response?.statusCode == 404) return null;
      rethrow;
    }
  }

  /// Update patient profile
  Future<Patient> updatePatient(String id, Map<String, dynamic> data) async {
    final response = await _dio.put('/api/patients/$id', data: data);
    final apiResponse = response.data as Map<String, dynamic>;
    return Patient.fromJson(apiResponse['data'] as Map<String, dynamic>);
  }

  /// Delete patient profile
  Future<void> deletePatient(String id) async {
    await _dio.delete('/api/patients/$id');
  }
}

/// Global provider for PatientService
final patientServiceProvider = Provider<PatientService>((ref) {
  return PatientService();
});
