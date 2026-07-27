import 'dart:io';
import 'dart:convert';
import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../models/health_record.dart';
import '../config/api_config.dart';
import 'api_service.dart';

class HealthRecordService {
  final ApiService _apiService;

  HealthRecordService(this._apiService);

  Future<List<HealthRecord>> getAll(String patientId) async {
    final response = await _apiService.get('${ApiConfig.patients}/$patientId/health-records');
    final apiResponse = response.data as Map<String, dynamic>;
    final List<dynamic> data = apiResponse['data'];
    return data.map((json) => HealthRecord.fromJson(json as Map<String, dynamic>)).toList();
  }

  Future<HealthRecord> getById(String patientId, String recordId) async {
    final response = await _apiService.get('${ApiConfig.patients}/$patientId/health-records/$recordId');
    final apiResponse = response.data as Map<String, dynamic>;
    return HealthRecord.fromJson(apiResponse['data'] as Map<String, dynamic>);
  }

  Future<HealthRecord> create(String patientId, Map<String, dynamic> data, List<File>? files) async {
    final formData = FormData.fromMap({
      'request': MultipartFile.fromString(
        jsonEncode(data),
        filename: 'request.json',
        contentType: DioMediaType.parse('application/json'),
      ),
    });

    if (files != null) {
      for (var file in files) {
        formData.files.add(MapEntry(
          'files',
          await MultipartFile.fromFile(file.path),
        ));
      }
    }

    final response = await _apiService.post(
      '${ApiConfig.patients}/$patientId/health-records',
      data: formData,
    );
    final apiResponse = response.data as Map<String, dynamic>;
    return HealthRecord.fromJson(apiResponse['data'] as Map<String, dynamic>);
  }

  Future<HealthRecord> update(String patientId, String recordId, Map<String, dynamic> data) async {
    final response = await _apiService.put(
      '${ApiConfig.patients}/$patientId/health-records/$recordId',
      data: data,
    );
    final apiResponse = response.data as Map<String, dynamic>;
    return HealthRecord.fromJson(apiResponse['data'] as Map<String, dynamic>);
  }

  Future<void> delete(String patientId, String recordId) async {
    await _apiService.delete('${ApiConfig.patients}/$patientId/health-records/$recordId');
  }

  String getFileUrl(String fileId) {
    return '${ApiConfig.baseUrl}/health-records/files/$fileId';
  }
}

final healthRecordServiceProvider = Provider<HealthRecordService>((ref) {
  final apiService = ref.read(apiServiceProvider);
  return HealthRecordService(apiService);
});
