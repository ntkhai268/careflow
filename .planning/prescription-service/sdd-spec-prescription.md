# Đặc Tả SDD (Spec-Driven Development) — `careflow-prescription-service`

> **Tài liệu Đặc tả Kỹ thuật & Nghiệp vụ (SDD Specification)**
> **Service**: `careflow-prescription-service` (Port `8089`)
> **Nhánh thực hiện**: `refactor/prescription-service`
> **Phiên bản Spec**: `SDD-1.0` (28/10/2026)

---

## 1. Bối cảnh & Ranh giới Trách nhiệm (Bounded Context)

`careflow-prescription-service` quản lý toàn bộ vòng đời **Đơn thuốc ngoại trú** của bệnh nhân từ lúc Bác sĩ tạo toa nháp (nhập tay hoặc qua giọng nói AI STT), chỉnh sửa, ký phát hành kèm đặt lịch tái khám, cho đến khi Dược sĩ tại Quầy Dược gọi số và xác nhận cấp phát thuốc.

### Sở hữu dữ liệu (Data Ownership)
- **`prescriptions`**: Bản ghi đơn thuốc (Ca khám `consultationId`, Bệnh nhân `patientId`, Bác sĩ kê đơn `doctorId`, Chẩn đoán `diagnosis`, Lời dặn `notes`, Trạng thái `PrescriptionStatus`, Điểm cấp phát thuốc `dispensingServicePointId`, Liên kết toa thay thế `replacesPrescriptionId`, Thông tin tái khám `followUpDate`, `followUpDepartment`, `followUpTimeSlot`, `followUpStatus`, `followUpAppointmentId`, `followUpErrorCode`).
- **`prescription_items`**: Danh sách các dòng thuốc trong toa (`medicineCode`, `medicineName`, `dosage`, `frequency`, `duration`, `quantity`, `unit`, `timing`, `notes`, `details` JSONB).
- **`drugs_dictionary`**: Danh mục thuốc chuẩn được phép kê tại bệnh viện (`code`, `name`, `unit`, `description`, `active`).
- **`prescription_ai_sources`**: Bản ghi đối chiếu nguồn Voice STT AI (`job_id`, `transcript_revision`, `prescription_id`, `consultation_id`, `actor_id`, `result_hash`) phục vụ kiểm soát chất lượng trước khi bác sĩ ký.
- **`prescription_event_outbox`**: Bảng Transactional Outbox đảm bảo phát hành sự kiện `PrescriptionIssued`, `PrescriptionCancelled`, `PrescriptionDispensed` tới RabbitMQ theo cơ chế *at-least-once*.

### Phụ thuộc Đối tác (Upstream / Downstream Collaborators)
| Service đối tác | Giao thức | Mục đích gọi / Tích hợp |
|---|---|---|
| `careflow-consultation-service` | Feign (`ConsultationClient`) | Xác minh trạng thái ca khám (`IN_PROGRESS`, `AWAITING_CLS`, `AWAITING_REVIEW`, `READY_TO_COMPLETE`, `COMPLETED`) và xác nhận bác sĩ hiện tại (`doctorId`) cùng `patientId`. |
| `careflow-hospital-directory-service` | Feign (`DirectoryClient`) | Lấy điểm cấp phát thuốc đang hoạt động (`getActivePharmacyServicePointId()`, mặc định `PHARMACY-MAIN-01`) khi ký đơn thuốc. |
| `careflow-appointment-service` | Feign (`AppointmentClient`) | Tự động đặt lịch hẹn tái khám (`schedule`) thông qua `PrescriptionFollowUpService` sau khi bác sĩ ký phát hành toa có `followUpDate`. |
| `careflow-queue-service` | Feign (`QueueExecutionClient`) | Kiểm tra lượt chờ phát thuốc `PHARMACY_DISPENSING` đang `IN_PROGRESS` tại đúng quầy dược và gọi `completePharmacyEntry` khi Dược sĩ phát thuốc. |
| `careflow-patient-service` | Feign (`PatientIdentityClient`) | Xác minh quyền sở hữu hồ sơ bệnh nhân khi `PATIENT` đọc toa thuốc của chính mình. |
| `careflow-ai-service` | HTTP (`PrescriptionAiClient`) | Lấy kết quả bóc tách đơn thuốc bằng giọng nói (`careflow.voice-prescription.v1`) trong `PrescriptionAiDraftService`. |

---

## 2. Máy Trạng Thái Đơn Thuốc & Hẹn Tái Khám (State Machines)

### 2.1. Tập Trạng Thái Đơn Thuốc (`PrescriptionStatus`)
```text
DRAFT ──(confirm / release)──► CONFIRMED ──(dispense)──► DISPENSED
  │                                │
  └──(cancel)──► CANCELLED         └──(amend)──► CANCELLED_BY_AMENDMENT (tạo DRAFT mới có replacesPrescriptionId)
```
- **`DRAFT`**: Toa nháp — bác sĩ có thể chỉnh sửa danh sách thuốc, lời dặn, ngày tái khám hoặc hủy trực tiếp. Bệnh nhân và Quầy dược chưa nhìn thấy toa `DRAFT`.
- **`CONFIRMED`**: Đã ký & phát hành — bất biến (không thể sửa trực tiếp hay hủy kiểu thường; nếu muốn sửa phải dùng `amendPrescription`). Kích hoạt tạo lượt chờ phát thuốc bên Queue.
- **`DISPENSED`**: Đã cấp phát thuốc cho bệnh nhân tại Quầy dược (Terminal state).
- **`CANCELLED`**: Toa nháp đã bị bác sĩ hủy (Terminal state).
- **`CANCELLED_BY_AMENDMENT`**: Toa đã ký bị thay thế bởi một toa đính chính mới (Terminal state).

### 2.2. Tập Trạng Thái Hẹn Tái Khám (`followUpStatus`)
- **`NOT_REQUESTED`**: Toa thuốc không có `followUpDate`.
- **`PENDING`**: Có `followUpDate`, đang chờ gọi `AppointmentService` sau khi ký toa.
- **`NEEDS_SELECTION`**: Có `followUpDate` nhưng thiếu `followUpDepartment` hoặc `followUpTimeSlot` (mã lỗi `SLOT_REQUIRED`).
- **`SCHEDULED`**: Đã tạo lịch hẹn tái khám thành công (`followUpAppointmentId != null`).
- **`FAILED`**: Gọi `AppointmentService` thất bại (`followUpErrorCode = APPOINTMENT_<status>`), cho phép bấm `POST /api/prescriptions/{id}/follow-up/retry` mà không ảnh hưởng đến toa thuốc đã ký.

### 2.3. Bảng Chuyển Đổi Trạng Thái & Điều Kiện Bất Biến (State Transition & Invariants Table)

| Từ Trạng Thái | Hành Động (Command) | Sang Trạng Thái | Điều Kiện Tiên Quyết (Pre-conditions & Invariants) | Mã Lỗi Khi Vi Phạm |
|---|---|---|---|---|
| *(Chưa tồn tại)* | `createPrescription` | `DRAFT` | 1. Actor phải là `DOCTOR` (`403`) và khớp `doctorId` trên ca khám (`403`).<br>2. Ca khám phải ở trạng thái mở (`IN_PROGRESS`, `AWAITING_CLS`, `AWAITING_REVIEW`, `READY_TO_COMPLETE`); nếu đã `COMPLETED`/`CANCELLED`/`TRANSFERRED` → `400`.<br>3. Mỗi thuốc nếu có `medicineCode` phải tồn tại trong `drugs_dictionary` (`400`); nếu kê bằng tên nháp (`medicineCode` rỗng) phải có `medicineName` (1..255 ký tự) và `quantity`/`duration > 0` nếu có nhập (`422`). | `400`, `403`, `422`, `503` |
| `DRAFT` | `updatePrescription` | `DRAFT` | 1. Actor phải là `DOCTOR` đã tạo toa (`403`), không được đổi `patientId`/`consultationId`/`doctorId` (`403`).<br>2. Toa phải đang ở `DRAFT` (khác `DRAFT` → `400`).<br>3. Ca khám chưa kết thúc/hủy (`400`); danh mục thuốc hợp lệ (`400`/`422`).<br>4. Reset `followUpStatus` (`NOT_REQUESTED` hoặc `PENDING`), xóa `followUpAppointmentId` & `followUpErrorCode`. | `400`, `403`, `422`, `503` |
| `DRAFT` | `confirmPrescription` | `CONFIRMED` | 1. Actor phải là `DOCTOR` sở hữu toa (`403`).<br>2. Nếu đã ở `CONFIRMED` hoặc `DISPENSED` → kiểm tra ca khám không bị `CANCELLED`/`TRANSFERRED` rồi trả về idempotent.<br>3. Danh sách thuốc không được rỗng (`400`).<br>4. Nếu là toa thường (`replacesPrescriptionId == null`): ca khám phải đang mở (`400`). Nếu là toa đính chính (`replacesPrescriptionId != null`): cho phép cả khi ca khám đã `COMPLETED` (chỉ chặn `CANCELLED`/`TRANSFERRED`).<br>5. **`PrescriptionAiReleaseGuard`**: Mọi thuốc kê bằng tên (không có `medicineCode`) phải có `manualReview = "confirmed"`, `unit`, và đủ liều/tần suất/thời điểm/số ngày/số lượng (`422`). Nếu toa có nguồn từ `prescription_ai_sources`, mọi dòng thuốc đều phải có đủ hướng dẫn dùng (`422`).<br>6. Gán `dispensingServicePointId` từ Directory, `confirmedAt = now()`, ghi Outbox `PrescriptionIssued`. | `400`, `403`, `422`, `503` |
| `CONFIRMED` | `amendPrescription` | Tạo toa `DRAFT` mới + Đổi toa cũ sang `CANCELLED_BY_AMENDMENT` | 1. Actor phải là `DOCTOR` sở hữu toa gốc (`403`).<br>2. Toa gốc phải đang ở `CONFIRMED` (nếu khác → `409`).<br>3. Ca khám không bị `CANCELLED`/`TRANSFERRED` (`400`), danh mục thuốc mới hợp lệ (`400`/`422`).<br>4. Lưu toa mới (`DRAFT`, `replacesPrescriptionId = original.id`), chuyển toa cũ sang `CANCELLED_BY_AMENDMENT` và ghi Outbox `PrescriptionCancelled`. | `400`, `403`, `409`, `422`, `503` |
| `DRAFT` | `cancelPrescription` | `CANCELLED` | 1. Actor phải là `DOCTOR` sở hữu toa và vẫn đang phụ trách ca khám (`403`).<br>2. Nếu đã `CANCELLED` → trả về idempotent.<br>3. Nếu ở `CANCELLED_BY_AMENDMENT`, `DISPENSED`, hoặc `CONFIRMED` → chặn với `409` (toa đã ký phải dùng `amend`).<br>4. Ghi Outbox `PrescriptionCancelled`. | `403`, `409`, `503` |
| `CONFIRMED` | `dispensePrescription` | `DISPENSED` | 1. Actor phải là `STAFF` (`403`).<br>2. Nếu đã `DISPENSED`: chỉ đúng `dispensedByUserId` cũ mới được gọi lại idempotent (`403` nếu nhân viên khác).<br>3. Nếu chưa `CONFIRMED` → `409`.<br>4. Hàng đợi dược (`QueueExecutionClient.getPharmacyEntry`) phải khớp `patientId`, `prescriptionId`, `type = PHARMACY_DISPENSING`, `queueStatus = IN_PROGRESS`, và khớp `dispensingServicePointId` (`409`).<br>5. Hoàn tất lượt chờ dược (`completePharmacyEntry`), set `status = DISPENSED`, ghi Outbox `PrescriptionDispensed`. | `403`, `409`, `503` |

---

## 3. Ma Trận Mã Lỗi Chuẩn (Error Matrix)

| HTTP Status | Ý nghĩa Nghiệp vụ | Tình huống Kích hoạt |
|---|---|---|
| **`400 Bad Request`** | Thao tác sai trạng thái cơ bản hoặc thuốc ngoài danh mục | - Tạo/cập nhật/ký toa thường khi ca khám đã `COMPLETED`, `CANCELLED` hoặc `TRANSFERRED`.<br>- Mã thuốc `medicineCode` không tồn tại trong `drugs_dictionary`.<br>- Ký toa rỗng (`items.isEmpty()`) hoặc cập nhật toa không còn ở `DRAFT`. |
| **`403 Forbidden`** | Vi phạm quyền hạn hoặc quyền sở hữu | - Actor không đúng vai trò (`DOCTOR` khi kê/sửa/ký/hủy, `STAFF` khi phát thuốc).<br>- Bác sĩ thao tác trên toa của bác sĩ khác hoặc đã bàn giao ca khám cho bác sĩ khác.<br>- Nhân viên dược khác gọi lại `dispense` trên toa đã `DISPENSED`. |
| **`404 Not Found`** | Không tìm thấy tài nguyên | - `prescriptionId` không tồn tại hoặc đọc nguồn AI của bác sĩ khác. |
| **`409 Conflict`** | Vi phạm State Machine hoặc điều kiện phối hợp | - Hủy trực tiếp toa đã `CONFIRMED` (bắt buộc phải qua `amend`), `DISPENSED` hoặc `CANCELLED_BY_AMENDMENT`.<br>- Gọi `amend` hoặc `dispense` trên toa không ở `CONFIRMED`.<br>- Phát thuốc khi Queue Entry tại quầy dược chưa ở trạng thái `IN_PROGRESS` hoặc lệch điểm dịch vụ. |
| **`422 Unprocessable Entity`** | Thiếu thông tin lâm sàng bắt buộc trên dòng thuốc / AI Draft | - Thuốc kê bằng tên nháp thiếu tên hoặc số lượng/số ngày `<= 0`.<br>- Khi ký toa (`PrescriptionAiReleaseGuard`): thuốc ngoài danh mục chưa xác nhận `manualReview=confirmed` hoặc dòng thuốc AI còn trống liều/tần suất/thời điểm/số ngày/số lượng. |
| **`503 Service Unavailable`** | Fail-closed khi đối tác không phản hồi hợp lệ | - Không xác minh được trạng thái ca khám từ `ConsultationClient`, lỗi lấy điểm dược từ `DirectoryClient`, hoặc lỗi serialize Outbox. |

---

## 4. Mã Sơ Đồ Eraser.io (Copy & Paste Trực Tiếp Vào Eraser.io)

### 4.1. Sơ Đồ Sequence Toàn Luồng Đơn Thuốc, Tái Khám & Phát Thuốc (Eraser Sequence Diagram DSL)
> Mở **Eraser.io** → Chọn **Sequence Diagram** → Dán đoạn mã dưới đây vào khung code bên phải:

```
title CareFlow — Prescription Service Lifecycle (SDD Spec)

Doctor Web [icon: monitor, color: purple]
Pharmacy Web [icon: tablet, color: teal]
Prescription Service [icon: file-text, color: pink]
Consultation Service [icon: server, color: blue]
Directory Service [icon: building, color: gray]
Appointment Service [icon: calendar, color: green]
Queue Service [icon: list, color: orange]
Outbox & RabbitMQ [icon: message-square, color: red]

// === PHASE 1: CREATE OR UPDATE DRAFT ===
Doctor Web > Prescription Service: POST /api/prescriptions (consultationId, patientId, items, followUp)
activate Prescription Service
Prescription Service > Consultation Service: GET /api/consultations/{consultationId}
Consultation Service --> Prescription Service: Verified (Open status, matching doctorId & patientId)
Prescription Service > Prescription Service: Validate items against drugs_dictionary or named draft rules
Prescription Service > Prescription Service: Save Prescription (status = DRAFT, followUpStatus = PENDING/NOT_REQUESTED)
Prescription Service --> Doctor Web: 201 Created (PrescriptionResponse - DRAFT)
deactivate Prescription Service

// === PHASE 2: RELEASE (SIGN & SCHEDULE FOLLOW-UP) ===
Doctor Web > Prescription Service: POST /api/prescriptions/{id}/release
activate Prescription Service
Prescription Service > Consultation Service: GET /api/consultations/{consultationId}
Consultation Service --> Prescription Service: Verified Consultation Context
Prescription Service > Prescription Service: PrescriptionAiReleaseGuard.validate(prescription)
Prescription Service > Directory Service: GET /api/v1/directory/service-points/pharmacy/active
Directory Service --> Prescription Service: dispensingServicePointId (PHARMACY-MAIN-01)
Prescription Service > Prescription Service: Transition DRAFT -> CONFIRMED & Append Outbox (PrescriptionIssued)
opt Has followUpDate (Non-spanning TX via PrescriptionFollowUpService)
  Prescription Service > Appointment Service: POST /api/appointments/follow-up (schedule)
  Appointment Service --> Prescription Service: followUpAppointmentId (or FAILED status if slot full)
  Prescription Service > Prescription Service: Update followUpStatus = SCHEDULED / NEEDS_SELECTION / FAILED
end
Prescription Service --> Doctor Web: 200 OK (CONFIRMED + Follow-up result)
deactivate Prescription Service
Prescription Service > Outbox & RabbitMQ: Async publish PrescriptionIssued -> Queue creates PHARMACY_DISPENSING entry

// === PHASE 3: AMEND CONFIRMED PRESCRIPTION (OPTIONAL) ===
Doctor Web > Prescription Service: POST /api/prescriptions/{id}/amendments
activate Prescription Service
Prescription Service > Consultation Service: GET /api/consultations/{consultationId}
Consultation Service --> Prescription Service: Verified (not CANCELLED/TRANSFERRED)
Prescription Service > Prescription Service: Create replacement DRAFT (replacesPrescriptionId = old.id)
Prescription Service > Prescription Service: Mark old CONFIRMED -> CANCELLED_BY_AMENDMENT & Append Outbox (PrescriptionCancelled)
Prescription Service --> Doctor Web: 201 Created (Replacement DRAFT)
deactivate Prescription Service

// === PHASE 4: PHARMACY DISPENSE ===
Pharmacy Web > Prescription Service: POST /api/prescriptions/{id}/dispense
activate Prescription Service
Prescription Service > Queue Service: GET /api/queues/pharmacy/prescription/{id}
Queue Service --> Prescription Service: Verified (PHARMACY_DISPENSING, status = IN_PROGRESS, matching servicePointId)
Prescription Service > Queue Service: POST /api/queues/pharmacy/prescription/{id}/complete
Queue Service --> Prescription Service: Queue Entry COMPLETED
Prescription Service > Prescription Service: Transition CONFIRMED -> DISPENSED & Append Outbox (PrescriptionDispensed)
Prescription Service --> Pharmacy Web: 200 OK (status = DISPENSED)
deactivate Prescription Service
```

---

## 5. Đánh Giá Tác Động GitNexus & Kế Hoạch Refactor 4 Lát Cắt (Slices)

### 5.1. Điểm Mùi Code ("Code Smells") Đã Phát Hiện
1. **Truy vấn khóa DB 2 lần liên tiếp (Double Pessimistic Lock Lookup)**:
   - Trong [`PrescriptionService.updatePrescription(id, request, actorUserId, actorRole)`](file:///d:/PTIT/CareFlow/careflow-prescription-service/src/main/java/com/careflow/prescription/service/PrescriptionService.java#L329-L343), hàm gọi `findPrescriptionForUpdateOrThrow(id)` ở dòng 332 để kiểm tra quyền, sau đó ở dòng 342 lại gọi `updatePrescription(id, request)` khiến dòng 193 gọi `findPrescriptionForUpdateOrThrow(id)` **lần thứ 2**!
2. **Dư thừa các hàm Overload không kiểm tra quyền (Dead/Legacy Overloads)**:
   - `getPrescription(UUID id)` (dòng 183), `confirmPrescription(UUID id)` (dòng 479), `getByConsultation(UUID consultationId)` (dòng 487), `getByPatient(UUID patientId)` (dòng 507) chỉ là các vỏ bọc cũ hoặc chỉ được gọi bởi bài test mock cũ.
3. **Trộn lẫn `@Autowired` Field Injection & Thừa Dependency**:
   - `PrescriptionService` dùng `@RequiredArgsConstructor` nhưng lại đặt `@Autowired` field rời cho `eventOutbox` và `aiReleaseGuard` (dòng 50–51).
   - Biến `private final AppointmentClient appointmentClient` (dòng 44) được khai báo trong `PrescriptionService` nhưng **không hề được sử dụng ở bất kỳ dòng nào** (do trách nhiệm đặt lịch tái khám đã nằm ở `PrescriptionFollowUpService`).
4. **Code viết nén 1 dòng (Minified One-Liners)**:
   - [`PrescriptionFollowUpService.java`](file:///d:/PTIT/CareFlow/careflow-prescription-service/src/main/java/com/careflow/prescription/service/PrescriptionFollowUpService.java) (36 dòng), [`PrescriptionReleaseController.java`](file:///d:/PTIT/CareFlow/careflow-prescription-service/src/main/java/com/careflow/prescription/controller/PrescriptionReleaseController.java) (24 dòng), [`PrescriptionAiReleaseGuard.java`](file:///d:/PTIT/CareFlow/careflow-prescription-service/src/main/java/com/careflow/prescription/service/PrescriptionAiReleaseGuard.java) (16 dòng), [`PrescriptionAiDraftController.java`](file:///d:/PTIT/CareFlow/careflow-prescription-service/src/main/java/com/careflow/prescription/controller/PrescriptionAiDraftController.java) (10 dòng), [`PrescriptionAiDraftService.java`](file:///d:/PTIT/CareFlow/careflow-prescription-service/src/main/java/com/careflow/prescription/service/PrescriptionAiDraftService.java) (91 dòng) và [`PrescriptionEventOutbox.java`](file:///d:/PTIT/CareFlow/careflow-prescription-service/src/main/java/com/careflow/prescription/service/PrescriptionEventOutbox.java) (46 dòng) đều bị viết dính liền nhiều câu lệnh trên cùng 1 dòng.
5. **Trùng lặp logic kiểm tra thuốc & khởi tạo trạng thái tái khám**:
   - `validateMedicineItems` (dòng 91–103) và `validateAmendmentMedicineItems` (dòng 109–120) lặp lại 95% cấu trúc kiểm tra `drugs_dictionary`.
   - Biểu thức `request.getFollowUpDate()==null?"NOT_REQUESTED":"PENDING"` lặp lại ở cả `createPrescription`, `updatePrescription`, và `amendPrescription`.

### 5.2. Lộ Trình 3 Lát Cắt Phẫu Thuật (Surgical Slices)
- **Slice 1.2a — Làm sạch `PrescriptionService` & Domain `Prescription`**:
  - Gộp `updatePrescription` để chỉ gọi `findPrescriptionForUpdateOrThrow(id)` đúng 1 lần duy nhất.
  - Đưa logic gán thông tin tái khám (`applyFollowUpRequest`) và thay thế danh sách thuốc (`replaceItems`) vào Domain [`Prescription.java`](file:///d:/PTIT/CareFlow/careflow-prescription-service/src/main/java/com/careflow/prescription/model/Prescription.java).
  - Hợp nhất hàm kiểm tra danh mục thuốc `validateMedicineItems(List<PrescriptionItemRequest>)`.
- **Slice 1.2b — Giải nén & Chuẩn hóa `PrescriptionFollowUpService`, `PrescriptionReleaseController` & `PrescriptionEventOutbox`**:
  - Format chuẩn, tách hằng số trạng thái tái khám và làm rõ luồng *Sign-first, Schedule-FollowUp-second* (ký toa thành công kể cả khi đặt lịch tái khám lỗi).
- **Slice 1.2c — Giải nén & Làm sạch cụm AI Voice Prescription (`PrescriptionAiReleaseGuard`, `PrescriptionAiDraftService`, `PrescriptionAiDraftController`)**:
  - Format chuẩn toàn bộ các dòng code bị ép 1 dòng, tách các hàm nhỏ đọc hiểu ngay lập tức mà vẫn giữ nguyên 100% hành vi và 107/107 bài test.
