import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:intl/intl.dart';

import '../../../config/theme.dart';
import '../application/journey_providers.dart';

/// Patient-facing history of completed visits and their clinical outcomes.
class VisitResultsScreen extends ConsumerWidget {
  const VisitResultsScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final results = ref.watch(visitResultsProvider);
    return Scaffold(
      appBar: AppBar(title: const Text('Kết quả khám')),
      body: results.when(
        loading: () => const _ResultsLoadingState(),
        error: (_, _) => _ResultsErrorState(
          onRetry: () => ref.invalidate(visitResultsProvider),
        ),
        data: (journeys) => journeys.isEmpty
            ? const _ResultsEmptyState()
            : RefreshIndicator(
                onRefresh: () async => ref.refresh(visitResultsProvider.future),
                child: ListView.separated(
                  physics: const AlwaysScrollableScrollPhysics(),
                  padding: const EdgeInsets.fromLTRB(
                    AppSpacing.base,
                    AppSpacing.base,
                    AppSpacing.base,
                    AppSpacing.xxxl,
                  ),
                  itemCount: journeys.length,
                  separatorBuilder: (_, _) =>
                      const SizedBox(height: AppSpacing.md),
                  itemBuilder: (context, index) => _VisitResultCard(
                    key: ValueKey(journeys[index].journey.appointmentId),
                    result: journeys[index],
                    onTap: () => context.push(
                      '/journey/${journeys[index].journey.appointmentId}/outcome',
                      extra: journeys[index].journey,
                    ),
                  ),
                ),
              ),
      ),
    );
  }
}

class _VisitResultCard extends StatelessWidget {
  const _VisitResultCard({
    super.key,
    required this.result,
    required this.onTap,
  });

  final VisitResult result;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final appointment = result.appointment;
    final journey = result.journey;
    final diagnosis = journey.diagnosis?.title;
    final medicationCount = journey.prescription?.items.length ?? 0;
    final labCount = journey.laboratoryOrders.length;
    final hasClinicalData =
        diagnosis != null || medicationCount > 0 || labCount > 0;

    return Semantics(
      button: true,
      label: 'Kết quả khám ${appointment.departmentDisplayName}',
      child: Card(
        clipBehavior: Clip.antiAlias,
        child: InkWell(
          onTap: onTap,
          child: Padding(
            padding: const EdgeInsets.all(AppSpacing.base),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Container(
                      width: 48,
                      height: 48,
                      decoration: BoxDecoration(
                        color: AppColors.successLight,
                        borderRadius: BorderRadius.circular(AppRadius.md),
                      ),
                      child: const Icon(
                        Icons.assignment_turned_in_rounded,
                        color: AppColors.success,
                      ),
                    ),
                    const SizedBox(width: AppSpacing.md),
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            appointment.departmentDisplayName,
                            maxLines: 2,
                            overflow: TextOverflow.ellipsis,
                            style: Theme.of(context).textTheme.titleMedium,
                          ),
                          const SizedBox(height: AppSpacing.xs),
                          Text(
                            DateFormat(
                              'dd/MM/yyyy',
                            ).format(appointment.appointmentDate.toLocal()),
                            style: Theme.of(context).textTheme.bodyMedium,
                          ),
                        ],
                      ),
                    ),
                    const SizedBox(width: AppSpacing.sm),
                    const Icon(
                      Icons.chevron_right_rounded,
                      color: AppColors.textSecondary,
                    ),
                  ],
                ),
                const SizedBox(height: AppSpacing.md),
                const Divider(),
                const SizedBox(height: AppSpacing.md),
                if (diagnosis != null)
                  _ResultSummaryRow(
                    icon: Icons.medical_services_outlined,
                    label: 'Chẩn đoán',
                    value: diagnosis,
                  ),
                if (medicationCount > 0)
                  _ResultSummaryRow(
                    icon: Icons.medication_outlined,
                    label: 'Toa thuốc',
                    value: '$medicationCount loại thuốc',
                  ),
                if (labCount > 0)
                  _ResultSummaryRow(
                    icon: Icons.science_outlined,
                    label: 'Xét nghiệm',
                    value: '$labCount chỉ định',
                  ),
                if (!hasClinicalData)
                  Text(
                    'Kết quả chi tiết đang được cập nhật.',
                    style: Theme.of(context).textTheme.bodyMedium,
                  ),
                const SizedBox(height: AppSpacing.md),
                SizedBox(
                  width: double.infinity,
                  child: OutlinedButton.icon(
                    onPressed: onTap,
                    icon: const Icon(Icons.visibility_outlined),
                    label: const Text('Xem kết quả và toa thuốc'),
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

class _ResultSummaryRow extends StatelessWidget {
  const _ResultSummaryRow({
    required this.icon,
    required this.label,
    required this.value,
  });

  final IconData icon;
  final String label;
  final String value;

  @override
  Widget build(BuildContext context) => Padding(
    padding: const EdgeInsets.only(bottom: AppSpacing.sm),
    child: Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Icon(icon, size: 20, color: AppColors.primary),
        const SizedBox(width: AppSpacing.sm),
        SizedBox(
          width: 82,
          child: Text(label, style: Theme.of(context).textTheme.bodyMedium),
        ),
        const SizedBox(width: AppSpacing.sm),
        Expanded(
          child: Text(
            value,
            maxLines: 2,
            overflow: TextOverflow.ellipsis,
            style: Theme.of(context).textTheme.bodyLarge,
          ),
        ),
      ],
    ),
  );
}

class _ResultsLoadingState extends StatelessWidget {
  const _ResultsLoadingState();

  @override
  Widget build(BuildContext context) => const Center(
    child: Padding(
      padding: EdgeInsets.all(AppSpacing.xxl),
      child: CircularProgressIndicator(),
    ),
  );
}

class _ResultsEmptyState extends StatelessWidget {
  const _ResultsEmptyState();

  @override
  Widget build(BuildContext context) => Center(
    child: Padding(
      padding: const EdgeInsets.all(AppSpacing.xxl),
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          Container(
            width: 112,
            height: 112,
            decoration: const BoxDecoration(
              color: AppColors.primarySurface,
              shape: BoxShape.circle,
            ),
            child: const Icon(
              Icons.assignment_outlined,
              size: 52,
              color: AppColors.primary,
            ),
          ),
          const SizedBox(height: AppSpacing.xl),
          Text(
            'Chưa có kết quả khám',
            style: Theme.of(context).textTheme.titleLarge,
          ),
          const SizedBox(height: AppSpacing.sm),
          Text(
            'Kết quả khám, xét nghiệm và toa thuốc sẽ xuất hiện tại đây sau khi lượt khám hoàn tất.',
            textAlign: TextAlign.center,
            style: Theme.of(context).textTheme.bodyLarge,
          ),
        ],
      ),
    ),
  );
}

class _ResultsErrorState extends StatelessWidget {
  const _ResultsErrorState({required this.onRetry});

  final VoidCallback onRetry;

  @override
  Widget build(BuildContext context) => Center(
    child: Padding(
      padding: const EdgeInsets.all(AppSpacing.xxl),
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          const Icon(Icons.cloud_off_rounded, size: 52, color: AppColors.error),
          const SizedBox(height: AppSpacing.base),
          Text(
            'Không tải được kết quả khám',
            style: Theme.of(context).textTheme.titleLarge,
            textAlign: TextAlign.center,
          ),
          const SizedBox(height: AppSpacing.sm),
          Text(
            'Kiểm tra kết nối rồi thử lại.',
            style: Theme.of(context).textTheme.bodyLarge,
            textAlign: TextAlign.center,
          ),
          const SizedBox(height: AppSpacing.lg),
          ElevatedButton.icon(
            onPressed: onRetry,
            icon: const Icon(Icons.refresh_rounded),
            label: const Text('Thử lại'),
          ),
        ],
      ),
    ),
  );
}
