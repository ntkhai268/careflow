# Vertical Slice 01 — Check-in và Queue thật

## Phạm vi đã triển khai

```text
Patient đặt lịch
  → Appointment lưu CONFIRMED + phòng được phân
  → ghi AppointmentConfirmed vào transactional outbox
  → RabbitMQ chuyển EventEnvelope v1
  → Queue consume idempotent
  → cấp Visit Ticket + số thứ tự
  → Mobile hiển thị phiếu thật
  → Hospital Web hiển thị QR check-in theo phòng/phiên
  → Patient Mobile quét QR và gửi latitude/longitude để kiểm tra geofence
  → Queue Entry chuyển CHECKED_IN và vào active queue
  → Doctor đọc active queue, xem recommendedNext và bấm Gọi tại một phần tử
  → Mobile tải lại và thấy CALLED
```

Slice check-in này chỉ đi qua `CONSULTATION` phase `INITIAL`. Domain Queue hiện
đã tách `QueueType` và `ConsultationPhase`; phía consumer/FIFO của
`PHARMACY_DISPENSING` cũng đã sẵn sàng, còn producer Prescription,
`LAB_EXECUTION` và `CONSULTATION + RESULT_REVIEW` thuộc các vertical slice tiếp theo.

## Nguồn sự thật

| Dữ liệu | Service sở hữu |
|---|---|
| Appointment, khoa và phòng được phân | Appointment Service |
| Visit Ticket, QR check-in theo phòng/phiên, số thứ tự và Queue Entry | Queue Service |
| Tài khoản và role `PATIENT`, `STAFF`, `DOCTOR` | Identity Service |

Appointment ghi event vào outbox cùng transaction tạo lịch. Queue ghi event
`VisitTicketIssued`, `PatientCheckedIn`, `PatientCalled`, `QueueEntryStarted` và
`QueueEntryCompleted` vào outbox. Consumer lưu `processed_events`, vì vậy
RabbitMQ redelivery không tạo phiếu trùng.

## API sử dụng trong slice

| API | Role |
|---|---|
| `GET /api/queues/tickets/appointment/{appointmentId}` | Chính chủ, clinical staff |
| `GET /api/queues/rooms/{roomId}/check-in-qr?date=&session=` | `STAFF`, `ADMIN` |
| `POST /api/queues/check-in` | Chính chủ `PATIENT`; `STAFF`, `ADMIN` hỗ trợ tại quầy |
| `GET /api/queues/patients/{patientId}/current` | Chính chủ, clinical staff |
| `GET /api/queues/rooms/{roomId}/active` | `DOCTOR`, `STAFF`, `ADMIN` |
| `POST /api/queues/entries/{entryId}/call` | `DOCTOR`, `ADMIN` |
| `POST /api/queues/rooms/{roomId}/call-next` | `DOCTOR`, `ADMIN` — gọi nhanh lượt gợi ý |
| `POST /api/queues/entries/{entryId}/start` | `DOCTOR`, `ADMIN` trong MVP |
| `POST /api/queues/entries/{entryId}/complete` | `DOCTOR`, `ADMIN` trong MVP |

Request check-in:

```json
{
  "appointmentId": "<appointment-id>",
  "checkInQrToken": "<hospital-session-signed-token>",
  "latitude": 10.7769,
  "longitude": 106.7009,
  "accuracyMeters": 12.5
}
```

Server lấy bệnh nhân từ JWT, suy ra phòng từ token/Appointment và chỉ chuyển sang
`CHECKED_IN` khi khoảng cách tới tâm Hospital Geofence không vượt quá bán kính
cấu hình. Lượt ưu tiên chỉ được staff xác nhận bằng `queueClass=PRIORITY` và bắt
buộc có `priorityReasonCode`; bệnh nhân không tự khai ưu tiên. Người không có
Mobile được staff hỗ trợ bằng mã phiếu tại quầy.

## Chạy local

Ngoài năm container core của Mobile, bật thêm Queue Service:

```powershell
docker compose -f docker-compose.yml up -d --build `
  careflow-api-gateway `
  careflow-identity-service `
  careflow-patient-service `
  careflow-appointment-service `
  careflow-queue-service
```

Các migration tự chạy:

- Appointment `V3`: owner, room assignment và appointment outbox.
- Identity `V6`: role `STAFF`, `LAB_TECHNICIAN`.
- Queue `V4`: audit check-in, ticket snapshot và cấu hình một phòng/khoa cho MVP.
- Queue `V6`: đồng bộ kiểu số cấu hình với entity Java.

Admin gán role cho tài khoản nhân viên:

```http
PATCH /api/users/{userId}/role
Authorization: Bearer <admin-token>
Content-Type: application/json

{"role":"STAFF"}
```

Sau khi tạo lịch mới, chạy smoke script:

```powershell
.\scripts\queue-vertical-slice-smoke.ps1 `
  -AppointmentId <appointment-id> `
  -RoomId ROOM-21 `
  -PatientToken <patient-jwt> `
  -StaffToken <staff-jwt> `
  -DoctorToken <doctor-jwt>
```

Production luôn lấy phiếu và queue từ backend thật. Để chạy hybrid — Queue thật
nhưng Consultation/Lab/Prescription vẫn mô phỏng — truyền thêm:

```text
--dart-define=DEMO_MODE=true --dart-define=REAL_QUEUE=true
```

## Tiêu chí hoàn tất slice

- Appointment tạo thành công vẫn bền vững khi RabbitMQ tạm gián đoạn nhờ outbox.
- Một `AppointmentConfirmed` chỉ tạo đúng một ticket.
- QR check-in do bệnh viện hiển thị theo phòng/phiên trong thời hạn token, không
  chứa PII/bệnh án.
- QR sai ngày, sai chữ ký, sai ticket, sai phòng hoặc ngoài geofence bị từ chối.
- Patient QR hợp lệ trong geofence mới check-in; staff/admin có luồng hỗ trợ tại quầy.
- Active queue không chứa ticket chưa check-in.
- Doctor Web hiển thị `recommendedNext`, nhưng bác sĩ có thể gọi bất kỳ lượt
  `CHECKED_IN` nào bằng nút **Gọi** trên từng hàng.
- `call` và `call-next` yêu cầu idempotency key; không chặn gọi thêm khi phòng đã
  có lượt `CALLED` hoặc `IN_PROGRESS`.
- Hospital Web hiển thị QR phiên/phòng; Mobile hiển thị ticket và trạng thái Queue thật.
