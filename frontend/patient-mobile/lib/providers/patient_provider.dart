import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../models/patient.dart';
import '../providers/auth_provider.dart';
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

/// Provider managing patient profile state.
/// Uses the authenticated user's real userId from authProvider.
class PatientNotifier extends StateNotifier<PatientState> {
  final PatientService _service;
  final Ref _ref;

  PatientNotifier(this._service, this._ref) : super(const PatientState());

  /// Get the authenticated user's userId from AuthProvider.
  String? get _userId => _ref.read(authProvider).userId;

  /// Load patient profile for the currently authenticated user.
  Future<void> loadPatient() async {
    final userId = _userId;
    if (userId == null || userId.isEmpty) {
      state = state.copyWith(
        isLoading: false,
        errorMessage: 'Chưa đăng nhập. Vui lòng đăng nhập lại.',
      );
      return;
    }

    state = state.copyWith(isLoading: true, clearError: true);
    try {
      final patient = await _service.getPatientByUserId(userId);
      state = PatientState(patient: patient, isLoading: false);
    } catch (e) {
      state = state.copyWith(
        isLoading: false,
        errorMessage: 'Không thể tải hồ sơ: ${_parseError(e)}',
      );
    }
  }

  /// Create a new patient profile linked to the current user.
  Future<bool> createPatient(Map<String, dynamic> data) async {
    final userId = _userId;
    if (userId == null || userId.isEmpty) return false;

    state = state.copyWith(isLoading: true, clearError: true);
    try {
      data['userId'] = userId;
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
  return PatientNotifier(service, ref);
});
