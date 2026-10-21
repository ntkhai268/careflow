import 'package:careflow_patient/config/release_configuration.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  test('release rejects journey demo and mock authentication flags', () {
    expect(
      () => validateReleaseConfiguration(release: true, demo: true),
      throwsStateError,
    );
    expect(
      () => validateReleaseConfiguration(release: true, mockAuth: true),
      throwsStateError,
    );
    expect(() => validateReleaseConfiguration(release: true), returnsNormally);
    expect(
      () => validateReleaseConfiguration(
        release: false,
        demo: true,
        mockAuth: true,
      ),
      returnsNormally,
    );
  });
}
