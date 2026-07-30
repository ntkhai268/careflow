import 'package:careflow_patient/features/journey/application/journey_providers.dart';
import 'package:careflow_patient/features/journey/domain/journey_models.dart';
import 'package:careflow_patient/features/journey/presentation/journey_timeline_screen.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:intl/intl.dart';

void main() {
  testWidgets('shows Vietnamese timeline events in chronological order', (
    tester,
  ) async {
    await tester.pumpWidget(timelineApp(timelineJourney()));

    final checkIn = tester.getTopLeft(find.text('Đã xác nhận check-in'));
    final consultation = tester.getTopLeft(find.text('Bác sĩ đang khám'));

    expect(checkIn.dy, lessThan(consultation.dy));
    expect(find.text('Nhân viên đã quét mã QR.'), findsOneWidget);
    expect(find.text('Bác sĩ đã bắt đầu buổi khám của bạn.'), findsOneWidget);
    expect(
      find.text(
        DateFormat(
          'HH:mm • dd/MM/yyyy',
        ).format(DateTime.utc(2026, 7, 30, 8).toLocal()),
      ),
      findsOneWidget,
    );
  });
}

Widget timelineApp(PatientJourney journey) => ProviderScope(
  overrides: [
    journeyForAppointmentProvider(
      'apt-1',
    ).overrideWithValue(AsyncData(journey)),
  ],
  child: const MaterialApp(home: JourneyTimelineScreen(appointmentId: 'apt-1')),
);

PatientJourney timelineJourney() => PatientJourney(
  appointmentId: 'apt-1',
  patientId: 'patient-1',
  status: JourneyStatus.inConsultation,
  laboratoryOrders: const [],
  timeline: [
    JourneyTimelineEvent(
      id: 'consultation',
      title: 'Bác sĩ đang khám',
      detail: 'Bác sĩ đã bắt đầu buổi khám của bạn.',
      occurredAt: DateTime.utc(2026, 7, 30, 9),
    ),
    JourneyTimelineEvent(
      id: 'check-in',
      title: 'Đã xác nhận check-in',
      detail: 'Nhân viên đã quét mã QR.',
      occurredAt: DateTime.utc(2026, 7, 30, 8),
    ),
  ],
  notifications: const [],
  updatedAt: DateTime.utc(2026, 7, 30),
);
