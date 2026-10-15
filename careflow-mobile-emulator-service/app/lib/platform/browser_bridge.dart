import 'dart:convert';
import 'dart:typed_data';
import 'browser_bridge_stub.dart'
    if (dart.library.js_interop) 'browser_bridge_web.dart'
    as platform;

class BrowserFile {
  const BrowserFile(this.name, this.bytes);
  final String name;
  final Uint8List bytes;
}

Future<dynamic> browserCall(String method, [Map<String, dynamic>? data]) =>
    platform.call(method, data ?? {});

Future<List<BrowserFile>> pickBrowserFiles({bool imageOnly = false}) async {
  final result = await browserCall('files', {'imageOnly': imageOnly});
  return (result as List)
      .map(
        (dynamic file) => BrowserFile(
          file['name'] as String,
          base64Decode(file['base64'] as String),
        ),
      )
      .toList();
}

void listenBrowserNotifications(void Function() callback) =>
    platform.listenNotifications(callback);

void listenBrowserControls(void Function(String) callback) =>
    platform.listenControls(callback);
