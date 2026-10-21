import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../services/notification_resource_service.dart';
import '../../services/push_notification_service.dart';
import '../../services/notification_channel.dart';
import '../../utils/api_error_message.dart';

class NotificationResourceScreen extends ConsumerWidget {
  const NotificationResourceScreen({super.key, required this.notificationId});
  final String notificationId;
  static const labels = {
    'WAITING': 'Chờ tiếp nhận đúng ngày khám',
    'CHECKED_IN': 'Đã tiếp nhận, đang chờ gọi',
    'QUEUED': 'Đang chờ phục vụ',
    'CALLED': 'Đã được gọi',
    'IN_PROGRESS': 'Đang phục vụ',
    'MISSED': 'Đã lỡ lượt',
    'COMPLETED': 'Đã hoàn tất',
    'CANCELLED': 'Đã hủy',
    'CONFIRMED': 'Đã xác nhận',
    'DISPENSED': 'Đã cấp thuốc',
    'DRAFT': 'Bản nháp',
    'REVIEWED': 'Bác sĩ đã đọc kết quả',
    'RESULT_AVAILABLE': 'Đã có kết quả',
    'AWAITING_CLS': 'Chờ cận lâm sàng',
    'AWAITING_REVIEW': 'Chờ bác sĩ đọc kết quả',
  };
  @override
  Widget build(BuildContext context, WidgetRef ref) => Scaffold(
    appBar: AppBar(
      title: const Text('Chi tiết thông báo'),
      actions: [
        IconButton(
          tooltip: 'Tải lại',
          onPressed: () =>
              ref.invalidate(notificationResourceProvider(notificationId)),
          icon: const Icon(Icons.refresh),
        ),
      ],
    ),
    body: ref
        .watch(notificationResourceProvider(notificationId))
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
                          'Không mở được tài nguyên. Quyền truy cập hoặc trạng thái có thể đã thay đổi.',
                    ),
                  ),
                  TextButton(
                    onPressed: () => ref.invalidate(
                      notificationResourceProvider(notificationId),
                    ),
                    child: const Text('Thử lại'),
                  ),
                ],
              ),
            ),
          ),
          data: (resource) {
            if (resource == null) {
              return const Center(
                child: Text('Đăng nhập để xem thông báo của bạn.'),
              );
            }
            final data = resource.data;
            final status = data['status']?.toString();
            return RefreshIndicator(
              onRefresh: () async {
                final _ = await ref.refresh(
                  notificationResourceProvider(notificationId).future,
                );
              },
              child: ListView(
                padding: const EdgeInsets.all(20),
                physics: const AlwaysScrollableScrollPhysics(),
                children: [
                  Text(
                    resource.notification['title']?.toString() ?? 'Thông báo',
                    style: Theme.of(context).textTheme.headlineSmall,
                  ),
                  const SizedBox(height: 12),
                  Text(resource.patient['fullName']?.toString() ?? ''),
                  if (status != null) Text(labels[status] ?? status),
                  const Divider(),
                  if (resource.kind == 'appointment') ...[
                    Text('Ngày khám: ${data['appointmentDate'] ?? ''}'),
                    Text('Ca khám: ${data['timeSlot'] ?? ''}'),
                    Text(
                      'Phòng: ${data['roomDisplayName'] ?? data['roomId'] ?? ''}',
                    ),
                    Text('Bác sĩ: ${data['doctorName'] ?? 'Chưa ghi nhận'}'),
                  ],
                  if (resource.kind == 'queue') ...[
                    Text(
                      'Số thứ tự: ${data['queueNumber'] ?? ''}',
                      style: Theme.of(context).textTheme.headlineMedium,
                    ),
                    Text('Phòng/điểm phục vụ: ${data['roomId'] ?? ''}'),
                    if (data['phase'] == 'RESULT_REVIEW')
                      const Text('Lượt quay lại đọc kết quả'),
                  ],
                  if (resource.kind == 'consultation') ...[
                    Text('Bác sĩ: ${data['doctorName'] ?? 'Chưa ghi nhận'}'),
                    if (data['diagnosis'] != null)
                      Text('Chẩn đoán: ${data['diagnosis']}'),
                    if (data['clinicalNotes'] != null)
                      Text(data['clinicalNotes'].toString()),
                  ],
                  if (resource.kind == 'lab')
                    for (final item in data['items'] as List? ?? const [])
                      if (item is Map) ...[
                        Text(
                          item['serviceName']?.toString() ?? '',
                          style: Theme.of(context).textTheme.titleMedium,
                        ),
                        Text(
                          'Kết quả: ${item['resultValue'] ?? 'Chưa phát hành'} ${item['unit'] ?? ''}',
                        ),
                        if (item['referenceRange'] != null)
                          Text('Khoảng tham chiếu: ${item['referenceRange']}'),
                        const SizedBox(height: 12),
                      ],
                  if (resource.kind == 'prescription') ...[
                    for (final item in data['items'] as List? ?? const [])
                      if (item is Map) ...[
                        Text(
                          item['medicineName']?.toString() ?? '',
                          style: Theme.of(context).textTheme.titleMedium,
                        ),
                        Text(
                          '${item['dosage'] ?? ''} · ${item['frequency'] ?? ''} · ${item['timing'] ?? ''}',
                        ),
                        if (item['quantity'] != null)
                          Text(
                            'Số lượng: ${item['quantity']} ${item['unit'] ?? ''}',
                          ),
                        if (item['duration'] != null)
                          Text('Số ngày: ${item['duration']}'),
                        const SizedBox(height: 12),
                      ],
                    if (data['notes'] != null) Text(data['notes'].toString()),
                  ],
                ],
              ),
            );
          },
        ),
  );
}

class NotificationConnectionTile extends ConsumerWidget {
  const NotificationConnectionTile({super.key});
  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final push = ref.watch(pushNotificationServiceProvider),
        channel = ref.watch(notificationChannelProvider);
    return StreamBuilder<NotificationConnection>(
      stream: channel.states,
      initialData: channel.state,
      builder: (context, state) => ListTile(
        dense: true,
        leading: Icon(
          state.data == NotificationConnection.online
              ? Icons.cloud_done_outlined
              : Icons.sync,
        ),
        title: Text(
          state.data == NotificationConnection.online
              ? 'Đang nhận cập nhật trực tiếp'
              : state.data == NotificationConnection.retrying
              ? 'Đang kết nối lại; dữ liệu sẽ được tải bù'
              : 'Thông báo trong ứng dụng',
        ),
        subtitle: ValueListenableBuilder<String>(
          valueListenable: push.statusListenable,
          builder: (_, value, _) => Text(value),
        ),
        trailing: TextButton(
          onPressed: push.enable,
          child: const Text('Bật push'),
        ),
      ),
    );
  }
}
