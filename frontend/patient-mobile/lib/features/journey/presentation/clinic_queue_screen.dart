import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../config/theme.dart';
import '../application/journey_providers.dart';
import '../data/journey_repository.dart';
import '../domain/journey_models.dart';
import 'widgets/journey_status_card.dart';

class ClinicQueueScreen extends ConsumerWidget {
  const ClinicQueueScreen({super.key, required this.appointmentId});

  final String appointmentId;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final journey = ref.watch(journeyForAppointmentProvider(appointmentId));
    return Scaffold(
      appBar: AppBar(title: const Text('Hàng đợi phòng khám')),
      body: journey.when(
        loading: () => const _QueueMessage('Đang tải trạng thái hàng đợi...'),
        error: (error, _) => _QueueMessage(
          error is JourneyBackendUnavailable
              ? 'Hành trình khám đang chờ backend triển khai.'
              : 'Không thể tải trạng thái hàng đợi.',
        ),
        data: (value) => _QueueBody(journey: value),
      ),
    );
  }
}

class _QueueBody extends StatelessWidget {
  const _QueueBody({required this.journey});

  final PatientJourney? journey;

  @override
  Widget build(BuildContext context) {
    final queue = journey?.clinicQueue;
    if (journey == null || queue == null) {
      return const _QueueMessage('Bạn chưa ở trong hàng đợi phòng khám.');
    }
    final called = journey!.status == JourneyStatus.called;
    final consulting = journey!.status == JourneyStatus.inConsultation;
    return ListView(
      padding: const EdgeInsets.all(AppSpacing.base),
      children: [
        JourneyStatusCard(journey: journey!),
        const SizedBox(height: AppSpacing.base),
        Card(
          child: Padding(
            padding: const EdgeInsets.all(AppSpacing.xl),
            child: Column(
              children: [
                Icon(
                  called ? Icons.campaign_rounded : Icons.people_alt_rounded,
                  color: called ? AppColors.success : AppColors.primary,
                  size: 48,
                ),
                const SizedBox(height: AppSpacing.md),
                Text(
                  called
                      ? 'Đã đến lượt bạn'
                      : consulting
                      ? 'Bác sĩ đang khám cho bạn'
                      : 'Còn ${queue.peopleAhead} người phía trước',
                  style: Theme.of(context).textTheme.headlineSmall,
                  textAlign: TextAlign.center,
                ),
                const SizedBox(height: AppSpacing.md),
                const Text('Phòng khám'),
                Text(
                  queue.room,
                  style: Theme.of(context).textTheme.titleMedium,
                ),
                const SizedBox(height: AppSpacing.xs),
                const Text('Thời gian chờ'),
                Text(queue.expectedWait),
              ],
            ),
          ),
        ),
      ],
    );
  }
}

class _QueueMessage extends StatelessWidget {
  const _QueueMessage(this.message);

  final String message;

  @override
  Widget build(BuildContext context) => Center(
    child: Padding(
      padding: const EdgeInsets.all(AppSpacing.xxl),
      child: Text(message, textAlign: TextAlign.center),
    ),
  );
}
