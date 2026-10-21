import 'dart:convert';

import 'package:crypto/crypto.dart';
import 'package:jose/jose.dart';
import 'package:uuid/uuid.dart';

/// Signs requests with the same device key used to bind the login session.
class DpopProof {
  final JsonWebKey _key;
  final String _apiOrigin;
  final String _proofOrigin;

  DpopProof(this._key, {required Uri apiBase, Uri? proofOrigin})
    : _apiOrigin = apiBase.origin,
      _proofOrigin = (proofOrigin ?? apiBase).origin;

  String sign(String method, Uri uri, {String? accessToken}) {
    if (uri.origin != _apiOrigin) {
      throw ArgumentError('Cannot sign a request for another API origin');
    }
    final key = _key.toJson();
    final builder = JsonWebSignatureBuilder()
      ..jsonContent = {
        'jti': const Uuid().v4(),
        'htm': method.toUpperCase(),
        'htu': '$_proofOrigin${uri.path}',
        'iat': DateTime.now().millisecondsSinceEpoch ~/ 1000,
        if (accessToken != null && accessToken.isNotEmpty)
          'ath': base64Url
              .encode(sha256.convert(utf8.encode(accessToken)).bytes)
              .replaceAll('=', ''),
      }
      ..setProtectedHeader('typ', 'dpop+jwt')
      ..setProtectedHeader('jwk', {
        for (final name in ['kty', 'crv', 'x', 'y']) name: key[name],
      })
      ..addRecipient(_key, algorithm: 'ES256');
    return builder.build().toCompactSerialization();
  }
}
