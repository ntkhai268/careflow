# Đặc Tả SDD (Spec-Driven Development) — `careflow-lab-service`

> **Tài liệu Đặc tả Kỹ thuật & Nghiệp vụ (SDD Specification)**
> **Service**: `careflow-lab-service` (Port `8088`)
> **Nhánh thực hiện**: `refactor/lab-service`
> **Phiên bản Spec**: `SDD-1.0` (02/11/2026)

---

## 1. Bối cảnh & Ranh giới Trách nhiệm (Bounded Context)

`careflow-lab-service` quản lý toàn bộ vòng đời **Chỉ định & Trả kết quả Cận lâm sàng (CLS / Xét nghiệm)** trong ca khám ngoại trú: từ khi Bác sĩ tạo phiếu chỉ định CLS, phát hành sự kiện xếp hàng tới các phòng xét nghiệm (`servicePointId`), Kỹ thuật viên Xét nghiệm (`LAB_TECHNICIAN`) nhận ca, nhập chỉ số kết quả từng hạng mục, chốt kết quả (`finalizeOrder`) kích hoạt sự kiện `AllRequiredResultsAvailable` để gọi bệnh nhân quay lại phòng khám, cho đến khi Bác sĩ đọc và xác nhận đã xem kết quả (`markReviewed`).

### Sở hữu dữ liệu (Data Ownership)
- **`lab_orders`**: Bản ghi phiếu chỉ định CLS (`consultationId`, `patientId`, `orderedByDoctorId`, `sourceQueueEntryId`, `sourceVerifiedBy`, `sourceVerifiedAt`, `departmentId`, `clinicalNote`, `status` [`LabOrderStatus`], `paymentStatus` [`PaymentStatus`], `version` optimistic locking).
- **`lab_order_items`**: Các hạng mục xét nghiệm cụ thể thuộc phiếu (`serviceCode`, `serviceName`, `servicePointId`, `required`, `status` [`LabOrderItemStatus`], `resultValue`, `resultUnit`, `referenceRange`, `resultFlag`, `comment`, `performedByStaffId`, `performedAt`).
- **`lab_service_catalog`**: Danh mục dịch vụ CLS chuẩn đang hoạt động (`service_code`, `service_name`, `service_point_id`, `preparation_instruction`, `is_active`).
- **`lab_event_outbox`**: Bảng Transactional Outbox đảm bảo phát hành sự kiện `LabOrderReadyForExecution` và `AllRequiredResultsAvailable` tới RabbitMQ (`lab.exchange`) theo cơ chế *at-least-once*.

### Phụ thuộc Đối tác (Upstream / Downstream Collaborators)
| Service đối tác | Giao thức | Mục đích gọi / Tích hợp |
|---|---|---|
| `careflow-consultation-service` | Feign (`LabConsultationClient`) | Xác minh trạng thái ca khám (`IN_PROGRESS`, `AWAITING_CLS`, `AWAITING_REVIEW`, `READY_TO_COMPLETE`, `COMPLETED`, `CANCELLED`, `TRANSFERRED`), đối chiếu `doctorId`, `patientId` và lấy `queueEntryId` / `appointmentId` gốc. |
| `careflow-hospital-directory-service` | Feign (`LabDirectoryClient`) | Kiểm tra mọi `servicePointId` trong chỉ định phải tồn tại, `isActive = true` và có `roomType = "LAB"`. |
| `careflow-queue-service` | Feign (`QueueExecutionClient` & `LabQueueSourceClient`) | 1. Xác minh Kỹ thuật viên Xét nghiệm đang thực sự gọi/phụ trách lượt chờ `LAB_EXECUTION` (`CALLED`, `IN_PROGRESS`, `COMPLETED`) tại đúng `servicePointId`.<br>2. Truy vấn `ticketId` gốc từ `appointmentId` (`LabQueueSourceClient`) khi ca khám chưa gắn sẵn `queueEntryId`. |
| `careflow-patient-service` | Feign (`PatientIdentityClient`) | Xác minh quyền sở hữu hồ sơ khi `PATIENT` tra cứu lịch sử kết quả xét nghiệm của chính mình. |

---

## 2. Máy Trạng Thái Phiếu Xét Nghiệm (`LabOrder` & `LabOrderItem`)

### 2.1. Tập Trạng Thái Phiếu Chỉ Định (`LabOrderStatus`)
```text
ORDERED / QUEUED / CALLED ──(startOrder)──► IN_PROGRESS ──(finalizeOrder)──► RESULT_AVAILABLE ──(markReviewed)──► REVIEWED
           │                                     │
           └──────────(cancel)───────────────────┴──────► CANCELLED
```
- **`ORDERED`**: Bác sĩ vừa tạo phiếu chỉ định CLS, đã ghi Outbox `LabOrderReadyForExecution`.
- **`QUEUED` / `CALLED`**: Trạng thái đồng bộ khi bệnh nhân đang xếp hàng hoặc được gọi tại phòng CLS.
- **`IN_PROGRESS`**: Kỹ thuật viên Xét nghiệm đã bắt đầu thực hiện phiếu (`startOrder`), cho phép nhập kết quả vào từng `LabOrderItem`.
- **`RESULT_AVAILABLE`**: Tất cả các hạng mục bắt buộc (`required = true`) đã có kết quả hợp lệ; phiếu đã được chốt (`finalizeOrder`) và phát hành sự kiện `AllRequiredResultsAvailable`. Bệnh nhân bắt đầu được phép xem kết quả từ trạng thái này.
- **`REVIEWED`**: Bác sĩ điều trị đã xem xét kết quả xét nghiệm (`markReviewed`) (Terminal state).
- **`CANCELLED`**: Bác sĩ hủy phiếu chỉ định khi chưa phát hành kết quả (Terminal state).

### 2.2. Tập Trạng Thái Hạng Mục Xét Nghiệm (`LabOrderItemStatus`)
- **`ORDERED`**: Mới khởi tạo theo phiếu.
- **`IN_PROGRESS`**: Đang thực hiện.
- **`COMPLETED`**: Kỹ thuật viên đã nhập `resultValue` hợp lệ (`performedByStaffId`, `performedAt`).
- **`CANCELLED`**: Bị hủy theo phiếu cha khi phiếu cha chuyển sang `CANCELLED`.

### 2.3. Bảng Chuyển Đổi Trạng Thái & Điều Kiện Bất Biến (State Transition & Invariants Table)

| Từ Trạng Thái | Hành Động (Command) | Sang Trạng Thái | Điều Kiện Tiên Quyết (Pre-conditions & Invariants) | Mã Lỗi Khi Vi Phạm |
|---|---|---|---|---|
| *(Chưa tồn tại)* | `createOrder` | `ORDERED` | 1. Actor phải là `DOCTOR` (`403`), có `actorUserId` (`401`).<br>2. Danh sách `items` không rỗng (`400`), `paymentRequired != true` (`400`).<br>3. Ca khám phải đang mở (`IN_PROGRESS`, `AWAITING_CLS`, `AWAITING_REVIEW`, `READY_TO_COMPLETE`); nếu đã đóng (`COMPLETED`, `CANCELLED`, `TRANSFERRED`) → `409`.<br>4. Phải xác minh được `sourceQueueEntryId` từ ca khám hoặc `LabQueueSourceClient` (`409` / `503`).<br>5. Mọi `servicePointId` phải là phòng `LAB` đang active trên Directory (`422` / `503`) và từng `serviceCode` phải khớp `lab_service_catalog` (`422`). | `400`, `401`, `403`, `409`, `422`, `503` |
| `ORDERED` / `QUEUED` / `CALLED` | `startOrder` | `IN_PROGRESS` | 1. Actor phải là `LAB_TECHNICIAN` (`403`).<br>2. `QueueExecutionClient` xác nhận KTV đang giữ lượt `LAB_EXECUTION` ở trạng thái `IN_PROGRESS` cho phiếu này (`403` / `409` / `503`).<br>3. Nếu phiếu đã ở `IN_PROGRESS` → trả về idempotent (sau khi đã kiểm tra claim). Nếu ở trạng thái khác (`RESULT_AVAILABLE`, `REVIEWED`, `CANCELLED`) → `409`. | `401`, `403`, `404`, `409`, `503` |
| `IN_PROGRESS` | `updateResult` | `IN_PROGRESS` *(Item → `COMPLETED`)* | 1. Actor phải là `LAB_TECHNICIAN` (`403`).<br>2. Phiếu cha phải đang ở `IN_PROGRESS` (`409`).<br>3. `itemId` phải tồn tại và thuộc về đúng `orderId` (`404`).<br>4. KTV phải đang claim lượt chờ tại đúng `item.servicePointId` (`403` / `409`).<br>5. `resultValue` không được rỗng (`400`). | `400`, `401`, `403`, `404`, `409`, `503` |
| `IN_PROGRESS` | `finalizeOrder` | `RESULT_AVAILABLE` | 1. Actor phải là `LAB_TECHNICIAN` (`403`) và có claim hợp lệ trên Queue (`403` / `409`).<br>2. Nếu đã `RESULT_AVAILABLE` hoặc `REVIEWED` → trả về idempotent (không bắn lại event).<br>3. Nếu chưa `IN_PROGRESS` → `409`.<br>4. Tất cả các item có `required = true` phải ở trạng thái `COMPLETED` và có `resultValue` khác rỗng (`409`).<br>5. `sourceQueueEntryId` phải khác null (`409`). Ghi Outbox `AllRequiredResultsAvailable`. | `401`, `403`, `404`, `409`, `503` |
| `RESULT_AVAILABLE` | `markReviewed` | `REVIEWED` | 1. Actor phải là `DOCTOR` phụ trách ca khám (`403`).<br>2. Nếu đã `REVIEWED` → trả về idempotent.<br>3. Nếu chưa ở `RESULT_AVAILABLE` → `409`. | `401`, `403`, `404`, `409`, `503` |
| `ORDERED` / `QUEUED` / `CALLED` / `IN_PROGRESS` | `cancel` | `CANCELLED` | 1. Actor phải là `DOCTOR` phụ trách ca khám (`403`).<br>2. Nếu đã `CANCELLED` → trả về idempotent.<br>3. Nếu đã `RESULT_AVAILABLE` hoặc `REVIEWED` → chặn với `409`.<br>4. Chuyển phiếu sang `CANCELLED` và hủy tất cả các item đang ở `ORDERED`. | `401`, `403`, `404`, `409`, `503` |

---

## 3. Ma Trận Mã Lỗi Chuẩn (Error Matrix)

| HTTP Status | Ý nghĩa Nghiệp vụ | Tình huống Kích hoạt |
|---|---|---|
| **`400 Bad Request`** | Dữ liệu đầu vào không hợp lệ | - Tạo phiếu rỗng (`items` trống), gửi `paymentRequired = true`, hoặc nhập `resultValue` rỗng. |
| **`401 Unauthorized`** | Thiếu định danh người dùng | - Thiếu header `X-User-Id` (`actorUserId == null`). |
| **`403 Forbidden`** | Vi phạm phân quyền RBAC hoặc Ownership | - Sai vai trò (`DOCTOR` vs `LAB_TECHNICIAN` vs `PATIENT`).<br>- Bác sĩ thao tác trên ca khám của bác sĩ khác; KTV thao tác trên phiếu/điểm dịch vụ mà mình chưa claim; Bệnh nhân đọc phiếu chưa công bố (`!isPublished`) hoặc của bệnh nhân khác. |
| **`404 Not Found`** | Không tìm thấy tài nguyên | - `orderId` không tồn tại hoặc `itemId` không thuộc `orderId`. |
| **`409 Conflict`** | Vi phạm State Machine hoặc điều kiện đồng bộ | - Tạo chỉ định cho ca khám đã đóng (`COMPLETED`, `CANCELLED`, `TRANSFERRED`).<br>- Chốt kết quả khi phiếu chưa `IN_PROGRESS`, còn thiếu kết quả bắt buộc, hoặc thiếu `sourceQueueEntryId`.<br>- KTV thao tác khi lượt chờ CLS chưa ở trạng thái cho phép (`404` từ Queue chuyển thành `409`). |
| **`422 Unprocessable Entity`** | Sai danh mục CLS hoặc điểm thực hiện | - `servicePointId` không tồn tại, không active hoặc không phải loại phòng `LAB`.<br>- Cặp `(serviceCode, servicePointId)` không khớp `lab_service_catalog`. |
| **`503 Service Unavailable`** | Fail-closed khi đối tác lỗi hoặc dữ liệu ngữ cảnh thiếu | - Không kết nối được Consultation, Directory, Queue, Patient service hoặc dữ liệu trả về thiếu trường bắt buộc. |

---

## 4. Mã Sơ Đồ Eraser.io (Copy & Paste Trực Tiếp Vào Eraser.io)

### 4.1. Sơ Đồ Sequence Toàn Luồng Chỉ Định, Thực Hiện & Trả Kết Quả CLS (Eraser Sequence Diagram DSL)
> Mở **Eraser.io** → Chọn **Sequence Diagram** → Dán đoạn mã dưới đây vào khung code bên phải:

```
title CareFlow — Lab Order Lifecycle & Result Review Synchronization (SDD Spec)

Doctor Web [icon: monitor, color: purple]
Lab Technician Web [icon: activity, color: teal]
Lab Service [icon: file-text, color: blue]
Consultation Service [icon: server, color: indigo]
Directory Service [icon: building, color: gray]
Queue Service [icon: list, color: orange]
Outbox & RabbitMQ [icon: message-square, color: red]

// === PHASE 1: DOCTOR CREATES LAB ORDER ===
Doctor Web > Lab Service: POST /api/labs/orders (consultationId, patientId, items)
activate Lab Service
Lab Service > Consultation Service: GET /api/consultations/{consultationId}
Consultation Service --> Lab Service: Context (Open status, doctorId, queueEntryId / appointmentId)
opt queueEntryId is null but appointmentId present
  Lab Service > Queue Service: GET /api/queues/tickets/appointment/{appointmentId}
  Queue Service --> Lab Service: Verified sourceQueueEntryId
end
Lab Service > Directory Service: GET /api/v1/directory/rooms
Directory Service --> Lab Service: Verify all servicePointIds are active LAB rooms
Lab Service > Lab Service: Validate items against lab_service_catalog & Save LabOrder (ORDERED)
Lab Service > Outbox & RabbitMQ: Append LabOrderReadyForExecution (Transactional Outbox)
Lab Service --> Doctor Web: 201 Created (LabOrderResponse - ORDERED)
deactivate Lab Service
Outbox & RabbitMQ > Queue Service: Async publish LabOrderReadyForExecution -> Create LAB_EXECUTION queue entries

// === PHASE 2: LAB TECHNICIAN STARTS & ENTERS RESULTS ===
Lab Technician Web > Lab Service: POST /api/labs/orders/{id}/start
activate Lab Service
Lab Service > Queue Service: GET /api/queues/lab/order/{id}/current
Queue Service --> Lab Service: Verified technician claim (LAB_EXECUTION, IN_PROGRESS)
Lab Service > Lab Service: Transition ORDERED -> IN_PROGRESS
Lab Service --> Lab Technician Web: 200 OK (IN_PROGRESS)
deactivate Lab Service

Lab Technician Web > Lab Service: PUT /api/labs/orders/{id}/items/{itemId}/result
activate Lab Service
Lab Service > Queue Service: GET /api/queues/lab/order/{id}/service-point/{servicePointId}
Queue Service --> Lab Service: Verified technician claim for item servicePointId
Lab Service > Lab Service: Record resultValue, referenceRange, flag & mark item COMPLETED
Lab Service --> Lab Technician Web: 200 OK (LabOrderItemResponse - COMPLETED)
deactivate Lab Service

// === PHASE 3: FINALIZE RESULTS & TRIGGER RESULT REVIEW ===
Lab Technician Web > Lab Service: POST /api/labs/orders/{id}/finalize
activate Lab Service
Lab Service > Queue Service: GET /api/queues/lab/order/{id}/current
Queue Service --> Lab Service: Verified technician claim
Lab Service > Lab Service: Verify all required items COMPLETED & sourceQueueEntryId present
Lab Service > Lab Service: Transition IN_PROGRESS -> RESULT_AVAILABLE
Lab Service > Outbox & RabbitMQ: Append AllRequiredResultsAvailable (Transactional Outbox)
Lab Service --> Lab Technician Web: 200 OK (RESULT_AVAILABLE)
deactivate Lab Service
Outbox & RabbitMQ > Consultation Service: Async AllRequiredResultsAvailable -> Transition AWAITING_CLS to AWAITING_REVIEW
Outbox & RabbitMQ > Queue Service: Async AllRequiredResultsAvailable -> Enqueue patient into RESULT_REVIEW lane

// === PHASE 4: DOCTOR REVIEWS RESULTS ===
Doctor Web > Lab Service: POST /api/labs/orders/{id}/mark-reviewed
activate Lab Service
Lab Service > Consultation Service: GET /api/consultations/{consultationId}
Consultation Service --> Lab Service: Verified assigned doctor
Lab Service > Lab Service: Transition RESULT_AVAILABLE -> REVIEWED
Lab Service --> Doctor Web: 200 OK (REVIEWED)
deactivate Lab Service
```

---

## 5. Đánh Giá Tác Động GitNexus & Kế Hoạch Refactor Phẫu Thuật (Slices)

### 5.1. Điểm Mùi Code ("Code Smells") Đã Phát Hiện
1. **Trộn lẫn `@Autowired` Field Injection với Constructor Injection**:
   - Trong [`LabOrderService.java`](file:///d:/PTIT/CareFlow/careflow-lab-service/src/main/java/com/careflow/lab/service/LabOrderService.java#L36-L48), biến `@Autowired private LabQueueSourceClient sourceQueues;` nằm rời bên ngoài constructor.
   - Trong [`RabbitLabEventPublisher.java`](file:///d:/PTIT/CareFlow/careflow-lab-service/src/main/java/com/careflow/lab/service/RabbitLabEventPublisher.java#L12-L16), biến `@Autowired private LabEventOutbox outbox;` cũng bị đặt rời ngoài constructor.
2. **Logic State Machine & Kiểm tra kết quả bắt buộc nằm rải rác ở tầng Service**:
   - Các kiểm tra chuyển trạng thái (`startOrder`, `finalizeOrder`, `markReviewed`, `cancel`, `recordSourceVerification`, `hasAllRequiredResults`) và cập nhật kết quả trên [`LabOrderItem`](file:///d:/PTIT/CareFlow/careflow-lab-service/src/main/java/com/careflow/lab/model/LabOrderItem.java) đang viết thủ công bằng nhiều cụm `if/else` và setter rời rạc trong `LabOrderService`.
3. **Code viết nén nhiều câu lệnh trên 1 dòng (Minified One-Liners)**:
   - [`LabOrder.java`](file:///d:/PTIT/CareFlow/careflow-lab-service/src/main/java/com/careflow/lab/model/LabOrder.java#L32-L35): Khai báo các cột `sourceQueueEntryId`, `sourceVerifiedBy`, `sourceVerifiedAt`, `version` dồn 1 dòng.
   - [`LabOrderService.java`](file:///d:/PTIT/CareFlow/careflow-lab-service/src/main/java/com/careflow/lab/service/LabOrderService.java#L71): Các dòng 71, 177, 215–236 (`verifiedSource`, `synchronizeReviewQueue`) bị nén chặt nhiều lệnh `if(...)throw...` trên 1 dòng.
   - [`LabEventOutbox.java`](file:///d:/PTIT/CareFlow/careflow-lab-service/src/main/java/com/careflow/lab/service/LabEventOutbox.java), [`RabbitLabEventPublisher.java`](file:///d:/PTIT/CareFlow/careflow-lab-service/src/main/java/com/careflow/lab/service/RabbitLabEventPublisher.java), [`LabReviewSynchronizationController.java`](file:///d:/PTIT/CareFlow/careflow-lab-service/src/main/java/com/careflow/lab/controller/LabReviewSynchronizationController.java), [`LabCatalogController.java`](file:///d:/PTIT/CareFlow/careflow-lab-service/src/main/java/com/careflow/lab/controller/LabCatalogController.java), [`LabCatalogService.java`](file:///d:/PTIT/CareFlow/careflow-lab-service/src/main/java/com/careflow/lab/service/LabCatalogService.java), [`LabQueueSourceClient.java`](file:///d:/PTIT/CareFlow/careflow-lab-service/src/main/java/com/careflow/lab/client/LabQueueSourceClient.java), [`ClinicalDeliveryConfiguration.java`](file:///d:/PTIT/CareFlow/careflow-lab-service/src/main/java/com/careflow/lab/config/ClinicalDeliveryConfiguration.java), [`DataInitializer.java`](file:///d:/PTIT/CareFlow/careflow-lab-service/src/main/java/com/careflow/lab/config/DataInitializer.java) đều bị viết dồn dòng.

### 5.2. Lộ Trình 2 Lát Cắt Phẫu Thuật (Surgical Slices)
- **Slice 1.3a — Chuẩn hóa Domain `LabOrder` & `LabOrderItem` + Làm sạch `LabOrderService`**:
  - Đóng gói các hành vi domain vào [`LabOrder.java`](file:///d:/PTIT/CareFlow/careflow-lab-service/src/main/java/com/careflow/lab/model/LabOrder.java) (`recordSourceVerification`, `hasAllRequiredResults`, `cancelPendingItems`, `isPublished`) và [`LabOrderItem.java`](file:///d:/PTIT/CareFlow/careflow-lab-service/src/main/java/com/careflow/lab/model/LabOrderItem.java) (`completeWithResult`).
  - Chuẩn hóa Constructor Injection cho `LabOrderService` (hỗ trợ cả constructor đầy đủ có `LabQueueSourceClient` lẫn constructor 6 tham số tương thích ngược với các bài test hiện hữu).
  - Giải nén và làm sạch `verifiedSource` & `synchronizeReviewQueue`.
- **Slice 1.3b — Giải nén & Chuẩn hóa `LabEventOutbox`, `RabbitLabEventPublisher`, Controllers, Clients & Catalog**:
  - Format chuẩn 100%, chuẩn hóa Constructor Injection và giữ nguyên toàn bộ chữ ký API/Test contracts.
