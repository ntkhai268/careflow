import 'package:careflow_patient/features/journey/application/journey_controller.dart';
import 'package:careflow_patient/features/journey/application/journey_providers.dart';
import 'package:careflow_patient/features/journey/data/demo_journey_repository.dart';
import 'package:careflow_patient/features/journey/data/shared_preferences_journey_store.dart';
import 'package:careflow_patient/features/journey/domain/journey_models.dart';
import 'package:careflow_patient/features/journey/domain/journey_transition.dart';
import 'package:careflow_patient/features/journey/presentation/clinic_queue_screen.dart';
import 'package:careflow_patient/features/journey/presentation/consultation_screen.dart';
import 'package:careflow_patient/features/journey/presentation/journey_notification_screen.dart';
import 'package:careflow_patient/features/journey/presentation/laboratory_screen.dart';
import 'package:careflow_patient/features/journey/presentation/result_review_screen.dart';
import 'package:careflow_patient/features/journey/presentation/visit_outcome_screen.dart';
import 'package:careflow_patient/features/journey/presentation/visit_ticket_screen.dart';
import 'package:careflow_patient/models/appointment.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:intl/date_symbol_data_local.dart';
import 'package:shared_preferences/shared_preferences.dart';

void main() {
  setUpAll(() => initializeDateFormatting('vi'));
  setUp(() => SharedPreferences.setMockInitialValues({}));

  testWidgets(
    'real appointment completes the laboratory journey with outcome and inbox',
    (tester) async {
      final clock = _AdvancingClock(DateTime.utc(2026, 8, 18, 3, 30));
      final controller = JourneyController(
        repository: DemoJourneyRepository(
          store: SharedPreferencesJourneyStore(),
          now: clock.call,
        ),
        demoMode: true,
      );
      await controller.bootstrap(
        appointment: appointment,
        patientId: appointment.patientId,
      );
      final harnessKey = GlobalKey<_JourneyHarnessState>();

      await tester.pumpWidget(
        ProviderScope(
          overrides: [
            demoModeProvider.overrideWithValue(true),
            journeyAccountScopeProvider.overrideWithValue((
              userId: 'user-1',
              patientId: appointment.patientId,
            )),
            journeyControllerProvider.overrideWith((ref) => controller),
          ],
          child: MaterialApp(
            home: _JourneyHarness(
              key: harnessKey,
              appointmentId: appointment.id,
            ),
          ),
        ),
      );
      await tester.pumpAndSettle();

      _expectStatus(controller, JourneyStatus.ticketIssued);
      expect(find.text('Phiếu khám đã sẵn sàng'), findsOneWidget);
      expect(
        find.text('Vui lòng đưa mã QR cho nhân viên để xác nhận đến khám.'),
        findsOneWidget,
      );
      expect(
        find.text('Bệnh viện CareFlow (dữ liệu mô phỏng)'),
        findsOneWidget,
      );
      expect(find.text('Nội tổng quát - Phòng 21'), findsOneWidget);

      await _advance(controller, tester, JourneyEvent.staffScannedQr);
      await _advance(controller, tester, JourneyEvent.admittedToClinicQueue);
      _expectStatus(controller, JourneyStatus.waiting);
      expect(find.text('Đang chờ khám'), findsOneWidget);
      expect(find.text('Vui lòng theo dõi thứ tự hàng đợi.'), findsOneWidget);
      expect(find.text('Còn 3 người phía trước'), findsOneWidget);
      expect(find.text('Nội tổng quát - Phòng 21'), findsOneWidget);

      await _advance(controller, tester, JourneyEvent.doctorCalled);
      _expectStatus(controller, JourneyStatus.called);
      expect(find.text('Vui lòng đến phòng khám ngay.'), findsOneWidget);
      expect(find.text('Đã đến lượt bạn'), findsWidgets);
      expect(find.text('Nội tổng quát - Phòng 21'), findsOneWidget);

      await _advance(controller, tester, JourneyEvent.consultationStarted);
      _expectStatus(controller, JourneyStatus.inConsultation);
      expect(find.text('Bác sĩ đang khám cho bạn.'), findsOneWidget);
      expect(find.text('BS. Nguyễn Minh Anh'), findsOneWidget);
      expect(find.text('Nội tổng quát - Phòng 21'), findsOneWidget);

      await _advance(controller, tester, JourneyEvent.laboratoryOrdered);
      await _advance(controller, tester, JourneyEvent.paymentRequested);
      _expectStatus(controller, JourneyStatus.paymentPending);
      expect(
        find.text('Vui lòng chọn phương thức thanh toán.'),
        findsOneWidget,
      );
      expect(find.text('Phòng xét nghiệm tầng 1'), findsOneWidget);
      expect(find.text('Thanh toán trực tuyến'), findsOneWidget);

      await tester.ensureVisible(find.text('Thanh toán trực tuyến'));
      await tester.tap(find.text('Thanh toán trực tuyến'));
      await tester.pumpAndSettle();
      _expectStatus(controller, JourneyStatus.waitingLab);
      expect(
        find.text(
          'Chỉ định đã được tiếp nhận. Vui lòng đến đúng nơi thực hiện.',
        ),
        findsOneWidget,
      );
      expect(find.text('Phòng xét nghiệm tầng 1'), findsOneWidget);

      await _advance(controller, tester, JourneyEvent.laboratoryStarted);
      _expectStatus(controller, JourneyStatus.labInProgress);
      await _scrollToTop(tester);
      expect(find.text('Xét nghiệm đang được thực hiện.'), findsOneWidget);
      expect(find.text('Phòng xét nghiệm tầng 1'), findsOneWidget);

      await _advance(
        controller,
        tester,
        JourneyEvent.laboratoryResultsPublished,
      );
      _expectStatus(controller, JourneyStatus.labResultReady);
      await _scrollToTop(tester);
      expect(find.text('Kết quả xét nghiệm đã sẵn sàng.'), findsOneWidget);
      await tester.scrollUntilVisible(
        find.text('5.2 G/L'),
        180,
        scrollable: find.byType(Scrollable),
      );
      expect(find.text('5.2 G/L'), findsOneWidget);
      expect(find.text('Dữ liệu mô phỏng'), findsOneWidget);
      expect(
        controller.state.requireValue!.laboratoryOrders.map(
          (order) => order.result?.source,
        ),
        everyElement('Dữ liệu mô phỏng'),
      );

      await _advance(
        controller,
        tester,
        JourneyEvent.admittedToResultReviewQueue,
      );
      _expectStatus(controller, JourneyStatus.waitingResultReview);
      expect(find.text('Quay lại Nội tổng quát - Phòng 21'), findsOneWidget);
      expect(
        find.text('Bạn được xếp sau bệnh nhân khám mới tiếp theo'),
        findsOneWidget,
      );

      await _advance(controller, tester, JourneyEvent.resultReviewCalled);
      _expectStatus(controller, JourneyStatus.resultReview);
      expect(find.text('Bác sĩ đang đọc kết quả'), findsOneWidget);
      expect(find.text('Nội tổng quát - Phòng 21'), findsOneWidget);

      await _advance(controller, tester, JourneyEvent.finalPrescriptionIssued);
      _expectStatus(controller, JourneyStatus.prescribed);
      expect(find.text('Đơn thuốc'), findsOneWidget);
      expect(find.text('Paracetamol 500 mg'), findsOneWidget);
      await tester.scrollUntilVisible(
        find.text('Tái khám'),
        180,
        scrollable: find.byType(Scrollable),
      );
      expect(find.text('Tái khám'), findsOneWidget);

      await _advance(controller, tester, JourneyEvent.visitCompleted);
      _expectStatus(controller, JourneyStatus.completed);
      await _scrollToTop(tester);
      expect(find.text('Lượt khám đã hoàn tất'), findsOneWidget);
      expect(find.text('Paracetamol 500 mg'), findsOneWidget);
      await tester.scrollUntilVisible(
        find.text('Amoxicillin 500 mg'),
        180,
        scrollable: find.byType(Scrollable),
      );
      expect(find.text('Amoxicillin 500 mg'), findsOneWidget);
      await tester.scrollUntilVisible(
        find.text('Tái khám'),
        180,
        scrollable: find.byType(Scrollable),
      );
      expect(find.text('Tái khám'), findsOneWidget);
      expect(find.text('Nội tổng quát - Phòng 21'), findsOneWidget);

      final completed = controller.state.requireValue!;
      expect(completed.notifications, hasLength(14));
      harnessKey.currentState!.showNotifications();
      await tester.pumpAndSettle();

      for (final notification in completed.notifications.reversed) {
        final tile = find.byKey(Key('notification-${notification.id}'));
        await tester.scrollUntilVisible(
          tile,
          180,
          scrollable: find.byType(Scrollable),
        );
        expect(tile, findsOneWidget);
        expect(
          find.descendant(of: tile, matching: find.text(notification.body)),
          findsOneWidget,
        );
      }
    },
  );
}

Future<void> _advance(
  JourneyController controller,
  WidgetTester tester,
  JourneyEvent event,
) async {
  await controller.advance(event);
  await tester.pumpAndSettle();
}

void _expectStatus(JourneyController controller, JourneyStatus status) {
  expect(controller.state.requireValue!.status, status);
}

Future<void> _scrollToTop(WidgetTester tester) async {
  await tester.fling(find.byType(Scrollable), const Offset(0, 1000), 1000);
  await tester.pumpAndSettle();
}

final appointment = Appointment(
  id: 'apt-api-2026-0818',
  patientId: 'patient-1',
  patientName: 'Nguyễn An',
  department: 'NOI_TONG_QUAT',
  departmentDisplayName: 'Nội tổng quát',
  doctorId: 'doctor-42',
  doctorName: 'BS. Nguyễn Minh Anh',
  appointmentDate: DateTime(2026, 8, 18),
  timeSlot: '10:30 - 11:30',
  status: 'CONFIRMED',
  statusDisplayName: 'Đã xác nhận',
  reason: 'Đau họng và sốt nhẹ',
);

class _JourneyHarness extends StatefulWidget {
  const _JourneyHarness({super.key, required this.appointmentId});

  final String appointmentId;

  @override
  State<_JourneyHarness> createState() => _JourneyHarnessState();
}

class _JourneyHarnessState extends State<_JourneyHarness> {
  bool _showNotifications = false;

  void showNotifications() => setState(() => _showNotifications = true);

  @override
  Widget build(BuildContext context) => Consumer(
    builder: (context, ref, _) {
      if (_showNotifications) {
        return JourneyNotificationScreen(appointmentId: widget.appointmentId);
      }
      final status =
          ref.watch(journeyControllerProvider).valueOrNull?.status ??
          JourneyStatus.booked;
      return switch (status) {
        JourneyStatus.booked ||
        JourneyStatus.ticketIssued ||
        JourneyStatus.checkedIn => VisitTicketScreen(
          appointmentId: widget.appointmentId,
        ),
        JourneyStatus.waiting || JourneyStatus.called => ClinicQueueScreen(
          appointmentId: widget.appointmentId,
        ),
        JourneyStatus.inConsultation => ConsultationScreen(
          appointmentId: widget.appointmentId,
        ),
        JourneyStatus.labOrdered ||
        JourneyStatus.paymentPending ||
        JourneyStatus.waitingLab ||
        JourneyStatus.labInProgress ||
        JourneyStatus.labResultReady => LaboratoryScreen(
          appointmentId: widget.appointmentId,
        ),
        JourneyStatus.waitingResultReview || JourneyStatus.resultReview =>
          ResultReviewScreen(appointmentId: widget.appointmentId),
        JourneyStatus.prescribed || JourneyStatus.completed =>
          VisitOutcomeScreen(appointmentId: widget.appointmentId),
      };
    },
  );
}

class _AdvancingClock {
  _AdvancingClock(this._current);

  DateTime _current;

  DateTime call() {
    final value = _current;
    _current = _current.add(const Duration(minutes: 1));
    return value;
  }
}
