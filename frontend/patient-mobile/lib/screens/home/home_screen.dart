import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../config/theme.dart';
import '../../features/journey/application/journey_providers.dart';
import '../../features/journey/domain/journey_models.dart';
import '../../features/journey/presentation/widgets/journey_status_card.dart';
import '../../providers/auth_provider.dart';
import '../../widgets/quick_action_card.dart';

/// Task-first landing page for the patient journey.
class HomeScreen extends ConsumerWidget {
  const HomeScreen({
    super.key,
    this.onAppointmentsTap,
    this.onRecordsTap,
    this.onNotificationTap,
  });

  final VoidCallback? onAppointmentsTap;
  final VoidCallback? onRecordsTap;
  final VoidCallback? onNotificationTap;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final authState = ref.watch(authProvider);
    final activeJourney = ref.watch(activeJourneyProvider);
    final unreadCount = ref.watch(unreadJourneyNotificationCountProvider);

    return Scaffold(
      body: SafeArea(
        bottom: false,
        child: CustomScrollView(
          slivers: [
            SliverToBoxAdapter(
              child: _Header(
                userName: authState.fullName ?? 'Bạn',
                unreadCount: unreadCount,
                onNotificationTap: onNotificationTap,
              ),
            ),
            SliverPadding(
              padding: const EdgeInsets.fromLTRB(16, 8, 16, 32),
              sliver: SliverList.list(
                children: [
                  _JourneySection(journey: activeJourney),
                  const SizedBox(height: AppSpacing.xl),
                  SizedBox(
                    width: double.infinity,
                    child: ElevatedButton.icon(
                      key: const Key('home-quick-action-0'),
                      onPressed: () => context.push('/booking/step1'),
                      icon: const Icon(Icons.add_circle_outline_rounded),
                      label: const Text('Đặt lịch khám'),
                    ),
                  ),
                  const SizedBox(height: AppSpacing.xl),
                  Text(
                    'Truy cập nhanh',
                    style: Theme.of(context).textTheme.titleLarge,
                  ),
                  const SizedBox(height: AppSpacing.md),
                  QuickActionCard(
                    key: const Key('home-quick-action-1'),
                    icon: Icons.event_note_rounded,
                    label: 'Lịch khám và phiếu khám',
                    onTap: onAppointmentsTap,
                  ),
                  const SizedBox(height: AppSpacing.sm),
                  QuickActionCard(
                    key: const Key('home-quick-action-2'),
                    icon: Icons.folder_shared_rounded,
                    label: 'Hồ sơ sức khỏe',
                    iconColor: AppColors.accent,
                    backgroundColor: AppColors.accentLight,
                    onTap: onRecordsTap,
                  ),
                  const SizedBox(height: AppSpacing.sm),
                  QuickActionCard(
                    key: const Key('home-quick-action-3'),
                    icon: Icons.notifications_rounded,
                    label: unreadCount == 0
                        ? 'Thông báo'
                        : 'Thông báo · $unreadCount chưa đọc',
                    showBadge: unreadCount > 0,
                    badgeText: unreadCount > 99 ? '99+' : '$unreadCount',
                    onTap: onNotificationTap,
                  ),
                  const SizedBox(height: AppSpacing.xl),
                  const _PreparationGuide(),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _Header extends StatelessWidget {
  const _Header({
    required this.userName,
    required this.unreadCount,
    required this.onNotificationTap,
  });

  final String userName;
  final int unreadCount;
  final VoidCallback? onNotificationTap;

  @override
  Widget build(BuildContext context) => Padding(
    padding: const EdgeInsets.fromLTRB(16, 12, 8, 16),
    child: Row(
      children: [
        Container(
          width: 48,
          height: 48,
          decoration: BoxDecoration(
            color: AppColors.primarySurface,
            borderRadius: BorderRadius.circular(AppRadius.md),
          ),
          child: const Icon(
            Icons.local_hospital_rounded,
            color: AppColors.primary,
          ),
        ),
        const SizedBox(width: AppSpacing.md),
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text('Xin chào,', style: Theme.of(context).textTheme.bodyMedium),
              Text(
                userName,
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                style: Theme.of(context).textTheme.titleLarge,
              ),
            ],
          ),
        ),
        Semantics(
          label: unreadCount == 0
              ? 'Thông báo, không có thông báo chưa đọc'
              : 'Thông báo, $unreadCount thông báo chưa đọc',
          button: true,
          child: IconButton(
            key: const Key('home-notification-button'),
            tooltip: 'Thông báo',
            onPressed: onNotificationTap,
            icon: Stack(
              clipBehavior: Clip.none,
              children: [
                const Icon(Icons.notifications_outlined),
                if (unreadCount > 0)
                  Positioned(
                    right: -8,
                    top: -8,
                    child: Container(
                      constraints: const BoxConstraints(
                        minWidth: 18,
                        minHeight: 18,
                      ),
                      padding: const EdgeInsets.symmetric(horizontal: 4),
                      decoration: BoxDecoration(
                        color: AppColors.error,
                        borderRadius: BorderRadius.circular(AppRadius.full),
                        border: Border.all(color: AppColors.surface),
                      ),
                      alignment: Alignment.center,
                      child: Text(
                        unreadCount > 99 ? '99+' : '$unreadCount',
                        style: const TextStyle(
                          color: Colors.white,
                          fontSize: 9,
                          fontWeight: FontWeight.w600,
                        ),
                      ),
                    ),
                  ),
              ],
            ),
          ),
        ),
      ],
    ),
  );
}

class _JourneySection extends StatelessWidget {
  const _JourneySection({required this.journey});

  final PatientJourney? journey;

  @override
  Widget build(BuildContext context) => Column(
    crossAxisAlignment: CrossAxisAlignment.start,
    children: [
      Text(
        journey == null ? 'Hôm nay của bạn' : 'Hành trình đang diễn ra',
        style: Theme.of(context).textTheme.titleLarge,
      ),
      const SizedBox(height: AppSpacing.md),
      if (journey == null)
        const _NoActiveJourney()
      else
        Semantics(
          button: true,
          label: 'Mở bước hiện tại của hành trình khám',
          child: InkWell(
            key: const Key('active-journey-card'),
            borderRadius: BorderRadius.circular(AppRadius.lg),
            onTap: () => context.push(_destinationFor(journey!)),
            child: JourneyStatusCard(journey: journey!),
          ),
        ),
    ],
  );
}

class _NoActiveJourney extends StatelessWidget {
  const _NoActiveJourney();

  @override
  Widget build(BuildContext context) => Container(
    width: double.infinity,
    padding: const EdgeInsets.all(AppSpacing.lg),
    decoration: BoxDecoration(
      color: AppColors.primarySurface,
      borderRadius: BorderRadius.circular(AppRadius.lg),
      border: Border.all(color: AppColors.primaryLight),
    ),
    child: Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        const Icon(
          Icons.event_available_rounded,
          color: AppColors.primaryDark,
          size: 28,
        ),
        const SizedBox(width: AppSpacing.md),
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                'Chưa có hành trình đang hoạt động',
                style: Theme.of(context).textTheme.titleMedium,
              ),
              const SizedBox(height: AppSpacing.xs),
              Text(
                'Lịch khám sắp tới và việc cần làm sẽ xuất hiện tại đây.',
                style: Theme.of(context).textTheme.bodyMedium,
              ),
            ],
          ),
        ),
      ],
    ),
  );
}

class _PreparationGuide extends StatelessWidget {
  const _PreparationGuide();

  @override
  Widget build(BuildContext context) => Container(
    padding: const EdgeInsets.all(AppSpacing.base),
    decoration: BoxDecoration(
      color: AppColors.surface,
      borderRadius: BorderRadius.circular(AppRadius.lg),
      border: Border.all(color: AppColors.cardBorder),
    ),
    child: Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Row(
          children: [
            const Icon(Icons.checklist_rounded, color: AppColors.info),
            const SizedBox(width: AppSpacing.sm),
            Expanded(
              child: Text(
                'Chuẩn bị trước khi đi khám',
                style: Theme.of(context).textTheme.titleMedium,
              ),
            ),
          ],
        ),
        const SizedBox(height: AppSpacing.md),
        const _GuideItem('Mang theo giấy tờ tùy thân và hồ sơ liên quan.'),
        const _GuideItem('Đến đúng cơ sở, khoa và khung giờ trên phiếu khám.'),
        const _GuideItem('Theo dõi thông báo và lượt khám trên ứng dụng.'),
      ],
    ),
  );
}

class _GuideItem extends StatelessWidget {
  const _GuideItem(this.text);

  final String text;

  @override
  Widget build(BuildContext context) => Padding(
    padding: const EdgeInsets.only(bottom: AppSpacing.sm),
    child: Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        const Padding(
          padding: EdgeInsets.only(top: 5),
          child: Icon(
            Icons.check_circle_outline_rounded,
            size: 18,
            color: AppColors.success,
          ),
        ),
        const SizedBox(width: AppSpacing.sm),
        Expanded(
          child: Text(text, style: Theme.of(context).textTheme.bodyMedium),
        ),
      ],
    ),
  );
}

String _destinationFor(PatientJourney journey) {
  final base = '/journey/${journey.appointmentId}';
  return switch (journey.status) {
    JourneyStatus.booked => base,
    JourneyStatus.ticketIssued || JourneyStatus.checkedIn => '$base/ticket',
    JourneyStatus.waiting || JourneyStatus.called => '$base/queue',
    JourneyStatus.inConsultation => '$base/consultation',
    JourneyStatus.labOrdered ||
    JourneyStatus.paymentPending ||
    JourneyStatus.waitingLab ||
    JourneyStatus.labInProgress ||
    JourneyStatus.labResultReady => '$base/laboratory',
    JourneyStatus.waitingResultReview ||
    JourneyStatus.resultReview => '$base/result-review',
    JourneyStatus.prescribed ||
    JourneyStatus.prescriptionPaymentPending ||
    JourneyStatus.prescriptionPaid ||
    JourneyStatus.medicationReady => '$base/pharmacy',
    JourneyStatus.completed => '$base/outcome',
  };
}
