import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:qr_flutter/qr_flutter.dart';

import '../../../config/theme.dart';
import '../application/journey_providers.dart';
import '../data/journey_repository.dart';
import '../domain/journey_models.dart';
import 'widgets/journey_status_card.dart';

class VisitTicketScreen extends ConsumerWidget {
  const VisitTicketScreen({super.key, required this.appointmentId});

  final String appointmentId;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final journey = ref.watch(journeyForAppointmentProvider(appointmentId));
    return Scaffold(
      appBar: AppBar(title: const Text('Phiếu khám')),
      body: journey.when(
        loading: () => const _JourneyMessage(
          icon: Icons.hourglass_top_rounded,
          message: 'Đang tải phiếu khám...',
        ),
        error: (error, _) => _JourneyMessage(
          icon: Icons.cloud_off_rounded,
          message: error is JourneyBackendUnavailable
              ? 'Hành trình khám đang chờ backend triển khai.'
              : 'Không thể tải phiếu khám.',
        ),
        data: (value) => _TicketBody(journey: value),
      ),
    );
  }
}

class _TicketBody extends StatelessWidget {
  const _TicketBody({required this.journey});

  final PatientJourney? journey;

  @override
  Widget build(BuildContext context) {
    final ticket = journey?.ticket;
    if (journey == null || ticket == null) {
      return const _JourneyMessage(
        icon: Icons.confirmation_number_outlined,
        message: 'Chưa có phiếu khám cho lịch hẹn này.',
      );
    }
    return ListView(
      padding: const EdgeInsets.all(AppSpacing.base),
      children: [
        JourneyStatusCard(journey: journey!),
        const SizedBox(height: AppSpacing.base),
        Card(
          child: Padding(
            padding: const EdgeInsets.all(AppSpacing.lg),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  ticket.hospitalName,
                  style: Theme.of(context).textTheme.titleLarge,
                ),
                const SizedBox(height: AppSpacing.sm),
                Text(ticket.specialtyName),
                const Divider(height: AppSpacing.xl),
                _DetailRow(label: 'Phòng khám', value: ticket.room),
                _DetailRow(
                  label: 'Khung giờ dự kiến',
                  value: ticket.expectedWindow,
                ),
                _DetailRow(label: 'Số thứ tự', value: ticket.queueNumber),
                const SizedBox(height: AppSpacing.lg),
                Center(
                  child: Semantics(
                    label: 'Mã QR phiếu khám',
                    child: ExcludeSemantics(
                      child: QrImageView(data: ticket.qrPayload, size: 156),
                    ),
                  ),
                ),
              ],
            ),
          ),
        ),
      ],
    );
  }
}

class _DetailRow extends StatelessWidget {
  const _DetailRow({required this.label, required this.value});

  final String label;
  final String value;

  @override
  Widget build(BuildContext context) => Padding(
    padding: const EdgeInsets.only(bottom: AppSpacing.sm),
    child: Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Expanded(child: Text(label)),
        const SizedBox(width: AppSpacing.sm),
        Expanded(
          flex: 2,
          child: Text(
            value,
            maxLines: 2,
            overflow: TextOverflow.ellipsis,
            textAlign: TextAlign.right,
            style: Theme.of(context).textTheme.titleMedium,
          ),
        ),
      ],
    ),
  );
}

class _JourneyMessage extends StatelessWidget {
  const _JourneyMessage({required this.icon, required this.message});

  final IconData icon;
  final String message;

  @override
  Widget build(BuildContext context) => Center(
    child: Padding(
      padding: const EdgeInsets.all(AppSpacing.xxl),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(icon, size: 48, color: AppColors.textSecondary),
          const SizedBox(height: AppSpacing.md),
          Text(message, textAlign: TextAlign.center),
        ],
      ),
    ),
  );
}
