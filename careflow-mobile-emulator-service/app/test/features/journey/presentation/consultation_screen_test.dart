import 'package:careflow_patient/features/journey/application/journey_providers.dart';
import 'package:careflow_patient/features/journey/domain/journey_models.dart';
import 'package:careflow_patient/features/journey/presentation/consultation_screen.dart';
import 'package:careflow_patient/features/journey/presentation/widgets/demo_control_sheet.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  testWidgets('shows the doctor status and consultation room', (tester) async {
    await tester.pumpWidget(consultationApp(demoMode: false));
    expect(find.text('Bác sĩ đang khám'), findsOneWidget);
    expect(find.text('Bác sĩ phụ trách'), findsOneWidget);
    expect(find.text('BS. Nguyễn Minh Anh'), findsOneWidget);
    expect(find.text('Phòng 21'), findsOneWidget);
  });

  testWidgets('shows clinical branching only in demo control', (tester) async {
    await tester.pumpWidget(consultationApp(demoMode: true));
    expect(find.byType(DemoControlSheet), findsOneWidget);
    expect(find.text('Mô phỏng chỉ định xét nghiệm'), findsOneWidget);
    expect(find.text('Mô phỏng kê đơn trực tiếp'), findsOneWidget);
  });
}

Widget consultationApp({required bool demoMode}) => ProviderScope(
  overrides: [
    demoModeProvider.overrideWithValue(demoMode),
    journeyForAppointmentProvider('apt-1').overrideWithValue(
      AsyncData(consultationJourney),
    ),
    activeJourneyProvider.overrideWithValue(consultationJourney),
  ],
  child: const MaterialApp(home: ConsultationScreen(appointmentId: 'apt-1')),
);

final consultationJourney = PatientJourney(
  appointmentId: 'apt-1',
  patientId: 'patient-1',
  status: JourneyStatus.inConsultation,
  doctorName: 'BS. Nguyễn Minh Anh',
  ticket: const VisitTicket(
    code: 'CF-APT-1',
    qrPayload: 'careflow://visit/apt-1',
    queueNumber: '42',
    hospitalName: 'Bệnh viện CareFlow',
    specialtyName: 'Nội tổng quát',
    room: 'Phòng 21',
    expectedWindow: '10:30 - 11:30',
  ),
  laboratoryOrders: const [],
  timeline: const [],
  notifications: const [],
  updatedAt: DateTime.utc(2026, 7, 30),
);
