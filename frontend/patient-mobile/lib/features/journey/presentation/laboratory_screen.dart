import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../config/theme.dart';
import '../application/journey_providers.dart';
import '../domain/journey_models.dart';
import 'widgets/journey_status_card.dart';

/// Laboratory orders are queued immediately after the doctor submits them.
/// Payment is intentionally handled later by the visit settlement screen.
class LaboratoryScreen extends ConsumerWidget {
  const LaboratoryScreen({super.key, required this.appointmentId});

  final String appointmentId;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final journey = ref.watch(journeyForAppointmentProvider(appointmentId));
    return Scaffold(
      appBar: AppBar(title: const Text('Xét nghiệm')),
      body: journey.when(
        loading: () => const Center(child: Text('Đang tải chỉ định xét nghiệm...')),
        error: (_, _) => const Center(
          child: Text('Không thể tải thông tin xét nghiệm.'),
        ),
        data: (value) => value == null
            ? const Center(child: Text('Chưa có chỉ định xét nghiệm.'))
            : _buildBody(context, value),
      ),
    );
  }

  Widget _buildBody(BuildContext context, PatientJourney journey) {
    if (journey.laboratoryOrders.isEmpty) {
      return const Center(child: Text('Chưa có chỉ định xét nghiệm.'));
    }
    final total = journey.laboratoryOrders.fold(
      0,
      (sum, order) => sum + order.price,
    );
    return ListView(
      padding: const EdgeInsets.all(AppSpacing.base),
      children: [
        JourneyStatusCard(journey: journey),
        const SizedBox(height: AppSpacing.base),
        Card(
          color: AppColors.primarySurface,
          child: Padding(
            padding: const EdgeInsets.all(AppSpacing.base),
            child: Text(_messageFor(journey.status)),
          ),
        ),
        const SizedBox(height: AppSpacing.base),
        for (final order in journey.laboratoryOrders) ...[
          _LaboratoryOrderCard(order: order),
          const SizedBox(height: AppSpacing.md),
        ],
        Card(
          child: ListTile(
            leading: const Icon(Icons.receipt_long_rounded),
            title: const Text('Tổng chỉ định'),
            subtitle: const Text('Chi phí sẽ được cộng vào quyết toán cuối lượt khám.'),
            trailing: Text(_currency(total)),
          ),
        ),
      ],
    );
  }

  String _messageFor(JourneyStatus status) => switch (status) {
    JourneyStatus.labOrdered || JourneyStatus.waitingLab =>
      'Chỉ định đã được tiếp nhận và đưa vào hàng đợi xét nghiệm. Vui lòng đến đúng nơi thực hiện.',
    JourneyStatus.labInProgress => 'Xét nghiệm đang được thực hiện.',
    JourneyStatus.labResultReady => 'Kết quả xét nghiệm đã sẵn sàng.',
    _ => 'Theo dõi thông tin chỉ định xét nghiệm tại đây.',
  };
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

String _currency(int amount) {
  final digits = amount.toString();
  final buffer = StringBuffer();
  for (var index = 0; index < digits.length; index++) {
    if (index > 0 && (digits.length - index) % 3 == 0) buffer.write('.');
    buffer.write(digits[index]);
  }
  return '${buffer.toString()} ₫';
}
