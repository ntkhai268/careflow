import 'package:careflow_patient/utils/appointment_slot.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  final now = DateTime(2026, 7, 31, 14, 56);

  test('rejects slots whose start time has passed today', () {
    expect(
      isAppointmentSlotAvailable(
        selectedDate: DateTime(2026, 7, 31),
        slot: '07:30-08:00',
        now: now,
      ),
      isFalse,
    );
    expect(
      isAppointmentSlotAvailable(
        selectedDate: DateTime(2026, 7, 31),
        slot: '14:30-15:00',
        now: now,
      ),
      isFalse,
    );
  });

  test('accepts a future slot today and every valid slot on a future date', () {
    expect(
      isAppointmentSlotAvailable(
        selectedDate: DateTime(2026, 7, 31),
        slot: '15:00-15:30',
        now: now,
      ),
      isTrue,
    );
    expect(
      isAppointmentSlotAvailable(
        selectedDate: DateTime(2026, 8, 1),
        slot: '07:30-08:00',
        now: now,
      ),
      isTrue,
    );
  });

  test('fails closed for malformed time slots', () {
    expect(
      isAppointmentSlotAvailable(
        selectedDate: DateTime(2026, 8, 1),
        slot: 'invalid',
        now: now,
      ),
      isFalse,
    );
  });
}
