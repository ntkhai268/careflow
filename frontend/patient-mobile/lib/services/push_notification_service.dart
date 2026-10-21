import 'dart:async';
import 'package:firebase_core/firebase_core.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:package_info_plus/package_info_plus.dart';
import 'package:uuid/uuid.dart';
import '../config/api_config.dart';
import 'api_service.dart';
import 'push_platform.dart';

class FirebaseBootstrap {
  static const enabled = bool.fromEnvironment(
    'FIREBASE_ENABLED',
    defaultValue: false,
  );
  static bool isReady = false;

  static Future<void> initialize() async {
    if (!enabled) return;
    try {
      await Firebase.initializeApp();
      isReady = true;
    } catch (error) {
      debugPrint(
        'Firebase chưa được cấu hình; app tiếp tục bằng inbox/WebSocket: $error',
      );
    }
  }
}

class PushNotificationService {
  PushNotificationService(this._api, {PushPlatform? platform})
    : _platform = platform ?? FirebasePushPlatform();
  final ApiService _api;
  final PushPlatform _platform;
  final _changes = StreamController<void>.broadcast(),
      _taps = StreamController<String>.broadcast();
  Stream<void> get changes => _changes.stream;
  Stream<String> get taps => _taps.stream;
  String? _owner, _binding, _device;
  int _epoch = 0;
  bool _disposed = false;
  Future<void>? _initializing;
  final statusListenable = ValueNotifier<String>('Chưa bật thông báo đẩy');
  String get status => statusListenable.value;
  set status(String value) {
    if (!_disposed) statusListenable.value = value;
  }

  StreamSubscription<String>? _tokens;
  StreamSubscription<Map<String, dynamic>>? _foreground, _opened;
  final Set<String> _shown = {};
  Map<String, dynamic>? _pendingTap;
  void setAccount(String? user) {
    if (_owner == user) return;
    ++_epoch;
    _owner = user;
    _binding = null;
    _shown.clear();
    unawaited(_tokens?.cancel());
    _tokens = null;
    if (FirebaseBootstrap.isReady) {
      unawaited(_platform.clearVisible().catchError((Object _) {}));
    }
    if (user == null) status = 'Cần đăng nhập để nhận thông báo';
  }

  Future<void> initializeHandlers() async {
    if (!FirebaseBootstrap.isReady || _disposed) return;
    final pending = _initializing;
    if (pending != null) return pending;
    final future = _initializeHandlers();
    _initializing = future;
    try {
      await future;
    } catch (_) {
      _initializing = null;
      rethrow;
    }
  }

  Future<void> _initializeHandlers() async {
    await _platform.initialize();
    if (_disposed) return;
    _foreground = _platform.foreground.listen(_receive);
    _opened = _platform.opened.listen(_open);
    final initial = await _platform.initial();
    _pendingTap ??= initial;
  }

  Future<void> bindAuthenticatedSession({
    bool requestPermission = false,
  }) async {
    if (!FirebaseBootstrap.isReady) {
      status =
          'Push chưa được cấu hình; thông báo trong ứng dụng vẫn hoạt động';
      return;
    }
    final epoch = _epoch;
    try {
      await initializeHandlers();
      if (epoch != _epoch || _disposed) return;
      final me = await _api.get('/auth/me');
      final user = me.data['data']['id'] as String;
      if (epoch != _epoch) return;
      if (_owner == null) {
        _owner = user;
      } else if (_owner != user) {
        return;
      }
      if (!await _platform.allowed(requestPermission)) {
        if (epoch == _epoch) status = 'Chưa cho phép thông báo trên điện thoại';
        return;
      }
      if (epoch != _epoch) return;
      final token = await _platform.token();
      if (epoch != _epoch) return;
      if (token == null || token.isEmpty) {
        status = 'Thiết bị chưa có mã push; hãy thử lại sau';
        return;
      }
      await _register(token, epoch, user);
      if (epoch != _epoch) return;
      _tokens ??= _platform.tokens.listen((value) {
        unawaited(
          _register(value, epoch, user).catchError((Object _) {
            if (epoch == _epoch) {
              status = 'Chưa cập nhật được đăng ký thông báo';
            }
          }),
        );
      });
      final tap = _pendingTap;
      _pendingTap = null;
      if (tap != null) _open(tap);
    } catch (_) {
      if (epoch == _epoch) status = 'Chưa đăng ký được push; có thể thử lại';
    }
  }

  Future<void> enable() => bindAuthenticatedSession(requestPermission: true);
  Future<void> unbindAuthenticatedSession() async {
    setAccount(null);
    _pendingTap = null;
    try {
      if (FirebaseBootstrap.isReady) {
        await _api.delete(
          '${ApiConfig.notifications}/devices/${await _deviceId()}',
        );
      }
    } catch (_) {
      /* Server session validation still gates future delivery. */
    }
    try {
      if (FirebaseBootstrap.isReady) await _platform.deleteToken();
    } catch (_) {
      /* Next successful bind transfers the actual token exclusively. */
    }
    if (FirebaseBootstrap.isReady) await _platform.clearVisible();
  }

  Future<void> _register(String token, int epoch, String owner) async {
    final id = await _deviceId();
    final package = await PackageInfo.fromPlatform();
    if (epoch != _epoch || _owner != owner) return;
    final response = await _api.put(
      '${ApiConfig.notifications}/devices',
      data: {
        'deviceId': id,
        'registrationToken': token,
        'platform': defaultTargetPlatform == TargetPlatform.iOS
            ? 'IOS'
            : 'ANDROID',
        'appVersion': '${package.version}+${package.buildNumber}',
      },
    );
    if (epoch != _epoch || _owner != owner) return;
    final binding = response.data['data']['bindingId'];
    if (binding is! String || binding.isEmpty) {
      throw const FormatException('Thiếu ràng buộc thông báo.');
    }
    _binding = binding;
    status = 'Đã đăng ký nhận thông báo';
  }

  Future<String> _deviceId() async {
    if (_device != null) return _device!;
    final preferences = await SharedPreferences.getInstance();
    final key = 'careflow_push_device:${ApiConfig.baseUrl}';
    final current = preferences.getString(key);
    if (current != null) return _device = current;
    final generated = const Uuid().v4();
    await preferences.setString(key, generated);
    return _device = generated;
  }

  bool _matches(Map<String, dynamic> data) =>
      _owner != null &&
      _binding != null &&
      data['bindingId'] == _binding &&
      data['notificationId'] is String;
  void _receive(Map<String, dynamic> data) {
    if (!_matches(data) || _disposed) return;
    _changes.add(null);
    final id = data['notificationId'] as String;
    if (!_shown.add(id)) return;
    if (_shown.length > 256) _shown.remove(_shown.first);
    unawaited(_platform.showGeneric(data).catchError((Object _) {}));
  }

  void _open(Map<String, dynamic> data) {
    if (_disposed) return;
    if (_owner == null || _binding == null) {
      _pendingTap = data;
      return;
    }
    if (!_matches(data) || _disposed) return;
    // Retain the tap until navigation accepts it. An interactive lock can occur
    // between the OS callback and the next authenticated foreground session.
    _pendingTap = data;
    _taps.add(data['notificationId'] as String);
  }

  void acknowledgeTap(String id) {
    final pending = _pendingTap;
    if (pending != null &&
        pending['notificationId'] == id &&
        _matches(pending)) {
      _pendingTap = null;
    }
  }

  Future<void> dispose() async {
    _disposed = true;
    ++_epoch;
    await _tokens?.cancel();
    await _foreground?.cancel();
    await _opened?.cancel();
    await _changes.close();
    await _taps.close();
    statusListenable.dispose();
  }
}

final pushNotificationServiceProvider = Provider<PushNotificationService>((
  ref,
) {
  final service = PushNotificationService(ref.read(apiServiceProvider));
  ref.onDispose(() => unawaited(service.dispose()));
  return service;
});
