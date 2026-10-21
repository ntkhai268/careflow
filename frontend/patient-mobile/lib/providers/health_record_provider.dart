import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../models/health_record.dart';
import '../services/health_record_service.dart';
import 'auth_provider.dart';
import '../models/patient.dart';
import '../services/patient_service.dart';

final healthRecordsProvider = FutureProvider.autoDispose
    .family<List<HealthRecord>, String>((ref, patientId) async {
      final scope = ref.watch(
        authProvider.select((auth) => (auth.status, auth.userId)),
      );
      if (scope.$1 != AuthStatus.authenticated || scope.$2 == null) {
        return const [];
      }
      return ref.watch(healthRecordServiceProvider).getAll(patientId);
    });

final healthRecordPatientProvider = FutureProvider.autoDispose
    .family<Patient?, String>((ref, id) async {
      final scope = ref.watch(
        authProvider.select((auth) => (auth.status, auth.userId)),
      );
      if (scope.$1 != AuthStatus.authenticated || scope.$2 == null) return null;
      final patient = await ref
          .watch(patientServiceProvider)
          .getPatientById(id);
      if (patient.id != id || patient.userId != scope.$2) {
        throw const FormatException(
          'Hồ sơ không thuộc tài khoản đang đăng nhập.',
        );
      }
      return patient;
    });
