import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../config/theme.dart';
import '../application/journey_providers.dart';
import '../domain/journey_models.dart';
import 'journey_date_formatter.dart';

class VisitOutcomeScreen extends ConsumerWidget {
  const VisitOutcomeScreen({super.key, required this.appointmentId});

  final String appointmentId;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final journey = ref.watch(journeyForAppointmentProvider(appointmentId));
    return Scaffold(
      appBar: AppBar(title: const Text('Kết quả lượt khám')),
      body: journey.when(
        loading: () =>
            const Center(child: Text('Đang tải kết quả lượt khám...')),
        error: (_, _) =>
            const Center(child: Text('Không thể tải kết quả lượt khám.')),
        data: (value) => value == null
            ? const Center(child: Text('Chưa có kết quả lượt khám.'))
            : _VisitOutcomeBody(journey: value),
      ),
    );
  }
}

class _VisitOutcomeBody extends StatelessWidget {
  const _VisitOutcomeBody({required this.journey});

  final PatientJourney journey;

  @override
  Widget build(BuildContext context) => ListView(
    padding: const EdgeInsets.all(AppSpacing.base),
    children: [
      const _OutcomeHeader(),
      if (journey.diagnosis case final diagnosis?) ...[
        const SizedBox(height: AppSpacing.base),
        _DiagnosisCard(diagnosis: diagnosis),
      ],
      if (journey.prescription case final prescription?
          when prescription.items.isNotEmpty) ...[
        const SizedBox(height: AppSpacing.base),
        _PrescriptionCard(prescription: prescription),
      ],
      if (journey.laboratoryOrders.isNotEmpty) ...[
        const SizedBox(height: AppSpacing.base),
        _LaboratorySummaryCard(orders: journey.laboratoryOrders),
      ],
      if (journey.followUp case final followUp?) ...[
        const SizedBox(height: AppSpacing.base),
        _FollowUpCard(followUp: followUp),
      ],
    ],
  );
}

class _OutcomeHeader extends StatelessWidget {
  const _OutcomeHeader();

  @override
  Widget build(BuildContext context) => Card(
    color: AppColors.successLight,
    child: const Padding(
      padding: EdgeInsets.all(AppSpacing.xl),
      child: Row(
        children: [
          Icon(Icons.check_circle_rounded, color: AppColors.success, size: 40),
          SizedBox(width: AppSpacing.md),
          Expanded(
            child: Text(
              'Lượt khám đã hoàn tất',
              style: TextStyle(fontSize: 20, fontWeight: FontWeight.w600),
            ),
          ),
        ],
      ),
    ),
  );
}

class _DiagnosisCard extends StatelessWidget {
  const _DiagnosisCard({required this.diagnosis});

  final DiagnosisSummary diagnosis;

  @override
  Widget build(BuildContext context) => _SectionCard(
    icon: Icons.medical_services_rounded,
    title: 'Chẩn đoán',
    child: Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(diagnosis.title, style: Theme.of(context).textTheme.titleLarge),
        const SizedBox(height: AppSpacing.sm),
        Text(diagnosis.detail),
      ],
    ),
  );
}

class _PrescriptionCard extends StatelessWidget {
  const _PrescriptionCard({required this.prescription});

  final Prescription prescription;

  @override
  Widget build(BuildContext context) => _SectionCard(
    icon: Icons.medication_rounded,
    title: 'Đơn thuốc',
    child: Column(
      children: [
        for (var index = 0; index < prescription.items.length; index++) ...[
          _PrescriptionItem(item: prescription.items[index]),
          if (index < prescription.items.length - 1) const Divider(height: 24),
        ],
      ],
    ),
  );
}

class _PrescriptionItem extends StatelessWidget {
  const _PrescriptionItem({required this.item});

  final PrescriptionItem item;

  @override
  Widget build(BuildContext context) => Column(
    crossAxisAlignment: CrossAxisAlignment.start,
    children: [
      Text(item.medicationName, style: Theme.of(context).textTheme.titleMedium),
      const SizedBox(height: AppSpacing.xs),
      Text(
        '${item.dosage} • ${item.route} • ${item.frequency} • ${item.duration}',
      ),
      const SizedBox(height: AppSpacing.xs),
      Text('Lưu ý:', style: Theme.of(context).textTheme.bodyMedium),
      Text(item.caution, style: Theme.of(context).textTheme.bodyMedium),
    ],
  );
}

class _LaboratorySummaryCard extends StatelessWidget {
  const _LaboratorySummaryCard({required this.orders});

  final List<LaboratoryOrder> orders;

  @override
  Widget build(BuildContext context) => _SectionCard(
    icon: Icons.science_rounded,
    title: 'Xét nghiệm đã thực hiện',
    child: Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        for (var index = 0; index < orders.length; index++) ...[
          Text(
            orders[index].name,
            style: Theme.of(context).textTheme.titleMedium,
          ),
          if (orders[index].result != null) ...[
            const SizedBox(height: AppSpacing.xs),
            Text(
              '${orders[index].result!.value} ${orders[index].result!.unit}'
                  .trim(),
            ),
          ],
          if (index < orders.length - 1) const Divider(height: 24),
        ],
      ],
    ),
  );
}

class _FollowUpCard extends StatelessWidget {
  const _FollowUpCard({required this.followUp});

  final FollowUpAppointment followUp;

  @override
  Widget build(BuildContext context) => _SectionCard(
    icon: Icons.event_available_rounded,
    title: 'Tái khám',
    child: Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          'Ngày tái khám: ${formatJourneyDateTime(followUp.scheduledAt, "dd/MM/yyyy 'lúc' HH:mm")}',
          style: Theme.of(context).textTheme.titleMedium,
        ),
        const SizedBox(height: AppSpacing.sm),
        Text(followUp.room),
        const SizedBox(height: AppSpacing.sm),
        Text(followUp.note, style: Theme.of(context).textTheme.bodyMedium),
      ],
    ),
  );
}

class _SectionCard extends StatelessWidget {
  const _SectionCard({
    required this.icon,
    required this.title,
    required this.child,
  });

  final IconData icon;
  final String title;
  final Widget child;

  @override
  Widget build(BuildContext context) => Card(
    child: Padding(
      padding: const EdgeInsets.all(AppSpacing.base),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Icon(icon, color: AppColors.primary),
              const SizedBox(width: AppSpacing.sm),
              Text(title, style: Theme.of(context).textTheme.titleLarge),
            ],
          ),
          const SizedBox(height: AppSpacing.md),
          child,
        ],
      ),
    ),
  );
}
