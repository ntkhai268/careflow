import 'dart:io';
import 'package:flutter/services.dart';
import 'notification_socket_types.dart';
export 'notification_socket_types.dart';

Future<NotificationSocket> connectNotificationSocket(Uri uri) async {
  final context = SecurityContext(withTrustedRoots: true);
  const asset = String.fromEnvironment('LOCAL_TEST_CA_ASSET');
  if (asset.isNotEmpty) {
    final bytes = await rootBundle.load(asset);
    context.setTrustedCertificatesBytes(
      bytes.buffer.asUint8List(bytes.offsetInBytes, bytes.lengthInBytes),
    );
  }
  final client = HttpClient(context: context)
    ..connectionTimeout = const Duration(seconds: 10);
  try {
    final socket = await WebSocket.connect(
      uri.toString(),
      customClient: client,
    ).timeout(const Duration(seconds: 12));
    socket.pingInterval = const Duration(seconds: 20);
    return _Socket(socket, client);
  } catch (_) {
    client.close(force: true);
    rethrow;
  }
}

class _Socket implements NotificationSocket {
  _Socket(this.socket, this.client);
  final WebSocket socket;
  final HttpClient client;
  @override
  Stream<dynamic> get messages => socket;
  @override
  void send(String frame) => socket.add(frame);
  @override
  Future<void> close() async {
    await socket.close();
    client.close(force: true);
  }
}
