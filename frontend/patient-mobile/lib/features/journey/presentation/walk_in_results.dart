import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../../../services/walk_in_history_service.dart';
import '../../../utils/api_error_message.dart';

class WalkInResults extends ConsumerWidget {
  const WalkInResults({super.key});
  @override
  Widget build(BuildContext context, WidgetRef ref) => ref
      .watch(walkInHistoryProvider)
      .when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (error, stack) => Center(
          child: Padding(
            padding: const EdgeInsets.all(24),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                Text(
                  ApiErrorMessage.from(
                    error,
                    fallback: 'Không tải được kết quả khám tại viện.',
                  ),
                ),
                TextButton(
                  onPressed: () => ref.invalidate(walkInHistoryProvider),
                  child: const Text('Thử lại'),
                ),
              ],
            ),
          ),
        ),
        data: (visits) => RefreshIndicator(
          onRefresh: () async {
            final _ = await ref.refresh(walkInHistoryProvider.future);
          },
          child: ListView(
            padding: const EdgeInsets.all(16),
            physics: const AlwaysScrollableScrollPhysics(),
            children: [
              if (visits.isEmpty)
                const Padding(
                  padding: EdgeInsets.all(24),
                  child: Text(
                    'Chưa có kết quả khám không hẹn đã hoàn tất. Hồ sơ tiếp nhận cần được liên kết với chính tài khoản của bạn.',
                  ),
                ),
              for (final visit in visits)
                Card(
                  child: ListTile(
                    title: Text(visit.patientName),
                    subtitle: Text(
                      '${visit.department}\n${visit.diagnosis}${visit.completedAt == null ? '' : '\n${visit.completedAt!.split('T').first}'}',
                    ),
                    isThreeLine: true,
                    trailing: const Icon(Icons.chevron_right),
                    onTap: () => context.push('/clinical-visits/${visit.id}'),
                  ),
                ),
            ],
          ),
        ),
      );
}

class ClinicalVisitOutcomeScreen extends ConsumerWidget {
  final String consultationId;
  const ClinicalVisitOutcomeScreen({super.key, required this.consultationId});
  @override
  Widget build(BuildContext context, WidgetRef ref) => Scaffold(
    appBar: AppBar(title: const Text('Kết quả lượt khám tại viện')),
    body: ref
        .watch(clinicalVisitOutcomeProvider(consultationId))
        .when(
          loading: () => const Center(child: CircularProgressIndicator()),
          error: (error, stack) => Center(
            child: Padding(
              padding: const EdgeInsets.all(24),
              child: Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  Text(
                    ApiErrorMessage.from(
                      error,
                      fallback:
                          'Không tải được kết quả. Quyền hồ sơ có thể đã thay đổi.',
                    ),
                  ),
                  TextButton(
                    onPressed: () => ref.invalidate(
                      clinicalVisitOutcomeProvider(consultationId),
                    ),
                    child: const Text('Thử lại'),
                  ),
                ],
              ),
            ),
          ),
          data: (outcome) {
            if (outcome == null) {
              return const Center(child: Text('Cần đăng nhập để xem kết quả.'));
            }
            final visit = outcome.consultation;
            return RefreshIndicator(
              onRefresh: () async {
                final _ = await ref.refresh(
                  clinicalVisitOutcomeProvider(consultationId).future,
                );
              },
              child: ListView(
                padding: const EdgeInsets.all(16),
                children: [
                  Card(
                    child: Padding(
                      padding: const EdgeInsets.all(16),
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            visit['patientName'] as String? ??
                                'Hồ sơ đã liên kết',
                            style: Theme.of(context).textTheme.titleLarge,
                          ),
                          Text(
                            visit['departmentName'] as String? ??
                                visit['departmentCode'] as String? ??
                                'Chưa ghi nhận khoa',
                          ),
                          Text(
                            'Bác sĩ: ${visit['doctorName'] ?? 'Chưa ghi nhận'}',
                          ),
                          const SizedBox(height: 12),
                          Text(
                            'Chẩn đoán: ${visit['diagnosis'] ?? 'Chưa ghi nhận'}',
                          ),
                          if (visit['clinicalNotes'] is String)
                            Text(visit['clinicalNotes'] as String),
                          for (final field in [
                            ('temperature', 'Nhiệt độ'),
                            ('bloodPressure', 'Huyết áp'),
                            ('heartRate', 'Mạch'),
                            ('spo2', 'SpO₂'),
                          ])
                            if (visit[field.$1] != null)
                              Text('${field.$2}: ${visit[field.$1]}'),
                        ],
                      ),
                    ),
                  ),
                  const Padding(
                    padding: EdgeInsets.all(8),
                    child: Text('Toa thuốc đã ký'),
                  ),
                  if (outcome.prescriptions.isEmpty)
                    const Padding(
                      padding: EdgeInsets.all(8),
                      child: Text('Không có toa thuốc đã phát hành.'),
                    ),
                  for (final prescription in outcome.prescriptions)
                    Card(
                      child: Padding(
                        padding: const EdgeInsets.all(16),
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text(
                              prescription['status'] == 'DISPENSED'
                                  ? 'Đã cấp thuốc'
                                  : 'Đã ký toa',
                            ),
                            for (final item
                                in (prescription['items'] as List? ?? const []))
                              if (item is Map)
                                Padding(
                                  padding: const EdgeInsets.symmetric(
                                    vertical: 6,
                                  ),
                                  child: Text(
                                    '${item['medicineName'] ?? ''}\n${item['dosage'] ?? ''} · ${item['frequency'] ?? ''} · ${item['duration'] ?? ''} ngày\n${item['timing'] ?? ''}',
                                  ),
                                ),
                            if (prescription['notes'] is String)
                              Text(prescription['notes'] as String),
                            if (prescription['followUpDate'] != null)
                              Text(
                                prescription['followUpStatus'] == 'SCHEDULED'
                                    ? 'Đã hẹn tái khám: ${prescription['followUpDate']} · ${prescription['followUpTimeSlot'] ?? ''}'
                                    : 'Đề nghị tái khám: ${prescription['followUpDate']}. Chưa có xác nhận lịch; liên hệ cơ sở y tế.',
                              ),
                          ],
                        ),
                      ),
                    ),
                  const Padding(
                    padding: EdgeInsets.all(8),
                    child: Text('Kết quả xét nghiệm'),
                  ),
                  if (outcome.labOrders.isEmpty)
                    const Padding(
                      padding: EdgeInsets.all(8),
                      child: Text('Không có chỉ định xét nghiệm.'),
                    ),
                  for (final order in outcome.labOrders)
                    Card(
                      child: Padding(
                        padding: const EdgeInsets.all(16),
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text(
                              order['status'] == 'REVIEWED'
                                  ? 'Bác sĩ đã đọc kết quả'
                                  : order['status'] == 'RESULT_AVAILABLE'
                                  ? 'Đã có kết quả'
                                  : 'Trạng thái: ${order['status']}',
                            ),
                            for (final item
                                in (order['items'] as List? ?? const []))
                              if (item is Map)
                                Padding(
                                  padding: const EdgeInsets.symmetric(
                                    vertical: 6,
                                  ),
                                  child: Text(
                                    '${item['serviceName'] ?? ''}: ${item['resultValue'] ?? 'Chưa có kết quả'} ${item['unit'] ?? ''}${item['referenceRange'] == null ? '' : '\nKhoảng tham chiếu: ${item['referenceRange']}'}',
                                  ),
                                ),
                          ],
                        ),
                      ),
                    ),
                ],
              ),
            );
          },
        ),
  );
}
