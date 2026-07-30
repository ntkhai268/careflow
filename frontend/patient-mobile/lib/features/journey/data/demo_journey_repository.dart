import '../../../models/appointment.dart';
import '../domain/journey_models.dart';
import '../domain/journey_transition.dart';
import 'journey_repository.dart';
import 'journey_store.dart';

class DemoJourneyRepository implements JourneyRepository {
  DemoJourneyRepository({required JourneyStore store, DateTime Function()? now})
    : _store = store,
      _now = now ?? DateTime.now;

  final JourneyStore _store;
  final DateTime Function() _now;
  static const _demoDoctorName = 'BS. Nguyễn Minh Anh (dữ liệu mô phỏng)';

  @override
  Future<PatientJourney> bootstrap({
    required Appointment appointment,
    required String patientId,
  }) async {
    final cached = await _store.load(patientId, appointment.id);
    if (cached.journey != null) return cached.journey!;

    final suffix = appointment.id.codeUnits.fold<int>(0, (a, b) => a + b);
    final createdAt = _timestamp();
    final initial = PatientJourney(
      appointmentId: appointment.id,
      patientId: patientId,
      status: JourneyStatus.booked,
      doctorName: appointment.doctorName ?? _demoDoctorName,
      ticket: VisitTicket(
        code: 'CF-${appointment.id.toUpperCase()}',
        qrPayload: 'careflow://visit/${appointment.id}',
        queueNumber: '${40 + (suffix % 20)}',
        hospitalName: 'Bệnh viện CareFlow (dữ liệu mô phỏng)',
        specialtyName: appointment.departmentDisplayName,
        room: _roomFor(appointment),
        expectedWindow: appointment.timeSlot,
      ),
      laboratoryOrders: const [],
      timeline: const [],
      notifications: const [],
      updatedAt: createdAt,
    );
    final journey = JourneyTransition.apply(
      initial,
      JourneyEvent.issueTicket,
      now: createdAt,
    );
    await _store.save(journey);
    return journey;
  }

  @override
  Future<PatientJourney> advance(
    PatientJourney journey,
    JourneyEvent event,
  ) async {
    var next = JourneyTransition.apply(journey, event, now: _timestamp());
    switch (event) {
      case JourneyEvent.admittedToClinicQueue:
        next = next.copyWith(clinicQueue: _clinicQueue(next));
      case JourneyEvent.laboratoryOrdered:
        next = next.copyWith(laboratoryOrders: _laboratoryOrders(next));
      case JourneyEvent.laboratoryResultsPublished:
        next = next.copyWith(
          laboratoryOrders: next.laboratoryOrders.map(_withDemoResult).toList(),
        );
      case JourneyEvent.admittedToResultReviewQueue:
        next = next.copyWith(resultReviewQueue: _resultReviewQueue(next));
      default:
        break;
    }
    await _store.save(next);
    return next;
  }

  @override
  Future<PatientJourney> acknowledgePayment(
    PatientJourney journey,
    PaymentMethod method,
  ) async {
    final next =
        JourneyTransition.apply(
          journey,
          JourneyEvent.paymentAcknowledged,
          now: _timestamp(),
        ).copyWith(
          payment: VisitPayment(
            method: method,
            amount: journey.laboratoryOrders.fold(
              0,
              (sum, order) => sum + order.price,
            ),
            acknowledgedAt: _timestamp(),
          ),
        );
    await _store.save(next);
    return next;
  }

  @override
  Future<PatientJourney> markNotificationRead(
    PatientJourney journey,
    String notificationId,
  ) async {
    final next = journey.copyWith(
      notifications: journey.notifications
          .map(
            (notification) => notification.id == notificationId
                ? notification.copyWith(isRead: true)
                : notification,
          )
          .toList(),
      updatedAt: _timestamp(),
    );
    await _store.save(next);
    return next;
  }

  @override
  Future<void> reset(PatientJourney journey) =>
      _store.delete(journey.patientId, journey.appointmentId);

  DateTime _timestamp() => _now().toUtc();

  String _roomFor(Appointment appointment) =>
      '${appointment.departmentDisplayName} - Phòng 21';

  QueueSnapshot _clinicQueue(PatientJourney journey) => QueueSnapshot(
    room: journey.ticket!.room,
    peopleAhead: 3,
    expectedWait: '15 phút',
  );

  List<LaboratoryOrder> _laboratoryOrders(PatientJourney journey) => [
    LaboratoryOrder(
      id: '${journey.appointmentId}-cbc',
      name: 'Xét nghiệm công thức máu',
      department: 'Khoa Xét nghiệm',
      destination: 'Phòng xét nghiệm tầng 1',
      preparationNote: 'Nhịn ăn 8 giờ nếu được bác sĩ dặn',
      price: 120000,
    ),
    LaboratoryOrder(
      id: '${journey.appointmentId}-xray',
      name: 'X-quang ngực thẳng',
      department: 'Khoa Chẩn đoán hình ảnh',
      destination: 'Phòng X-quang tầng 1',
      preparationNote: 'Tháo vật dụng kim loại vùng ngực',
      price: 180000,
    ),
  ];

  LaboratoryOrder _withDemoResult(LaboratoryOrder order) => LaboratoryOrder(
    id: order.id,
    name: order.name,
    department: order.department,
    destination: order.destination,
    preparationNote: order.preparationNote,
    price: order.price,
    result: LaboratoryResult(
      id: '${order.id}-result',
      value: order.id.endsWith('cbc') ? '5.2' : 'Không thấy tổn thương cấp',
      unit: order.id.endsWith('cbc') ? 'G/L' : '',
      referenceRange: order.id.endsWith('cbc')
          ? '4.0 - 10.0'
          : 'Hình ảnh trong giới hạn thường gặp',
      source: 'Dữ liệu mô phỏng',
      reportedAt: _timestamp(),
    ),
  );

  QueueSnapshot _resultReviewQueue(PatientJourney journey) => QueueSnapshot(
    room: journey.ticket!.room,
    peopleAhead: 1,
    expectedWait: 'Sau bệnh nhân khám mới tiếp theo',
  );
}
