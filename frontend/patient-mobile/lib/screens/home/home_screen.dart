import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../config/theme.dart';
import '../../features/journey/application/journey_providers.dart';
import '../../features/journey/domain/journey_models.dart';
import '../../features/journey/presentation/widgets/journey_status_card.dart';
import '../../providers/auth_provider.dart';
import '../../widgets/quick_action_card.dart';

/// Main landing page after login.
class HomeScreen extends ConsumerWidget {
  const HomeScreen({super.key, this.onNotificationTap});

  final VoidCallback? onNotificationTap;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final authState = ref.watch(authProvider);
    final activeJourney = ref.watch(activeJourneyProvider);
    final unreadCount = ref.watch(unreadJourneyNotificationCountProvider);

    return Scaffold(
      backgroundColor: AppColors.background,
      body: CustomScrollView(
        slivers: [
          SliverToBoxAdapter(
            child: _Header(
              userName: authState.fullName ?? 'Bạn',
              unreadCount: unreadCount,
              onNotificationTap: onNotificationTap,
            ),
          ),
          const SliverToBoxAdapter(child: _QuickActions()),
          SliverToBoxAdapter(
            child: _ActiveJourneySection(journey: activeJourney),
          ),
          const SliverToBoxAdapter(child: _HealthTip()),
          const SliverToBoxAdapter(child: SizedBox(height: 24)),
        ],
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
  Widget build(BuildContext context) => Container(
    decoration: const BoxDecoration(
      gradient: AppColors.headerGradient,
      borderRadius: BorderRadius.only(
        bottomLeft: Radius.circular(24),
        bottomRight: Radius.circular(24),
      ),
    ),
    child: SafeArea(
      bottom: false,
      child: Padding(
        padding: const EdgeInsets.fromLTRB(20, 16, 20, 24),
        child: Column(
          children: [
            Row(
              children: [
                Container(
                  width: 44,
                  height: 44,
                  decoration: BoxDecoration(
                    color: Colors.white,
                    borderRadius: BorderRadius.circular(AppRadius.md),
                  ),
                  child: const Icon(
                    Icons.local_hospital_rounded,
                    color: AppColors.primary,
                  ),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        'CareFlow xin chào,',
                        style: TextStyle(
                          fontSize: 13,
                          color: Colors.white.withValues(alpha: 0.85),
                        ),
                      ),
                      Text(
                        userName,
                        style: const TextStyle(
                          fontSize: 18,
                          fontWeight: FontWeight.w700,
                          color: Colors.white,
                        ),
                      ),
                    ],
                  ),
                ),
                IconButton(
                  key: const Key('home-notification-button'),
                  tooltip: 'Thông báo',
                  onPressed: onNotificationTap,
                  icon: Stack(
                    clipBehavior: Clip.none,
                    children: [
                      const Icon(
                        Icons.notifications_outlined,
                        color: Colors.white,
                        size: 26,
                      ),
                      if (unreadCount > 0)
                        Positioned(
                          right: -5,
                          top: -5,
                          child: Container(
                            constraints: const BoxConstraints(
                              minWidth: 18,
                              minHeight: 18,
                            ),
                            padding: const EdgeInsets.symmetric(horizontal: 4),
                            decoration: BoxDecoration(
                              color: AppColors.error,
                              borderRadius: BorderRadius.circular(
                                AppRadius.full,
                              ),
                              border: Border.all(
                                color: Colors.white,
                                width: 1.5,
                              ),
                            ),
                            child: Center(
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
                        ),
                    ],
                  ),
                ),
              ],
            ),
            const SizedBox(height: 20),
            Container(
              height: 48,
              decoration: BoxDecoration(
                color: Colors.white,
                borderRadius: BorderRadius.circular(AppRadius.md),
                boxShadow: [
                  BoxShadow(
                    color: Colors.black.withValues(alpha: 0.08),
                    blurRadius: 8,
                    offset: const Offset(0, 2),
                  ),
                ],
              ),
              child: Row(
                children: [
                  const SizedBox(width: 16),
                  Icon(Icons.search, color: AppColors.textHint, size: 20),
                  const SizedBox(width: 12),
                  Expanded(
                    child: Text(
                      'Tìm CSYT/bác sĩ/chuyên khoa/dịch vụ',
                      style: TextStyle(color: AppColors.textHint, fontSize: 13),
                    ),
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    ),
  );
}

class _QuickActions extends StatelessWidget {
  const _QuickActions();

  static const _actions = <(IconData, String, String?)>[
    (Icons.local_hospital_rounded, 'Đặt khám\ntại cơ sở', null),
    (Icons.medical_services_rounded, 'Đặt khám\nchuyên khoa', null),
    (Icons.access_time_rounded, 'Đặt khám\nngoài giờ', null),
    (Icons.science_rounded, 'Đặt lịch\nxét nghiệm', null),
    (Icons.person_search_rounded, 'Giúp việc\ncá nhân', 'Mới'),
    (Icons.video_call_rounded, 'Gọi video\nvới bác sĩ', 'Mới'),
    (Icons.health_and_safety_rounded, 'Gói sức khỏe\ntoàn diện', null),
    (Icons.groups_rounded, 'Khám doanh\nnghiệp', 'Mới'),
  ];

  @override
  Widget build(BuildContext context) => Container(
    margin: const EdgeInsets.fromLTRB(16, 20, 16, 0),
    padding: const EdgeInsets.symmetric(vertical: 16, horizontal: 8),
    decoration: BoxDecoration(
      color: AppColors.surface,
      borderRadius: BorderRadius.circular(AppRadius.lg),
      boxShadow: AppShadows.card,
    ),
    child: GridView.builder(
      shrinkWrap: true,
      physics: const NeverScrollableScrollPhysics(),
      gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(
        crossAxisCount: 4,
        childAspectRatio: 0.72,
      ),
      itemCount: _actions.length,
      itemBuilder: (context, index) {
        final action = _actions[index];
        final opensBooking = index < 3;
        return QuickActionCard(
          key: Key('home-quick-action-$index'),
          icon: action.$1,
          label: action.$2,
          showBadge: action.$3 != null,
          badgeText: action.$3,
          onTap: opensBooking
              ? () => context.push('/booking/step1')
              : null,
        );
      },
    ),
  );
}

class _ActiveJourneySection extends StatelessWidget {
  const _ActiveJourneySection({required this.journey});

  final PatientJourney? journey;

  @override
  Widget build(BuildContext context) => Padding(
    padding: const EdgeInsets.fromLTRB(16, 24, 16, 0),
    child: Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Row(
          mainAxisAlignment: MainAxisAlignment.spaceBetween,
          children: [
            const Expanded(
              child: Text(
                'Hành trình khám đang hoạt động',
                style: TextStyle(
                  fontSize: 18,
                  fontWeight: FontWeight.w700,
                  color: AppColors.textPrimary,
                ),
              ),
            ),
            TextButton(
              onPressed: journey == null
                  ? null
                  : () => context.push(
                      '/journey/${journey!.appointmentId}/timeline',
                    ),
              child: const Text('Dòng thời gian'),
            ),
          ],
        ),
        const SizedBox(height: 12),
        if (journey == null)
          const Card(
            child: Padding(
              padding: EdgeInsets.all(AppSpacing.lg),
              child: Row(
                children: [
                  Icon(
                    Icons.event_available_outlined,
                    color: AppColors.textSecondary,
                  ),
                  SizedBox(width: AppSpacing.md),
                  Expanded(
                    child: Text('Bạn chưa có hành trình khám đang hoạt động.'),
                  ),
                ],
              ),
            ),
          )
        else
          InkWell(
            key: const Key('active-journey-card'),
            borderRadius: BorderRadius.circular(AppRadius.lg),
            onTap: () => context.push(_destinationFor(journey!)),
            child: JourneyStatusCard(journey: journey!),
          ),
      ],
    ),
  );
}

class _HealthTip extends StatelessWidget {
  const _HealthTip();

  @override
  Widget build(BuildContext context) => Padding(
    padding: const EdgeInsets.fromLTRB(16, 24, 16, 0),
    child: Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        const Text(
          'Mẹo sức khỏe',
          style: TextStyle(
            fontSize: 18,
            fontWeight: FontWeight.w700,
            color: AppColors.textPrimary,
          ),
        ),
        const SizedBox(height: 12),
        Card(
          child: Padding(
            padding: const EdgeInsets.all(AppSpacing.base),
            child: Row(
              children: [
                Container(
                  width: 48,
                  height: 48,
                  decoration: BoxDecoration(
                    color: AppColors.accentLight,
                    borderRadius: BorderRadius.circular(AppRadius.md),
                  ),
                  child: const Icon(
                    Icons.tips_and_updates_rounded,
                    color: AppColors.accent,
                  ),
                ),
                const SizedBox(width: AppSpacing.md),
                const Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        'Uống đủ nước mỗi ngày',
                        style: TextStyle(
                          fontWeight: FontWeight.w600,
                          color: AppColors.textPrimary,
                        ),
                      ),
                      SizedBox(height: AppSpacing.xs),
                      Text(
                        'Nên uống 2-3 lít nước mỗi ngày để duy trì sức khỏe tốt nhất.',
                        style: TextStyle(color: AppColors.textSecondary),
                      ),
                    ],
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
    JourneyStatus.prescribed || JourneyStatus.completed => '$base/outcome',
  };
}
