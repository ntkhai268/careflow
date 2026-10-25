import 'dart:async';
import 'package:careflow_patient/providers/auth_provider.dart';
import 'package:careflow_patient/providers/patient_provider.dart';
import 'package:careflow_patient/screens/appointment/appointment_detail_screen.dart';
import 'package:careflow_patient/screens/main_shell.dart';
import 'package:careflow_patient/features/journey/application/journey_providers.dart';
import 'package:careflow_patient/features/journey/data/journey_repository.dart';
import 'package:careflow_patient/services/api_service.dart';
import 'package:careflow_patient/services/appointment_service.dart';
import 'package:careflow_patient/services/auth_service.dart';
import 'package:careflow_patient/services/patient_service.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:intl/date_symbol_data_local.dart';
import 'family_booking_access_test.dart' show FamilyPatients, FamilyAppointments;

void main() {
  setUpAll(() => initializeDateFormatting('vi'));

  testWidgets('reloading the appointments tab restores auth before loading profiles', (tester) async {
    final service = RestoringAuthService();
    final container = ProviderContainer(overrides: [
      authProvider.overrideWith((ref) => AuthNotifier(service)),
      patientServiceProvider.overrideWithValue(FamilyPatients()),
      appointmentServiceProvider.overrideWithValue(FamilyAppointments()),
      journeyRepositoryProvider.overrideWithValue(const UnavailableJourneyRepository()),
    ]);
    addTearDown(container.dispose);
    await tester.pumpWidget(UncontrolledProviderScope(
      container: container,
      child: const MaterialApp(home: MainShell(initialIndex: 1)),
    ));
    await tester.pump();
    expect(container.read(authProvider).status, AuthStatus.loading);
    service.restored.complete(UserInfo(
      id: 'owner', username: 'Owner', email: 'owner@example.test', role: 'PATIENT',
    ));
    // Other tabs can keep loading their own data in the IndexedStack.
    for (var frame = 0; frame < 10; frame++) {
      await tester.pump(const Duration(milliseconds: 100));
    }
    expect(find.text('Primary Profile'), findsOneWidget);
    expect(find.text('Family Booking Profile'), findsOneWidget);
    expect(container.read(patientProvider).patient?.id, 'primary');
  });

  for (final validSession in [true, false]) {
    testWidgets('direct appointment reload restores session: $validSession', (tester) async {
      final service = RestoringAuthService();
      final container = ProviderContainer(overrides: [
        authProvider.overrideWith((ref) => AuthNotifier(service)),
        patientServiceProvider.overrideWithValue(FamilyPatients()),
        appointmentServiceProvider.overrideWithValue(FamilyAppointments()),
      ]);
      addTearDown(container.dispose);
      await tester.pumpWidget(UncontrolledProviderScope(
        container: container,
        child: const MaterialApp(home: AppointmentDetailScreen(appointmentId: 'family-booking')),
      ));
      await tester.pump();
      expect(find.byType(CircularProgressIndicator), findsOneWidget);
      expect(find.text('Bạn không có quyền xem phiếu khám này.'), findsNothing);
      service.restored.complete(validSession
          ? UserInfo(id: 'owner', username: 'Owner', email: 'owner@example.test', role: 'PATIENT')
          : null);
      await tester.pumpAndSettle();
      if (validSession) {
        expect(find.textContaining('Family Booking Profile'), findsWidgets);
        expect(container.read(patientProvider).patient?.id, 'relative');
        expect(find.text('Bạn không có quyền xem phiếu khám này.'), findsNothing);
      } else {
        expect(find.text('Bạn không có quyền xem phiếu khám này.'), findsOneWidget);
        expect(container.read(patientProvider).patient, isNull);
      }
    });
  }
}

class RestoringAuthService extends AuthService {
  RestoringAuthService() : super(ApiService());
  final restored = Completer<UserInfo?>();
  @override
  Future<bool> isAuthenticated() async => true;
  @override
  Future<UserInfo?> fetchCurrentUser() => restored.future;
}
