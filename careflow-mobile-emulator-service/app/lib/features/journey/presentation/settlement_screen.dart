import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../config/theme.dart';
import '../application/journey_providers.dart';
import '../domain/journey_models.dart';
import '../domain/journey_transition.dart';
import 'widgets/journey_status_card.dart';

/// Visit-level self-pay settlement and pharmacy handoff.
///
/// The production adapter will read the same summary from Visit Settlement
/// Service. Demo mode keeps the actions local so the whole patient journey can
/// be exercised before that backend slice is available.
class SettlementScreen extends ConsumerStatefulWidget {
  const SettlementScreen({super.key, required this.appointmentId});

  final String appointmentId;

  @override
  ConsumerState<SettlementScreen> createState() => _SettlementScreenState();
}

class _SettlementScreenState extends ConsumerState<SettlementScreen> {
  bool _isSubmitting = false;

  Future<void> _advance(JourneyEvent event) async {
    if (_isSubmitting) return;
    setState(() => _isSubmitting = true);
    await ref.read(journeyControllerProvider.notifier).advance(event);
    if (mounted) setState(() => _isSubmitting = false);
  }

  Future<void> _pay(PaymentMethod method) async {
    if (_isSubmitting) return;
    setState(() => _isSubmitting = true);
    final ok = await ref
        .read(journeyControllerProvider.notifier)
        .acknowledgeSettlement(method);
    if (!mounted) return;
    setState(() => _isSubmitting = false);
    if (!ok) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text('Không thể ghi nhận quyết toán. Vui lòng thử lại.'),
        ),
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    final journey = ref.watch(
      journeyForAppointmentProvider(widget.appointmentId),
    );
    final demoMode = ref.watch(demoModeProvider);
    return Scaffold(
      appBar: AppBar(title: const Text('Quyết toán lượt khám')),
      body: journey.when(
        loading: () => const Center(child: Text('Đang tải quyết toán...')),
        error: (_, _) => const Center(
          child: Text('Không thể tải thông tin quyết toán.'),
        ),
        data: (value) => value == null
            ? const Center(child: Text('Chưa có quyết toán lượt khám.'))
            : _buildBody(value, demoMode),
      ),
    );
  }

  Widget _buildBody(PatientJourney journey, bool demoMode) {
    final settlement = journey.settlement;
    final prescription = journey.prescription;
    if (settlement == null) {
      return ListView(
        padding: const EdgeInsets.all(AppSpacing.base),
        children: [
          JourneyStatusCard(journey: journey),
          const SizedBox(height: AppSpacing.base),
          const _MessageCard(
            message: 'Quyết toán sẽ xuất hiện sau khi bác sĩ kết luận lượt khám.',
          ),
        ],
      );
    }
    return ListView(
      padding: const EdgeInsets.all(AppSpacing.base),
      children: [
        JourneyStatusCard(journey: journey),
        const SizedBox(height: AppSpacing.base),
        _SettlementSummaryCard(settlement: settlement),
        if (journey.laboratoryOrders.isNotEmpty) ...[
          const SizedBox(height: AppSpacing.base),
          _CostSection(
            title: 'Xét nghiệm',
            amount: journey.laboratoryOrders.fold(
              0,
              (sum, order) => sum + order.price,
            ),
            detail: '${journey.laboratoryOrders.length} chỉ định đã thực hiện',
          ),
        ],
        if (prescription != null && prescription.items.isNotEmpty) ...[
          const SizedBox(height: AppSpacing.base),
          _CostSection(
            title: 'Thuốc theo toa',
            amount: _medicationTotalFor(prescription),
            detail: '${prescription.items.length} loại thuốc',
          ),
        ],
        const SizedBox(height: AppSpacing.xl),
        _actionFor(journey, demoMode),
        if (!demoMode && journey.status != JourneyStatus.completed) ...[
          const SizedBox(height: AppSpacing.md),
          const Text(
            'Thanh toán thật sẽ được kết nối với Visit Settlement Service.',
            textAlign: TextAlign.center,
          ),
        ],
      ],
    );
  }

  Widget _actionFor(PatientJourney journey, bool demoMode) {
    if (!demoMode) return const SizedBox.shrink();
    return switch (journey.status) {
      JourneyStatus.settlementPending => _pendingSettlementActions(journey),
      JourneyStatus.paymentDue => _PaymentMethods(
        isSubmitting: _isSubmitting,
        onSelected: _pay,
      ),
      JourneyStatus.settled || JourneyStatus.refunded => _actionButton(
        icon: Icons.inventory_2_rounded,
        label: 'Mô phỏng thuốc đã phát',
        onPressed: () => _advance(JourneyEvent.medicationDispensed),
      ),
      JourneyStatus.medicationReady => _actionButton(
        icon: Icons.check_circle_rounded,
        label: 'Xác nhận đã nhận thuốc',
        onPressed: () => _advance(JourneyEvent.visitCompleted),
      ),
      JourneyStatus.completed => const _MessageCard(
        message: 'Lượt khám đã hoàn tất. Bạn có thể xem lại toa thuốc trong hồ sơ.',
      ),
      _ => const SizedBox.shrink(),
    };
  }

  Widget _pendingSettlementActions(PatientJourney journey) {
    final settlement = journey.settlement!;
    if (settlement.amountDue > 0) {
      return _actionButton(
        icon: Icons.receipt_long_rounded,
        label: 'Xác nhận số tiền còn phải trả',
        onPressed: () => _advance(JourneyEvent.settlementPaymentRequested),
      );
    }
    if (settlement.refundDue > 0) {
      return _actionButton(
        icon: Icons.currency_exchange_rounded,
        label: 'Mô phỏng chờ hoàn khoản dư',
        onPressed: () => _advance(JourneyEvent.settlementRefundRequested),
      );
    }
    return _actionButton(
      icon: Icons.verified_rounded,
      label: 'Xác nhận quyết toán',
      onPressed: () => _advance(JourneyEvent.settlementAcknowledged),
    );
  }

  Widget _actionButton({
    required IconData icon,
    required String label,
    required VoidCallback onPressed,
  }) => SizedBox(
    width: double.infinity,
    child: ElevatedButton.icon(
      onPressed: _isSubmitting ? null : onPressed,
      icon: Icon(icon),
      label: Text(label),
    ),
  );
}

class _SettlementSummaryCard extends StatelessWidget {
  const _SettlementSummaryCard({required this.settlement});

  final VisitSettlement settlement;

  @override
  Widget build(BuildContext context) => Card(
    color: AppColors.primarySurface,
    child: Padding(
      padding: const EdgeInsets.all(AppSpacing.base),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            'Tổng chi phí lượt khám',
            style: Theme.of(context).textTheme.titleMedium,
          ),
          const SizedBox(height: AppSpacing.md),
          _AmountRow(label: 'Tổng chi phí', amount: settlement.totalVisitCost),
          _AmountRow(label: 'Đã trả trước', amount: -settlement.prepaidAmount),
          const Divider(),
          _AmountRow(
            label: settlement.amountDue > 0 ? 'Còn phải trả' : 'Khoản hoàn',
            amount: settlement.amountDue > 0
                ? settlement.amountDue
                : settlement.refundDue,
            emphasize: true,
          ),
        ],
      ),
    ),
  );
}

class _AmountRow extends StatelessWidget {
  const _AmountRow({
    required this.label,
    required this.amount,
    this.emphasize = false,
  });

  final String label;
  final int amount;
  final bool emphasize;

  @override
  Widget build(BuildContext context) => Padding(
    padding: const EdgeInsets.only(bottom: AppSpacing.sm),
    child: Row(
      children: [
        Expanded(child: Text(label)),
        Text(
          _currency(amount.abs()),
          style: emphasize
              ? Theme.of(context).textTheme.titleMedium?.copyWith(
                  color: AppColors.primaryDark,
                  fontWeight: FontWeight.w700,
                )
              : null,
        ),
      ],
    ),
  );
}

class _CostSection extends StatelessWidget {
  const _CostSection({required this.title, required this.amount, required this.detail});

  final String title;
  final int amount;
  final String detail;

  @override
  Widget build(BuildContext context) => Card(
    child: ListTile(
      leading: const Icon(Icons.receipt_long_rounded),
      title: Text(title),
      subtitle: Text(detail),
      trailing: Text(_currency(amount)),
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

class _MessageCard extends StatelessWidget {
  const _MessageCard({required this.message});

  final String message;

  @override
  Widget build(BuildContext context) => Card(
    color: AppColors.primarySurface,
    child: Padding(
      padding: const EdgeInsets.all(AppSpacing.base),
      child: Text(message),
    ),
  );
}

int _medicationTotalFor(Prescription prescription) {
  final pricedTotal = prescription.items.fold(
    0,
    (sum, item) => sum + item.lineAmount,
  );
  return pricedTotal == 0 ? 85000 : pricedTotal;
}

String _currency(int amount) {
  final digits = amount.toString();
  final buffer = StringBuffer();
  for (var index = 0; index < digits.length; index++) {
    if (index > 0 && (digits.length - index) % 3 == 0) buffer.write('.');
    buffer.write(digits[index]);
  }
  return '${buffer.toString()} ₫';
}
