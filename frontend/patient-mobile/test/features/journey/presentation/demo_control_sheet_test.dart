import 'package:careflow_patient/features/journey/application/journey_controller.dart';
import 'package:careflow_patient/features/journey/application/journey_providers.dart';
import 'package:careflow_patient/features/journey/data/journey_repository.dart';
import 'package:careflow_patient/features/journey/domain/journey_models.dart';
import 'package:careflow_patient/features/journey/domain/journey_transition.dart';
import 'package:careflow_patient/features/journey/presentation/journey_hub_screen.dart';
import 'package:careflow_patient/features/journey/presentation/widgets/demo_control_sheet.dart';
import 'package:careflow_patient/models/patient.dart';
import 'package:careflow_patient/providers/auth_provider.dart';
import 'package:careflow_patient/providers/patient_provider.dart';
import 'package:careflow_patient/services/api_service.dart';
import 'package:careflow_patient/services/auth_service.dart';
import 'package:careflow_patient/services/patient_service.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  testWidgets('does not instantiate demo controls in production mode', (
    tester,
  ) async {
    await tester.pumpWidget(hubApp(demoMode: false));
    expect(find.byType(DemoControlSheet), findsNothing);
  });

  testWidgets('shows the next legal event in demo mode', (tester) async {
    await tester.pumpWidget(hubApp(demoMode: true));
    expect(find.text('Điều khiển mô phỏng'), findsOneWidget);
    expect(find.text('Mô phỏng bệnh nhân quét QR'), findsOneWidget);
    expect(find.text('Mô phỏng đưa vào hàng đợi khám'), findsNothing);
  });

  testWidgets('exposes final settlement controls without lab or pharmacy payment', (
    tester,
  ) async {
    await tester.pumpWidget(
      hubApp(
        demoMode: true,
        controller: RecordingJourneyController(
          journey: ticketJourney.copyWith(status: JourneyStatus.settlementPending),
        ),
      ),
    );

    expect(find.text('Mô phỏng yêu cầu thanh toán phần còn lại'), findsOneWidget);
    expect(find.text('Mô phỏng chờ hoàn khoản dư'), findsNothing);
    expect(find.text('Thanh toán tiền thuốc'), findsNothing);
    expect(find.text('Thanh toán xét nghiệm'), findsNothing);
  });

  testWidgets('sends the staff QR scan event to the controller', (tester) async {
    final controller = RecordingJourneyController();
    await tester.pumpWidget(hubApp(demoMode: true, controller: controller));
    await tester.tap(find.text('Mô phỏng bệnh nhân quét QR'));
    expect(controller.receivedEvents, [JourneyEvent.staffScannedQr]);
  });

  testWidgets('requires confirmation before resetting the appointment', (
    tester,
  ) async {
    final controller = RecordingJourneyController();
    await tester.pumpWidget(hubApp(demoMode: true, controller: controller));
    await tester.tap(find.text('Đặt lại hành trình'));
    await tester.pumpAndSettle();
    expect(find.text('Xác nhận đặt lại hành trình?'), findsOneWidget);
    expect(controller.resetCalls, 0);
    await tester.tap(find.widgetWithText(ElevatedButton, 'Đặt lại'));
    await tester.pumpAndSettle();
    expect(controller.resetCalls, 1);
  });
}

Widget hubApp({bool demoMode = true, JourneyController? controller}) =>
    ProviderScope(
      overrides: [
        authProvider.overrideWith((ref) => PresentationAuthNotifier()),
        patientProvider.overrideWith((ref) => PresentationPatientNotifier(ref)),
        demoModeProvider.overrideWithValue(demoMode),
        journeyControllerProvider.overrideWith(
          (ref) => controller ?? RecordingJourneyController(),
        ),
      ],
      child: const MaterialApp(home: JourneyHubScreen(appointmentId: 'apt-1')),
    );

class RecordingJourneyController extends JourneyController {
  RecordingJourneyController({PatientJourney? journey})
    : super(repository: const UnavailableJourneyRepository(), demoMode: true) {
    state = AsyncData(journey ?? ticketJourney);
  }

  final List<JourneyEvent> receivedEvents = [];
  int resetCalls = 0;

  @override
  Future<void> advance(JourneyEvent event) async => receivedEvents.add(event);

  @override
  Future<void> resetCurrentJourney() async => resetCalls += 1;
}

final ticketJourney = PatientJourney(
  appointmentId: 'apt-1',
  patientId: 'patient-1',
  status: JourneyStatus.ticketIssued,
  ticket: const VisitTicket(
    code: 'CF-APT-1',
    qrPayload: 'careflow://visit/apt-1',
    queueNumber: '42',
    hospitalName: 'Bệnh viện Minh Khai',
    specialtyName: 'Nội tổng quát',
    room: 'Phòng 21',
    expectedWindow: '10:30 - 11:30',
  ),
  laboratoryOrders: const [],
  timeline: const [],
  notifications: const [],
  updatedAt: DateTime.utc(2026, 7, 30),
);

class PresentationAuthNotifier extends AuthNotifier {
  PresentationAuthNotifier() : super(AuthService(ApiService(), useMock: true)) {
    state = const AuthState(status: AuthStatus.authenticated, userId: 'user-1');
  }
}

class PresentationPatientNotifier extends PatientNotifier {
  PresentationPatientNotifier(Ref ref)
    : super(PatientService(ApiService()), ref) {
    state = PatientState(
      patient: Patient(id: 'patient-1', userId: 'user-1', fullName: 'Nguyễn An'),
    );
  }
}
