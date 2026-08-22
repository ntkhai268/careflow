import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:intl/intl.dart';
import '../../config/theme.dart';
import '../../features/journey/application/journey_providers.dart';
import '../../models/appointment.dart';
import '../../models/appointment_payment.dart';
import '../../models/appointment_service_option.dart';
import '../../models/patient.dart';
import '../../services/appointment_service.dart';
import '../../services/appointment_payment_store.dart';

/// Booking Step 4: Confirm and submit
class BookingStep4Screen extends ConsumerStatefulWidget {
  final Patient patient;
  final Department department;
  final DateTime date;
  final String timeSlot;

  const BookingStep4Screen({
    super.key,
    required this.patient,
    required this.department,
    required this.date,
    required this.timeSlot,
  });

  @override
  ConsumerState<BookingStep4Screen> createState() => _BookingStep4ScreenState();
}

class _BookingStep4ScreenState extends ConsumerState<BookingStep4Screen> {
  final _reasonController = TextEditingController();
  final _service = AppointmentServiceOption.generalConsultation;
  bool _isSubmitting = false;

  @override
  void dispose() {
    _reasonController.dispose();
    super.dispose();
  }

  Future<void> _submitAppointment() async {
    setState(() => _isSubmitting = true);

    try {
      final service = ref.read(appointmentServiceProvider);
      final dateStr = DateFormat('yyyy-MM-dd').format(widget.date);

      final appointment = await service.createAppointment({
        'patientId': widget.patient.id,
        'patientName': widget.patient.fullName,
        'department': widget.department.code,
        'appointmentDate': dateStr,
        'timeSlot': widget.timeSlot,
        'reason': _reasonController.text.trim().isNotEmpty
            ? _reasonController.text.trim()
            : null,
      });

      final demoMode = ref.read(demoModeProvider);
      AppointmentPaymentReceipt? receipt;
      var receiptSaved = false;
      if (demoMode) {
        receipt = AppointmentPaymentReceipt(
          appointmentId: appointment.id,
          serviceCode: _service.code,
          serviceName: _service.name,
          amount: _service.price,
          method: AppointmentPaymentMethod.onlineMock,
          status: AppointmentPaymentStatus.paid,
          createdAt: DateTime.now().toUtc(),
        );
        receiptSaved = true;
        try {
          await ref.read(appointmentPaymentStoreProvider).save(receipt);
        } catch (_) {
          // A local receipt failure must not roll back a successful appointment.
          receiptSaved = false;
        }
      }

      if (appointment.allowsActiveJourney) {
        try {
          await ref
              .read(journeyControllerProvider.notifier)
              .bootstrap(
                appointment: appointment,
                patientId: widget.patient.id,
              );
        } catch (_) {
          // The real booking remains successful when the journey backend is
          // unavailable. The ticket screen renders the controller's real error.
        }
      }

      if (mounted) {
        _showSuccessDialog(
          appointment,
          receipt: receipt,
          receiptSaved: receiptSaved,
          demoMode: demoMode,
        );
      }
    } catch (e) {
      if (mounted) {
        setState(() => _isSubmitting = false);
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(appointmentBookingErrorMessage(e)),
            backgroundColor: AppColors.error,
          ),
        );
      }
    }
  }

  void _showSuccessDialog(
    Appointment appointment, {
    required AppointmentPaymentReceipt? receipt,
    required bool receiptSaved,
    required bool demoMode,
  }) {
    final canOpenJourney = appointment.allowsActiveJourney;
    showDialog(
      context: context,
      barrierDismissible: false,
      builder: (ctx) => Dialog(
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(AppRadius.xl),
        ),
        child: Padding(
          padding: const EdgeInsets.all(AppSpacing.xl),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Container(
                width: 80,
                height: 80,
                decoration: BoxDecoration(
                  color: AppColors.successLight,
                  shape: BoxShape.circle,
                ),
                child: const Icon(
                  Icons.check_circle_rounded,
                  color: AppColors.success,
                  size: 48,
                ),
              ),
              const SizedBox(height: AppSpacing.lg),
              Text(
                'Đặt khám thành công!',
                style: Theme.of(ctx).textTheme.titleLarge?.copyWith(
                  fontWeight: FontWeight.w700,
                  color: AppColors.success,
                ),
              ),
              const SizedBox(height: AppSpacing.sm),
              Text(
                canOpenJourney
                    ? 'Lịch khám của bạn đã được ghi nhận.\n'
                          'Phiếu khám đã sẵn sàng để theo dõi.'
                    : 'Lịch khám đang chờ xác nhận.',
                textAlign: TextAlign.center,
                style: Theme.of(ctx).textTheme.bodyMedium,
              ),
              const SizedBox(height: AppSpacing.md),
              if (demoMode && receipt != null)
                _buildPaymentReceiptSummary(
                  ctx,
                  receipt: receipt,
                  receiptSaved: receiptSaved,
                )
              else
                _buildPaymentPendingSummary(ctx),
              const SizedBox(height: AppSpacing.xl),
              SizedBox(
                width: double.infinity,
                child: ElevatedButton(
                  onPressed: () {
                    Navigator.of(ctx).pop();
                    if (canOpenJourney) {
                      // Keep the booking result in the appointment flow. The
                      // ticket route is nested under JourneyHubScreen, so
                      // navigating there makes the system Back button reveal
                      // the Journey screen again.
                      context.go('/appointment/${appointment.id}');
                    }
                  },
                  child: Text(canOpenJourney ? 'Xem phiếu khám' : 'Hoàn tất'),
                ),
              ),
              if (canOpenJourney) ...[
                const SizedBox(height: AppSpacing.sm),
                SizedBox(
                  width: double.infinity,
                  child: TextButton(
                    onPressed: () {
                      Navigator.of(ctx).pop();
                      context.go('/?tab=1');
                    },
                    child: const Text('Về danh sách lịch khám'),
                  ),
                ),
              ],
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildPaymentPendingSummary(BuildContext context) => Container(
    width: double.infinity,
    padding: const EdgeInsets.all(AppSpacing.md),
    decoration: BoxDecoration(
      color: AppColors.warningLight,
      borderRadius: BorderRadius.circular(AppRadius.md),
    ),
    child: const Text(
      'Chi phí khám sẽ được thanh toán theo hướng dẫn của bệnh viện. Chưa có giao dịch nào được ghi nhận trên ứng dụng.',
      textAlign: TextAlign.center,
    ),
  );

  Widget _buildPaymentReceiptSummary(
    BuildContext context, {
    required AppointmentPaymentReceipt receipt,
    required bool receiptSaved,
  }) {
    final color = receipt.isPaid ? AppColors.success : AppColors.warning;
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(AppSpacing.md),
      decoration: BoxDecoration(
        color: receipt.isPaid ? AppColors.successLight : AppColors.warningLight,
        borderRadius: BorderRadius.circular(AppRadius.md),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            receipt.serviceName,
            style: Theme.of(
              context,
            ).textTheme.titleSmall?.copyWith(fontWeight: FontWeight.w700),
          ),
          const SizedBox(height: 4),
          Text(_formatCurrency(receipt.amount)),
          const SizedBox(height: 4),
          Text(
            '${receipt.status.displayName} • ${receipt.method.displayName}',
            style: TextStyle(color: color, fontWeight: FontWeight.w600),
          ),
          if (!receiptSaved) ...[
            const SizedBox(height: 6),
            const Text(
              'Lịch đã tạo nhưng chưa lưu được biên lai trên thiết bị.',
              style: TextStyle(color: AppColors.error, fontSize: 12),
            ),
          ],
        ],
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final dateStr = DateFormat('EEEE, dd/MM/yyyy', 'vi').format(widget.date);
    final demoMode = ref.watch(demoModeProvider);

    return Scaffold(
      backgroundColor: AppColors.background,
      appBar: AppBar(
        title: const Text('Xác nhận đặt khám'),
        leading: IconButton(
          icon: const Icon(Icons.arrow_back_rounded),
          onPressed: () => context.pop(),
        ),
      ),
      body: Column(
        children: [
          _buildStepIndicator(),
          Expanded(
            child: SingleChildScrollView(
              padding: const EdgeInsets.all(AppSpacing.base),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  // Summary card
                  Container(
                    width: double.infinity,
                    padding: const EdgeInsets.all(AppSpacing.lg),
                    decoration: BoxDecoration(
                      color: AppColors.surface,
                      borderRadius: BorderRadius.circular(AppRadius.lg),
                      boxShadow: AppShadows.card,
                      border: Border.all(
                        color: AppColors.cardBorder,
                        width: 0.5,
                      ),
                    ),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          'Thông tin đặt khám',
                          style: Theme.of(context).textTheme.titleMedium
                              ?.copyWith(fontWeight: FontWeight.w700),
                        ),
                        const SizedBox(height: AppSpacing.base),
                        _buildInfoRow(
                          Icons.person_rounded,
                          'Bệnh nhân',
                          widget.patient.fullName,
                        ),
                        const Divider(height: AppSpacing.lg),
                        _buildInfoRow(
                          Appointment.departmentIcon(widget.department.code),
                          'Chuyên khoa',
                          widget.department.name,
                        ),
                        const Divider(height: AppSpacing.lg),
                        _buildInfoRow(
                          Icons.calendar_today_rounded,
                          'Ngày khám',
                          dateStr,
                        ),
                        const Divider(height: AppSpacing.lg),
                        _buildInfoRow(
                          Icons.access_time_rounded,
                          'Ca khám',
                          widget.timeSlot,
                        ),
                      ],
                    ),
                  ),
                  const SizedBox(height: AppSpacing.lg),
                  // Reason input
                  Text(
                    'Lý do khám (không bắt buộc)',
                    style: Theme.of(context).textTheme.titleMedium?.copyWith(
                      fontWeight: FontWeight.w600,
                    ),
                  ),
                  const SizedBox(height: AppSpacing.sm),
                  TextField(
                    controller: _reasonController,
                    maxLines: 3,
                    decoration: const InputDecoration(
                      hintText: 'VD: Đau đầu, sốt, ho kéo dài...',
                    ),
                  ),
                  const SizedBox(height: AppSpacing.lg),
                  _buildPaymentSection(demoMode),
                  const SizedBox(height: AppSpacing.lg),
                  // Note
                  Container(
                    padding: const EdgeInsets.all(AppSpacing.md),
                    decoration: BoxDecoration(
                      color: AppColors.warningLight,
                      borderRadius: BorderRadius.circular(AppRadius.md),
                    ),
                    child: Row(
                      children: [
                        Icon(
                          Icons.info_outline_rounded,
                          color: AppColors.warning,
                          size: 20,
                        ),
                        const SizedBox(width: AppSpacing.sm),
                        Expanded(
                          child: Text(
                            'Vui lòng đến trước giờ hẹn 15 phút để làm thủ tục check-in.',
                            style: TextStyle(
                              fontSize: 13,
                              color: AppColors.textPrimary,
                            ),
                          ),
                        ),
                      ],
                    ),
                  ),
                ],
              ),
            ),
          ),
          _buildBottomBar(),
        ],
      ),
    );
  }

  Widget _buildPaymentSection(bool demoMode) {
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
            demoMode ? 'Thanh toán phí khám (demo)' : 'Chi phí khám dự kiến',
            style: Theme.of(
              context,
            ).textTheme.titleMedium?.copyWith(fontWeight: FontWeight.w700),
          ),
          const SizedBox(height: AppSpacing.sm),
          Row(
            children: [
              Expanded(child: Text(_service.name)),
              Text(
                _formatCurrency(_service.price),
                style: const TextStyle(fontWeight: FontWeight.w700),
              ),
            ],
          ),
          const SizedBox(height: AppSpacing.sm),
          Text(
            _service.description,
            style: TextStyle(
              color: AppColors.textSecondary,
              fontSize: 12,
              height: 1.35,
            ),
          ),
          const SizedBox(height: AppSpacing.sm),
          ListTile(
            contentPadding: EdgeInsets.zero,
            leading: const Icon(Icons.account_balance_wallet_rounded),
            title: Text(
              demoMode ? 'Thanh toán trực tuyến' : 'Thanh toán tại bệnh viện',
            ),
            subtitle: Text(
              demoMode
                  ? 'Chỉ là mô phỏng, không phát sinh giao dịch thật'
                  : 'Thực hiện theo hướng dẫn của bệnh viện sau khi đặt lịch',
            ),
            trailing: Icon(
              demoMode ? Icons.science_outlined : Icons.info_outline_rounded,
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildStepIndicator() {
    return Container(
      padding: const EdgeInsets.symmetric(
        horizontal: AppSpacing.base,
        vertical: AppSpacing.md,
      ),
      color: AppColors.surface,
      child: Row(
        children: [
          _buildStepDone(1, 'Hồ sơ'),
          _buildStepLineDone(),
          _buildStepDone(2, 'Chuyên khoa'),
          _buildStepLineDone(),
          _buildStepDone(3, 'Ngày & Ca'),
          _buildStepLineDone(),
          _buildStepActive(4, 'Xác nhận'),
        ],
      ),
    );
  }

  Widget _buildStepDone(int number, String label) {
    return Expanded(
      child: Column(
        children: [
          Container(
            width: 28,
            height: 28,
            decoration: const BoxDecoration(
              color: AppColors.success,
              shape: BoxShape.circle,
            ),
            child: const Center(
              child: Icon(Icons.check, size: 16, color: Colors.white),
            ),
          ),
          const SizedBox(height: 4),
          Text(
            label,
            style: const TextStyle(
              fontSize: 11,
              color: AppColors.success,
              fontWeight: FontWeight.w600,
            ),
            textAlign: TextAlign.center,
          ),
        ],
      ),
    );
  }

  Widget _buildStepActive(int number, String label) {
    return Expanded(
      child: Column(
        children: [
          Container(
            width: 28,
            height: 28,
            decoration: const BoxDecoration(
              color: AppColors.primary,
              shape: BoxShape.circle,
            ),
            child: Center(
              child: Text(
                '$number',
                style: const TextStyle(
                  color: Colors.white,
                  fontSize: 13,
                  fontWeight: FontWeight.w600,
                ),
              ),
            ),
          ),
          const SizedBox(height: 4),
          Text(
            label,
            style: const TextStyle(
              fontSize: 11,
              color: AppColors.primary,
              fontWeight: FontWeight.w600,
            ),
            textAlign: TextAlign.center,
          ),
        ],
      ),
    );
  }

  Widget _buildStepLineDone() {
    return Container(
      width: 20,
      height: 2,
      color: AppColors.success,
      margin: const EdgeInsets.only(bottom: 16),
    );
  }

  Widget _buildInfoRow(IconData icon, String label, String value) {
    return Row(
      children: [
        Icon(icon, color: AppColors.primary, size: 20),
        const SizedBox(width: AppSpacing.md),
        Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              label,
              style: TextStyle(fontSize: 12, color: AppColors.textSecondary),
            ),
            const SizedBox(height: 2),
            Text(
              value,
              style: TextStyle(
                fontSize: 15,
                fontWeight: FontWeight.w600,
                color: AppColors.textPrimary,
              ),
            ),
          ],
        ),
      ],
    );
  }

  Widget _buildBottomBar() {
    return Container(
      padding: const EdgeInsets.all(AppSpacing.base),
      decoration: BoxDecoration(
        color: AppColors.surface,
        boxShadow: AppShadows.bottomNav,
      ),
      child: SafeArea(
        child: SizedBox(
          width: double.infinity,
          height: 52,
          child: ElevatedButton(
            onPressed: _isSubmitting ? null : _submitAppointment,
            child: _isSubmitting
                ? const SizedBox(
                    width: 24,
                    height: 24,
                    child: CircularProgressIndicator(
                      strokeWidth: 2,
                      color: Colors.white,
                    ),
                  )
                : const Text('Xác nhận đặt khám'),
          ),
        ),
      ),
    );
  }

  String _formatCurrency(int amount) {
    final formatted = NumberFormat('#,###', 'vi_VN').format(amount);
    return '$formatted ₫';
  }
}
