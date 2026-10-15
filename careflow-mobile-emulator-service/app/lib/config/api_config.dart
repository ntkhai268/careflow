/// API configuration for CareFlow Patient App.
///
/// Base URL defaults to the same-origin local emulator proxy.
/// Override with --dart-define=API_BASE_URL=... for local development.
class ApiConfig {
  static final Uri _origin =
      Uri.base.scheme == 'http' || Uri.base.scheme == 'https'
      ? Uri.parse(Uri.base.origin)
      : Uri.parse('http://localhost:3000');
  static const _apiOverride = String.fromEnvironment('API_BASE_URL');
  static const _wsOverride = String.fromEnvironment('WS_BASE_URL');
  static final String baseUrl = _apiOverride.isEmpty
      ? _origin.resolve('/api').toString()
      : _apiOverride;
  static final String wsUrl = _wsOverride.isEmpty
      ? _origin
            .replace(
              scheme: _origin.scheme == 'https' ? 'wss' : 'ws',
              path: '/ws',
            )
            .toString()
      : _wsOverride;

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
  static const String consultations = '/consultations';
  static const String labOrders = '/labs/orders';
  static const String emrRecords = '/emr/records';
  static const String prescriptions = '/prescriptions';
  static const String notifications = '/notifications';
}
