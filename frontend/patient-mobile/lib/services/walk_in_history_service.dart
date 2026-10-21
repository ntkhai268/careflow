import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../providers/auth_provider.dart';
import 'api_service.dart';
import '../config/api_config.dart';

class ClinicalVisit {
  final String id, patientId, patientName, department, diagnosis;
  final String? doctorName, completedAt;
  const ClinicalVisit({
    required this.id,
    required this.patientId,
    required this.patientName,
    required this.department,
    required this.diagnosis,
    this.doctorName,
    this.completedAt,
  });
  factory ClinicalVisit.fromJson(Map<String, dynamic> data, String name) =>
      ClinicalVisit(
        id: data['id'] as String,
        patientId: data['patientId'] as String,
        patientName: name,
        department:
            data['departmentName'] as String? ??
            data['departmentCode'] as String? ??
            'Chưa ghi nhận khoa',
        diagnosis: data['diagnosis'] as String? ?? 'Chưa ghi nhận chẩn đoán',
        doctorName: data['doctorName'] as String?,
        completedAt: data['completedAt'] as String?,
      );
}

class ClinicalVisitOutcome {
  final Map<String, dynamic> consultation;
  final List<Map<String, dynamic>> prescriptions, labOrders;
  const ClinicalVisitOutcome(
    this.consultation,
    this.prescriptions,
    this.labOrders,
  );
}

class WalkInHistoryService {
  final ApiService api;
  WalkInHistoryService(this.api);
  List<Map<String, dynamic>> rows(dynamic body) {
    final data = body is Map ? body['data'] : null;
    if (data is! List || data.any((row) => row is! Map)) {
      throw const FormatException('Máy chủ chưa trả đủ dữ liệu kết quả.');
    }
    return data.map((row) => Map<String, dynamic>.from(row as Map)).toList();
  }

  Future<List<ClinicalVisit>> list(String userId) async {
    final profiles = rows(
      (await api.get('/patients/user/$userId/profiles')).data,
    );
    final result = <ClinicalVisit>[];
    for (final profile in profiles) {
      if (profile['userId'] != userId || profile['id'] is! String) {
        throw const FormatException('Hồ sơ không thuộc tài khoản hiện tại.');
      }
      final visits = rows(
        (await api.get('/consultations/patient/${profile['id']}')).data,
      );
      for (final visit in visits) {
        if (visit['patientId'] != profile['id'] || visit['id'] is! String) {
          throw const FormatException('Ca khám không khớp hồ sơ.');
        }
        if (visit['status'] == 'COMPLETED' && visit['appointmentId'] == null) {
          result.add(
            ClinicalVisit.fromJson(
              visit,
              profile['fullName'] as String? ?? 'Chưa ghi nhận họ tên',
            ),
          );
        }
      }
    }
    result.sort((a, b) => (b.completedAt ?? '').compareTo(a.completedAt ?? ''));
    return result;
  }

  Future<ClinicalVisitOutcome> detail(String id) async {
    final response = await api.get('/consultations/$id');
    final data = response.data['data'];
    if (data is! Map ||
        data['id'] != id ||
        data['patientId'] is! String ||
        data['status'] != 'COMPLETED') {
      throw const FormatException(
        'Ca khám chưa hoàn tất hoặc không khớp yêu cầu.',
      );
    }
    final consultation = Map<String, dynamic>.from(data);
    final prescriptions = rows(
      (await api.get('/prescriptions/consultation/$id')).data,
    );
    final labs = rows(
      (await api.get(
        '${ApiConfig.labOrders}/patient/${consultation['patientId']}',
      )).data,
    );
    for (final record in prescriptions) {
      if (record['consultationId'] != id ||
          record['patientId'] != consultation['patientId']) {
        throw const FormatException('Toa thuốc không khớp ca khám.');
      }
    }
    if (labs.any(
      (record) => record['patientId'] != consultation['patientId'],
    )) {
      throw const FormatException('Kết quả xét nghiệm không khớp bệnh nhân.');
    }
    return ClinicalVisitOutcome(
      consultation,
      prescriptions
          .where((p) => const {'CONFIRMED', 'DISPENSED'}.contains(p['status']))
          .toList(),
      labs.where((record) => record['consultationId'] == id).toList(),
    );
  }
}

final walkInHistoryServiceProvider = Provider(
  (ref) => WalkInHistoryService(ref.watch(apiServiceProvider)),
);
final walkInHistoryProvider = FutureProvider.autoDispose<List<ClinicalVisit>>((
  ref,
) async {
  final auth = ref.watch(authProvider.select((a) => (a.status, a.userId)));
  if (auth.$1 != AuthStatus.authenticated || auth.$2 == null) return const [];
  return ref.watch(walkInHistoryServiceProvider).list(auth.$2!);
});
final clinicalVisitOutcomeProvider = FutureProvider.autoDispose
    .family<ClinicalVisitOutcome?, String>((ref, id) async {
      final auth = ref.watch(authProvider.select((a) => (a.status, a.userId)));
      if (auth.$1 != AuthStatus.authenticated || auth.$2 == null) return null;
      return ref.watch(walkInHistoryServiceProvider).detail(id);
    });
