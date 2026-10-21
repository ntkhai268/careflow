import 'dart:convert';
import 'package:crypto/crypto.dart';
import 'package:flutter/services.dart';
import 'package:jose/jose.dart';
import 'package:uuid/uuid.dart';
import 'dpop_proof.dart';

abstract class DeviceRequestProof {
  Future<String> sign(String method, Uri uri, {String? accessToken});
}

class SoftwareRequestProof implements DeviceRequestProof {
  SoftwareRequestProof(this.proof);
  final DpopProof proof;
  @override
  Future<String> sign(String method, Uri uri, {String? accessToken}) async =>
      proof.sign(method, uri, accessToken: accessToken);
}

class HardwareRequestProof implements DeviceRequestProof {
  HardwareRequestProof._(
    this.namespace,
    this.publicJwk,
    this.protection,
    this.apiBase,
    this.proofOrigin,
  );
  static const channel = MethodChannel('careflow/device-key');
  final String namespace, protection;
  final Map<String, dynamic> publicJwk;
  final Uri apiBase, proofOrigin;

  static Future<HardwareRequestProof> create(
    Uri apiBase, {
    Uri? proofOrigin,
    String? testNamespace,
  }) async {
    final namespace =
        testNamespace ??
        sha256.convert(utf8.encode(apiBase.toString())).toString();
    final result = await channel.invokeMapMethod<String, dynamic>('publicKey', {
      'namespace': namespace,
    });
    if (result == null) throw PlatformException(code: 'KEY_UNAVAILABLE');
    final key = Map<String, dynamic>.from(result['jwk'] as Map);
    if (key.keys.any((k) => !const {'kty', 'crv', 'x', 'y'}.contains(k)) ||
        key['kty'] != 'EC' ||
        key['crv'] != 'P-256') {
      throw const FormatException('Invalid public device key');
    }
    JsonWebKey.fromJson(key); // Validate public coordinates before signing.
    return HardwareRequestProof._(
      namespace,
      key,
      result['protection'] as String,
      apiBase,
      proofOrigin ?? apiBase,
    );
  }

  @override
  Future<String> sign(String method, Uri uri, {String? accessToken}) async {
    if (uri.origin != apiBase.origin) {
      throw ArgumentError('Cannot sign a request for another API origin');
    }
    String encode(Object value) =>
        base64Url.encode(utf8.encode(jsonEncode(value))).replaceAll('=', '');
    final header = encode({
      'alg': 'ES256',
      'typ': 'dpop+jwt',
      'jwk': publicJwk,
    });
    final payload = encode({
      'jti': const Uuid().v4(),
      'htm': method.toUpperCase(),
      'htu': '${proofOrigin.origin}${uri.path}',
      'iat': DateTime.now().millisecondsSinceEpoch ~/ 1000,
      if (accessToken != null && accessToken.isNotEmpty)
        'ath': base64Url
            .encode(sha256.convert(utf8.encode(accessToken)).bytes)
            .replaceAll('=', ''),
    });
    final signingInput = '$header.$payload';
    final signature = await channel.invokeMethod<Uint8List>('sign', {
      'namespace': namespace,
      'message': Uint8List.fromList(utf8.encode(signingInput)),
    });
    if (signature == null) throw PlatformException(code: 'SIGN_FAILED');
    return '$signingInput.${base64Url.encode(ecdsaDerToJose(signature)).replaceAll('=', '')}';
  }
}

/// P-256 DER signature → fixed-width JOSE R || S. Neither side exposes a private key.
Uint8List ecdsaDerToJose(Uint8List der) {
  if (der.length < 8 ||
      der.length > 72 ||
      der[0] != 0x30 ||
      der[1] != der.length - 2) {
    throw const FormatException('Invalid ECDSA signature');
  }
  var offset = 2;
  final out = Uint8List(64);
  for (var part = 0; part < 2; part++) {
    if (offset + 2 > der.length || der[offset++] != 2) {
      throw const FormatException('Invalid ECDSA integer');
    }
    final length = der[offset++];
    if (length < 1 ||
        length > 33 ||
        offset + length > der.length ||
        (der[offset] & 0x80) != 0) {
      throw const FormatException('Invalid ECDSA integer');
    }
    var start = offset;
    if (length > 1 && der[start] == 0) start++;
    final width = offset + length - start;
    if (width > 32) throw const FormatException('ECDSA integer too large');
    out.setRange(
      part * 32 + 32 - width,
      part * 32 + 32,
      der.sublist(start, offset + length),
    );
    offset += length;
  }
  if (offset != der.length) throw const FormatException('Trailing ECDSA bytes');
  return out;
}
