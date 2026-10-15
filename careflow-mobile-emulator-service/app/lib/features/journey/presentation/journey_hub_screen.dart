import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../config/theme.dart';
import '../application/journey_providers.dart';
import '../data/journey_repository.dart';
import '../domain/journey_models.dart';
import 'widgets/demo_control_sheet.dart';
import 'widgets/journey_status_card.dart';

class JourneyHubScreen extends ConsumerWidget {
  const JourneyHubScreen({super.key, required this.appointmentId});

  final String appointmentId;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final journey = ref.watch(journeyForAppointmentProvider(appointmentId));
    final demoMode = ref.watch(demoModeProvider);
    return Scaffold(
      appBar: AppBar(title: const Text('Hành trình khám')),
      body: journey.when(
        loading: () => const Center(child: Text('Đang tải hành trình khám...')),
        error: (error, _) => Center(
          child: Text(
            error is JourneyBackendUnavailable
                ? 'Hành trình khám đang chờ backend triển khai.'
                : 'Không thể tải hành trình khám.',
          ),
        ),
        data: (value) {
          if (value == null) {
            return const Center(child: Text('Chưa có hành trình khám.'));
          }
          final destination = _destinationFor(value.status);
          return ListView(
            padding: const EdgeInsets.all(AppSpacing.base),
            children: [
              JourneyStatusCard(journey: value),
              const SizedBox(height: AppSpacing.base),
              if (destination != null)
                SizedBox(
                  width: double.infinity,
                  child: ElevatedButton.icon(
                    onPressed: () =>
                        context.push(_pathFor(destination, appointmentId)),
                    icon: Icon(_iconFor(destination)),
                    label: Text(_labelFor(destination)),
                  ),
                ),
              if (demoMode) ...[
                const SizedBox(height: AppSpacing.xl),
                const DemoControlSheet(),
              ],
            ],
          );
        },
      ),
    );
  }
}

_PrimaryDestination? _destinationFor(JourneyStatus status) => switch (status) {
  JourneyStatus.ticketIssued ||
  JourneyStatus.checkedIn => _PrimaryDestination.ticket,
  JourneyStatus.waiting || JourneyStatus.called => _PrimaryDestination.queue,
  JourneyStatus.inConsultation => _PrimaryDestination.consultation,
  JourneyStatus.labOrdered ||
  JourneyStatus.paymentPending ||
  JourneyStatus.waitingLab ||
  JourneyStatus.labInProgress ||
  JourneyStatus.labResultReady => _PrimaryDestination.laboratory,
  JourneyStatus.waitingResultReview ||
  JourneyStatus.resultReview => _PrimaryDestination.resultReview,
  JourneyStatus.prescribed ||
  JourneyStatus.settlementPending ||
  JourneyStatus.paymentDue ||
  JourneyStatus.settled ||
  JourneyStatus.refundPending ||
  JourneyStatus.refunded ||
  JourneyStatus.prescriptionPaymentPending ||
  JourneyStatus.prescriptionPaid ||
  JourneyStatus.medicationReady => _PrimaryDestination.pharmacy,
  JourneyStatus.completed => _PrimaryDestination.outcome,
  JourneyStatus.booked => null,
};

String _pathFor(_PrimaryDestination destination, String appointmentId) {
  final base = '/journey/$appointmentId';
  return switch (destination) {
    _PrimaryDestination.ticket => '$base/ticket',
    _PrimaryDestination.queue => '$base/queue',
    _PrimaryDestination.consultation => '$base/consultation',
    _PrimaryDestination.laboratory => '$base/laboratory',
    _PrimaryDestination.resultReview => '$base/result-review',
    _PrimaryDestination.pharmacy => '$base/settlement',
    _PrimaryDestination.outcome => '$base/outcome',
  };
}

IconData _iconFor(_PrimaryDestination destination) => switch (destination) {
  _PrimaryDestination.ticket => Icons.confirmation_number_rounded,
  _PrimaryDestination.queue => Icons.people_alt_rounded,
  _PrimaryDestination.consultation => Icons.medical_services_rounded,
  _PrimaryDestination.laboratory => Icons.science_rounded,
  _PrimaryDestination.resultReview => Icons.manage_search_rounded,
  _PrimaryDestination.pharmacy => Icons.local_pharmacy_rounded,
  _PrimaryDestination.outcome => Icons.task_alt_rounded,
};

String _labelFor(_PrimaryDestination destination) => switch (destination) {
  _PrimaryDestination.ticket => 'Xem phiếu khám',
  _PrimaryDestination.queue => 'Theo dõi hàng đợi',
  _PrimaryDestination.consultation => 'Xem trạng thái khám',
  _PrimaryDestination.laboratory => 'Xem xét nghiệm',
  _PrimaryDestination.resultReview => 'Xem đọc kết quả',
  _PrimaryDestination.pharmacy => 'Quyết toán/nhận thuốc',
  _PrimaryDestination.outcome => 'Xem kết quả lượt khám',
};

enum _PrimaryDestination {
  ticket,
  queue,
  consultation,
  laboratory,
  resultReview,
  pharmacy,
  outcome,
}
