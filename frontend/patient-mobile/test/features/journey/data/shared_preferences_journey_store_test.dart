import 'package:careflow_patient/features/journey/data/journey_store.dart';
import 'package:careflow_patient/features/journey/data/shared_preferences_journey_store.dart';
import 'package:careflow_patient/features/journey/domain/journey_models.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';

class FailingPersistence implements JourneyPersistenceAdapter {
  FailingPersistence({this.setSucceeds = true, this.removeSucceeds = true});

  final bool setSucceeds;
  final bool removeSucceeds;

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
    'keeps journeys with a shared appointment ID isolated by patient',
    () async {
      final store = SharedPreferencesJourneyStore();
      final first = journeyFor(patientId: 'patient-a', appointmentId: 'apt-1');
      final second = journeyFor(patientId: 'patient-b', appointmentId: 'apt-1');

      await store.save(first);
      await store.save(second);

      expect((await store.load('patient-a', 'apt-1')).journey, first);
      expect((await store.load('patient-b', 'apt-1')).journey, second);
    },
  );

  test('removes only the malformed patient journey from storage', () async {
    final intact = journeyFor(patientId: 'patient-b', appointmentId: 'apt-1');
    SharedPreferences.setMockInitialValues({
      journeyStorageKey('patient-a', 'apt-1'): '{not json',
    });
    final store = SharedPreferencesJourneyStore();
    await store.save(intact);
    final preferences = await SharedPreferences.getInstance();

    final corruptLoad = await store.load('patient-a', 'apt-1');

    expect(corruptLoad.journey, isNull);
    expect(corruptLoad.wasCorrupted, isTrue);
    expect(corruptLoad.errorMessage, isNotEmpty);
    expect(
      preferences.containsKey(journeyStorageKey('patient-a', 'apt-1')),
      isFalse,
    );
    expect((await store.load('patient-b', 'apt-1')).journey, intact);
  });

  test(
    'reports failed save and delete calls from the persistence adapter',
    () async {
      final journey = journeyFor(
        patientId: 'patient-a',
        appointmentId: 'apt-1',
      );
      final saveStore = SharedPreferencesJourneyStore(
        persistence: Future.value(FailingPersistence(setSucceeds: false)),
      );
      final deleteStore = SharedPreferencesJourneyStore(
        persistence: Future.value(FailingPersistence(removeSucceeds: false)),
      );

      await expectLater(saveStore.save(journey), throwsA(isA<StateError>()));
      await expectLater(
        deleteStore.delete('patient-a', 'apt-1'),
        throwsA(isA<StateError>()),
      );
    },
  );
}

PatientJourney journeyFor({
  required String patientId,
  required String appointmentId,
}) => PatientJourney(
  appointmentId: appointmentId,
  patientId: patientId,
  status: JourneyStatus.booked,
  laboratoryOrders: const [],
  timeline: const [],
  notifications: const [],
  updatedAt: DateTime.utc(2026, 8, 18, 3, 30),
);
