import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../config/theme.dart';
import '../application/journey_providers.dart';
import '../domain/journey_models.dart';
import '../domain/journey_transition.dart';
import 'widgets/journey_status_card.dart';

class PharmacyScreen extends ConsumerStatefulWidget {
  const PharmacyScreen({super.key, required this.appointmentId});

  final String appointmentId;

  @override
  ConsumerState<PharmacyScreen> createState() => _PharmacyScreenState();
}

class _PharmacyScreenState extends ConsumerState<PharmacyScreen> {
  bool _isSubmitting = false;
  String? _message;

  Future<void> _requestPayment() async {
    if (_isSubmitting) return;
    setState(() {
      _isSubmitting = true;
      _message = null;
    });
    await ref
        .read(journeyControllerProvider.notifier)
        .advance(JourneyEvent.prescriptionPaymentRequested);
    if (!mounted) return;
    setState(() => _isSubmitting = false);
  }

  Future<void> _pay(PaymentMethod method) async {
    if (_isSubmitting) return;
    setState(() {
      _isSubmitting = true;
      _message = null;
    });
    final ok = await ref
        .read(journeyControllerProvider.notifier)
        .acknowledgePrescriptionPayment(method);
    if (!mounted) return;
    setState(() {
      _isSubmitting = false;
      _message = ok
          ? method == PaymentMethod.online
                ? 'Thanh toán tiền thuốc trực tuyến mô phỏng thành công.'
                : 'Đã ghi nhận thanh toán tiền mặt tại bệnh viện.'
          : null;
    });
    if (!ok) _showError('Không thể ghi nhận thanh toán tiền thuốc.');
  }

  Future<void> _advance(JourneyEvent event) async {
    if (_isSubmitting) return;
    setState(() => _isSubmitting = true);
    await ref.read(journeyControllerProvider.notifier).advance(event);
    if (!mounted) return;
    setState(() => _isSubmitting = false);
  }

  void _showError(String message) {
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(content: Text(message), backgroundColor: AppColors.error),
    );
  }

  @override
  Widget build(BuildContext context) {
    final journey = ref.watch(
      journeyForAppointmentProvider(widget.appointmentId),
    );
    final demoMode = ref.watch(demoModeProvider);
    return Scaffold(
      appBar: AppBar(title: const Text('Nhà thuốc và thanh toán')),
      body: journey.when(
        loading: () => const Center(child: Text('Đang tải đơn thuốc...')),
        error: (_, _) => const Center(
          child: Text('Không thể tải thông tin đơn thuốc.'),
        ),
        data: (value) => value == null
            ? const Center(child: Text('Chưa có đơn thuốc.'))
            : _buildBody(value, demoMode),
      ),
    );
  }

  Widget _buildBody(PatientJourney journey, bool demoMode) {
    final prescription = journey.prescription;
    if (prescription == null || prescription.items.isEmpty) {
      return const Center(child: Text('Chưa có thuốc được kê trong đơn.'));
    }
    return ListView(
      padding: const EdgeInsets.all(AppSpacing.base),
      children: [
        JourneyStatusCard(journey: journey),
        if (_message != null) ...[
          const SizedBox(height: AppSpacing.base),
          _MessageCard(message: _message!),
        ],
        const SizedBox(height: AppSpacing.base),
        _MedicationTotalCard(
          items: prescription.items,
          amount: _medicationTotal(journey),
        ),
        const SizedBox(height: AppSpacing.base),
        Text('Đơn thuốc', style: Theme.of(context).textTheme.titleLarge),
        const SizedBox(height: AppSpacing.sm),
        for (final item in prescription.items) ...[
          _MedicationCard(item: item),
          const SizedBox(height: AppSpacing.md),
        ],
        if (journey.followUp case final followUp?) ...[
          _FollowUpCard(followUp: followUp),
          const SizedBox(height: AppSpacing.md),
        ],
        const SizedBox(height: AppSpacing.sm),
        _actionFor(journey, demoMode),
        if (!demoMode && journey.status != JourneyStatus.completed) ...[
          const SizedBox(height: AppSpacing.md),
          const Text(
            'Thanh toán và phát thuốc thật sẽ được kết nối khi backend Nhà thuốc triển khai.',
            textAlign: TextAlign.center,
          ),
        ],
      ],
    );
  }

  Widget _actionFor(PatientJourney journey, bool demoMode) {
    if (!demoMode) return const SizedBox.shrink();
    return switch (journey.status) {
      JourneyStatus.prescribed => SizedBox(
        width: double.infinity,
        child: ElevatedButton.icon(
          onPressed: _isSubmitting ? null : _requestPayment,
          icon: const Icon(Icons.payment_rounded),
          label: const Text('Thanh toán tiền thuốc'),
        ),
      ),
      JourneyStatus.prescriptionPaymentPending => _PaymentMethods(
        isSubmitting: _isSubmitting,
        onSelected: _pay,
      ),
      JourneyStatus.prescriptionPaid => SizedBox(
        width: double.infinity,
        child: ElevatedButton.icon(
          onPressed: _isSubmitting
              ? null
              : () => _advance(JourneyEvent.medicationDispensed),
          icon: const Icon(Icons.inventory_2_rounded),
          label: const Text('Mô phỏng thuốc đã sẵn sàng'),
        ),
      ),
      JourneyStatus.medicationReady => SizedBox(
        width: double.infinity,
        child: ElevatedButton.icon(
          onPressed: _isSubmitting
              ? null
              : () => _advance(JourneyEvent.visitCompleted),
          icon: const Icon(Icons.check_circle_rounded),
          label: const Text('Xác nhận đã nhận thuốc'),
        ),
      ),
      JourneyStatus.completed => const _MessageCard(
        message: 'Lượt khám đã hoàn tất. Bạn có thể xem lại đơn thuốc trong hồ sơ.',
      ),
      _ => const SizedBox.shrink(),
    };
  }

  int _medicationTotal(PatientJourney journey) =>
      journey.prescriptionPayment?.amount ?? 85000;
}

class _MedicationTotalCard extends StatelessWidget {
  const _MedicationTotalCard({required this.items, required this.amount});

  final List<PrescriptionItem> items;
  final int amount;

  @override
  Widget build(BuildContext context) => Card(
    color: AppColors.primarySurface,
    child: Padding(
      padding: const EdgeInsets.all(AppSpacing.base),
      child: Row(
        children: [
          const Icon(Icons.receipt_long_rounded, color: AppColors.primary),
          const SizedBox(width: AppSpacing.md),
          Expanded(
            child: Text(
              '${items.length} loại thuốc',
              style: Theme.of(context).textTheme.titleMedium,
            ),
          ),
          Text(
            _currency(amount),
            style: Theme.of(context).textTheme.titleMedium?.copyWith(
              fontWeight: FontWeight.w700,
              color: AppColors.primaryDark,
            ),
          ),
        ],
      ),
    ),
  );

  static String _currency(int amount) {
    final digits = amount.toString();
    final buffer = StringBuffer();
    for (var index = 0; index < digits.length; index++) {
      if (index > 0 && (digits.length - index) % 3 == 0) buffer.write('.');
      buffer.write(digits[index]);
    }
    return '${buffer.toString()} ₫';
  }
}

class _MedicationCard extends StatelessWidget {
  const _MedicationCard({required this.item});

  final PrescriptionItem item;

  @override
  Widget build(BuildContext context) => Card(
    child: Padding(
      padding: const EdgeInsets.all(AppSpacing.base),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(item.medicationName, style: Theme.of(context).textTheme.titleMedium),
          const SizedBox(height: AppSpacing.sm),
          Text('${item.dosage} • ${item.route} • ${item.frequency} • ${item.duration}'),
          const SizedBox(height: AppSpacing.sm),
          Text('Lưu ý: ${item.caution}'),
        ],
      ),
    ),
  );
}

class _PaymentMethods extends StatelessWidget {
  const _PaymentMethods({required this.isSubmitting, required this.onSelected});

  final bool isSubmitting;
  final ValueChanged<PaymentMethod> onSelected;

  @override
  Widget build(BuildContext context) => Card(
    child: Padding(
      padding: const EdgeInsets.all(AppSpacing.base),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text('Phương thức thanh toán', style: Theme.of(context).textTheme.titleMedium),
          const SizedBox(height: AppSpacing.md),
          SizedBox(
            width: double.infinity,
            child: ElevatedButton(
              onPressed: isSubmitting ? null : () => onSelected(PaymentMethod.online),
              child: const Text('Thanh toán trực tuyến'),
            ),
          ),
          const SizedBox(height: AppSpacing.sm),
          SizedBox(
            width: double.infinity,
            child: OutlinedButton(
              onPressed: isSubmitting ? null : () => onSelected(PaymentMethod.cash),
              child: const Text('Tiền mặt tại bệnh viện'),
            ),
          ),
        ],
      ),
    ),
  );
}

class _FollowUpCard extends StatelessWidget {
  const _FollowUpCard({required this.followUp});

  final FollowUpAppointment followUp;

  @override
  Widget build(BuildContext context) => Card(
    child: Padding(
      padding: const EdgeInsets.all(AppSpacing.base),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text('Tái khám', style: Theme.of(context).textTheme.titleMedium),
          const SizedBox(height: AppSpacing.sm),
          Text('Theo lịch hẹn sau 7 ngày tại ${followUp.room}.'),
          const SizedBox(height: AppSpacing.xs),
          Text(followUp.note),
        ],
      ),
    ),
  );
}

class _MessageCard extends StatelessWidget {
  const _MessageCard({required this.message});

  final String message;

  @override
  Widget build(BuildContext context) => Card(
    color: AppColors.successLight,
    child: Padding(
      padding: const EdgeInsets.all(AppSpacing.base),
      child: Text(message, style: Theme.of(context).textTheme.titleMedium),
    ),
  );
}
