import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'api_service.dart';

class LoginDevice {
  LoginDevice.fromJson(Map<String, dynamic> json)
    : id = json['id'] as String,
      createdAt = DateTime.parse(json['created_at'] as String),
      expiresAt = DateTime.parse(json['expires_at'] as String),
      lastUsedAt = DateTime.tryParse(json['last_used_at']?.toString() ?? '');
  final String id;
  final DateTime createdAt, expiresAt;
  final DateTime? lastUsedAt;
}

class DeviceManagementService {
  DeviceManagementService(this.api);
  final ApiService api;
  Future<List<LoginDevice>> list() async {
    final response = await api.get('/auth/devices');
    return (response.data['data'] as List)
        .map((row) => LoginDevice.fromJson(Map<String, dynamic>.from(row)))
        .toList();
  }

  Future<void> revoke(String id) =>
      api.post('/auth/devices/$id/revoke', data: {}).then((_) {});
}

final deviceManagementServiceProvider = Provider(
  (ref) => DeviceManagementService(ref.watch(apiServiceProvider)),
);
