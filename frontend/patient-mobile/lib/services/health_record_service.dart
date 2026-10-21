import 'dart:io';
import 'dart:convert';
import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../models/health_record.dart';
import '../config/api_config.dart';
import 'api_service.dart';
import 'medical_file_cache.dart';

class HealthRecordService {
  final ApiService _apiService;

  HealthRecordService(this._apiService);

  Future<List<HealthRecord>> getAll(String patientId) async {
    final response = await _apiService.get(
      '${ApiConfig.patients}/$patientId/health-records',
    );
    final apiResponse = response.data as Map<String, dynamic>;
    final List<dynamic> data = apiResponse['data'];
    return data
        .map((json) => HealthRecord.fromJson(json as Map<String, dynamic>))
        .toList();
  }

  Future<HealthRecord> getById(String patientId, String recordId) async {
    final response = await _apiService.get(
      '${ApiConfig.patients}/$patientId/health-records/$recordId',
    );
    final apiResponse = response.data as Map<String, dynamic>;
    return HealthRecord.fromJson(apiResponse['data'] as Map<String, dynamic>);
  }

  Future<HealthRecord> create(
    String patientId,
    Map<String, dynamic> data,
    List<File>? files,
  ) async {
    return createWithProgress(patientId, data, files, cancel: CancelToken());
  }

  Future<HealthRecord> createWithProgress(
    String patientId,
    Map<String, dynamic> data,
    List<File>? files, {
    required CancelToken cancel,
    void Function(int, int)? progress,
  }) async {
    final cache = MedicalFileCache.instance,
        epoch = MedicalFileCache.instance.epoch;
    final selected = files ?? const <File>[];
    if (selected.length > 5) {
      throw const FormatException('Mỗi hồ sơ tối đa 5 tệp.');
    }
    cache.register(cancel);
    try {
      var total = 0;
      final formData = FormData.fromMap({
        'request': MultipartFile.fromString(
          jsonEncode(data),
          filename: 'request.json',
          contentType: DioMediaType.parse('application/json'),
        ),
      });
      for (final file in selected) {
        final extension = file.path.split('.').last.toLowerCase();
        if (!const {'pdf', 'jpg', 'jpeg', 'png'}.contains(extension)) {
          throw const FormatException('Chỉ hỗ trợ PDF, JPEG và PNG.');
        }
        final size = await file.length();
        total += size;
        if (size <= 0 || size > 10 * 1024 * 1024 || total > 50 * 1024 * 1024) {
          throw const FormatException('Mỗi tệp tối đa 10MB, tổng tối đa 50MB.');
        }
        formData.files.add(
          MapEntry(
            'files',
            await MultipartFile.fromFile(
              file.path,
              contentType: DioMediaType.parse(
                extension == 'pdf'
                    ? 'application/pdf'
                    : extension == 'png'
                    ? 'image/png'
                    : 'image/jpeg',
              ),
            ),
          ),
        );
      }
      if (cancel.isCancelled || epoch != cache.epoch) {
        throw const FormatException('Phiên đã thay đổi hoặc đã hủy gửi.');
      }
      final response = await _apiService.upload(
        '${ApiConfig.patients}/$patientId/health-records',
        data: formData,
        cancelToken: cancel,
        onSendProgress: progress,
      );
      if (cancel.isCancelled || epoch != cache.epoch) {
        throw const FormatException(
          'Phiên đã thay đổi. Tải lại danh sách hồ sơ để kiểm tra kết quả.',
        );
      }
      final result = HealthRecord.fromJson(
        Map<String, dynamic>.from(response.data['data'] as Map),
      );
      if (result.patientId != patientId) {
        throw const FormatException('Hồ sơ trả về không khớp bệnh nhân.');
      }
      return result;
    } finally {
      cache.unregister(cancel);
    }
  }

  Future<HealthRecord> update(
    String patientId,
    String recordId,
    Map<String, dynamic> data,
  ) async {
    final response = await _apiService.put(
      '${ApiConfig.patients}/$patientId/health-records/$recordId',
      data: data,
    );
    final apiResponse = response.data as Map<String, dynamic>;
    return HealthRecord.fromJson(apiResponse['data'] as Map<String, dynamic>);
  }

  Future<void> delete(String patientId, String recordId) async {
    await _apiService.delete(
      '${ApiConfig.patients}/$patientId/health-records/$recordId',
    );
  }

  String getFileUrl(String patientId, String fileId) {
    return '${ApiConfig.baseUrl}${ApiConfig.patients}/$patientId/health-records/files/$fileId';
  }
}

final healthRecordServiceProvider = Provider<HealthRecordService>((ref) {
  final apiService = ref.read(apiServiceProvider);
  return HealthRecordService(apiService);
});
