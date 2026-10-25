import 'package:flutter/material.dart';
import 'dart:async';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../config/router.dart';
import '../config/theme.dart';
import '../providers/auth_provider.dart';
import '../services/api_service.dart';

/// Hide content in app switching and lock biometric sessions after backgrounding.
/// Inactive alone (including an OS biometric prompt) must not trigger a lock.
class BiometricAppLock extends ConsumerStatefulWidget {
  const BiometricAppLock({super.key, required this.child});
  final Widget child;
  @override
  ConsumerState<BiometricAppLock> createState() => _BiometricAppLockState();
}

class _BiometricAppLockState extends ConsumerState<BiometricAppLock>
    with WidgetsBindingObserver {
  bool _hidden = false, _locking = false;
  DateTime? _pausedAt, _lastHeartbeat;
  Timer? _idleTimer, _backgroundTimer;
  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted) _interaction(heartbeat: false);
    });
  }

  @override
  void dispose() {
    _idleTimer?.cancel();
    _backgroundTimer?.cancel();
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.paused) {
      _pausedAt = DateTime.now();
      _backgroundTimer?.cancel();
      _backgroundTimer = Timer(const Duration(seconds: 60), _lock);
    }
    if (state == AppLifecycleState.resumed) {
      _resume();
    } else if (mounted) {
      setState(() => _hidden = true);
    }
  }

  Future<void> _resume() async {
    _backgroundTimer?.cancel();
    final pausedAt = _pausedAt;
    _pausedAt = null;
    try {
      if (pausedAt != null &&
          DateTime.now().difference(pausedAt) >= const Duration(seconds: 60)) {
        await _lock();
      }
    } finally {
      if (mounted) setState(() => _hidden = false);
    }
  }

  Future<void> _lock() async {
    if (_locking || ref.read(authProvider).status != AuthStatus.authenticated) {
      return;
    }
    _locking = true;
    _idleTimer?.cancel();
    try {
      await ref.read(authProvider.notifier).lockSession();
      if (mounted &&
          !appRouter.routeInformationProvider.value.uri.path.startsWith(
            '/login/',
          )) {
        appRouter.go('/login');
      }
    } finally {
      _locking = false;
    }
  }

  void _interaction({bool heartbeat = true}) {
    if (ref.read(authProvider).status != AuthStatus.authenticated) return;
    _idleTimer?.cancel();
    final role = ref.read(authProvider).role;
    _idleTimer = Timer(
      Duration(minutes: role == null || role == 'PATIENT' ? 30 : 15),
      _lock,
    );
    if (!heartbeat) return;
    final now = DateTime.now();
    if (_lastHeartbeat != null &&
        now.difference(_lastHeartbeat!) < const Duration(minutes: 1)) {
      return;
    }
    _lastHeartbeat = now;
    // This request is triggered by a person interacting, never by background polling.
    unawaited(
      ref
          .read(apiServiceProvider)
          .post('/auth/activity')
          .then<void>((_) {}, onError: (Object _) {}),
    );
  }

  @override
  Widget build(BuildContext context) {
    ref.listen(authProvider, (previous, next) {
      if (next.status == AuthStatus.authenticated) {
        _interaction(heartbeat: false);
      } else if (next.status == AuthStatus.unauthenticated) {
        _idleTimer?.cancel();
      }
    });
    ref.listen(sessionInvalidationProvider, (_, _) {
      unawaited(_lock());
    });
    return Listener(
      onPointerDown: (_) => _interaction(),
      onPointerSignal: (_) => _interaction(),
      child: Stack(
        children: [
          widget.child,
          if (_hidden)
            const Positioned.fill(
              child: ColoredBox(
                color: AppColors.surface,
                child: Center(
                  child: Icon(
                    Icons.lock_outline_rounded,
                    color: AppColors.primary,
                    size: 48,
                  ),
                ),
              ),
            ),
        ],
      ),
    );
  }
}
