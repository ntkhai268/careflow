import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../config/theme.dart';
import '../../platform/browser_bridge.dart';
import '../../services/api_service.dart';
import '../../utils/api_error_message.dart';

/// Extra entry point for member A's existing Identity multipart endpoint.
class EkycScreen extends ConsumerStatefulWidget {
  const EkycScreen({super.key});
  @override
  ConsumerState<EkycScreen> createState() => _EkycState();
}

class _EkycState extends ConsumerState<EkycScreen> {
  BrowserFile? _file;
  bool _busy = false;
  String? _error;
  Map<String, dynamic>? _result;
  Future<void> _pick() async {
    try {
      final files = await pickBrowserFiles(imageOnly: true);
      if (mounted && files.isNotEmpty) {
        setState(() {
          _file = files.first;
          _result = null;
          _error = null;
        });
      }
    } catch (error) {
      if (mounted) setState(() => _error = '$error');
    }
  }

  Future<void> _verify() async {
    if (_file == null) return;
    setState(() {
      _busy = true;
      _error = null;
      _result = null;
    });
    try {
      final image = _file!;
      final response = await ref
          .read(apiServiceProvider)
          .post(
            '/auth/ekyc',
            data: FormData.fromMap({
              'image': MultipartFile.fromBytes(
                image.bytes,
                filename: image.name,
                contentType: DioMediaType.parse(
                  image.name.toLowerCase().endsWith('.png')
                      ? 'image/png'
                      : 'image/jpeg',
                ),
              ),
            }),
          );
      if (mounted) {
        setState(
          () =>
              _result = Map<String, dynamic>.from(response.data['data'] as Map),
        );
      }
    } catch (error) {
      if (mounted) {
        setState(
          () => _error = ApiErrorMessage.from(
            error,
            fallback: 'Không xác minh được ảnh. Vui lòng thử lại.',
          ),
        );
      }
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(title: const Text('Xác minh eKYC')),
    body: ListView(
      padding: const EdgeInsets.all(20),
      children: [
        const Icon(
          Icons.verified_user_outlined,
          size: 64,
          color: AppColors.primary,
        ),
        const SizedBox(height: 20),
        const Text(
          'Xác minh định danh',
          style: TextStyle(fontSize: 22, fontWeight: FontWeight.bold),
        ),
        const SizedBox(height: 8),
        const Text(
          'Gửi ảnh PNG/JPG đến Identity Service qua Gateway. Nếu backend trả mock=true, kết quả sẽ được đánh dấu là mô phỏng.',
        ),
        const SizedBox(height: 20),
        OutlinedButton.icon(
          onPressed: _busy ? null : _pick,
          icon: const Icon(Icons.upload_file),
          label: const Text('Chọn ảnh giấy tờ'),
        ),
        if (_file != null) ...[
          const SizedBox(height: 12),
          ClipRRect(
            borderRadius: BorderRadius.circular(12),
            child: Image.memory(
              _file!.bytes,
              height: 190,
              fit: BoxFit.contain,
              errorBuilder: (_, _, _) =>
                  const Text('Không xem được ảnh. Chọn tệp PNG/JPG hợp lệ.'),
            ),
          ),
          const SizedBox(height: 8),
          Text(_file!.name),
        ],
        const SizedBox(height: 20),
        FilledButton(
          onPressed: _busy || _file == null ? null : _verify,
          child: Text(_busy ? 'Đang xác minh…' : 'Gửi xác minh'),
        ),
        if (_error != null)
          Padding(
            padding: const EdgeInsets.only(top: 16),
            child: Text(
              _error!,
              style: const TextStyle(color: AppColors.error),
            ),
          ),
        if (_result != null) ...[
          const SizedBox(height: 20),
          Card(
            child: Padding(
              padding: const EdgeInsets.all(16),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    _result!['mock'] == true
                        ? 'Kết quả mô phỏng từ backend'
                        : 'Kết quả từ Identity Service',
                    style: const TextStyle(fontWeight: FontWeight.bold),
                  ),
                  const SizedBox(height: 8),
                  for (final key in [
                    'status',
                    'documentNumber',
                    'fullName',
                    'dateOfBirth',
                    'confidence',
                    'processedAt',
                  ])
                    Padding(
                      padding: const EdgeInsets.symmetric(vertical: 4),
                      child: SelectableText('$key: ${_result![key] ?? '—'}'),
                    ),
                ],
              ),
            ),
          ),
        ],
      ],
    ),
  );
}
