import 'package:careflow_patient/features/journey/application/journey_providers.dart';
import 'package:careflow_patient/features/journey/domain/journey_models.dart';
import 'package:careflow_patient/features/journey/presentation/visit_outcome_screen.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  testWidgets('shows the complete outcome, prescription, and follow-up', (
    tester,
  ) async {
    await tester.pumpWidget(outcomeApp(completedJourney()));

    expect(find.text('Viêm họng cấp'), findsOneWidget);
    expect(
      find.text('Niêm mạc họng sung huyết, chưa ghi nhận biến chứng.'),
      findsOneWidget,
    );
    expect(find.text('Paracetamol 500 mg'), findsOneWidget);
    expect(find.text('1 viên • Uống • 3 lần/ngày • 5 ngày'), findsOneWidget);
    expect(
      find.text('Uống sau ăn; không dùng quá liều khuyến cáo.'),
      findsOneWidget,
    );
    expect(find.text('Amoxicillin 500 mg'), findsOneWidget);
    expect(find.text('1 viên • Uống • 2 lần/ngày • 7 ngày'), findsOneWidget);
    expect(find.text('Uống đủ liệu trình theo chỉ định.'), findsOneWidget);
    final followUpDate = find.text('Ngày tái khám: 06/08/2026 lúc 16:00');
    await tester.scrollUntilVisible(followUpDate, 200);
    expect(followUpDate, findsOneWidget);
    expect(find.text('Phòng khám Nội tổng quát 21'), findsOneWidget);
  });

  testWidgets(
    'omits optional outcome sections when the direct visit has none',
    (tester) async {
      await tester.pumpWidget(
        outcomeApp(
          completedJourney().copyWith(
            diagnosis: null,
            prescription: null,
            followUp: null,
            laboratoryOrders: const [],
          ),
        ),
      );

      expect(find.text('Chẩn đoán'), findsNothing);
      expect(find.text('Đơn thuốc'), findsNothing);
      expect(find.text('Tái khám'), findsNothing);
      expect(find.text('Xét nghiệm đã thực hiện'), findsNothing);
      expect(find.text('null'), findsNothing);
    },
  );

  testWidgets(
    'keeps separators between value-equal prescription and lab items',
    (tester) async {
      const duplicateItem = PrescriptionItem(
        medicationName: 'Vitamin C 500 mg',
        dosage: '1 viên',
        route: 'Uống',
        frequency: '1 lần/ngày',
        duration: '7 ngày',
        caution: 'Uống sau ăn.',
      );
      final duplicateOrder = LaboratoryOrder(
        id: 'duplicate-order',
        name: 'Xét nghiệm đường huyết',
        department: 'Khoa Xét nghiệm',
        destination: 'Phòng xét nghiệm',
        preparationNote: 'Nhịn ăn',
        price: 100000,
      );
      await tester.pumpWidget(
        outcomeApp(
          completedJourney().copyWith(
            prescription: Prescription(
              id: 'duplicate-prescription',
              issuedAt: DateTime.utc(2026, 7, 30),
              items: const [duplicateItem, duplicateItem],
            ),
            laboratoryOrders: [duplicateOrder, duplicateOrder],
            followUp: null,
          ),
        ),
      );

      expect(find.byType(Divider, skipOffstage: false), findsNWidgets(2));
    },
  );
}

Widget outcomeApp(PatientJourney journey) => ProviderScope(
  overrides: [
    journeyForAppointmentProvider(
      'apt-1',
    ).overrideWithValue(AsyncData(journey)),
  ],
  child: const MaterialApp(home: VisitOutcomeScreen(appointmentId: 'apt-1')),
);

PatientJourney completedJourney() => PatientJourney(
  appointmentId: 'apt-1',
  patientId: 'patient-1',
  status: JourneyStatus.completed,
  diagnosis: const DiagnosisSummary(
    title: 'Viêm họng cấp',
    detail: 'Niêm mạc họng sung huyết, chưa ghi nhận biến chứng.',
  ),
  prescription: Prescription(
    id: 'prescription-1',
    issuedAt: DateTime.utc(2026, 7, 30),
    items: const [
      PrescriptionItem(
        medicationName: 'Paracetamol 500 mg',
        dosage: '1 viên',
        route: 'Uống',
        frequency: '3 lần/ngày',
        duration: '5 ngày',
        caution: 'Uống sau ăn; không dùng quá liều khuyến cáo.',
      ),
      PrescriptionItem(
        medicationName: 'Amoxicillin 500 mg',
        dosage: '1 viên',
        route: 'Uống',
        frequency: '2 lần/ngày',
        duration: '7 ngày',
        caution: 'Uống đủ liệu trình theo chỉ định.',
      ),
    ],
  ),
  followUp: FollowUpAppointment(
    scheduledAt: DateTime.utc(2026, 8, 6, 9),
    room: 'Phòng khám Nội tổng quát 21',
    note: 'Tái khám nếu triệu chứng không cải thiện.',
  ),
  laboratoryOrders: const [],
  timeline: const [],
  notifications: const [],
  updatedAt: DateTime.utc(2026, 7, 30),
);
