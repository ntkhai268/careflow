import 'dart:convert';

import 'package:shared_preferences/shared_preferences.dart';

import '../models/appointment_payment.dart';

/// Local adapter for appointment payment receipts.
///
/// The Appointment Service does not expose a payment contract yet. Keeping
/// this persistence behind a tiny adapter makes the UX testable now and gives
/// us one seam to replace when the payment endpoint is available.
class AppointmentPaymentStore {
  AppointmentPaymentStore({Future<SharedPreferences>? preferences})
    : _preferences = preferences ?? SharedPreferences.getInstance();

  final Future<SharedPreferences> _preferences;

  Future<AppointmentPaymentReceipt?> load(String appointmentId) async {
    final preferences = await _preferences;
    final raw = preferences.getString(_key(appointmentId));
    if (raw == null) return null;
    try {
      return AppointmentPaymentReceipt.fromJson(
        jsonDecode(raw) as Map<String, dynamic>,
      );
    } catch (_) {
      await preferences.remove(_key(appointmentId));
      return null;
    }
  }

  Future<void> save(AppointmentPaymentReceipt receipt) async {
    final preferences = await _preferences;
    final saved = await preferences.setString(
      _key(receipt.appointmentId),
      jsonEncode(receipt.toJson()),
    );
    if (!saved) {
      throw StateError('Could not save appointment payment receipt.');
    }
  }

  Future<void> delete(String appointmentId) async {
    final preferences = await _preferences;
    await preferences.remove(_key(appointmentId));
  }

  String _key(String appointmentId) =>
      'careflow.appointment-payment.$appointmentId';
}
