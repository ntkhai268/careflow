import 'dart:async';

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
  AppointmentPaymentMethod _paymentMethod =
      AppointmentPaymentMethod.onlineMock;
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

      unawaited(
        _savePaymentReceipt(appointment.id).catchError((_) {}),
      );

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
        _showSuccessDialog(appointment);
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

  void _showSuccessDialog(Appointment appointment) {
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
                    ? '${_paymentMethod == AppointmentPaymentMethod.onlineMock ? 'Đã thanh toán phí khám trực tuyến. ' : 'Đã ghi nhận thanh toán tại bệnh viện. '}'
                          'Lịch khám và phiếu khám đã sẵn sàng.'
                    : 'Lịch khám đang chờ xác nhận.',
                textAlign: TextAlign.center,
                style: Theme.of(ctx).textTheme.bodyMedium,
              ),
              const SizedBox(height: AppSpacing.xl),
              SizedBox(
                width: double.infinity,
                child: ElevatedButton(
                  onPressed: () {
                    Navigator.of(ctx).pop();
                    if (canOpenJourney) {
                      context.go('/journey/${appointment.id}/ticket');
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

  @override
  Widget build(BuildContext context) {
    final dateStr = DateFormat('EEEE, dd/MM/yyyy', 'vi').format(widget.date);

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
                  _buildServiceCard(),
                  const SizedBox(height: AppSpacing.lg),
                  _buildPaymentCard(),
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

  Widget _buildServiceCard() {
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(AppSpacing.lg),
      decoration: BoxDecoration(
        color: AppColors.primarySurface,
        borderRadius: BorderRadius.circular(AppRadius.lg),
        border: Border.all(color: AppColors.primaryLight),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              const Icon(
                Icons.medical_services_rounded,
                color: AppColors.primary,
              ),
              const SizedBox(width: AppSpacing.sm),
              Expanded(
                child: Text(
                  'Dịch vụ khám',
                  style: Theme.of(context).textTheme.titleMedium?.copyWith(
                    fontWeight: FontWeight.w700,
                  ),
                ),
              ),
              const Icon(Icons.check_circle_rounded, color: AppColors.success),
            ],
          ),
          const SizedBox(height: AppSpacing.md),
          Text(_service.name, style: Theme.of(context).textTheme.titleLarge),
          const SizedBox(height: AppSpacing.xs),
          Text(_service.description),
          const SizedBox(height: AppSpacing.md),
          Row(
            children: [
              const Icon(
                Icons.schedule_rounded,
                size: 18,
                color: AppColors.textSecondary,
              ),
              const SizedBox(width: AppSpacing.xs),
              Text('Khoảng ${_service.estimatedMinutes} phút'),
              const Spacer(),
              Text(
                _currency(_service.price),
                style: Theme.of(context).textTheme.titleMedium?.copyWith(
                  color: AppColors.primaryDark,
                  fontWeight: FontWeight.w700,
                ),
              ),
            ],
          ),
        ],
      ),
    );
  }

  Future<void> _savePaymentReceipt(String appointmentId) async {
    try {
      await AppointmentPaymentStore().save(
        AppointmentPaymentReceipt(
          appointmentId: appointmentId,
          serviceCode: _service.code,
          serviceName: _service.name,
          amount: _service.price,
          method: _paymentMethod,
          status: _paymentMethod == AppointmentPaymentMethod.onlineMock
              ? AppointmentPaymentStatus.paid
              : AppointmentPaymentStatus.dueAtHospital,
          createdAt: DateTime.now().toUtc(),
        ),
      );
    } catch (_) {
      // The appointment itself is authoritative. Until the payment API is
      // available, a storage/plugin issue must not turn a successful booking
      // into a booking error.
    }
  }

  Widget _buildPaymentCard() {
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
            'Thanh toán phí khám',
            style: Theme.of(context).textTheme.titleMedium?.copyWith(
              fontWeight: FontWeight.w700,
            ),
          ),
          const SizedBox(height: AppSpacing.xs),
          const Text('Chọn cách thanh toán trước khi nhận phiếu khám.'),
          const SizedBox(height: AppSpacing.md),
          _paymentOption(
            AppointmentPaymentMethod.onlineMock,
            Icons.account_balance_wallet_rounded,
            'Thanh toán trực tuyến',
            'Mô phỏng cổng thanh toán an toàn',
          ),
          const SizedBox(height: AppSpacing.sm),
          _paymentOption(
            AppointmentPaymentMethod.cashAtHospital,
            Icons.payments_rounded,
            'Tiền mặt tại bệnh viện',
            'Thanh toán tại quầy trước khi tiếp nhận',
          ),
        ],
      ),
    );
  }

  Widget _paymentOption(
    AppointmentPaymentMethod method,
    IconData icon,
    String title,
    String subtitle,
  ) {
    final selected = _paymentMethod == method;
    return Semantics(
      button: true,
      selected: selected,
      label: '$title. $subtitle',
      child: InkWell(
        borderRadius: BorderRadius.circular(AppRadius.md),
        onTap: _isSubmitting
            ? null
            : () => setState(() => _paymentMethod = method),
        child: Container(
          padding: const EdgeInsets.all(AppSpacing.md),
          decoration: BoxDecoration(
            color: selected ? AppColors.primarySurface : AppColors.surface,
            borderRadius: BorderRadius.circular(AppRadius.md),
            border: Border.all(
              color: selected ? AppColors.primary : AppColors.cardBorder,
              width: selected ? 1.5 : 1,
            ),
          ),
          child: Row(
            children: [
              Icon(
                icon,
                color: selected
                    ? AppColors.primary
                    : AppColors.textSecondary,
              ),
              const SizedBox(width: AppSpacing.md),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      title,
                      style: const TextStyle(fontWeight: FontWeight.w600),
                    ),
                    const SizedBox(height: 2),
                    Text(subtitle, style: Theme.of(context).textTheme.bodySmall),
                  ],
                ),
              ),
              Icon(
                selected
                    ? Icons.radio_button_checked_rounded
                    : Icons.radio_button_unchecked_rounded,
                color: selected ? AppColors.primary : AppColors.textHint,
              ),
            ],
          ),
        ),
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

  String _currency(int amount) {
    final digits = amount.toString();
    final buffer = StringBuffer();
    for (var index = 0; index < digits.length; index++) {
      if (index > 0 && (digits.length - index) % 3 == 0) {
        buffer.write('.');
      }
      buffer.write(digits[index]);
    }
    return '${buffer.toString()} ₫';
  }
}
