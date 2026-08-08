import 'package:careflow_patient/features/journey/domain/journey_models.dart';
import 'package:careflow_patient/features/journey/presentation/widgets/journey_status_card.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  testWidgets('explains that a laboratory indication is ready to view', (
    tester,
  ) async {
    await tester.pumpWidget(
      MaterialApp(
        home: JourneyStatusCard(journey: journey(JourneyStatus.labOrdered)),
      ),
    );

    expect(find.text('Bác sĩ đã chỉ định xét nghiệm'), findsOneWidget);
    expect(
      find.text('Mở hành trình để xem chỉ định và điểm thực hiện.'),
      findsOneWidget,
    );
  });

  testWidgets('shows the active consultation state on the home card', (
    tester,
  ) async {
    await tester.pumpWidget(
      MaterialApp(
        home: JourneyStatusCard(journey: journey(JourneyStatus.inConsultation)),
      ),
    );

    expect(find.text('Đang khám bệnh'), findsOneWidget);
    expect(find.text('Bác sĩ đang khám cho bạn.'), findsOneWidget);
  });
}

PatientJourney journey(JourneyStatus status) => PatientJourney(
  appointmentId: 'apt-1',
  patientId: 'patient-1',
  status: status,
  laboratoryOrders: const [],
  timeline: const [],
  notifications: const [],
  updatedAt: DateTime.utc(2026, 8, 8),
);
