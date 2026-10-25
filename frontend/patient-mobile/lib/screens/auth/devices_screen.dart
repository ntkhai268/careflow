import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:intl/intl.dart';
import '../../services/device_management_service.dart';
import '../../services/api_service.dart';
import '../../providers/auth_provider.dart';
import '../../utils/api_error_message.dart';
import '../../widgets/device_key_protection_tile.dart';

class DevicesScreen extends ConsumerStatefulWidget {
  const DevicesScreen({super.key});
  @override
  ConsumerState<DevicesScreen> createState() => _DevicesScreenState();
}

class _DevicesScreenState extends ConsumerState<DevicesScreen> {
  List<LoginDevice>? _devices;
  String? _currentId, _busyId, _error;
  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    try {
      final api = ref.read(apiServiceProvider);
      var current = await api.sessions.credentialId();
      final devices = await ref.read(deviceManagementServiceProvider).list();
      if (current != null &&
          !devices.any((device) => device.id == current) &&
          await api.sessions.credentialId() == current) {
        await api.sessions.disable();
        current = null;
      }
      if (mounted) {
        setState(() {
          _devices = devices;
          _currentId = current;
          _error = null;
        });
      }
    } catch (e) {
      if (mounted) setState(() => _error = ApiErrorMessage.from(e));
    }
  }

  Future<void> _revoke(LoginDevice device) async {
    final current = device.id == _currentId;
    final accepted = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Thu hồi thiết bị?'),
        content: Text(
          current
              ? 'Bạn sẽ đăng xuất và cần dùng mật khẩu để bật lại Face ID/vân tay trên điện thoại này.'
              : 'Thiết bị này sẽ mất các phiên đăng nhập gắn với nó và quyền đăng nhập bằng Face ID/vân tay.',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx, false),
            child: const Text('Giữ lại'),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(ctx, true),
            child: const Text('Thu hồi'),
          ),
        ],
      ),
    );
    if (accepted != true || !mounted) return;
    setState(() {
      _busyId = device.id;
      _error = null;
    });
    try {
      await ref.read(deviceManagementServiceProvider).revoke(device.id);
      if (current) {
        await ref.read(apiServiceProvider).clearTokens();
        await ref.read(authProvider.notifier).logout();
        if (mounted) context.go('/login');
      } else {
        await _load();
      }
    } catch (e) {
      if (mounted) setState(() => _error = ApiErrorMessage.from(e));
    } finally {
      if (mounted) setState(() => _busyId = null);
    }
  }

  String _date(DateTime value) =>
      DateFormat('dd/MM/yyyy HH:mm').format(value.toLocal());
  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(title: const Text('Thiết bị sinh trắc học')),
    body: RefreshIndicator(
      onRefresh: _load,
      child: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          const DeviceKeyProtectionTile(),
          const Text(
            'Các thiết bị đã bật Face ID/vân tay cho tài khoản. Danh sách này không bao gồm mọi phiên đăng nhập bằng mật khẩu hoặc web.',
          ),
          const SizedBox(height: 16),
          if (_error != null) ...[
            Text(
              _error!,
              style: TextStyle(color: Theme.of(context).colorScheme.error),
            ),
            TextButton(
              onPressed: _busyId == null ? _load : null,
              child: const Text('Thử lại'),
            ),
          ],
          if (_devices == null && _error == null)
            const Center(child: CircularProgressIndicator()),
          if (_devices?.isEmpty == true)
            const Text('Chưa có thiết bị sinh trắc học đang hoạt động.'),
          for (final device in _devices ?? <LoginDevice>[])
            Card(
              child: ListTile(
                leading: const Icon(Icons.phonelink_lock),
                title: Text(
                  device.id == _currentId
                      ? 'Điện thoại này'
                      : 'Thiết bị ${device.id.substring(0, 8)}',
                ),
                subtitle: Text(
                  'Đăng ký: ${_date(device.createdAt)}\n${device.lastUsedAt == null ? 'Chưa đăng nhập bằng sinh trắc học' : 'Lần dùng cuối: ${_date(device.lastUsedAt!)}'}\nHết hạn: ${_date(device.expiresAt)}',
                ),
                isThreeLine: true,
                trailing: _busyId == device.id
                    ? const SizedBox(
                        width: 24,
                        height: 24,
                        child: CircularProgressIndicator(),
                      )
                    : TextButton(
                        onPressed: _busyId != null
                            ? null
                            : () => _revoke(device),
                        child: const Text('Thu hồi'),
                      ),
              ),
            ),
        ],
      ),
    ),
  );
}
