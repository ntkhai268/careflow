import 'dart:convert';

import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../models/appointment_payment.dart';

abstract interface class AppointmentPaymentPersistenceAdapter {
  String? getString(String key);

  Future<bool> setString(String key, String value);

  Future<bool> remove(String key);
}

class SharedPreferencesAppointmentPaymentAdapter
    implements AppointmentPaymentPersistenceAdapter {
  SharedPreferencesAppointmentPaymentAdapter(this._preferences);

  final SharedPreferences _preferences;

  @override
  String? getString(String key) => _preferences.getString(key);

  @override
  Future<bool> setString(String key, String value) =>
      _preferences.setString(key, value);

  @override
  Future<bool> remove(String key) => _preferences.remove(key);
}

abstract interface class AppointmentPaymentStore {
  Future<AppointmentPaymentReceipt?> load(String appointmentId);

  Future<void> save(AppointmentPaymentReceipt receipt);

  Future<void> delete(String appointmentId);
}

class SharedPreferencesAppointmentPaymentStore
    implements AppointmentPaymentStore {
  SharedPreferencesAppointmentPaymentStore({
    Future<SharedPreferences>? preferences,
    Future<AppointmentPaymentPersistenceAdapter>? persistence,
  }) : assert(preferences == null || persistence == null),
       _persistence =
           persistence ??
           (preferences ?? SharedPreferences.getInstance())
               .then<AppointmentPaymentPersistenceAdapter>(
                 SharedPreferencesAppointmentPaymentAdapter.new,
               );

  final Future<AppointmentPaymentPersistenceAdapter> _persistence;

  static String storageKey(String appointmentId) =>
      'careflow.appointment-payment.$appointmentId';

  @override
  Future<AppointmentPaymentReceipt?> load(String appointmentId) async {
    final persistence = await _persistence;
    final raw = persistence.getString(storageKey(appointmentId));
    if (raw == null) return null;

    try {
      return AppointmentPaymentReceipt.fromJson(
        jsonDecode(raw) as Map<String, dynamic>,
      );
    } catch (_) {
      await persistence.remove(storageKey(appointmentId));
      return null;
    }
  }

  @override
  Future<void> save(AppointmentPaymentReceipt receipt) async {
    final persistence = await _persistence;
    final saved = await persistence.setString(
      storageKey(receipt.appointmentId),
      jsonEncode(receipt.toJson()),
    );
    if (!saved) throw StateError('Could not save appointment payment receipt.');
  }

  @override
  Future<void> delete(String appointmentId) async {
    final persistence = await _persistence;
    await persistence.remove(storageKey(appointmentId));
  }
}

final appointmentPaymentStoreProvider = Provider<AppointmentPaymentStore>(
  (ref) => SharedPreferencesAppointmentPaymentStore(),
);
