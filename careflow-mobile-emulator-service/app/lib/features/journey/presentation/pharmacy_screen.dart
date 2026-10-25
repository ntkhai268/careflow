import 'settlement_screen.dart';

/// Backwards-compatible route name for clients that still open `/pharmacy`.
/// The screen now renders the visit-level settlement before dispensing.
class PharmacyScreen extends SettlementScreen {
  const PharmacyScreen({super.key, required super.appointmentId});
}
