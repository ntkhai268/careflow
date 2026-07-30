import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../config/theme.dart';
import '../application/journey_providers.dart';
import '../domain/journey_models.dart';
import 'widgets/journey_status_card.dart';

class LaboratoryScreen extends ConsumerStatefulWidget {
  const LaboratoryScreen({super.key, required this.appointmentId});

  final String appointmentId;

  @override
  ConsumerState<LaboratoryScreen> createState() => _LaboratoryScreenState();
}

class _LaboratoryScreenState extends ConsumerState<LaboratoryScreen> {
  String? _paymentMessage;

  Future<void> _acknowledgePayment(PaymentMethod method, bool demoMode) async {
    final message = switch ((method, demoMode)) {
      (PaymentMethod.online, true) =>
        'Thanh toán trực tuyến mô phỏng thành công',
      (PaymentMethod.cash, true) =>
        'Đã ghi nhận lựa chọn tiền mặt. Thanh toán tại bệnh viện.',
      (PaymentMethod.insurance, true) =>
        'Đã ghi nhận thông tin bảo hiểm để bệnh viện xác nhận.',
      _ => 'Tính năng đang chờ backend triển khai',
    };
    setState(() {
      _paymentMessage = message;
    });
    ScaffoldMessenger.of(
      context,
    ).showSnackBar(SnackBar(content: Text(message)));
    await ref
        .read(journeyControllerProvider.notifier)
        .acknowledgePayment(method);
  }

  @override
  Widget build(BuildContext context) {
    final journey = ref.watch(
      journeyForAppointmentProvider(widget.appointmentId),
    );
    final demoMode = ref.watch(demoModeProvider);
    return Scaffold(
      appBar: AppBar(title: const Text('Xét nghiệm và thanh toán')),
      body: journey.when(
        loading: () =>
            const Center(child: Text('Đang tải chỉ định xét nghiệm...')),
        error: (_, _) =>
            const Center(child: Text('Không thể tải thông tin xét nghiệm.')),
        data: (value) => value == null
            ? const Center(child: Text('Chưa có chỉ định xét nghiệm.'))
            : ListView(
                padding: const EdgeInsets.all(AppSpacing.base),
                children: [
                  JourneyStatusCard(journey: value),
                  if (_paymentMessage != null) ...[
                    const SizedBox(height: AppSpacing.base),
                    _PaymentMessage(message: _paymentMessage!),
                  ],
                  const SizedBox(height: AppSpacing.base),
                  _LaboratoryInstruction(journey: value),
                  const SizedBox(height: AppSpacing.base),
                  if (value.status == JourneyStatus.paymentPending) ...[
                    _PaymentMethods(
                      onSelected: (method) =>
                          _acknowledgePayment(method, demoMode),
                    ),
                    const SizedBox(height: AppSpacing.base),
                  ],
                  for (final order in value.laboratoryOrders) ...[
                    _LaboratoryOrderCard(order: order),
                    const SizedBox(height: AppSpacing.md),
                  ],
                ],
              ),
      ),
    );
  }
}

class _LaboratoryInstruction extends StatelessWidget {
  const _LaboratoryInstruction({required this.journey});

  final PatientJourney journey;

  @override
  Widget build(BuildContext context) {
    final message = switch (journey.status) {
      JourneyStatus.labOrdered => 'Bác sĩ đã chỉ định xét nghiệm cho bạn.',
      JourneyStatus.paymentPending => 'Vui lòng chọn phương thức thanh toán.',
      JourneyStatus.waitingLab =>
        'Chỉ định đã được tiếp nhận. Vui lòng đến đúng nơi thực hiện.',
      JourneyStatus.labInProgress => 'Xét nghiệm đang được thực hiện.',
      JourneyStatus.labResultReady => 'Kết quả xét nghiệm đã sẵn sàng.',
      _ => 'Theo dõi thông tin chỉ định xét nghiệm tại đây.',
    };
    return Card(
      color: AppColors.primarySurface,
      child: Padding(
        padding: const EdgeInsets.all(AppSpacing.base),
        child: Text(message, style: Theme.of(context).textTheme.titleMedium),
      ),
    );
  }
}

class _LaboratoryOrderCard extends StatelessWidget {
  const _LaboratoryOrderCard({required this.order});

  final LaboratoryOrder order;

  @override
  Widget build(BuildContext context) => Card(
    child: Padding(
      padding: const EdgeInsets.all(AppSpacing.base),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(order.name, style: Theme.of(context).textTheme.titleMedium),
          const SizedBox(height: AppSpacing.md),
          _OrderDetail(label: 'Khoa thực hiện', value: order.department),
          _OrderDetail(label: 'Nơi thực hiện', value: order.destination),
          _OrderDetail(label: 'Chuẩn bị', value: order.preparationNote),
          _OrderDetail(label: 'Chi phí', value: _currency(order.price)),
          if (order.result != null) ...[
            const Divider(height: AppSpacing.xl),
            Text('Kết quả', style: Theme.of(context).textTheme.titleMedium),
            const SizedBox(height: AppSpacing.sm),
            _OrderDetail(
              label: 'Giá trị',
              value: '${order.result!.value} ${order.result!.unit}'.trim(),
            ),
            _OrderDetail(
              label: 'Khoảng tham chiếu',
              value: order.result!.referenceRange,
            ),
            _OrderDetail(label: 'Nguồn', value: order.result!.source),
          ],
        ],
      ),
    ),
  );
}

class _OrderDetail extends StatelessWidget {
  const _OrderDetail({required this.label, required this.value});

  final String label;
  final String value;

  @override
  Widget build(BuildContext context) => Padding(
    padding: const EdgeInsets.only(bottom: AppSpacing.sm),
    child: Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(label, style: Theme.of(context).textTheme.bodySmall),
        Text(value),
      ],
    ),
  );
}

class _PaymentMethods extends StatelessWidget {
  const _PaymentMethods({required this.onSelected});

  final ValueChanged<PaymentMethod> onSelected;

  @override
  Widget build(BuildContext context) => Card(
    child: Padding(
      padding: const EdgeInsets.all(AppSpacing.base),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            'Phương thức thanh toán',
            style: Theme.of(context).textTheme.titleMedium,
          ),
          const SizedBox(height: AppSpacing.md),
          SizedBox(
            width: double.infinity,
            child: ElevatedButton(
              onPressed: () => onSelected(PaymentMethod.online),
              child: const Text('Thanh toán trực tuyến'),
            ),
          ),
          const SizedBox(height: AppSpacing.sm),
          SizedBox(
            width: double.infinity,
            child: OutlinedButton(
              onPressed: () => onSelected(PaymentMethod.cash),
              child: const Text('Tiền mặt'),
            ),
          ),
          const Padding(
            padding: EdgeInsets.only(top: AppSpacing.xs),
            child: Text('Thanh toán tại bệnh viện'),
          ),
          const SizedBox(height: AppSpacing.sm),
          SizedBox(
            width: double.infinity,
            child: OutlinedButton(
              onPressed: () => onSelected(PaymentMethod.insurance),
              child: const Text('Bảo hiểm y tế'),
            ),
          ),
          const Padding(
            padding: EdgeInsets.only(top: AppSpacing.xs),
            child: Text('Bệnh viện sẽ xác nhận quyền lợi bảo hiểm.'),
          ),
        ],
      ),
    ),
  );
}

class _PaymentMessage extends StatelessWidget {
  const _PaymentMessage({required this.message});

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
