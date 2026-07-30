# Analytics Service Contract

> Contract ID: `CF-SVC-07` | Version: `1.0` | Target module: `careflow-analytics-service` |
> Application name: `analytics-service` | Port local: `8091`

## 1. Trách nhiệm và ranh giới

Analytics tạo read model vận hành:

- số lượt đặt, check-in, no-show và hoàn tất;
- thời gian chờ theo khoa/phòng/khung giờ;
- thời lượng khám và cận lâm sàng;
- throughput, missed rate và tỷ lệ phải đọc kết quả;
- số thông báo gửi/thất bại và mức sử dụng AI ở dạng tổng hợp.

Analytics không điều khiển queue, không thay đổi lịch, không chẩn đoán và không là nguồn dữ liệu
nghiệp vụ. Dashboard chậm vài giây được chấp nhận.

## 2. Dữ liệu và quyền riêng tư

- Projection ưu tiên ID giả danh và số liệu tổng hợp.
- Không lưu nội dung chẩn đoán, ghi chú bác sĩ, ảnh, file hoặc prompt AI.
- Dashboard không trả họ tên, CCCD, BHYT hoặc số điện thoại.
- `ADMIN` xem toàn viện; quản lý khoa nếu được bổ sung chỉ xem khoa được phân quyền.

## 3. Chỉ số chuẩn

```text
waitingTime = QueueEntryStarted.occurredAt - PatientCheckedIn.occurredAt
consultationDuration = ConsultationCompleted.occurredAt - ConsultationStarted.occurredAt
labTurnaround = LabResultAvailable.occurredAt - LabOrderStarted.occurredAt
noShowRate = AppointmentNoShow / AppointmentConfirmed
missedRate = QueueEntryMissed / PatientCalled
throughput = QueueEntryCompleted theo room/time bucket
```

Không thay đổi công thức trong code mà không cập nhật contract và dashboard label.

## 4. HTTP API

| Method và path | Quyền | Mục đích |
|---|---|---|
| `GET /api/analytics/overview?from=&to=&department=` | `ADMIN` | KPI tổng quan |
| `GET /api/analytics/queues?from=&to=&roomId=&bucket=` | `ADMIN` | Chờ/throughput theo queue |
| `GET /api/analytics/appointments?from=&to=&department=` | `ADMIN` | Confirm/cancel/no-show |
| `GET /api/analytics/labs?from=&to=&servicePointId=` | `ADMIN` | TAT cận lâm sàng |
| `GET /api/analytics/daily?date=` | `ADMIN` | Báo cáo ngày demo |

Overview response `data`:

```json
{
  "period": {
    "from": "2026-08-18T00:00:00+07:00",
    "to": "2026-08-19T00:00:00+07:00"
  },
  "appointmentsConfirmed": 320,
  "patientsCheckedIn": 281,
  "consultationsCompleted": 264,
  "noShowRate": 0.0813,
  "averageWaitingMinutes": 24.6,
  "p95WaitingMinutes": 61.2,
  "averageConsultationMinutes": 12.4,
  "lastProjectedAt": "2026-08-18T10:00:03+07:00"
}
```

## 5. Event consume

Consume tối thiểu:

```text
AppointmentConfirmed
AppointmentCancelled
AppointmentNoShow
VisitTicketIssued
PatientCheckedIn
PatientCalled
QueueEntryMissed
QueueEntryStarted
QueueEntryCompleted
ConsultationStarted
ConsultationCompleted
LabOrderCreated
LabOrderStarted
LabResultAvailable
PrescriptionIssued
NotificationDelivered
NotificationFailed
AiSuggestionGenerated
```

Mỗi fact lưu `eventId`, `eventType`, timestamp, các dimension không nhạy cảm và correlation ID.
Duplicate event không tăng counter lần hai.

## 6. Mock cho Admin Web

- Fixture có ngày không dữ liệu, ngày bình thường và khoa/phòng filter.
- `lastProjectedAt` luôn có để UI thể hiện độ trễ.
- Mock `403` cho non-admin.
- Không mock patient-level table nếu contract chỉ cho phép aggregate.

## 7. Definition of Done

### `CONTRACT_READY`

- Module, metric definitions, dimension, API và event input được chốt.
- Admin Web có fixture dashboard.

### `FUNCTIONAL_READY`

- Module được thêm vào parent Maven, Gateway và cấu hình local/Docker.
- Projection/migration/query hoạt động; metric tests dùng timeline cố định.
- Duplicate/out-of-order event không làm sai counter hoặc duration.

### `INTEGRATION_READY`

- Consume được event thật từ Appointment, Queue, Consultation và Lab.
- Độ trễ projection đo được; DLQ/replay không double count.
- API chỉ cho `ADMIN`, không trả PII.

### `DEMO_READY`

- Sau demo end-to-end, dashboard tăng đúng các KPI.
- Có báo cáo ngày lọc theo khoa/phòng.
- Số liệu truy ngược được đến event ID để debug mà không lộ hồ sơ bệnh án.
