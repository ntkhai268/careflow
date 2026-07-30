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

  testWidgets('demo department failure uses explicit demo reference data', (
    tester,
  ) async {
    await tester.pumpWidget(
      bookingApp(BookingStep2Screen(patient: patient), demoMode: true),
    );
    await tester.pumpAndSettle();

    expect(find.text('Nội tổng quát'), findsOneWidget);
    expect(find.text('Dữ liệu chuyên khoa mô phỏng'), findsOneWidget);
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

  testWidgets('demo time-slot failure uses explicit demo reference data', (
    tester,
  ) async {
    await tester.pumpWidget(
      bookingApp(
        BookingStep3Screen(patient: patient, department: department),
        demoMode: true,
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('07:30-08:00'), findsOneWidget);
    expect(find.text('Dữ liệu ca khám mô phỏng'), findsOneWidget);
  });
}

Widget bookingApp(Widget home, {required bool demoMode}) => ProviderScope(
  overrides: [
    demoModeProvider.overrideWithValue(demoMode),
    appointmentServiceProvider.overrideWithValue(FailingReferenceDataService()),
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
  Future<List<String>> getTimeSlots() =>
      Future.error(StateError('gateway unavailable'));
}
