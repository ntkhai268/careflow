import 'package:careflow_patient/features/journey/application/journey_controller.dart';
import 'package:careflow_patient/features/journey/data/demo_journey_repository.dart';
import 'package:careflow_patient/features/journey/data/shared_preferences_journey_store.dart';
import 'package:careflow_patient/features/journey/domain/journey_models.dart';
import 'package:careflow_patient/features/journey/domain/journey_transition.dart';
import 'package:careflow_patient/models/appointment.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:shared_preferences/shared_preferences.dart';

void main() {
  setUp(() => SharedPreferences.setMockInitialValues({}));

  test('completes the mobile journey with one final visit settlement', () async {
    final controller = await _buildController();

    await controller.advance(JourneyEvent.staffScannedQr);
    expect(controller.state.requireValue!.status, JourneyStatus.waiting);
    await controller.advance(JourneyEvent.doctorCalled);
    await controller.advance(JourneyEvent.consultationStarted);

    await controller.advance(JourneyEvent.laboratoryOrdered);
    expect(controller.state.requireValue!.status, JourneyStatus.waitingLab);
    expect(controller.state.requireValue!.payment, isNull);
    await controller.advance(JourneyEvent.laboratoryStarted);
    await controller.advance(JourneyEvent.laboratoryResultsPublished);
    await controller.advance(JourneyEvent.resultReviewCalled);
    await controller.advance(JourneyEvent.finalPrescriptionIssued);

    final pending = controller.state.requireValue!;
    expect(pending.status, JourneyStatus.settlementPending);
    expect(pending.settlement!.totalVisitCost, 535000);
    expect(pending.settlement!.amountDue, 385000);

    await controller.advance(JourneyEvent.settlementPaymentRequested);
    expect(controller.state.requireValue!.status, JourneyStatus.paymentDue);
    expect(await controller.acknowledgeSettlement(PaymentMethod.online), isTrue);
    expect(controller.state.requireValue!.settlement!.status, VisitSettlementStatus.settled);

    await controller.advance(JourneyEvent.medicationDispensed);
    await controller.advance(JourneyEvent.visitCompleted);
    expect(controller.state.requireValue!.status, JourneyStatus.completed);
  });
}

Future<JourneyController> _buildController() async {
  final controller = JourneyController(
    repository: DemoJourneyRepository(
      store: SharedPreferencesJourneyStore(),
      now: () => DateTime.utc(2026, 8, 18, 3, 30),
    ),
    demoMode: true,
  );
  await controller.bootstrap(
    appointment: appointmentFor('apt-integration'),
    patientId: 'patient-1',
  );
  return controller;
}

Appointment appointmentFor(String id) => Appointment(
  id: id,
  patientId: 'patient-1',
  department: 'NOI_TONG_QUAT',
  departmentDisplayName: 'Nội tổng quát',
  doctorName: 'BS. Nguyễn Minh Anh',
  appointmentDate: DateTime.utc(2026, 8, 18, 3),
  timeSlot: '10:30 - 11:30',
  status: 'CONFIRMED',
  statusDisplayName: 'Đã xác nhận',
);
