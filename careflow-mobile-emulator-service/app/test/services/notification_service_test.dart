import 'package:careflow_patient/models/app_notification.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  test('parses the Notification Service action and read state', () {
    final item = AppNotification.fromJson({
      'id': 'notification-1',
      'type': 'QUEUE_CALLED',
      'title': 'Đã đến lượt',
      'body': 'Mời số 47 vào phòng khám.',
      'action': {'type': 'OPEN_QUEUE', 'resourceId': 'entry-47'},
      'status': 'DELIVERED',
      'createdAt': '2026-08-18T03:35:00Z',
      'readAt': null,
    });

    expect(item.actionType, 'OPEN_QUEUE');
    expect(item.resourceId, 'entry-47');
    expect(item.isRead, isFalse);
    expect(item.copyWith(status: 'READ').isRead, isTrue);
  });
}
