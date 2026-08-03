# CareFlow — Patient Journey Implementation Checklist

> **Nguồn**: Chuyển đổi trực tiếp 1:1 theo đúng thứ tự các mục trong `docs/patient-journey-and-system-workflow.md` (v30/07/2026)
> **Mục đích**: Checklist coding chi tiết theo từng mục tài liệu nghiệp vụ để theo dõi và lập Implementation Plan.
> **Cập nhật lần cuối**: 2026-08-02

> [!IMPORTANT]
> **LƯU Ý PHẠM VI ĐẢM NHẬN & PHỤ THUỘC (WEBAPP SCOPE)**:
> 1. **Phạm vi sở hữu trực tiếp**: Nhóm đảm nhận phát triển **Hospital WebApp** và các microservice liên quan trực tiếp gồm: `careflow-consultation-service` (bác sĩ khám), `careflow-lab-service` (kỹ thuật viên cận lâm sàng), `careflow-prescription-service` (kê toa), `careflow-emr-service`, cùng các phân hệ WebApp cho `DOCTOR`, `LAB_TECHNICIAN`, `STAFF` và `ADMIN`.
> 2. **Nguyên tắc xử lý Service bên ngoài**: `careflow-queue-service` do nhóm khác đảm nhận hiện **đã hoàn thiện API thật** (`GET /api/queues/rooms/{roomId}/active`, `POST /call-next`, `POST /call`). Doctor Web sẽ kết nối trực tiếp đến các endpoint thật này thay vì dùng mock data.

---

## Quy ước Trạng thái

- `[ ]` Chưa làm
- `[/]` Đang làm
- `[x]` Hoàn thành

---

## 1. Mục tiêu hệ thống

Hệ thống điều phối hành trình khám ngoại trú tại bệnh viện công, giải quyết các vấn đề chính:

- [ ] **1.1. Bệnh nhân chủ động đặt lịch và nhận phiếu khám điện tử.**
- [ ] **1.2. Bệnh viện biết bệnh nhân nào thực sự đã đến và đang chờ.**
- [ ] **1.3. Bác sĩ theo dõi queue, hồ sơ và toàn bộ diễn biến của lượt khám.**
- [ ] **1.4. Chỉ định cận lâm sàng được chuyển tự động tới đúng bộ phận.**
- [ ] **1.5. Bệnh nhân được hướng dẫn rõ bước tiếp theo trên Mobile App.**
- [ ] **1.6. Kết quả, toa thuốc và lịch tái khám được lưu thành lịch sử liên tục.**

---

## 2. Các quyết định nghiệp vụ đã chốt

- [ ] **2.1. Appointment được hệ thống tự động xác nhận nếu slot còn khả dụng; không cần nhân viên duyệt thủ công.**
- [ ] **2.2. Khi đặt khám thành công, bệnh nhân nhận phiếu khám, số thứ tự, khung giờ, phòng khám và QR.**
- [ ] **2.3. QR thuộc phiếu khám của bệnh nhân. Nhân viên hoặc kiosk tại phòng khám quét QR để xác nhận tiếp nhận.**
- [ ] **2.4. Active queue của phòng khám chỉ chứa bệnh nhân đã `CHECKED_IN`.**
- [ ] **2.5. Queue mặc định là FIFO theo từng khoa, phòng và phiên khám; không dùng thuật toán N:M.**
- [ ] **2.6. Bác sĩ nhập sinh hiệu trực tiếp trong màn hình khám. Role điều dưỡng có thể bổ sung sau nhưng không bắt buộc trong MVP.**
- [ ] **2.7. Thanh toán có thể online hoặc tại bệnh viện. Payment không phải trọng tâm và có thể được mock hoặc tích hợp ngoài.**
- [ ] **2.8. Sau khi bác sĩ tạo chỉ định cận lâm sàng, hệ thống tự tạo lượt tại khu tương ứng. Bệnh nhân tới ngồi chờ, không check-in thêm tại mỗi khu.**
- [ ] **2.9. Khi kết quả sẵn sàng, bệnh nhân được tự động đưa lại vào queue phòng bác sĩ, sau bệnh nhân khám ban đầu tiếp theo.**
- [ ] **2.10. Lượt đọc kết quả vẫn thuộc cùng consultation, không tạo Appointment mới.**

---

## 3. Các vai trò và giao diện

- [ ] **3.1. Patient Mobile App (Bệnh nhân)**: Đặt khám, xem phiếu, theo dõi lượt, nhận chỉ định, kết quả, toa và lịch tái khám.
- [ ] **3.2. Hospital Web App (Bác sĩ)**: Theo dõi queue, khám, nhập sinh hiệu, chẩn đoán, tạo chỉ định, kê toa.
- [ ] **3.3. Hospital Web App (Nhân viên tiếp nhận)**: Quét phiếu, xác nhận bệnh nhân đã đến, hỗ trợ lỡ lượt.
- [ ] **3.4. Hospital Web App (Kỹ thuật viên cận lâm sàng)**: Theo dõi order, gọi số, thực hiện kỹ thuật và nhập kết quả.
- [ ] **3.5. Hospital Web App / External (Thu ngân / BHYT)**: Xác nhận thanh toán hoặc quyền lợi BHYT.
- [ ] **3.6. Hospital Web App (Quản trị viên)**: Cấu hình khoa, phòng, bác sĩ, lịch, capacity và điểm phục vụ.

---

## 4. Các khái niệm nghiệp vụ cốt lõi

- [ ] **4.1. Appointment**: Lịch đặt khám trong tương lai (bệnh nhân, khoa, phòng, bác sĩ, ngày, khung giờ). Giữ capacity nhưng không chứng minh đã đến.
- [ ] **4.2. Visit Ticket**: Phiếu khám điện tử do Queue Service cấp (Mã phiếu, Số thứ tự, Khung giờ dự kiến, Khoa/Phòng, QR token, Trạng thái thanh toán).
- [ ] **4.3. Queue Entry**: Một lượt chờ tại điểm phục vụ (Phòng khám, Khu lấy mẫu XN, Phòng siêu âm, Danh sách chờ đọc kết quả).
- [ ] **4.4. Consultation**: Phiên khám lâm sàng của bác sĩ. Có thể tạm dừng để chờ CLS và tiếp tục khi có kết quả.
- [ ] **4.5. Clinical/Laboratory Order**: Chỉ định do bác sĩ tạo, gom các xét nghiệm cùng lần lấy mẫu vào 1 lượt phục vụ.
- [ ] **4.6. Result**: Kết quả cận lâm sàng gắn với order và consultation. Đủ kết quả tự động đưa bệnh nhân về queue đọc kết quả.
- [ ] **4.7. Prescription & Follow-up**: Toa thuốc và lịch tái khám tạo sau khi bác sĩ hoàn thiện kết luận.

---

## 5. Luồng tổng thể (Overall Workflow)

- [x] **5.1. Bệnh nhân chọn lịch** → Hệ thống kiểm tra slot và tự xác nhận.
- [x] **5.2. Cấp phiếu, số thứ tự và QR.**
- [x] **5.3. Bệnh nhân đến phòng khám** → Nhân viên hoặc kiosk quét QR.
- [x] **5.4. `CHECKED_IN` và vào active FIFO queue.**
- [ ] **5.5. Bác sĩ gọi bệnh nhân** → Bác sĩ nhập sinh hiệu và khám.
- [ ] **5.6. Phân nhánh cận lâm sàng**:
  - Không cần CLS: Chẩn đoán, kê toa, hoàn tất.
  - Có CLS: Bác sĩ tạo order → Thanh toán nếu cần → Hệ thống tự tạo lượt CLS → Bệnh nhân tới khu thực hiện ngồi chờ → KTV gọi/thực hiện/trả kết quả → Hệ thống chèn lượt đọc kết quả sau bệnh nhân tiếp theo → Bác sĩ đọc kết quả & kết luận.
- [ ] **5.7. Mobile nhận kết quả, toa và lịch tái khám.**

---

## 6. Tiền điều kiện: Bệnh viện cấu hình hệ thống (Admin Setup)

- [ ] **6.1. Cấu hình Cơ sở bệnh viện & Khoa chuyên môn.**
- [ ] **6.2. Cấu hình Phòng khám và các điểm phục vụ (Service Points).**
- [ ] **6.3. Cấu hình Danh sách bác sĩ & Lịch làm việc của bác sĩ.**
- [ ] **6.4. Cấu hình Khung giờ & Capacity theo khung giờ.**
- [ ] **6.5. Cấu hình Chính sách check-in sớm/muộn (`checkInWindowMinutes`).**
- [ ] **6.6. Cấu hình Chính sách `MISSED`, `NO_SHOW` và quy trình gọi lại.**
- [ ] **6.7. Cấu hình Danh mục dịch vụ cận lâm sàng.**

---

## 7. Giai đoạn 1: Đặt khám và tự động xác nhận (Appointment)

### 7.1. Bệnh nhân thao tác (Patient Mobile App)
- [x] **7.1.1. Đăng nhập ứng dụng.**
- [x] **7.1.2. Chọn hồ sơ bệnh nhân.**
- [x] **7.1.3. Chọn khoa hoặc bác sĩ.**
- [x] **7.1.4. Chọn ngày và khung giờ.**
- [x] **7.1.5. Nhập lý do khám.**
- [x] **7.1.6. Chọn hình thức thanh toán.**
- [x] **7.1.7. Xác nhận đặt khám.**

### 7.2. Hệ thống xử lý (Appointment Service)
- [x] **7.2.1. Kiểm tra slot còn capacity** (Atomic decrement / Lock).
- [x] **7.2.2. Kiểm tra ngày khám hợp lệ** (Giờ bắt đầu slot > thời điểm hiện tại).
- [x] **7.2.3. Kiểm tra bệnh nhân không đặt trùng khung giờ.**
- [x] **7.2.4. Kiểm tra bác sĩ/phòng đang hoạt động.**
- [x] **7.2.5. Tạo Appointment ở trạng thái `CONFIRMED`** (Không qua bước duyệt `PENDING`).
- [x] **7.2.6. Phát sự kiện `AppointmentConfirmed`** (với `EventEnvelope` chuẩn).
- [x] **7.2.7. Hỗ trợ Header `Idempotency-Key`** trên `POST /api/appointments`.

### 7.3. App hiển thị (Patient Mobile)
- [x] **7.3.1. Hiển thị màn hình "ĐẶT KHÁM THÀNH CÔNG"** (Khoa, Phòng, Ngày, Khung giờ, Status: Đã xác nhận).

---

## 8. Giai đoạn 2: Cấp phiếu khám điện tử (Visit Ticket)

- [x] **8.1. Queue Service consume `AppointmentConfirmed`** (Xử lý Idempotent theo `eventId`).
- [x] **8.2. Cấp Mã phiếu điện tử** (Format: `PK-YYYY-NNNNN`).
- [x] **8.3. Cấp Số thứ tự** (Sequential theo phòng/phiên).
- [x] **8.4. Tạo QR Token** (Opaque signed token, không chứa PII).
- [x] **8.5. Lưu thông tin phiếu khám** (Khung giờ, Khoa, Phòng, Trạng thái thanh toán).
- [x] **8.6. Tạo Queue Entry ở trạng thái `TICKET_ISSUED`** (Chưa vào active queue).
- [ ] **8.7. Patient Mobile hiển thị phiếu khám, số thứ tự, QR, hướng dẫn di chuyển & nút hủy.** (Scope Mobile)

---

## 9. Giai đoạn 3: Đến bệnh viện và check-in

- [x] **9.1. Bệnh nhân đến đúng phòng khám và xuất trình QR.**
- [x] **9.2. Staff / Kiosk quét QR** (`POST /api/queues/check-in`).
- [x] **9.3. Hệ thống validate ticket & khung giờ hợp lệ.**
- [x] **9.4. Chuyển trạng thái `TICKET_ISSUED → CHECKED_IN`.**
- [x] **9.5. Queue Entry chính thức xuất hiện trong Active FIFO Queue của phòng.**
- [x] **9.6. Phát sự kiện `PatientCheckedIn`.**
- [x] **9.7. Idempotency**: Quét QR nhiều lần vẫn giữ nguyên trạng thái `CHECKED_IN`.
- [ ] **9.8. Handling NO_SHOW**: Job tự động quét ticket hết giờ check-in → chuyển `NO_SHOW` & phát `AppointmentNoShow`.
- [ ] **9.9. Patient Mobile cập nhật**: Trạng thái "ĐÃ TIẾP NHẬN", số thứ tự, dự kiến thời gian gọi. (Scope Mobile)

---

## 10. Giai đoạn 4: Active FIFO Queue và gọi bệnh nhân

### 10.1. Hai danh sách khác nhau
- [x] **10.1.1. Appointment Service**: Trả danh sách tất cả lịch dự kiến trong ngày (`CONFIRMED`, `CHECKED_IN`, `NO_SHOW`).
- [x] **10.1.2. Queue Service**: Chỉ trả Active Queue chứa danh sách bệnh nhân đã `CHECKED_IN`.

### 10.2. Quy tắc Queue & Động cơ Điều phối (Queue Engine)
- [x] **10.2.1. Truy vấn Queue theo Bác sĩ**: Doctor Web gọi API lấy thông tin phân công -> lấy `departmentId` theo bác sĩ -> lấy `roomId` thuộc khoa đó -> lấy Active Queue của phòng (`GET /api/queues/rooms/{roomId}/active?date=&session=`).
- [x] **10.2.2. Thuật toán điều phối 3 luồng Hàng đợi (Round-Robin 1:1:1)**:
  - **Luồng 1 (Active Initial Queue)**: Bệnh nhân khám ban đầu đã `CHECKED_IN`.
  - **Luồng 2 (Result Review Queue)**: Bệnh nhân đã làm xong cận lâm sàng, có đủ kết quả quay lại đọc.
  - **Luồng 3 (Priority / Emergency Queue)**: Bệnh nhân thuộc đối tượng ưu tiên / cấp cứu nhẹ.
  - **Cơ chế gọi (Call Next)**: Gọi số điều phối luân phiên theo tỷ lệ **1:1:1** giữa 3 luồng (Initial -> Result Review -> Priority -> Initial...). Nếu 1 luồng rỗng, tự động skip sang luồng tiếp theo.
- [x] **10.2.3. Bác sĩ bấm Gọi bệnh nhân tiếp theo** (`POST /api/queues/rooms/{roomId}/call-next`).
- [x] **10.2.4. Chuyển trạng thái `CHECKED_IN → CALLED`.**
- [x] **10.2.5. Phát notification `QUEUE_CALLED`** tới Patient Mobile ("Mời số X vào Phòng Y").
- [x] **10.2.6. Xử lý Vắng mặt**: Gọi lại (`recall`) hoặc đánh dấu `CALLED → MISSED` và đưa xuống cuối queue / xử lý thủ công.
- [x] **10.2.7. Tích hợp kết nối API thật Queue Service vào Doctor Web**: Thay thế mock/appointment API bằng các call thật (`GET /api/queues/rooms/{roomId}/active`, `POST /call-next`, `POST /entries/{entryId}/call`).

---

## 11. Giai đoạn 5: Khám lâm sàng ban đầu (Consultation)

- [ ] **11.1. Bác sĩ bấm `Bắt đầu khám`** (`POST /api/consultations/{id}/start`).
- [ ] **11.2. Chuyển trạng thái Queue Entry**: `CALLED → IN_PROGRESS`.
- [ ] **11.3. Chuyển trạng thái Consultation**: `NOT_STARTED → IN_PROGRESS`.
- [ ] **11.4. Doctor Web hiển thị thông tin bệnh nhân**: Định danh, lý do khám, tiền sử, dị ứng, hồ sơ upload, lịch sử khám cũ.
- [ ] **11.5. Bác sĩ nhập trực tiếp Sinh hiệu**: Nhiệt độ, Huyết áp, Nhịp tim, SpO2, Cân nặng, Chiều cao.
- [ ] **11.6. Bác sĩ nhập Lâm sàng**: Triệu chứng, khám thực thể, chẩn đoán sơ bộ, mã ICD-10, ghi chú lâm sàng.
- [ ] **11.7. Tích hợp AI Clinical Assistant (Tùy chọn)**: Hỗ trợ tóm tắt, gợi ý chẩn đoán phân biệt & cảnh báo an toàn.

---

## 12. Giai đoạn 6A: Không cần cận lâm sàng

- [ ] **12.1. Bác sĩ hoàn thiện chẩn đoán & dặn dò.**
- [ ] **12.2. Bác sĩ tạo toa thuốc** (`Prescription`).
- [ ] **12.3. Bác sĩ tạo lịch tái khám** (nếu cần).
- [ ] **12.4. Bác sĩ bấm `Hoàn tất khám`.**
- [ ] **12.5. Chuyển trạng thái**:
  - `Consultation: IN_PROGRESS → COMPLETED`
  - `QueueEntry: IN_PROGRESS → COMPLETED`
  - `Appointment: CONFIRMED → FULFILLED`
- [ ] **12.6. Chuyển sang Giai đoạn 9 & 10.**

---

## 13. Giai đoạn 6B: Tạo chỉ định cận lâm sàng và thanh toán

- [ ] **13.1. Bác sĩ tạo Lab Order** (`POST /api/labs/orders`).
- [ ] **13.2. Chuyển trạng thái Consultation**: `IN_PROGRESS → WAITING_FOR_RESULTS` (Không hoàn tất consultation).
- [ ] **13.3. Patient Mobile hiển thị**: Màn hình "ĐÃ KHÁM BAN ĐẦU", danh sách chỉ định, địa điểm khu thực hiện & trạng thái chờ.
- [ ] **13.4. Xử lý Thanh toán / BHYT (Mock / Integration)**:
  - Online / Tiền mặt / BHYT xác nhận → `LabOrder: PAYMENT_PENDING → PAID/COVERED → QUEUED`.

---

## 14. Giai đoạn 7: Thực hiện cận lâm sàng (Lab Execution)

- [ ] **14.1. Tự động tạo lượt CLS**: Queue Service nhận `ClinicalOrderCreated` → Tự động tạo lượt `LAB_EXECUTION` (Bệnh nhân **không cần check-in lần 2**).
- [ ] **14.2. Gom order theo điểm phục vụ**: Gom các XN cùng lần lấy mẫu vào 1 Queue Entry (ví dụ `XN-105`), dịch vụ khác (siêu âm) tạo lượt riêng (ví dụ `SA-042`).
- [ ] **14.3. Patient Mobile hiển thị**: Số thứ tự CLS, địa điểm khu thực hiện, số lượt đang chờ phía trước.
- [ ] **14.4. Kỹ thuật viên thao tác (Lab Web)**:
  - Gọi số tiếp theo (`CALLED`).
  - Đối chiếu danh tính khi bệnh nhân tới bàn.
  - Bấm `Bắt đầu thực hiện` (`IN_PROGRESS`).
  - Lấy mẫu / thực hiện kỹ thuật.
  - Nhập kết quả & khoảng tham chiếu (`PUT /api/labs/orders/{id}/items/{itemId}/result`).
  - Phát hành kết quả (`POST /api/labs/orders/{id}/finalize` → `RESULT_AVAILABLE`).

---

## 15. Giai đoạn 8: Quay lại bác sĩ đọc kết quả (Result Review)

- [ ] **15.1. Tự động tạo lượt đọc kết quả**: Khi đủ kết quả bắt buộc → Phát `AllRequiredResultsAvailable` → `Consultation: WAITING_FOR_REVIEW`.
- [ ] **15.2. Kích hoạt Queue `RESULT_REVIEW`**: Queue Service kích hoạt lượt đọc kết quả (Bệnh nhân **không check-in lại, không đặt appointment mới**).
- [ ] **15.3. Quy tắc điều phối Queue 3 luồng (Round-Robin 1:1:1)**: Đưa bệnh nhân vào hàng chờ `RESULT_REVIEW`. Khi bác sĩ bấm Gọi tiếp theo, Queue Engine gọi luân phiên **1:1:1** giữa Initial Queue, Result Review Queue và Priority Queue (ví dụ: `Initial 1 -> Result Review 1 -> Priority 1 -> Initial 2...`).
- [ ] **15.4. Patient Mobile nhận Push**: "Kết quả đã sẵn sàng. Vui lòng quay lại Phòng X".
- [ ] **15.5. Xử lý Vắng mặt khi đọc kết quả**: Nếu đến lượt mà chưa có mặt → `CALLED → MISSED` → tự động chèn lại sau 1 bệnh nhân khám ban đầu tiếp theo.
- [ ] **15.6. Bác sĩ tiếp tục Consultation**: Gọi số R → `Consultation: WAITING_FOR_REVIEW → IN_PROGRESS`. Bác sĩ xem kết quả trên Doctor Web và chốt chẩn đoán.

---

## 16. Giai đoạn 9: Kê toa, tái khám và hoàn tất

- [ ] **16.1. Bác sĩ tạo & xác nhận toa thuốc**: `DRAFT → CONFIRMED` → Phát `PrescriptionIssued`. Validate dị ứng, liều lượng & an toàn.
- [ ] **16.2. Bác sĩ hẹn lịch tái khám**: Chọn ngày/khung giờ → Appointment Service tạo follow-up appointment → Phát `FollowUpScheduled`.
- [ ] **16.3. Hoàn tất toàn bộ ca khám**:
  - `Consultation → COMPLETED`
  - `QueueEntry → COMPLETED`
  - `Appointment → FULFILLED`
- [ ] **16.4. Đồng bộ EMR**: EMR Service tổng hợp sinh hiệu, triệu chứng, chẩn đoán, CLS, toa thuốc, lịch tái khám thành hồ sơ bệnh án hoàn chỉnh.

---

## 17. Giai đoạn 10: Patient Mobile sau khám

- [ ] **17.1. Màn hình "LƯỢT KHÁM ĐÃ HOÀN TẤT"**: Hiển thị chẩn đoán cuối, kết quả CLS, toa thuốc & ngày hẹn tái khám.
- [ ] **17.2. Tra cứu Lịch sử**: Xem lại lịch sử các lượt khám cũ, tải file/ảnh kết quả.
- [ ] **17.3. Nhắc lịch & Đặt khám**: Tự động thông báo nhắc tái khám khi gần tới ngày hẹn.

---

## 18. Kiểm soát Vòng đời Trạng thái (State Machine Matrix)

- [ ] **18.1. Appointment**: `CONFIRMED` → `FULFILLED` | `CANCELLED` | `NO_SHOW`.
- [ ] **18.2. Clinic Queue Entry**: `TICKET_ISSUED` → `CHECKED_IN` → `CALLED` → `IN_PROGRESS` → `COMPLETED` (Ngoại lệ: `CALLED → MISSED → CHECKED_IN`).
- [ ] **18.3. Consultation**: `NOT_STARTED` → `IN_PROGRESS` → (`WAITING_FOR_RESULTS` → `WAITING_FOR_REVIEW` → `IN_PROGRESS`) → `COMPLETED`.
- [ ] **18.4. Lab Order**: `ORDERED` → (`PAYMENT_PENDING`) → `QUEUED` → `CALLED` → `IN_PROGRESS` → `RESULT_AVAILABLE` → `REVIEWED`.
- [ ] **18.5. Result Review Entry**: `QUEUED` → `CALLED` → `IN_PROGRESS` → `COMPLETED` (Ngoại lệ: `MISSED` → Re-queued lại luồng Result Review).
- [ ] **18.6. Prescription**: `DRAFT` → `CONFIRMED` → `DISPENSED`.

---

## 19. Phân định Trách nhiệm Service (Microservice Mapping)

- [ ] **19.1. API Gateway**: Auth JWT, routing, correlationId, trusted headers.
- [ ] **19.2. Identity Service**: User accounts, roles, auth.
- [ ] **19.3. Patient Service**: Hồ sơ bệnh nhân, dị ứng, tiền sử.
- [ ] **19.4. Appointment Service**: Slot, capacity, đặt/hủy lịch, tái khám.
- [ ] **19.5. Queue Service**: Ticket, QR, số thứ tự, active queue, check-in, gọi số, chèn result-review.
- [ ] **19.6. Notification Service**: Push notification, WebSocket, inbox.
- [ ] **19.7. Consultation Service**: Phiên khám, sinh hiệu, chẩn đoán, trạng thái ca khám.
- [ ] **19.8. Lab Service**: Chỉ định CLS, điểm thực hiện, nhập/phát hành kết quả.
- [ ] **19.9. Prescription Service**: Đơn thuốc, kiểm tra an toàn kê đơn.
- [ ] **19.10. EMR Service**: Tổng hợp hồ sơ bệnh án liên tục.
- [ ] **19.11. AI Service**: Gợi ý tham khảo, tóm tắt EMR.
- [ ] **19.12. Analytics Service**: Thống kê thời gian chờ, hiệu quả vận hành.

---

## 20. Phân định Giao diện (App vs. Web)

- [ ] **20.1. Patient Mobile App**: Đặt khám, nhận QR/Ticket, theo dõi hàng chờ, nhận kết quả/toa/lịch tái khám.
- [ ] **20.2. Doctor Web**: Xem Active Queue (khám ban đầu + đọc kết quả), nhập sinh hiệu/chẩn đoán, tạo Lab order, kê toa.
- [ ] **20.3. Staff Web**: Quét QR check-in, quản lý vắng mặt (missed/recall), hỗ trợ quầy.
- [ ] **20.4. Lab Web (KTV)**: Xem queue CLS, đối chiếu bệnh nhân, bấm bắt đầu & nhập kết quả.

---

## 21. Quy ước Giao tiếp (Sync API vs. Async Event)

- [ ] **21.1. Synchronous HTTP APIs**: Đặt lịch, Check-in, Gọi số, Bắt đầu khám, Tạo order, Nhập kết quả, Kê toa, Hoàn tất.
- [ ] **21.2. Asynchronous RabbitMQ Events**: `AppointmentConfirmed`, `VisitTicketIssued`, `PatientCheckedIn`, `PatientCalled`, `ConsultationStarted`, `ClinicalOrderCreated`, `LabResultAvailable`, `AllRequiredResultsAvailable`, `ConsultationCompleted`, `PrescriptionIssued`, `FollowUpScheduled`.
- [ ] **21.3. Standard Event Envelope**: Bắt buộc có `eventId`, `eventType`, `eventVersion`, `aggregateId`, `correlationId`, `occurredAt`, `payload`.

---

## 22. Quy tắc Queue Chính thức & Ranh giới (Boundary Rules)

- [ ] **22.1. Phòng khám**: FIFO theo `roomId` + session. Chỉ chứa `CHECKED_IN`. `MISSED` không giữ đầu queue.
- [ ] **22.2. Cận lâm sàng**: Tự tạo từ order. Không check-in thêm. KTV xác minh danh tính trước khi bấm bắt đầu.
- [ ] **22.3. Đọc kết quả**: Tự động chèn 1:1 sau bệnh nhân khám ban đầu kế tiếp.
- [ ] **22.4. Không áp dụng**: Không dùng N:M toàn bệnh viện, không trộn cấp cứu vào queue ngoại trú.

---

## 23. Bảo mật & An toàn dữ liệu

- [ ] **23.1. QR Token**: Không chứa PII (tên, CCCD, chẩn đoán).
- [ ] **23.2. Gateway Headers**: Xóa header giả, inject trusted header từ JWT Token.
- [ ] **23.3. Downstream Auth**: Kiểm tra quyền Role & Ownership (Bệnh nhân chỉ xem dữ liệu mình; Bác sĩ chỉ xem dữ liệu ca khám được phân công).
- [ ] **23.4. Public Screens**: Màn hình chờ công cộng chỉ hiển thị Số thứ tự / Mã ticket, không hiển thị tên bệnh nhân hoặc chẩn đoán.
- [ ] **23.5. Audit Logging**: Ghi log audit mọi truy cập EMR, chỉnh sửa chẩn đoán, nhập kết quả & kê toa.

---

## 24. Kịch bản Test Demo (End-to-End Scenarios)

- [ ] **24.1. Scenario A (Không có cận lâm sàng)**: Đặt lịch → Auto confirm → Cấp Ticket số X → Check-in QR → Vào Active Queue → Bác sĩ gọi → Khám & nhập sinh hiệu → Chẩn đoán → Kê toa → Hoàn tất → Mobile nhận toa & lịch tái khám.
- [ ] **24.2. Scenario B (Full Journey có cận lâm sàng)**: Đặt lịch → Check-in → Bác sĩ khám → Tạo Lab Order → Tự tạo số XN/SA → Bệnh nhân ngồi chờ → KTV gọi/thực hiện/trả kết quả → Tự chèn lượt `RESULT_REVIEW` sau bệnh nhân tiếp theo → Bác sĩ đọc kết quả → Kết luận & Kê toa → Mobile nhận đầy đủ hồ sơ.

---

## 25. Tiêu chí hoàn thành (Definition of Done - Business)

- [ ] **25.1. Bệnh nhân đặt được lịch & nhận phiếu không qua duyệt thủ công.**
- [ ] **25.2. Quét QR đưa đúng bệnh nhân vào đúng Active Queue.**
- [ ] **25.3. Bệnh nhân chưa check-in KHÔNG xuất hiện trong Active Queue.**
- [ ] **25.4. Doctor Web gọi & bắt đầu đúng lượt.**
- [ ] **25.5. Bác sĩ nhập được sinh hiệu, chẩn đoán & chỉ định.**
- [ ] **25.6. Lab Order tự xuất hiện trên Lab Web.**
- [ ] **25.7. Bệnh nhân nhận được số CLS mà KHÔNG CẦN check-in lại.**
- [ ] **25.8. Kết quả tự động đưa consultation sang trạng thái chờ review.**
- [ ] **25.9. Lượt đọc kết quả được chèn đúng sau bệnh nhân khám ban đầu tiếp theo.**
- [ ] **25.10. Bác sĩ hoàn thiện consultation, kê toa & hẹn tái khám.**
- [ ] **25.11. Mobile hiển thị đầy đủ timeline, kết quả & tài liệu sau khám.**
- [ ] **25.12. Mọi API được bảo vệ đúng Role & Ownership.**

---

## 26. Ngoài phạm vi MVP (Out of Scope)

- [ ] ~~Thanh toán production kết nối ngân hàng / ví điện tử~~
- [ ] ~~Quyết toán BHYT đầy đủ~~
- [ ] ~~Kho dược và cấp phát thuốc thực tế~~
- [ ] ~~PACS và lưu trữ ảnh DICOM y khoa~~
- [ ] ~~Nội trú và quản lý giường bệnh~~
- [ ] ~~Cấp cứu~~
- [ ] ~~Ký số y tế production-grade~~
- [ ] ~~AI tự chẩn đoán hoặc tự phát hành toa~~
- [ ] ~~Thuật toán ưu tiên N:M toàn bệnh viện~~

---

## 27. Danh mục Yêu cầu Chức năng & Phi Chức năng (Trích xuất từ `docs/report.md`)

### 27.1. Yêu cầu Chức năng (Functional Requirements - FR)
#### a. Xác thực và Tài khoản (Auth & Identity)
- [ ] **FR-AUTH-01**: Đăng ký tài khoản bệnh nhân bằng thông tin hợp lệ (Email, Số điện thoại, Họ tên).
- [ ] **FR-AUTH-02**: Đăng nhập và nhận JWT token phân quyền theo vai trò (`PATIENT`, `DOCTOR`, `STAFF`, `LAB_TECHNICIAN`, `ADMIN`).
- [ ] **FR-AUTH-03**: Làm mới phiên truy cập (Refresh Token) và Đăng xuất an toàn.
- [ ] **FR-AUTH-04**: Quản trị viên có thể khóa, mở hoặc vô hiệu hóa tài khoản nội bộ.

#### b. Hồ sơ Bệnh nhân (Patient Profiles & Documents)
- [ ] **FR-PAT-01**: Bệnh nhân tạo, xem và cập nhật thông tin hồ sơ cá nhân thuộc tài khoản của mình.
- [ ] **FR-PAT-02**: Bệnh nhân tải lên, sửa mô tả và xóa các tài liệu y tế cũ (kết quả xét nghiệm cũ, giấy chuyển viện).
- [ ] **FR-PAT-03**: Bác sĩ chỉ truy cập được hồ sơ của bệnh nhân thuộc lượt khám được phân công.
- [ ] **FR-PAT-04**: Kiểm tra ngặt nghèo quyền sở hữu (`ownership`) trước khi trả dữ liệu hồ sơ hoặc file đính kèm.

#### c. Lịch khám (Appointment Booking)
- [ ] **FR-APT-01**: Bệnh nhân tra cứu danh sách khoa, ngày khám và khung giờ còn trống (Slot capacity).
- [ ] **FR-APT-02**: Đặt lịch khám: chọn hồ sơ, khoa, ngày, khung giờ và nhập lý do khám.
- [ ] **FR-APT-03**: Tự động xác nhận lịch (`CONFIRMED`) ngay khi slot khả dụng, không duyệt thủ công.
- [ ] **FR-APT-04**: Xem danh sách, chi tiết lịch khám và hủy lịch khi chưa check-in.
- [ ] **FR-APT-05**: Bác sĩ tạo lịch tái khám từ ca khám đã có kết luận.

#### d. Phiếu khám và Hàng đợi (Ticket & Queue)
- [ ] **FR-QUE-01**: Tự động cấp Visit Ticket, số thứ tự (Queue Number) và QR Code sau khi lịch confirmed.
- [ ] **FR-QUE-02**: Quét mã QR tại phòng/kiosk để chuyển trạng thái tiếp nhận (`CHECKED_IN`).
- [ ] **FR-QUE-03**: Active Queue phòng khám chỉ hiển thị bệnh nhân đã `CHECKED_IN`.
- [ ] **FR-QUE-04**: Bác sĩ gọi lượt hợp lệ tiếp theo theo nguyên tắc FIFO.
- [ ] **FR-QUE-05**: Xử lý gọi lại (Recall), đánh dấu vắng mặt (`MISSED`) và xếp lại lượt theo chính sách.
- [ ] **FR-QUE-06**: Bệnh nhân theo dõi trạng thái lượt và vị trí hàng đợi realtime trên Mobile App.
- [ ] **FR-QUE-07**: Tự động tạo lượt xếp hàng Cận lâm sàng và lượt Đọc kết quả khi đủ điều kiện.

#### e. Phiên khám lâm sàng (Consultation)
- [ ] **FR-CON-01**: Bác sĩ bắt đầu phiên khám (`IN_PROGRESS`) từ bệnh nhân đã gọi.
- [ ] **FR-CON-02**: Ghi nhận sinh hiệu, triệu chứng lâm sàng, khám thực thể và chẩn đoán sơ bộ (mã ICD-10).
- [ ] **FR-CON-03**: Chuyển trạng thái ca khám sang chờ kết quả (`WAITING_FOR_RESULTS`) khi phát sinh chỉ định cận lâm sàng.
- [ ] **FR-CON-04**: Bác sĩ tiếp tục phiên khám (`IN_PROGRESS`) khi có đủ kết quả cận lâm sàng.
- [ ] **FR-CON-05**: Hoàn tất ca khám (`COMPLETED`) sau khi tổng hợp xong chẩn đoán và kê toa.

#### f. Cận lâm sàng (Laboratory Orders & Results)
- [ ] **FR-LAB-01**: Bác sĩ tạo chỉ định cận lâm sàng gồm 1 hoặc nhiều hạng mục.
- [ ] **FR-LAB-02**: Ghi nhận trạng thái thanh toán / BHYT demo (`paymentStatus`).
- [ ] **FR-LAB-03**: Chỉ định đủ điều kiện tự tạo lượt tại đúng điểm thực hiện (Service Point), **không check-in lần hai**.
- [ ] **FR-LAB-04**: Kỹ thuật viên gọi số, bắt đầu thực hiện và cập nhật tiến trình trên Lab Web.
- [ ] **FR-LAB-05**: Kỹ thuật viên nhập và phát hành kết quả; kết quả đã phát hành không bị ghi đè âm thầm.
- [ ] **FR-LAB-06**: Khi đủ kết quả bắt buộc, tự động tạo lượt `RESULT_REVIEW` đưa bệnh nhân về lại phòng bác sĩ.

#### g. Toa thuốc, Tái khám và Thông báo (Prescription & Notifications)
- [ ] **FR-PRE-01**: Bác sĩ tạo và cập nhật đơn thuốc ở bản nháp (`DRAFT`).
- [ ] **FR-PRE-02**: Bác sĩ xác nhận đơn thuốc (`CONFIRMED`); đơn đã xác nhận không được sửa trực tiếp.
- [ ] **FR-PRE-03**: Bệnh nhân xem đơn thuốc đã xác nhận hoặc đã phát thuốc của mình trên Mobile.
- [ ] **FR-NOT-01**: Hệ thống tạo thông báo tự động tại các mốc quan trọng (xác nhận lịch, check-in, gọi số, có kết quả CLS, kê toa).
- [ ] **FR-NOT-02**: Xem lại danh sách thông báo lưu trong Inbox và đánh dấu đã đọc (`READ`).
- [ ] **FR-NOT-03**: Cập nhật realtime (WebSocket) chỉ làm nhiệm vụ thông báo, không thay thế dữ liệu lưu trữ bền vững.

#### h. Quản trị Cấu hình (Administration)
- [ ] **FR-ADM-01**: Quản trị viên quản lý danh sách tài khoản bác sĩ, kỹ thuật viên và nhân viên.
- [ ] **FR-ADM-02**: Quản trị viên cấu hình cơ sở y tế, danh mục khoa, phòng khám và các điểm phục vụ cận lâm sàng.
- [ ] **FR-ADM-03**: Quản trị viên cấu hình lịch làm việc của bác sĩ, khung giờ và capacity tối đa theo khung giờ.

---

### 27.2. Yêu cầu Phi Chức năng (Non-Functional Requirements - NFR)
- [ ] **NFR-PERF-01 (Hiệu năng API)**: Response time cho các API đồng bộ (Đặt lịch, Check-in, Gọi số) phải dưới **500ms** ở điều kiện tải bình thường.
- [ ] **NFR-SEC-01 (Bảo mật JWT & Gateway)**: 100% request qua Gateway phải xóa header định danh giả từ client và inject trusted headers (`X-User-Id`, `X-User-Role`, `X-Correlation-Id`).
- [ ] **NFR-SEC-02 (Quyền riêng tư PII & QR)**: Mã QR không chứa thông tin định danh y tế nhạy cảm (PII). Màn hình công cộng không hiển thị họ tên đầy đủ hoặc chẩn đoán.
- [ ] **NFR-AVAIL-01 (Tính sẵn sàng microservice)**: Mọi service phải có endpoint `/actuator/health` trả về `UP`. Lỗi của 1 service (ví dụ Notification/Analytics) không làm ngắt đoạn các giao dịch cốt lõi khác.
- [ ] **NFR-TRACE-01 (Khả năng quan sát - Observability)**: 100% log hệ thống phải có `X-Correlation-Id` xuyên suốt từ Gateway qua RabbitMQ đến các downstream services. Log không được chứa JWT token hay thông tin nhạy cảm.

---

## 28. Bộ Kiểm tra Idempotency & Đề xuất Cải tiến Hệ thống

### 28.1. Kiểm tra Idempotency trong HTTP API Commands
- [ ] **IDEM-HTTP-01 (`POST /api/appointments`)**:
  - Gửi 2 request trùng `Idempotency-Key` + cùng Payload → Request thứ 2 trả lại đúng kết quả `201 Created` đã tạo của request đầu.
  - Gửi 2 request trùng `Idempotency-Key` + khác Payload → Request thứ 2 bị chặn và trả `409 Conflict`.
- [ ] **IDEM-HTTP-02 (`POST /api/queues/check-in`)**:
  - Quét cùng 1 mã QR nhiều lần tại kiosk/quầy tiếp nhận → Tất cả các lần sau đều trả về trạng thái `CHECKED_IN` thành công, không tạo dư lượt chờ hoặc sinh lỗi hệ thống.
- [ ] **IDEM-HTTP-03 (`POST /api/queues/rooms/{roomId}/call-next`)**:
  - Bác sĩ bấm "Gọi tiếp theo" nhiều lần liên tiếp do lag mạng → Chỉ gọi 1 bệnh nhân đầu hàng chờ, không chuyển trạng thái hàng loạt bệnh nhân sang `CALLED`.
- [ ] **IDEM-HTTP-04 (`POST /api/labs/orders/{orderId}/start` & `Start Queue Entry`)**:
  - Kỹ thuật viên bấm Bắt đầu thực hiện cận lâm sàng trùng lặp / retry → Hệ thống giữ nguyên lượt `IN_PROGRESS`, không tạo nhiều bản ghi thực hiện.
- [ ] **IDEM-HTTP-05 (`POST /api/prescriptions/{id}/confirm`)**:
  - Xác nhận đơn thuốc trùng lặp → Trả lại đơn đã `CONFIRMED`, không phát trùng sự kiện kê toa.

### 28.2. Kiểm tra Idempotency trong RabbitMQ Event Consumers
- [ ] **IDEM-EVT-01 (Event Deduplication)**: Mọi Consumer (`QueueService`, `NotificationService`, `EMRService`) phải ghi `eventId` vào bảng `processed_events` trong cùng Database Transaction với xử lý nghiệp vụ.
- [ ] **IDEM-EVT-02 (Duplicate Event Delivery)**: Giả lập RabbitMQ gửi lại cùng 1 event `AppointmentConfirmed` nhiều lần (At-least-once delivery) → `QueueService` chỉ tạo đúng 1 Visit Ticket và 1 Queue Entry duy nhất.
- [ ] **IDEM-EVT-03 (Lab Result Completed Event)**: Gửi trùng event `AllRequiredResultsAvailable` → `QueueService` chỉ chèn 1 lượt `RESULT_REVIEW` duy nhất vào hàng chờ phòng bác sĩ.

### 28.3. Đề xuất Cải tiến Nâng cao về Idempotency & Kiến trúc
- [ ] **IMPR-01 (Áp dụng Transactional Outbox Pattern)**:
  - *Hiện trạng*: Đổi database state và publish event trực tiếp có nguy cơ rớt event nếu Broker nghẽn.
  - *Đề xuất*: Lưu event vào bảng `outbox` trong cùng DB transaction, sử dụng Debezium hoặc Background Worker để publish sang RabbitMQ đảm bảo không mất event.
- [ ] **IMPR-02 (Cơ chế Distributed Lock cho Slot Capacity)**:
  - *Hiện trạng*: Đặt lịch đồng thời có thể gây race condition nếu dùng Optimistic Lock.
  - *Đề xuất*: Áp dụng Redis Distributed Lock hoặc Pessimistic Lock (`SELECT FOR UPDATE`) theo cặp `department + date + timeSlot` để chống overbook tuyệt đối.
- [ ] **IMPR-03 (Tự động gia hạn Idempotency Key)**:
  - *Đề xuất*: Cấu hình TTL (Time-To-Live) cho Idempotency Key trong Redis (ví dụ 24 giờ) để giải phóng bộ nhớ tự động sau khi khung giờ khám kết thúc.
- [ ] **IMPR-04 (Tự động hóa Retry & Dead Letter Queue - DLQ)**:
  - *Đề xuất*: Tất cả event consumer nếu gặp lỗi tạm thời (Transient Error) sẽ retry tối đa 3 lần với Exponential Backoff; nếu vẫn thất bại sẽ tự động đẩy vào DLQ (`*.dlq`) kèm alert cho Admin.

---

*Tài liệu checklist này đã được bổ sung đầy đủ Chức năng, Phi chức năng, và Bộ kiểm tra Idempotency nâng cao.*
