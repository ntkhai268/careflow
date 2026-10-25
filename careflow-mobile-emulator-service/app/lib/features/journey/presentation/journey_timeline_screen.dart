import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../config/theme.dart';
import '../application/journey_providers.dart';
import '../domain/journey_models.dart';
import 'journey_date_formatter.dart';

class JourneyTimelineScreen extends ConsumerWidget {
  const JourneyTimelineScreen({super.key, required this.appointmentId});

  final String appointmentId;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final journey = ref.watch(journeyForAppointmentProvider(appointmentId));
    return Scaffold(
      appBar: AppBar(title: const Text('Dòng thời gian khám')),
      body: journey.when(
        loading: () => const Center(child: Text('Đang tải dòng thời gian...')),
        error: (_, _) =>
            const Center(child: Text('Không thể tải dòng thời gian khám.')),
        data: (value) => value == null
            ? const Center(child: Text('Chưa có hành trình khám.'))
            : _TimelineBody(events: value.timeline),
      ),
    );
  }
}

class _TimelineBody extends StatelessWidget {
  const _TimelineBody({required this.events});

  final List<JourneyTimelineEvent> events;

  @override
  Widget build(BuildContext context) {
    final chronologicalEvents = [...events]
      ..sort((left, right) => left.occurredAt.compareTo(right.occurredAt));
    if (chronologicalEvents.isEmpty) {
      return const Center(child: Text('Chưa có sự kiện nào trong hành trình.'));
    }
    return ListView.separated(
      padding: const EdgeInsets.all(AppSpacing.base),
      itemCount: chronologicalEvents.length,
      separatorBuilder: (_, _) => const SizedBox(height: AppSpacing.md),
      itemBuilder: (context, index) => _TimelineEventCard(
        event: chronologicalEvents[index],
        isLast: index == chronologicalEvents.length - 1,
      ),
    );
  }
}

class _TimelineEventCard extends StatelessWidget {
  const _TimelineEventCard({required this.event, required this.isLast});

  final JourneyTimelineEvent event;
  final bool isLast;

  @override
  Widget build(BuildContext context) => Row(
    crossAxisAlignment: CrossAxisAlignment.start,
    children: [
      SizedBox(
        width: 32,
        child: Column(
          children: [
            const Icon(Icons.circle, color: AppColors.primary, size: 18),
            if (!isLast)
              const SizedBox(
                height: 40,
                child: VerticalDivider(
                  color: AppColors.primaryLight,
                  thickness: 2,
                  indent: AppSpacing.xs,
                  endIndent: AppSpacing.xs,
                ),
              ),
          ],
        ),
      ),
      const SizedBox(width: AppSpacing.sm),
      Expanded(
        child: Card(
          child: Padding(
            padding: const EdgeInsets.all(AppSpacing.base),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  event.title,
                  style: Theme.of(context).textTheme.titleMedium,
                ),
                const SizedBox(height: AppSpacing.xs),
                Text(event.detail),
                const SizedBox(height: AppSpacing.sm),
                Text(
                  formatJourneyDateTime(event.occurredAt, 'HH:mm • dd/MM/yyyy'),
                  style: Theme.of(context).textTheme.bodySmall,
                ),
              ],
            ),
          ),
        ),
      ),
    ],
  );
}
