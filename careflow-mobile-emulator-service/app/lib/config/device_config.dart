/// Each Flutter iframe owns its preferences and encrypted tokens. The scope
/// changes with the selected Gateway so sessions cannot cross environments.
class DeviceConfig {
  static String? namespaceFor(Uri uri) {
    final device = uri.queryParameters['device'];
    final scope = uri.queryParameters['scope'];
    if (device == null && scope == null) return null;
    if (device == null || scope == null) {
      throw const FormatException('Thiếu định danh thiết bị hoặc môi trường.');
    }
    if (!RegExp(r'^[a-z0-9-]{1,64}$').hasMatch(device) ||
        !RegExp(r'^[a-f0-9]{16}$').hasMatch(scope)) {
      throw const FormatException('Thiết bị giả lập không hợp lệ.');
    }
    return 'careflow.$scope.$device';
  }

  static final namespace = namespaceFor(Uri.base);
  static final deviceId = Uri.base.queryParameters['device'];
}
