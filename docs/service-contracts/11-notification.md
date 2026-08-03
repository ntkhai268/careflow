# Notification Service Contract

> Contract ID: `CF-SVC-11` | Version: `1.1` | Module: `careflow-notification-service`

## 1. Trách nhiệm và ranh giới

Sở hữu:

- notification inbox của từng user;
- template và mapping event → thông báo;
- trạng thái `PENDING`, `DELIVERED`, `READ`, `FAILED`;
- WebSocket realtime và delivery log;
- retry/DLQ của kênh gửi.

Notification Service giữ projection tối thiểu `patientId → userId` từ
`PatientProfileCreated`; projection này chỉ dùng để xác định người nhận và không
thay thế Patient Service.

Không quyết định queue state, appointment state hoặc clinical state. Notification thất bại không được
rollback giao dịch nghiệp vụ đã thành công.

## 2. Kênh MVP

```text
IN_APP | WEBSOCKET
```

Push notification thật, SMS và email là mở rộng. Có thể mock adapter nhưng không tuyên bố đã tích hợp
provider production.

## 3. HTTP/WebSocket API

| Method và path | Quyền | Mục đích |
|---|---|---|
| `GET /api/notifications?status=&limit=` | Chính user | Danh sách inbox |
| `GET /api/notifications/unread-count` | Chính user | Số chưa đọc |
| `POST /api/notifications/{notificationId}/read` | Chính user | Đánh dấu đã đọc |
| `POST /api/notifications/read-all` | Chính user | Đọc tất cả |
| `WS /ws/notifications` | Authenticated | STOMP handshake qua Gateway |

Sau khi kết nối, client subscribe destination `/user/queue/notifications`.
Mỗi session chỉ nhận notification của principal trong JWT; client không truyền
`userId` trong URL hoặc destination. Preferences chưa cần trong MVP vì
`IN_APP/WEBSOCKET` là hai mặt của cùng một inbox bắt buộc, không phải hai kênh
marketing để người dùng bật/tắt.

Notification response `data` item:

```json
{
  "id": "c1f706bd-fc96-4868-92bd-338f71af1c87",
  "type": "QUEUE_CALLED",
  "title": "Đã đến lượt",
  "body": "Mời số 47 vào Phòng 21.",
  "action": {
    "type": "OPEN_QUEUE",
    "resourceId": "6fb18911-a1e8-45d9-a53e-f5b207b8c588"
  },
  "status": "DELIVERED",
  "createdAt": "2026-08-18T03:35:00Z",
  "readAt": null
}
```

Không gửi chẩn đoán chi tiết, kết quả nhạy cảm hoặc tên đầy đủ lên màn hình công cộng.

## 4. Event consume và template bắt buộc

| Event | Notification type | Người nhận |
|---|---|---|
| `AppointmentConfirmed` | `APPOINTMENT_CONFIRMED` | Patient |
| `VisitTicketIssued` | `VISIT_TICKET_ISSUED` | Patient |
| `PatientCheckedIn` | `CHECK_IN_SUCCESS` | Patient |
| `QueueNearTurn` | `QUEUE_NEAR_TURN` | Patient |
| `PatientCalled` | `QUEUE_CALLED` | Patient |
| `QueueEntryMissed` | `QUEUE_MISSED` | Patient |
| `LabOrderCreated` | `LAB_ORDER_CREATED` | Patient |
| `LabOrderReadyForExecution` | `LAB_READY` | Patient |
| `LabResultAvailable` | `LAB_RESULT_AVAILABLE` | Patient, không kèm kết quả chi tiết |
| `AllRequiredResultsAvailable` | `RETURN_FOR_REVIEW` | Patient; chỉ hướng dẫn quay lại, không yêu cầu xác nhận để vào queue |
| `PrescriptionIssued` | `PRESCRIPTION_AVAILABLE` | Patient |
| `FollowUpScheduled` | `FOLLOW_UP_SCHEDULED` | Patient |

Template nhận dữ liệu tối thiểu và action link/resource ID. Nội dung chi tiết được tải qua API nguồn sau
khi kiểm tra quyền.

### 4.1. Xác định người nhận

Notification xử lý theo thứ tự sau:

1. Event Queue có `recipientUserId`: dùng trực tiếp sau khi validate UUID.
2. `AppointmentConfirmed` v1 có `userId`: dùng field này cho patient owner.
3. Event Lab/Prescription có `patientId`: tra projection được tạo từ
   `PatientProfileCreated(patientId, userId)`.

Trong giai đoạn Patient Service chưa phát `PatientProfileCreated`, Notification
cũng được phép upsert cùng projection khi nhận Appointment/Queue event có đồng
thời `patientId` và `userId`/`recipientUserId`. Đây là fallback tương thích,
không thay đổi ownership dữ liệu gốc.

Producer không gọi Notification API và không phải đổi `patientId` thành
`userId`. Nếu projection chưa có do event đến sai thứ tự, consumer retry hữu hạn;
hết retry đưa event vào DLQ. Không mở Patient API nội bộ vô danh để giải quyết
background event.

`PatientProfileCreated` được bind từ `patient.exchange` chỉ để upsert projection,
không tạo inbox item cho bệnh nhân.

Notification cho Doctor Web và topic màn hình công cộng nằm ngoài vertical slice
này. Doctor Web tiếp tục lấy trạng thái lâm sàng từ service nguồn và Queue API.

## 5. Event publish

Exchange: `notification.exchange`.

| Publish | Routing key |
|---|---|
| `NotificationDelivered` v1 | `notification.delivered` |
| `NotificationRead` v1 | `notification.read` |
| `NotificationFailed` v1 | `notification.failed` |

`NotificationDelivered.payload`:

```json
{
  "notificationId": "c1f706bd-fc96-4868-92bd-338f71af1c87",
  "recipientUserId": "2f12f672-82d4-4ca2-902d-e8ad333003d5",
  "type": "QUEUE_CALLED",
  "channel": "WEBSOCKET",
  "deliveredAt": "2026-08-18T03:35:00Z"
}
```

## 6. Idempotency và failure

- Unique theo `sourceEventId + recipientUserId + notificationType`.
- Duplicate broker delivery không tạo hai inbox item.
- Inbox item được lưu bền vững trước khi thử gửi WebSocket. WebSocket offline
  không làm item `FAILED`; user lấy lại qua REST khi mở app.
- `PENDING` là trạng thái xử lý trước khi inbox item được lưu/gửi;
  `DELIVERED` nghĩa là item đã có trong inbox, `READ` là user đã đọc, còn
  `FAILED` chỉ dùng khi xử lý/template đã hết retry.
- Retry adapter lỗi hữu hạn; hết retry vào DLQ và publish `NotificationFailed`.
- Không retry vô hạn và không làm nghẽn consumer của event khác.

## 7. Mock cho frontend và producer

- Mobile mock inbox rỗng, nhiều thông báo, unread count và WebSocket reconnect.
- Producer chỉ cần publish event nghiệp vụ; không gọi Notification database/API.
- Fixture phải có near-turn, called, lab result, prescription và follow-up.
- WebSocket mock duplicate message để UI deduplicate theo notification ID.

Canonical fixture:

- [`fixtures/notification/inbox-list.json`](fixtures/notification/inbox-list.json)
- [`fixtures/notification/queue-called-realtime.json`](fixtures/notification/queue-called-realtime.json)

## 8. Definition of Done

### `CONTRACT_READY`

- Chốt notification types, template payload, REST/WS contract và mapping event.
- Mobile/Doctor Web có fixture/reconnect behavior.

### `FUNCTIONAL_READY`

- Inbox/read/WebSocket và delivery states hoạt động.
- Test template, duplicate event, offline user, retry và DLQ pass.
- Không có dữ liệu nhạy cảm ngoài mức cần thiết.

### `INTEGRATION_READY`

- Consume event thật từ Appointment, Queue, Lab, Prescription.
- User chỉ subscribe/read inbox của mình qua Gateway.
- Publish delivery/failure event envelope cho Analytics.

### `DEMO_READY`

- Mobile nhận realtime khi gần lượt/đến lượt/có kết quả/có toa.
- Tắt Mobile rồi mở lại vẫn thấy inbox.
- Notification failure không làm appointment/queue/lab transaction thất bại.
