import 'package:careflow_patient/features/journey/application/journey_providers.dart';
import 'package:careflow_patient/features/journey/domain/journey_models.dart';
import 'package:careflow_patient/features/journey/presentation/settlement_screen.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  testWidgets('shows one final settlement instead of separate lab payment', (
    tester,
  ) async {
    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          demoModeProvider.overrideWithValue(true),
          journeyForAppointmentProvider('apt-1').overrideWithValue(
            AsyncData(settlementJourney()),
          ),
        ],
        child: const MaterialApp(
          home: SettlementScreen(appointmentId: 'apt-1'),
        ),
      ),
    );

    expect(find.text('Quyết toán lượt khám'), findsOneWidget);
    expect(find.text('Tổng chi phí lượt khám'), findsOneWidget);
    expect(find.text('Đã trả trước'), findsOneWidget);
    expect(find.text('Còn phải trả'), findsOneWidget);
    expect(find.text('Thanh toán trực tuyến'), findsOneWidget);
    expect(find.text('Thanh toán xét nghiệm'), findsNothing);
    expect(find.text('Thanh toán tiền thuốc'), findsNothing);
  });
}

PatientJourney settlementJourney() => PatientJourney(
  appointmentId: 'apt-1',
  patientId: 'patient-1',
  status: JourneyStatus.paymentDue,
  laboratoryOrders: const [],
  settlement: VisitSettlement(
    status: VisitSettlementStatus.paymentDue,
    totalVisitCost: 235000,
    prepaidAmount: 150000,
    amountDue: 85000,
    refundDue: 0,
    calculatedAt: DateTime.utc(2026, 8, 5),
  ),
  prescription: Prescription(
    id: 'rx-1',
    issuedAt: DateTime.utc(2026, 8, 5),
    items: const [],
  ),
  timeline: const [],
  notifications: const [],
  updatedAt: DateTime.utc(2026, 8, 5),
);
