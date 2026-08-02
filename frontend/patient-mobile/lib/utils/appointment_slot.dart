DateTime? appointmentSlotStart(DateTime date, String slot) {
  final match = RegExp(r'^(\d{2}):(\d{2})').firstMatch(slot.trim());
  if (match == null) return null;

  final hour = int.tryParse(match.group(1)!);
  final minute = int.tryParse(match.group(2)!);
  if (hour == null ||
      minute == null ||
      hour < 0 ||
      hour > 23 ||
      minute < 0 ||
      minute > 59) {
    return null;
  }
  return DateTime(date.year, date.month, date.day, hour, minute);
}

bool isAppointmentSlotAvailable({
  required DateTime selectedDate,
  required String slot,
  required DateTime now,
}) {
  final start = appointmentSlotStart(selectedDate, slot);
  return start != null && start.isAfter(now);
}
