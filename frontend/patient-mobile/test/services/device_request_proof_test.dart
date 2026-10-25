import 'dart:convert';
import 'package:careflow_patient/services/device_request_proof.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:jose/jose.dart';

Uint8List toDer(List<int> signature) {
  List<int> integer(List<int> bytes) {
    while (bytes.length > 1 && bytes.first == 0) {
      bytes = bytes.sublist(1);
    }
    if (bytes.first & 0x80 != 0) bytes = [0, ...bytes];
    return [2, bytes.length, ...bytes];
  }

  final values = [
    ...integer(signature.sublist(0, 32)),
    ...integer(signature.sublist(32)),
  ];
  return Uint8List.fromList([0x30, values.length, ...values]);
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();
  final key = JsonWebKey.generate('ES256');
  final base = Uri.parse('https://example.test/api');
  final public = Map<String, dynamic>.from(key.toJson())
    ..remove('d')
    ..remove('key_ops')
    ..remove('use')
    ..remove('alg')
    ..remove('kid');
  setUp(() {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(HardwareRequestProof.channel, (call) async {
          if (call.method == 'publicKey') {
            return {'jwk': public, 'protection': 'secure_enclave'};
          }
          if (call.method == 'sign') {
            return toDer(
              key.sign((call.arguments as Map)['message'], algorithm: 'ES256'),
            );
          }
          throw MissingPluginException();
        });
  });
  tearDown(
    () => TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(HardwareRequestProof.channel, null),
  );
  test(
    'native DER signature forms a verifiable ES256 proof with only the public JWK',
    () async {
      final signer = await HardwareRequestProof.create(base);
      final proof = await signer.sign(
        'post',
        Uri.parse('$base/auth/me?ignored=yes'),
        accessToken: 'access',
      );
      final jws = JsonWebSignature.fromCompactSerialization(proof);
      final keys = JsonWebKeyStore()..addKey(JsonWebKey.fromJson(public));
      expect(await jws.verify(keys), isTrue);
      final header =
          jsonDecode(
                utf8.decode(
                  base64Url.decode(base64Url.normalize(proof.split('.')[0])),
                ),
              )
              as Map;
      expect((header['jwk'] as Map).containsKey('d'), isFalse);
      final claims = jsonDecode(utf8.decode(jws.unverifiedPayload.data)) as Map;
      expect(claims['htm'], 'POST');
      expect(claims['htu'], 'https://example.test/api/auth/me');
      expect(claims['ath'], isNotEmpty);
      expect(signer.protection, 'secure_enclave');
      expect(
        await signer.sign('GET', Uri.parse('$base/auth/me')),
        isNot(proof),
      );
    },
  );
  test('origin mismatch fails before native signing', () async {
    final signer = await HardwareRequestProof.create(base);
    await expectLater(
      signer.sign('GET', Uri.parse('https://elsewhere.test/api/auth/me')),
      throwsArgumentError,
    );
  });
  test('a native response containing a private key is rejected', () async {
    TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
        .setMockMethodCallHandler(
          HardwareRequestProof.channel,
          (_) async => {'jwk': key.toJson(), 'protection': 'invalid'},
        );
    await expectLater(HardwareRequestProof.create(base), throwsFormatException);
  });
  test(
    'malformed and negative DER integers cannot become a JOSE signature',
    () {
      for (final bytes in [
        <int>[],
        [0x30, 6, 2, 1, 0x80, 2, 1, 1],
        [0x30, 6, 2, 1, 1, 2, 1],
        [0x30, 7, 2, 1, 1, 2, 1, 1, 0],
      ]) {
        expect(
          () => ecdsaDerToJose(Uint8List.fromList(bytes)),
          throwsFormatException,
        );
      }
    },
  );
}
