import 'package:careflow_patient/models/queue.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  test(
    'accepts queue entries that only identify a laboratory service point',
    () {
      final queue = PatientQueueStatus.fromJson({
        'entryId': 'entry-1',
        'patientId': 'patient-1',
        'roomCode': null,
        'servicePointId': 'LAB-HEMATOLOGY-01',
        'queueNumber': 'L-001',
        'queueStatus': 'QUEUED',
        'effectivePosition': 1,
        'estimatedWaitMinutes': 10,
      });

      expect(queue.roomCode, 'LAB-HEMATOLOGY-01');
      expect(queue.servicePointId, 'LAB-HEMATOLOGY-01');
    },
  );
}
