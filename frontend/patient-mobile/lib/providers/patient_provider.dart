import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../models/patient.dart';
import '../services/patient_service.dart';

/// State for patient data
class PatientState {
  final Patient? patient;
  final bool isLoading;
  final String? errorMessage;

  const PatientState({
    this.patient,
    this.isLoading = false,
    this.errorMessage,
  });

  PatientState copyWith({
    Patient? patient,
    bool? isLoading,
    String? errorMessage,
    bool clearPatient = false,
    bool clearError = false,
  }) {
    return PatientState(
      patient: clearPatient ? null : (patient ?? this.patient),
      isLoading: isLoading ?? this.isLoading,
      errorMessage: clearError ? null : (errorMessage ?? this.errorMessage),
    );
  }
}

/// Provider managing patient profile state
class PatientNotifier extends StateNotifier<PatientState> {
  final PatientService _service;

  // TODO: Replace with actual userId from Auth when available
  static const String _tempUserId = '550e8400-e29b-41d4-a716-446655440000';

  PatientNotifier(this._service) : super(const PatientState());

  String get currentUserId => _tempUserId;

  /// Load patient profile for current user
  Future<void> loadPatient() async {
    state = state.copyWith(isLoading: true, clearError: true);
    try {
      final patient = await _service.getPatientByUserId(_tempUserId);
      state = PatientState(patient: patient, isLoading: false);
    } catch (e) {
      state = state.copyWith(
        isLoading: false,
        errorMessage: 'Không thể tải hồ sơ: ${e.toString()}',
      );
    }
  }

  /// Create a new patient profile
  Future<bool> createPatient(Map<String, dynamic> data) async {
    state = state.copyWith(isLoading: true, clearError: true);
    try {
      data['userId'] = _tempUserId;
      final patient = await _service.createPatient(data);
      state = PatientState(patient: patient, isLoading: false);
      return true;
    } catch (e) {
      state = state.copyWith(
        isLoading: false,
        errorMessage: 'Không thể tạo hồ sơ: ${_parseError(e)}',
      );
      return false;
    }
  }

  /// Update patient profile
  Future<bool> updatePatient(String id, Map<String, dynamic> data) async {
    state = state.copyWith(isLoading: true, clearError: true);
    try {
      final patient = await _service.updatePatient(id, data);
      state = PatientState(patient: patient, isLoading: false);
      return true;
    } catch (e) {
      state = state.copyWith(
        isLoading: false,
        errorMessage: 'Không thể cập nhật hồ sơ: ${_parseError(e)}',
      );
      return false;
    }
  }

  /// Delete patient profile
  Future<bool> deletePatient(String id) async {
    state = state.copyWith(isLoading: true, clearError: true);
    try {
      await _service.deletePatient(id);
      state = const PatientState(isLoading: false);
      return true;
    } catch (e) {
      state = state.copyWith(
        isLoading: false,
        errorMessage: 'Không thể xóa hồ sơ: ${_parseError(e)}',
      );
      return false;
    }
  }

  String _parseError(dynamic e) {
    if (e.toString().contains('Connection refused') ||
        e.toString().contains('SocketException')) {
      return 'Không thể kết nối server';
    }
    return e.toString();
  }
}

/// Main provider for patient state
final patientProvider =
    StateNotifierProvider<PatientNotifier, PatientState>((ref) {
  final service = ref.watch(patientServiceProvider);
  return PatientNotifier(service);
});
