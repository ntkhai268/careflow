import 'dart:async';
import 'package:careflow_patient/providers/auth_provider.dart';
import 'package:careflow_patient/services/api_service.dart';
import 'package:careflow_patient/services/auth_service.dart';
import 'package:flutter_test/flutter_test.dart';

class SlowSessionClear extends AuthService {
  SlowSessionClear() : super(ApiService(), useMock: true);
  final cleared = Completer<void>();
  @override
  Future<void> lockSession() => cleared.future;
}

class AuthenticatedSession extends AuthNotifier {
  AuthenticatedSession(super.service) {
    state = const AuthState(status: AuthStatus.authenticated, userId: 'A');
  }
}

void main() {
  test(
    'interactive lock clears account scope before asynchronous credential cleanup',
    () async {
      final service = SlowSessionClear(), auth = AuthenticatedSession(service);
      final locked = auth.lockSession();
      expect(auth.state.status, AuthStatus.unauthenticated);
      expect(auth.state.userId, isNull);
      service.cleared.complete();
      await locked;
      expect(auth.state.status, AuthStatus.unauthenticated);
      auth.dispose();
    },
  );
}
