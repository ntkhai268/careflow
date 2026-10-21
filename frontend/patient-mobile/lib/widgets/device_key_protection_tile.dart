import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../services/api_service.dart';

class DeviceKeyProtectionTile extends ConsumerWidget {
  const DeviceKeyProtectionTile({super.key});
  @override
  Widget build(BuildContext context, WidgetRef ref) => FutureBuilder<String>(
    future: ref.read(apiServiceProvider).deviceKeyProtection(),
    builder: (context, snapshot) => Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Icon(Icons.phonelink_lock),
            const SizedBox(width: 12),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  const Text(
                    'Bảo vệ khóa trên điện thoại',
                    style: TextStyle(fontWeight: FontWeight.w600),
                  ),
                  const SizedBox(height: 6),
                  Text(switch (snapshot.data) {
                    'secure_enclave' =>
                      'Khóa ký được giữ trong Secure Enclave của iPhone.',
                    'strongbox' =>
                      'Khóa ký được giữ trong StrongBox của điện thoại.',
                    'trusted_environment' =>
                      'Khóa ký được bảo vệ bằng phần cứng Android Keystore.',
                    'software_keystore' =>
                      'Khóa không xuất ra khỏi Android Keystore; thiết bị này chưa báo hỗ trợ phần cứng bảo mật.',
                    'legacy_software' =>
                      'Khóa cũ sẽ được nâng cấp sau lần đăng nhập thành công tiếp theo. Thiết lập Face ID/vân tay được giữ lại khi chuyển khóa thành công.',
                    _ =>
                      snapshot.hasError
                          ? 'Chưa đọc được trạng thái khóa. Hãy mở khóa điện thoại và thử lại.'
                          : 'Đang kiểm tra…',
                  }),
                ],
              ),
            ),
          ],
        ),
      ),
    ),
  );
}
