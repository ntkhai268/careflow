import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../config/theme.dart';
import '../application/journey_providers.dart';
import '../domain/journey_models.dart';
import 'journey_date_formatter.dart';

class JourneyNotificationScreen extends ConsumerWidget {
  const JourneyNotificationScreen({super.key, required this.appointmentId});

  final String appointmentId;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final journey = ref.watch(journeyForAppointmentProvider(appointmentId));
    return Scaffold(
      appBar: AppBar(title: const Text('Thông báo')),
      body: journey.when(
        loading: () => const Center(child: Text('Đang tải thông báo...')),
        error: (_, _) => const Center(child: Text('Không thể tải thông báo.')),
        data: (value) => value == null
            ? const Center(child: Text('Bạn chưa có thông báo nào.'))
            : _NotificationList(notifications: value.notifications),
      ),
    );
  }
}

class _NotificationList extends ConsumerWidget {
  const _NotificationList({required this.notifications});

  final List<PatientNotification> notifications;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final newestFirst = [...notifications]
      ..sort((left, right) => right.createdAt.compareTo(left.createdAt));
    if (newestFirst.isEmpty) {
      return const Center(child: Text('Bạn chưa có thông báo nào.'));
    }
    return ListView.separated(
      padding: const EdgeInsets.all(AppSpacing.base),
      itemCount: newestFirst.length,
      separatorBuilder: (_, _) => const SizedBox(height: AppSpacing.sm),
      itemBuilder: (context, index) => _NotificationTile(
        notification: newestFirst[index],
        onTap: () => ref
            .read(journeyControllerProvider.notifier)
            .markNotificationRead(newestFirst[index].id),
      ),
    );
  }
}

class _NotificationTile extends StatelessWidget {
  const _NotificationTile({required this.notification, required this.onTap});

  final PatientNotification notification;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) => Card(
    color: notification.isRead ? null : AppColors.primarySurface,
    child: ListTile(
      key: Key('notification-${notification.id}'),
      onTap: notification.isRead ? null : onTap,
      contentPadding: const EdgeInsets.all(AppSpacing.base),
      leading: notification.isRead
          ? const Icon(Icons.notifications_none_rounded, color: AppColors.info)
          : Container(
              key: Key('unread-marker-${notification.id}'),
              width: 12,
              height: 12,
              decoration: const BoxDecoration(
                color: AppColors.primary,
                shape: BoxShape.circle,
              ),
            ),
      title: Text(
        notification.title,
        style: Theme.of(context).textTheme.titleMedium?.copyWith(
          fontWeight: notification.isRead ? FontWeight.w500 : FontWeight.w700,
        ),
      ),
      subtitle: Padding(
        padding: const EdgeInsets.only(top: AppSpacing.xs),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(notification.body),
            const SizedBox(height: AppSpacing.xs),
            Text(
              formatJourneyDateTime(
                notification.createdAt,
                'HH:mm • dd/MM/yyyy',
              ),
              style: Theme.of(context).textTheme.bodySmall,
            ),
          ],
        ),
      ),
    ),
  );
}
