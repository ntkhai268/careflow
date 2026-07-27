import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:intl/intl.dart';
import '../../config/theme.dart';
import '../../models/appointment.dart';
import '../../services/appointment_service.dart';

/// Appointment detail screen
class AppointmentDetailScreen extends ConsumerStatefulWidget {
  final String appointmentId;

  const AppointmentDetailScreen({super.key, required this.appointmentId});

  @override
  ConsumerState<AppointmentDetailScreen> createState() =>
      _AppointmentDetailScreenState();
}

class _AppointmentDetailScreenState
    extends ConsumerState<AppointmentDetailScreen> {
  Appointment? _appointment;
  bool _isLoading = true;
  bool _isCancelling = false;
  String? _error;

  @override
  void initState() {
    super.initState();
    _loadAppointment();
  }

  Future<void> _loadAppointment() async {
    setState(() { _isLoading = true; _error = null; });
    try {
      final service = ref.read(appointmentServiceProvider);
      final appointment = await service.getAppointmentById(widget.appointmentId);
      setState(() { _appointment = appointment; _isLoading = false; });
    } catch (e) {
      setState(() { _error = e.toString(); _isLoading = false; });
    }
  }

  Future<void> _cancelAppointment() async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(AppRadius.lg),
        ),
        title: const Text('Hủy lịch khám?'),
        content: const Text('Bạn có chắc chắn muốn hủy lịch khám này không?'),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx, false),
            child: const Text('Không'),
          ),
          ElevatedButton(
            onPressed: () => Navigator.pop(ctx, true),
            style: ElevatedButton.styleFrom(backgroundColor: AppColors.error),
            child: const Text('Hủy lịch'),
          ),
        ],
      ),
    );

    if (confirmed != true) return;

    setState(() => _isCancelling = true);
    try {
      final service = ref.read(appointmentServiceProvider);
      final updated = await service.cancelAppointment(widget.appointmentId);
      setState(() { _appointment = updated; _isCancelling = false; });
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(
            content: Text('Đã hủy lịch khám'),
            backgroundColor: AppColors.success,
          ),
        );
      }
    } catch (e) {
      setState(() => _isCancelling = false);
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text('Lỗi: ${e.toString()}'),
            backgroundColor: AppColors.error,
          ),
        );
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppColors.background,
      appBar: AppBar(
        title: const Text('Chi tiết phiếu khám'),
        leading: IconButton(
          icon: const Icon(Icons.arrow_back_rounded),
          onPressed: () => context.pop(),
        ),
      ),
      body: _isLoading
          ? const Center(child: CircularProgressIndicator())
          : _error != null
              ? _buildError()
              : _appointment != null
                  ? _buildContent()
                  : const SizedBox.shrink(),
    );
  }

  Widget _buildError() {
    return Center(
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          Icon(Icons.error_outline, size: 64, color: AppColors.error),
          const SizedBox(height: 16),
          Text('Không tìm thấy phiếu khám'),
          const SizedBox(height: 8),
          TextButton(onPressed: _loadAppointment, child: const Text('Thử lại')),
        ],
      ),
    );
  }

  Widget _buildContent() {
    final appt = _appointment!;
    final dateStr = DateFormat('EEEE, dd/MM/yyyy', 'vi').format(appt.appointmentDate);
    final canCancel = appt.status != 'COMPLETED' && appt.status != 'CANCELLED';

    return Column(
      children: [
        Expanded(
          child: SingleChildScrollView(
            padding: const EdgeInsets.all(AppSpacing.base),
            child: Column(
              children: [
                // Status header
                Container(
                  width: double.infinity,
                  padding: const EdgeInsets.all(AppSpacing.xl),
                  decoration: BoxDecoration(
                    color: appt.statusBgColor,
                    borderRadius: BorderRadius.circular(AppRadius.lg),
                  ),
                  child: Column(
                    children: [
                      Icon(appt.statusIcon, size: 48, color: appt.statusColor),
                      const SizedBox(height: AppSpacing.sm),
                      Text(
                        appt.statusDisplayName,
                        style: TextStyle(
                          fontSize: 20,
                          fontWeight: FontWeight.w700,
                          color: appt.statusColor,
                        ),
                      ),
                    ],
                  ),
                ),
                const SizedBox(height: AppSpacing.base),
                // Info card
                Container(
                  width: double.infinity,
                  padding: const EdgeInsets.all(AppSpacing.lg),
                  decoration: BoxDecoration(
                    color: AppColors.surface,
                    borderRadius: BorderRadius.circular(AppRadius.lg),
                    boxShadow: AppShadows.card,
                    border: Border.all(color: AppColors.cardBorder, width: 0.5),
                  ),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text('Thông tin lịch khám',
                          style: Theme.of(context).textTheme.titleMedium?.copyWith(fontWeight: FontWeight.w700)),
                      const SizedBox(height: AppSpacing.base),
                      if (appt.patientName != null)
                        _buildDetailRow(Icons.person_rounded, 'Bệnh nhân', appt.patientName!),
                      _buildDetailRow(
                        Appointment.departmentIcon(appt.department),
                        'Chuyên khoa',
                        appt.departmentDisplayName,
                      ),
                      _buildDetailRow(Icons.calendar_today_rounded, 'Ngày khám', dateStr),
                      _buildDetailRow(Icons.access_time_rounded, 'Ca khám', appt.timeSlot),
                      if (appt.queueNumber != null)
                        _buildDetailRow(Icons.confirmation_number_rounded, 'Số thứ tự', appt.queueNumber!),
                      if (appt.reason != null && appt.reason!.isNotEmpty)
                        _buildDetailRow(Icons.note_rounded, 'Lý do khám', appt.reason!),
                    ],
                  ),
                ),
                const SizedBox(height: AppSpacing.base),
                // Status timeline
                _buildTimeline(appt),
              ],
            ),
          ),
        ),
        if (canCancel) _buildCancelBar(),
      ],
    );
  }

  Widget _buildDetailRow(IconData icon, String label, String value) {
    return Padding(
      padding: const EdgeInsets.only(bottom: AppSpacing.md),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Icon(icon, color: AppColors.primary, size: 20),
          const SizedBox(width: AppSpacing.md),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(label, style: TextStyle(fontSize: 12, color: AppColors.textSecondary)),
                const SizedBox(height: 2),
                Text(value, style: TextStyle(fontSize: 15, fontWeight: FontWeight.w500, color: AppColors.textPrimary)),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildTimeline(Appointment appt) {
    final statuses = [
      {'key': 'PENDING', 'label': 'Đặt khám', 'icon': Icons.schedule_rounded},
      {'key': 'CONFIRMED', 'label': 'Xác nhận', 'icon': Icons.check_circle_outline_rounded},
      {'key': 'CHECKED_IN', 'label': 'Check-in', 'icon': Icons.login_rounded},
      {'key': 'IN_PROGRESS', 'label': 'Đang khám', 'icon': Icons.medical_services_rounded},
      {'key': 'COMPLETED', 'label': 'Hoàn tất', 'icon': Icons.task_alt_rounded},
    ];

    if (appt.status == 'CANCELLED') {
      return Container(
        width: double.infinity,
        padding: const EdgeInsets.all(AppSpacing.lg),
        decoration: BoxDecoration(
          color: AppColors.errorLight,
          borderRadius: BorderRadius.circular(AppRadius.lg),
        ),
        child: Row(
          children: [
            Icon(Icons.cancel_outlined, color: AppColors.error, size: 24),
            const SizedBox(width: AppSpacing.md),
            Text('Lịch khám đã bị hủy',
                style: TextStyle(fontSize: 15, fontWeight: FontWeight.w600, color: AppColors.error)),
          ],
        ),
      );
    }

    final statusOrder = ['PENDING', 'CONFIRMED', 'CHECKED_IN', 'IN_PROGRESS', 'COMPLETED'];
    final currentIndex = statusOrder.indexOf(appt.status);

    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(AppSpacing.lg),
      decoration: BoxDecoration(
        color: AppColors.surface,
        borderRadius: BorderRadius.circular(AppRadius.lg),
        boxShadow: AppShadows.card,
        border: Border.all(color: AppColors.cardBorder, width: 0.5),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text('Tiến trình',
              style: Theme.of(context).textTheme.titleMedium?.copyWith(fontWeight: FontWeight.w700)),
          const SizedBox(height: AppSpacing.base),
          ...statuses.asMap().entries.map((entry) {
            final i = entry.key;
            final s = entry.value;
            final isDone = i <= currentIndex;
            final isCurrent = i == currentIndex;
            final isLast = i == statuses.length - 1;

            return Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Column(
                  children: [
                    Container(
                      width: 28,
                      height: 28,
                      decoration: BoxDecoration(
                        color: isDone ? AppColors.success : AppColors.cardBorder,
                        shape: BoxShape.circle,
                        border: isCurrent
                            ? Border.all(color: AppColors.primary, width: 2)
                            : null,
                      ),
                      child: Center(
                        child: isDone
                            ? const Icon(Icons.check, size: 16, color: Colors.white)
                            : Icon(s['icon'] as IconData, size: 14, color: AppColors.textHint),
                      ),
                    ),
                    if (!isLast)
                      Container(
                        width: 2,
                        height: 28,
                        color: isDone ? AppColors.success : AppColors.cardBorder,
                      ),
                  ],
                ),
                const SizedBox(width: AppSpacing.md),
                Padding(
                  padding: const EdgeInsets.only(top: 4),
                  child: Text(
                    s['label'] as String,
                    style: TextStyle(
                      fontSize: 14,
                      fontWeight: isCurrent ? FontWeight.w600 : FontWeight.w400,
                      color: isDone ? AppColors.textPrimary : AppColors.textHint,
                    ),
                  ),
                ),
              ],
            );
          }),
        ],
      ),
    );
  }

  Widget _buildCancelBar() {
    return Container(
      padding: const EdgeInsets.all(AppSpacing.base),
      decoration: BoxDecoration(color: AppColors.surface, boxShadow: AppShadows.bottomNav),
      child: SafeArea(
        child: SizedBox(
          width: double.infinity,
          height: 52,
          child: OutlinedButton(
            onPressed: _isCancelling ? null : _cancelAppointment,
            style: OutlinedButton.styleFrom(
              foregroundColor: AppColors.error,
              side: const BorderSide(color: AppColors.error, width: 1.5),
            ),
            child: _isCancelling
                ? const SizedBox(width: 24, height: 24, child: CircularProgressIndicator(strokeWidth: 2))
                : const Text('Hủy lịch khám'),
          ),
        ),
      ),
    );
  }
}
