import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'api_service.dart';

class ProfileLink {
  final String id, patientId, patientName, roomId, status;
  final String? dateOfBirth;
  const ProfileLink({
    required this.id,
    required this.patientId,
    required this.patientName,
    required this.roomId,
    required this.status,
    this.dateOfBirth,
  });
  factory ProfileLink.fromJson(Map<String, dynamic> json) => ProfileLink(
    id: json['id'] as String,
    patientId: json['patientId'] as String,
    patientName: json['patientName'] as String,
    roomId: json['roomId'] as String,
    status: json['status'] as String,
    dateOfBirth: json['dateOfBirth'] as String?,
  );
}

class ProfileLinkService {
  final ApiService api;
  ProfileLinkService(this.api);
  static const path = '/patients/profile-links';
  Future<DateTime> authorize(String requestId, String token) async {
    final response = await api.post(
      '$path/authorization',
      data: {'requestId': requestId, 'token': token},
    );
    return DateTime.parse(response.data['data']['expiresAt'] as String);
  }

  Future<List<ProfileLink>> mine() async {
    final response = await api.get('$path/mine');
    return (response.data['data'] as List)
        .map(
          (item) =>
              ProfileLink.fromJson(Map<String, dynamic>.from(item as Map)),
        )
        .toList();
  }

  Future<ProfileLink> decide(String id, bool accepted) async {
    final response = await api.post(
      '$path/$id/decision',
      data: {'accepted': accepted},
    );
    return ProfileLink.fromJson(
      Map<String, dynamic>.from(response.data['data'] as Map),
    );
  }

  Future<void> revoke(String id) async {
    await api.post('$path/$id/revoke', data: {});
  }
}

final profileLinkServiceProvider = Provider<ProfileLinkService>(
  (ref) => ProfileLinkService(ref.watch(apiServiceProvider)),
);
