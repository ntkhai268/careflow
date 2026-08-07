# Appointment Service Contract

> Contract ID: `CF-SVC-05` | Version: `1.2` | Module: `careflow-appointment-service`

## 1. Trách nhiệm và ranh giới

Sở hữu:

- khoa, phòng khám, lịch làm việc và capacity theo khung giờ;
- đặt/hủy lịch khám;
- appointment và lịch tái khám;
- `NO_SHOW` khi hết cửa sổ check-in.

Không sở hữu QR, phiếu khám, số thứ tự hoặc active queue. Các dữ liệu đó thuộc Queue Management.
Field `queueNumber` hiện có trong `AppointmentResponse` không phải nguồn sự thật và phải được bỏ hoặc
đánh dấu deprecated khi consumer chuyển sang Queue API.

Mô hình mục tiêu:

```text
Department 1 ─── N ClinicRoom
Appointment N ─── 1 ClinicRoom
```

`ClinicRoom` thuộc cấu hình lịch khám của Appointment Service. Trong MVP, mỗi
khoa chỉ có đúng một phòng active. Đây là invariant của
dữ liệu/cấu hình MVP, không phải ràng buộc 1:1 trong schema.
Hospital Directory cũng phải có đúng một doctor đang active được gán vào phòng
consultation của khoa trong MVP. Appointment Service lưu doctor user ID vào
`appointments.doctor_id`; client không được chọn hoặc ghi đè assignment này.

### Ranh giới payment của Patient Mobile

Appointment Service **chưa nhận payment/service contract** và chưa sở hữu
catalog dịch vụ khám trong phiên bản này. Khi Mobile chưa có catalog từ
Directory/Appointment, ứng dụng dùng fixture:

```text
code: GENERAL_CONSULTATION
name: Khám thường
price: 150.000 ₫ (demo)
duration: 15 phút (tham khảo)
```

Mobile bắt buộc ghi nhận `ONLINE_MOCK` cho phí khám trước khi hoàn tất flow đặt
lịch, sau đó lưu `AppointmentPaymentReceipt` qua local adapter. `CASH_AT_HOSPITAL`
không còn là lựa chọn trong flow đặt lịch; nếu cần thu thêm `amountDue` sau
khám, đó là một phần của `VisitSettlement` cuối lượt. Receipt local không được
đưa vào request hoặc event của Appointment. Nếu local storage lỗi, Mobile không
được coi lịch đã tạo thành công là thất bại.

Giá trị thực tế đã thu được lưu thành `prepaidAmount` để đối trừ khi tạo
`VisitSettlement` cuối lượt khám. Chi phí cận lâm sàng không tạo lần thanh toán
riêng. Phạm vi contract chỉ xét người bệnh tự chi trả.

Khi backend sẵn sàng, catalog dịch vụ/giá và Payment/Refund API sẽ thay
fixture/adapter qua một contract mới có version; request tạo Appointment hiện
tại vẫn giữ nguyên.

## 2. Trạng thái mục tiêu

```text
CONFIRMED → FULFILLED
CONFIRMED → CANCELLED
CONFIRMED → NO_SHOW
```

MVP tự động xác nhận khi slot còn capacity; không có bước duyệt thủ công. Dữ liệu
`PENDING` cũ chỉ là dữ liệu legacy, không phải trạng thái business mục tiêu.

## 3. HTTP API

| Method và path | Quyền | Mục đích |
|---|---|---|
| `GET /api/appointments/departments` | Authenticated | Danh sách khoa |
| `GET /api/appointments/departments/{department}/rooms?active=true` | Clinical staff/Admin | Danh sách phòng thuộc khoa |
| `GET /api/appointments/clinical-context/me?date=&session=` | `DOCTOR` | Lấy khoa và các phòng bác sĩ được phân công từ trusted user ID |
| `GET /api/appointments/time-slots?department=&date=` | Authenticated | Slot và capacity còn lại |
| `POST /api/appointments` | `PATIENT` chính chủ hoặc `ADMIN` | Đặt lịch, tự xác nhận |
| `GET /api/appointments/{appointmentId}` | Chính chủ, assigned doctor hoặc `ADMIN` | Chi tiết |
| `GET /api/appointments/patient/{patientId}` | Chính chủ, assigned doctor hoặc `ADMIN` | Lịch của bệnh nhân |
| `GET /api/appointments/access/patient/{patientId}` | Internal trusted service call | Kiểm tra assignment doctor hoặc appointment/room scope của staff |
| `GET /api/appointments/department/{department}?date=` | `DOCTOR` được phân công hoặc `ADMIN` | Lịch dự kiến; bác sĩ chỉ thấy appointment gắn với user ID của mình |
| `PUT /api/appointments/{appointmentId}/status` | Assigned doctor hoặc `ADMIN` | Cập nhật trạng thái lâm sàng |
| `PUT /api/appointments/{appointmentId}/cancel` | Chính chủ, assigned doctor hoặc `ADMIN` | Hủy lịch |
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

Client không gửi `roomId`. Khi tạo lịch, server truy vấn các `ClinicRoom` active
theo `department`:

1. Không có phòng: trả lỗi cấu hình, không tạo Appointment.
2. Có đúng một phòng: lưu `roomId` vào Appointment và tiếp tục giữ capacity.
3. Có nhiều phòng: MVP trả lỗi cấu hình, không dùng `findFirst()` hoặc random.
4. Không có hoặc có nhiều doctor active gán vào phòng: trả lỗi cấu hình, không tự chọn doctor.

Phiên bản mở rộng sẽ thay bước 3 bằng `RoomAssignmentPolicy`; request, schema
Appointment, event và Queue API không cần đổi.

Doctor Web không tự suy đoán khoa từ dữ liệu client. Sau đăng nhập, nó gọi
`clinical-context/me`; Appointment Service dùng trusted user ID và cấu hình lịch
làm việc để trả `department` cùng danh sách phòng được phân công. Dữ liệu MVP
trả đúng một phòng, nhưng response dùng danh sách để giữ khả năng mở rộng 1:N.

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
    "roomId": "ROOM-21",
    "roomDisplayName": "Phòng khám Thần kinh 21",
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
- Không có hoặc có nhiều hơn một phòng active của khoa trong MVP trả
  `409 ROOM_CONFIGURATION_INVALID`; không phân phòng ngẫu nhiên.
- Không có hoặc có nhiều hơn một doctor active trong phòng consultation trả
  `409 DOCTOR_CONFIGURATION_INVALID`; không phân doctor ngẫu nhiên.
- Nếu ngày là hôm nay, `timeSlot` phải có giờ bắt đầu lớn hơn thời điểm server
  nhận request. Ca đã bắt đầu hoặc đã qua trả `400` với thông báo tiếng Việt.

### Error contract cho `POST /api/appointments`

| HTTP | Trường hợp | `message` tối thiểu |
|---|---|---|
| `400` | Ngày/ca đã qua hoặc payload sai | `Ca khám đã qua. Vui lòng chọn ca khác` |
| `401` | JWT thiếu/hết hạn | Không trả stack trace |
| `403` | Patient không sở hữu hồ sơ | Không tiết lộ dữ liệu hồ sơ |
| `409` | Cùng patient đã có appointment chưa hủy trong cùng ngày/ca | `Bệnh nhân đã có lịch khám vào ca này` |
| `409` | Slot hết capacity | `Ca khám đã hết chỗ` |
| `409` | Cấu hình phòng của khoa không xác định duy nhất trong MVP | `Khoa chưa được cấu hình đúng một phòng khám hoạt động` |

Mobile hiển thị `message` đã kiểm soát từ envelope; không hiển thị
`DioException`, stack trace hoặc nội dung lỗi transport.

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
  "userId": "2f12f672-82d4-4ca2-902d-e8ad333003d5",
  "department": "NEUROLOGY",
  "roomId": "ROOM-21",
  "roomDisplayName": "Phòng khám Thần kinh 21",
  "appointmentDate": "2026-08-18",
  "timeSlot": "10:30-11:30"
}
```

`roomId` là phòng đã được Appointment Service xác định và lưu trong Appointment,
không phải giá trị do Queue Service hoặc client tự sinh.
`doctorId` là `userId` của doctor trong Hospital Directory, dùng làm assignment
authority cho các service lâm sàng; không phải ID kỹ thuật của `DoctorProfile`.
`userId` là owner đã được xác thực khi tạo Appointment; Queue và Notification
dùng field này làm recipient, không nhận giá trị tùy ý từ Mobile.

Runtime ghi `AppointmentConfirmed` trong `EventEnvelope` v1 vào transactional
outbox cùng transaction tạo lịch. Publisher retry có giới hạn và Queue consumer
xử lý redelivery idempotent.

Canonical fixtures:

- [`fixtures/appointment/create-confirmed.json`](fixtures/appointment/create-confirmed.json)
- [`fixtures/appointment/create-conflict.json`](fixtures/appointment/create-conflict.json)
- [`fixtures/appointment/appointment-confirmed-v1.json`](fixtures/appointment/appointment-confirmed-v1.json)
- [`fixtures/appointment/appointment-cancelled-v1.json`](fixtures/appointment/appointment-cancelled-v1.json)

Tại mốc 2026-08-02, create trả `CONFIRMED`, chặn ca đã qua, lưu owner/phòng được
phân và phát event contract qua outbox. MVP dùng một phòng cấu hình tĩnh cho mỗi
khoa; bảng quản trị `ClinicRoom`, capacity concurrency-safe và `Idempotency-Key`
của create vẫn là các gap riêng của Appointment Service.

## 6. Mock cho consumer

- Queue dùng fixture `AppointmentConfirmed` và `AppointmentCancelled`.
- Mobile mock `time-slots` với `capacityRemaining`, create success và `409 SLOT_FULL`.
- Doctor Web mock lịch dự kiến gồm cả người chưa check-in; không dùng danh sách này làm active queue.

## 7. Definition of Done

### `CONTRACT_READY`

- Slot/capacity, auto-confirm, state, request/response và event được chốt.
- Chốt `Department 1:N ClinicRoom`, chính sách MVP một phòng active và nguồn gốc
  của `roomId` trong Appointment/event.
- Queue có thể tạo Visit Ticket chỉ từ fixture `AppointmentConfirmed`.

### `FUNCTIONAL_READY`

- Create tự trả `CONFIRMED`, lưu đúng `roomId`, capacity concurrency-safe và
  cancel/no-show/follow-up đúng state.
- Migration tạo `departments`, `clinic_rooms` và `appointments.room_id`.
- Unit/service test phủ 0/1/nhiều phòng active, duplicate slot, full capacity,
  ownership và idempotency.

### `INTEGRATION_READY`

- Patient được xác minh; Gateway authorization đúng.
- Event envelope/outbox hoạt động; Queue và Notification contract test pass.
- Appointment cancel làm ticket chưa check-in bị vô hiệu.

### `DEMO_READY`

- Mobile chọn slot → server phân phòng duy nhất → nhận appointment confirmed có
  `roomId` và tên phòng.
- Queue tự cấp phiếu/QR/số từ event, không cần admin duyệt.
- Doctor Web xem lịch dự kiến nhưng active queue chỉ lấy từ Queue Service.
