class VisitTicket {
  const VisitTicket({
    required this.ticketId,
    required this.ticketCode,
    required this.appointmentId,
    required this.patientId,
    required this.queueNumber,
    required this.department,
    required this.departmentDisplayName,
    required this.roomId,
    required this.roomDisplayName,
    required this.appointmentDate,
    required this.timeSlot,
    this.qrToken,
    required this.status,
  });

  final String ticketId;
  final String ticketCode;
  final String appointmentId;
  final String patientId;
  final String queueNumber;
  final String department;
  final String departmentDisplayName;
  final String roomId;
  final String roomDisplayName;
  final DateTime appointmentDate;
  final String timeSlot;
  final String? qrToken;
  final String status;

  factory VisitTicket.fromJson(Map<String, dynamic> json) => VisitTicket(
    ticketId: json['ticketId'] as String,
    ticketCode: json['ticketCode'] as String,
    appointmentId: json['appointmentId'] as String,
    patientId: json['patientId'] as String,
    queueNumber: json['queueNumber'].toString(),
    department: json['department'] as String,
    departmentDisplayName: json['departmentDisplayName'] as String,
    roomId: json['roomId'] as String,
    roomDisplayName: json['roomDisplayName'] as String,
    appointmentDate: DateTime.parse(json['appointmentDate'] as String),
    timeSlot: json['timeSlot'] as String,
    qrToken: json['qrToken'] as String?,
    status: json['status'] as String,
  );
}

class PatientQueueStatus {
  const PatientQueueStatus({
    required this.entryId,
    required this.patientId,
    required this.roomCode,
    required this.queueNumber,
    required this.status,
    required this.position,
    required this.estimatedWaitMinutes,
    this.type,
    this.consultationPhase,
  });

  final String entryId;
  final String patientId;
  final String roomCode;
  final String queueNumber;
  final String status;
  final int? position;
  final int? estimatedWaitMinutes;
  final String? type;
  final String? consultationPhase;

  factory PatientQueueStatus.fromJson(Map<String, dynamic> json) =>
      PatientQueueStatus(
        entryId: json['entryId'] as String,
        patientId: json['patientId'] as String,
        roomCode: json['roomCode'] as String,
        queueNumber: json['queueNumber'].toString(),
        status: (json['queueStatus'] ?? json['status']) as String,
        position: json['effectivePosition'] as int?,
        estimatedWaitMinutes: json['estimatedWaitMinutes'] as int?,
        type: json['type'] as String?,
        consultationPhase: json['consultationPhase'] as String?,
      );
}
