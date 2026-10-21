import 'notification_socket_types.dart';
export 'notification_socket_types.dart';

Future<NotificationSocket> connectNotificationSocket(Uri uri) => Future.error(
  UnsupportedError('Native notification transport is unavailable.'),
);
