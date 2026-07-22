/// API configuration for CareFlow Patient App.
///
/// Base URLs point to localhost by default.
/// Change to Mac Mini IP when deploying to home server.
class ApiConfig {
  // TODO: Tối về nhà sửa thành IP Mac Mini (ví dụ: http://192.168.x.x:8080/api)
  static const String baseUrl = 'http://localhost:8080/api';
  static const String wsUrl = 'http://localhost:8080/ws';

  // Direct service URLs (bypass Gateway for dev)
  // TODO: In production, all traffic goes through API Gateway
  static const String patientServiceUrl = 'http://localhost:8082';
  static const String appointmentServiceUrl = 'http://localhost:8083';

  // Service-specific endpoints
  static const String authLogin = '/auth/login';
  static const String authRegister = '/auth/register';
  static const String patients = '/patients';
  static const String appointments = '/appointments';
  static const String queues = '/queues';
  static const String emrRecords = '/emr/records';
  static const String prescriptions = '/prescriptions';
}

