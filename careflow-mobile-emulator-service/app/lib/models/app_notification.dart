/// Notification item returned by CareFlow Notification Service.
class AppNotification {
  const AppNotification({
    required this.id,
    required this.type,
    required this.title,
    required this.body,
    required this.status,
    required this.createdAt,
    this.actionType,
    this.resourceId,
    this.readAt,
  });

  final String id;
  final String type;
  final String title;
  final String body;
  final String status;
  final DateTime createdAt;
  final String? actionType;
  final String? resourceId;
  final DateTime? readAt;

  bool get isRead => status == 'READ' || readAt != null;

  factory AppNotification.fromJson(Map<String, dynamic> json) {
    final action = json['action'];
    final actionMap = action is Map
        ? Map<String, dynamic>.from(action)
        : const <String, dynamic>{};
    return AppNotification(
      id: json['id']?.toString() ?? '',
      type: json['type']?.toString() ?? 'GENERAL',
      title: json['title']?.toString() ?? 'Thông báo CareFlow',
      body: json['body']?.toString() ?? '',
      status: json['status']?.toString() ?? 'DELIVERED',
      createdAt: DateTime.parse(json['createdAt'].toString()).toUtc(),
      actionType: actionMap['type']?.toString(),
      resourceId: actionMap['resourceId']?.toString(),
      readAt: json['readAt'] == null
          ? null
          : DateTime.parse(json['readAt'].toString()).toUtc(),
    );
  }

  AppNotification copyWith({String? status, DateTime? readAt}) =>
      AppNotification(
        id: id,
        type: type,
        title: title,
        body: body,
        status: status ?? this.status,
        createdAt: createdAt,
        actionType: actionType,
        resourceId: resourceId,
        readAt: readAt ?? this.readAt,
      );
}
