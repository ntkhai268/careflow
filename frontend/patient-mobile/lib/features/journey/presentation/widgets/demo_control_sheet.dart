import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../../../config/theme.dart';
import '../../application/journey_providers.dart';
import '../../domain/journey_models.dart';
import '../../domain/journey_transition.dart';

class DemoControlSheet extends ConsumerWidget {
  const DemoControlSheet({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final journey = ref.watch(activeJourneyProvider);
    if (journey == null) return const SizedBox.shrink();
    final events = _eventsFor(journey.status);
    return Card(
      color: AppColors.warningLight,
      child: Padding(
        padding: const EdgeInsets.all(AppSpacing.base),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              'Điều khiển mô phỏng',
              style: Theme.of(context).textTheme.titleMedium,
            ),
            const SizedBox(height: AppSpacing.xs),
            const Text('Chỉ dùng để mô phỏng sự kiện từ nhân viên và bác sĩ.'),
            const SizedBox(height: AppSpacing.md),
            for (final event in events) ...[
              SizedBox(
                width: double.infinity,
                child: ElevatedButton(
                  onPressed: () => ref
                      .read(journeyControllerProvider.notifier)
                      .advance(event.event),
                  child: Text(event.label),
                ),
              ),
              const SizedBox(height: AppSpacing.sm),
            ],
            Align(
              alignment: Alignment.centerRight,
              child: OutlinedButton(
                onPressed: () => _confirmReset(context, ref),
                child: const Text('Đặt lại hành trình'),
              ),
            ),
          ],
        ),
      ),
    );
  }

  Future<void> _confirmReset(BuildContext context, WidgetRef ref) async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Xác nhận đặt lại hành trình?'),
        content: const Text(
          'Dữ liệu mô phỏng của lịch hẹn hiện tại sẽ bị xóa.',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(context).pop(false),
            child: const Text('Hủy'),
          ),
          ElevatedButton(
            onPressed: () => Navigator.of(context).pop(true),
            child: const Text('Đặt lại'),
          ),
        ],
      ),
    );
    if (confirmed == true) {
      await ref.read(journeyControllerProvider.notifier).resetCurrentJourney();
    }
  }
}

List<_DemoEvent> _eventsFor(JourneyStatus status) => switch (status) {
  JourneyStatus.ticketIssued => const [
    _DemoEvent(JourneyEvent.staffScannedQr, 'Mô phỏng bệnh nhân quét QR'),
  ],
  JourneyStatus.checkedIn => const [
    _DemoEvent(
      JourneyEvent.admittedToClinicQueue,
      'Mô phỏng nhân viên đưa vào hàng đợi',
    ),
  ],
  JourneyStatus.waiting => const [
    _DemoEvent(JourneyEvent.doctorCalled, 'Mô phỏng bác sĩ gọi'),
  ],
  JourneyStatus.called => const [
    _DemoEvent(
      JourneyEvent.consultationStarted,
      'Mô phỏng bác sĩ bắt đầu khám',
    ),
  ],
  JourneyStatus.inConsultation => const [
    _DemoEvent(
      JourneyEvent.laboratoryOrdered,
      'Mô phỏng bác sĩ chỉ định xét nghiệm',
    ),
    _DemoEvent(
      JourneyEvent.directPrescriptionIssued,
      'Mô phỏng bác sĩ kê đơn trực tiếp',
    ),
  ],
  JourneyStatus.labOrdered => const [
    _DemoEvent(JourneyEvent.paymentRequested, 'Mô phỏng yêu cầu thanh toán'),
  ],
  JourneyStatus.waitingLab => const [
    _DemoEvent(JourneyEvent.laboratoryStarted, 'Mô phỏng bắt đầu xét nghiệm'),
  ],
  JourneyStatus.labInProgress => const [
    _DemoEvent(
      JourneyEvent.laboratoryResultsPublished,
      'Mô phỏng công bố kết quả',
    ),
  ],
  JourneyStatus.labResultReady => const [
    _DemoEvent(
      JourneyEvent.admittedToResultReviewQueue,
      'Mô phỏng đưa vào hàng đợi đọc kết quả',
    ),
  ],
  JourneyStatus.waitingResultReview => const [
    _DemoEvent(JourneyEvent.resultReviewCalled, 'Mô phỏng bác sĩ đọc kết quả'),
  ],
  JourneyStatus.resultReview => const [
    _DemoEvent(
      JourneyEvent.finalPrescriptionIssued,
      'Mô phỏng bác sĩ kê đơn sau đọc kết quả',
    ),
  ],
  JourneyStatus.prescribed => const [
    _DemoEvent(
      JourneyEvent.prescriptionPaymentRequested,
      'Mô phỏng yêu cầu thanh toán tiền thuốc',
    ),
    _DemoEvent(JourneyEvent.visitCompleted, 'Mô phỏng hoàn tất lượt khám'),
  ],
  JourneyStatus.prescriptionPaymentPending => const [],
  JourneyStatus.prescriptionPaid => const [
    _DemoEvent(JourneyEvent.medicationDispensed, 'Mô phỏng thuốc đã sẵn sàng'),
  ],
  JourneyStatus.medicationReady => const [
    _DemoEvent(JourneyEvent.visitCompleted, 'Mô phỏng hoàn tất lượt khám'),
  ],
  _ => const [],
};

class _DemoEvent {
  const _DemoEvent(this.event, this.label);

  final JourneyEvent event;
  final String label;
}
