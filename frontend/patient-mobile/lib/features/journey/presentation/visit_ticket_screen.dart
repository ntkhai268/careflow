import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:qr_flutter/qr_flutter.dart';

import '../../../config/theme.dart';
import '../../../models/queue.dart';
import '../../../services/queue_service.dart';
import '../application/journey_providers.dart';
import '../data/journey_repository.dart';
import '../domain/journey_models.dart' hide VisitTicket;
import 'widgets/journey_status_card.dart';

class VisitTicketScreen extends ConsumerWidget {
  const VisitTicketScreen({super.key, required this.appointmentId});

  final String appointmentId;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    if (ref.watch(realQueueEnabledProvider)) {
      final ticket = ref.watch(queueTicketProvider(appointmentId));
      return _backToAppointmentsOnSystemBack(
        context,
        Scaffold(
          appBar: AppBar(
            title: const Text('Phiếu khám'),
            leading: IconButton(
              tooltip: 'Quay lại lịch khám',
              onPressed: () => _goToAppointments(context),
              icon: const Icon(Icons.arrow_back_rounded),
            ),
            actions: [
              IconButton(
                tooltip: 'Tải lại',
                onPressed: () =>
                    ref.invalidate(queueTicketProvider(appointmentId)),
                icon: const Icon(Icons.refresh_rounded),
              ),
            ],
          ),
          body: ticket.when(
            loading: () => const Center(child: CircularProgressIndicator()),
            error: (error, _) => _JourneyMessage(
              icon: Icons.cloud_off_rounded,
              message: error is QueueServiceException
                  ? error.message
                  : 'Không thể tải phiếu khám.',
            ),
            data: (value) => _RealTicketBody(ticket: value),
          ),
        ),
      );
    }
    final journey = ref.watch(journeyForAppointmentProvider(appointmentId));
    return _backToAppointmentsOnSystemBack(
      context,
      Scaffold(
        appBar: AppBar(
          title: const Text('Phiếu khám'),
          leading: IconButton(
            tooltip: 'Quay lại lịch khám',
            onPressed: () => _goToAppointments(context),
            icon: const Icon(Icons.arrow_back_rounded),
          ),
        ),
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
      ),
    );
  }

  void _goToAppointments(BuildContext context) {
    context.go('/appointments');
  }

  Widget _backToAppointmentsOnSystemBack(BuildContext context, Widget child) =>
      PopScope(
        canPop: false,
        onPopInvokedWithResult: (didPop, _) {
          if (!didPop) _goToAppointments(context);
        },
        child: child,
      );
}

class _RealTicketBody extends StatelessWidget {
  const _RealTicketBody({required this.ticket});

  final VisitTicket ticket;

  @override
  Widget build(BuildContext context) => ListView(
    padding: const EdgeInsets.all(AppSpacing.base),
    children: [
      Card(
        color: ticket.status == 'TICKET_ISSUED'
            ? AppColors.warningLight
            : AppColors.successLight,
        child: Padding(
          padding: const EdgeInsets.all(AppSpacing.lg),
          child: Column(
            children: [
              Icon(
                ticket.status == 'TICKET_ISSUED'
                    ? Icons.qr_code_2_rounded
                    : Icons.check_circle_rounded,
                size: 42,
                color: ticket.status == 'TICKET_ISSUED'
                    ? AppColors.warning
                    : AppColors.success,
              ),
              const SizedBox(height: AppSpacing.sm),
              Text(
                ticket.status == 'TICKET_ISSUED'
                    ? 'Xuất trình QR tại phòng khám để check-in'
                    : 'Đã check-in tại phòng khám',
                textAlign: TextAlign.center,
                style: Theme.of(context).textTheme.titleMedium,
              ),
            ],
          ),
        ),
      ),
      const SizedBox(height: AppSpacing.base),
      Card(
        child: Padding(
          padding: const EdgeInsets.all(AppSpacing.lg),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                'BỆNH VIỆN CAREFLOW',
                style: Theme.of(context).textTheme.titleLarge,
              ),
              const SizedBox(height: AppSpacing.sm),
              Text(ticket.departmentDisplayName),
              const Divider(height: AppSpacing.xl),
              _DetailRow(label: 'Phòng khám', value: ticket.roomDisplayName),
              _DetailRow(
                label: 'Ngày khám',
                value: _date(ticket.appointmentDate),
              ),
              _DetailRow(label: 'Khung giờ', value: ticket.timeSlot),
              _DetailRow(label: 'Số thứ tự', value: ticket.queueNumber),
              _DetailRow(label: 'Mã phiếu khám', value: ticket.ticketCode),
              const SizedBox(height: AppSpacing.lg),
              Center(
                child: Semantics(
                  label: 'Mã QR phiếu khám',
                  child: ExcludeSemantics(
                    child: QrImageView(data: ticket.qrToken, size: 180),
                  ),
                ),
              ),
              const SizedBox(height: AppSpacing.sm),
              const Text(
                'Nhân viên hoặc kiosk tại đúng phòng khám sẽ quét mã này. QR không chứa thông tin bệnh án.',
                textAlign: TextAlign.center,
              ),
              if (ticket.status != 'TICKET_ISSUED') ...[
                const SizedBox(height: AppSpacing.lg),
                SizedBox(
                  width: double.infinity,
                  child: ElevatedButton.icon(
                    onPressed: () =>
                        context.push('/journey/${ticket.appointmentId}/queue'),
                    icon: const Icon(Icons.people_alt_rounded),
                    label: const Text('Theo dõi hàng đợi'),
                  ),
                ),
              ],
            ],
          ),
        ),
      ),
    ],
  );

  static String _date(DateTime value) =>
      '${value.day.toString().padLeft(2, '0')}/'
      '${value.month.toString().padLeft(2, '0')}/${value.year}';
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
                _DetailRow(label: 'Mã phiếu khám', value: ticket.code),
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
