import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../../config/theme.dart';
import '../../features/journey/application/journey_providers.dart';
import '../../models/appointment.dart';
import '../../providers/auth_provider.dart';
import '../../providers/patient_provider.dart';
import '../../services/appointment_service.dart';
import 'package:intl/intl.dart';

/// Appointment list screen — Tab "Phiếu khám"
class AppointmentScreen extends ConsumerStatefulWidget {
  const AppointmentScreen({super.key});

  @override
  ConsumerState<AppointmentScreen> createState() => _AppointmentScreenState();
}

class _AppointmentScreenState extends ConsumerState<AppointmentScreen> {
  List<Appointment> _appointments = [];
  bool _isLoading = false;
  String? _error;

  @override
  void initState() {
    super.initState();
    // Load appointments once we have patient data
    WidgetsBinding.instance.addPostFrameCallback((_) {
      _tryLoadAppointments();
    });
  }

  /// Get the current patient ID from patientProvider.
  String? get _patientId {
    final auth = ref.read(authProvider);
    final patient = ref.read(patientProvider).patient;
    if (auth.status != AuthStatus.authenticated ||
        patient == null ||
        patient.userId != auth.userId) {
      return null;
    }
    return patient.id;
  }

  /// Try loading appointments if patient profile is available.
  void _tryLoadAppointments() {
    final patientId = _patientId;
    if (patientId != null && patientId.isNotEmpty) {
      _loadAppointments(patientId);
    }
  }

  Future<void> _loadAppointments(String patientId) async {
    if (patientId.isEmpty) return;
    setState(() {
      _isLoading = true;
      _error = null;
      _appointments = [];
    });
    try {
      final service = ref.read(appointmentServiceProvider);
      final appointments = await service.getAppointmentsByPatientId(patientId);
      if (!mounted || patientId != _patientId) return;
      setState(() {
        _appointments = appointments
            .where((appointment) => appointment.patientId == patientId)
            .toList();
        _isLoading = false;
      });
    } catch (e) {
      if (!mounted || patientId != _patientId) return;
      setState(() {
        _error = e.toString();
        _isLoading = false;
      });
    }
  }

  Future<void> _openJourney(Appointment appointment) async {
    final patientId = _patientId;
    if (!appointment.allowsActiveJourney ||
        patientId == null ||
        appointment.patientId != patientId) {
      return;
    }
    final activeJourney = ref.read(activeJourneyProvider);
    if (activeJourney?.appointmentId != appointment.id ||
        activeJourney?.patientId != appointment.patientId) {
      try {
        await ref
            .read(journeyControllerProvider.notifier)
            .bootstrap(
              appointment: appointment,
              patientId: appointment.patientId,
            );
      } catch (_) {
        // Navigate with the controller error intact so production displays
        // backend-unavailable instead of manufacturing demo data.
      }
    }
    if (mounted) context.push('/journey/${appointment.id}');
  }

  @override
  Widget build(BuildContext context) {
    // Watch patient state to re-load appointments when patient changes
    final authState = ref.watch(authProvider);
    final patientState = ref.watch(patientProvider);
    final patient = patientState.patient;
    final currentPatientId =
        authState.status == AuthStatus.authenticated &&
            patient?.userId == authState.userId
        ? patient?.id
        : null;
    ref.listen(authProvider.select((auth) => (auth.status, auth.userId)), (
      previous,
      next,
    ) {
      if (previous == next) return;
      setState(() {
        _appointments = [];
        _error = null;
        _isLoading = false;
      });
    });
    ref.listen<String?>(patientProvider.select((state) => state.patient?.id), (
      previous,
      next,
    ) {
      if (previous == next) return;
      setState(() {
        _appointments = [];
        _error = null;
        _isLoading = false;
      });
      if (next != null && next == _patientId && next.isNotEmpty) {
        _loadAppointments(next);
      }
    });

    return Scaffold(
      backgroundColor: AppColors.background,
      appBar: AppBar(
        title: const Text('Phiếu khám'),
        automaticallyImplyLeading: false,
      ),
      body: _isLoading
          ? const Center(child: CircularProgressIndicator())
          : _error != null
          ? _buildErrorState(currentPatientId)
          : _appointments.isEmpty
          ? _buildEmptyState()
          : RefreshIndicator(
              onRefresh: () => _loadAppointments(currentPatientId ?? ''),
              child: _buildAppointmentList(),
            ),
      floatingActionButton: FloatingActionButton.extended(
        onPressed: () async {
          final result = await context.push('/booking/step1');
          if (result != null && result is String) {
            // result is patientId — reload appointments for that patient
            _loadAppointments(result);
          } else {
            // Reload with current patient
            _tryLoadAppointments();
          }
        },
        backgroundColor: AppColors.primary,
        foregroundColor: AppColors.textOnPrimary,
        icon: const Icon(Icons.add_rounded),
        label: const Text('Đặt khám'),
      ),
    );
  }

  Widget _buildEmptyState() {
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(AppSpacing.xxl),
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Container(
              width: 120,
              height: 120,
              decoration: BoxDecoration(
                color: AppColors.primarySurface,
                shape: BoxShape.circle,
              ),
              child: Icon(
                Icons.calendar_month_rounded,
                size: 56,
                color: AppColors.primary,
              ),
            ),
            const SizedBox(height: AppSpacing.xl),
            Text(
              'Chưa có phiếu khám',
              style: Theme.of(context).textTheme.titleLarge,
            ),
            const SizedBox(height: AppSpacing.sm),
            Text(
              'Đặt lịch khám bệnh ngay để được\nphục vụ nhanh chóng và thuận tiện',
              textAlign: TextAlign.center,
              style: Theme.of(context).textTheme.bodyMedium,
            ),
            const SizedBox(height: AppSpacing.xxl),
            ElevatedButton.icon(
              onPressed: () async {
                final result = await context.push('/booking/step1');
                if (result != null && result is String) {
                  _loadAppointments(result);
                }
              },
              icon: const Icon(Icons.add_rounded),
              label: const Text('Đặt khám ngay'),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildErrorState(String? patientId) {
    return Center(
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          Icon(Icons.error_outline, size: 64, color: AppColors.error),
          const SizedBox(height: 16),
          Text('Đã xảy ra lỗi', style: Theme.of(context).textTheme.titleMedium),
          const SizedBox(height: 8),
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: AppSpacing.xl),
            child: Text(
              _error!,
              textAlign: TextAlign.center,
              style: Theme.of(context).textTheme.bodySmall,
            ),
          ),
          const SizedBox(height: 8),
          TextButton(
            onPressed: () => _loadAppointments(patientId ?? ''),
            child: const Text('Thử lại'),
          ),
        ],
      ),
    );
  }

  Widget _buildAppointmentList() {
    return ListView.builder(
      padding: const EdgeInsets.all(AppSpacing.base),
      itemCount: _appointments.length,
      itemBuilder: (context, index) {
        final appt = _appointments[index];
        return _AppointmentCard(
          appointment: appt,
          onTap: () => context.push('/appointment/${appt.id}'),
          onJourneyTap: appt.allowsActiveJourney
              ? () => _openJourney(appt)
              : null,
        );
      },
    );
  }
}

class _AppointmentCard extends StatelessWidget {
  final Appointment appointment;
  final VoidCallback onTap;
  final VoidCallback? onJourneyTap;

  const _AppointmentCard({
    required this.appointment,
    required this.onTap,
    required this.onJourneyTap,
  });

  @override
  Widget build(BuildContext context) {
    final dateStr = DateFormat(
      'dd/MM/yyyy',
    ).format(appointment.appointmentDate);

    return Container(
      margin: const EdgeInsets.only(bottom: AppSpacing.md),
      decoration: BoxDecoration(
        color: AppColors.surface,
        borderRadius: BorderRadius.circular(AppRadius.lg),
        boxShadow: AppShadows.card,
        border: Border.all(color: AppColors.cardBorder, width: 0.5),
      ),
      child: Material(
        color: Colors.transparent,
        borderRadius: BorderRadius.circular(AppRadius.lg),
        child: InkWell(
          onTap: onTap,
          borderRadius: BorderRadius.circular(AppRadius.lg),
          child: Padding(
            padding: const EdgeInsets.all(AppSpacing.base),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                // Header: department + status
                Row(
                  children: [
                    Container(
                      padding: const EdgeInsets.all(10),
                      decoration: BoxDecoration(
                        color: AppColors.primarySurface,
                        borderRadius: BorderRadius.circular(AppRadius.md),
                      ),
                      child: Icon(
                        Appointment.departmentIcon(appointment.department),
                        color: AppColors.primary,
                        size: 24,
                      ),
                    ),
                    const SizedBox(width: AppSpacing.md),
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            appointment.departmentDisplayName,
                            style: Theme.of(context).textTheme.titleMedium
                                ?.copyWith(fontWeight: FontWeight.w600),
                          ),
                          if (appointment.patientName != null)
                            Text(
                              appointment.patientName!,
                              style: Theme.of(context).textTheme.bodySmall,
                            ),
                        ],
                      ),
                    ),
                    Container(
                      padding: const EdgeInsets.symmetric(
                        horizontal: 10,
                        vertical: 4,
                      ),
                      decoration: BoxDecoration(
                        color: appointment.statusBgColor,
                        borderRadius: BorderRadius.circular(AppRadius.full),
                      ),
                      child: Row(
                        mainAxisSize: MainAxisSize.min,
                        children: [
                          Icon(
                            appointment.statusIcon,
                            size: 14,
                            color: appointment.statusColor,
                          ),
                          const SizedBox(width: 4),
                          Text(
                            appointment.statusDisplayName,
                            style: TextStyle(
                              fontSize: 12,
                              fontWeight: FontWeight.w600,
                              color: appointment.statusColor,
                            ),
                          ),
                        ],
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: AppSpacing.md),
                const Divider(),
                const SizedBox(height: AppSpacing.sm),
                // Date + time
                Row(
                  children: [
                    Icon(
                      Icons.calendar_today_rounded,
                      size: 16,
                      color: AppColors.textSecondary,
                    ),
                    const SizedBox(width: 6),
                    Text(
                      dateStr,
                      style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                        fontWeight: FontWeight.w500,
                        color: AppColors.textPrimary,
                      ),
                    ),
                    const SizedBox(width: AppSpacing.base),
                    Icon(
                      Icons.access_time_rounded,
                      size: 16,
                      color: AppColors.textSecondary,
                    ),
                    const SizedBox(width: 6),
                    Text(
                      appointment.timeSlot,
                      style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                        fontWeight: FontWeight.w500,
                        color: AppColors.textPrimary,
                      ),
                    ),
                    const Spacer(),
                    IconButton(
                      key: Key('open-journey-${appointment.id}'),
                      tooltip: 'Xem hành trình khám',
                      onPressed: onJourneyTap,
                      icon: const Icon(Icons.route_rounded),
                      color: AppColors.primary,
                    ),
                  ],
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}
