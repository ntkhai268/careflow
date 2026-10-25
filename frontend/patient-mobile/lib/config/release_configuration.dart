import 'package:flutter/foundation.dart';

void validateReleaseConfiguration({
  bool release = kReleaseMode,
  bool demo = const bool.fromEnvironment('DEMO_MODE'),
  bool mockAuth = const bool.fromEnvironment('USE_MOCK_AUTH'),
}) {
  if (release && (demo || mockAuth)) {
    throw StateError(
      'Release builds must use the real backend and authentication.',
    );
  }
}
