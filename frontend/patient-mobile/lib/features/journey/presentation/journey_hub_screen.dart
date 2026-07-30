import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../config/theme.dart';
import '../application/journey_providers.dart';
import '../data/journey_repository.dart';
import '../domain/journey_models.dart';
import 'clinic_queue_screen.dart';
import 'visit_ticket_screen.dart';
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
                    onPressed: () => Navigator.of(context).push(
                      MaterialPageRoute<void>(
                        builder: (_) =>
                            destination == _PrimaryDestination.ticket
                            ? VisitTicketScreen(appointmentId: appointmentId)
                            : ClinicQueueScreen(appointmentId: appointmentId),
                      ),
                    ),
                    icon: Icon(
                      destination == _PrimaryDestination.ticket
                          ? Icons.confirmation_number_rounded
                          : Icons.people_alt_rounded,
                    ),
                    label: Text(
                      destination == _PrimaryDestination.ticket
                          ? 'Xem phiếu khám'
                          : 'Theo dõi hàng đợi',
                    ),
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
  JourneyStatus.waiting ||
  JourneyStatus.called ||
  JourneyStatus.inConsultation => _PrimaryDestination.queue,
  _ => null,
};

enum _PrimaryDestination { ticket, queue }
