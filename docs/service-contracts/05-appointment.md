# Appointment Service Contract

> Contract ID: `CF-SVC-05` | Version: `1.0` | Module: `careflow-appointment-service`

## 1. Trách nhiệm và ranh giới

Sở hữu:

- khoa, lịch làm việc và capacity theo khung giờ;
- đặt/hủy lịch khám;
- appointment và lịch tái khám;
- `NO_SHOW` khi hết cửa sổ check-in.

Không sở hữu QR, phiếu khám, số thứ tự hoặc active queue. Các dữ liệu đó thuộc Queue Management.
Field `queueNumber` hiện có trong `AppointmentResponse` không phải nguồn sự thật và phải được bỏ hoặc
đánh dấu deprecated khi consumer chuyển sang Queue API.

## 2. Trạng thái mục tiêu

```text
CONFIRMED → FULFILLED
CONFIRMED → CANCELLED
CONFIRMED → NO_SHOW
```

MVP tự động xác nhận khi slot còn capacity; không có bước duyệt thủ công. `PENDING` trong code hiện
tại là khoảng cách cần sửa, không phải trạng thái business mục tiêu.

## 3. HTTP API

| Method và path | Quyền | Mục đích |
|---|---|---|
| `GET /api/appointments/departments` | Authenticated | Danh sách khoa |
| `GET /api/appointments/time-slots?department=&date=` | Authenticated | Slot và capacity còn lại |
| `POST /api/appointments` | `PATIENT` chính chủ hoặc `STAFF` | Đặt lịch, tự xác nhận |
| `GET /api/appointments/{appointmentId}` | Chính chủ/assigned staff/doctor | Chi tiết |
| `GET /api/appointments/patient/{patientId}` | Chính chủ/clinical staff | Lịch của bệnh nhân |
| `GET /api/appointments/department/{department}?date=` | `DOCTOR`, `STAFF`, `ADMIN` | Lịch dự kiến |
| `PUT /api/appointments/{appointmentId}/cancel` | Chính chủ hoặc `STAFF` | Hủy lịch |
| `POST /api/appointments/follow-ups` | `DOCTOR` | Hẹn tái khám từ consultation |

Create request giữ tương thích API hiện tại:

```json
{
  "patientId": "9c613831-90c2-48f6-81c5-0105c20502a1",
  "patientName": "Nguyễn Thị Quỳnh Thương",
  "department": "NEUROLOGY",
  "appointmentDate": "2026-08-18",
  "timeSlot": "10:30-11:30",
  "reason": "Đau đầu kéo dài"
}
```

Server phải đối chiếu `patientName` từ Patient Service/projection; không coi tên client gửi là dữ liệu
đáng tin. Response `201`:

```json
{
  "status": 201,
  "message": "Đặt lịch khám thành công",
  "data": {
    "id": "cf367b19-b946-41dc-969b-0d7958075b22",
    "patientId": "9c613831-90c2-48f6-81c5-0105c20502a1",
    "department": "NEUROLOGY",
    "appointmentDate": "2026-08-18",
    "timeSlot": "10:30-11:30",
    "status": "CONFIRMED",
    "reason": "Đau đầu kéo dài"
  },
  "timestamp": "2026-07-30T04:00:00Z"
}
```

Follow-up request:

```json
{
  "consultationId": "35df361e-f4b3-4113-ad0d-0ed853fe61fc",
  "patientId": "9c613831-90c2-48f6-81c5-0105c20502a1",
  "department": "NEUROLOGY",
  "recommendedDate": "2026-08-18",
  "note": "Tái khám sau 2 tuần"
}
```

## 4. Capacity và idempotency

- Capacity khóa/giữ nguyên tử theo `department + date + timeSlot`.
- Không overbook khi hai request đồng thời.
- Một patient không có hai appointment chưa hủy cùng slot.
- `Idempotency-Key` bắt buộc với create từ Mobile.
- Hết capacity trả `409`, không âm thầm chuyển slot.

## 5. Event

Exchange: `appointment.exchange`.

| Publish | Routing key | Consumer |
|---|---|---|
| `AppointmentConfirmed` v1 | `appointment.confirmed` | Queue, Notification, Analytics |
| `AppointmentCancelled` v1 | `appointment.cancelled` | Queue, Notification, Analytics |
| `AppointmentNoShow` v1 | `appointment.no-show` | Queue, Analytics |
| `AppointmentFulfilled` v1 | `appointment.fulfilled` | EMR, Analytics |
| `FollowUpScheduled` v1 | `appointment.follow-up.scheduled` | EMR, Notification |

`AppointmentConfirmed.payload`:

```json
{
  "appointmentId": "cf367b19-b946-41dc-969b-0d7958075b22",
  "patientId": "9c613831-90c2-48f6-81c5-0105c20502a1",
  "department": "NEUROLOGY",
  "roomId": "ROOM-21",
  "appointmentDate": "2026-08-18",
  "timeSlot": "10:30-11:30"
}
```

Code hiện tại publish raw `AppointmentCreated` map. Để đạt contract phải chuyển sang
`AppointmentConfirmed` trong `EventEnvelope`.

## 6. Mock cho consumer

- Queue dùng fixture `AppointmentConfirmed` và `AppointmentCancelled`.
- Mobile mock `time-slots` với `capacityRemaining`, create success và `409 SLOT_FULL`.
- Doctor Web mock lịch dự kiến gồm cả người chưa check-in; không dùng danh sách này làm active queue.

## 7. Definition of Done

### `CONTRACT_READY`

- Slot/capacity, auto-confirm, state, request/response và event được chốt.
- Queue có thể tạo Visit Ticket chỉ từ fixture `AppointmentConfirmed`.

### `FUNCTIONAL_READY`

- Create tự trả `CONFIRMED`, capacity concurrency-safe, cancel/no-show/follow-up đúng state.
- Migration, unit/service test cho duplicate slot, full capacity, ownership và idempotency pass.

### `INTEGRATION_READY`

- Patient được xác minh; Gateway authorization đúng.
- Event envelope/outbox hoạt động; Queue và Notification contract test pass.
- Appointment cancel làm ticket chưa check-in bị vô hiệu.

### `DEMO_READY`

- Mobile chọn slot → đặt lịch → nhận appointment confirmed.
- Queue tự cấp phiếu/QR/số từ event, không cần admin duyệt.
- Doctor Web xem lịch dự kiến nhưng active queue chỉ lấy từ Queue Service.
