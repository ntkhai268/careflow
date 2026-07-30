import 'package:careflow_patient/features/journey/application/journey_controller.dart';
import 'package:careflow_patient/features/journey/application/journey_providers.dart';
import 'package:careflow_patient/features/journey/data/journey_repository.dart';
import 'package:careflow_patient/features/journey/domain/journey_models.dart';
import 'package:careflow_patient/features/journey/presentation/laboratory_screen.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  testWidgets('shows complete laboratory order details before payment', (
    tester,
  ) async {
    await tester.pumpWidget(
      laboratoryApp(labJourney(JourneyStatus.labOrdered)),
    );

    expect(find.text('Khoa Xét nghiệm'), findsOneWidget);
    expect(find.text('Phòng xét nghiệm tầng 1'), findsOneWidget);
    expect(find.text('Nhịn ăn 8 giờ'), findsOneWidget);
    expect(find.text('120.000 ₫'), findsOneWidget);
  });

  testWidgets('offers all payment methods and hospital cash copy', (
    tester,
  ) async {
    await tester.pumpWidget(
      laboratoryApp(labJourney(JourneyStatus.paymentPending)),
    );

    expect(find.text('Thanh toán trực tuyến'), findsOneWidget);
    expect(find.text('Tiền mặt'), findsOneWidget);
    expect(find.text('Bảo hiểm y tế'), findsOneWidget);
    expect(find.text('Thanh toán tại bệnh viện'), findsOneWidget);
  });

  testWidgets('acknowledges the selected payment method', (tester) async {
    final controller = RecordingJourneyController();
    await tester.pumpWidget(
      laboratoryApp(
        labJourney(JourneyStatus.paymentPending),
        controller: controller,
      ),
    );

    await tester.ensureVisible(find.text('Tiền mặt'));
    await tester.tap(find.text('Tiền mặt'));
    await tester.pumpAndSettle();

    expect(controller.paymentMethods, [PaymentMethod.cash]);
  });

  testWidgets('shows simulated online receipt only in demo mode', (
    tester,
  ) async {
    final controller = RecordingJourneyController();
    await tester.pumpWidget(
      laboratoryApp(
        labJourney(JourneyStatus.paymentPending),
        controller: controller,
      ),
    );

    await tester.ensureVisible(find.text('Thanh toán trực tuyến'));
    await tester.tap(find.text('Thanh toán trực tuyến'));
    await tester.pumpAndSettle();

    expect(controller.paymentMethods, [PaymentMethod.online]);
    expect(
      find.text('Thanh toán trực tuyến mô phỏng thành công'),
      findsOneWidget,
    );
  });

  testWidgets(
    'shows backend-unavailable copy for online payment in production',
    (tester) async {
      await tester.pumpWidget(
        laboratoryApp(
          labJourney(JourneyStatus.paymentPending),
          demoMode: false,
        ),
      );

      await tester.ensureVisible(find.text('Thanh toán trực tuyến'));
      await tester.tap(find.text('Thanh toán trực tuyến'));
      await tester.pumpAndSettle();

      expect(
        find.text('Tính năng đang chờ backend triển khai'),
        findsOneWidget,
      );
      expect(
        find.text('Thanh toán trực tuyến mô phỏng thành công'),
        findsNothing,
      );
    },
  );

  for (final status in [
    JourneyStatus.waitingLab,
    JourneyStatus.labInProgress,
  ]) {
    testWidgets('does not request a second check-in while ${status.name}', (
      tester,
    ) async {
      await tester.pumpWidget(laboratoryApp(labJourney(status)));

      expect(find.text('Check-in xét nghiệm'), findsNothing);
      expect(find.text('Quét mã QR'), findsNothing);
    });
  }

  testWidgets('shows published laboratory results and their simulated source', (
    tester,
  ) async {
    await tester.pumpWidget(
      laboratoryApp(labJourney(JourneyStatus.labResultReady, withResult: true)),
    );

    expect(find.text('5.2 G/L'), findsOneWidget);
    expect(find.text('Dữ liệu mô phỏng'), findsOneWidget);
  });
}

Widget laboratoryApp(
  PatientJourney journey, {
  bool demoMode = true,
  JourneyController? controller,
}) => ProviderScope(
  overrides: [
    demoModeProvider.overrideWithValue(demoMode),
    journeyForAppointmentProvider(
      'apt-1',
    ).overrideWithValue(AsyncData(journey)),
    if (controller != null)
      journeyControllerProvider.overrideWith((ref) => controller),
  ],
  child: const MaterialApp(home: LaboratoryScreen(appointmentId: 'apt-1')),
);

class RecordingJourneyController extends JourneyController {
  RecordingJourneyController()
    : super(repository: const UnavailableJourneyRepository(), demoMode: true);

  final List<PaymentMethod> paymentMethods = [];

  @override
  Future<void> acknowledgePayment(PaymentMethod method) async {
    paymentMethods.add(method);
  }
}

PatientJourney labJourney(JourneyStatus status, {bool withResult = false}) =>
    PatientJourney(
      appointmentId: 'apt-1',
      patientId: 'patient-1',
      status: status,
      ticket: const VisitTicket(
        code: 'CF-APT-1',
        qrPayload: 'careflow://visit/apt-1',
        queueNumber: '42',
        hospitalName: 'Bệnh viện CareFlow',
        specialtyName: 'Nội tổng quát',
        room: 'Phòng 21',
        expectedWindow: '10:30 - 11:30',
      ),
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
