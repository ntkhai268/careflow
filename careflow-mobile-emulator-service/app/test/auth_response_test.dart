import 'package:careflow_patient/services/auth_service.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  test('parses Identity Service login contract', () {
    final response = AuthResponse.fromJson({
      'accessToken': 'access-token',
      'refreshToken': 'refresh-token',
      'tokenType': 'Bearer',
      'user': {
        'id': '8cab3464-59ea-4875-b2b8-cd008b9ea994',
        'username': 'patient01',
        'email': 'patient01@example.com',
      },
    });

    expect(response.token, 'access-token');
    expect(response.refreshToken, 'refresh-token');
    expect(response.userId, '8cab3464-59ea-4875-b2b8-cd008b9ea994');
    expect(response.fullName, 'patient01');
  });

  test('rejects legacy mock response shape', () {
    expect(
      () => AuthResponse.fromJson({'token': 'legacy-token'}),
      throwsFormatException,
    );
  });
}
