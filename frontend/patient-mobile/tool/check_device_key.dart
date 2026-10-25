// Explicit physical-device diagnostic target. Never selected by the production main.dart.
// Uses a separate test namespace; never accesses the account's key or biometric vault.
import 'dart:convert';
import 'dart:io';
import 'package:path_provider/path_provider.dart';
import 'package:crypto/crypto.dart';
import 'package:flutter/material.dart';
import 'package:jose/jose.dart';
import 'package:careflow_patient/services/device_request_proof.dart';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  String message;
  try {
    final namespace = sha256
        .convert(utf8.encode('careflow-device-key-acceptance-v1'))
        .toString();
    final base = Uri.parse('https://device-key-check.invalid/api');
    final key = await HardwareRequestProof.create(
      base,
      testNamespace: namespace,
    );
    final proof = await key.sign(
      'POST',
      Uri.parse('$base/check'),
      accessToken: 'diagnostic-only',
    );
    final jws = JsonWebSignature.fromCompactSerialization(proof);
    if (!await jws.verify(
      JsonWebKeyStore()..addKey(JsonWebKey.fromJson(key.publicJwk)),
    )) {
      throw StateError('Signature verification failed');
    }
    final same = await HardwareRequestProof.create(
      base,
      testNamespace: namespace,
    );
    if (jsonEncode(key.publicJwk) != jsonEncode(same.publicJwk)) {
      throw StateError('Key not stable');
    }
    message =
        'CAREFLOW_DEVICE_KEY_CHECK PASS ${key.protection}; ES256 verified; stable public key; no account access';
  } catch (error) {
    message = 'CAREFLOW_DEVICE_KEY_CHECK FAIL ${error.runtimeType}';
  }
  // Only non-sensitive status reaches device logs.
  final directory = await getApplicationDocumentsDirectory();
  await File(
    '${directory.path}/careflow-device-key-check.txt',
  ).writeAsString(message, flush: true);
  debugPrint(message);
  runApp(
    MaterialApp(
      home: Scaffold(
        body: SafeArea(
          child: Center(
            child: Padding(
              padding: const EdgeInsets.all(24),
              child: Text(message),
            ),
          ),
        ),
      ),
    ),
  );
}
