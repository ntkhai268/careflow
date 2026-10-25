import 'package:flutter_test/flutter_test.dart';
import 'package:careflow_patient/config/device_config.dart';

void main() {
  test('device and Gateway scope isolate all local persistence', () {
    final first = DeviceConfig.namespaceFor(
      Uri.parse(
        'http://localhost:3000/mobile/?device=phone-01&scope=0123456789abcdef',
      ),
    );
    final second = DeviceConfig.namespaceFor(
      Uri.parse(
        'http://localhost:3000/mobile/?device=phone-02&scope=0123456789abcdef',
      ),
    );
    final otherBackend = DeviceConfig.namespaceFor(
      Uri.parse(
        'http://localhost:3000/mobile/?device=phone-01&scope=abcdef0123456789',
      ),
    );
    expect(first, 'careflow.0123456789abcdef.phone-01');
    expect(first, isNot(second));
    expect(first, isNot(otherBackend));
  });
  test('standalone mobile preserves the existing storage namespace', () {
    expect(
      DeviceConfig.namespaceFor(Uri.parse('http://localhost:3000/mobile/')),
      isNull,
    );
  });
  test('partial device URLs cannot reuse the standalone account', () {
    expect(
      () => DeviceConfig.namespaceFor(
        Uri.parse('http://localhost:3000/mobile/?device=phone-01'),
      ),
      throwsFormatException,
    );
  });
  test('rejects invalid namespaces rather than sharing another device scope', () {
    expect(
      () => DeviceConfig.namespaceFor(
        Uri.parse(
          'http://localhost:3000/mobile/?device=phone.01&scope=0123456789abcdef',
        ),
      ),
      throwsFormatException,
    );
    expect(
      () => DeviceConfig.namespaceFor(
        Uri.parse('http://localhost:3000/mobile/?device=phone-01&scope=bad'),
      ),
      throwsFormatException,
    );
  });
}
