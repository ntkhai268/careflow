import 'package:careflow_patient/features/journey/application/journey_providers.dart';
import 'package:careflow_patient/features/journey/domain/journey_models.dart';
import 'package:careflow_patient/features/journey/presentation/clinic_queue_screen.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  testWidgets('shows the number of people ahead while waiting', (tester) async {
    await tester.pumpWidget(
      queueApp(
        AsyncData(waitingJourney),
        const ClinicQueueScreen(appointmentId: 'apt-1'),
      ),
    );

    expect(find.text('Còn 3 người phía trước'), findsOneWidget);
    expect(find.text('Phòng 21'), findsOneWidget);
    expect(find.text('Khoảng 15 phút'), findsOneWidget);
  });

  testWidgets('tells the patient when it is their turn and shows the room', (
    tester,
  ) async {
    await tester.pumpWidget(
      queueApp(
        AsyncData(calledJourney),
        const ClinicQueueScreen(appointmentId: 'apt-1'),
      ),
    );

    expect(find.text('Đã đến lượt bạn'), findsWidgets);
    expect(find.text('Phòng 21'), findsOneWidget);
  });

  testWidgets('shows explicit errors when queue data cannot load', (
    tester,
  ) async {
    await tester.pumpWidget(
      queueApp(
        AsyncError(StateError('offline'), StackTrace.empty),
        const ClinicQueueScreen(appointmentId: 'apt-1'),
      ),
    );

    expect(find.text('Không thể tải trạng thái hàng đợi.'), findsOneWidget);
  });
}

Widget queueApp(AsyncValue<PatientJourney?> journey, Widget child) =>
    ProviderScope(
      overrides: [
        journeyForAppointmentProvider('apt-1').overrideWithValue(journey),
      ],
      child: MaterialApp(home: child),
    );

final waitingJourney = PatientJourney(
  appointmentId: 'apt-1',
  patientId: 'patient-1',
  status: JourneyStatus.waiting,
  ticket: VisitTicket(
    code: 'CF-APT-1',
    qrPayload: 'careflow://visit/apt-1',
    queueNumber: '42',
    hospitalName: 'Bệnh viện Minh Khai',
    specialtyName: 'Nội thần kinh',
    room: 'Phòng 21',
    expectedWindow: '10:30 - 11:30',
  ),
  clinicQueue: QueueSnapshot(
    room: 'Phòng 21',
    peopleAhead: 3,
    expectedWait: 'Khoảng 15 phút',
  ),
  laboratoryOrders: [],
  timeline: [],
  notifications: [],
  updatedAt: DateTime.utc(2026, 7, 30),
);

final calledJourney = PatientJourney(
  appointmentId: 'apt-1',
  patientId: 'patient-1',
  status: JourneyStatus.called,
  ticket: VisitTicket(
    code: 'CF-APT-1',
    qrPayload: 'careflow://visit/apt-1',
    queueNumber: '42',
    hospitalName: 'Bệnh viện Minh Khai',
    specialtyName: 'Nội thần kinh',
    room: 'Phòng 21',
    expectedWindow: '10:30 - 11:30',
  ),
  clinicQueue: QueueSnapshot(
    room: 'Phòng 21',
    peopleAhead: 0,
    expectedWait: 'Ngay bây giờ',
  ),
  laboratoryOrders: [],
  timeline: [],
  notifications: [],
  updatedAt: DateTime.utc(2026, 7, 30),
);
