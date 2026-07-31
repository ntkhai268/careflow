import 'package:careflow_patient/features/journey/application/journey_providers.dart';
import 'package:careflow_patient/features/journey/data/journey_repository.dart';
import 'package:careflow_patient/features/journey/domain/journey_models.dart';
import 'package:careflow_patient/features/journey/presentation/visit_ticket_screen.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:qr_flutter/qr_flutter.dart';

void main() {
  testWidgets('shows the issued ticket details and QR code', (tester) async {
    await tester.pumpWidget(
      journeyApp(
        AsyncData(ticketJourney),
        const VisitTicketScreen(appointmentId: 'apt-1'),
      ),
    );

    expect(find.text('Bệnh viện Minh Khai'), findsOneWidget);
    expect(find.text('Nội thần kinh'), findsOneWidget);
    expect(find.text('Phòng 21'), findsOneWidget);
    expect(find.text('10:30 - 11:30'), findsOneWidget);
    expect(find.text('42'), findsOneWidget);
    expect(
      find.byWidgetPredicate(
        (widget) =>
            widget is Semantics &&
            widget.properties.label == 'Mã QR phiếu khám',
      ),
      findsOneWidget,
    );
    final qrCode = tester.widget<QrImageView>(find.byType(QrImageView));
    expect(qrCode.size, 156);
  });

  testWidgets('asks ticket holders to present QR before they are queued', (
    tester,
  ) async {
    await tester.pumpWidget(
      journeyApp(
        AsyncData(ticketJourney),
        const VisitTicketScreen(appointmentId: 'apt-1'),
      ),
    );

    expect(
      find.text('Vui lòng đưa mã QR cho nhân viên để xác nhận đến khám.'),
      findsOneWidget,
    );
    expect(find.textContaining('đang trong hàng đợi'), findsNothing);
  });

  testWidgets('shows explicit loading and unavailable messages', (
    tester,
  ) async {
    await tester.pumpWidget(
      journeyApp(
        const AsyncLoading(),
        const VisitTicketScreen(appointmentId: 'apt-1'),
      ),
    );
    expect(find.text('Đang tải phiếu khám...'), findsOneWidget);

    await tester.pumpWidget(
      journeyApp(
        AsyncError(const JourneyBackendUnavailable(), StackTrace.empty),
        const VisitTicketScreen(appointmentId: 'apt-1'),
      ),
    );
    expect(
      find.text('Hành trình khám đang chờ backend triển khai.'),
      findsOneWidget,
    );
  });

  testWidgets('wraps long ticket metadata without a narrow-screen overflow', (
    tester,
  ) async {
    await tester.binding.setSurfaceSize(const Size(320, 800));
    addTearDown(() => tester.binding.setSurfaceSize(null));
    final longJourney = ticketJourney.copyWith(
      ticket: const VisitTicket(
        code: 'CF-APT-1',
        qrPayload: 'careflow://visit/apt-1',
        queueNumber: '42',
        hospitalName: 'Bệnh viện Minh Khai',
        specialtyName: 'Chuyên khoa Nội thần kinh và phục hồi chức năng',
        room: 'Chuyên khoa Nội thần kinh và phục hồi chức năng - Phòng 21',
        expectedWindow: '10:30 - 11:30',
      ),
    );

    await tester.pumpWidget(
      journeyApp(
        AsyncData(longJourney),
        const VisitTicketScreen(appointmentId: 'apt-1'),
      ),
    );

    expect(
      find.text('Chuyên khoa Nội thần kinh và phục hồi chức năng'),
      findsOneWidget,
    );
    expect(tester.takeException(), isNull);
  });
}

Widget journeyApp(AsyncValue<PatientJourney?> journey, Widget child) =>
    ProviderScope(
      overrides: [
        journeyForAppointmentProvider('apt-1').overrideWithValue(journey),
      ],
      child: MaterialApp(home: child),
    );

final ticketJourney = PatientJourney(
  appointmentId: 'apt-1',
  patientId: 'patient-1',
  status: JourneyStatus.ticketIssued,
  ticket: VisitTicket(
    code: 'CF-APT-1',
    qrPayload: 'careflow://visit/apt-1',
    queueNumber: '42',
    hospitalName: 'Bệnh viện Minh Khai',
    specialtyName: 'Nội thần kinh',
    room: 'Phòng 21',
    expectedWindow: '10:30 - 11:30',
  ),
  laboratoryOrders: [],
  timeline: [],
  notifications: [],
  updatedAt: DateTime.utc(2026, 7, 30),
);
