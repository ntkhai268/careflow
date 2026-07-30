import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../config/theme.dart';
import '../application/journey_providers.dart';
import '../domain/journey_models.dart';
import 'widgets/demo_control_sheet.dart';
import 'widgets/journey_status_card.dart';

class ConsultationScreen extends ConsumerWidget {
  const ConsultationScreen({super.key, required this.appointmentId});

  final String appointmentId;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final journey = ref.watch(journeyForAppointmentProvider(appointmentId));
    final demoMode = ref.watch(demoModeProvider);
    return Scaffold(
      appBar: AppBar(title: const Text('Đang khám')),
      body: journey.when(
        loading: () => const Center(child: Text('Đang tải trạng thái khám...')),
        error: (_, _) =>
            const Center(child: Text('Không thể tải trạng thái buổi khám.')),
        data: (value) => value == null
            ? const Center(child: Text('Chưa có thông tin buổi khám.'))
            : ListView(
                padding: const EdgeInsets.all(AppSpacing.base),
                children: [
                  JourneyStatusCard(journey: value),
                  const SizedBox(height: AppSpacing.base),
                  _ConsultationStatusCard(journey: value),
                  if (demoMode) ...[
                    const SizedBox(height: AppSpacing.xl),
                    const DemoControlSheet(),
                  ],
                ],
              ),
      ),
    );
  }
}

class _ConsultationStatusCard extends StatelessWidget {
  const _ConsultationStatusCard({required this.journey});

  final PatientJourney journey;

  @override
  Widget build(BuildContext context) {
    final room = journey.ticket?.room ?? 'Phòng khám đang cập nhật';
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(AppSpacing.xl),
        child: Column(
          children: [
            const Icon(
              Icons.medical_services_rounded,
              color: AppColors.primary,
              size: 48,
            ),
            const SizedBox(height: AppSpacing.md),
            Text(
              'Bác sĩ đang khám',
              style: Theme.of(context).textTheme.headlineSmall,
            ),
            const SizedBox(height: AppSpacing.sm),
            const Text(
              'Vui lòng chờ bác sĩ hoàn tất đánh giá và thông báo bước tiếp theo.',
              textAlign: TextAlign.center,
            ),
            const SizedBox(height: AppSpacing.lg),
            const _ConsultationDetail(
              label: 'Bác sĩ phụ trách',
              value: 'Đang khám',
            ),
            const SizedBox(height: AppSpacing.md),
            _ConsultationDetail(label: 'Phòng khám', value: room),
          ],
        ),
      ),
    );
  }
}

class _ConsultationDetail extends StatelessWidget {
  const _ConsultationDetail({required this.label, required this.value});

  final String label;
  final String value;

  @override
  Widget build(BuildContext context) => Row(
    mainAxisAlignment: MainAxisAlignment.spaceBetween,
    children: [
      Text(label, style: Theme.of(context).textTheme.bodyMedium),
      Flexible(
        child: Text(
          value,
          textAlign: TextAlign.end,
          style: Theme.of(context).textTheme.titleMedium,
        ),
      ),
    ],
  );
}
