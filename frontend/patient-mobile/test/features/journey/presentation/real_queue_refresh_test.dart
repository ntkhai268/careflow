import 'package:careflow_patient/features/journey/presentation/clinic_queue_screen.dart';
import 'package:careflow_patient/features/journey/application/journey_providers.dart';
import 'package:careflow_patient/models/queue.dart';
import 'package:careflow_patient/services/queue_service.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

PatientQueueStatus status(String value) => PatientQueueStatus(
  entryId: 'entry',
  patientId: 'patient',
  roomCode: 'ROOM-01',
  queueNumber: 'NOI-001',
  status: value,
  position: null,
  estimatedWaitMinutes: null,
);
void main() {
  for (final item in {
    'CANCELLED': 'Lượt khám đã hủy',
    'WAITING': 'Bạn chưa check-in',
  }.entries) {
    testWidgets('${item.key} never claims the patient is first in queue', (
      tester,
    ) async {
      await tester.pumpWidget(
        ProviderScope(
          overrides: [
            realQueueEnabledProvider.overrideWithValue(true),
            appointmentQueueStatusProvider(
              'appointment',
            ).overrideWith((ref) async => status(item.key)),
          ],
          child: const MaterialApp(
            home: ClinicQueueScreen(appointmentId: 'appointment'),
          ),
        ),
      );
      await tester.pumpAndSettle();
      expect(find.text(item.value), findsOneWidget);
      expect(find.text('Bạn đang ở đầu hàng đợi'), findsNothing);
      await tester.pumpWidget(const SizedBox.shrink());
    });
  }
  testWidgets('resume fetches the same appointment again immediately', (
    tester,
  ) async {
    var calls = 0;
    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          realQueueEnabledProvider.overrideWithValue(true),
          appointmentQueueStatusProvider('appointment').overrideWith((
            ref,
          ) async {
            calls++;
            return status(calls == 1 ? 'CHECKED_IN' : 'CALLED');
          }),
        ],
        child: const MaterialApp(
          home: ClinicQueueScreen(appointmentId: 'appointment'),
        ),
      ),
    );
    await tester.pumpAndSettle();
    expect(calls, 1);
    tester.binding.handleAppLifecycleStateChanged(AppLifecycleState.paused);
    tester.binding.handleAppLifecycleStateChanged(AppLifecycleState.resumed);
    await tester.pumpAndSettle();
    expect(calls, greaterThanOrEqualTo(2));
    expect(find.text('Đã đến lượt bạn'), findsOneWidget);
    await tester.pumpWidget(const SizedBox.shrink());
  });
}
