import 'dart:async';

import 'package:careflow_patient/models/appointment.dart';
import 'package:careflow_patient/models/patient.dart';
import 'package:careflow_patient/providers/auth_provider.dart';
import 'package:careflow_patient/providers/patient_provider.dart';
import 'package:careflow_patient/screens/appointment/appointment_screen.dart';
import 'package:careflow_patient/services/api_service.dart';
import 'package:careflow_patient/services/appointment_service.dart';
import 'package:careflow_patient/services/auth_service.dart';
import 'package:careflow_patient/services/patient_service.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  testWidgets(
    'account switch clears old appointments before new profile load fails',
    (tester) async {
      final auth = AppointmentAuthNotifier()
        ..authenticate(userId: 'user-a', name: 'Former User');
      final patientService = SwitchingPatientService();
      final appointmentService = RecordingAppointmentService();
      final container = ProviderContainer(
        overrides: [
          authProvider.overrideWith((ref) => auth),
          patientServiceProvider.overrideWithValue(patientService),
          appointmentServiceProvider.overrideWithValue(appointmentService),
        ],
      );
      addTearDown(container.dispose);

      await container.read(patientProvider.notifier).loadPatient();
      await tester.pumpWidget(
        UncontrolledProviderScope(
          container: container,
          child: const MaterialApp(home: AppointmentScreen()),
        ),
      );
      await tester.pumpAndSettle();

      expect(appointmentService.requestedPatientIds, ['patient-a']);
      expect(find.text('Former Appointment Secret'), findsOneWidget);

      auth.authenticate(userId: 'user-b', name: 'Current User');
      await tester.pump();

      expect(
        find.text('Former Appointment Secret'),
        findsNothing,
        reason: 'old appointments must disappear as soon as identity changes',
      );

      final currentProfileLoad = container
          .read(patientProvider.notifier)
          .loadPatient();
      await patientService.currentLoadStarted.future;
      await tester.pump();

      expect(find.text('Former Appointment Secret'), findsNothing);
      expect(appointmentService.requestedPatientIds, ['patient-a']);

      patientService.currentLoad.completeError(
        Exception('Current profile failed to load'),
      );
      await currentProfileLoad;
      await tester.pumpAndSettle();

      expect(patientService.requestedUserIds, ['user-a', 'user-b']);
      expect(
        appointmentService.requestedPatientIds,
        ['patient-a'],
        reason: 'the old patient ID must never be queried after the switch',
      );
      expect(find.text('Former Appointment Secret'), findsNothing);
    },
  );

  testWidgets('logout prevents retained patient appointment queries', (
    tester,
  ) async {
    final auth = AppointmentAuthNotifier()
      ..authenticate(userId: 'user-a', name: 'Former User');
    final patientService = SwitchingPatientService();
    final appointmentService = RecordingAppointmentService();
    final container = ProviderContainer(
      overrides: [
        authProvider.overrideWith((ref) => auth),
        patientServiceProvider.overrideWithValue(patientService),
        appointmentServiceProvider.overrideWithValue(appointmentService),
      ],
    );
    addTearDown(container.dispose);

    await container.read(patientProvider.notifier).loadPatient();
    auth.logOut();

    await tester.pumpWidget(
      UncontrolledProviderScope(
        container: container,
        child: const MaterialApp(home: AppointmentScreen()),
      ),
    );
    await tester.pumpAndSettle();

    expect(appointmentService.requestedPatientIds, isEmpty);
    expect(find.text('Former Appointment Secret'), findsNothing);
  });
}

class AppointmentAuthNotifier extends AuthNotifier {
  AppointmentAuthNotifier() : super(AuthService(ApiService(), useMock: true));

  void authenticate({required String userId, required String name}) {
    state = AuthState(
      status: AuthStatus.authenticated,
      userId: userId,
      fullName: name,
    );
  }

  void logOut() {
    state = const AuthState(status: AuthStatus.unauthenticated);
  }
}

class SwitchingPatientService extends PatientService {
  SwitchingPatientService() : super(ApiService());

  final requestedUserIds = <String>[];
  final currentLoadStarted = Completer<void>();
  final currentLoad = Completer<Patient?>();

  @override
  Future<Patient?> getPatientByUserId(String userId) async {
    requestedUserIds.add(userId);
    if (userId == 'user-a') {
      return Patient(
        id: 'patient-a',
        userId: userId,
        fullName: 'Former Patient',
      );
    }
    currentLoadStarted.complete();
    return currentLoad.future;
  }
}

class RecordingAppointmentService extends AppointmentService {
  RecordingAppointmentService() : super(ApiService());

  final requestedPatientIds = <String>[];

  @override
  Future<List<Appointment>> getAppointmentsByPatientId(String patientId) async {
    requestedPatientIds.add(patientId);
    return [
      Appointment(
        id: 'former-appointment',
        patientId: patientId,
        patientName: 'Former Patient',
        department: 'NOI_TONG_QUAT',
        departmentDisplayName: 'Former Appointment Secret',
        appointmentDate: DateTime(2026, 7, 30),
        timeSlot: '08:00 - 08:30',
        status: 'CONFIRMED',
        statusDisplayName: 'Đã xác nhận',
      ),
    ];
  }
}
