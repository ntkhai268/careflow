import 'package:careflow_patient/models/appointment.dart';
import 'package:careflow_patient/models/patient.dart';
import 'package:careflow_patient/providers/auth_provider.dart';
import 'package:careflow_patient/providers/patient_provider.dart';
import 'package:careflow_patient/screens/appointment/appointment_detail_screen.dart';
import 'package:careflow_patient/screens/appointment/appointment_screen.dart';
import 'package:careflow_patient/services/api_service.dart';
import 'package:careflow_patient/services/appointment_service.dart';
import 'package:careflow_patient/services/patient_service.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'appointment_account_scope_test.dart' show AppointmentAuthNotifier;
import 'package:intl/date_symbol_data_local.dart';

final primary = Patient(id: 'primary', userId: 'owner', fullName: 'Primary Profile');
final relative = Patient(id: 'relative', userId: 'owner', fullName: 'Family Booking Profile');

void main() {
  setUpAll(() => initializeDateFormatting('vi'));
  Future<ProviderContainer> setup(WidgetTester tester, Widget screen, {bool foreign = false}) async {
    final auth = AppointmentAuthNotifier()..authenticate(userId: 'owner', name: 'Owner');
    final container = ProviderContainer(overrides: [
      authProvider.overrideWith((ref) => auth),
      patientServiceProvider.overrideWithValue(FamilyPatients(foreign: foreign)),
      appointmentServiceProvider.overrideWithValue(FamilyAppointments()),
    ]);
    addTearDown(container.dispose);
    await container.read(patientProvider.notifier).loadPatient();
    await tester.pumpWidget(UncontrolledProviderScope(container: container, child: MaterialApp(home: screen)));
    await tester.pumpAndSettle();
    return container;
  }

  testWidgets('booking for an owned second profile opens and selects that profile', (tester) async {
    final c = await setup(tester, const AppointmentDetailScreen(appointmentId: 'family-booking'));
    expect(find.text('Bạn không có quyền xem phiếu khám này.'), findsNothing);
    expect(c.read(patientProvider).patient?.id, 'relative');
    expect(find.textContaining('Family Booking Profile'), findsWidgets);
  });

  testWidgets('a foreign profile remains denied even when an appointment response arrives', (tester) async {
    final c = await setup(tester, const AppointmentDetailScreen(appointmentId: 'family-booking'), foreign: true);
    expect(find.textContaining('Bạn không có quyền xem phiếu khám này.'), findsOneWidget);
    expect(c.read(patientProvider).patient?.id, 'primary');
    expect(find.text('Family Booking Profile'), findsNothing);
  });

  testWidgets('appointment list includes bookings for both owned profiles', (tester) async {
    await setup(tester, const AppointmentScreen());
    expect(find.text('Primary Profile'), findsOneWidget);
    expect(find.text('Family Booking Profile'), findsOneWidget);
  });
}

class FamilyPatients extends PatientService {
  FamilyPatients({this.foreign = false}) : super(ApiService());
  final bool foreign;
  @override
  Future<Patient?> getPatientByUserId(String userId) async => primary;
  @override
  Future<List<Patient>> getPatientsByUserId(String userId) async => [primary, relative];
  @override
  Future<Patient> getPatientById(String id) async => foreign
      ? Patient(id: 'relative', userId: 'another-account', fullName: 'Family Booking Profile') : relative;
}

class FamilyAppointments extends AppointmentService {
  FamilyAppointments() : super(ApiService());
  Appointment appointment(String patientId) => Appointment(
    id: patientId == 'relative' ? 'family-booking' : 'primary-booking',
    patientId: patientId, patientName: patientId == 'relative' ? relative.fullName : primary.fullName,
    department: 'CO_XUONG_KHOP', departmentDisplayName: 'Cơ xương khớp',
    appointmentDate: DateTime(2026, 10, 17), timeSlot: '16:00-16:30',
    status: 'CANCELLED', statusDisplayName: 'Đã hủy',
  );
  @override
  Future<Appointment> getAppointmentById(String id) async => appointment('relative');
  @override
  Future<List<Appointment>> getAppointmentsByPatientId(String id) async => [appointment(id)];
}
