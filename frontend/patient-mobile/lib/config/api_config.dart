/// API configuration for CareFlow Patient App.
///
/// Base URL defaults to the production API gateway.
/// Override with --dart-define=API_BASE_URL=... for local development.
class ApiConfig {
  static const String baseUrl = String.fromEnvironment(
    'API_BASE_URL',
    defaultValue: 'https://api.careflow-demo.online/api',
  );
  static const String wsUrl = String.fromEnvironment(
    'WS_BASE_URL',
    defaultValue: 'wss://api.careflow-demo.online/ws',
  );

  // Auth endpoints (Identity Service via Gateway)
  static const String authLogin = '/auth/login';
  static const String authRegister = '/auth/register';
  static const String authLogout = '/auth/logout';
  static const String authRefresh = '/auth/refresh';
  static const String authMe = '/auth/me';

  // Resource endpoints (via Gateway)
  static const String patients = '/patients';
  static const String appointments = '/appointments';
  static const String queues = '/queues';
  static const String emrRecords = '/emr/records';
  static const String prescriptions = '/prescriptions';
  static const String notifications = '/notifications';
}
