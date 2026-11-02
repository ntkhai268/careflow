part of 'journey_models.dart';

class DiagnosisSummary {
  const DiagnosisSummary({required this.title, required this.detail});

  final String title;
  final String detail;

  Map<String, dynamic> toJson() => {'title': title, 'detail': detail};

  factory DiagnosisSummary.fromJson(Map<String, dynamic> json) =>
      DiagnosisSummary(
        title: json['title'] as String,
        detail: json['detail'] as String,
      );

  @override
  bool operator ==(Object other) =>
      other is DiagnosisSummary &&
      title == other.title &&
      detail == other.detail;

  @override
  int get hashCode => Object.hash(title, detail);
}

class PrescriptionItem {
  const PrescriptionItem({
    required this.medicationName,
    required this.dosage,
    required this.route,
    required this.frequency,
    required this.duration,
    required this.caution,
    this.unitPrice,
    this.quantity = 1,
  });

  final String medicationName;
  final String dosage;
  final String route;
  final String frequency;
  final String duration;
  final String caution;
  final int? unitPrice;
  final int quantity;

  int get lineAmount => (unitPrice ?? 0) * quantity;

  Map<String, dynamic> toJson() => {
    'medicationName': medicationName,
    'dosage': dosage,
    'route': route,
    'frequency': frequency,
    'duration': duration,
    'caution': caution,
    if (unitPrice != null) 'unitPrice': unitPrice,
    'quantity': quantity,
  };

  factory PrescriptionItem.fromJson(Map<String, dynamic> json) =>
      PrescriptionItem(
        medicationName: json['medicationName'] as String,
        dosage: json['dosage'] as String,
        route: json['route'] as String,
        frequency: json['frequency'] as String,
        duration: json['duration'] as String,
        caution: json['caution'] as String,
        unitPrice: json['unitPrice'] == null
            ? null
            : (json['unitPrice'] as num).toInt(),
        quantity: json['quantity'] == null
            ? 1
            : (json['quantity'] as num).toInt(),
      );

  @override
  bool operator ==(Object other) =>
      other is PrescriptionItem &&
      medicationName == other.medicationName &&
      dosage == other.dosage &&
      route == other.route &&
      frequency == other.frequency &&
      duration == other.duration &&
      caution == other.caution &&
      unitPrice == other.unitPrice &&
      quantity == other.quantity;

  @override
  int get hashCode => Object.hash(
    medicationName,
    dosage,
    route,
    frequency,
    duration,
    caution,
    unitPrice,
    quantity,
  );
}

class Prescription {
  Prescription({
    required this.id,
    required DateTime issuedAt,
    required List<PrescriptionItem> items,
  }) : issuedAt = issuedAt.toUtc(),
       items = List.unmodifiable(items);

  final String id;
  final DateTime issuedAt;
  final List<PrescriptionItem> items;

  Map<String, dynamic> toJson() => {
    'id': id,
    'issuedAt': _iso(issuedAt),
    'items': items.map((item) => item.toJson()).toList(),
  };

  factory Prescription.fromJson(Map<String, dynamic> json) => Prescription(
    id: json['id'] as String,
    issuedAt: _utc(json['issuedAt'] as String),
    items: _objects(json['items'], PrescriptionItem.fromJson),
  );

  @override
  bool operator ==(Object other) =>
      other is Prescription &&
      id == other.id &&
      issuedAt == other.issuedAt &&
      _sameList(items, other.items);

  @override
  int get hashCode => Object.hash(id, issuedAt, Object.hashAll(items));
}

class FollowUpAppointment {
  FollowUpAppointment({
    required DateTime scheduledAt,
    required this.room,
    required this.note,
  }) : scheduledAt = scheduledAt.toUtc();

  final DateTime scheduledAt;
  final String room;
  final String note;

  Map<String, dynamic> toJson() => {
    'scheduledAt': _iso(scheduledAt),
    'room': room,
    'note': note,
  };

  factory FollowUpAppointment.fromJson(Map<String, dynamic> json) =>
      FollowUpAppointment(
        scheduledAt: _utc(json['scheduledAt'] as String),
        room: json['room'] as String,
        note: json['note'] as String,
      );

  @override
  bool operator ==(Object other) =>
      other is FollowUpAppointment &&
      scheduledAt == other.scheduledAt &&
      room == other.room &&
      note == other.note;

  @override
  int get hashCode => Object.hash(scheduledAt, room, note);
}
