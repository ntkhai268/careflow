import 'dart:convert';

import 'package:careflow_patient/services/dpop_proof.dart';
import 'package:crypto/crypto.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:jose/jose.dart';

Map<String, dynamic> decodePart(String proof, int part) =>
    jsonDecode(
          utf8.decode(
            base64Url.decode(base64Url.normalize(proof.split('.')[part])),
          ),
        )
        as Map<String, dynamic>;

void main() {
  final key = JsonWebKey.generate('ES256');
  final api = Uri.parse('https://device.test/api');
  final signer = DpopProof(
    key,
    apiBase: api,
    proofOrigin: Uri.parse('https://gateway.test'),
  );

  test('anonymous proof verifies with its public P-256 key only', () async {
    final proof = signer.sign('post', api.resolve('/api/auth/login'));
    final header = decodePart(proof, 0);
    final claims = decodePart(proof, 1);
    final publicKey = Map<String, dynamic>.from(header['jwk'] as Map);
    expect(header['typ'], 'dpop+jwt');
    expect(header['alg'], 'ES256');
    expect(publicKey.containsKey('d'), isFalse);
    expect(publicKey['crv'], 'P-256');
    expect(claims['htm'], 'POST');
    expect(claims['htu'], 'https://gateway.test/api/auth/login');
    expect(claims.containsKey('ath'), isFalse);
    final store = JsonWebKeyStore()..addKey(JsonWebKey.fromJson(publicKey));
    expect(
      await JsonWebSignature.fromCompactSerialization(proof).verify(store),
      isTrue,
    );
  });

  test('resource proof binds the access token and excludes query/fragment', () {
    final uri = api.resolve('/api/patients?page=1#details');
    final first = signer.sign('GET', uri, accessToken: 'test-token');
    final second = signer.sign('GET', uri, accessToken: 'test-token');
    final claims = decodePart(first, 1);
    expect(claims['htu'], 'https://gateway.test/api/patients');
    expect(
      claims['ath'],
      base64Url
          .encode(sha256.convert(utf8.encode('test-token')).bytes)
          .replaceAll('=', ''),
    );
    expect(claims['jti'], isNot(decodePart(second, 1)['jti']));
  });

  test('refuses to sign requests for another API origin', () {
    expect(
      () => signer.sign('GET', Uri.parse('https://other.test/api/patients')),
      throwsArgumentError,
    );
  });
}
