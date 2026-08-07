import 'package:careflow_patient/features/journey/application/journey_providers.dart';
import 'package:careflow_patient/models/appointment.dart';
import 'package:careflow_patient/models/patient.dart';
import 'package:careflow_patient/screens/appointment/booking_step2_screen.dart';
import 'package:careflow_patient/screens/appointment/booking_step3_screen.dart';
import 'package:careflow_patient/services/api_service.dart';
import 'package:careflow_patient/services/appointment_service.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:intl/date_symbol_data_local.dart';

void main() {
  setUpAll(() => initializeDateFormatting('vi'));

  testWidgets('production department failure is visible and fails closed', (
    tester,
  ) async {
    await tester.pumpWidget(
      bookingApp(BookingStep2Screen(patient: patient), demoMode: false),
    );
    await tester.pumpAndSettle();

    expect(
      find.text('Không thể tải danh sách chuyên khoa. Vui lòng thử lại.'),
      findsOneWidget,
    );
    expect(find.text('Nội tổng quát'), findsNothing);
  });

  testWidgets('demo department failure also shows the mapped API error', (
    tester,
  ) async {
    await tester.pumpWidget(
      bookingApp(BookingStep2Screen(patient: patient), demoMode: true),
    );
    await tester.pumpAndSettle();

    expect(
      find.text('Không thể tải danh sách chuyên khoa. Vui lòng thử lại.'),
      findsOneWidget,
    );
    expect(find.text('Nội tổng quát'), findsNothing);
    expect(find.widgetWithText(OutlinedButton, 'Thử lại'), findsOneWidget);
  });

  testWidgets('production time-slot failure is visible and fails closed', (
    tester,
  ) async {
    await tester.pumpWidget(
      bookingApp(
        BookingStep3Screen(patient: patient, department: department),
        demoMode: false,
      ),
    );
    await tester.pumpAndSettle();

    expect(
      find.text('Không thể tải danh sách ca khám. Vui lòng thử lại.'),
      findsOneWidget,
    );
    expect(find.text('07:30-08:00'), findsNothing);
  });

  testWidgets('demo time-slot failure also shows the mapped API error', (
    tester,
  ) async {
    await tester.pumpWidget(
      bookingApp(
        BookingStep3Screen(patient: patient, department: department),
        demoMode: true,
      ),
    );
    await tester.pumpAndSettle();

    expect(
      find.text('Không thể tải danh sách ca khám. Vui lòng thử lại.'),
      findsOneWidget,
    );
    expect(find.text('07:30-08:00'), findsNothing);
    expect(find.widgetWithText(OutlinedButton, 'Thử lại'), findsOneWidget);
  });

  testWidgets('today hides slots whose start time has already passed', (
    tester,
  ) async {
    await tester.pumpWidget(
      bookingApp(
        BookingStep3Screen(
          patient: patient,
          department: department,
          currentTimeOverride: DateTime(2026, 7, 31, 14, 56),
        ),
        demoMode: true,
        service: AvailableReferenceDataService(),
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('07:30-08:00'), findsNothing);
    expect(find.text('14:30-15:00'), findsNothing);
    expect(find.text('15:00-15:30'), findsOneWidget);
    expect(find.text('Không còn ca phù hợp trong buổi này.'), findsOneWidget);
  });
}

Widget bookingApp(
  Widget home, {
  required bool demoMode,
  AppointmentService? service,
}) => ProviderScope(
  overrides: [
    demoModeProvider.overrideWithValue(demoMode),
    appointmentServiceProvider.overrideWithValue(
      service ?? FailingReferenceDataService(),
    ),
  ],
  child: MaterialApp(home: home),
);

final patient = Patient(
  id: 'patient-1',
  userId: 'user-1',
  fullName: 'Nguyễn An',
);

final department = Department(code: 'NOI_TONG_QUAT', name: 'Nội tổng quát');

class FailingReferenceDataService extends AppointmentService {
  FailingReferenceDataService() : super(ApiService());

  @override
  Future<List<Department>> getDepartments() =>
      Future.error(StateError('gateway unavailable'));

  @override
  Future<List<String>> getTimeSlots({String? department, DateTime? date}) =>
      Future.error(StateError('gateway unavailable'));
}

class AvailableReferenceDataService extends AppointmentService {
  AvailableReferenceDataService() : super(ApiService());

  @override
  Future<List<String>> getTimeSlots({String? department, DateTime? date}) async => const [
    '07:30-08:00',
    '14:30-15:00',
    '15:00-15:30',
  ];
}
