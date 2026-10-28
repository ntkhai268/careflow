# Đặc Tả SDD (Spec-Driven Development) — `careflow-consultation-service`

> **Tài liệu Đặc tả Kỹ thuật & Nghiệp vụ (SDD Specification)**
> **Service**: `careflow-consultation-service` (Port `8088`)
> **Nhánh thực hiện**: `refactor/consultation-service`
> **Phiên bản Spec**: `SDD-1.0` (27/10/2026)

---

## 1. Bối cảnh & Ranh giới Trách nhiệm (Bounded Context)

`careflow-consultation-service` là **trái tim lâm sàng** của hệ thống CareFlow, điều phối phiên khám ngoại trú từ lúc Bác sĩ bấm **"Bắt đầu khám"** cho đến khi **Kết luận & Hoàn tất lượt khám**.

### Sở hữu dữ liệu (Data Ownership)
- **`consultations`**: Bản ghi phiên khám (Bác sĩ phụ trách gốc `doctorId`, Bác sĩ chịu trách nhiệm hiện tại `responsibleDoctorId`, Bệnh nhân `patientId`, Phiếu hàng đợi `queueEntryId`, Lịch hẹn `appointmentId`, Sinh hiệu, Triệu chứng, Ghi chú lâm sàng, Mã chẩn đoán ICD-10, Trạng thái `ConsultationStatus`).
- **`consultation_handovers`**: Yêu cầu và lịch sử bàn giao ca khám chuẩn SBAR giữa các bác sĩ cùng phòng/khoa.
- **`consultation_handover_coordinators` & `consultation_coordinator_permission_audit`**: Quyền điều phối bàn giao y khoa và nhật ký cấp/thu hồi quyền.
- **`consultation_event_outbox`**: Bảng Transactional Outbox đảm bảo gửi sự kiện `ConsultationCompleted` tới RabbitMQ theo cơ chế *at-least-once*.

### Phụ thuộc Đối tác (Upstream / Downstream Collaborators)
| Service đối tác | Giao thức | Mục đích gọi / Tích hợp |
|---|---|---|
| `careflow-queue-service` | Feign (`ConsultationQueueClient`) | Xác minh phiếu khám (`CALLED` / `IN_PROGRESS`), lấy danh sách các entry cần đóng (`INITIAL`, `RESULT_REVIEW`) và gọi `complete` khi hoàn tất. |
| `careflow-appointment-service` | Feign (`AppointmentClient`) | Xác minh lịch hẹn (`CONFIRMED` / `CHECKED_IN` / `IN_PROGRESS`) khi bắt đầu khám hẹn trước; gọi `completeClinical` khi hoàn tất ca khám. |
| `careflow-hospital-directory-service` | Feign (`DirectoryClient`) | Snapshot thông tin Bác sĩ & Khoa (`doctorName`, `departmentCode`, `departmentName`) vào ca khám; kiểm tra cùng phòng/khoa khi bàn giao SBAR. |
| `careflow-lab-service` | Feign (`ConsultationLabClient`) | Kiểm tra danh sách `LabOrder` của ca khám trước khi chuyển chờ kết quả (`waitForResults`), khi tiếp tục khám (`resume`), và trước khi hoàn tất (`completeConsultation`). |
| `careflow-prescription-service` | Feign (`ConsultationPrescriptionClient`) | Kiểm tra trạng thái đơn thuốc thông qua `ClinicalCompletionGuard` (chặn hoàn tất nếu bác sĩ hiện tại còn đơn `DRAFT` chưa ký/hủy). |
| `careflow-patient-service` | Feign (`PatientOwnershipClient`) | Xác minh quyền sở hữu hồ sơ bệnh nhân khi `PATIENT` xem chi tiết/lịch sử ca khám (kể cả ca khám vãng lai không có `appointmentId`). |

---

## 2. Máy Trạng Thái Phiên Khám (Consultation State Machine)

### 2.1. Tập Trạng Thái (`ConsultationStatus`)
- **`IN_PROGRESS`**: Đang khám lâm sàng (trạng thái khởi tạo ngay khi tạo phiên khám hoặc sau khi bác sĩ tiếp tục khám từ bước đọc kết quả CLS).
- **`AWAITING_CLS`**: Đang chờ thực hiện Cận lâm sàng (Lab).
- **`AWAITING_REVIEW`**: Đã có đủ kết quả CLS bắt buộc, đang chờ bác sĩ gọi lại vào phòng để đọc kết quả.
- **`READY_TO_COMPLETE`**: Sẵn sàng hoàn tất (trạng thái trung gian tùy chọn trước khi đóng ca).
- **`COMPLETED`**: Đã hoàn tất ca khám (Terminal state — bất biến đối với cập nhật thông thường).
- **`CANCELLED`**: Đã hủy phiên khám (Terminal state).
- **`TRANSFERRED`**: Chuyển viện / Chuyển khoa.

### 2.2. Bảng Chuyển Đổi Trạng Thái & Điều Kiện Bất Biến (State Transition & Invariants Table)

| Từ Trạng Thái | Hành Động (Command) | Sang Trạng Thái | Điều Kiện Tiên Quyết (Pre-conditions & Invariants) | Mã Lỗi Khi Vi Phạm |
|---|---|---|---|---|
| *(Chưa tồn tại)* | `createConsultation` | `IN_PROGRESS` | 1. Phải có `appointmentId` hoặc `queueEntryId` (nếu thiếu cả hai → `400`).<br>2. Khóa tải `DoctorWorkloadLock` theo `doctorId` và `queueEntryId`.<br>3. Idempotent theo `queueEntryId`: nếu đã có ca khám khớp `doctorId`, `patientId`, `appointmentId` → trả về ca hiện có (lệch → `403`).<br>4. Phiếu Queue phải ở trạng thái `CALLED` hoặc `IN_PROGRESS` (phase `INITIAL` đối với `clinicalContext`). Chưa gọi số → `409`.<br>5. Nếu có `appointmentId`: Appointment phải thuộc đúng `patientId` & `doctorId` (`403`) và ở trạng thái `CONFIRMED`, `CHECKED_IN` hoặc `IN_PROGRESS` (`409`).<br>6. Nếu không có `appointmentId` (Walk-in): `verifiedName` từ Queue không được rỗng (`409`).<br>7. Bác sĩ không được có ca khám khác đang `IN_PROGRESS` (`400`). | `400`, `403`, `409`, `503` |
| `IN_PROGRESS` | `waitForResults` | `AWAITING_CLS` | 1. Actor phải là bác sĩ đang phụ trách (`currentDoctorId()`) (`403`).<br>2. Nếu đã ở `AWAITING_CLS` → trả về idempotent.<br>3. Phải đang ở `IN_PROGRESS` (nếu khác → `409`).<br>4. Bắt buộc phải có ít nhất 1 `LabOrder` gắn với ca khám (`!labOrders.isEmpty()`, nếu rỗng → `409`). | `403`, `409`, `503` |
| `AWAITING_CLS` hoặc `AWAITING_REVIEW` | `resume` | `IN_PROGRESS` | 1. Actor phải là `currentDoctorId()` (`403`).<br>2. Nếu đã ở `IN_PROGRESS` → trả về idempotent.<br>3. Phải đang ở `AWAITING_CLS` hoặc `AWAITING_REVIEW` (`409`).<br>4. Phải có `LabOrder` và **mọi** `LabOrder` đều thuộc `{RESULT_AVAILABLE, REVIEWED, CANCELLED}` (còn `ORDERED`/`IN_PROGRESS` → `409`).<br>5. Bác sĩ không có ca khám nào khác đang `IN_PROGRESS` (`409`). | `403`, `409`, `503` |
| `IN_PROGRESS` hoặc `READY_TO_COMPLETE` | `completeConsultation` | `COMPLETED` | 1. Actor phải là `currentDoctorId()` (`403`).<br>2. Nếu đã ở `COMPLETED` → trả về idempotent.<br>3. Không được ở `CANCELLED` (`400`), `AWAITING_CLS` hoặc `AWAITING_REVIEW` (`409`).<br>4. **`ClinicalCompletionGuard`**: `diagnosis` không được rỗng (`409`); không còn đơn thuốc `DRAFT` nào do `currentDoctorId()` tạo (`409`).<br>5. Mọi `LabOrder` của ca khám (nếu có) phải ở trạng thái `REVIEWED` hoặc `CANCELLED` (còn `RESULT_AVAILABLE` chưa đọc → `409`).<br>6. Set `completedAt = now()`, ghi Outbox `ConsultationCompleted`. | `400`, `403`, `409`, `503` |
| `IN_PROGRESS`, `AWAITING_CLS`, `AWAITING_REVIEW`, `READY_TO_COMPLETE` | `updateClinicalData` | *(Giữ nguyên)* | 1. Actor phải là `currentDoctorId()` (`403`).<br>2. Trạng thái hiện tại không thuộc `{COMPLETED, CANCELLED}` (`400`). | `400`, `403` |

---

## 3. Ma Trận Mã Lỗi Chuẩn (Error Matrix)

| HTTP Status | Ý nghĩa Nghiệp vụ | Tình huống Kích hoạt |
|---|---|---|
| **`400 Bad Request`** | Dữ liệu đầu vào hoặc thao tác sai quy trình cơ bản | - Thiếu cả `appointmentId` lẫn `queueEntryId` khi tạo ca khám.<br>- Bác sĩ đang có một ca khám khác ở trạng thái `IN_PROGRESS` khi tạo ca mới.<br>- Cập nhật thông tin hoặc đổi trạng thái ca khám đã `COMPLETED` / `CANCELLED`. |
| **`403 Forbidden`** | Vi phạm quyền hạn hoặc quyền sở hữu ca khám | - Actor không phải `DOCTOR` hoặc `doctorId` gửi lên khác `X-User-Id`.<br>- Bác sĩ thao tác trên ca khám không thuộc `currentDoctorId()` của mình.<br>- `patientId` / `appointmentId` không khớp giữa Request, Queue Ticket và Appointment.<br>- Bệnh nhân (`PATIENT`) đọc ca khám của người khác. |
| **`404 Not Found`** | Không tìm thấy tài nguyên | - `consultationId` hoặc `handoverId` không tồn tại. |
| **`409 Conflict`** | Vi phạm State Machine hoặc điều kiện lâm sàng | - Bắt đầu khám khi phiếu Queue chưa được gọi (`TICKET_ISSUED`, `CHECKED_IN`, `CANCELLED`, `NO_SHOW`).<br>- Chuyển `AWAITING_CLS` khi chưa kê chỉ định xét nghiệm nào.<br>- Gọi `resume` khi kết quả xét nghiệm vẫn đang chờ (`ORDERED`, `IN_PROGRESS`).<br>- Gọi `complete` khi chưa nhập `diagnosis`, còn toa thuốc `DRAFT` của bác sĩ hiện tại, hoặc còn kết quả xét nghiệm chưa `REVIEWED`. |
| **`422 Unprocessable Entity`** | Vi phạm điều kiện danh mục / điều phối | - Bàn giao ca khám cho bác sĩ không hoạt động, khác phòng/khoa, hoặc tự bàn giao cho chính mình. |
| **`503 Service Unavailable`** | Fail-closed khi không xác minh được với service đối tác | - Queue, Appointment, Lab, Prescription, Directory hoặc Patient Ownership trả về `null`, lỗi mạng Feign, hoặc dữ liệu đối chiếu không nhất quán. |

---

## 4. Mã Sơ Đồ Eraser.io (Copy & Paste Trực Tiếp Vào Eraser.io)

### 4.1. Sơ Đồ Sequence Toàn Luồng Khám Bệnh (Eraser Sequence Diagram DSL)
> Mở **Eraser.io** → Chọn **Sequence Diagram** → Dán đoạn mã dưới đây vào khung code bên phải:

```
title CareFlow — Consultation Service Lifecycle (SDD Spec)

Doctor Web [icon: monitor, color: purple]
Consultation Service [icon: server, color: blue]
Queue Service [icon: list, color: orange]
Appointment Service [icon: calendar, color: teal]
Directory Service [icon: building, color: gray]
Lab Service [icon: activity, color: green]
Prescription Service [icon: file-text, color: pink]
Outbox & RabbitMQ [icon: message-square, color: red]

// === PHASE 1: START CONSULTATION ===
Doctor Web > Consultation Service: POST /api/consultations (queueEntryId, appointmentId, patientId)
activate Consultation Service
Consultation Service > Consultation Service: Acquire DoctorWorkloadLock (doctorId, queueEntryId)
alt Walk-in or Queue Entry Provided
  Consultation Service > Queue Service: GET /api/queues/entries/{id}/clinical-context
  Queue Service --> Consultation Service: Verified (status in CALLED/IN_PROGRESS, phase=INITIAL)
else Appointment Provided without QueueEntryId
  Consultation Service > Queue Service: GET /api/queues/tickets/appointment/{appointmentId}
  Queue Service --> Consultation Service: TicketContext (CALLED/IN_PROGRESS)
end
opt Has appointmentId (Scheduled Visit)
  Consultation Service > Appointment Service: GET /api/appointments/{appointmentId}
  Appointment Service --> Consultation Service: Verified (CONFIRMED/CHECKED_IN/IN_PROGRESS)
end
Consultation Service > Directory Service: GET /api/v1/directory/doctors/user/{doctorId}
Directory Service --> Consultation Service: Doctor Snapshot (Name, Dept)
Consultation Service > Consultation Service: Save Consultation (status = IN_PROGRESS)
Consultation Service --> Doctor Web: 201 Created (ConsultationResponse)
deactivate Consultation Service

// === PHASE 2: WAIT FOR LAB RESULTS (OPTIONAL) ===
Doctor Web > Consultation Service: POST /api/consultations/{id}/wait-for-results
activate Consultation Service
Consultation Service > Lab Service: GET /api/labs/orders/consultation/{id}
Lab Service --> Consultation Service: Non-empty LabOrder List
Consultation Service > Consultation Service: Transition IN_PROGRESS -> AWAITING_CLS
Consultation Service --> Doctor Web: 200 OK (status = AWAITING_CLS)
deactivate Consultation Service

// === PHASE 3: RESUME AFTER LAB RESULTS ===
Doctor Web > Consultation Service: POST /api/consultations/{id}/resume
activate Consultation Service
Consultation Service > Lab Service: GET /api/labs/orders/consultation/{id}
Lab Service --> Consultation Service: All Orders in (RESULT_AVAILABLE, REVIEWED, CANCELLED)
Consultation Service > Consultation Service: Transition AWAITING_CLS/AWAITING_REVIEW -> IN_PROGRESS
Consultation Service --> Doctor Web: 200 OK (status = IN_PROGRESS)
deactivate Consultation Service

// === PHASE 4: FINALIZE & COMPLETE VISIT ===
Doctor Web > Consultation Service: POST /api/consultations/{id}/finalize
activate Consultation Service
Consultation Service > Consultation Service: Verify diagnosis != blank
Consultation Service > Prescription Service: GET /api/prescriptions/consultation/{id}
Prescription Service --> Consultation Service: No DRAFT owned by currentDoctorId
Consultation Service > Lab Service: GET /api/labs/orders/consultation/{id}
Lab Service --> Consultation Service: All Orders in (REVIEWED, CANCELLED)
Consultation Service > Consultation Service: Transition -> COMPLETED & Insert Outbox Event (Atomic TX)
Consultation Service > Queue Service: Complete INITIAL & RESULT_REVIEW Queue Entries
Consultation Service > Appointment Service: POST /internal/clinical-complete
Consultation Service --> Doctor Web: 200 OK (Result: complete=true, pendingSteps=[])
deactivate Consultation Service
Consultation Service > Outbox & RabbitMQ: Async @Scheduled publish ConsultationCompleted
```

---

## 5. Đánh Giá Tác Động GitNexus & Kế Hoạch Refactor 4 Lát Cắt (Slices)

### 5.1. Điểm Mùi Code ("Code Smells") Cần Xử Lý
1. **Minified Code (Code viết dồn 1 dòng)**: `ClinicalCompletionGuard.java` (19 dòng), `ClinicalFinalizationService.java` (43 dòng), `ClinicalFinalizationController.java` (13 dòng), `ConsultationEventOutbox.java` (46 dòng) và một số đoạn trong `ConsultationService.java` đang viết dính liền nhiều câu lệnh trên 1 dòng.
2. **Trộn lẫn `@Autowired` Field Injection với Constructor Injection**: `ConsultationService` có 3 field `@Autowired` rời (`eventOutbox`, `completionGuard`, `currentOwners`) bên ngoài constructor, gây ra các câu kiểm tra `if (completionGuard != null)` ẩn chứa rủi ro bỏ sót guard khi khởi tạo thủ công.
3. **Anemic Domain Model**: Class `Consultation` chỉ có getter/setter, trong khi toàn bộ kiểm tra trạng thái (`isTerminal`, `canWaitForResults`, `canResume`, `canComplete`) nằm rải rác trong `ConsultationService`.
4. **Hàm `requireStartedQueueContext` quá tải**: Vừa kiểm tra Queue, vừa mutate `request.setQueueEntryId(...)`, vừa lấy `workload.acquireVisit(...)`, vừa kiểm tra `AppointmentClient`.

### 5.2. Lộ Trình 4 Lát Cắt Phẫu Thuật (Surgical Slices)
- **Slice 1.1a (Domain State Machine & Clean DI)**:
  - Bổ sung các domain methods vào [`Consultation.java`](file:///d:/PTIT/CareFlow/careflow-consultation-service/src/main/java/com/careflow/consultation/model/Consultation.java): `isTerminal()`, `ensureUpdatable()`, `markWaitingForResults()`, `resumeToInProgress()`, `markCompleted(LocalDateTime)`.
  - Chuẩn hóa Constructor của [`ConsultationService.java`](file:///d:/PTIT/CareFlow/careflow-consultation-service/src/main/java/com/careflow/consultation/service/ConsultationService.java): đưa `eventOutbox`, `completionGuard`, `currentOwners` vào constructor chính (kèm 1 constructor tương thích ngược cho các unit test cũ hoặc cập nhật sạch sẽ), định dạng lại toàn bộ code 1 dòng.
- **Slice 1.1b (Clean Format & Guard/Finalization/Outbox)**:
  - Định dạng chuẩn, tách hàm nhỏ rõ tên cho [`ClinicalCompletionGuard.java`](file:///d:/PTIT/CareFlow/careflow-consultation-service/src/main/java/com/careflow/consultation/service/ClinicalCompletionGuard.java), [`ClinicalFinalizationService.java`](file:///d:/PTIT/CareFlow/careflow-consultation-service/src/main/java/com/careflow/consultation/service/ClinicalFinalizationService.java), [`ClinicalFinalizationController.java`](file:///d:/PTIT/CareFlow/careflow-consultation-service/src/main/java/com/careflow/consultation/controller/ClinicalFinalizationController.java) và [`ConsultationEventOutbox.java`](file:///d:/PTIT/CareFlow/careflow-consultation-service/src/main/java/com/careflow/consultation/service/ConsultationEventOutbox.java).
- **Slice 1.1c (Clarify Start Context — Appointment vs Walk-in)**:
  - Tách bạch rõ ràng bước xác minh Queue (`verifyQueueEntryContext` vs `verifyAppointmentTicketContext`) và bước xác minh Appointment (`verifyScheduledAppointment`) trong `ConsultationService`.
- **Slice 1.1d (Clean Format `HandoverService`)**:
  - Giải nén toàn bộ các hàm 1 dòng trong [`HandoverService.java`](file:///d:/PTIT/CareFlow/careflow-consultation-service/src/main/java/com/careflow/consultation/handover/HandoverService.java) để dễ đọc, dễ bảo trì 100%.
