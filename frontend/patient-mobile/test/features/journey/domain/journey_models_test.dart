import 'package:careflow_patient/features/journey/domain/journey_models.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  test('round-trips explicit hospital and specialty ticket metadata', () {
    const ticket = VisitTicket(
      code: 'CF-APT-1',
      qrPayload: 'careflow://visit/apt-1',
      queueNumber: '42',
      hospitalName: 'Bệnh viện Minh Khai',
      specialtyName: 'Nội thần kinh',
      room: 'Phòng khám 21',
      expectedWindow: '10:30 - 11:30',
    );

    expect(ticket.toJson()['hospitalName'], 'Bệnh viện Minh Khai');
    expect(ticket.toJson()['specialtyName'], 'Nội thần kinh');
    expect(VisitTicket.fromJson(ticket.toJson()), ticket);
  });

  test(
    'round-trips a laboratory patient journey without losing UTC instants',
    () {
      final journey = PatientJourney(
        appointmentId: 'apt-47',
        patientId: 'patient-1',
        status: JourneyStatus.waitingLab,
        doctorName: 'BS. Nguyễn Minh Anh (dữ liệu mô phỏng)',
        ticket: const VisitTicket(
          code: 'CF-APT-47',
          qrPayload: 'careflow://visit/apt-47',
          queueNumber: '47',
          hospitalName: 'Bệnh viện CareFlow (dữ liệu mô phỏng)',
          specialtyName: 'Nội tổng quát',
          room: 'Phòng 21 - Lầu 1 khu A',
          expectedWindow: '10:30 - 11:30',
        ),
        clinicQueue: const QueueSnapshot(
          room: 'Phòng 21 - Lầu 1 khu A',
          peopleAhead: 3,
          expectedWait: '15 phút',
        ),
        laboratoryOrders: [
          LaboratoryOrder(
            id: 'lab-1',
            name: 'Xét nghiệm công thức máu',
            department: 'Khoa Xét nghiệm',
            destination: 'Phòng xét nghiệm tầng 1',
            preparationNote: 'Nhịn ăn 8 giờ',
            price: 120000,
            result: LaboratoryResult(
              id: 'result-1',
              value: '5.2',
              unit: 'G/L',
              referenceRange: '4.0 - 10.0',
              source: 'Dữ liệu mô phỏng',
              reportedAt: DateTime.parse('2026-08-18T04:00:00+07:00'),
            ),
          ),
        ],
        payment: VisitPayment(
          method: PaymentMethod.insurance,
          amount: 120000,
          acknowledgedAt: DateTime.parse('2026-08-18T03:45:00Z'),
        ),
        resultReviewQueue: const QueueSnapshot(
          room: 'Phòng 21 - Lầu 1 khu A',
          peopleAhead: 1,
          expectedWait: 'Sau bệnh nhân khám mới tiếp theo',
        ),
        diagnosis: const DiagnosisSummary(
          title: 'Viêm họng cấp',
          detail: 'Theo dõi tại nhà',
        ),
        prescription: Prescription(
          id: 'rx-1',
          issuedAt: DateTime.parse('2026-08-18T04:30:00Z'),
          items: const [
            PrescriptionItem(
              medicationName: 'Paracetamol',
              dosage: '500 mg',
              route: 'Uống',
              frequency: 'Khi sốt',
              duration: '3 ngày',
              caution: 'Không dùng quá liều',
            ),
          ],
        ),
        followUp: FollowUpAppointment(
          scheduledAt: DateTime.parse('2026-08-25T02:00:00Z'),
          room: 'Phòng 21',
          note: 'Tái khám nếu không đỡ',
        ),
        timeline: [
          JourneyTimelineEvent(
            id: 'event-1',
            title: 'Đã thanh toán',
            detail: 'Bảo hiểm đã được ghi nhận',
            occurredAt: DateTime.parse('2026-08-18T03:45:00Z'),
          ),
        ],
        notifications: [
          PatientNotification(
            id: 'notification-1',
            title: 'Chờ xét nghiệm',
            body: 'Vui lòng đến Phòng xét nghiệm tầng 1',
            createdAt: DateTime.parse('2026-08-18T03:46:00Z'),
            isRead: false,
            actionType: 'OPEN_LAB_ORDER',
            resourceId: 'lab-order-1',
          ),
        ],
        updatedAt: DateTime.parse('2026-08-18T03:30:00Z'),
      );

      expect(PatientJourney.fromJson(journey.toJson()), journey);
      expect(journey.laboratoryOrders.single.result!.reportedAt.isUtc, isTrue);
      expect(
        journey.laboratoryOrders.single.result!.reportedAt,
        DateTime.parse('2026-08-17T21:00:00Z'),
      );
    },
  );

  test(
    'keeps omitted optional outcome fields null through JSON and copyWith',
    () {
      final journey = PatientJourney(
        appointmentId: 'apt-48',
        patientId: 'patient-2',
        status: JourneyStatus.booked,
        laboratoryOrders: const [],
        timeline: const [],
        notifications: const [],
        updatedAt: DateTime.parse('2026-08-18T03:30:00Z'),
      );

      final restored = PatientJourney.fromJson(journey.toJson());

      expect(restored.diagnosis, isNull);
      expect(restored.prescription, isNull);
      expect(restored.followUp, isNull);
      expect(
        journey
            .copyWith(
              diagnosis: const DiagnosisSummary(
                title: 'Tạm thời',
                detail: 'Tạm thời',
              ),
            )
            .copyWith(diagnosis: null),
        journey,
      );
    },
  );
}
