import 'package:careflow_patient/models/appointment_payment.dart';
import 'package:careflow_patient/services/appointment_payment_store.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  test('stores and loads an online appointment payment receipt', () async {
    final adapter = MemoryPaymentAdapter();
    final store = SharedPreferencesAppointmentPaymentStore(
      persistence: Future.value(adapter),
    );
    final receipt = AppointmentPaymentReceipt(
      appointmentId: 'appointment-1',
      serviceCode: 'GENERAL_CONSULTATION',
      serviceName: 'Khám thường',
      amount: 150000,
      method: AppointmentPaymentMethod.onlineMock,
      status: AppointmentPaymentStatus.paid,
      createdAt: DateTime.utc(2026, 8, 8, 8),
    );

    await store.save(receipt);

    expect(await store.load('appointment-1'), receipt);
    expect(
      adapter.values[SharedPreferencesAppointmentPaymentStore.storageKey(
        'appointment-1',
      )],
      isNotNull,
    );
  });

  test(
    'reads legacy cash receipts without offering cash in new booking UI',
    () async {
      final adapter = MemoryPaymentAdapter();
      final store = SharedPreferencesAppointmentPaymentStore(
        persistence: Future.value(adapter),
      );
      adapter.values[SharedPreferencesAppointmentPaymentStore.storageKey(
            'appointment-2',
          )] =
          '{"appointmentId":"appointment-2","serviceCode":"GENERAL_CONSULTATION",'
          '"serviceName":"Khám thường","amount":150000,'
          '"method":"CASH_AT_HOSPITAL","status":"DUE_AT_HOSPITAL",'
          '"createdAt":"2026-08-08T08:00:00.000Z"}';

      final receipt = await store.load('appointment-2');

      expect(receipt?.method, AppointmentPaymentMethod.cashAtHospital);
      expect(receipt?.status, AppointmentPaymentStatus.dueAtHospital);
    },
  );
}

class MemoryPaymentAdapter implements AppointmentPaymentPersistenceAdapter {
  final values = <String, String>{};

  @override
  String? getString(String key) => values[key];

  @override
  Future<bool> remove(String key) async => values.remove(key) != null;

  @override
  Future<bool> setString(String key, String value) async {
    values[key] = value;
    return true;
  }
}
