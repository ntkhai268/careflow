# Queue Management Service Contract

> Contract ID: `CF-SVC-06` | Version: `1.0` | Module: `careflow-queue-service`

## 1. Trách nhiệm và nguyên tắc

Sở hữu:

- Visit Ticket, QR token và số thứ tự;
- check-in và active FIFO queue theo phòng/phiên;
- gọi, recall, missed, bắt đầu và hoàn tất lượt;
- queue cận lâm sàng tự tạo từ order;
- chèn lượt `RESULT_REVIEW` sau một lượt khám ban đầu kế tiếp.

Không quản lý slot/capacity lịch hẹn, chẩn đoán hoặc kết quả xét nghiệm. Không dùng N:M, không trộn
cấp cứu, không tự tạo priority vượt hàng trong queue ngoại trú.

## 2. Loại queue và trạng thái

```text
QueueType: INITIAL_CONSULTATION | LAB_EXECUTION | RESULT_REVIEW

INITIAL_CONSULTATION:
TICKET_ISSUED → CHECKED_IN → CALLED → IN_PROGRESS → COMPLETED
CALLED → MISSED
MISSED → CHECKED_IN
TICKET_ISSUED → CANCELLED | NO_SHOW

LAB_EXECUTION:
QUEUED → CALLED → IN_PROGRESS → COMPLETED
CALLED → MISSED → QUEUED

RESULT_REVIEW:
QUEUED → CALLED → IN_PROGRESS → COMPLETED
CALLED → MISSED → QUEUED_AFTER_NEXT_INITIAL
```

`ARRIVED` và `READY` không tồn tại trong MVP; `CHECKED_IN` mang cả hai ý nghĩa.

## 3. Quy tắc xếp hàng

### Queue phòng khám

- Tạo ticket khi nhận `AppointmentConfirmed`, nhưng chưa vào active queue.
- Số được cấp trước và giữ theo phòng/phiên.
- Active queue chỉ chứa `CHECKED_IN`.
- FIFO theo số/lịch đã cấp trong từng `roomId + sessionDate + sessionCode`.
- Số chưa check-in không chặn số sau đã check-in.
- Bệnh nhân đến trễ được đưa cuối active queue hoặc staff xử lý thủ công có audit.

### Queue cận lâm sàng

- Tạo tự động khi `LabOrderReadyForExecution`.
- Không yêu cầu check-in thứ hai.
- Kỹ thuật viên gọi và đối chiếu danh tính trước khi bắt đầu.

### Queue đọc kết quả

- Tạo khi nhận `AllRequiredResultsAvailable`.
- Chèn sau `INITIAL_CONSULTATION` kế tiếp đang chờ, không đưa ngay lên đầu.
- Nếu bị missed, chèn lại sau một initial consultation kế tiếp.

## 4. HTTP API

| Method và path | Quyền | Mục đích |
|---|---|---|
| `GET /api/queues/tickets/appointment/{appointmentId}` | Chính chủ/Staff | Xem phiếu, QR, số |
| `POST /api/queues/check-in` | `STAFF`, `ADMIN` | Quét QR và tiếp nhận |
| `GET /api/queues/patients/{patientId}/current` | Chính chủ/clinical staff | Lượt hiện tại của bệnh nhân |
| `GET /api/queues/rooms/{roomId}/active?date=&session=` | `DOCTOR`, `STAFF`, `ADMIN` | Active queue phòng |
| `POST /api/queues/rooms/{roomId}/call-next` | `DOCTOR`, `STAFF` | Gọi lượt hợp lệ tiếp theo |
| `POST /api/queues/entries/{entryId}/recall` | `DOCTOR`, `STAFF` | Gọi lại lượt đang CALLED |
| `POST /api/queues/entries/{entryId}/miss` | `DOCTOR`, `STAFF`, `LAB_TECHNICIAN` | Đánh dấu vắng |
| `POST /api/queues/entries/{entryId}/requeue` | Staff phù hợp | Đưa lại hàng |
| `POST /api/queues/entries/{entryId}/start` | Doctor/Lab tech phù hợp | Bắt đầu phục vụ |
| `POST /api/queues/entries/{entryId}/complete` | Doctor/Lab tech phù hợp | Hoàn tất lượt |

Check-in request:

```json
{
  "qrToken": "eyJ0aWNrZXRJZCI6IlBLLTIwMjYtMDAxMjUifQ.signed",
  "roomId": "ROOM-21"
}
```

Ticket response `data`:

```json
{
  "ticketId": "b0881e06-f2e3-402d-bd19-63410367ac3e",
  "ticketCode": "PK-2026-00125",
  "appointmentId": "cf367b19-b946-41dc-969b-0d7958075b22",
  "patientId": "9c613831-90c2-48f6-81c5-0105c20502a1",
  "queueNumber": 47,
  "department": "NEUROLOGY",
  "roomId": "ROOM-21",
  "roomDisplayName": "Phòng 21 - Lầu 1 khu A",
  "timeSlot": "10:30-11:30",
  "qrToken": "<opaque-or-signed-token>",
  "status": "TICKET_ISSUED"
}
```

Active queue item:

```json
{
  "entryId": "6fb18911-a1e8-45d9-a53e-f5b207b8c588",
  "type": "INITIAL_CONSULTATION",
  "queueNumber": 47,
  "status": "CHECKED_IN",
  "checkedInAt": "2026-08-18T03:20:00Z",
  "position": 1
}
```

Màn hình công cộng chỉ được nhận `queueNumber`, `roomDisplayName`, `status`; không nhận tên,
patientId hoặc dữ liệu bệnh án.

## 5. Event

Exchange: `queue.exchange`.

Consume:

| Event | Hành động |
|---|---|
| `AppointmentConfirmed` | Tạo ticket `TICKET_ISSUED` idempotent |
| `AppointmentCancelled/NoShow` | Vô hiệu ticket chưa phục vụ |
| `LabOrderReadyForExecution` | Tạo `LAB_EXECUTION` ở trạng thái `QUEUED` |
| `AllRequiredResultsAvailable` | Tạo `RESULT_REVIEW` theo insertion rule |

Publish:

| Event | Routing key |
|---|---|
| `VisitTicketIssued` v1 | `queue.visit-ticket.issued` |
| `PatientCheckedIn` v1 | `queue.checked-in` |
| `PatientCalled` v1 | `queue.called` |
| `QueueEntryMissed` v1 | `queue.missed` |
| `QueueEntryStarted` v1 | `queue.started` |
| `QueueEntryCompleted` v1 | `queue.completed` |
| `QueueNearTurn` v1 | `queue.near-turn` |

`PatientCheckedIn.payload`:

```json
{
  "entryId": "6fb18911-a1e8-45d9-a53e-f5b207b8c588",
  "appointmentId": "cf367b19-b946-41dc-969b-0d7958075b22",
  "patientId": "9c613831-90c2-48f6-81c5-0105c20502a1",
  "roomId": "ROOM-21",
  "queueNumber": 47,
  "checkedInAt": "2026-08-18T03:20:00Z"
}
```

## 6. Concurrency, idempotency và lỗi

- `call-next` phải lock/claim nguyên tử; hai bác sĩ không gọi cùng entry.
- Một appointment chỉ có một initial ticket; một lab order chỉ có một lab entry.
- QR hết hạn/sai phòng/sai ngày trả `400` hoặc `409`; ticket bị hủy trả `409`.
- Action lặp lại cùng `Idempotency-Key` trả kết quả cũ.
- State transition sai trả `409`, không âm thầm bỏ qua.

## 7. Mock cho consumer

- Appointment test publish fixture và assert một `VisitTicketIssued`.
- Doctor Web mock active queue có số 47 và 49 nhưng không có 48 chưa check-in.
- Lab Web mock `LAB_EXECUTION` đã tự vào queue, không có nút check-in.
- Consultation mock `QueueEntryStarted`; Notification mock called/near-turn/missed.
- Test insertion: initial A đang phục vụ, initial B đang chờ, result R vừa sẵn sàng → thứ tự B, R.

## 8. Definition of Done

### `CONTRACT_READY`

- Chốt ba loại queue, state machine, QR, API, event và insertion rule.
- Các UI có fixture cho ticket, active queue, lab queue và result review.

### `FUNCTIONAL_READY`

- Migration/domain/API chạy; QR không chứa PII.
- FIFO, late arrival, missed/requeue và result insertion có deterministic tests.
- Concurrency test chứng minh không double-call/double-ticket.

### `INTEGRATION_READY`

- Consume event thật từ Appointment/Lab; publish envelope cho Notification/Consultation/Analytics.
- Redelivery không tạo entry trùng.
- Auth/room assignment/ownership chạy qua Gateway.

### `DEMO_READY`

- Appointment confirmed → có phiếu số 47.
- Trước check-in số 47 không có trong active queue; sau check-in xuất hiện đúng vị trí.
- Lab order tự vào hàng không check-in lại.
- Result review được chèn sau bệnh nhân khám ban đầu kế tiếp.
