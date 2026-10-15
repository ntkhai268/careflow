/// A hospital service shown while making an appointment.
///
/// The catalogue is intentionally local until Hospital Directory/Appointment
/// exposes a versioned service-price contract.
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
