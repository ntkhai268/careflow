import 'package:careflow_patient/features/journey/application/journey_providers.dart';
import 'package:careflow_patient/features/journey/domain/journey_models.dart';
import 'package:careflow_patient/features/journey/presentation/laboratory_screen.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  testWidgets('shows laboratory orders and queues without a payment gate', (
    tester,
  ) async {
    await tester.pumpWidget(laboratoryApp(labJourney(JourneyStatus.waitingLab)));

    expect(find.text('Xét nghiệm công thức máu'), findsOneWidget);
    expect(find.text('Phòng xét nghiệm tầng 1'), findsOneWidget);
    expect(find.text('Chỉ định đã được tiếp nhận và đưa vào hàng đợi xét nghiệm. Vui lòng đến đúng nơi thực hiện.'), findsOneWidget);
    expect(find.text('Thanh toán xét nghiệm'), findsNothing);
    expect(find.text('Thanh toán trực tuyến'), findsNothing);
    expect(find.text('Tiền mặt'), findsNothing);
  });

  testWidgets('shows published laboratory results in the same order card', (
    tester,
  ) async {
    await tester.pumpWidget(
      laboratoryApp(labJourney(JourneyStatus.labResultReady, withResult: true)),
    );

    expect(find.text('5.2 G/L'), findsOneWidget);
    expect(find.text('Dữ liệu mô phỏng'), findsOneWidget);
  });
}

Widget laboratoryApp(PatientJourney journey) => ProviderScope(
  overrides: [
    demoModeProvider.overrideWithValue(true),
    journeyForAppointmentProvider('apt-1').overrideWithValue(AsyncData(journey)),
  ],
  child: const MaterialApp(home: LaboratoryScreen(appointmentId: 'apt-1')),
);

PatientJourney labJourney(JourneyStatus status, {bool withResult = false}) =>
    PatientJourney(
      appointmentId: 'apt-1',
      patientId: 'patient-1',
      status: status,
      laboratoryOrders: [
        LaboratoryOrder(
          id: 'cbc',
          name: 'Xét nghiệm công thức máu',
          department: 'Khoa Xét nghiệm',
          destination: 'Phòng xét nghiệm tầng 1',
          preparationNote: 'Nhịn ăn 8 giờ',
          price: 120000,
          result: withResult
              ? LaboratoryResult(
                  id: 'cbc-result',
                  value: '5.2',
                  unit: 'G/L',
                  referenceRange: '4.0 - 10.0',
                  source: 'Dữ liệu mô phỏng',
                  reportedAt: DateTime.utc(2026, 7, 30),
                )
              : null,
        ),
      ],
      timeline: const [],
      notifications: const [],
      updatedAt: DateTime.utc(2026, 7, 30),
    );
