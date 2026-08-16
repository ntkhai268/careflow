import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../config/api_config.dart';
import 'api_service.dart';

abstract interface class LabGateway {
  Future<List<Map<String, dynamic>>> getByConsultation(String consultationId);

  Future<List<Map<String, dynamic>>> getByPatient(String patientId);
}

class LabService implements LabGateway {
  LabService(this._api);

  final ApiService _api;

  @override
  Future<List<Map<String, dynamic>>> getByConsultation(
    String consultationId,
  ) async {
    final response = await _api.get(
      '${ApiConfig.labOrders}/consultation/$consultationId',
    );
    return _dataList(response.data);
  }

  @override
  Future<List<Map<String, dynamic>>> getByPatient(String patientId) async {
    final response = await _api.get(
      '${ApiConfig.labOrders}/patient/$patientId',
    );
    return _dataList(response.data);
  }
}

final labServiceProvider = Provider<LabService>(
  (ref) => LabService(ref.watch(apiServiceProvider)),
);

List<Map<String, dynamic>> _dataList(Object? responseData) {
  final data = responseData is Map ? responseData['data'] : responseData;
  if (data is! List) return const [];
  return data
      .whereType<Map>()
      .map((item) => Map<String, dynamic>.from(item))
      .toList(growable: false);
}
