import 'package:careflow_patient/features/journey/application/journey_controller.dart';
import 'package:careflow_patient/features/journey/application/journey_providers.dart';
import 'package:careflow_patient/features/journey/data/demo_journey_repository.dart';
import 'package:careflow_patient/features/journey/data/journey_repository.dart';
import 'package:careflow_patient/features/journey/data/shared_preferences_journey_store.dart';
import 'package:careflow_patient/features/journey/domain/journey_models.dart';
import 'package:careflow_patient/features/journey/domain/journey_transition.dart';
import 'package:careflow_patient/models/appointment.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';

class ResetFailureRepository implements JourneyRepository {
  ResetFailureRepository(this._delegate);

  final JourneyRepository _delegate;

  @override
  Future<PatientJourney> acknowledgePayment(
    PatientJourney journey,
    PaymentMethod method,
  ) => _delegate.acknowledgePayment(journey, method);

  @override
  Future<PatientJourney> advance(PatientJourney journey, JourneyEvent event) =>
      _delegate.advance(journey, event);

  @override
  Future<PatientJourney> bootstrap({
    required Appointment appointment,
    required String patientId,
  }) => _delegate.bootstrap(appointment: appointment, patientId: patientId);

  @override
  Future<PatientJourney> markNotificationRead(
    PatientJourney journey,
    String notificationId,
  ) => _delegate.markNotificationRead(journey, notificationId);

  @override
  Future<void> reset(PatientJourney journey) =>
      Future<void>.error(StateError('storage unavailable'));
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
      final initial = await controller.bootstrap(
        appointment: appointmentFor('apt-1'),
        patientId: 'patient-a',
      );

      await controller.advance(JourneyEvent.staffScannedQr);

      expect(controller.state.value, initial);
      expect(actionError, 'Tính năng đang chờ backend triển khai');
      controller.dispose();
    },
  );

  test('converts reset persistence failures into AsyncError', () async {
    final controller = JourneyController(
      repository: ResetFailureRepository(buildRepository()),
      demoMode: true,
    );
    await controller.bootstrap(
      appointment: appointmentFor('apt-1'),
      patientId: 'patient-a',
    );

    await controller.resetCurrentJourney();

    expect(controller.state.hasError, isTrue);
    expect(controller.state.error, isA<StateError>());
    controller.dispose();
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
