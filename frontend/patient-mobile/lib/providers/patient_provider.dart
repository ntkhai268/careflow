import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../models/patient.dart';
import '../providers/auth_provider.dart';
import '../services/patient_service.dart';
import '../utils/api_error_message.dart';

/// State for patient data
class PatientState {
  final Patient? patient;
  final bool isLoading;
  final String? errorMessage;

  const PatientState({this.patient, this.isLoading = false, this.errorMessage});

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

  int _operationGeneration = 0;

  /// Get the authenticated user's userId from AuthProvider.
  String? get _userId {
    final auth = _ref.read(authProvider);
    return auth.status == AuthStatus.authenticated ? auth.userId : null;
  }

  void invalidateAccountScope() {
    ++_operationGeneration;
    state = const PatientState();
  }

  /// Load patient profile for the currently authenticated user.
  Future<void> loadPatient() async {
    final userId = _userId;
    if (userId == null || userId.isEmpty) {
      invalidateAccountScope();
      state = const PatientState(
        errorMessage: 'Chưa đăng nhập. Vui lòng đăng nhập lại.',
      );
      return;
    }

    if (state.isLoading) return;

    final generation = ++_operationGeneration;
    state = const PatientState(isLoading: true);
    try {
      final patient = await _service.getPatientByUserId(userId);
      if (generation != _operationGeneration || _userId != userId) return;
      state = PatientState(patient: patient, isLoading: false);
    } catch (e) {
      if (generation != _operationGeneration || _userId != userId) return;
      state = PatientState(
        isLoading: false,
        errorMessage: _parseError(
          e,
          fallback: 'Không thể tải hồ sơ bệnh nhân. Vui lòng thử lại.',
        ),
      );
    }
  }

  /// Create a new patient profile linked to the current user.
  Future<bool> createPatient(Map<String, dynamic> data) async {
    final userId = _userId;
    if (userId == null || userId.isEmpty) return false;

    final generation = ++_operationGeneration;
    state = const PatientState(isLoading: true);
    try {
      data['userId'] = userId;
      final patient = await _service.createPatient(data);
      if (generation != _operationGeneration || _userId != userId) {
        return false;
      }
      state = PatientState(patient: patient, isLoading: false);
      return true;
    } catch (e) {
      if (generation != _operationGeneration || _userId != userId) {
        return false;
      }
      state = PatientState(
        isLoading: false,
        errorMessage: _parseError(
          e,
          fallback: 'Không thể tạo hồ sơ bệnh nhân. Vui lòng thử lại.',
        ),
      );
      return false;
    }
  }

  /// Update patient profile
  Future<bool> updatePatient(String id, Map<String, dynamic> data) async {
    final userId = _userId;
    if (userId == null || userId.isEmpty) return false;
    final generation = ++_operationGeneration;
    state = const PatientState(isLoading: true);
    try {
      final patient = await _service.updatePatient(id, data);
      if (generation != _operationGeneration || _userId != userId) {
        return false;
      }
      state = PatientState(patient: patient, isLoading: false);
      return true;
    } catch (e) {
      if (generation != _operationGeneration || _userId != userId) {
        return false;
      }
      state = PatientState(
        isLoading: false,
        errorMessage: _parseError(
          e,
          fallback: 'Không thể cập nhật hồ sơ bệnh nhân. Vui lòng thử lại.',
        ),
      );
      return false;
    }
  }

  /// Delete patient profile
  Future<bool> deletePatient(String id) async {
    final userId = _userId;
    if (userId == null || userId.isEmpty) return false;
    final generation = ++_operationGeneration;
    state = const PatientState(isLoading: true);
    try {
      await _service.deletePatient(id);
      if (generation != _operationGeneration || _userId != userId) {
        return false;
      }
      state = const PatientState(isLoading: false);
      return true;
    } catch (e) {
      if (generation != _operationGeneration || _userId != userId) {
        return false;
      }
      state = PatientState(
        isLoading: false,
        errorMessage: _parseError(
          e,
          fallback: 'Không thể xóa hồ sơ bệnh nhân. Vui lòng thử lại.',
        ),
      );
      return false;
    }
  }

  String _parseError(Object error, {required String fallback}) {
    return ApiErrorMessage.from(error, fallback: fallback);
  }
}

/// Main provider for patient state
final patientProvider = StateNotifierProvider<PatientNotifier, PatientState>((
  ref,
) {
  final service = ref.watch(patientServiceProvider);
  final notifier = PatientNotifier(service, ref);
  ref.listen(authProvider.select((auth) => (auth.status, auth.userId)), (
    previous,
    next,
  ) {
    if (previous != next) notifier.invalidateAccountScope();
  });
  return notifier;
});
