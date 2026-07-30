import 'dart:convert';

import 'package:shared_preferences/shared_preferences.dart';

import '../domain/journey_models.dart';
import 'journey_store.dart';

class SharedPreferencesJourneyStore implements JourneyStore {
  SharedPreferencesJourneyStore({Future<SharedPreferences>? preferences})
    : _preferences = preferences ?? SharedPreferences.getInstance();

  final Future<SharedPreferences> _preferences;

  @override
  Future<JourneyLoadResult> load(String patientId, String appointmentId) async {
    final key = journeyStorageKey(patientId, appointmentId);
    final preferences = await _preferences;
    final value = preferences.getString(key);
    if (value == null) return const JourneyLoadResult();

    try {
      final json = jsonDecode(value) as Map<String, dynamic>;
      return JourneyLoadResult(journey: PatientJourney.fromJson(json));
    } catch (error) {
      await preferences.remove(key);
      return JourneyLoadResult(
        wasCorrupted: true,
        errorMessage: error.toString(),
      );
    }
  }

  @override
  Future<void> save(PatientJourney journey) async {
    final preferences = await _preferences;
    await preferences.setString(
      journeyStorageKey(journey.patientId, journey.appointmentId),
      jsonEncode(journey.toJson()),
    );
  }

  @override
  Future<void> delete(String patientId, String appointmentId) async {
    final preferences = await _preferences;
    await preferences.remove(journeyStorageKey(patientId, appointmentId));
  }
}
