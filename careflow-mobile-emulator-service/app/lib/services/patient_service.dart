import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../config/api_config.dart';
import '../models/patient.dart';
import 'api_service.dart';

/// Service for communicating with the Patient Service via API Gateway.
/// Uses the shared ApiService (with JWT interceptor) so all requests
/// carry the authenticated user's Bearer token automatically.
class PatientService {
  final ApiService _apiService;

  PatientService(this._apiService);

  /// Create a new patient profile
  Future<Patient> createPatient(Map<String, dynamic> data) async {
    final response = await _apiService.post(ApiConfig.patients, data: data);
    final apiResponse = response.data as Map<String, dynamic>;
    return Patient.fromJson(apiResponse['data'] as Map<String, dynamic>);
  }

  /// Get patient by ID
  Future<Patient> getPatientById(String id) async {
    final response = await _apiService.get('${ApiConfig.patients}/$id');
    final apiResponse = response.data as Map<String, dynamic>;
    return Patient.fromJson(apiResponse['data'] as Map<String, dynamic>);
  }

  /// Get patient by user ID
  Future<Patient?> getPatientByUserId(String userId) async {
    try {
      final response = await _apiService.get(
        '${ApiConfig.patients}/user/$userId',
      );
      final apiResponse = response.data as Map<String, dynamic>;
      if (apiResponse['data'] == null) return null;
      return Patient.fromJson(apiResponse['data'] as Map<String, dynamic>);
    } on DioException catch (e) {
      if (e.response?.statusCode == 404) return null;
      rethrow;
    }
  }

  /// Get all patient profiles owned by the authenticated account.
  Future<List<Patient>> getPatientsByUserId(String userId) async {
    final response = await _apiService.get(
      '${ApiConfig.patients}/user/$userId/profiles',
    );
    final apiResponse = response.data as Map<String, dynamic>;
    final data = apiResponse['data'] as List<dynamic>? ?? const [];
    return data
        .map((item) => Patient.fromJson(Map<String, dynamic>.from(item as Map)))
        .toList();
  }

  /// Update patient profile
  Future<Patient> updatePatient(String id, Map<String, dynamic> data) async {
    final response = await _apiService.put(
      '${ApiConfig.patients}/$id',
      data: data,
    );
    final apiResponse = response.data as Map<String, dynamic>;
    return Patient.fromJson(apiResponse['data'] as Map<String, dynamic>);
  }

  /// Delete patient profile
  Future<void> deletePatient(String id) async {
    await _apiService.delete('${ApiConfig.patients}/$id');
  }
}

/// Global provider for PatientService — uses the shared ApiService
/// so JWT Bearer token is automatically attached to all requests.
final patientServiceProvider = Provider<PatientService>((ref) {
  final apiService = ref.read(apiServiceProvider);
  return PatientService(apiService);
});
