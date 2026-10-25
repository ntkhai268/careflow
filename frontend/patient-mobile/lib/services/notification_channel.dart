import 'dart:async';
import 'dart:convert';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../config/api_config.dart';
import 'api_service.dart';
import 'notification_socket.dart';

enum NotificationConnection { paused, connecting, online, retrying }

class NotificationChannel {
  NotificationChannel(
    this.api, {
    Future<NotificationSocket> Function(Uri)? connect,
    this.retryBase = const Duration(seconds: 1),
  }) : connect = connect ?? connectNotificationSocket;
  final ApiService api;
  final Future<NotificationSocket> Function(Uri) connect;
  final Duration retryBase;
  final _updates = StreamController<void>.broadcast(),
      _states = StreamController<NotificationConnection>.broadcast();
  Stream<void> get updates => _updates.stream;
  Stream<NotificationConnection> get states => _states.stream;
  NotificationConnection state = NotificationConnection.paused;
  String? _owner;
  int _epoch = 0, _attempt = 0;
  bool _paused = true, _disposed = false;
  NotificationSocket? _socket;
  StreamSubscription<dynamic>? _subscription;
  Timer? _retry, _heartbeat, _handshake;
  String _buffer = '';
  void activate(String? owner) {
    if (_owner == owner && !_paused) return;
    _owner = owner;
    _paused = owner == null;
    _stop();
    if (!_paused) unawaited(_connect());
  }

  void pause() {
    _paused = true;
    _stop();
  }

  void resume() {
    if (_owner == null || !_paused) return;
    _paused = false;
    _stop();
    unawaited(_connect());
  }

  void _status(NotificationConnection next) {
    state = next;
    if (!_disposed) _states.add(next);
  }

  void _stop() {
    ++_epoch;
    _retry?.cancel();
    _retry = null;
    _heartbeat?.cancel();
    _handshake?.cancel();
    unawaited(_subscription?.cancel());
    _subscription = null;
    final old = _socket;
    _socket = null;
    unawaited(old?.close());
    _buffer = '';
    _status(NotificationConnection.paused);
  }

  Future<void> _connect() async {
    final epoch = _epoch, owner = _owner;
    if (_paused || owner == null || _disposed) return;
    _status(NotificationConnection.connecting);
    try {
      final credentials = await api.notificationSocketCredentials();
      if (epoch != _epoch || _paused) return;
      if (credentials.userId != owner) {
        throw const FormatException('Notification account changed.');
      }
      final base = Uri.parse(ApiConfig.wsUrl),
          apiUri = Uri.parse(ApiConfig.baseUrl);
      if (!{'wss', 'ws'}.contains(base.scheme) ||
          base.userInfo.isNotEmpty ||
          base.hasQuery ||
          base.host != apiUri.host ||
          (base.hasPort
                  ? base.port
                  : base.scheme == 'wss'
                  ? 443
                  : 80) !=
              apiUri.port ||
          (apiUri.scheme == 'https' && base.scheme != 'wss')) {
        throw const FormatException(
          'Notification endpoint does not match the API.',
        );
      }
      final uri = base.replace(
        path: base.path.endsWith('/notifications')
            ? base.path
            : '${base.path.replaceAll(RegExp(r'/+$'), '')}/notifications',
      );
      final socket = await connect(uri);
      if (epoch != _epoch || _paused) {
        await socket.close();
        return;
      }
      _socket = socket;
      _subscription = socket.messages.listen(
        (event) => _frame(event, epoch),
        onError: (Object _) => _lost(epoch),
        onDone: () => _lost(epoch),
      );
      if (credentials.headers.values.any(
        (v) => v.contains('\n') || v.contains('\r'),
      )) {
        throw const FormatException('Invalid notification credentials.');
      }
      socket.send(
        'CONNECT\naccept-version:1.2\nheart-beat:10000,0\nAuthorization:${credentials.headers['Authorization']}\nDPoP:${credentials.headers['DPoP']}\n\n\u0000',
      );
      _handshake = Timer(const Duration(seconds: 12), () => _lost(epoch));
    } catch (_) {
      _lost(epoch);
    }
  }

  void _frame(dynamic data, int epoch) {
    if (epoch != _epoch || _paused) return;
    try {
      _buffer += data is String ? data : utf8.decode(data as List<int>);
      if (_buffer.length > 131072) {
        throw const FormatException('Notification frame too large.');
      }
      while (_buffer.contains('\u0000')) {
        final end = _buffer.indexOf('\u0000');
        final frame = _buffer
            .substring(0, end)
            .replaceFirst(RegExp(r'^\s+'), '');
        _buffer = _buffer.substring(end + 1);
        if (frame.startsWith('CONNECTED\n') ||
            frame.startsWith('CONNECTED\r\n')) {
          _handshake?.cancel();
          _attempt = 0;
          _socket?.send(
            'SUBSCRIBE\nid:careflow-inbox\ndestination:/user/queue/notifications\nack:auto\n\n\u0000',
          );
          _status(NotificationConnection.online);
          _updates.add(null);
          _heartbeat?.cancel();
          _heartbeat = Timer.periodic(const Duration(seconds: 10), (_) {
            if (epoch == _epoch) _socket?.send('\n');
          });
        } else if (frame.startsWith('MESSAGE\n') ||
            frame.startsWith('MESSAGE\r\n')) {
          final split = frame.contains('\r\n\r\n') ? '\r\n\r\n' : '\n\n';
          final index = frame.indexOf(split);
          if (index < 0) throw const FormatException('Malformed notification.');
          final value = jsonDecode(frame.substring(index + split.length));
          if (value is Map && value['id'] is String) _updates.add(null);
        } else if (frame.startsWith('ERROR')) {
          _lost(epoch);
          return;
        }
      }
      if (_buffer.trim().isEmpty) _buffer = '';
    } catch (_) {
      _lost(epoch);
    }
  }

  void _lost(int epoch) {
    if (epoch != _epoch || _paused || _disposed || _retry != null) return;
    _heartbeat?.cancel();
    _handshake?.cancel();
    unawaited(_subscription?.cancel());
    _subscription = null;
    final old = _socket;
    _socket = null;
    unawaited(old?.close());
    _buffer = '';
    _status(NotificationConnection.retrying);
    final nextEpoch = ++_epoch;
    final seconds = 1 << (_attempt++).clamp(0, 5);
    _retry = Timer(retryBase * seconds, () {
      _retry = null;
      if (nextEpoch == _epoch) unawaited(_connect());
    });
  }

  Future<void> dispose() async {
    _disposed = true;
    _stop();
    await _updates.close();
    await _states.close();
  }
}

final notificationChannelProvider = Provider((ref) {
  final channel = NotificationChannel(ref.read(apiServiceProvider));
  ref.onDispose(() => unawaited(channel.dispose()));
  return channel;
});
