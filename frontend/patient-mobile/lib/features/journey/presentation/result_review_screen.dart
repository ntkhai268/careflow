import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../config/theme.dart';
import '../application/journey_providers.dart';
import '../domain/journey_models.dart';
import 'widgets/journey_status_card.dart';

class ResultReviewScreen extends ConsumerWidget {
  const ResultReviewScreen({super.key, required this.appointmentId});

  final String appointmentId;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final journey = ref.watch(journeyForAppointmentProvider(appointmentId));
    return Scaffold(
      appBar: AppBar(title: const Text('Đọc kết quả')),
      body: journey.when(
        loading: () =>
            const Center(child: Text('Đang tải trạng thái đọc kết quả...')),
        error: (_, _) =>
            const Center(child: Text('Không thể tải trạng thái đọc kết quả.')),
        data: (value) => value == null
            ? const Center(child: Text('Chưa có thông tin đọc kết quả.'))
            : ListView(
                padding: const EdgeInsets.all(AppSpacing.base),
                children: [
                  JourneyStatusCard(journey: value),
                  const SizedBox(height: AppSpacing.base),
                  _ResultReviewCard(journey: value),
                ],
              ),
      ),
    );
  }
}

class _ResultReviewCard extends StatelessWidget {
  const _ResultReviewCard({required this.journey});

  final PatientJourney journey;

  @override
  Widget build(BuildContext context) {
    final isReviewing = journey.status == JourneyStatus.resultReview;
    final room =
        journey.resultReviewQueue?.room ?? journey.ticket?.room ?? 'phòng khám';
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(AppSpacing.xl),
        child: Column(
          children: [
            Icon(
              isReviewing
                  ? Icons.manage_search_rounded
                  : Icons.event_seat_rounded,
              color: isReviewing ? AppColors.primary : AppColors.warning,
              size: 48,
            ),
            const SizedBox(height: AppSpacing.md),
            Text(
              isReviewing ? 'Bác sĩ đang đọc kết quả' : 'Quay lại $room',
              textAlign: TextAlign.center,
              style: Theme.of(context).textTheme.headlineSmall,
            ),
            if (!isReviewing) ...[
              const SizedBox(height: AppSpacing.md),
              const Text(
                'Bạn được xếp sau bệnh nhân khám mới tiếp theo',
                textAlign: TextAlign.center,
              ),
            ],
          ],
        ),
      ),
    );
  }
}
