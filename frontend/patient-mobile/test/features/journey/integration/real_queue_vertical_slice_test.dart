import 'package:careflow_patient/features/journey/application/journey_providers.dart';
import 'package:careflow_patient/features/journey/presentation/clinic_queue_screen.dart';
import 'package:careflow_patient/features/journey/presentation/visit_ticket_screen.dart';
import 'package:careflow_patient/models/queue.dart';
import 'package:careflow_patient/services/api_service.dart';
import 'package:careflow_patient/services/queue_service.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:qr_flutter/qr_flutter.dart';

void main() {
  testWidgets('production ticket renders Queue Service QR and assigned room', (
    tester,
  ) async {
    await tester.pumpWidget(
      app(const VisitTicketScreen(appointmentId: 'apt-1')),
    );
    await tester.pumpAndSettle();

    expect(find.text('Số thứ tự'), findsOneWidget);
    expect(find.text('TK-047'), findsNWidgets(2));
    expect(find.text('Phòng 21 - Lầu 1 khu A'), findsOneWidget);
    expect(
      find.text('Xuất trình QR tại phòng khám để check-in'),
      findsOneWidget,
    );
    expect(find.byType(QrImageView), findsOneWidget);
  });

  testWidgets('production queue renders live called state', (tester) async {
    await tester.pumpWidget(
      app(const ClinicQueueScreen(appointmentId: 'apt-1')),
    );
    await tester.pumpAndSettle();

    expect(find.text('Đã đến lượt bạn'), findsOneWidget);
    expect(find.text('Số thứ tự TK-047'), findsOneWidget);
    expect(find.text('ROOM-21'), findsOneWidget);
  });
}

Widget app(Widget home) => ProviderScope(
  overrides: [
    demoModeProvider.overrideWithValue(false),
    queueServiceProvider.overrideWithValue(FakeQueueService()),
  ],
  child: MaterialApp(home: home),
);

class FakeQueueService extends QueueService {
  FakeQueueService() : super(ApiService());

  @override
  Future<VisitTicket> getTicket(String appointmentId) async => VisitTicket(
    ticketId: 'ticket-1',
    ticketCode: 'TK-047',
    appointmentId: appointmentId,
    patientId: 'patient-1',
    queueNumber: 'TK-047',
    department: 'Thần kinh',
    departmentDisplayName: 'Thần kinh',
    roomId: 'ROOM-21',
    roomDisplayName: 'Phòng 21 - Lầu 1 khu A',
    appointmentDate: DateTime(2026, 8, 18),
    timeSlot: '10:30-11:00',
    qrToken: 'signed-queue-ticket-token',
    status: 'TICKET_ISSUED',
  );

  @override
  Future<PatientQueueStatus> getCurrent(String patientId) async =>
      const PatientQueueStatus(
        entryId: 'entry-1',
        patientId: 'patient-1',
        roomCode: 'ROOM-21',
        queueNumber: 'TK-047',
        status: 'CALLED',
        position: 0,
        estimatedWaitMinutes: 0,
      );
}
