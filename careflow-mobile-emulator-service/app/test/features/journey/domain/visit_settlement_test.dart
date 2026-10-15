import 'package:flutter_test/flutter_test.dart';

import 'package:careflow_patient/features/journey/domain/journey_models.dart';

void main() {
  test('calculates final self-pay amount after appointment prepayment', () {
    final settlement = VisitSettlement.calculate(
      consultationFee: 150000,
      laboratoryTotal: 300000,
      medicationTotal: 85000,
      prepaidAmount: 150000,
      calculatedAt: DateTime.utc(2026, 8, 5, 10),
    );

    expect(settlement.totalVisitCost, 535000);
    expect(settlement.amountDue, 385000);
    expect(settlement.refundDue, 0);
    expect(settlement.status, VisitSettlementStatus.paymentDue);
  });

  test('round trips settlement status and amounts', () {
    final original = VisitSettlement(
      status: VisitSettlementStatus.refundPending,
      totalVisitCost: 100000,
      prepaidAmount: 150000,
      amountDue: 0,
      refundDue: 50000,
      calculatedAt: DateTime.utc(2026, 8, 5),
      method: PaymentMethod.online,
    );

    expect(VisitSettlement.fromJson(original.toJson()), original);
  });

  test('prescription items expose priced medication lines for settlement', () {
    const item = PrescriptionItem(
      medicationName: 'Thuốc mô phỏng',
      dosage: '1 viên',
      route: 'Uống',
      frequency: '1 lần/ngày',
      duration: '2 ngày',
      caution: '',
      unitPrice: 25000,
      quantity: 2,
    );

    expect(item.lineAmount, 50000);
    expect(PrescriptionItem.fromJson(item.toJson()), item);
  });
}
