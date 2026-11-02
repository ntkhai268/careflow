part of 'journey_models.dart';

class LaboratoryResult {
  LaboratoryResult({
    required this.id,
    required this.value,
    required this.unit,
    required this.referenceRange,
    required this.source,
    required DateTime reportedAt,
  }) : reportedAt = reportedAt.toUtc();

  final String id;
  final String value;
  final String unit;
  final String referenceRange;
  final String source;
  final DateTime reportedAt;

  Map<String, dynamic> toJson() => {
    'id': id,
    'value': value,
    'unit': unit,
    'referenceRange': referenceRange,
    'source': source,
    'reportedAt': _iso(reportedAt),
  };

  factory LaboratoryResult.fromJson(Map<String, dynamic> json) =>
      LaboratoryResult(
        id: json['id'] as String,
        value: json['value'] as String,
        unit: json['unit'] as String,
        referenceRange: json['referenceRange'] as String,
        source: json['source'] as String,
        reportedAt: _utc(json['reportedAt'] as String),
      );

  @override
  bool operator ==(Object other) =>
      other is LaboratoryResult &&
      id == other.id &&
      value == other.value &&
      unit == other.unit &&
      referenceRange == other.referenceRange &&
      source == other.source &&
      reportedAt == other.reportedAt;

  @override
  int get hashCode =>
      Object.hash(id, value, unit, referenceRange, source, reportedAt);
}

class LaboratoryOrder {
  LaboratoryOrder({
    required this.id,
    required this.name,
    required this.department,
    required this.destination,
    required this.preparationNote,
    required this.price,
    this.result,
  });

  final String id;
  final String name;
  final String department;
  final String destination;
  final String preparationNote;
  final int price;
  final LaboratoryResult? result;

  Map<String, dynamic> toJson() => {
    'id': id,
    'name': name,
    'department': department,
    'destination': destination,
    'preparationNote': preparationNote,
    'price': price,
    if (result != null) 'result': result!.toJson(),
  };

  factory LaboratoryOrder.fromJson(Map<String, dynamic> json) =>
      LaboratoryOrder(
        id: json['id'] as String,
        name: json['name'] as String,
        department: json['department'] as String,
        destination: json['destination'] as String,
        preparationNote: json['preparationNote'] as String,
        price: (json['price'] as num).toInt(),
        result: json['result'] == null
            ? null
            : LaboratoryResult.fromJson(_map(json['result'])),
      );

  @override
  bool operator ==(Object other) =>
      other is LaboratoryOrder &&
      id == other.id &&
      name == other.name &&
      department == other.department &&
      destination == other.destination &&
      preparationNote == other.preparationNote &&
      price == other.price &&
      result == other.result;

  @override
  int get hashCode => Object.hash(
    id,
    name,
    department,
    destination,
    preparationNote,
    price,
    result,
  );
}

class VisitPayment {
  VisitPayment({
    required this.method,
    required this.amount,
    required DateTime acknowledgedAt,
  }) : acknowledgedAt = acknowledgedAt.toUtc();

  final PaymentMethod method;
  final int amount;
  final DateTime acknowledgedAt;

  Map<String, dynamic> toJson() => {
    'method': method.wireValue,
    'amount': amount,
    'acknowledgedAt': _iso(acknowledgedAt),
  };

  factory VisitPayment.fromJson(Map<String, dynamic> json) => VisitPayment(
    method: PaymentMethodContract.fromWireValue(json['method']),
    amount: (json['amount'] as num).toInt(),
    acknowledgedAt: _utc(json['acknowledgedAt'] as String),
  );

  @override
  bool operator ==(Object other) =>
      other is VisitPayment &&
      method == other.method &&
      amount == other.amount &&
      acknowledgedAt == other.acknowledgedAt;

  @override
  int get hashCode => Object.hash(method, amount, acknowledgedAt);
}

class VisitSettlement {
  VisitSettlement({
    required this.status,
    required this.totalVisitCost,
    required this.prepaidAmount,
    required this.amountDue,
    required this.refundDue,
    required DateTime calculatedAt,
    this.method,
    DateTime? acknowledgedAt,
  }) : calculatedAt = calculatedAt.toUtc(),
       acknowledgedAt = acknowledgedAt?.toUtc();

  final VisitSettlementStatus status;
  final int totalVisitCost;
  final int prepaidAmount;
  final int amountDue;
  final int refundDue;
  final DateTime calculatedAt;
  final PaymentMethod? method;
  final DateTime? acknowledgedAt;

  static VisitSettlement calculate({
    required int consultationFee,
    required int laboratoryTotal,
    required int medicationTotal,
    required int prepaidAmount,
    required DateTime calculatedAt,
  }) {
    final totalVisitCost = consultationFee + laboratoryTotal + medicationTotal;
    final amountDue = totalVisitCost > prepaidAmount
        ? totalVisitCost - prepaidAmount
        : 0;
    final refundDue = prepaidAmount > totalVisitCost
        ? prepaidAmount - totalVisitCost
        : 0;
    return VisitSettlement(
      status: amountDue > 0
          ? VisitSettlementStatus.paymentDue
          : refundDue > 0
          ? VisitSettlementStatus.refundPending
          : VisitSettlementStatus.settled,
      totalVisitCost: totalVisitCost,
      prepaidAmount: prepaidAmount,
      amountDue: amountDue,
      refundDue: refundDue,
      calculatedAt: calculatedAt,
    );
  }

  VisitSettlement copyWith({
    VisitSettlementStatus? status,
    PaymentMethod? method,
    Object? acknowledgedAt = _unset,
  }) => VisitSettlement(
    status: status ?? this.status,
    totalVisitCost: totalVisitCost,
    prepaidAmount: prepaidAmount,
    amountDue: amountDue,
    refundDue: refundDue,
    calculatedAt: calculatedAt,
    method: method ?? this.method,
    acknowledgedAt: identical(acknowledgedAt, _unset)
        ? this.acknowledgedAt
        : acknowledgedAt as DateTime?,
  );

  Map<String, dynamic> toJson() => {
    'status': status.wireValue,
    'totalVisitCost': totalVisitCost,
    'prepaidAmount': prepaidAmount,
    'amountDue': amountDue,
    'refundDue': refundDue,
    'calculatedAt': _iso(calculatedAt),
    if (method != null) 'method': method!.wireValue,
    if (acknowledgedAt != null) 'acknowledgedAt': _iso(acknowledgedAt!),
  };

  factory VisitSettlement.fromJson(Map<String, dynamic> json) => VisitSettlement(
    status: VisitSettlementStatusContract.fromWireValue(json['status']),
    totalVisitCost: (json['totalVisitCost'] as num).toInt(),
    prepaidAmount: (json['prepaidAmount'] as num).toInt(),
    amountDue: (json['amountDue'] as num).toInt(),
    refundDue: (json['refundDue'] as num).toInt(),
    calculatedAt: _utc(json['calculatedAt'] as String),
    method: json['method'] == null
        ? null
        : PaymentMethodContract.fromWireValue(json['method']),
    acknowledgedAt: json['acknowledgedAt'] == null
        ? null
        : _utc(json['acknowledgedAt'] as String),
  );

  @override
  bool operator ==(Object other) =>
      other is VisitSettlement &&
      status == other.status &&
      totalVisitCost == other.totalVisitCost &&
      prepaidAmount == other.prepaidAmount &&
      amountDue == other.amountDue &&
      refundDue == other.refundDue &&
      calculatedAt == other.calculatedAt &&
      method == other.method &&
      acknowledgedAt == other.acknowledgedAt;

  @override
  int get hashCode => Object.hash(
    status,
    totalVisitCost,
    prepaidAmount,
    amountDue,
    refundDue,
    calculatedAt,
    method,
    acknowledgedAt,
  );
}
