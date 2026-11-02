# Đặc Tả SDD (Spec-Driven Development) — `careflow-appointment-service`

> **Tài liệu Đặc tả Kỹ thuật & Nghiệp vụ (SDD Specification)**
> **Service**: `careflow-appointment-service` (Port `8083`)
> **Nhánh thực hiện**: `refactor/appointment-service`
> **Phiên bản Spec**: `SDD-1.0` (02/11/2026)

---

## 1. Bối cảnh & Ranh giới Trách nhiệm (Bounded Context)

`careflow-appointment-service` quản lý toàn bộ vòng đời **Đặt lịch khám ngoại trú & Phân bổ Phòng/Ca khám (Slot Allocation)**: từ khi Bệnh nhân đặt lịch trên Mobile App (hoặc Admin đặt hộ, Bác sĩ đặt lịch tái khám từ toa thuốc), đối chiếu bảng giá dịch vụ, chọn phòng khám đang hoạt động có đúng 1 bác sĩ phụ trách còn nhiều chỗ trống nhất, khóa slot chống đặt vượt sức chứa (`AppointmentSlotLock` & PostgreSQL advisory lock), cho đến khi Bệnh nhân check-in tại bệnh viện và Bác sĩ hoàn tất ca khám.

### Sở hữu dữ liệu (Data Ownership)
- **`appointments`**: Bản ghi lịch hẹn khám (`patientId`, `ownerUserId`, `patientName`, `department`, `departmentId`, `roomId`, `roomDisplayName`, `doctorId`, `doctorName`, `appointmentDate`, `timeSlot`, `status` [`AppointmentStatus`], `reason`, `notes`, `sourceConsultationId`, `queueNumber`, `serviceCode`, `serviceName`, `priceVersion`, `quotedAmount`, `quotedCurrency`, `priceEffectiveAt`).
- **`appointment_slot_locks`**: Khóa bi quan theo khóa tổ hợp `roomId|appointmentDate|timeSlot` đảm bảo không vượt quá `capacity` khi nhiều yêu cầu đặt lịch đồng thời.
- **`appointment_room_schedules` & `appointment_schedule_revisions`**: Cấu hình lịch hoạt động theo từng phòng khám (khung giờ làm việc `windows`, thời lượng ca `slotMinutes`, sức chứa `capacity`, ngày nghỉ/ngoại lệ `exceptions`) kèm lịch sử phiên bản.
- **`appointment_booking_commands`**: Bảng lưu trữ khóa chống trùng `Idempotency-Key` (`scope_key = ownerUserId:idempotencyKey`, `fingerprint` SHA-256 của toàn bộ tham số đặt lịch, `appointment_id`).
- **`appointment_outbox_events`**: Bảng Transactional Outbox đảm bảo phát hành sự kiện `AppointmentConfirmed`, `FollowUpScheduled`, `AppointmentCancelled` tới RabbitMQ (`appointment.exchange`).

### Phụ thuộc Đối tác (Upstream / Downstream Collaborators)
| Service đối tác | Giao thức | Mục đích gọi / Tích hợp |
|---|---|---|
| `careflow-hospital-directory-service` | Feign (`DirectoryClient`) | 1. Xác minh chuyên khoa (`getDepartmentByCode`), danh sách phòng `CONSULTATION` đang active (`getAllRooms`) và bác sĩ được phân công (`getDoctors`, `getDoctorByUserId`).<br>2. Lấy và khóa phiên bản bảng giá dịch vụ (`getServicePrice`).<br>3. Kiểm tra phân quyền nhân viên theo phòng (`hasStaffRoomAccess`). |
| `careflow-patient-service` | Feign (`PatientClient`) | Xác minh quyền sở hữu hồ sơ bệnh nhân khi đặt lịch (`getBookingContext`) và lấy hồ sơ lâm sàng khi bác sĩ tạo lịch tái khám (`getClinicalProfile`). |
| `careflow-consultation-service` | Feign (`AppointmentConsultationClient`) | 1. Xác minh trạng thái ca khám khi đặt lịch tái khám (`getById`) hoặc khi hoàn tất lịch khám (`getByAppointment`).<br>2. Kiểm tra quyền truy cập ca khám đã bàn giao (`patientAccess`). |

---

## 2. Máy Trạng Thái Lịch Khám (`AppointmentStatus`)

### 2.1. Sơ Đồ Chuyển Trạng Thái
```text
PENDING ──► CONFIRMED ──► CHECKED_IN ──► IN_PROGRESS ──► COMPLETED
   │            │              │
   │            └──────────────┼─────────────► COMPLETED (khi Consultation liên quan đã COMPLETED)
   └────────────┴──────────────┴─────────────► CANCELLED
```
- **`PENDING`**: Trạng thái khởi tạo mặc định của entity.
- **`CONFIRMED`**: Lịch hẹn đã được xác nhận thành công (mọi lịch đặt online và lịch tái khám đều vào thẳng `CONFIRMED`), đã giữ chỗ trong slot của phòng khám.
- **`CHECKED_IN`**: Bệnh nhân đã check-in tại bệnh viện và lấy số thứ tự vào hàng đợi.
- **`IN_PROGRESS`**: Bác sĩ đang thực hiện khám cho bệnh nhân.
- **`COMPLETED`**: Ca khám đã hoàn tất (Terminal state).
- **`CANCELLED`**: Lịch hẹn đã bị hủy bởi bệnh nhân, bác sĩ phụ trách hoặc admin trước khi vào khám, giải phóng chỗ trong slot (Terminal state).

### 2.2. Bảng Chuyển Đổi Trạng Thái & Điều Kiện Bất Biến (State Transition & Invariants Table)

| Từ Trạng Thái | Hành Động (Command) | Sang Trạng Thái | Điều Kiện Tiên Quyết (Pre-conditions & Invariants) | Mã Lỗi Khi Vi Phạm |
|---|---|---|---|---|
| *(Chưa tồn tại)* | `bookAppointment` / `createAppointment` | `CONFIRMED` | 1. Actor phải là `PATIENT` (sở hữu hồ sơ) hoặc `ADMIN` (`401`/`403`).<br>2. `Idempotency-Key` nếu trùng `scope_key` phải khớp hoàn toàn `fingerprint` SHA-256 (`409` nếu khác tham số).<br>3. Ngày và giờ bắt đầu ca khám không nằm trong quá khứ (`400`).<br>4. Bệnh nhân chưa có lịch khám khác (khác `CANCELLED`) vào cùng `appointmentDate` + `timeSlot` (`409`).<br>5. Khoa phải active, có ít nhất 1 phòng `CONSULTATION` active với đúng 1 bác sĩ phụ trách (`409`/`503`).<br>6. Bảng giá dịch vụ hợp lệ và khớp `priceVersion` (`409`/`503`).<br>7. Phòng được chọn phải còn chỗ trống (`booked < capacity` dưới khóa `appointment_slot_locks`, nếu đầy → `409`). | `400`, `401`, `403`, `404`, `409`, `503` |
| *(Chưa tồn tại)* | `createFollowUpAppointment` | `CONFIRMED` | 1. Actor phải là `DOCTOR` phụ trách ca khám nguồn (`401`/`403`).<br>2. Ca khám nguồn không ở trạng thái `CANCELLED` hoặc `TRANSFERRED` (`409`).<br>3. Phải có `timeSlot` hợp lệ (`422`).<br>4. Thực thi idempotent theo khóa `"follow-up-" + consultationId + "-" + recommendedDate`, gắn `sourceConsultationId` và phát sự kiện `FollowUpScheduled`. | `400`, `401`, `403`, `409`, `422`, `503` |
| `CONFIRMED` | `updateAppointmentStatus` | `CHECKED_IN` / `IN_PROGRESS` / `COMPLETED` | 1. Actor phải là `ADMIN` hoặc `DOCTOR` phụ trách (hoặc bác sĩ nhận bàn giao khi chuyển sang `COMPLETED`) (`403`).<br>2. Nếu chuyển thẳng từ `CONFIRMED` → `COMPLETED`, bắt buộc phải xác minh có ca khám `COMPLETED` gắn với `appointmentId` bên `ConsultationService` (`409`/`503`). | `400`, `403`, `409`, `503` |
| `CHECKED_IN` | `updateAppointmentStatus` | `IN_PROGRESS` | Actor là `DOCTOR` phụ trách hoặc `ADMIN` (`403`). | `400`, `403` |
| `IN_PROGRESS` | `updateAppointmentStatus` | `COMPLETED` | Actor là `DOCTOR` phụ trách, bác sĩ nhận bàn giao hoặc `ADMIN` (`403`). Nếu đã `COMPLETED` → trả về idempotent. | `400`, `403` |
| `PENDING` / `CONFIRMED` / `CHECKED_IN` | `cancelAppointment` | `CANCELLED` | 1. Actor phải là chủ hồ sơ (`PATIENT`), `DOCTOR` phụ trách hoặc `ADMIN` (`403`).<br>2. Không được hủy khi đã `IN_PROGRESS` hoặc `COMPLETED` (`409`).<br>3. Nếu đã `CANCELLED` trước đó → báo lỗi `400`.<br>4. Ghi Outbox `AppointmentCancelled`. | `400`, `403`, `409` |

---

## 3. Ma Trận Mã Lỗi Chuẩn (Error Matrix)

| HTTP Status | Ý nghĩa Nghiệp vụ | Tình huống Kích hoạt |
|---|---|---|
| **`400 Bad Request`** | Tham số thời gian, chuyên khoa hoặc trạng thái không hợp lệ | - Ngày khám/ca khám đã qua, sai định dạng `HH:mm-HH:mm`, chuyên khoa không tồn tại, hủy lịch đã `CANCELLED`, hoặc chuyển trạng thái trái quy tắc. |
| **`401 Unauthorized`** | Thiếu định danh người gọi | - Thiếu `actorUserId` hoặc `role`. |
| **`403 Forbidden`** | Vi phạm phân quyền hoặc quyền sở hữu | - Bệnh nhân đặt/xem/hủy lịch của hồ sơ người khác.<br>- Bác sĩ cập nhật/xem lịch không thuộc phân công hoặc đã bàn giao cho bác sĩ khác. |
| **`404 Not Found`** | Không tìm thấy tài nguyên | - `appointmentId` hoặc `DoctorProfile` không tồn tại. |
| **`409 Conflict`** | Xung đột Idempotency, trùng lịch, hết chỗ hoặc cấu hình phòng sai | - Dùng lại `Idempotency-Key` cho yêu cầu khác.<br>- Bệnh nhân đã có lịch trong cùng khung giờ.<br>- Khung giờ đã đầy (`booked >= capacity`), khoa ngừng hoạt động, hoặc phòng có 0 / nhiều hơn 1 bác sĩ chính.<br>- Bảng giá thay đổi (`priceVersion` lệch) hoặc cập nhật lịch phòng làm giảm sức chứa dưới số lượng đã đặt. |
| **`422 Unprocessable Entity`** | Thiếu thông tin ca tái khám hoặc tài khoản bệnh nhân | - Tạo lịch tái khám thiếu `timeSlot` hoặc hồ sơ bệnh nhân chưa liên kết `userId`. |
| **`503 Service Unavailable`** | Fail-closed khi đối tác không phản hồi hợp lệ | - Không xác minh được hồ sơ từ `PatientClient`, phòng/bác sĩ/bảng giá từ `DirectoryClient`, hoặc trạng thái ca khám từ `AppointmentConsultationClient`. |

---

## 4. Mã Sơ Đồ Eraser.io (Copy & Paste Trực Tiếp Vào Eraser.io)

### 4.1. Sơ Đồ Sequence Đặt Lịch, Phân Phòng Tự Động & Khóa Slot (Eraser Sequence Diagram DSL)
> Mở **Eraser.io** → Chọn **Sequence Diagram** → Dán đoạn mã dưới đây vào khung code bên phải:

```
title CareFlow — Appointment Booking, Room Allocation & Slot Locking (SDD Spec)

Patient Mobile [icon: smartphone, color: purple]
Appointment Service [icon: calendar, color: blue]
Command & Slot Lock DB [icon: database, color: orange]
Patient Service [icon: user, color: teal]
Directory Service [icon: building, color: gray]
Outbox & RabbitMQ [icon: message-square, color: red]

// === PHASE 1: VERIFY OWNERSHIP & IDEMPOTENCY ===
Patient Mobile > Appointment Service: POST /api/appointments (Idempotency-Key, patientId, department, date, timeSlot)
activate Appointment Service
Appointment Service > Patient Service: GET /api/patients/{patientId}/booking-context
Patient Service --> Appointment Service: Verified (ownerUserId == actorUserId, fullName)
Appointment Service > Command & Slot Lock DB: AppointmentCommandStore.begin(ownerUserId, idempotencyKey, fingerprint)
alt Idempotency-Key already completed with same fingerprint
  Command & Slot Lock DB --> Appointment Service: Existing appointmentId
  Appointment Service --> Patient Mobile: 201 Created (Idempotent AppointmentResponse)
else New booking command
  Command & Slot Lock DB --> Appointment Service: Advisory lock acquired on scope & patient+date+slot
end

// === PHASE 2: ALLOCATE BEST ROOM & LOCK SLOT CAPACITY ===
Appointment Service > Directory Service: GET /api/v1/directory/departments/{code}, /rooms, /doctors
Directory Service --> Appointment Service: Active CONSULTATION rooms + single primary doctor per room
Appointment Service > Command & Slot Lock DB: RoomScheduleService.lockRooms() & Load room slot capacities
Appointment Service > Appointment Service: Select candidate room with highest remaining capacity
Appointment Service > Directory Service: GET /api/v1/directory/service-prices/{serviceCode}
Directory Service --> Appointment Service: Verified active price & priceVersion
Appointment Service > Command & Slot Lock DB: SELECT * FROM appointment_slot_locks WHERE slot_key = ? FOR UPDATE
Command & Slot Lock DB --> Appointment Service: Slot lock acquired (verify booked < capacity)

// === PHASE 3: PERSIST APPOINTMENT & APPEND OUTBOX ===
Appointment Service > Command & Slot Lock DB: Save Appointment (status = CONFIRMED) & Complete Idempotency Command
Appointment Service > Outbox & RabbitMQ: Append AppointmentConfirmed (Transactional Outbox)
Appointment Service --> Patient Mobile: 201 Created (AppointmentResponse - CONFIRMED)
deactivate Appointment Service
Outbox & RabbitMQ > Patient Mobile: Async publish AppointmentConfirmed -> Notification Service
```

---

## 5. Đánh Giá Tác Động GitNexus & Kế Hoạch Refactor Phẫu Thuật (Slices)

### 5.1. Điểm Mùi Code ("Code Smells") Đã Phát Hiện
1. **`AppointmentService.java` (840 dòng) ôm đồm quá nhiều trách nhiệm**:
   - Vừa xử lý luồng đặt lịch/hủy lịch/tái khám, vừa chứa toàn bộ thuật toán giải phân công Khoa–Phòng–Bác sĩ từ Directory (`resolveDirectoryAssignments`), chọn phòng còn nhiều chỗ trống nhất (`resolveBookingAssignment`), khóa slot DB (`enforceSlotCapacity`), và tổng hợp tình trạng chỗ trống theo ca (`getTimeSlotAvailability`).
2. **Logic kiểm tra chuyển trạng thái nằm rời khỏi Domain**:
   - Hàm `validateStatusTransition` và kiểm tra hủy lịch nằm rời trong `AppointmentService` thay vì thuộc về `AppointmentStatus` và `Appointment`.
3. **Code viết nén nhiều câu lệnh trên 1 dòng (Minified One-Liners)**:
   - [`RoomScheduleService.java`](file:///d:/PTIT/CareFlow/careflow-appointment-service/src/main/java/com/careflow/appointment/service/RoomScheduleService.java) (135 dòng) và [`AppointmentCommandStore.java`](file:///d:/PTIT/CareFlow/careflow-appointment-service/src/main/java/com/careflow/appointment/service/AppointmentCommandStore.java) (35 dòng) bị viết dính liền nhiều lệnh `if(...)throw...`, vòng lặp và câu SQL dài trên 1 dòng.
   - Các controller [`ClinicalAppointmentCompletionController.java`](file:///d:/PTIT/CareFlow/careflow-appointment-service/src/main/java/com/careflow/appointment/controller/ClinicalAppointmentCompletionController.java), [`FollowUpQueryController.java`](file:///d:/PTIT/CareFlow/careflow-appointment-service/src/main/java/com/careflow/appointment/controller/FollowUpQueryController.java), [`RoomScheduleController.java`](file:///d:/PTIT/CareFlow/careflow-appointment-service/src/main/java/com/careflow/appointment/controller/RoomScheduleController.java), [`AppointmentAnalyticsExportController.java`](file:///d:/PTIT/CareFlow/careflow-appointment-service/src/main/java/com/careflow/appointment/controller/AppointmentAnalyticsExportController.java) và nhiều đoạn trong `AppointmentService.java` cũng bị nén 1 dòng.

### 5.2. Lộ Trình 2 Lát Cắt Phẫu Thuật (Surgical Slices)
- **Slice 2.1a — Tách [`RoomSlotAllocationPolicy.java`](file:///d:/PTIT/CareFlow/careflow-appointment-service/src/main/java/com/careflow/appointment/service/RoomSlotAllocationPolicy.java) & Chuẩn hóa Domain [`Appointment`](file:///d:/PTIT/CareFlow/careflow-appointment-service/src/main/java/com/careflow/appointment/model/Appointment.java) / [`AppointmentStatus`](file:///d:/PTIT/CareFlow/careflow-appointment-service/src/main/java/com/careflow/appointment/model/AppointmentStatus.java)**:
  - Tách toàn bộ logic phân bổ phòng, kiểm tra bác sĩ phụ trách từ Directory, khóa `appointment_slot_locks`, và tính toán `TimeSlotAvailabilityResponse` sang `RoomSlotAllocationPolicy`.
  - Đưa `canTransitionTo(AppointmentStatus)` vào `AppointmentStatus` và các hàm chuyển trạng thái vào `Appointment`.
  - Giảm kích thước `AppointmentService.java` từ 840 dòng xuống ~560 dòng sạch sẽ, rõ ràng và tương thích 100% với các constructor trong 87 bài test.
- **Slice 2.1b — Giải nén & Làm sạch `RoomScheduleService`, `AppointmentCommandStore` và 4 Controllers**:
  - Format chuẩn 100%, tách các hàm kiểm tra nhỏ có tên tường minh trong `RoomScheduleService` và `AppointmentCommandStore`.
