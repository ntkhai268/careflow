import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../config/theme.dart';
import '../../models/app_notification.dart';
import '../../services/notification_service.dart';

class NotificationInboxScreen extends ConsumerWidget {
  const NotificationInboxScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final inbox = ref.watch(notificationInboxProvider);
    return Scaffold(
      appBar: AppBar(
        title: const Text('Thông báo'),
        actions: [
          inbox.maybeWhen(
            data: (items) => items.any((item) => !item.isRead)
                ? TextButton(
                  onPressed: () async {
                      try {
                        await ref
                            .read(notificationServiceProvider)
                            .markAllRead();
                        ref.invalidate(notificationInboxProvider);
                      } on NotificationServiceException catch (error) {
                        if (context.mounted) {
                          ScaffoldMessenger.of(context).showSnackBar(
                            SnackBar(content: Text(error.message)),
                          );
                        }
                      }
                    },
                    child: const Text('Đọc tất cả'),
                  )
                : const SizedBox.shrink(),
            orElse: () => const SizedBox.shrink(),
          ),
        ],
      ),
      body: inbox.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (error, _) => _ErrorState(
          message: error is NotificationServiceException
              ? error.message
              : 'Không thể tải thông báo. Vui lòng thử lại.',
          onRetry: () => ref.invalidate(notificationInboxProvider),
        ),
        data: (items) => items.isEmpty
            ? const Center(child: Text('Bạn chưa có thông báo nào.'))
            : RefreshIndicator(
                onRefresh: () async =>
                    ref.invalidate(notificationInboxProvider),
                child: _NotificationList(items: items),
              ),
      ),
    );
  }
}

class _NotificationList extends ConsumerWidget {
  const _NotificationList({required this.items});

  final List<AppNotification> items;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final newestFirst = [...items]
      ..sort((left, right) => right.createdAt.compareTo(left.createdAt));
    return ListView.separated(
      padding: const EdgeInsets.all(AppSpacing.base),
      itemCount: newestFirst.length,
      separatorBuilder: (_, _) => const SizedBox(height: AppSpacing.sm),
      itemBuilder: (context, index) {
        final item = newestFirst[index];
        return Card(
          color: item.isRead ? null : AppColors.primarySurface,
          child: ListTile(
            key: Key('server-notification-${item.id}'),
            onTap: () => _open(context, ref, item),
            leading: item.isRead
                ? const Icon(Icons.notifications_none_rounded)
                : const Icon(
                    Icons.notifications_active_rounded,
                    color: AppColors.primary,
                  ),
            title: Text(
              item.title,
              style: Theme.of(context).textTheme.titleMedium?.copyWith(
                fontWeight: item.isRead ? FontWeight.w500 : FontWeight.w700,
              ),
            ),
            subtitle: Padding(
              padding: const EdgeInsets.only(top: AppSpacing.xs),
              child: Text(
                '${item.body}\n${_date(item.createdAt)}',
                maxLines: 4,
                overflow: TextOverflow.ellipsis,
              ),
            ),
          ),
        );
      },
    );
  }

  Future<void> _open(
    BuildContext context,
    WidgetRef ref,
    AppNotification item,
  ) async {
    if (!item.isRead) {
      try {
        await ref.read(notificationServiceProvider).markRead(item.id);
        ref.invalidate(notificationInboxProvider);
      } catch (_) {
        // Navigation remains useful even if marking read is temporarily offline.
      }
    }
    if (!context.mounted) return;
    final resourceId = item.resourceId;
    switch (item.actionType) {
      case 'OPEN_APPOINTMENT':
        if (resourceId != null && resourceId.isNotEmpty) {
          context.go('/appointment/$resourceId');
          return;
        }
      case 'OPEN_TICKET':
      case 'OPEN_QUEUE':
        context.go('/?tab=1');
        return;
    }
  }

  String _date(DateTime value) =>
      '${value.day.toString().padLeft(2, '0')}/'
      '${value.month.toString().padLeft(2, '0')}/${value.year} '
      '${value.hour.toString().padLeft(2, '0')}:${value.minute.toString().padLeft(2, '0')}';
}

class _ErrorState extends StatelessWidget {
  const _ErrorState({required this.message, required this.onRetry});

  final String message;
  final VoidCallback onRetry;

  @override
  Widget build(BuildContext context) => Center(
    child: Padding(
      padding: const EdgeInsets.all(AppSpacing.xxl),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          const Icon(Icons.cloud_off_rounded, size: 48),
          const SizedBox(height: AppSpacing.md),
          Text(message, textAlign: TextAlign.center),
          const SizedBox(height: AppSpacing.md),
          TextButton(onPressed: onRetry, child: const Text('Thử lại')),
        ],
      ),
    ),
  );
}
