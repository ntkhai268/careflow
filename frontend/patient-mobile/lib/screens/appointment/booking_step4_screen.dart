import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:intl/intl.dart';
import '../../config/theme.dart';
import '../../models/appointment.dart';
import '../../models/patient.dart';
import '../../services/appointment_service.dart';

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

      await service.createAppointment({
        'patientId': widget.patient.id,
        'patientName': widget.patient.fullName,
        'department': widget.department.code,
        'appointmentDate': dateStr,
        'timeSlot': widget.timeSlot,
        'reason': _reasonController.text.trim().isNotEmpty
            ? _reasonController.text.trim()
            : null,
      });

      if (mounted) {
        _showSuccessDialog();
      }
    } catch (e) {
      if (mounted) {
        setState(() => _isSubmitting = false);
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text('Đặt khám thất bại: ${e.toString()}'),
            backgroundColor: AppColors.error,
          ),
        );
      }
    }
  }

  void _showSuccessDialog() {
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
                'Lịch khám của bạn đã được ghi nhận.\nVui lòng chờ xác nhận.',
                textAlign: TextAlign.center,
                style: Theme.of(ctx).textTheme.bodyMedium,
              ),
              const SizedBox(height: AppSpacing.xl),
              SizedBox(
                width: double.infinity,
                child: ElevatedButton(
                  onPressed: () {
                    Navigator.of(ctx).pop(); // close dialog
                    // Pop all booking screens back to appointment list
                    // Return patientId so appointment list can reload
                    context.go('/');
                  },
                  child: const Text('Về trang chủ'),
                ),
              ),
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
                      border: Border.all(color: AppColors.cardBorder, width: 0.5),
                    ),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          'Thông tin đặt khám',
                          style: Theme.of(context).textTheme.titleMedium?.copyWith(
                            fontWeight: FontWeight.w700,
                          ),
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
                  // Note
                  Container(
                    padding: const EdgeInsets.all(AppSpacing.md),
                    decoration: BoxDecoration(
                      color: AppColors.warningLight,
                      borderRadius: BorderRadius.circular(AppRadius.md),
                    ),
                    child: Row(
                      children: [
                        Icon(Icons.info_outline_rounded, color: AppColors.warning, size: 20),
                        const SizedBox(width: AppSpacing.sm),
                        Expanded(
                          child: Text(
                            'Vui lòng đến trước giờ hẹn 15 phút để làm thủ tục check-in.',
                            style: TextStyle(fontSize: 13, color: AppColors.textPrimary),
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

  Widget _buildStepIndicator() {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: AppSpacing.base, vertical: AppSpacing.md),
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
          Container(width: 28, height: 28, decoration: const BoxDecoration(color: AppColors.success, shape: BoxShape.circle),
            child: const Center(child: Icon(Icons.check, size: 16, color: Colors.white))),
          const SizedBox(height: 4),
          Text(label, style: const TextStyle(fontSize: 11, color: AppColors.success, fontWeight: FontWeight.w600), textAlign: TextAlign.center),
        ],
      ),
    );
  }

  Widget _buildStepActive(int number, String label) {
    return Expanded(
      child: Column(
        children: [
          Container(width: 28, height: 28, decoration: const BoxDecoration(color: AppColors.primary, shape: BoxShape.circle),
            child: Center(child: Text('$number', style: const TextStyle(color: Colors.white, fontSize: 13, fontWeight: FontWeight.w600)))),
          const SizedBox(height: 4),
          Text(label, style: const TextStyle(fontSize: 11, color: AppColors.primary, fontWeight: FontWeight.w600), textAlign: TextAlign.center),
        ],
      ),
    );
  }

  Widget _buildStepLineDone() {
    return Container(width: 20, height: 2, color: AppColors.success, margin: const EdgeInsets.only(bottom: 16));
  }

  Widget _buildInfoRow(IconData icon, String label, String value) {
    return Row(
      children: [
        Icon(icon, color: AppColors.primary, size: 20),
        const SizedBox(width: AppSpacing.md),
        Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(label, style: TextStyle(fontSize: 12, color: AppColors.textSecondary)),
            const SizedBox(height: 2),
            Text(value, style: TextStyle(fontSize: 15, fontWeight: FontWeight.w600, color: AppColors.textPrimary)),
          ],
        ),
      ],
    );
  }

  Widget _buildBottomBar() {
    return Container(
      padding: const EdgeInsets.all(AppSpacing.base),
      decoration: BoxDecoration(color: AppColors.surface, boxShadow: AppShadows.bottomNav),
      child: SafeArea(
        child: SizedBox(
          width: double.infinity,
          height: 52,
          child: ElevatedButton(
            onPressed: _isSubmitting ? null : _submitAppointment,
            child: _isSubmitting
                ? const SizedBox(width: 24, height: 24, child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white))
                : const Text('Xác nhận đặt khám'),
          ),
        ),
      ),
    );
  }
}
