import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../../../config/theme.dart';
import '../../../platform/browser_bridge.dart';
import '../../../services/queue_service.dart';

/// Desktop input for the same QR/GPS payload as mobile. Ownership, QR expiry
/// and geofencing are validated by the backend without any bypass.
class HospitalQrCheckInScreen extends ConsumerStatefulWidget {
  const HospitalQrCheckInScreen({super.key, required this.appointmentId});
  final String appointmentId;
  @override
  ConsumerState<HospitalQrCheckInScreen> createState() => _CheckInState();
}

class _CheckInState extends ConsumerState<HospitalQrCheckInScreen> {
  final _form = GlobalKey<FormState>();
  final _token = TextEditingController();
  final _latitude = TextEditingController();
  final _longitude = TextEditingController();
  final _accuracy = TextEditingController(text: '10');
  bool _busy = false;
  String? _error;
  @override
  void dispose() {
    for (final field in [_token, _latitude, _longitude, _accuracy]) {
      field.dispose();
    }
    super.dispose();
  }

  Future<void> _position() async {
    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      final data = await browserCall('position') as Map;
      _latitude.text = '${data['latitude']}';
      _longitude.text = '${data['longitude']}';
      _accuracy.text = '${data['accuracy']}';
    } catch (error) {
      if (mounted) setState(() => _error = '$error');
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<void> _checkIn() async {
    if (!_form.currentState!.validate()) return;
    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      await ref
          .read(queueServiceProvider)
          .checkInAtHospital(
            appointmentId: widget.appointmentId,
            checkInQrToken: _token.text.trim(),
            latitude: double.parse(_latitude.text),
            longitude: double.parse(_longitude.text),
            accuracyMeters: double.parse(_accuracy.text),
          );
      if (mounted) context.pop(true);
    } catch (error) {
      if (mounted) setState(() => _error = '$error');
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Widget _number(
    String label,
    TextEditingController field,
    double min,
    double max,
  ) => Padding(
    padding: const EdgeInsets.only(bottom: 12),
    child: TextFormField(
      controller: field,
      keyboardType: const TextInputType.numberWithOptions(
        decimal: true,
        signed: true,
      ),
      decoration: InputDecoration(labelText: label),
      validator: (value) {
        final number = double.tryParse(value ?? '');
        return number == null ||
                !number.isFinite ||
                number < min ||
                number > max
            ? 'Nhập giá trị từ $min đến $max'
            : null;
      },
    ),
  );
  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(title: const Text('Check-in tại bệnh viện')),
    body: Form(
      key: _form,
      child: ListView(
        padding: const EdgeInsets.all(AppSpacing.base),
        children: [
          const Icon(Icons.qr_code_scanner, size: 64, color: AppColors.primary),
          const SizedBox(height: 16),
          const Text(
            'Check-in trên web giả lập',
            style: TextStyle(fontSize: 20, fontWeight: FontWeight.bold),
          ),
          const SizedBox(height: 8),
          const Text(
            'Dán nội dung QR đang hiển thị trên Hospital Web. Dùng vị trí trình duyệt hoặc nhập tọa độ kiểm thử. Máy chủ vẫn kiểm tra QR, thời hạn và phạm vi bệnh viện.',
          ),
          const SizedBox(height: 20),
          TextFormField(
            controller: _token,
            minLines: 2,
            maxLines: 4,
            decoration: const InputDecoration(
              labelText: 'Nội dung QR check-in',
            ),
            validator: (value) => value == null || value.trim().isEmpty
                ? 'Vui lòng nhập QR token'
                : null,
          ),
          const SizedBox(height: 16),
          OutlinedButton.icon(
            onPressed: _busy ? null : _position,
            icon: const Icon(Icons.my_location),
            label: const Text('Lấy vị trí trình duyệt'),
          ),
          const SizedBox(height: 16),
          _number('Vĩ độ (latitude)', _latitude, -90, 90),
          _number('Kinh độ (longitude)', _longitude, -180, 180),
          _number('Độ chính xác (m)', _accuracy, 0, 100000),
          if (_error != null)
            Padding(
              padding: const EdgeInsets.only(bottom: 16),
              child: Text(
                _error!,
                style: const TextStyle(color: AppColors.error),
              ),
            ),
          FilledButton.icon(
            onPressed: _busy ? null : _checkIn,
            icon: const Icon(Icons.check_circle_outline),
            label: Text(_busy ? 'Đang xử lý…' : 'Xác nhận check-in'),
          ),
        ],
      ),
    ),
  );
}
