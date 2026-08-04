/// A hospital service that can be selected when making an appointment.
///
/// The catalogue intentionally contains one option for the current MVP. The
/// stable code and price make it possible to replace the local catalogue with
/// Hospital Directory/Appointment data later without changing the booking UI.
class AppointmentServiceOption {
  const AppointmentServiceOption({
    required this.code,
    required this.name,
    required this.description,
    required this.price,
    required this.estimatedMinutes,
  });

  final String code;
  final String name;
  final String description;
  final int price;
  final int estimatedMinutes;

  static const generalConsultation = AppointmentServiceOption(
    code: 'GENERAL_CONSULTATION',
    name: 'Khám thường',
    description: 'Khám ban đầu với bác sĩ chuyên khoa',
    price: 150000,
    estimatedMinutes: 15,
  );

  static const catalog = <AppointmentServiceOption>[generalConsultation];
}
