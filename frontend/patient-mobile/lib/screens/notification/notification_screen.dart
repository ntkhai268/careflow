import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../features/journey/application/journey_providers.dart';
import '../../features/journey/presentation/journey_notification_screen.dart';

/// Notification tab backed by the active patient's journey.
class NotificationScreen extends ConsumerWidget {
  const NotificationScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final appointmentId = ref.watch(activeJourneyProvider)?.appointmentId ?? '';
    return JourneyNotificationScreen(appointmentId: appointmentId);
  }
}
