import 'dart:convert';

import 'package:shared_preferences/shared_preferences.dart';

import '../domain/journey_models.dart';
import 'journey_store.dart';

abstract interface class JourneyPersistenceAdapter {
  String? getString(String key);

  Future<bool> setString(String key, String value);

  Future<bool> remove(String key);
}

class SharedPreferencesPersistenceAdapter implements JourneyPersistenceAdapter {
  SharedPreferencesPersistenceAdapter(this._preferences);

  final SharedPreferences _preferences;

  @override
  String? getString(String key) => _preferences.getString(key);

  @override
  Future<bool> remove(String key) => _preferences.remove(key);

  @override
  Future<bool> setString(String key, String value) =>
      _preferences.setString(key, value);
}

class SharedPreferencesJourneyStore implements JourneyStore {
  SharedPreferencesJourneyStore({
    Future<SharedPreferences>? preferences,
    Future<JourneyPersistenceAdapter>? persistence,
  }) : assert(preferences == null || persistence == null),
       _persistence =
           persistence ??
           (preferences ?? SharedPreferences.getInstance())
               .then<JourneyPersistenceAdapter>(
                 SharedPreferencesPersistenceAdapter.new,
               );

  final Future<JourneyPersistenceAdapter> _persistence;

  @override
  Future<JourneyLoadResult> load(String patientId, String appointmentId) async {
    final key = journeyStorageKey(patientId, appointmentId);
    final persistence = await _persistence;
    final value = persistence.getString(key);
    if (value == null) return const JourneyLoadResult();

    try {
      final json = jsonDecode(value) as Map<String, dynamic>;
      return JourneyLoadResult(journey: PatientJourney.fromJson(json));
    } catch (error) {
      await _remove(persistence, key);
      return JourneyLoadResult(
        wasCorrupted: true,
        errorMessage: error.toString(),
      );
    }
  }

  @override
  Future<void> save(PatientJourney journey) async {
    final persistence = await _persistence;
    final key = journeyStorageKey(journey.patientId, journey.appointmentId);
    if (!await persistence.setString(key, jsonEncode(journey.toJson()))) {
      throw StateError('Could not save journey at $key.');
    }
  }

  @override
  Future<void> delete(String patientId, String appointmentId) async {
    final persistence = await _persistence;
    await _remove(persistence, journeyStorageKey(patientId, appointmentId));
  }

  Future<void> _remove(
    JourneyPersistenceAdapter persistence,
    String key,
  ) async {
    if (!await persistence.remove(key)) {
      throw StateError('Could not delete journey at $key.');
    }
  }
}
