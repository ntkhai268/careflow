import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:intl/intl.dart';
import '../../config/theme.dart';
import '../../features/journey/application/journey_providers.dart';
import '../../models/appointment.dart';
import '../../models/appointment_payment.dart';
import '../../services/appointment_service.dart';
import '../../services/appointment_payment_store.dart';
import '../../utils/api_error_message.dart';

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
  static const _unauthorizedMessage = 'Bạn không có quyền xem phiếu khám này.';

  Appointment? _appointment;
  AppointmentPaymentReceipt? _paymentReceipt;
  bool _isLoading = true;
  bool _isCancelling = false;
  String? _error;

  @override
  void initState() {
    super.initState();
    _loadAppointment();
  }

  Future<void> _loadAppointment() async {
    setState(() {
      _isLoading = true;
      _error = null;
    });
    final scope = ref.read(journeyAccountScopeProvider);
    if (scope == null) {
      setState(() {
        _error = _unauthorizedMessage;
        _isLoading = false;
      });
      return;
    }
    try {
      final service = ref.read(appointmentServiceProvider);
      final appointment = await service.getAppointmentById(
        widget.appointmentId,
      );
      if (!mounted) return;
      final currentScope = ref.read(journeyAccountScopeProvider);
      if (currentScope != scope || appointment.patientId != scope.patientId) {
        setState(() {
          _appointment = null;
          _error = _unauthorizedMessage;
          _isLoading = false;
        });
        return;
      }
      AppointmentPaymentReceipt? paymentReceipt;
      try {
        paymentReceipt = await ref
            .read(appointmentPaymentStoreProvider)
            .load(appointment.id);
      } catch (_) {
        // Payment receipt is local presentation data and must not hide a real
        // appointment when local storage is unavailable.
      }
      setState(() {
        _appointment = appointment;
        _paymentReceipt = paymentReceipt;
        _isLoading = false;
      });
    } catch (e) {
      if (!mounted) return;
      setState(() {
        _error = ApiErrorMessage.from(
          e,
          fallback: 'Không thể tải thông tin lịch khám. Vui lòng thử lại.',
        );
        _isLoading = false;
      });
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
      final appointment = _appointment;
      final scope = ref.read(journeyAccountScopeProvider);
      if (appointment == null ||
          scope == null ||
          appointment.patientId != scope.patientId) {
        throw StateError(_unauthorizedMessage);
      }
      final service = ref.read(appointmentServiceProvider);
      final updated = await service.cancelAppointment(widget.appointmentId);
      if (updated.patientId != scope.patientId ||
          updated.id != appointment.id) {
        throw StateError(_unauthorizedMessage);
      }
      if (!mounted) return;
      setState(() {
        _appointment = updated;
        _isCancelling = false;
      });
      final retired = await ref
          .read(journeyControllerProvider.notifier)
          .retireAppointment(
            patientId: appointment.patientId,
            appointmentId: appointment.id,
          );
      if (!mounted) return;
      _showCancellationResult(updated, retired: retired);
    } catch (e) {
      if (!mounted) return;
      setState(() => _isCancelling = false);
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(
            ApiErrorMessage.from(
              e,
              fallback: 'Không thể hủy lịch khám. Vui lòng thử lại.',
            ),
          ),
          backgroundColor: AppColors.error,
        ),
      );
    }
  }

  void _showCancellationResult(
    Appointment appointment, {
    required bool retired,
  }) {
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Text(
          retired
              ? 'Đã hủy lịch khám'
              : 'Đã hủy lịch khám, nhưng chưa thể dọn dữ liệu cục bộ.',
        ),
        backgroundColor: retired ? AppColors.success : AppColors.warning,
        action: retired
            ? null
            : SnackBarAction(
                label: 'Thử lại',
                onPressed: () => _retryJourneyCleanup(appointment),
              ),
      ),
    );
  }

  Future<void> _retryJourneyCleanup(Appointment appointment) async {
    final retired = await ref
        .read(journeyControllerProvider.notifier)
        .retireAppointment(
          patientId: appointment.patientId,
          appointmentId: appointment.id,
        );
    if (!mounted) return;
    _showCancellationResult(appointment, retired: retired);
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: AppColors.background,
      appBar: AppBar(
        title: const Text('Chi tiết phiếu khám'),
        leading: IconButton(
          icon: const Icon(Icons.arrow_back_rounded),
          onPressed: () {
            // A post-booking `go` starts this screen without a previous
            // appointment route. Re-enter the appointment tab so its list
            // is rebuilt and fetched instead of showing stale state.
            if (context.canPop()) {
              context.pop();
            } else {
              context.go('/appointments');
            }
          },
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
          Text(
            _error == _unauthorizedMessage
                ? _unauthorizedMessage
                : 'Không tìm thấy phiếu khám',
            textAlign: TextAlign.center,
          ),
          const SizedBox(height: 8),
          TextButton(onPressed: _loadAppointment, child: const Text('Thử lại')),
        ],
      ),
    );
  }

  Widget _buildContent() {
    final appt = _appointment!;
    final dateStr = DateFormat(
      'EEEE, dd/MM/yyyy',
      'vi',
    ).format(appt.appointmentDate);
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
                      Text(
                        'Thông tin lịch khám',
                        style: Theme.of(context).textTheme.titleMedium
                            ?.copyWith(fontWeight: FontWeight.w700),
                      ),
                      const SizedBox(height: AppSpacing.base),
                      if (appt.patientName != null)
                        _buildDetailRow(
                          Icons.person_rounded,
                          'Bệnh nhân',
                          appt.patientName!,
                        ),
                      _buildDetailRow(
                        Appointment.departmentIcon(appt.department),
                        'Chuyên khoa',
                        appt.departmentDisplayName,
                      ),
                      if (appt.roomDisplayName != null)
                        _buildDetailRow(
                          Icons.meeting_room_rounded,
                          'Phòng khám',
                          appt.roomDisplayName!,
                        ),
                      _buildDetailRow(
                        Icons.calendar_today_rounded,
                        'Ngày khám',
                        dateStr,
                      ),
                      _buildDetailRow(
                        Icons.access_time_rounded,
                        'Ca khám',
                        appt.timeSlot,
                      ),
                      if (appt.queueNumber != null)
                        _buildDetailRow(
                          Icons.confirmation_number_rounded,
                          'Số thứ tự',
                          appt.queueNumber!,
                        ),
                      if (appt.reason != null && appt.reason!.isNotEmpty)
                        _buildDetailRow(
                          Icons.note_rounded,
                          'Lý do khám',
                          appt.reason!,
                        ),
                    ],
                  ),
                ),
                const SizedBox(height: AppSpacing.base),
                if (_paymentReceipt != null) ...[
                  _buildPaymentCard(_paymentReceipt!),
                  const SizedBox(height: AppSpacing.base),
                ],
                if (_canOpenVisitTicket(appt)) ...[
                  _buildVisitTicketAction(appt),
                  const SizedBox(height: AppSpacing.base),
                ],
                // Status timeline
                _buildTimeline(appt),
              ],
            ),
          ),
        ),
        if (canCancel) _buildActionBar(),
      ],
    );
  }

  bool _canOpenVisitTicket(Appointment appointment) =>
      appointment.status == 'CONFIRMED' ||
      appointment.status == 'CHECKED_IN' ||
      appointment.status == 'IN_PROGRESS';

  Widget _buildVisitTicketAction(Appointment appointment) {
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(AppSpacing.lg),
      decoration: BoxDecoration(
        color: AppColors.primaryLight,
        borderRadius: BorderRadius.circular(AppRadius.lg),
        border: Border.all(color: AppColors.primary.withValues(alpha: 0.2)),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Icon(Icons.confirmation_number_rounded, color: AppColors.primary),
              const SizedBox(width: AppSpacing.md),
              Expanded(
                child: Text(
                  'Phiếu khám của bạn',
                  style: Theme.of(context).textTheme.titleMedium?.copyWith(
                    fontWeight: FontWeight.w700,
                  ),
                ),
              ),
            ],
          ),
          const SizedBox(height: AppSpacing.sm),
          const Text('Mở để xem mã phiếu, mã QR và thông tin phòng khám.'),
          const SizedBox(height: AppSpacing.md),
          SizedBox(
            width: double.infinity,
            child: ElevatedButton.icon(
              key: const Key('open-journey-detail'),
              onPressed: () =>
                  context.push('/journey/${appointment.id}/ticket'),
              icon: const Icon(Icons.open_in_new_rounded),
              label: const Text('Xem phiếu khám'),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildPaymentCard(AppointmentPaymentReceipt receipt) {
    final color = receipt.isPaid ? AppColors.success : AppColors.warning;
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
          Text(
            'Thanh toán',
            style: Theme.of(
              context,
            ).textTheme.titleMedium?.copyWith(fontWeight: FontWeight.w700),
          ),
          const SizedBox(height: AppSpacing.base),
          _buildDetailRow(
            Icons.receipt_long_rounded,
            'Dịch vụ',
            receipt.serviceName,
          ),
          _buildDetailRow(
            Icons.payments_rounded,
            'Số tiền',
            _formatCurrency(receipt.amount),
          ),
          _buildDetailRow(
            Icons.account_balance_wallet_rounded,
            'Phương thức',
            receipt.method.displayName,
          ),
          Row(
            children: [
              Icon(Icons.verified_rounded, color: color, size: 20),
              const SizedBox(width: AppSpacing.md),
              Text(
                receipt.status.displayName,
                style: TextStyle(
                  color: color,
                  fontSize: 15,
                  fontWeight: FontWeight.w600,
                ),
              ),
            ],
          ),
        ],
      ),
    );
  }

  String _formatCurrency(int amount) =>
      '${NumberFormat('#,###', 'vi_VN').format(amount)} ₫';

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
                Text(
                  label,
                  style: TextStyle(
                    fontSize: 12,
                    color: AppColors.textSecondary,
                  ),
                ),
                const SizedBox(height: 2),
                Text(
                  value,
                  style: TextStyle(
                    fontSize: 15,
                    fontWeight: FontWeight.w500,
                    color: AppColors.textPrimary,
                  ),
                ),
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
      {
        'key': 'CONFIRMED',
        'label': 'Xác nhận',
        'icon': Icons.check_circle_outline_rounded,
      },
      {'key': 'CHECKED_IN', 'label': 'Check-in', 'icon': Icons.login_rounded},
      {
        'key': 'IN_PROGRESS',
        'label': 'Đang khám',
        'icon': Icons.medical_services_rounded,
      },
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
            Text(
              'Lịch khám đã bị hủy',
              style: TextStyle(
                fontSize: 15,
                fontWeight: FontWeight.w600,
                color: AppColors.error,
              ),
            ),
          ],
        ),
      );
    }

    final statusOrder = [
      'PENDING',
      'CONFIRMED',
      'CHECKED_IN',
      'IN_PROGRESS',
      'COMPLETED',
    ];
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
          Text(
            'Tiến trình',
            style: Theme.of(
              context,
            ).textTheme.titleMedium?.copyWith(fontWeight: FontWeight.w700),
          ),
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
                        color: isDone
                            ? AppColors.success
                            : AppColors.cardBorder,
                        shape: BoxShape.circle,
                        border: isCurrent
                            ? Border.all(color: AppColors.primary, width: 2)
                            : null,
                      ),
                      child: Center(
                        child: isDone
                            ? const Icon(
                                Icons.check,
                                size: 16,
                                color: Colors.white,
                              )
                            : Icon(
                                s['icon'] as IconData,
                                size: 14,
                                color: AppColors.textHint,
                              ),
                      ),
                    ),
                    if (!isLast)
                      Container(
                        width: 2,
                        height: 28,
                        color: isDone
                            ? AppColors.success
                            : AppColors.cardBorder,
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
                      color: isDone
                          ? AppColors.textPrimary
                          : AppColors.textHint,
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

  Widget _buildActionBar() {
    return Container(
      padding: const EdgeInsets.all(AppSpacing.base),
      decoration: BoxDecoration(
        color: AppColors.surface,
        boxShadow: AppShadows.bottomNav,
      ),
      child: SafeArea(
        child: Row(
          children: [
            Expanded(
              child: SizedBox(
                height: 52,
                child: OutlinedButton(
                  onPressed: _isCancelling ? null : _cancelAppointment,
                  style: OutlinedButton.styleFrom(
                    foregroundColor: AppColors.error,
                    side: const BorderSide(color: AppColors.error, width: 1.5),
                  ),
                  child: _isCancelling
                      ? const SizedBox(
                          width: 24,
                          height: 24,
                          child: CircularProgressIndicator(strokeWidth: 2),
                        )
                      : const Text('Hủy lịch khám'),
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}
