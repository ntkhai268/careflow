import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../config/theme.dart';
import '../application/journey_providers.dart';
import '../domain/journey_models.dart';
import 'journey_date_formatter.dart';

class JourneyNotificationScreen extends ConsumerWidget {
  const JourneyNotificationScreen({
    super.key,
    required this.appointmentId,
    this.useInbox = false,
  });

  final String appointmentId;
  final bool useInbox;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    if (useInbox) {
      return _NotificationScaffold(
        notifications: ref.watch(patientNotificationInboxProvider),
        appointmentId: appointmentId,
        onRefresh: () => _refreshInbox(context, ref),
        onMarkRead: (notification) => ref
            .read(patientNotificationInboxProvider.notifier)
            .markRead(notification.id),
      );
    }

    final journey = ref.watch(journeyForAppointmentProvider(appointmentId));
    return _NotificationScaffold(
      notifications: journey.whenData(
        (value) => value?.notifications ?? const <PatientNotification>[],
      ),
      appointmentId: appointmentId,
      onRefresh: () => _refreshJourney(context, ref),
    );
  }

  Future<void> _refreshInbox(BuildContext context, WidgetRef ref) async {
    final refreshed = await ref
        .read(patientNotificationInboxProvider.notifier)
        .refresh();
    if (!refreshed && context.mounted) _showRefreshError(context);
  }

  Future<void> _refreshJourney(BuildContext context, WidgetRef ref) async {
    final refreshed = await ref
        .read(journeyControllerProvider.notifier)
        .refreshCurrentJourney();
    if (!refreshed && context.mounted) _showRefreshError(context);
  }

  void _showRefreshError(BuildContext context) {
    ScaffoldMessenger.of(context)
      ..hideCurrentSnackBar()
      ..showSnackBar(
        const SnackBar(
          content: Text('Không thể tải thông báo mới. Vui lòng thử lại sau.'),
        ),
      );
  }
}

class _NotificationScaffold extends StatelessWidget {
  const _NotificationScaffold({
    required this.notifications,
    required this.appointmentId,
    required this.onRefresh,
    this.onMarkRead,
  });

  final AsyncValue<List<PatientNotification>> notifications;
  final String appointmentId;
  final Future<void> Function() onRefresh;
  final Future<bool> Function(PatientNotification notification)? onMarkRead;

  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(
      title: const Text('Thông báo'),
      actions: [
        IconButton(
          tooltip: 'Tải lại thông báo',
          onPressed: notifications.isLoading ? null : onRefresh,
          icon: const Icon(Icons.refresh_rounded),
        ),
      ],
    ),
    body: RefreshIndicator(
      onRefresh: onRefresh,
      child: notifications.when(
        loading: () =>
            const _RefreshableMessage(child: CircularProgressIndicator()),
        error: (_, _) => const _RefreshableMessage(
          child: Text('Không thể tải thông báo. Kéo xuống để thử lại.'),
        ),
        data: (value) => value.isEmpty
            ? const _RefreshableMessage(
                child: Text('Bạn chưa có thông báo nào.'),
              )
            : _NotificationList(
                notifications: value,
                appointmentId: appointmentId,
                onMarkRead: onMarkRead,
              ),
      ),
    ),
  );
}

class _RefreshableMessage extends StatelessWidget {
  const _RefreshableMessage({required this.child});

  final Widget child;

  @override
  Widget build(BuildContext context) => ListView(
    physics: const AlwaysScrollableScrollPhysics(),
    children: [SizedBox(height: 280, child: Center(child: child))],
  );
}

class _NotificationList extends ConsumerWidget {
  const _NotificationList({
    required this.notifications,
    required this.appointmentId,
    this.onMarkRead,
  });

  final List<PatientNotification> notifications;
  final String appointmentId;
  final Future<bool> Function(PatientNotification notification)? onMarkRead;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final newestFirst = [...notifications]
      ..sort((left, right) => right.createdAt.compareTo(left.createdAt));
    return ListView.separated(
      padding: const EdgeInsets.all(AppSpacing.base),
      physics: const AlwaysScrollableScrollPhysics(),
      itemCount: newestFirst.length,
      separatorBuilder: (_, _) => const SizedBox(height: AppSpacing.sm),
      itemBuilder: (context, index) {
        final notification = newestFirst[index];
        return _NotificationTile(
          notification: notification,
          onTap: () => _openNotification(context, ref, notification),
        );
      },
    );
  }

  Future<void> _openNotification(
    BuildContext context,
    WidgetRef ref,
    PatientNotification notification,
  ) async {
    if (!notification.isRead) {
      if (onMarkRead != null) {
        await onMarkRead!(notification);
      } else {
        await ref
            .read(journeyControllerProvider.notifier)
            .markNotificationRead(notification.id);
      }
    }
    if (!context.mounted) return;
    context.push(
      notificationDestination(
        notification: notification,
        appointmentId: appointmentId,
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
      onTap: onTap,
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
      trailing: const Icon(Icons.chevron_right_rounded),
    ),
  );
}

String notificationDestination({
  required PatientNotification notification,
  required String appointmentId,
}) {
  final actionType = notification.actionType?.trim().toUpperCase();
  if (actionType == 'OPEN_APPOINTMENT') {
    final resourceId = notification.resourceId;
    if (resourceId != null && resourceId.isNotEmpty) {
      return '/appointment/$resourceId';
    }
  }
  if (appointmentId.isEmpty) return '/';

  final base = '/journey/$appointmentId';
  switch (actionType) {
    case 'OPEN_APPOINTMENT':
      final resourceId = notification.resourceId;
      return resourceId == null || resourceId.isEmpty
          ? base
          : '/appointment/$resourceId';
    case 'OPEN_TICKET':
      return '$base/ticket';
    case 'OPEN_QUEUE':
      return '$base/queue';
    case 'OPEN_LAB_ORDER':
    case 'OPEN_LAB_RESULT':
      return '$base/laboratory';
    case 'OPEN_RESULT_REVIEW':
      return '$base/result-review';
    case 'OPEN_PRESCRIPTION':
      return '$base/outcome';
  }

  // Older locally-created notifications do not have an action object yet.
  // Keep them useful by inferring the safest journey destination from copy.
  final content = '${notification.title} ${notification.body}'.toLowerCase();
  if (content.contains('phiếu')) return '$base/ticket';
  if (content.contains('hàng đợi') || content.contains('gọi')) {
    return '$base/queue';
  }
  if (content.contains('xét nghiệm') || content.contains('cận lâm sàng')) {
    return '$base/laboratory';
  }
  if (content.contains('kết quả')) return '$base/result-review';
  if (content.contains('toa thuốc') || content.contains('đơn thuốc')) {
    return '$base/outcome';
  }
  return base;
}
