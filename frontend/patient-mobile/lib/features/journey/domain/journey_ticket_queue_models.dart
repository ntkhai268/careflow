part of 'journey_models.dart';

class VisitTicket {
  const VisitTicket({
    required this.code,
    required this.qrPayload,
    required this.queueNumber,
    required this.hospitalName,
    required this.specialtyName,
    required this.room,
    required this.expectedWindow,
  });

  final String code;
  final String qrPayload;
  final String queueNumber;
  final String hospitalName;
  final String specialtyName;
  final String room;
  final String expectedWindow;

  Map<String, dynamic> toJson() => {
    'code': code,
    'qrPayload': qrPayload,
    'queueNumber': queueNumber,
    'hospitalName': hospitalName,
    'specialtyName': specialtyName,
    'room': room,
    'expectedWindow': expectedWindow,
  };

  factory VisitTicket.fromJson(Map<String, dynamic> json) {
    final room = json['room'] as String;
    return VisitTicket(
      code: json['code'] as String,
      qrPayload: json['qrPayload'] as String,
      queueNumber: json['queueNumber'] as String,
      hospitalName:
          json['hospitalName'] as String? ??
          'Bệnh viện CareFlow (dữ liệu mô phỏng)',
      specialtyName:
          json['specialtyName'] as String? ?? _legacySpecialtyName(room),
      room: room,
      expectedWindow: json['expectedWindow'] as String,
    );
  }

  @override
  bool operator ==(Object other) =>
      other is VisitTicket &&
      code == other.code &&
      qrPayload == other.qrPayload &&
      queueNumber == other.queueNumber &&
      hospitalName == other.hospitalName &&
      specialtyName == other.specialtyName &&
      room == other.room &&
      expectedWindow == other.expectedWindow;

  @override
  int get hashCode => Object.hash(
    code,
    qrPayload,
    queueNumber,
    hospitalName,
    specialtyName,
    room,
    expectedWindow,
  );
}

String _legacySpecialtyName(String room) {
  const roomMarker = ' - Phòng';
  final roomMarkerIndex = room.indexOf(roomMarker);
  if (roomMarkerIndex > 0) return room.substring(0, roomMarkerIndex);
  return 'Chuyên khoa chưa xác định (dữ liệu mô phỏng)';
}

class QueueSnapshot {
  const QueueSnapshot({
    required this.room,
    required this.peopleAhead,
    required this.expectedWait,
  });

  final String room;
  final int peopleAhead;
  final String expectedWait;

  Map<String, dynamic> toJson() => {
    'room': room,
    'peopleAhead': peopleAhead,
    'expectedWait': expectedWait,
  };

  factory QueueSnapshot.fromJson(Map<String, dynamic> json) => QueueSnapshot(
    room: json['room'] as String,
    peopleAhead: (json['peopleAhead'] as num).toInt(),
    expectedWait: json['expectedWait'] as String,
  );

  @override
  bool operator ==(Object other) =>
      other is QueueSnapshot &&
      room == other.room &&
      peopleAhead == other.peopleAhead &&
      expectedWait == other.expectedWait;

  @override
  int get hashCode => Object.hash(room, peopleAhead, expectedWait);
}

class JourneyTimelineEvent {
  JourneyTimelineEvent({
    required this.id,
    required this.title,
    required this.detail,
    required DateTime occurredAt,
  }) : occurredAt = occurredAt.toUtc();

  final String id;
  final String title;
  final String detail;
  final DateTime occurredAt;

  Map<String, dynamic> toJson() => {
    'id': id,
    'title': title,
    'detail': detail,
    'occurredAt': _iso(occurredAt),
  };

  factory JourneyTimelineEvent.fromJson(Map<String, dynamic> json) =>
      JourneyTimelineEvent(
        id: json['id'] as String,
        title: json['title'] as String,
        detail: json['detail'] as String,
        occurredAt: _utc(json['occurredAt'] as String),
      );

  @override
  bool operator ==(Object other) =>
      other is JourneyTimelineEvent &&
      id == other.id &&
      title == other.title &&
      detail == other.detail &&
      occurredAt == other.occurredAt;

  @override
  int get hashCode => Object.hash(id, title, detail, occurredAt);
}

class PatientNotification {
  PatientNotification({
    required this.id,
    required this.title,
    required this.body,
    required DateTime createdAt,
    required this.isRead,
    this.actionType,
    this.resourceId,
  }) : createdAt = createdAt.toUtc();

  final String id;
  final String title;
  final String body;
  final DateTime createdAt;
  final bool isRead;
  final String? actionType;
  final String? resourceId;

  PatientNotification copyWith({bool? isRead}) => PatientNotification(
    id: id,
    title: title,
    body: body,
    createdAt: createdAt,
    isRead: isRead ?? this.isRead,
    actionType: actionType,
    resourceId: resourceId,
  );

  Map<String, dynamic> toJson() => {
    'id': id,
    'title': title,
    'body': body,
    'createdAt': _iso(createdAt),
    'isRead': isRead,
    if (actionType != null) 'actionType': actionType,
    if (resourceId != null) 'resourceId': resourceId,
  };

  factory PatientNotification.fromJson(Map<String, dynamic> json) =>
      PatientNotification(
        id: json['id'] as String,
        title: json['title'] as String,
        body: json['body'] as String,
        createdAt: _utc(json['createdAt'] as String),
        isRead: json['isRead'] as bool,
        actionType: json['actionType'] as String?,
        resourceId: json['resourceId'] as String?,
      );

  @override
  bool operator ==(Object other) =>
      other is PatientNotification &&
      id == other.id &&
      title == other.title &&
      body == other.body &&
      createdAt == other.createdAt &&
      isRead == other.isRead &&
      actionType == other.actionType &&
      resourceId == other.resourceId;

  @override
  int get hashCode =>
      Object.hash(id, title, body, createdAt, isRead, actionType, resourceId);
}
