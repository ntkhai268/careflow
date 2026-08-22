import 'package:careflow_patient/features/journey/data/backend_journey_repository.dart';
import 'package:careflow_patient/features/journey/domain/journey_models.dart';
import 'package:careflow_patient/models/appointment.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  final mapper = BackendJourneyMapper(now: () => DateTime.utc(2026, 8, 18, 10));

  test('maps booked appointment and visit ticket without clinical data', () {
    final journey = mapper.mapResources(
      BackendJourneyResources(
        appointment: appointment(status: 'CONFIRMED'),
        patientId: 'patient-1',
        ticket: {
          'ticketCode': 'CF-001',
          'qrToken': 'qr-token',
          'queueNumber': 12,
          'departmentDisplayName': 'Nội tổng quát',
          'roomDisplayName': 'Phòng 21',
          'timeSlot': '10:30-11:00',
        },
      ),
    );

    expect(journey.status, JourneyStatus.ticketIssued);
    expect(journey.ticket!.code, 'CF-001');
    expect(journey.ticket!.queueNumber, '12');
    expect(journey.ticket!.specialtyName, 'Nội tổng quát');
    expect(journey.diagnosis, isNull);
    expect(journey.prescription, isNull);
    expect(journey.followUp, isNull);
  });

  test('maps clinic queue aliases into waiting and called states', () {
    final waiting = mapper.mapResources(
      BackendJourneyResources(
        appointment: appointment(status: 'CHECKED_IN'),
        patientId: 'patient-1',
        ticket: ticket(),
        clinicQueue: {
          'queueStatus': 'QUEUED',
          'effectivePosition': 4,
          'estimatedWaitMinutes': 20,
          'roomCode': 'P21',
        },
      ),
    );
    final called = mapper.mapResources(
      BackendJourneyResources(
        appointment: appointment(status: 'CHECKED_IN'),
        patientId: 'patient-1',
        ticket: ticket(),
        clinicQueue: {'status': 'CALLED', 'roomCode': 'P21'},
      ),
    );

    expect(waiting.status, JourneyStatus.waiting);
    expect(waiting.clinicQueue!.peopleAhead, 3);
    expect(waiting.clinicQueue!.expectedWait, '20 phút');
    expect(called.status, JourneyStatus.called);
  });

  test('keeps a not-yet-checked-in ticket out of the clinic waiting state', () {
    final journey = mapper.mapResources(
      BackendJourneyResources(
        appointment: appointment(status: 'CONFIRMED'),
        patientId: 'patient-1',
        ticket: {...ticket(), 'status': 'TICKET_ISSUED'},
        clinicQueue: {
          'queueStatus': 'WAITING',
          'effectivePosition': 1,
          'estimatedWaitMinutes': 10,
          'roomCode': 'P21',
        },
      ),
    );

    expect(journey.status, JourneyStatus.ticketIssued);
  });

  test('maps waiting lab state from consultation and paid lab order', () {
    final journey = mapper.mapResources(
      BackendJourneyResources(
        appointment: appointment(status: 'IN_PROGRESS'),
        patientId: 'patient-1',
        ticket: ticket(),
        consultation: consultation(status: 'AWAITING_CLS'),
        labOrders: [
          labOrder(
            status: 'QUEUED',
            paymentStatus: 'PAID_CASH',
            items: [
              labItem(
                status: 'ORDERED',
                serviceName: 'Công thức máu',
                servicePointId: 'LAB-1',
              ),
            ],
          ),
        ],
      ),
    );

    expect(journey.status, JourneyStatus.waitingLab);
    expect(journey.laboratoryOrders.single.name, 'Công thức máu');
    expect(journey.laboratoryOrders.single.result, isNull);
    expect(journey.payment, isNull);
  });

  test('maps a lab queue without a room code to its service point', () {
    final journey = mapper.mapResources(
      BackendJourneyResources(
        appointment: appointment(status: 'IN_PROGRESS'),
        patientId: 'patient-1',
        ticket: ticket(),
        consultation: consultation(status: 'AWAITING_CLS'),
        clinicQueue: {
          'queueStatus': 'QUEUED',
          'servicePointId': 'LAB-HEMATOLOGY-01',
        },
        labOrders: [
          labOrder(
            status: 'ORDERED',
            items: [
              labItem(
                status: 'ORDERED',
                serviceName: 'Công thức máu',
                servicePointId: 'LAB-HEMATOLOGY-01',
              ),
            ],
          ),
        ],
      ),
    );

    expect(journey.clinicQueue!.room, 'LAB-HEMATOLOGY-01');
    expect(journey.status, JourneyStatus.labOrdered);
  });

  test('maps only released lab results and result review queue', () {
    final journey = mapper.mapResources(
      BackendJourneyResources(
        appointment: appointment(status: 'IN_PROGRESS'),
        patientId: 'patient-1',
        ticket: ticket(),
        consultation: consultation(status: 'AWAITING_REVIEW'),
        labOrders: [
          labOrder(
            status: 'RESULT_AVAILABLE',
            items: [
              labItem(
                status: 'COMPLETED',
                serviceName: 'Glucose',
                resultValue: '5.2',
                unit: 'mmol/L',
                referenceRange: '3.9 - 6.4',
                performedAt: '2026-08-18T03:15:00Z',
              ),
              labItem(
                status: 'ORDERED',
                serviceName: 'CRP',
                resultValue: 'hidden',
              ),
            ],
          ),
        ],
        resultReviewQueue: {
          'queueStatus': 'WAITING',
          'effectivePosition': 2,
          'estimatedWaitMinutes': 8,
          'roomCode': 'P21',
        },
      ),
    );

    expect(journey.status, JourneyStatus.waitingResultReview);
    expect(journey.laboratoryOrders, hasLength(2));
    expect(journey.laboratoryOrders.first.result!.value, '5.2');
    expect(journey.laboratoryOrders.first.result!.unit, 'mmol/L');
    expect(journey.laboratoryOrders.last.result, isNull);
    expect(journey.resultReviewQueue!.peopleAhead, 1);
  });

  test('maps completed outcome with diagnosis prescription and follow-up', () {
    final journey = mapper.mapResources(
      BackendJourneyResources(
        appointment: appointment(status: 'COMPLETED'),
        patientId: 'patient-1',
        ticket: ticket(),
        consultation: consultation(
          status: 'COMPLETED',
          icd10Code: 'J00',
          icd10Name: 'Viêm mũi họng cấp',
          diagnosis: 'Theo dõi tại nhà',
          completedAt: '2026-08-18T04:00:00Z',
        ),
        prescriptions: [
          {
            'id': 'rx-1',
            'status': 'CONFIRMED',
            'diagnosis': 'Viêm mũi họng cấp',
            'notes': 'Tái khám nếu sốt lại',
            'followUpDate': '2026-08-25',
            'createdAt': '2026-08-18T04:05:00',
            'items': [
              {
                'medicineName': 'Paracetamol 500 mg',
                'dosage': '1 viên',
                'frequency': '3 lần/ngày',
                'timing': 'Sau ăn',
                'duration': 5,
                'notes': 'Không dùng quá liều',
              },
            ],
          },
        ],
      ),
    );

    expect(journey.status, JourneyStatus.completed);
    expect(journey.diagnosis!.title, 'J00 - Viêm mũi họng cấp');
    expect(journey.diagnosis!.detail, 'Theo dõi tại nhà');
    expect(
      journey.prescription!.items.single.medicationName,
      'Paracetamol 500 mg',
    );
    expect(journey.prescription!.items.single.route, 'Sau ăn');
    expect(journey.prescription!.items.single.duration, '5 ngày');
    expect(journey.followUp!.scheduledAt, DateTime.utc(2026, 8, 25));
    expect(journey.followUp!.note, 'Tái khám nếu sốt lại');
  });

  test('maps a follow-up appointment linked to the consultation', () {
    final journey = mapper.mapResources(
      BackendJourneyResources(
        appointment: appointment(status: 'COMPLETED'),
        patientId: 'patient-1',
        consultation: consultation(status: 'COMPLETED'),
        patientAppointments: [
          Appointment(
            id: 'follow-up-1',
            patientId: 'patient-1',
            department: 'NOI_TONG_QUAT',
            departmentDisplayName: 'Nội tổng quát',
            roomDisplayName: 'Phòng 01',
            appointmentDate: DateTime.utc(2026, 8, 25),
            timeSlot: '09:00-09:30',
            status: 'CONFIRMED',
            statusDisplayName: 'Đã xác nhận',
            reason: 'Tái khám sau khi xem kết quả',
            sourceConsultationId: 'consult-1',
          ),
        ],
      ),
    );

    expect(journey.followUp!.scheduledAt, DateTime.utc(2026, 8, 25));
    expect(journey.followUp!.room, 'Phòng 01');
    expect(journey.followUp!.note, 'Tái khám sau khi xem kết quả');
  });

  test(
    'maps notification read state and rejects patient ownership mismatch',
    () {
      final journey = mapper.mapResources(
        BackendJourneyResources(
          appointment: appointment(status: 'CONFIRMED'),
          patientId: 'patient-1',
          ticket: ticket(),
          notifications: [
            {
              'id': 'n-1',
              'title': 'Đã có kết quả xét nghiệm',
              'body': 'Vui lòng quay lại phòng khám',
              'status': 'READ',
              'action': {
                'type': 'OPEN_RESULT_REVIEW',
                'resourceId': 'consult-1',
              },
              'createdAt': '2026-08-18T03:30:00Z',
            },
          ],
        ),
      );

      expect(journey.notifications.single.isRead, isTrue);
      expect(journey.notifications.single.actionType, 'OPEN_RESULT_REVIEW');
      expect(journey.notifications.single.resourceId, 'consult-1');
      expect(
        () => mapper.mapResources(
          BackendJourneyResources(
            appointment: appointment(patientId: 'other-patient'),
            patientId: 'patient-1',
          ),
        ),
        throwsA(isA<JourneyOwnershipMismatch>()),
      );
    },
  );
}

Appointment appointment({
  String patientId = 'patient-1',
  String status = 'CONFIRMED',
  String? sourceConsultationId,
}) => Appointment(
  id: 'apt-1',
  patientId: patientId,
  department: 'NOI_TONG_QUAT',
  departmentDisplayName: 'Nội tổng quát',
  appointmentDate: DateTime.utc(2026, 8, 18),
  timeSlot: '10:30-11:00',
  status: status,
  statusDisplayName: status,
  doctorName: 'BS. Minh Anh',
  sourceConsultationId: sourceConsultationId,
);

Map<String, dynamic> ticket() => {
  'ticketCode': 'CF-001',
  'qrToken': 'qr-token',
  'queueNumber': '12',
  'departmentDisplayName': 'Nội tổng quát',
  'roomDisplayName': 'Phòng 21',
  'timeSlot': '10:30-11:00',
};

Map<String, dynamic> consultation({
  required String status,
  String? icd10Code,
  String? icd10Name,
  String? diagnosis,
  String? completedAt,
}) {
  final value = <String, dynamic>{
    'id': 'consult-1',
    'appointmentId': 'apt-1',
    'patientId': 'patient-1',
    'doctorName': 'BS. Minh Anh',
    'status': status,
  };
  if (icd10Code != null) value['icd10Code'] = icd10Code;
  if (icd10Name != null) value['icd10Name'] = icd10Name;
  if (diagnosis != null) value['diagnosis'] = diagnosis;
  if (completedAt != null) value['completedAt'] = completedAt;
  return value;
}

Map<String, dynamic> labOrder({
  required String status,
  String paymentStatus = 'NOT_REQUIRED',
  required List<Map<String, dynamic>> items,
}) => {
  'id': 'lab-order-1',
  'status': status,
  'paymentStatus': paymentStatus,
  'items': items,
};

Map<String, dynamic> labItem({
  required String status,
  required String serviceName,
  String? servicePointId,
  String? resultValue,
  String? unit,
  String? referenceRange,
  String? performedAt,
}) {
  final value = <String, dynamic>{
    'id': 'lab-item-$serviceName',
    'status': status,
    'serviceName': serviceName,
  };
  if (servicePointId != null) value['servicePointId'] = servicePointId;
  if (resultValue != null) value['resultValue'] = resultValue;
  if (unit != null) value['unit'] = unit;
  if (referenceRange != null) value['referenceRange'] = referenceRange;
  if (performedAt != null) value['performedAt'] = performedAt;
  return value;
}
