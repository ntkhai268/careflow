# BÁO CÁO PHÂN TÍCH ĐẶC TẢ USE CASE: PRESCRIPTION SERVICE (PHÂN HỆ KÊ ĐƠN THUỐC ĐIỆN TỬ)

* **Tên dự án:** Hệ thống Quản lý Bệnh viện & Phòng khám CareFlow
* **Phân hệ:** Prescription Service (Dịch vụ Kê đơn thuốc Điện tử)
* **Tài liệu quản lý:** `.planning/prescription-service/usecase-design-specification.md`
* **Liên kết DBML:** [.planning/prescription-service/prescription-db.dbml](file:///home/levi/Desktop/careflow/.planning/prescription-service/prescription-db.dbml)

---

## 1. TỔNG QUAN VÀ PHẠM VI (OVERVIEW & SCOPE)

Phân hệ **Prescription Service** đảm nhận nhiệm vụ quản lý toàn bộ vòng đời của đơn thuốc điện tử trong hệ thống CareFlow. Dịch vụ cho phép Bác sĩ tra cứu danh mục thuốc bệnh viện, kiểm tra liều dùng/chống chỉ định, lập đơn thuốc nháp (`DRAFT`), chỉnh sửa dòng thuốc, ký xác nhận đơn thuốc (`CONFIRMED`), đồng bộ liên thông tới Quầy thuốc (Pharmacy) qua RabbitMQ Event, và tự động liên kết tạo lịch tái khám với `appointment-service`.

---

## 2. SƠ ĐỒ USE CASE TỔNG THỂ (PLANTUML USE CASE DIAGRAM)

```plantuml
@startuml
left to right direction
skinparam packageStyle rectangle
skinparam actorStyle awesome
skinparam backgroundColor #FFFFFF

actor "Bác sĩ Khám bệnh" as Doctor #Primary
actor "Dược sĩ Quầy thuốc" as Pharmacist
actor "Hệ thống Quầy thuốc (Pharmacy)" as PharmacySystem
actor "Appointment Service" as ApptService

rectangle "CareFlow Prescription Service" {
    usecase "UC-PRES-01: Tra cứu Danh mục Thuốc Bệnh viện" as UC1
    usecase "UC-PRES-02: Tạo Đơn thuốc Nháp (DRAFT)" as UC2
    usecase "UC-PRES-03: Thêm / Sửa / Xóa Dòng thuốc" as UC3
    usecase "UC-PRES-04: Ký & Xác nhận Đơn thuốc (CONFIRMED)" as UC4
    usecase "UC-PRES-05: Tự động Tạo Lịch hẹn Tái khám" as UC5
    usecase "UC-PRES-06: Hủy / Thu hồi Đơn thuốc" as UC6
    usecase "Tra cứu Lịch sử Đơn thuốc Bệnh nhân" as UC7
}

Doctor --> UC1
Doctor --> UC2
Doctor --> UC3
Doctor --> UC4
Doctor --> UC6
Doctor --> UC7

Pharmacist --> UC7

UC4 ..> PharmacySystem : <<Publish RabbitMQ Event>>
UC4 ..> UC5 : <<trigger if followUpDate set>>
UC5 ..> ApptService : <<Create Appointment>>
UC2 .> UC3 : <<include>>
@enduml
```

---

## 3. ĐẶC TẢ CHI TIẾT CÁC USE CASE (USE CASE SPECIFICATIONS)

### UC-PRES-01: Tra cứu Danh mục Thuốc Bệnh viện & Kiểm tra Trùng thuốc

| Mục | Nội dung Đặc tả |
| :--- | :--- |
| **Mã Use Case** | **UC-PRES-01** |
| **Tên Use Case** | Tra cứu Danh mục Thuốc Bệnh viện & Cảnh báo Dị ứng / Trùng thuốc |
| **Tác nhân chính** | Bác sĩ Khám bệnh (`UserRole.DOCTOR`) |
| **Mô tả** | Bác sĩ tìm kiếm các biệt dược trong kho dược bệnh viện (`drugs_dictionary`) theo tên thuốc, hoạt chất, mã thuốc để chọn vào đơn. |
| **Tiền điều kiện** | Đơn thuốc nháp đang được tạo hoặc chỉnh sửa. |
| **Luồng sự kiện chính** | 1. Bác sĩ gõ từ khóa tìm kiếm (tên thuốc/hoạt chất).<br>2. Hệ thống truy vấn bảng `drugs_dictionary` và trả về kết quả khớp.<br>3. Bác sĩ chọn thuốc, hệ thống gợi ý đơn vị tính, đường dùng và liều dùng chuẩn.<br>4. Nếu Bác sĩ chọn lại loại thuốc **đã có sẵn trong đơn lần 2**: hệ thống **không báo lỗi cứng**, mà tự động cập nhật lại số lượng và liều dùng mới kèm thông báo Toast vàng: *"Thuốc [Tên thuốc] đã có trong đơn. Hệ thống đã cập nhật lại số lượng và liều dùng mới."* |
| **Hậu điều kiện** | Chọn đúng biệt dược và kiểm tra trùng thuốc an toàn. |

---

### UC-PRES-02: Tạo Đơn thuốc Nháp (`DRAFT`)

| Mục | Nội dung Đặc tả |
| :--- | :--- |
| **Mã Use Case** | **UC-PRES-02** |
| **Tên Use Case** | Lập Đơn thuốc Nháp cho Ca khám Lâm sàng |
| **Tác nhân chính** | Bác sĩ Khám bệnh |
| **Mô tả** | Bác sĩ tạo mới đơn thuốc nháp gắn liền với ca khám (`consultation_id`) của bệnh nhân. |
| **Tiền điều kiện** | Ca khám đang ở trạng thái `IN_PROGRESS` hoặc `READY_TO_COMPLETE`. |
| **Luồng sự kiện chính** | 1. Bác sĩ nhấn nút **"LƯU NHÁP ĐƠN"** hoặc thêm thuốc đầu tiên vào đơn.<br>2. Hệ thống kiểm tra ca khám liên quan.<br>3. Hệ thống tạo bản ghi `prescriptions` với `status = DRAFT`.<br>4. Lưu danh sách các dòng thuốc vào `prescription_items`.<br>5. Đơn nháp có thể sửa đổi nhiều lần mà chưa khóa hay chuyển sang Quầy thuốc. |
| **Hậu điều kiện** | Đơn thuốc được lưu nháp an toàn ở trạng thái `DRAFT`. |

---

### UC-PRES-03: Thêm / Sửa / Xóa Dòng Thuốc trong Đơn Nháp

| Mục | Nội dung Đặc tả |
| :--- | :--- |
| **Mã Use Case** | **UC-PRES-03** |
| **Tên Use Case** | Chỉnh sửa Chi tiết Dòng Thuốc trong Đơn Nháp |
| **Tác nhân chính** | Bác sĩ Khám bệnh |
| **Mô tả** | Thay đổi số lượng viên, liều dùng (số lần/ngày, số lượng/lần), cách dùng (trước/sau ăn) hoặc xóa bỏ một dòng thuốc khỏi đơn nháp. |
| **Luồng sự kiện chính** | 1. Bác sĩ nhấn nút **"Sửa"** hoặc **"Xóa"** trên dòng thuốc.<br>2. Điều chỉnh số lượng viên hoặc hướng dẫn sử dụng.<br>3. Hệ thống cập nhật bảng `prescription_items` tương ứng.<br>4. Cập nhật lại tổng số loại thuốc trong đơn. |
| **Hậu điều kiện** | Đơn nháp được điều chỉnh số lượng và thông tin chính xác. |

---

### UC-PRES-04: Ký & Xác nhận Đơn thuốc Điện tử (`CONFIRMED`)

| Mục | Nội dung Đặc tả |
| :--- | :--- |
| **Mã Use Case** | **UC-PRES-04** |
| **Tên Use Case** | Ký & Xác nhận Đơn thuốc Điện tử Chuyển Quầy thuốc |
| **Tác nhân chính** | Bác sĩ Khám bệnh |
| **Mô tả** | Bác sĩ ký và xác nhận đơn thuốc. Hệ thống chuyển trạng thái đơn sang `CONFIRMED`, khóa dữ liệu không cho sửa đổi và đẩy Event tới Quầy thuốc. |
| **Tiền điều kiện** | 1. Đơn thuốc nháp có ít nhất 1 loại thuốc hợp lệ.<br>2. Bác sĩ xác nhận trong Modal popup xem trước đơn thuốc. |
| **Luồng sự kiện chính** | 1. Bác sĩ nhấn nút **"KÝ & XÁC NHẬN ĐƠN"** trên thanh dưới cùng.<br>2. Modal popup xác nhận hiển thị danh sách thuốc mờ xám tinh giản 1 dòng (`1. Omeprazole 20mg x10 (2 lần/ngày) - MED003`).<br>3. Bác sĩ nhấn nút **"Xác nhận"** (Màu tím SJD Purple `#6E2582`).<br>4. Hệ thống cập nhật `status = CONFIRMED` và `signed_at = NOW()`.<br>5. Hệ thống phát RabbitMQ Event `prescription.confirmed` tới Quầy thuốc (`careflow-pharmacy`). |
| **Hậu điều kiện** | Đơn thuốc bị khóa vĩnh viễn (Read-only), thông tin đơn chuyển sang Quầy thuốc để phát thuốc cho bệnh nhân. |

---

### UC-PRES-05: Tự động Đặt Lịch hẹn Tái khám (`FOLLOW_UP`)

| Mục | Nội dung Đặc tả |
| :--- | :--- |
| **Mã Use Case** | **UC-PRES-05** |
| **Tên Use Case** | Tự động Khởi tạo Lịch hẹn Tái khám cho Bệnh nhân |
| **Tác nhân chính** | Bác sĩ Khám bệnh, Hệ thống |
| **Mô tả** | Khi đơn thuốc có thiết lập ngày tái khám (`followUpDate`), hệ thống tự động gọi `appointment-service` tạo lịch hẹn tái khám cho bệnh nhân. |
| **Luồng sự kiện chính** | 1. Bác sĩ chọn ngày hẹn tái khám (ví dụ: sau 7 ngày hoặc 14 ngày).<br>2. Khi đơn thuốc được ký xác nhận (`CONFIRMED`), hệ thống kiểm tra trường `follow_up_date`.<br>3. Hệ thống gửi yêu cầu tạo lịch hẹn tới `careflow-appointment-service`.<br>4. Lịch tái khám được ghi nhận tự động ở trạng thái `CONFIRMED`. |
| **Hậu điều kiện** | Lịch tái khám được tự động tạo và gửi thông báo nhắc lịch cho bệnh nhân. |

---

## 4. SƠ ĐỒ SEQUENCE PLANTUML (CHI TIẾT QUY TRÌNH KÊ ĐƠN & KÝ XÁC NHẬN)

```plantuml
@startuml
autonumber
actor "Bác sĩ" as Doctor #Primary
participant "Doctor Web UI" as UI
participant "Prescription Service" as PS
database "PostgreSQL Remote" as DB
participant "Consultation Service" as CS
queue "RabbitMQ Event Bus" as MQ
participant "Appointment Service" as Appt

== 1. Lập đơn thuốc nháp & Thêm thuốc ==
Doctor -> UI: Chọn thuốc Omeprazole 20mg (Số lượng: 10)
UI -> PS: POST /api/prescriptions/{id}/items (drugId, quantity, dosage)
PS -> DB: Query prescription item existing
alt Thuốc đã tồn tại trong đơn
    PS -> DB: UPDATE prescription_items SET quantity = quantity + 10
    PS --> UI: HTTP 200 OK (Cập nhật số lượng + Toast cảnh báo)
else Thuốc chưa có trong đơn
    PS -> DB: INSERT INTO prescription_items (...)
    PS --> UI: HTTP 201 Created (Thêm dòng thuốc mới)
end

== 2. Mở Modal Ký & Xác nhận Đơn ==
Doctor -> UI: Bấm "KÝ & XÁC NHẬN ĐƠN"
UI -> UI: Bật Modal xem trước danh sách thuốc mờ xám tinh giản (max-h-[85vh])
Doctor -> UI: Bấm nút "Xác nhận" (Màu tím SJD #6E2582)

== 3. Ký xác nhận & Phát Event Quầy thuốc ==
UI -> PS: POST /api/prescriptions/{id}/confirm
PS -> CS: GET /api/consultations/{consultationId} (Check status)
CS --> PS: Status OK ('IN_PROGRESS' / 'READY_TO_COMPLETE')
PS -> DB: UPDATE prescriptions SET status='CONFIRMED', signed_at=NOW()
PS -> MQ: Publish Event 'prescription.confirmed' (Payload: PrescriptionConfirmedEvent)
Note over MQ: Quầy thuốc (Pharmacy) tiêu thụ event & chuẩn bị thuốc

== 4. Liên thông Tái khám tự động (nếu có ngày hẹn) ==
alt Có thiết lập followUpDate
    PS -> Appt: POST /api/appointments (patientId, doctorId, date=followUpDate)
    Appt --> PS: OK (AppointmentCreated)
end

PS --> UI: HTTP 200 OK (Đơn thuốc đã ký thành công)
UI -> Doctor: Thông báo thành công, khóa form đơn thuốc (Read-only)
@enduml
```
