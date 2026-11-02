import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:intl/intl.dart';
import '../../config/theme.dart';
import '../../features/journey/application/journey_providers.dart';
import '../../models/appointment.dart';
import '../../models/appointment_payment.dart';
import '../../services/appointment_service.dart';
import '../../services/notification_refresh.dart';
import '../../services/appointment_payment_store.dart';
import '../../utils/api_error_message.dart';

part 'widgets/appointment_detail_cards.dart';

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
  int _loadGeneration = 0;

  @override
  void initState() {
    super.initState();
    _loadAppointment();
  }

  Future<void> _loadAppointment() async {
    final generation = ++_loadGeneration;
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
      if (!mounted || generation != _loadGeneration) return;
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
      if (ref.read(demoModeProvider)) {
        try {
          paymentReceipt = await ref
              .read(appointmentPaymentStoreProvider)
              .load(appointment.id);
        } catch (_) {
          // Payment receipt is local demo presentation data and must not hide
          // a real appointment when local storage is unavailable.
        }
      }
      if (!mounted ||
          generation != _loadGeneration ||
          scope != ref.read(journeyAccountScopeProvider)) {
        return;
      }
      setState(() {
        _appointment = appointment;
        _paymentReceipt = paymentReceipt;
        _isLoading = false;
      });
    } catch (e) {
      if (!mounted || generation != _loadGeneration) return;
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
    ref.listen(
      notificationRefreshRevisionProvider,
      (_, _) => _loadAppointment(),
    );
    return Scaffold(
      backgroundColor: AppColors.background,
      appBar: AppBar(
        title: const Text('Chi tiết phiếu khám'),
        leading: IconButton(
          icon: const Icon(Icons.arrow_back_rounded),
          onPressed: () {
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
    final canCancel = appt.status == 'PENDING' || appt.status == 'CONFIRMED';

    return Column(
      children: [
        Expanded(
          child: SingleChildScrollView(
            padding: const EdgeInsets.all(AppSpacing.base),
            child: Column(
              children: [
                _AppointmentStatusHeader(appointment: appt),
                const SizedBox(height: AppSpacing.base),
                _AppointmentInfoCard(appointment: appt, dateStr: dateStr),
                const SizedBox(height: AppSpacing.base),
                if (_paymentReceipt != null) ...[
                  _AppointmentPaymentCard(receipt: _paymentReceipt!),
                  const SizedBox(height: AppSpacing.base),
                ],
                if (appt.quotedAmount != null) ...[
                  Card(
                    child: Padding(
                      padding: const EdgeInsets.all(16),
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            appt.serviceName ?? 'Dịch vụ khám',
                            style: const TextStyle(fontWeight: FontWeight.bold),
                          ),
                          Text(
                            'Báo giá khi đặt lịch: ${_formatAppointmentCurrency(appt.quotedAmount!)}',
                          ),
                          const Text(
                            'Thanh toán theo hướng dẫn của bệnh viện. Báo giá không phải xác nhận đã thanh toán.',
                          ),
                        ],
                      ),
                    ),
                  ),
                  const SizedBox(height: AppSpacing.base),
                ],
                if (_canOpenVisitTicket(appt)) ...[
                  _AppointmentVisitTicketActionCard(appointment: appt),
                  const SizedBox(height: AppSpacing.base),
                ],
                _AppointmentTimelineCard(appointment: appt),
              ],
            ),
          ),
        ),
        if (canCancel)
          _AppointmentCancelActionBar(
            isCancelling: _isCancelling,
            onCancel: _cancelAppointment,
          ),
      ],
    );
  }
}
