import 'dart:async';

import 'package:careflow_patient/features/journey/application/journey_controller.dart';
import 'package:careflow_patient/features/journey/application/journey_providers.dart';
import 'package:careflow_patient/features/journey/data/demo_journey_repository.dart';
import 'package:careflow_patient/features/journey/data/journey_repository.dart';
import 'package:careflow_patient/features/journey/data/journey_store.dart';
import 'package:careflow_patient/features/journey/data/shared_preferences_journey_store.dart';
import 'package:careflow_patient/features/journey/domain/journey_models.dart';
import 'package:careflow_patient/features/journey/domain/journey_transition.dart';
import 'package:careflow_patient/models/appointment.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';

class ControlledBootstrapRepository implements JourneyRepository {
  final patientAStarted = Completer<void>();
  final patientBStarted = Completer<void>();
  final patientAResult = Completer<PatientJourney>();
  final patientBResult = Completer<PatientJourney>();

  @override
  Future<PatientJourney> bootstrap({
    required Appointment appointment,
    required String patientId,
  }) {
    if (patientId == 'patient-a') {
      patientAStarted.complete();
      return patientAResult.future;
    }
    patientBStarted.complete();
    return patientBResult.future;
  }

  @override
  Future<PatientJourney> acknowledgePayment(
    PatientJourney journey,
    PaymentMethod method,
  ) => Future<PatientJourney>.error(UnimplementedError());

  @override
  Future<PatientJourney> advance(PatientJourney journey, JourneyEvent event) =>
      Future<PatientJourney>.error(UnimplementedError());

  @override
  Future<PatientJourney> markNotificationRead(
    PatientJourney journey,
    String notificationId,
  ) => Future<PatientJourney>.error(UnimplementedError());

  @override
  Future<void> reset(PatientJourney journey) =>
      Future<void>.error(UnimplementedError());
}

class ConfigurablePersistence implements JourneyPersistenceAdapter {
  bool setSucceeds = true;
  bool removeSucceeds = true;

  @override
  String? getString(String key) => null;

  @override
  Future<bool> remove(String key) async => removeSucceeds;

  @override
  Future<bool> setString(String key, String value) async => setSucceeds;
}

void main() {
  setUp(() => SharedPreferences.setMockInitialValues({}));

  test(
    'switching patients clears memory and isolates namespaced journeys',
    () async {
      final controller = JourneyController(
        repository: buildRepository(),
        demoMode: true,
      );
      final first = await controller.bootstrap(
        appointment: appointmentFor('apt-1'),
        patientId: 'patient-a',
      );

      final second = await controller.bootstrap(
        appointment: appointmentFor('apt-1'),
        patientId: 'patient-b',
      );

      expect(first.patientId, 'patient-a');
      expect(second.patientId, 'patient-b');
      expect(controller.state.value!.patientId, 'patient-b');
      expect(second, isNot(first));
      controller.dispose();
    },
  );

  test(
    'retains the journey and exposes a Vietnamese error for invalid events',
    () async {
      String? actionError;
      final controller = JourneyController(
        repository: buildRepository(),
        demoMode: true,
        onActionError: (value) => actionError = value,
      );
      final initial = await controller.bootstrap(
        appointment: appointmentFor('apt-1'),
        patientId: 'patient-a',
      );

      await controller.advance(JourneyEvent.doctorCalled);

      expect(controller.state.value, initial);
      expect(actionError, contains('Không thể chuyển'));
      controller.dispose();
    },
  );

  test(
    'production mode rejects demo advancement while leaving the journey intact',
    () async {
      String? actionError;
      final controller = JourneyController(
        repository: buildRepository(),
        demoMode: false,
        onActionError: (value) => actionError = value,
      );
      await controller.advance(JourneyEvent.staffScannedQr);

      expect(controller.state.valueOrNull, isNull);
      expect(actionError, 'Tính năng đang chờ backend triển khai');
      controller.dispose();
    },
  );

  test('converts failed store saves and deletes into AsyncError', () async {
    final persistence = ConfigurablePersistence()..setSucceeds = false;
    final repository = DemoJourneyRepository(
      store: SharedPreferencesJourneyStore(
        persistence: Future.value(persistence),
      ),
      now: () => DateTime.utc(2026, 8, 18, 3, 30),
    );
    final saveController = JourneyController(
      repository: repository,
      demoMode: true,
    );

    await expectLater(
      saveController.bootstrap(
        appointment: appointmentFor('apt-1'),
        patientId: 'patient-a',
      ),
      throwsA(isA<StateError>()),
    );
    expect(saveController.state.hasError, isTrue);

    persistence.setSucceeds = true;
    final deleteController = JourneyController(
      repository: repository,
      demoMode: true,
    );
    await deleteController.bootstrap(
      appointment: appointmentFor('apt-1'),
      patientId: 'patient-a',
    );
    persistence.removeSucceeds = false;

    await deleteController.resetCurrentJourney();

    expect(deleteController.state.hasError, isTrue);
    expect(deleteController.state.error, isA<StateError>());
    saveController.dispose();
    deleteController.dispose();
  });

  test('does not bootstrap or persist demo data in production mode', () async {
    final controller = JourneyController(
      repository: buildRepository(),
      demoMode: false,
    );
    final preferences = await SharedPreferences.getInstance();

    await expectLater(
      controller.bootstrap(
        appointment: appointmentFor('apt-1'),
        patientId: 'patient-a',
      ),
      throwsA(anything),
    );

    expect(controller.state.hasError, isTrue);
    expect(
      preferences.containsKey(journeyStorageKey('patient-a', 'apt-1')),
      isFalse,
    );
    controller.dispose();
  });

  test(
    'keeps the most recent patient active when an earlier bootstrap finishes late',
    () async {
      final repository = ControlledBootstrapRepository();
      final controller = JourneyController(
        repository: repository,
        demoMode: true,
      );

      final first = controller.bootstrap(
        appointment: appointmentFor('apt-1'),
        patientId: 'patient-a',
      );
      await repository.patientAStarted.future;
      final second = controller.bootstrap(
        appointment: appointmentFor('apt-2'),
        patientId: 'patient-b',
      );
      await repository.patientBStarted.future;
      repository.patientBResult.complete(
        journeyForPatient('patient-b', 'apt-2'),
      );
      await second;
      repository.patientAResult.complete(
        journeyForPatient('patient-a', 'apt-1'),
      );
      await first;

      expect(controller.state.value!.patientId, 'patient-b');
      expect(controller.state.value!.appointmentId, 'apt-2');
      controller.dispose();
    },
  );

  test('uses an unavailable repository when demo mode is disabled', () {
    final container = ProviderContainer(
      overrides: [demoModeProvider.overrideWithValue(false)],
    );

    expect(
      container.read(journeyRepositoryProvider),
      isNot(isA<DemoJourneyRepository>()),
    );
    container.dispose();
  });

  test('demo mode provider defaults to the compile-time environment flag', () {
    final container = ProviderContainer();

    expect(container.read(demoModeProvider), isFalse);
    container.dispose();
  });
}

DemoJourneyRepository buildRepository() => DemoJourneyRepository(
  store: SharedPreferencesJourneyStore(),
  now: () => DateTime.utc(2026, 8, 18, 3, 30),
);

Appointment appointmentFor(String id) => Appointment(
  id: id,
  patientId: 'patient-a',
  department: 'NOI_TONG_QUAT',
  departmentDisplayName: 'Nội tổng quát',
  appointmentDate: DateTime.utc(2026, 8, 18, 3),
  timeSlot: '10:30 - 11:30',
  status: 'CONFIRMED',
  statusDisplayName: 'Đã xác nhận',
);

PatientJourney journeyForPatient(String patientId, String appointmentId) =>
    PatientJourney(
      appointmentId: appointmentId,
      patientId: patientId,
      status: JourneyStatus.ticketIssued,
      laboratoryOrders: const [],
      timeline: const [],
      notifications: const [],
      updatedAt: DateTime.utc(2026, 8, 18, 3, 30),
    );
