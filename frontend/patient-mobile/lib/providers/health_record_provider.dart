import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../models/health_record.dart';
import '../services/health_record_service.dart';

final healthRecordsProvider = FutureProvider.family<List<HealthRecord>, String>((ref, patientId) async {
  final service = ref.read(healthRecordServiceProvider);
  return service.getAll(patientId);
});
