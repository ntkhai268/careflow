import 'package:flutter/material.dart';
import '../config/theme.dart';

/// Appointment model for CareFlow.
/// Maps to AppointmentResponse from the backend API.
class Appointment {
  final String id;
  final String patientId;
  final String? patientName;
  final String department;
  final String departmentDisplayName;
  final String? doctorId;
  final String? doctorName;
  final DateTime appointmentDate;
  final String timeSlot;
  final String status;
  final String statusDisplayName;
  final String? reason;
  final String? notes;
  final String? queueNumber;
  final DateTime? createdAt;
  final DateTime? updatedAt;

  Appointment({
    required this.id,
    required this.patientId,
    this.patientName,
    required this.department,
    required this.departmentDisplayName,
    this.doctorId,
    this.doctorName,
    required this.appointmentDate,
    required this.timeSlot,
    required this.status,
    required this.statusDisplayName,
    this.reason,
    this.notes,
    this.queueNumber,
    this.createdAt,
    this.updatedAt,
  });

  factory Appointment.fromJson(Map<String, dynamic> json) {
    return Appointment(
      id: json['id'] as String,
      patientId: json['patientId'] as String,
      patientName: json['patientName'] as String?,
      department: json['department'] as String,
      departmentDisplayName: json['departmentDisplayName'] as String? ?? '',
      doctorId: json['doctorId'] as String?,
      doctorName: json['doctorName'] as String?,
      appointmentDate: DateTime.parse(json['appointmentDate'] as String),
      timeSlot: json['timeSlot'] as String,
      status: json['status'] as String,
      statusDisplayName: json['statusDisplayName'] as String? ?? '',
      reason: json['reason'] as String?,
      notes: json['notes'] as String?,
      queueNumber: json['queueNumber'] as String?,
      createdAt: json['createdAt'] != null
          ? DateTime.parse(json['createdAt'] as String)
          : null,
      updatedAt: json['updatedAt'] != null
          ? DateTime.parse(json['updatedAt'] as String)
          : null,
    );
  }

  /// Color based on status
  Color get statusColor {
    switch (status) {
      case 'PENDING':
        return AppColors.warning;
      case 'CONFIRMED':
        return AppColors.info;
      case 'CHECKED_IN':
        return AppColors.accent;
      case 'IN_PROGRESS':
        return AppColors.primary;
      case 'COMPLETED':
        return AppColors.success;
      case 'CANCELLED':
        return AppColors.error;
      default:
        return AppColors.textSecondary;
    }
  }

  /// Background color for status chip
  Color get statusBgColor {
    switch (status) {
      case 'PENDING':
        return AppColors.warningLight;
      case 'CONFIRMED':
        return AppColors.infoLight;
      case 'CHECKED_IN':
        return AppColors.accentLight;
      case 'IN_PROGRESS':
        return AppColors.primarySurface;
      case 'COMPLETED':
        return AppColors.successLight;
      case 'CANCELLED':
        return AppColors.errorLight;
      default:
        return AppColors.background;
    }
  }

  /// Icon based on status
  IconData get statusIcon {
    switch (status) {
      case 'PENDING':
        return Icons.schedule_rounded;
      case 'CONFIRMED':
        return Icons.check_circle_outline_rounded;
      case 'CHECKED_IN':
        return Icons.login_rounded;
      case 'IN_PROGRESS':
        return Icons.medical_services_rounded;
      case 'COMPLETED':
        return Icons.task_alt_rounded;
      case 'CANCELLED':
        return Icons.cancel_outlined;
      default:
        return Icons.help_outline_rounded;
    }
  }

  /// Department icon
  static IconData departmentIcon(String dept) {
    switch (dept) {
      case 'NOI_TONG_QUAT':
        return Icons.local_hospital_rounded;
      case 'NHI':
        return Icons.child_care_rounded;
      case 'NGOAI':
        return Icons.healing_rounded;
      case 'SAN':
        return Icons.pregnant_woman_rounded;
      case 'MAT':
        return Icons.visibility_rounded;
      case 'TAI_MUI_HONG':
        return Icons.hearing_rounded;
      case 'RANG_HAM_MAT':
        return Icons.mood_rounded;
      case 'DA_LIEU':
        return Icons.spa_rounded;
      case 'THAN_KINH':
        return Icons.psychology_rounded;
      case 'TIM_MACH':
        return Icons.favorite_rounded;
      case 'CO_XUONG_KHOP':
        return Icons.accessibility_new_rounded;
      default:
        return Icons.medical_services_rounded;
    }
  }
}

/// Department model from API
class Department {
  final String code;
  final String name;

  Department({required this.code, required this.name});

  factory Department.fromJson(Map<String, dynamic> json) {
    return Department(
      code: json['code'] as String,
      name: json['name'] as String,
    );
  }
}
