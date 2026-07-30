import 'package:careflow_patient/features/journey/application/journey_controller.dart';
import 'package:careflow_patient/features/journey/application/journey_providers.dart';
import 'dart:async';

import 'package:careflow_patient/config/theme.dart';
import 'package:careflow_patient/features/journey/data/journey_repository.dart';
import 'package:careflow_patient/features/journey/domain/journey_transition.dart';
import 'package:careflow_patient/features/journey/domain/journey_models.dart';
import 'package:careflow_patient/features/journey/presentation/laboratory_screen.dart';
import 'package:careflow_patient/models/appointment.dart';
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
    final repository = PaymentJourneyRepository();
    final controller = paymentController(repository);
    await tester.pumpWidget(
      laboratoryApp(
        labJourney(JourneyStatus.paymentPending),
        controller: controller,
      ),
    );

    await tester.ensureVisible(find.text('Tiền mặt'));
    await tester.tap(find.text('Tiền mặt'));
    await tester.pumpAndSettle();

    expect(repository.paymentMethods, [PaymentMethod.cash]);
    expect(
      find.text('Đã ghi nhận lựa chọn tiền mặt. Thanh toán tại bệnh viện.'),
      findsOneWidget,
    );
  });

  testWidgets('shows insurance acknowledgement only after payment succeeds', (
    tester,
  ) async {
    final repository = PaymentJourneyRepository();
    await tester.pumpWidget(
      laboratoryApp(
        labJourney(JourneyStatus.paymentPending),
        controller: paymentController(repository),
      ),
    );

    await tester.ensureVisible(find.text('Bảo hiểm y tế'));
    await tester.tap(find.text('Bảo hiểm y tế'));
    await tester.pumpAndSettle();

    expect(repository.paymentMethods, [PaymentMethod.insurance]);
    expect(
      find.text('Đã ghi nhận thông tin bảo hiểm để bệnh viện xác nhận.'),
      findsOneWidget,
    );
  });

  testWidgets('shows simulated online receipt only in demo mode', (
    tester,
  ) async {
    final repository = PaymentJourneyRepository();
    final controller = paymentController(repository);
    await tester.pumpWidget(
      laboratoryApp(
        labJourney(JourneyStatus.paymentPending),
        controller: controller,
      ),
    );

    await tester.ensureVisible(find.text('Thanh toán trực tuyến'));
    await tester.tap(find.text('Thanh toán trực tuyến'));
    await tester.pumpAndSettle();

    expect(repository.paymentMethods, [PaymentMethod.online]);
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

  testWidgets(
    'does not claim payment acknowledgement after persistence fails',
    (tester) async {
      final repository = PaymentJourneyRepository(shouldFail: true);
      await tester.pumpWidget(
        laboratoryApp(
          labJourney(JourneyStatus.paymentPending),
          controller: paymentController(repository),
        ),
      );

      await tester.ensureVisible(find.text('Thanh toán trực tuyến'));
      await tester.tap(find.text('Thanh toán trực tuyến'));
      await tester.pumpAndSettle();

      expect(
        find.text('Không thể ghi nhận thanh toán. Vui lòng thử lại.'),
        findsOneWidget,
      );
      expect(
        tester.widget<SnackBar>(find.byType(SnackBar)).backgroundColor,
        AppColors.error,
      );
      expect(
        find.text('Thanh toán trực tuyến mô phỏng thành công'),
        findsNothing,
      );
    },
  );

  testWidgets('locks every payment method while acknowledgement is pending', (
    tester,
  ) async {
    final repository = PaymentJourneyRepository(
      pending: Completer<PatientJourney>(),
    );
    await tester.pumpWidget(
      laboratoryApp(
        labJourney(JourneyStatus.paymentPending),
        controller: paymentController(repository),
      ),
    );

    await tester.tap(find.text('Thanh toán trực tuyến'));
    await tester.pump();

    expect(
      tester
          .widget<ElevatedButton>(
            find.widgetWithText(ElevatedButton, 'Thanh toán trực tuyến'),
          )
          .onPressed,
      isNull,
    );
    expect(
      tester
          .widget<OutlinedButton>(
            find.widgetWithText(OutlinedButton, 'Tiền mặt'),
          )
          .onPressed,
      isNull,
    );

    await tester.tap(find.text('Tiền mặt'), warnIfMissed: false);
    await tester.pump();

    expect(repository.paymentMethods, [PaymentMethod.online]);
    repository.pending!.complete(
      paidJourney(
        labJourney(JourneyStatus.paymentPending),
        PaymentMethod.online,
      ),
    );
    await tester.pumpAndSettle();
  });

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

JourneyController paymentController(PaymentJourneyRepository repository) {
  final controller = JourneyController(repository: repository, demoMode: true);
  controller.state = AsyncData(labJourney(JourneyStatus.paymentPending));
  return controller;
}

class PaymentJourneyRepository implements JourneyRepository {
  PaymentJourneyRepository({this.shouldFail = false, this.pending});

  final bool shouldFail;
  final Completer<PatientJourney>? pending;
  final List<PaymentMethod> paymentMethods = [];

  @override
  Future<PatientJourney> acknowledgePayment(
    PatientJourney journey,
    PaymentMethod method,
  ) {
    paymentMethods.add(method);
    if (shouldFail) {
      return Future<PatientJourney>.error(StateError('save failed'));
    }
    return pending?.future ?? Future.value(paidJourney(journey, method));
  }

  @override
  Future<PatientJourney> advance(PatientJourney journey, JourneyEvent event) =>
      Future<PatientJourney>.error(UnimplementedError());

  @override
  Future<PatientJourney> bootstrap({
    required Appointment appointment,
    required String patientId,
  }) => Future<PatientJourney>.error(UnimplementedError());

  @override
  Future<PatientJourney> markNotificationRead(
    PatientJourney journey,
    String notificationId,
  ) => Future<PatientJourney>.error(UnimplementedError());

  @override
  Future<void> reset(PatientJourney journey) =>
      Future<void>.error(UnimplementedError());
}

PatientJourney paidJourney(PatientJourney journey, PaymentMethod method) =>
    journey.copyWith(
      status: JourneyStatus.waitingLab,
      payment: VisitPayment(
        method: method,
        amount: 120000,
        acknowledgedAt: DateTime.utc(2026, 7, 30),
      ),
    );

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
