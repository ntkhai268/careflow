import 'dart:io';

import 'package:dio/dio.dart';
import 'package:dio/io.dart';
import 'package:flutter/services.dart';

/// A local build may bundle its test CA without disabling TLS verification.
Future<void> configureNativeTransport(Dio client, Dio refreshClient) async {
  const asset = String.fromEnvironment('LOCAL_TEST_CA_ASSET');
  if (asset.isEmpty) return;
  final data = await rootBundle.load(asset);
  final context = SecurityContext(withTrustedRoots: true)
    ..setTrustedCertificatesBytes(
      data.buffer.asUint8List(data.offsetInBytes, data.lengthInBytes),
    );
  for (final dio in [client, refreshClient]) {
    dio.httpClientAdapter = IOHttpClientAdapter(
      createHttpClient: () => HttpClient(context: context),
    );
  }
}
