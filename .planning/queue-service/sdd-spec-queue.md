# Spec-Driven Development (SDD): Queue Service (`careflow-queue-service`)

> **Bounded Context**: Hàng đợi khám lâm sàng theo phòng (`CONSULTATION`: `INITIAL` & `RESULT_REVIEW`), Tiếp nhận & Check-in (QR + Haversine Geofence / Staff QR / Ticket Code / Manual Walk-in), và Hàng đợi FIFO tại điểm phục vụ Cận lâm sàng (`LAB_EXECUTION`) & Quầy phát thuốc (`PHARMACY_DISPENSING`).
> **Schema Ownership**: `queue` (`queue_configs`, `queue_entries`, `queue_number_sequences`, `service_point_sequences`, `idempotency_records`, `hospital_check_in_configs`, `room_policy_audit`, `outbox_events`, `processed_events`)
> **Port**: `8084`

---

## 1. Bounded Context & Data Ownership

### 1.1. Bảng CSDL thuộc quyền sở hữu (`queue` schema)
| Bảng | Vai trò | Ghi chú |
|---|---|---|
| `queue.queue_configs` | Cấu hình hàng đợi theo phòng (`room_code`) và khoa (`department_id`): `queue_prefix`, `avg_consultation_minutes`, `near_turn_threshold`, `missed_policy`, `last_served_lane`, `policy_version`, `is_active` | Khóa Pessimistic (`FOR UPDATE`) khi cấp số / gọi số tiếp theo |
| `queue.queue_entries` | Bản ghi lượt hàng đợi (`CONSULTATION`, `LAB_EXECUTION`, `PHARMACY_DISPENSING`) | Snapshot tên bệnh nhân, phòng, trạng thái, thông tin check-in (tọa độ, khoảng cách, phương thức) |
| `queue.queue_number_sequences` | Bộ đếm số thứ tự theo `(queue_config_id, queue_date)` | Sinh mã `queue_prefix + "-" + %03d` |
| `queue.service_point_sequences` | Bộ đếm số thứ tự theo `(service_point_id, queue_date)` | Sinh mã `LAB-%03d` hoặc `RX-%03d` |
| `queue.idempotency_records` | Lưu trữ Idempotency-Key + SHA-256 request fingerprint cho các lệnh ghi (`MANUAL_INTAKE`, `CALL_NEXT`, `CALL_ENTRY`, `CALL_NEXT_SERVICE_POINT`) | Hỗ trợ cả replay kết quả rỗng (`empty_result = true`) khi hàng đợi trống |
| `queue.hospital_check_in_configs` | Cấu hình tọa độ tâm Geofence bệnh viện (`latitude`, `longitude`, `radius_meters`, `max_accuracy_meters`) | Phục vụ kiểm tra khoảng cách Haversine khi bệnh nhân tự quét QR |
| `queue.room_policy_audit` | Nhật ký thay đổi chính sách hàng đợi phòng khám (`policy_version`, `actor_id`, `settings` JSONB) | Ghi nhận mỗi lần Admin cập nhật cấu hình phòng |
| `queue.outbox_events` / `processed_events` | Transactional Outbox & Consumer Idempotency | Đảm bảo gửi/nhận sự kiện RabbitMQ an toàn |

### 1.2. Liên kết ngoại vi (Cross-Service References)
- `DirectoryClient` / `ReceptionRoomClient` (`careflow-hospital-directory-service`): Kiểm tra phòng hoạt động (`roomType = CONSULTATION`), khoa hoạt động, phân công bác sĩ (`getDoctorByUserId`), và quyền truy cập phòng/service point của nhân viên (`hasStaffRoomAccess`).
- `PatientIntakeClient` / `PatientOwnershipClient` (`careflow-patient-service`): Xác minh chủ sở hữu hồ sơ bệnh nhân (`owner`) và lấy thông tin tối thiểu của bệnh nhân khi tiếp nhận tại phòng (`minimum`).

---

## 2. State Machine & Invariants

### 2.1. Vòng đời `QueueStatus` (`QueueEntry`)
```
                  ┌─── (AppointmentConfirmed) ───┐
                  ▼                              │
              [WAITING] ──(AppointmentCancelled)─┼──► [CANCELLED]
                  │                              │
     (QR/Staff Check-in)                         │
                  ▼                              │
  ┌────────► [CHECKED_IN] (INITIAL)              │
  │               │                              │
  │          (Call / CallNext)                   │
  │               ▼                              │
  │           [CALLED] ◄──(Recall < 3 lần)───────┤
  │            │    │                            │
  │ (CallAttempts   │ (Start)                    │
  │   >= 3 & Miss)  ▼                            │
  │            │  [IN_PROGRESS]                  │
  │            ▼         │                       │
  └──(Requeue)─[MISSED]  └──(Complete)──► [COMPLETED]
```
- Với các lượt `RESULT_REVIEW`, `LAB_EXECUTION`, và `PHARMACY_DISPENSING`:
  - Trạng thái khởi tạo chờ gọi là `QUEUED` (thay vì `CHECKED_IN`).
  - Lượt `PHARMACY_DISPENSING` chỉ chuyển sang `COMPLETED` khi nhận sự kiện `PrescriptionDispensed` hoặc xác nhận phát thuốc (`completePharmacyEntry`), và chuyển sang `CANCELLED` khi nhận `PrescriptionCancelled`.

### 2.2. Bộ lập lịch 3 làn khám lâm sàng (`ConsultationQueueScheduler`)
- Áp dụng cho `queueType == CONSULTATION` đang chờ gọi (`isWaitingForCall()`):
  1. `PRIORITY` (`consultationPhase == INITIAL && queueClass == PRIORITY`)
  2. `NORMAL` (`consultationPhase == INITIAL && queueClass == NORMAL`)
  3. `RESULT_REVIEW` (`consultationPhase == RESULT_REVIEW`)
- Trong mỗi làn, các lượt được sắp xếp theo `eligibleSinceAt ASC, sequenceNumber ASC`.
- Thuật toán chọn lượt kế tiếp: **Round-Robin** xoay vòng qua 3 làn không rỗng bắt đầu sau `config.lastServedLane` (`PRIORITY → NORMAL → RESULT_REVIEW`).
- Khi gọi bệnh nhân (`callNext` hoặc `call`), hệ thống tính toán danh sách chờ dự kiến và phát sự kiện `QueueNearTurn` cho tối đa `nearTurnThreshold` bệnh nhân đầu hàng chưa được thông báo (`nearTurnNotifiedAt == null`).

### 2.3. Quy tắc Check-in (QR & Geofence Haversine)
- **Bệnh nhân tự check-in (`PATIENT_QR_GEOFENCE`)**:
  - Quét mã QR bệnh viện (`checkInQrToken`) hợp lệ trong ngày (`sessionDate == businessDate`) và đúng `siteId`.
  - Kiểm tra quyền sở hữu `patientId` của lịch hẹn (`appointmentId`).
  - Kiểm tra tọa độ GPS hợp lệ (`latitude ∈ [-90, 90]`, `longitude ∈ [-180, 180]`, `0 < accuracyMeters <= maxAccuracyMeters`).
  - Tính khoảng cách Haversine từ vị trí thiết bị đến tâm bệnh viện; từ chối với HTTP `422` nếu `distanceMeters > radiusMeters`.
- **Nhân viên tiếp nhận check-in (`STAFF_QR` / `STAFF_MANUAL`)**:
  - Bắt buộc truyền `roomId` và có quyền truy cập phòng (`requireRoomAccess`).
  - Hỗ trợ quét QR trên phiếu hẹn của bệnh nhân (`qrToken`) hoặc nhập mã phiếu (`ticketCode`).
  - Khóa `pg_advisory_xact_lock` theo `(patientId, departmentId, businessDate)` để chống tạo 2 lượt khám hoạt động cùng khoa trong ngày.

---

## 3. Kiến trúc Module sau Refactor (`careflow-queue-service`)

| Lớp / Thành phần | Trách nhiệm |
|---|---|
| `QueueManagementService` | Facade điều phối giao dịch hàng đợi khám lâm sàng (`manualIntake`, `dashboard`, `callNext`, `call`, `recall`, `miss`, `requeue`, `start`, `complete`, `createAppointmentEntry`, `createResultReviewEntry`) |
| `QueueCheckInSupport` | Tách biệt toàn bộ nghiệp vụ phát hành QR bệnh viện, kiểm tra tọa độ Haversine Geofence, check-in bệnh nhân/nhân viên và kích hoạt lượt `WAITING → CHECKED_IN` |
| `ServicePointQueueSupport` | Tách biệt toàn bộ nghiệp vụ hàng đợi FIFO tại điểm phục vụ CLS (`LAB_EXECUTION`) và Nhà thuốc (`PHARMACY_DISPENSING`) |
| `QueueAccessValidator` | Tách biệt kiểm tra phân quyền & phân công phòng khám (`requireRoomAccess`), điểm phục vụ (`requireServicePointAccess`), và bác sĩ lâm sàng (`requireClinicalOperator`, `requireEntryAssignmentAccess`) |
| `ConsultationQueueScheduler` | Thuật toán lập lịch Round-Robin 3 làn (`PRIORITY`, `NORMAL`, `RESULT_REVIEW`) và ước lượng thời gian chờ |
| `QueueRoomConfigurationService` | Quản lý cấu hình hàng đợi theo phòng (`ensure`, `get`, `save`), khóa advisory lock bệnh nhân và ghi audit log |
| `ReceptionIntakeService` | Xác minh phòng, phân công nhân viên, chủ sở hữu bệnh nhân và dịch vụ khám trước khi tạo lượt tiếp nhận trực tiếp (`manualIntake`) |

---

## 4. Error Matrix

| HTTP Status | Tình huống | Thông điệp chuẩn |
|---|---|---|
| `400 Bad Request` | Thiếu `Idempotency-Key`, thiếu thông tin QR/mã phiếu, thiếu `roomId` khi staff check-in, thiếu `position` khi `REQUIRE_MANUAL` | Thông điệp chi tiết theo trường |
| `401 Unauthorized` | Thiếu người thực hiện hoặc QR phiếu hẹn không khớp ticket/user | `"Thiếu người tiếp nhận đã xác thực"` / `"QR không khớp phiếu khám"` |
| `403 Forbidden` | Sai vai trò, chưa được phân công vào phòng/service point, hoặc không phải chủ hồ sơ bệnh nhân | `"Actor chưa được phân công vào phòng này"` / `"Chỉ bác sĩ phụ trách..."` |
| `404 Not Found` | Không tìm thấy `QueueEntry`, `QueueConfig`, `VisitTicket` | `ResourceNotFoundException` |
| `409 Conflict` | Chuyển trạng thái không hợp lệ, chưa gọi đủ 3 lần đã đánh dấu `MISSED`, trùng lượt khám hoạt động trong khoa, hoặc xung đột `Idempotency-Key` | `"Không thể chuyển QueueEntry từ ... sang ..."` |
| `422 Unprocessable Entity` | QR khác ngày hiện tại, tọa độ GPS thiếu/sai, độ chính xác GPS vượt ngưỡng, hoặc ngoài bán kính Geofence | `"Bạn đang ở ngoài khu vực bệnh viện, chưa thể check-in"` |
| `503 Service Unavailable` | Không kết nối được Directory Service hoặc Patient Service | `"Không thể xác minh assignment của actor với phòng"` |

---

## 5. Sequence Diagrams (Eraser.io DSL)

```eraser
title Luồng Check-in Bệnh nhân (QR + Geofence) & Gọi số Khám Lâm Sàng 3 Làn

actor Patient
actor Doctor
participant "QueueController" as QC
participant "QueueManagementService" as QMS
participant "QueueCheckInSupport" as QCS
participant "ConsultationQueueScheduler" as CQS
participant "QueueEventService" as QES
database "PostgreSQL (queue)" as DB

Patient -> QC: POST /api/queues/check-in (appointmentId, checkInQrToken, lat, lon, accuracy)
QC -> QMS: checkIn(request, userId, "PATIENT", correlationId)
QMS -> QCS: checkInAsPatient(request, userId, correlationId)
QCS -> DB: Verify Hospital QR + Load QueueEntry & QueueConfig
QCS -> QCS: Validate GPS accuracy & Haversine distance <= radiusMeters
QCS -> DB: Update QueueEntry (WAITING -> CHECKED_IN, eligibleSinceAt = now)
QCS -> QES: append("PatientCheckedIn", queue.checked_in)
QCS --> QC: QueueEntryResponse (position, estimatedWaitMinutes)

Doctor -> QC: POST /api/queues/rooms/{roomId}/call-next (Idempotency-Key)
QC -> QMS: callNextInRoom(roomId, doctorId, "DOCTOR", idempotencyKey, correlationId)
QMS -> DB: Lock QueueConfig FOR UPDATE & check IdempotencyRecord
QMS -> CQS: next(waitingEntries, config.lastServedLane)
CQS --> QMS: Selected QueueEntry (Round-Robin PRIORITY -> NORMAL -> RESULT_REVIEW)
QMS -> DB: Update QueueEntry (status = CALLED, callAttempts = 1) & QueueConfig (lastServedLane)
QMS -> QES: append("PatientCalled", queue.called)
QMS -> QES: append("QueueNearTurn", queue.near_turn) [top N waiting]
QMS --> QC: 200 OK (QueueEntryResponse)
```
