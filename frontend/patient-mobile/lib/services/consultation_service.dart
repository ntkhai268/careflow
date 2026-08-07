import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../config/api_config.dart';
import 'api_service.dart';

abstract interface class ConsultationGateway {
  Future<List<Map<String, dynamic>>> getByAppointment(String appointmentId);
}

class ConsultationService implements ConsultationGateway {
  ConsultationService(this._api);

  final ApiService _api;

  @override
  Future<List<Map<String, dynamic>>> getByAppointment(
    String appointmentId,
  ) async {
    final response = await _api.get(
      '${ApiConfig.consultations}/appointment/$appointmentId',
    );
    return _dataList(response.data);
  }
}

final consultationServiceProvider = Provider<ConsultationService>(
  (ref) => ConsultationService(ref.watch(apiServiceProvider)),
);

List<Map<String, dynamic>> _dataList(Object? responseData) {
  final data = responseData is Map ? responseData['data'] : responseData;
  if (data is! List) return const [];
  return data
      .whereType<Map>()
      .map((item) => Map<String, dynamic>.from(item))
      .toList(growable: false);
}
