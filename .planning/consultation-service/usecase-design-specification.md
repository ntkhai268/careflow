# BÁO CÁO PHÂN TÍCH ĐẶC TẢ USE CASE: CONSULTATION SERVICE (PHÂN HỆ KHÁM BỆNH)

* **Tên dự án:** Hệ thống Quản lý Bệnh viện & Phòng khám CareFlow
* **Phân hệ:** Consultation Service (Dịch vụ Khám bệnh Lâm sàng)
* **Tài liệu quản lý:** `.planning/consultation-service/usecase-design-specification.md`
* **Liên kết DBML:** [.planning/consultation-service/consultation-db.dbml](file:///home/levi/Desktop/careflow/.planning/consultation-service/consultation-db.dbml)

---

## 1. TỔNG QUAN VÀ PHẠM VI (OVERVIEW & SCOPE)

Phân hệ **Consultation Service** đóng vai trò là hạt nhân trung tâm điều phối toàn bộ hành trình lâm sàng của bệnh nhân trong một phiên khám tại bệnh viện/phòng khám. Dịch vụ đảm nhận trách nhiệm từ lúc Bác sĩ tiếp nhận gọi bệnh nhân vào phòng khám, ghi nhận chỉ số sinh hiệu, triệu chứng lâm sàng, chỉ định cận lâm sàng (Xét nghiệm/Chẩn đoán hình ảnh), đọc kết quả duyệt chẩn đoán ICD-10, đến khi hoàn tất ca khám và khóa dữ liệu đẩy sang Bệnh án Điện tử (EMR Service).

---

## 2. SƠ ĐỒ USE CASE TỔNG THỂ (PLANTUML USE CASE DIAGRAM)

```plantuml
@startuml
left to right direction
skinparam packageStyle rectangle
skinparam actorStyle awesome
skinparam backgroundColor #FFFFFF

actor "Bác sĩ Khám bệnh" as Doctor #Primary
actor "Y tá / Điều dưỡng" as Nurse
actor "Phòng Cận lâm sàng (Lab/CLS)" as LabSystem
actor "Hệ thống EMR" as EMRSystem

rectangle "CareFlow Consultation Service" {
    usecase "UC-CONS-01: Gọi Bệnh nhân & Khởi tạo Ca khám" as UC1
    usecase "UC-CONS-02: Ghi nhận Chỉ số Sinh hiệu & Lâm sàng" as UC2
    usecase "UC-CONS-03: Chỉ định Cận lâm sàng (CLS/Lab)" as UC3
    usecase "UC-CONS-04: Đọc Kết quả CLS & Chốt Chẩn đoán ICD-10" as UC4
    usecase "UC-CONS-05: Hoàn tất & Khóa Phiên khám" as UC5
    usecase "UC-CONS-06: Hủy / Chuyển khoa Ca khám" as UC6
    usecase "Tra cứu Lịch sử Khám Bệnh nhân" as UC7
}

Doctor --> UC1
Doctor --> UC2
Doctor --> UC3
Doctor --> UC4
Doctor --> UC5
Doctor --> UC6
Doctor --> UC7

Nurse --> UC1
Nurse --> UC2

LabSystem ..> UC4 : <<Update Result>>
UC5 ..> EMRSystem : <<Publish RabbitMQ Event>>
UC1 .> UC2 : <<include>>
UC4 .> UC5 : <<precede>>
@enduml
```

---

## 3. ĐẶC TẢ CHI TIẾT CÁC USE CASE (USE CASE SPECIFICATIONS)

### UC-CONS-01: Gọi Bệnh nhân & Khởi tạo Ca khám (`IN_PROGRESS`)

| Mục | Nội dung Đặc tả |
| :--- | :--- |
| **Mã Use Case** | **UC-CONS-01** |
| **Tên Use Case** | Gọi Bệnh nhân từ Hàng đợi & Khởi tạo Phiên khám Lâm sàng |
| **Tác nhân chính** | Bác sĩ Khám bệnh (`UserRole.DOCTOR`) |
| **Mô tả** | Bác sĩ chọn bệnh nhân tiếp theo trong hàng đợi phòng khám để bắt đầu ca khám. Hệ thống tự động khởi tạo phiên khám với trạng thái `IN_PROGRESS`. |
| **Tiền điều kiện** | 1. Bác sĩ đã đăng nhập thành công vào hệ thống.<br>2. Bệnh nhân có lịch hẹn ở trạng thái `WAITING` / `CONFIRMED`. |
| **Luồng sự kiện chính (Main Flow)** | 1. Bác sĩ xem danh sách hàng đợi khám trên Dashboard.<br>2. Bác sĩ nhấn nút **"Vào khám"** / **"Gọi bệnh nhân tiếp theo"**.<br>3. Hệ thống kiểm tra điều kiện ca khám song song (Single Active Consultation Guard).<br>4. Nếu thỏa mãn, hệ thống tạo bản ghi `consultations` mới với trạng thái `IN_PROGRESS`, ghi nhận `started_at = NOW()`.<br>5. Hệ thống chuyển hướng Bác sĩ tới màn hình chi tiết phiên khám `/consultation/{id}`. |
| **Luồng ngoại lệ (Alternate Flow)** | **A1: Bác sĩ đang có ca khám dở chưa hoàn tất (`IN_PROGRESS`):**<br>- Hệ thống **chặn không tạo ca mới**.<br>- Hệ thống gửi thông điệp tới **Bác sĩ Chồn AI** (`careflow:ai-notify`) để phát thông báo bong bóng hoặc tin nhắn chat: *"Bác sĩ hiện tại đang có một ca khám chưa hoàn tất. Vui lòng hoàn thành lượt khám hiện tại trước khi gọi bệnh nhân khác."* kèm nút bấm `[Đến ca khám hiện tại →]`. |
| **Hậu điều kiện** | Tạo phiên khám mới thành công ở trạng thái `IN_PROGRESS`, khóa không cho tạo ca song song thứ 2. |

---

### UC-CONS-02: Ghi nhận Chỉ số Sinh hiệu & Lâm sàng

| Mục | Nội dung Đặc tả |
| :--- | :--- |
| **Mã Use Case** | **UC-CONS-02** |
| **Tên Use Case** | Ghi nhận Chỉ số Sinh hiệu & Triệu chứng Lâm sàng |
| **Tác nhân chính** | Bác sĩ Khám bệnh, Y tá / Điều dưỡng |
| **Mô tả** | Ghi nhận thông tin sinh hiệu (Nhiệt độ, Huyết áp, Nhịp tim, SpO2, Chiều cao, Cân nặng, BMI) và các triệu chứng cơ năng/thực thể của bệnh nhân. |
| **Tiền điều kiện** | Ca khám đang ở trạng thái `IN_PROGRESS`. |
| **Luồng sự kiện chính** | 1. Y tá hoặc Bác sĩ nhập các chỉ số sinh hiệu vào các ô dữ liệu tương ứng.<br>2. Bác sĩ nhập mô tả lý do khám, tiền sử bệnh và triệu chứng lâm sàng.<br>3. Hệ thống tự động tính chỉ số BMI và cảnh báo sinh hiệu bất thường (nếu SpO2 < 95% hoặc Huyết áp > 140/90 mmHg).<br>4. Hệ thống tự động lưu nháp dữ liệu sinh hiệu. |
| **Hậu điều kiện** | Các chỉ số sinh hiệu được ghi nhận đầy đủ vào cơ sở dữ liệu `careflow_consultation`. |

---

### UC-CONS-03: Chỉ định Cận lâm sàng (CLS/Lab) & Chuyển trạng thái (`AWAITING_CLS`)

| Mục | Nội dung Đặc tả |
| :--- | :--- |
| **Mã Use Case** | **UC-CONS-03** |
| **Tên Use Case** | Tạo Phiếu Chỉ định Cận lâm sàng (Xét nghiệm / CDHA) |
| **Tác nhân chính** | Bác sĩ Khám bệnh |
| **Mô tả** | Bác sĩ tạo các chỉ định xét nghiệm máu, nước tiểu, X-quang, Siêu âm nếu phát hiện triệu chứng cần chẩn đoán chuyên sâu. |
| **Luồng sự kiện chính** | 1. Bác sĩ chọn tab **"Chỉ định Cận lâm sàng"**.<br>2. Bác sĩ chọn các dịch vụ CLS cần thực hiện từ danh mục.<br>3. Bác sĩ nhấn **"Gửi chỉ định CLS"**.<br>4. Ca khám tự động chuyển trạng thái `status = AWAITING_CLS`.<br>5. Bệnh nhân cầm phiếu chỉ định đi thanh toán và làm cận lâm sàng. |
| **Hậu điều kiện** | Tạo phiếu chỉ định CLS thành công, phiên khám tạm thời chuyển sang trạng thái chờ kết quả. |

---

### UC-CONS-04: Đọc Kết quả CLS & Chốt Chẩn đoán ICD-10 (`READY_TO_COMPLETE`)

| Mục | Nội dung Đặc tả |
| :--- | :--- |
| **Mã Use Case** | **UC-CONS-04** |
| **Tên Use Case** | Duyệt Kết quả Cận lâm sàng & Nhập Mã Chẩn đoán ICD-10 |
| **Tác nhân chính** | Bác sĩ Khám bệnh |
| **Mô tả** | Khi phòng Lab/CLS trả kết quả, bệnh nhân quay lại phòng khám. Bác sĩ đọc kết quả, kết luận chẩn đoán và nhập mã danh mục quốc tế ICD-10. |
| **Luồng sự kiện chính** | 1. Khi có kết quả từ phòng Lab, ca khám chuyển sang trạng thái `AWAITING_REVIEW`.<br>2. Bệnh nhân quay lại phòng khám, Bác sĩ mở giao diện xem kết quả CLS chi tiết.<br>3. Bác sĩ tra cứu và chọn mã chẩn đoán ICD-10 (mã bệnh + tên bệnh).<br>4. Bác sĩ nhập chẩn đoán phụ / ghi chú lâm sàng.<br>5. Trạng thái ca khám chuyển thành `READY_TO_COMPLETE`. |
| **Hậu điều kiện** | Đã chốt chẩn đoán bệnh chính xác theo mã chuẩn ICD-10. |

---

### UC-CONS-05: Hoàn tất & Khóa Phiên khám (`COMPLETED`)

| Mục | Nội dung Đặc tả |
| :--- | :--- |
| **Mã Use Case** | **UC-CONS-05** |
| **Tên Use Case** | Hoàn tất Phiên khám & Đẩy Event sang EMR |
| **Tác nhân chính** | Bác sĩ Khám bệnh |
| **Mô tả** | Sau khi đã chốt chẩn đoán và kê đơn thuốc xong, Bác sĩ nhấn "Hoàn tất phiên khám". Ca khám bị khóa và đồng bộ sang Bệnh án Điện tử. |
| **Luồng sự kiện chính** | 1. Bác sĩ nhấn nút **"HOÀN TẤT PHIÊN KHÁM"**.<br>2. Hệ thống kiểm tra đơn thuốc liên quan (nếu có đơn nháp phải xác nhận hoặc bỏ qua).<br>3. Hệ thống cập nhật `status = COMPLETED` và `completed_at = NOW()`.<br>4. Hệ thống phát RabbitMQ Event `consultation.completed` chứa toàn bộ dữ liệu khám sang `careflow-emr-service`.<br>5. Bác sĩ giải phóng ca khám, sẵn sàng đón bệnh nhân tiếp theo. |
| **Hậu điều kiện** | Phiên khám bị khóa vĩnh viễn (Read-only), dữ liệu được lưu vết vào EMR. |

---

## 4. SƠ ĐỒ SEQUENCE PLANTUML (CHI TIẾT LUỒNG NGHIỆP VỤ KHÁM BỆNH)

```plantuml
@startuml
autonumber
actor "Bác sĩ" as Doctor #Primary
participant "Doctor Web UI" as UI
participant "API Gateway" as Gateway
participant "Consultation Service" as CS
database "PostgreSQL Remote" as DB
participant "Prescription Service" as PS
queue "RabbitMQ Event Bus" as MQ
participant "EMR Service" as EMR

== 1. Khởi tạo & Kiểm tra ca khám đơn duy nhất ==
Doctor -> UI: Bấm "Vào khám" bệnh nhân (patientId, appointmentId)
UI -> Gateway: POST /api/consultations
Gateway -> CS: Forward CreateConsultationRequest
CS -> DB: Query active consultation (doctorId, status='IN_PROGRESS')
alt Đã có ca khám chưa hoàn tất
    DB --> CS: Trả về ca khám activeCons
    CS --> Gateway: HTTP 400 BusinessException ("Chưa hoàn tất ca trước")
    Gateway --> UI: HTTP 400 Response
    UI -> UI: Phát sự kiện 'careflow:ai-notify' cho Bác sĩ Chồn AI
else Chưa có ca khám dở
    CS -> DB: INSERT INTO consultations (status='IN_PROGRESS', started_at=NOW())
    DB --> CS: OK (consultationId)
    CS --> UI: HTTP 201 Created (ConsultationDTO)
    UI -> UI: Chuyển hướng tới màn hình /consultation/{id}
end

== 2. Khám Lâm sàng & Chẩn đoán ==
Doctor -> UI: Nhập Sinh hiệu + Triệu chứng + Mã ICD-10
UI -> CS: PUT /api/consultations/{id} (icd10Code, symptoms, vitals)
CS -> DB: UPDATE consultations SET icd10_code=..., status='READY_TO_COMPLETE'
DB --> CS: OK

== 3. Kê đơn thuốc (Liên thông) ==
Doctor -> UI: Tạo & Ký xác nhận đơn thuốc
UI -> PS: POST /api/prescriptions/{id}/confirm
PS --> UI: Đơn thuốc đã ký (status='CONFIRMED')

== 4. Hoàn tất & Khóa phiên khám ==
Doctor -> UI: Bấm "HOÀN TẤT PHIÊN KHÁM"
UI -> CS: PUT /api/consultations/{id}/complete
CS -> DB: UPDATE consultations SET status='COMPLETED', completed_at=NOW()
CS -> MQ: Publish Event 'consultation.completed' (payload: ConsultationEvent)
MQ -> EMR: Consume Event & Lưu Hồ sơ bệnh án EMR
CS --> UI: HTTP 200 OK (Ca khám đã khóa)
UI -> Doctor: Thông báo hoàn tất thành công, mở lại hàng đợi
@enduml
```
