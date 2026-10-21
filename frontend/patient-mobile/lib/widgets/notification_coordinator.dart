import 'dart:async';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../config/router.dart';
import '../providers/auth_provider.dart';
import '../features/journey/application/journey_providers.dart';
import '../services/notification_channel.dart';
import '../services/push_notification_service.dart';
import '../services/queue_service.dart';
import '../services/notification_refresh.dart';
import '../services/walk_in_history_service.dart';
import '../services/notification_resource_service.dart';

class NotificationCoordinator extends ConsumerStatefulWidget {
  const NotificationCoordinator({super.key, required this.child});
  final Widget child;
  @override
  ConsumerState<NotificationCoordinator> createState() =>
      _NotificationCoordinatorState();
}

class _NotificationCoordinatorState
    extends ConsumerState<NotificationCoordinator>
    with WidgetsBindingObserver {
  StreamSubscription<void>? _socketUpdates, _pushUpdates;
  StreamSubscription<String>? _taps;
  ProviderSubscription<(AuthStatus, String?)>? _auth;
  Timer? _debounce, _recovery;
  bool _foreground = true, _busy = false, _dirty = false;
  String? _owner;
  int _epoch = 0;
  late final NotificationChannel _channel;
  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    final channel = _channel = ref.read(notificationChannelProvider),
        push = ref.read(pushNotificationServiceProvider);
    _socketUpdates = channel.updates.listen((_) => _schedule());
    _pushUpdates = push.changes.listen((_) => _schedule());
    _taps = push.taps.listen((id) {
      if (_owner == null || !_foreground) return;
      appRouter.push('/notifications/${Uri.encodeComponent(id)}');
      push.acknowledgeTap(id);
    });
    _auth = ref.listenManual(authProvider.select((a) => (a.status, a.userId)), (
      _,
      scope,
    ) {
      final owner = scope.$1 == AuthStatus.authenticated ? scope.$2 : null;
      if (owner == _owner) return;
      _owner = owner;
      ++_epoch;
      push.setAccount(owner);
      channel.activate(owner);
      if (!_foreground) channel.pause();
      _recovery?.cancel();
      if (owner != null) {
        unawaited(push.bindAuthenticatedSession());
        _schedule();
        _recovery = Timer.periodic(const Duration(seconds: 30), (_) {
          if (_foreground) _schedule();
        });
      }
    }, fireImmediately: true);
  }

  void _schedule() {
    if (_owner == null || !_foreground || !mounted) return;
    _dirty = true;
    _debounce?.cancel();
    _debounce = Timer(const Duration(milliseconds: 400), _refresh);
  }

  Future<void> _refresh() async {
    if (_busy || !_dirty || _owner == null || !_foreground || !mounted) return;
    _busy = true;
    _dirty = false;
    final epoch = _epoch;
    try {
      ref.read(notificationRefreshRevisionProvider.notifier).state++;
      ref.invalidate(appointmentQueueStatusProvider);
      ref.invalidate(queueTicketProvider);
      ref.invalidate(notificationResourceProvider);
      ref.invalidate(notificationUnreadCountProvider);
      ref.invalidate(visitResultsProvider);
      ref.invalidate(walkInHistoryProvider);
      ref.invalidate(clinicalVisitOutcomeProvider);
      await ref.read(patientNotificationInboxProvider.notifier).refresh();
      if (epoch != _epoch || !mounted) return;
      await ref
          .read(journeyControllerProvider.notifier)
          .refreshCurrentJourney();
    } catch (_) {
      /* A later signal, resume or manual refresh retries authoritative reads. */
    } finally {
      _busy = false;
      if (_dirty && mounted && _owner != null) _schedule();
    }
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    _foreground = state == AppLifecycleState.resumed;
    if (_foreground) {
      ref.read(notificationChannelProvider).resume();
      _schedule();
      if (_owner != null) {
        unawaited(
          ref.read(pushNotificationServiceProvider).bindAuthenticatedSession(),
        );
      }
    } else {
      ref.read(notificationChannelProvider).pause();
      _debounce?.cancel();
    }
  }

  @override
  void dispose() {
    ++_epoch;
    _debounce?.cancel();
    _recovery?.cancel();
    _auth?.close();
    unawaited(_socketUpdates?.cancel());
    unawaited(_pushUpdates?.cancel());
    unawaited(_taps?.cancel());
    _channel.pause();
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => widget.child;
}
