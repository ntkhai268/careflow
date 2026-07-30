import 'package:careflow_patient/features/journey/application/journey_providers.dart';
import 'package:careflow_patient/features/journey/domain/journey_models.dart';
import 'package:careflow_patient/features/journey/presentation/result_review_screen.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  testWidgets('explains the result-review queue placement', (tester) async {
    await tester.pumpWidget(resultReviewApp(JourneyStatus.waitingResultReview));

    expect(find.text('Quay lại Phòng 21'), findsOneWidget);
    expect(
      find.text('Bạn được xếp sau bệnh nhân khám mới tiếp theo'),
      findsOneWidget,
    );
  });

  testWidgets('shows when the doctor is reviewing laboratory results', (
    tester,
  ) async {
    await tester.pumpWidget(resultReviewApp(JourneyStatus.resultReview));

    expect(find.text('Bác sĩ đang đọc kết quả'), findsOneWidget);
  });
}

Widget resultReviewApp(JourneyStatus status) => ProviderScope(
  overrides: [
    journeyForAppointmentProvider(
      'apt-1',
    ).overrideWithValue(AsyncData(resultReviewJourney(status))),
  ],
  child: const MaterialApp(home: ResultReviewScreen(appointmentId: 'apt-1')),
);

PatientJourney resultReviewJourney(JourneyStatus status) => PatientJourney(
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
  resultReviewQueue: const QueueSnapshot(
    room: 'Phòng 21',
    peopleAhead: 1,
    expectedWait: 'Sau bệnh nhân khám mới tiếp theo',
  ),
  laboratoryOrders: const [],
  timeline: const [],
  notifications: const [],
  updatedAt: DateTime.utc(2026, 7, 30),
);
