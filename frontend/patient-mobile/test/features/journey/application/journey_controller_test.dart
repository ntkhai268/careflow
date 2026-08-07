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

class ControllableJourneyStore implements JourneyStore {
  PatientJourney? stored;
  bool blockNextSave = false;
  final saveStarted = Completer<void>();
  final releaseSave = Completer<void>();

  @override
  Future<void> delete(String patientId, String appointmentId) async {
    stored = null;
  }

  @override
  Future<JourneyLoadResult> load(
    String patientId,
    String appointmentId,
  ) async => JourneyLoadResult(journey: stored);

  @override
  Future<void> save(PatientJourney journey) async {
    if (blockNextSave) {
      blockNextSave = false;
      saveStarted.complete();
      await releaseSave.future;
    }
    stored = journey;
  }
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

  test(
    'bootstrap failure is terminal but reset failure retains last good data',
    () async {
      String? actionError;
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
        onActionError: (value) => actionError = value,
      );
      await deleteController.bootstrap(
        appointment: appointmentFor('apt-1'),
        patientId: 'patient-a',
      );
      persistence.removeSucceeds = false;

      await deleteController.resetCurrentJourney();

      expect(deleteController.state.hasError, isFalse);
      expect(deleteController.state.requireValue, isNotNull);
      expect(actionError, 'Không thể đặt lại hành trình. Vui lòng thử lại.');
      saveController.dispose();
      deleteController.dispose();
    },
  );

  test(
    'failed cancellation cleanup keeps the journey retired and can retry',
    () async {
      String? actionError;
      final persistence = ConfigurablePersistence();
      final controller = JourneyController(
        repository: DemoJourneyRepository(
          store: SharedPreferencesJourneyStore(
            persistence: Future.value(persistence),
          ),
          now: () => DateTime.utc(2026, 8, 18, 3, 30),
        ),
        demoMode: true,
        onActionError: (value) => actionError = value,
      );
      await controller.bootstrap(
        appointment: appointmentFor('apt-cancelled'),
        patientId: 'patient-a',
      );
      persistence.removeSucceeds = false;

      expect(
        await controller.retireAppointment(
          patientId: 'patient-a',
          appointmentId: 'apt-cancelled',
        ),
        isFalse,
      );

      expect(controller.state.valueOrNull, isNull);
      expect(
        actionError,
        'Không thể dọn dữ liệu hành trình đã hủy. Vui lòng thử lại.',
      );

      persistence.removeSucceeds = true;
      expect(
        await controller.retireAppointment(
          patientId: 'patient-a',
          appointmentId: 'apt-cancelled',
        ),
        isTrue,
      );
      expect(controller.state.valueOrNull, isNull);
      expect(actionError, isNull);
      controller.dispose();
    },
  );

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

  test('uses the backend repository when demo mode is disabled', () {
    final container = ProviderContainer(
      overrides: [demoModeProvider.overrideWithValue(false)],
    );

    expect(
      container.read(journeyRepositoryProvider).runtimeType.toString(),
      'BackendJourneyRepository',
    );
    container.dispose();
  });

  test(
    'reports an unsuccessful payment acknowledgement when persistence fails',
    () async {
      String? actionError;
      final controller = JourneyController(
        repository: const UnavailableJourneyRepository(),
        demoMode: true,
        onActionError: (value) => actionError = value,
      );
      controller.state = AsyncData(
        journeyForPatient(
          'patient-a',
          'apt-1',
        ).copyWith(status: JourneyStatus.paymentPending),
      );

      expect(
        await controller.acknowledgePayment(PaymentMethod.online),
        isFalse,
      );
      expect(controller.state.hasError, isFalse);
      expect(
        controller.state.requireValue!.status,
        JourneyStatus.paymentPending,
      );
      expect(actionError, 'Không thể cập nhật hành trình. Vui lòng thử lại.');
      controller.dispose();
    },
  );

  test(
    'failed persisted mutation retains data and succeeds on retry',
    () async {
      String? actionError;
      final persistence = ConfigurablePersistence();
      final controller = JourneyController(
        repository: DemoJourneyRepository(
          store: SharedPreferencesJourneyStore(
            persistence: Future.value(persistence),
          ),
          now: () => DateTime.utc(2026, 8, 18, 3, 30),
        ),
        demoMode: true,
        onActionError: (value) => actionError = value,
      );
      final initial = await controller.bootstrap(
        appointment: appointmentFor('apt-retry'),
        patientId: 'patient-a',
      );
      persistence.setSucceeds = false;

      await controller.advance(JourneyEvent.staffScannedQr);

      expect(controller.state.requireValue, initial);
      expect(actionError, 'Không thể cập nhật hành trình. Vui lòng thử lại.');

      persistence.setSucceeds = true;
      await controller.advance(JourneyEvent.staffScannedQr);

      expect(controller.state.requireValue!.status, JourneyStatus.waiting);
      expect(actionError, isNull);
      controller.dispose();
    },
  );

  test(
    'late advance after logout restores persisted and in-memory snapshot',
    () async {
      final store = ControllableJourneyStore();
      final controller = JourneyController(
        repository: DemoJourneyRepository(
          store: store,
          now: () => DateTime.utc(2026, 8, 18, 3, 30),
        ),
        demoMode: true,
      );
      final initial = await controller.bootstrap(
        appointment: appointmentFor('apt-late-advance'),
        patientId: 'patient-a',
      );
      store.blockNextSave = true;

      final advance = controller.advance(JourneyEvent.staffScannedQr);
      await store.saveStarted.future;
      controller.invalidateAccountScope();
      store.releaseSave.complete();
      await advance;

      expect(controller.state.valueOrNull, isNull);
      expect(store.stored, initial);
      controller.dispose();
    },
  );

  test('late payment after account switch cannot publish or persist', () async {
    final store = ControllableJourneyStore();
    final controller = JourneyController(
      repository: DemoJourneyRepository(
        store: store,
        now: () => DateTime.utc(2026, 8, 18, 3, 30),
      ),
      demoMode: true,
    );
    await controller.bootstrap(
      appointment: appointmentFor('apt-late-payment'),
      patientId: 'patient-a',
    );
    for (final event in [
      JourneyEvent.staffScannedQr,
      JourneyEvent.doctorCalled,
      JourneyEvent.consultationStarted,
      JourneyEvent.laboratoryOrdered,
      JourneyEvent.paymentRequested,
    ]) {
      await controller.advance(event);
    }
    final initial = controller.state.requireValue!;
    store.blockNextSave = true;

    final payment = controller.acknowledgePayment(PaymentMethod.online);
    await store.saveStarted.future;
    controller.invalidateAccountScope();
    store.releaseSave.complete();
    await payment;

    expect(controller.state.valueOrNull, isNull);
    expect(store.stored, initial);
    controller.dispose();
  });

  test(
    'late notification read after logout cannot publish or persist',
    () async {
      final store = ControllableJourneyStore();
      final controller = JourneyController(
        repository: DemoJourneyRepository(
          store: store,
          now: () => DateTime.utc(2026, 8, 18, 3, 30),
        ),
        demoMode: true,
      );
      final initial = await controller.bootstrap(
        appointment: appointmentFor('apt-late-read'),
        patientId: 'patient-a',
      );
      store.blockNextSave = true;

      final read = controller.markNotificationRead(
        initial.notifications.first.id,
      );
      await store.saveStarted.future;
      controller.invalidateAccountScope();
      store.releaseSave.complete();
      await read;

      expect(controller.state.valueOrNull, isNull);
      expect(store.stored, initial);
      expect(store.stored!.notifications.first.isRead, isFalse);
      controller.dispose();
    },
  );

  test(
    'reset wins over an older pending mutation in memory and storage',
    () async {
      final store = ControllableJourneyStore();
      final controller = JourneyController(
        repository: DemoJourneyRepository(
          store: store,
          now: () => DateTime.utc(2026, 8, 18, 3, 30),
        ),
        demoMode: true,
      );
      await controller.bootstrap(
        appointment: appointmentFor('apt-reset-race'),
        patientId: 'patient-a',
      );
      store.blockNextSave = true;

      final advance = controller.advance(JourneyEvent.staffScannedQr);
      await store.saveStarted.future;
      await controller.resetCurrentJourney();
      store.releaseSave.complete();
      await advance;

      expect(controller.state.valueOrNull, isNull);
      expect(store.stored, isNull);
      controller.dispose();
    },
  );

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
