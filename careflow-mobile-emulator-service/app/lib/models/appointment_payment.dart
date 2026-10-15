import 'package:flutter/foundation.dart';

enum AppointmentPaymentMethod { onlineMock, cashAtHospital }

extension AppointmentPaymentMethodContract on AppointmentPaymentMethod {
  String get wireValue => switch (this) {
    AppointmentPaymentMethod.onlineMock => 'ONLINE_MOCK',
    AppointmentPaymentMethod.cashAtHospital => 'CASH_AT_HOSPITAL',
  };

  String get displayName => switch (this) {
    AppointmentPaymentMethod.onlineMock => 'Thanh toán trực tuyến',
    AppointmentPaymentMethod.cashAtHospital => 'Tiền mặt tại bệnh viện',
  };
}

enum AppointmentPaymentStatus { paid, dueAtHospital }

extension AppointmentPaymentStatusPresentation on AppointmentPaymentStatus {
  String get wireValue => switch (this) {
    AppointmentPaymentStatus.paid => 'PAID',
    AppointmentPaymentStatus.dueAtHospital => 'DUE_AT_HOSPITAL',
  };

  String get displayName => switch (this) {
    AppointmentPaymentStatus.paid => 'Đã thanh toán',
    AppointmentPaymentStatus.dueAtHospital => 'Thanh toán tại bệnh viện',
  };
}

@immutable
class AppointmentPaymentReceipt {
  const AppointmentPaymentReceipt({
    required this.appointmentId,
    required this.serviceCode,
    required this.serviceName,
    required this.amount,
    required this.method,
    required this.status,
    required this.createdAt,
  });

  final String appointmentId;
  final String serviceCode;
  final String serviceName;
  final int amount;
  final AppointmentPaymentMethod method;
  final AppointmentPaymentStatus status;
  final DateTime createdAt;

  bool get isPaid => status == AppointmentPaymentStatus.paid;

  Map<String, dynamic> toJson() => {
    'appointmentId': appointmentId,
    'serviceCode': serviceCode,
    'serviceName': serviceName,
    'amount': amount,
    'method': method.wireValue,
    'status': status.wireValue,
    'createdAt': createdAt.toUtc().toIso8601String(),
  };

  factory AppointmentPaymentReceipt.fromJson(Map<String, dynamic> json) {
    final method = switch (json['method']?.toString().toUpperCase()) {
      'ONLINE_MOCK' || 'ONLINE' => AppointmentPaymentMethod.onlineMock,
      'CASH_AT_HOSPITAL' || 'CASH' => AppointmentPaymentMethod.cashAtHospital,
      _ => throw const FormatException(
        'Unsupported appointment payment method',
      ),
    };
    final status = switch (json['status']?.toString().toUpperCase()) {
      'PAID' => AppointmentPaymentStatus.paid,
      'DUE_AT_HOSPITAL' => AppointmentPaymentStatus.dueAtHospital,
      _ => throw const FormatException(
        'Unsupported appointment payment status',
      ),
    };
    return AppointmentPaymentReceipt(
      appointmentId: json['appointmentId'] as String,
      serviceCode: json['serviceCode'] as String,
      serviceName: json['serviceName'] as String,
      amount: (json['amount'] as num).toInt(),
      method: method,
      status: status,
      createdAt: DateTime.parse(json['createdAt'] as String).toUtc(),
    );
  }

  @override
  bool operator ==(Object other) =>
      other is AppointmentPaymentReceipt &&
      appointmentId == other.appointmentId &&
      serviceCode == other.serviceCode &&
      serviceName == other.serviceName &&
      amount == other.amount &&
      method == other.method &&
      status == other.status &&
      createdAt == other.createdAt;

  @override
  int get hashCode => Object.hash(
    appointmentId,
    serviceCode,
    serviceName,
    amount,
    method,
    status,
    createdAt,
  );
}
