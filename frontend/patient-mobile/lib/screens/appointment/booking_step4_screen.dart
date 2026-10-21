import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:intl/intl.dart';
import 'package:uuid/uuid.dart';
import '../../config/theme.dart';
import '../../features/journey/application/journey_providers.dart';
import '../../models/appointment.dart';
import '../../services/service_catalog_service.dart';
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
  final _bookingKey = const Uuid().v4();
  ServicePrice? _service;
  List<ServicePrice> _services = [];
  String? _priceError;
  bool _priceLoading = true;

  @override
  void initState() {
    super.initState();
    _loadPrices();
  }

  Future<void> _loadPrices() async {
    setState(() {
      _priceLoading = true;
      _priceError = null;
      _service = null;
    });
    try {
      final prices = await ref.read(serviceCatalogServiceProvider).list();
      if (prices.isEmpty) {
        throw const FormatException('Chưa có dịch vụ có bảng giá hiệu lực.');
      }
      if (mounted) {
        setState(() {
          _services = prices;
          _service = prices.first;
        });
      }
    } catch (e) {
      if (mounted) {
        setState(
          () => _priceError = e is FormatException
              ? e.message.toString()
              : 'Không thể tải bảng giá. Vui lòng thử lại.',
        );
      }
    } finally {
      if (mounted) setState(() => _priceLoading = false);
    }
  }

  Future<void> _chooseService() async {
    var query = '';
    final choice = await showModalBottomSheet<ServicePrice>(
      context: context,
      showDragHandle: true,
      builder: (ctx) => StatefulBuilder(
        builder: (ctx, update) => SafeArea(
          child: ListView(
            shrinkWrap: true,
            children: [
              const Padding(
                padding: EdgeInsets.all(16),
                child: Text(
                  'Chọn dịch vụ',
                  style: TextStyle(fontSize: 20, fontWeight: FontWeight.bold),
                ),
              ),
              Padding(
                padding: const EdgeInsets.all(16),
                child: TextField(
                  decoration: const InputDecoration(labelText: 'Tìm dịch vụ'),
                  onChanged: (value) =>
                      update(() => query = value.trim().toLowerCase()),
                ),
              ),
              for (final service in _services.where(
                (s) =>
                    s.name.toLowerCase().contains(query) ||
                    s.code.toLowerCase().contains(query),
              ))
                ListTile(
                  title: Text(service.name),
                  subtitle: Text(_formatCurrency(service.amount)),
                  trailing: service.code == _service?.code
                      ? const Icon(Icons.check)
                      : null,
                  onTap: () => Navigator.pop(ctx, service),
                ),
            ],
          ),
        ),
      ),
    );
    if (choice != null && mounted) setState(() => _service = choice);
  }

  bool _isSubmitting = false;

  @override
  void dispose() {
    _reasonController.dispose();
    super.dispose();
  }

  Future<void> _submitAppointment() async {
    final selectedService = _service;
    if (selectedService == null || _priceLoading) return;
    setState(() => _isSubmitting = true);

    try {
      final service = ref.read(appointmentServiceProvider);
      final dateStr = DateFormat('yyyy-MM-dd').format(widget.date);

      final appointment = await service.createAppointment({
        'serviceCode': selectedService.code,
        'priceVersion': selectedService.version,
        'patientId': widget.patient.id,
        'patientName': widget.patient.fullName,
        'department': widget.department.code,
        'appointmentDate': dateStr,
        'timeSlot': widget.timeSlot,
        'reason': _reasonController.text.trim().isNotEmpty
            ? _reasonController.text.trim()
            : null,
      }, idempotencyKey: _bookingKey);

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
        _loadPrices();
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
                    ? 'Lịch khám của bạn đã được ghi nhận.\n'
                          'Phiếu khám đã sẵn sàng để theo dõi.'
                    : 'Lịch khám đang chờ xác nhận.',
                textAlign: TextAlign.center,
                style: Theme.of(ctx).textTheme.bodyMedium,
              ),
              const SizedBox(height: AppSpacing.md),
              if (appointment.quotedAmount != null)
                Text(
                  '${appointment.serviceName ?? "Dịch vụ khám"}: ${_formatCurrency(appointment.quotedAmount!)}',
                ),
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
                  _buildPaymentSection(),
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

  Widget _buildPaymentSection() {
    if (_priceLoading) {
      return const Center(child: CircularProgressIndicator());
    }
    if (_priceError != null) {
      return Column(
        children: [
          Text(_priceError!, style: const TextStyle(color: AppColors.error)),
          TextButton(
            onPressed: _loadPrices,
            child: const Text('Tải lại bảng giá'),
          ),
        ],
      );
    }
    final service = _service!;
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(AppSpacing.lg),
      decoration: BoxDecoration(
        color: AppColors.surface,
        borderRadius: BorderRadius.circular(AppRadius.lg),
        border: Border.all(color: AppColors.cardBorder),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          const Text(
            'Chi phí khám dự kiến',
            style: TextStyle(fontWeight: FontWeight.w700),
          ),
          const SizedBox(height: 8),
          ListTile(
            contentPadding: EdgeInsets.zero,
            title: Text(service.name),
            subtitle: Text(service.description),
            trailing: Text(_formatCurrency(service.amount)),
            onTap: _isSubmitting ? null : _chooseService,
          ),
          if (_services.length > 1)
            TextButton(
              onPressed: _isSubmitting ? null : _chooseService,
              child: const Text('Chọn dịch vụ'),
            ),
          const Text(
            'Thanh toán tại bệnh viện theo hướng dẫn. Chưa phát sinh giao dịch trên ứng dụng.',
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
            onPressed: _isSubmitting || _priceLoading || _service == null
                ? null
                : _submitAppointment,
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
