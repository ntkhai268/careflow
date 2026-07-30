import 'package:careflow_patient/config/router.dart';
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
    'production router and journey controls complete the laboratory demo',
    (tester) async {
      final controller = await _buildController();
      final hubPath = '/journey/${appointment.id}';
      appRouter.go(hubPath);

      await tester.pumpWidget(_productionJourneyApp(controller));
      await tester.pumpAndSettle();

      _expectStatus(controller, JourneyStatus.ticketIssued);
      expect(
        find.text('Vui lòng đưa mã QR cho nhân viên để xác nhận đến khám.'),
        findsOneWidget,
      );
      await _openDestination(
        tester,
        label: 'Xem phiếu khám',
        path: '$hubPath/ticket',
        instruction: 'Vui lòng đưa mã QR cho nhân viên để xác nhận đến khám.',
      );

      await _tapDemoEvent(
        tester,
        controller,
        label: 'Mô phỏng nhân viên quét QR',
        status: JourneyStatus.checkedIn,
      );
      expect(
        find.text('Nhân viên đang đưa bạn vào hàng đợi phòng khám.'),
        findsOneWidget,
      );
      await _openDestination(
        tester,
        label: 'Xem phiếu khám',
        path: '$hubPath/ticket',
        instruction: 'Nhân viên đang đưa bạn vào hàng đợi phòng khám.',
      );

      await _tapDemoEvent(
        tester,
        controller,
        label: 'Mô phỏng nhân viên đưa vào hàng đợi',
        status: JourneyStatus.waiting,
      );
      await _openDestination(
        tester,
        label: 'Theo dõi hàng đợi',
        path: '$hubPath/queue',
        instruction: 'Còn 3 người phía trước',
      );

      await _tapDemoEvent(
        tester,
        controller,
        label: 'Mô phỏng bác sĩ gọi',
        status: JourneyStatus.called,
      );
      await _openDestination(
        tester,
        label: 'Theo dõi hàng đợi',
        path: '$hubPath/queue',
        instruction: 'Vui lòng đến phòng khám ngay.',
      );

      await _tapDemoEvent(
        tester,
        controller,
        label: 'Mô phỏng bác sĩ bắt đầu khám',
        status: JourneyStatus.inConsultation,
      );
      await _openDestination(
        tester,
        label: 'Xem trạng thái khám',
        path: '$hubPath/consultation',
        instruction:
            'Vui lòng chờ bác sĩ hoàn tất đánh giá và thông báo bước tiếp theo.',
      );

      await _tapDemoEvent(
        tester,
        controller,
        label: 'Mô phỏng bác sĩ chỉ định xét nghiệm',
        status: JourneyStatus.labOrdered,
      );
      await _openDestination(
        tester,
        label: 'Xem xét nghiệm',
        path: '$hubPath/laboratory',
        instruction: 'Bác sĩ đã chỉ định xét nghiệm cho bạn.',
      );

      await _tapDemoEvent(
        tester,
        controller,
        label: 'Mô phỏng yêu cầu thanh toán',
        status: JourneyStatus.paymentPending,
      );
      await _openDestination(
        tester,
        label: 'Xem xét nghiệm',
        path: '$hubPath/laboratory',
        instruction: 'Vui lòng chọn phương thức thanh toán.',
        returnToHub: false,
      );
      await tester.ensureVisible(find.text('Thanh toán trực tuyến'));
      await tester.tap(find.text('Thanh toán trực tuyến'));
      await tester.pumpAndSettle();
      _expectStatus(controller, JourneyStatus.waitingLab);
      await _scrollToTop(tester);
      expect(
        find.text(
          'Chỉ định đã được tiếp nhận. Vui lòng đến đúng nơi thực hiện.',
        ),
        findsOneWidget,
      );
      await _returnToHub(tester, hubPath);

      await _tapDemoEvent(
        tester,
        controller,
        label: 'Mô phỏng bắt đầu xét nghiệm',
        status: JourneyStatus.labInProgress,
      );
      await _openDestination(
        tester,
        label: 'Xem xét nghiệm',
        path: '$hubPath/laboratory',
        instruction: 'Xét nghiệm đang được thực hiện.',
      );

      await _tapDemoEvent(
        tester,
        controller,
        label: 'Mô phỏng công bố kết quả',
        status: JourneyStatus.labResultReady,
      );
      await _openDestination(
        tester,
        label: 'Xem xét nghiệm',
        path: '$hubPath/laboratory',
        instruction: 'Kết quả xét nghiệm đã sẵn sàng.',
      );

      await _tapDemoEvent(
        tester,
        controller,
        label: 'Mô phỏng đưa vào hàng đợi đọc kết quả',
        status: JourneyStatus.waitingResultReview,
      );
      await _openDestination(
        tester,
        label: 'Xem đọc kết quả',
        path: '$hubPath/result-review',
        instruction: 'Bạn được xếp sau bệnh nhân khám mới tiếp theo',
      );

      await _tapDemoEvent(
        tester,
        controller,
        label: 'Mô phỏng bác sĩ đọc kết quả',
        status: JourneyStatus.resultReview,
      );
      await _openDestination(
        tester,
        label: 'Xem đọc kết quả',
        path: '$hubPath/result-review',
        instruction: 'Bác sĩ đang đọc kết quả',
      );

      await _tapDemoEvent(
        tester,
        controller,
        label: 'Mô phỏng bác sĩ kê đơn sau đọc kết quả',
        status: JourneyStatus.prescribed,
      );
      await _openDestination(
        tester,
        label: 'Xem kết quả lượt khám',
        path: '$hubPath/outcome',
        instruction: 'Paracetamol 500 mg',
      );

      await _tapDemoEvent(
        tester,
        controller,
        label: 'Mô phỏng hoàn tất lượt khám',
        status: JourneyStatus.completed,
      );
      await _openDestination(
        tester,
        label: 'Xem kết quả lượt khám',
        path: '$hubPath/outcome',
        instruction: 'Lượt khám đã hoàn tất',
      );
    },
  );

  testWidgets(
    'real appointment completes the laboratory journey with outcome and inbox',
    (tester) async {
      final controller = await _buildController();
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
      expect(
        completed.notifications.reversed
            .map((item) => (item.title, item.body))
            .toList(),
        _expectedNotificationsNewestFirst,
      );
      harnessKey.currentState!.showNotifications();
      await tester.pumpAndSettle();

      for (final expected in _expectedNotificationsNewestFirst) {
        final body = find.text(expected.$2);
        await tester.scrollUntilVisible(
          body,
          180,
          scrollable: find.byType(Scrollable),
        );
        final tile = find.ancestor(of: body, matching: find.byType(Card));
        expect(tile, findsOneWidget);
        expect(
          find.descendant(of: tile, matching: find.text(expected.$1)),
          findsOneWidget,
        );
      }
    },
  );
}

Future<JourneyController> _buildController() async {
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
  return controller;
}

Widget _productionJourneyApp(JourneyController controller) => ProviderScope(
  overrides: [
    demoModeProvider.overrideWithValue(true),
    journeyAccountScopeProvider.overrideWithValue((
      userId: 'user-1',
      patientId: appointment.patientId,
    )),
    journeyControllerProvider.overrideWith((ref) => controller),
  ],
  child: MaterialApp.router(routerConfig: appRouter),
);

Future<void> _tapDemoEvent(
  WidgetTester tester,
  JourneyController controller, {
  required String label,
  required JourneyStatus status,
}) async {
  await tester.ensureVisible(find.text(label));
  await tester.tap(find.text(label));
  await tester.pumpAndSettle();
  _expectStatus(controller, status);
}

Future<void> _openDestination(
  WidgetTester tester, {
  required String label,
  required String path,
  required String instruction,
  bool returnToHub = true,
}) async {
  await tester.ensureVisible(find.text(label));
  await tester.tap(find.text(label));
  await tester.pumpAndSettle();

  expect(find.text(instruction), findsWidgets);
  expect(_screenForPath(path), findsOneWidget);
  expect(
    appRouter.routerDelegate.currentConfiguration.matches.last.matchedLocation,
    path,
  );

  if (returnToHub) {
    await _returnToHub(tester, path.substring(0, path.lastIndexOf('/')));
  }
}

Finder _screenForPath(String path) {
  if (path.endsWith('/ticket')) return find.byType(VisitTicketScreen);
  if (path.endsWith('/queue')) return find.byType(ClinicQueueScreen);
  if (path.endsWith('/consultation')) {
    return find.byType(ConsultationScreen);
  }
  if (path.endsWith('/laboratory')) return find.byType(LaboratoryScreen);
  if (path.endsWith('/result-review')) {
    return find.byType(ResultReviewScreen);
  }
  return find.byType(VisitOutcomeScreen);
}

Future<void> _returnToHub(WidgetTester tester, String hubPath) async {
  await tester.pageBack();
  await tester.pumpAndSettle();
  expect(
    appRouter.routerDelegate.currentConfiguration.matches.last.matchedLocation,
    hubPath,
  );
  expect(find.text('Điều khiển mô phỏng'), findsOneWidget);
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

const _expectedNotificationsNewestFirst = <(String, String)>[
  ('Lượt khám đã hoàn tất', 'Cảm ơn bạn đã sử dụng CareFlow.'),
  (
    'Đơn thuốc đã sẵn sàng',
    'Bác sĩ đã phát hành đơn thuốc sau khi đọc kết quả.',
  ),
  (
    'Đã đến lượt đọc kết quả',
    'Bác sĩ đã bắt đầu đọc kết quả xét nghiệm của bạn.',
  ),
  (
    'Chờ bác sĩ đọc kết quả',
    'Vui lòng quay lại phòng khám để chờ bác sĩ đọc kết quả.',
  ),
  ('Kết quả xét nghiệm đã sẵn sàng', 'Kết quả xét nghiệm đã được công bố.'),
  (
    'Xét nghiệm đang được thực hiện',
    'Phòng xét nghiệm đã bắt đầu thực hiện chỉ định của bạn.',
  ),
  ('Đã xác nhận thanh toán', 'Thanh toán xét nghiệm đã được ghi nhận.'),
  (
    'Cần xác nhận thanh toán',
    'Vui lòng chọn phương thức thanh toán cho xét nghiệm.',
  ),
  (
    'Có chỉ định xét nghiệm',
    'Bác sĩ đã chỉ định các xét nghiệm cần thực hiện.',
  ),
  ('Bác sĩ đang khám', 'Bác sĩ đã bắt đầu buổi khám của bạn.'),
  ('Đã đến lượt bạn', 'Vui lòng đến phòng khám khi được gọi.'),
  ('Đã vào hàng đợi khám', 'Bạn đã được thêm vào hàng đợi của phòng khám.'),
  ('Đã xác nhận check-in', 'Nhân viên đã quét mã QR và xác nhận bạn đến khám.'),
  ('Phiếu khám đã sẵn sàng', 'Phiếu khám điện tử của bạn đã sẵn sàng.'),
];
