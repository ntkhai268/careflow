import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'api_service.dart';

class EkycResult {
  const EkycResult({
    required this.id,
    required this.status,
    this.maskedDocumentNumber,
  });
  final String id, status;
  final String? maskedDocumentNumber;
  factory EkycResult.fromJson(Map<String, dynamic> json) => EkycResult(
    id: json['id'] as String,
    status: json['status'] as String,
    maskedDocumentNumber: json['maskedDocumentNumber'] as String?,
  );
  String get message => switch (status) {
    'PENDING_REVIEW' => 'Chờ đối chiếu giấy tờ gốc tại cơ sở y tế',
    'CONFIRMED' => 'Đã đối chiếu trực tiếp tại cơ sở y tế',
    'REJECTED' => 'Thông tin chưa khớp khi đối chiếu trực tiếp',
    'WITHDRAWN' => 'Đã hủy yêu cầu và xóa thông tin chờ đối chiếu',
    'EXPIRED' => 'Yêu cầu đã hết hạn. Vui lòng gửi lại khi cần đối chiếu.',
    _ => 'Chưa có kết quả đối chiếu',
  };
}

class EkycStatus {
  const EkycStatus({required this.available, this.latest});
  final bool available;
  final EkycResult? latest;
}

class EkycService {
  EkycService(this.api);
  final ApiService api;
  Future<EkycStatus> status() async {
    final response = await api.get('/auth/identity-document/status');
    final data = Map<String, dynamic>.from(response.data['data'] as Map);
    return EkycStatus(
      available: data['available'] == true,
      latest: data['latest'] == null
          ? null
          : EkycResult.fromJson(
              Map<String, dynamic>.from(data['latest'] as Map),
            ),
    );
  }

  Future<EkycResult> submit({
    required String requestKey,
    required String documentNumber,
    required String fullName,
    required String birthDate,
  }) async {
    final response = await api.post(
      '/auth/identity-document/requests',
      data: {
        'documentNumber': documentNumber,
        'fullName': fullName,
        'birthDate': birthDate,
        'consent': true,
      },
      options: Options(headers: {'Idempotency-Key': requestKey}),
    );
    return EkycResult.fromJson(
      Map<String, dynamic>.from(response.data['data'] as Map),
    );
  }

  Future<void> withdraw(String id) async {
    await api.post('/auth/identity-document/requests/$id/withdraw', data: {});
  }
}

final ekycServiceProvider = Provider<EkycService>(
  (ref) => EkycService(ref.read(apiServiceProvider)),
);
