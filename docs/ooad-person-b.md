# Phân tích & Thiết kế OOAD — Phân hệ Bệnh nhân (Người B)

> Tài liệu phân tích và thiết kế hướng đối tượng (OOAD) cho 3 service thuộc phân hệ Bệnh nhân trong dự án CareFlow.
>
> **Tài liệu lịch sử:** với Appointment/phân phòng, nguồn hiện hành là
> `docs/service-contracts/05-appointment.md` và baseline Chương 3. Event/trạng
> thái cũ còn lại trong file chỉ phản ánh kế hoạch ban đầu.

## Phạm vi

| Mục | Chi tiết |
|-----|----------|
| **Phân hệ** | Bệnh nhân |
| **Services** | Patient Service, Appointment Service, EMR Service |
| **Frontend** | Mobile App (Flutter) |
| **Phương pháp** | OOAD (Object-Oriented Analysis & Design) |

---

# PHASE 1: PHÂN TÍCH (Analysis)

---

## 1.1 Xác định Actor

```mermaid
graph LR
    subgraph "Actor chính (Primary)"
        BN["🧑 Bệnh nhân<br/>(Patient)"]
    end

    subgraph "Actor phụ (Secondary)"
        BS["👨‍⚕️ Bác sỹ<br/>(Doctor)"]
        HT["⚙️ Hệ thống<br/>(System)"]
    end

    BN -->|"Tương tác trực tiếp<br/>qua Mobile App"| S1["Patient Service"]
    BN -->|"Tương tác trực tiếp<br/>qua Mobile App"| S2["Appointment Service"]
    BN -->|"Tương tác trực tiếp<br/>qua Mobile App"| S3["EMR Service"]

    BS -->|"Gọi API gián tiếp<br/>qua Web App"| S1
    BS -->|"Gọi API gián tiếp<br/>qua Web App"| S3

    HT -->|"Lắng nghe Event<br/>qua RabbitMQ"| S2
    HT -->|"Gửi Event<br/>qua RabbitMQ"| S3
```

| Actor | Loại | Mô tả | Tương tác với Service nào |
|-------|------|-------|--------------------------|
| **Bệnh nhân** | Primary | Người dùng chính, tương tác qua Mobile App (Flutter). Là chủ thể của toàn bộ phân hệ. | Patient, Appointment, EMR |
| **Bác sỹ** | Secondary | Tương tác gián tiếp qua Web App (React) của Người C. Gọi API của B để tra cứu thông tin bệnh nhân và hồ sơ bệnh án. | Patient, EMR |
| **Hệ thống** | Secondary | Đại diện cho các service khác (Queue Service của A, Doctor Consultation Service của C). Giao tiếp qua RabbitMQ (event-driven). | Appointment, EMR |

> [!NOTE]
> **Actor "Hệ thống"** ở đây không phải là 1 người cụ thể, mà là các Microservice khác trong hệ thống CareFlow. Chúng tương tác với service của B thông qua **Event (RabbitMQ)** hoặc **API call trực tiếp (REST)**. Trong Use Case Diagram, ta gộp chúng lại thành 1 actor "Hệ thống" cho đơn giản.

---

## 1.2 Danh sách Use Case

### 🔵 Patient Service

| Mã UC | Tên Use Case | Actor chính | Actor phụ | Mức ưu tiên |
|-------|-------------|-------------|-----------|-------------|
| UC-P01 | Tạo hồ sơ bệnh nhân | Bệnh nhân | Hệ thống (Identity Service trigger) | 🔴 P0 |
| UC-P02 | Xem hồ sơ cá nhân | Bệnh nhân | — | 🔴 P0 |
| UC-P03 | Cập nhật hồ sơ cá nhân | Bệnh nhân | — | 🔴 P0 |
| UC-P04 | Tra cứu thông tin bệnh nhân | Bác sỹ | — | 🟡 P1 |

### 🟢 Appointment Service

| Mã UC | Tên Use Case | Actor chính | Actor phụ | Mức ưu tiên |
|-------|-------------|-------------|-----------|-------------|
| UC-A01 | Đăng ký khám bệnh | Bệnh nhân | Hệ thống (Queue Service nhận event) | 🔴 P0 |
| UC-A02 | Xem danh sách lịch hẹn | Bệnh nhân | — | 🔴 P0 |
| UC-A03 | Xem chi tiết lịch hẹn | Bệnh nhân | — | 🔴 P0 |
| UC-A04 | Hủy lịch hẹn | Bệnh nhân | Hệ thống (Queue Service nhận event hủy) | 🟡 P1 |
| UC-A05 | Xem danh sách chuyên khoa | Bệnh nhân | — | 🔴 P0 |

### 🟠 EMR Service (Electronic Medical Record)

| Mã UC | Tên Use Case | Actor chính | Actor phụ | Mức ưu tiên |
|-------|-------------|-------------|-----------|-------------|
| UC-E01 | Upload hồ sơ bệnh án | Bệnh nhân | — | 🔴 P0 |
| UC-E02 | Xem danh sách hồ sơ bệnh án | Bệnh nhân | — | 🔴 P0 |
| UC-E03 | Xem chi tiết hồ sơ bệnh án | Bệnh nhân | — | 🔴 P0 |
| UC-E04 | Tra cứu hồ sơ bệnh án của bệnh nhân | Bác sỹ | — | 🟡 P1 |
| UC-E05 | Ghi kết quả khám vào hồ sơ | Hệ thống (Doctor Consultation Service) | — | 🟡 P1 |

---

## 1.3 Use Case Diagram

### Patient Service

```mermaid
graph LR
    BN(("🧑 Bệnh nhân"))
    BS(("👨‍⚕️ Bác sỹ"))

    BN --> UCP01["UC-P01: Tạo hồ sơ bệnh nhân"]
    BN --> UCP02["UC-P02: Xem hồ sơ cá nhân"]
    BN --> UCP03["UC-P03: Cập nhật hồ sơ cá nhân"]
    BS --> UCP04["UC-P04: Tra cứu thông tin bệnh nhân"]
```

### Appointment Service

```mermaid
graph LR
    BN(("🧑 Bệnh nhân"))
    HT(("⚙️ Hệ thống"))

    BN --> UCA01["UC-A01: Đăng ký khám bệnh"]
    BN --> UCA02["UC-A02: Xem danh sách lịch hẹn"]
    BN --> UCA03["UC-A03: Xem chi tiết lịch hẹn"]
    BN --> UCA04["UC-A04: Hủy lịch hẹn"]
    BN --> UCA05["UC-A05: Xem danh sách chuyên khoa"]

    UCA01 --> HT
    UCA04 --> HT
```

### EMR Service

```mermaid
graph LR
    BN(("🧑 Bệnh nhân"))
    BS(("👨‍⚕️ Bác sỹ"))
    HT(("⚙️ Hệ thống"))

    BN --> UCE01["UC-E01: Upload hồ sơ bệnh án"]
    BN --> UCE02["UC-E02: Xem danh sách hồ sơ bệnh án"]
    BN --> UCE03["UC-E03: Xem chi tiết hồ sơ bệnh án"]
    BS --> UCE04["UC-E04: Tra cứu hồ sơ bệnh án"]
    HT --> UCE05["UC-E05: Ghi kết quả khám vào hồ sơ"]
```

---

## 1.4 Đặc tả Use Case chi tiết

---

### UC-P01: Tạo hồ sơ bệnh nhân

| Mục | Chi tiết |
|-----|----------|
| **Mã UC** | UC-P01 |
| **Tên** | Tạo hồ sơ bệnh nhân |
| **Actor** | Bệnh nhân |
| **Mô tả** | Sau khi đăng ký tài khoản thành công (qua Identity Service của A), bệnh nhân tạo hồ sơ cá nhân bao gồm thông tin y tế cơ bản. |
| **Tiền điều kiện** | Bệnh nhân đã đăng ký tài khoản và đăng nhập thành công (có JWT token). Tài khoản chưa đạt giới hạn 10 hồ sơ. |
| **Hậu điều kiện** | Hồ sơ bệnh nhân được tạo trong DB, gắn với `user_id` từ Identity Service. |

**Luồng chính (Main Flow):**
1. Bệnh nhân mở ứng dụng lần đầu sau khi đăng ký.
2. Hệ thống hiển thị form nhập thông tin cá nhân (họ tên, ngày sinh, giới tính, SĐT, CMND/CCCD, số BHYT, địa chỉ).
3. Bệnh nhân điền thông tin và nhấn "Lưu".
4. Hệ thống validate dữ liệu.
5. Hệ thống tạo bản ghi `Patient` trong DB với `user_id` lấy từ JWT token; một tài khoản có thể có nhiều hồ sơ cho người thân.
6. Hệ thống hiển thị thông báo "Tạo hồ sơ thành công" và chuyển về Trang chủ.

**Luồng thay thế (Alternative Flow):**
- **4a.** Dữ liệu không hợp lệ (thiếu họ tên, SĐT sai định dạng...):
  - Hệ thống highlight trường bị lỗi và hiển thị thông báo lỗi cụ thể.
  - Quay lại bước 3.
- **5a.** Tài khoản đã có đủ 10 hồ sơ:
  - Hệ thống trả về lỗi "Tài khoản đã đạt tối đa 10 hồ sơ bệnh nhân".
  - Người dùng cần quản lý hoặc xóa hồ sơ cũ trước khi tạo thêm.

---

### UC-P02: Xem hồ sơ cá nhân

| Mục | Chi tiết |
|-----|----------|
| **Mã UC** | UC-P02 |
| **Tên** | Xem hồ sơ cá nhân |
| **Actor** | Bệnh nhân |
| **Mô tả** | Bệnh nhân xem thông tin cá nhân của mình. |
| **Tiền điều kiện** | Bệnh nhân đã đăng nhập. Đã có hồ sơ bệnh nhân (UC-P01 đã thực hiện). |
| **Hậu điều kiện** | Không thay đổi dữ liệu. |

**Luồng chính:**
1. Bệnh nhân nhấn vào tab "Hồ sơ" trên thanh điều hướng.
2. Hệ thống gọi API `GET /api/patients/me` với JWT token.
3. Hệ thống hiển thị thông tin: họ tên, ngày sinh, giới tính, SĐT, CMND/CCCD, số BHYT, địa chỉ, ảnh đại diện.

**Luồng ngoại lệ:**
- **2a.** Không tìm thấy hồ sơ: Chuyển hướng tới form tạo hồ sơ (UC-P01).
- **2b.** Lỗi mạng/server: Hiển thị thông báo lỗi + nút "Thử lại".

---

### UC-P03: Cập nhật hồ sơ cá nhân

| Mục | Chi tiết |
|-----|----------|
| **Mã UC** | UC-P03 |
| **Tên** | Cập nhật hồ sơ cá nhân |
| **Actor** | Bệnh nhân |
| **Mô tả** | Bệnh nhân chỉnh sửa thông tin cá nhân (SĐT, địa chỉ, số BHYT, ảnh đại diện...). |
| **Tiền điều kiện** | Đã đăng nhập, đã có hồ sơ. |
| **Hậu điều kiện** | Thông tin bệnh nhân được cập nhật trong DB. |

**Luồng chính:**
1. Bệnh nhân đang ở màn hình Hồ sơ (UC-P02), nhấn nút "Chỉnh sửa".
2. Hệ thống hiển thị form chỉnh sửa với dữ liệu hiện tại đã được điền sẵn.
3. Bệnh nhân sửa các trường mong muốn và nhấn "Lưu".
4. Hệ thống validate dữ liệu mới.
5. Hệ thống gọi API `PUT /api/patients/{id}` để cập nhật.
6. Hệ thống hiển thị thông báo "Cập nhật thành công".

**Luồng thay thế:**
- **4a.** Dữ liệu không hợp lệ: Hiển thị lỗi, quay lại bước 3.
- **3a.** Bệnh nhân nhấn "Hủy": Quay lại màn hình xem hồ sơ, không thay đổi gì.

---

### UC-P04: Tra cứu thông tin bệnh nhân

| Mục | Chi tiết |
|-----|----------|
| **Mã UC** | UC-P04 |
| **Tên** | Tra cứu thông tin bệnh nhân |
| **Actor** | Bác sỹ |
| **Mô tả** | Bác sỹ tra cứu thông tin cá nhân của bệnh nhân trước hoặc trong khi khám. Bác sỹ truy cập qua Web App (của Người C), Web App gọi API của Patient Service. |
| **Tiền điều kiện** | Bác sỹ đã đăng nhập trên Web App với role `DOCTOR`. |
| **Hậu điều kiện** | Không thay đổi dữ liệu. |

**Luồng chính:**
1. Bác sỹ chọn bệnh nhân từ danh sách hàng đợi (dữ liệu từ Queue Service của A).
2. Web App gọi API `GET /api/patients/{id}` của Patient Service.
3. Hiển thị thông tin bệnh nhân: họ tên, tuổi, giới tính, số BHYT, tiền sử dị ứng.

**Luồng ngoại lệ:**
- **2a.** Không tìm thấy bệnh nhân: Hiển thị "Không tìm thấy hồ sơ bệnh nhân".

> [!NOTE]
> **Phân quyền quan trọng:** API `GET /api/patients/{id}` chỉ cho phép role `DOCTOR` hoặc chính bệnh nhân đó (kiểm tra `patient_id` trong JWT token khớp với `{id}`). Bệnh nhân không được xem hồ sơ của bệnh nhân khác.

---

### UC-A01: Đăng ký khám bệnh

| Mục | Chi tiết |
|-----|----------|
| **Mã UC** | UC-A01 |
| **Tên** | Đăng ký khám bệnh |
| **Actor** | Bệnh nhân |
| **Mô tả** | Bệnh nhân đăng ký lịch khám bệnh bằng cách chọn chuyên khoa, ngày, và ca khám. Đây là use case cốt lõi nhất của phân hệ Bệnh nhân theo đề bài. |
| **Tiền điều kiện** | Bệnh nhân đã đăng nhập, đã có hồ sơ (UC-P01). |
| **Hậu điều kiện** | Lịch hẹn được tạo trong DB với trạng thái `PENDING`. Event `AppointmentCreated` được publish lên RabbitMQ để Queue Service (A) xử lý. |

**Luồng chính:**
1. Bệnh nhân nhấn "Đăng ký khám" trên Trang chủ.
2. Hệ thống hiển thị danh sách chuyên khoa (lấy từ API `GET /api/appointments/departments`).
3. Bệnh nhân chọn chuyên khoa.
4. Hệ thống hiển thị lịch (calendar) để chọn ngày khám.
5. Bệnh nhân chọn ngày.
6. Hệ thống hiển thị các ca khám còn trống trong ngày đã chọn (ví dụ: "08:00-08:30", "08:30-09:00"...).
7. Bệnh nhân chọn ca khám.
8. Hệ thống hiển thị màn hình **Xác nhận**: tóm tắt chuyên khoa, ngày, giờ, thông tin bệnh nhân.
9. Bệnh nhân nhấn "Xác nhận đặt lịch".
10. Hệ thống tạo bản ghi `Appointment` trong DB (trạng thái = `PENDING`).
11. Hệ thống publish event `AppointmentCreated` lên RabbitMQ.
12. Hệ thống hiển thị thông báo "Đặt lịch thành công" + thông tin lịch hẹn.

**Luồng thay thế:**
- **6a.** Không còn ca trống trong ngày đã chọn:
  - Hiển thị "Ngày này đã hết lịch, vui lòng chọn ngày khác."
  - Quay lại bước 4.
- **9a.** Bệnh nhân nhấn "Quay lại":
  - Quay lại bước trước đó, không tạo lịch hẹn.
- **10a.** Bệnh nhân đã có lịch hẹn cùng chuyên khoa, cùng ngày:
  - Hệ thống trả lỗi "Bạn đã có lịch hẹn khoa Nội ngày 20/07. Vui lòng hủy lịch cũ trước."

**Sự kiện phát sinh (Event):**

```json
// AppointmentCreatedEvent → publish lên RabbitMQ
{
    "eventType": "APPOINTMENT_CREATED",
    "appointmentId": "uuid",
    "patientId": "uuid",
    "patientName": "Nguyễn Văn A",
    "department": "Nội khoa",
    "appointmentDate": "2026-07-20",
    "timeSlot": "08:00-08:30",
    "createdAt": "2026-07-15T10:30:00"
}
```

> [!IMPORTANT]
> Appointment Service không tự quyết định diện ưu tiên. Queue Service mặc định
> lượt khám ban đầu là `NORMAL`; nhân viên có quyền chỉ xác nhận `PRIORITY` khi
> check-in sau khi kiểm tra diện ưu tiên và ghi nhận lý do/audit.

---

### UC-A02: Xem danh sách lịch hẹn

| Mục | Chi tiết |
|-----|----------|
| **Mã UC** | UC-A02 |
| **Tên** | Xem danh sách lịch hẹn |
| **Actor** | Bệnh nhân |
| **Mô tả** | Bệnh nhân xem tất cả lịch hẹn khám của mình, phân loại theo: sắp tới, đã hoàn thành, đã hủy. |
| **Tiền điều kiện** | Bệnh nhân đã đăng nhập. |
| **Hậu điều kiện** | Không thay đổi dữ liệu. |

**Luồng chính:**
1. Bệnh nhân nhấn tab "Lịch hẹn" trên thanh điều hướng.
2. Hệ thống gọi API `GET /api/appointments/patient/{patientId}`.
3. Hệ thống hiển thị danh sách lịch hẹn, mặc định hiển thị tab "Sắp tới".
4. Mỗi lịch hẹn hiển thị: chuyên khoa, ngày giờ, trạng thái (badge màu).

**Luồng thay thế:**
- **3a.** Chưa có lịch hẹn nào: Hiển thị empty state "Bạn chưa có lịch hẹn nào" + nút "Đăng ký khám ngay".

---

### UC-A03: Xem chi tiết lịch hẹn

| Mục | Chi tiết |
|-----|----------|
| **Mã UC** | UC-A03 |
| **Tên** | Xem chi tiết lịch hẹn |
| **Actor** | Bệnh nhân |
| **Mô tả** | Bệnh nhân xem thông tin chi tiết của một lịch hẹn cụ thể: chuyên khoa, ngày giờ, trạng thái, số thứ tự (nếu đã được cấp), ghi chú. |
| **Tiền điều kiện** | Bệnh nhân đã đăng nhập, đã có ít nhất 1 lịch hẹn. |
| **Hậu điều kiện** | Không thay đổi dữ liệu. |

**Luồng chính:**
1. Từ danh sách lịch hẹn (UC-A02), bệnh nhân nhấn vào một lịch hẹn.
2. Hệ thống gọi API `GET /api/appointments/{id}`.
3. Hệ thống hiển thị chi tiết: chuyên khoa, ngày, ca khám, trạng thái, ghi chú.
4. Nếu trạng thái là `CONFIRMED` hoặc `PENDING`: hiển thị nút "Hủy lịch hẹn".
5. Nếu đã được cấp số thứ tự (từ Queue Service của A): hiển thị số thứ tự và vị trí hàng đợi.

---

### UC-A04: Hủy lịch hẹn

| Mục | Chi tiết |
|-----|----------|
| **Mã UC** | UC-A04 |
| **Tên** | Hủy lịch hẹn |
| **Actor** | Bệnh nhân |
| **Mô tả** | Bệnh nhân hủy một lịch hẹn đã đặt trước khi ngày khám diễn ra. |
| **Tiền điều kiện** | Lịch hẹn đang ở trạng thái `PENDING` hoặc `CONFIRMED`. Ngày khám chưa đến. |
| **Hậu điều kiện** | Trạng thái lịch hẹn chuyển thành `CANCELLED`. Event `AppointmentCancelled` được publish lên RabbitMQ. |

**Luồng chính:**
1. Từ chi tiết lịch hẹn (UC-A03), bệnh nhân nhấn nút "Hủy lịch hẹn".
2. Hệ thống hiển thị dialog xác nhận: "Bạn có chắc muốn hủy lịch khám khoa Nội ngày 20/07?"
3. Bệnh nhân nhấn "Xác nhận hủy".
4. Hệ thống cập nhật trạng thái lịch hẹn thành `CANCELLED`.
5. Hệ thống publish event `AppointmentCancelled` lên RabbitMQ (để Queue Service của A xóa khỏi hàng đợi).
6. Hệ thống hiển thị "Hủy lịch thành công" và quay về danh sách lịch hẹn.

**Luồng thay thế:**
- **3a.** Bệnh nhân nhấn "Không": Đóng dialog, quay lại chi tiết lịch hẹn.
- **1a.** Lịch hẹn đã ở trạng thái `CHECKED_IN` hoặc `IN_PROGRESS`: Nút "Hủy" bị ẩn, không cho hủy.

---

### UC-A05: Xem danh sách chuyên khoa

| Mục | Chi tiết |
|-----|----------|
| **Mã UC** | UC-A05 |
| **Tên** | Xem danh sách chuyên khoa |
| **Actor** | Bệnh nhân |
| **Mô tả** | Bệnh nhân xem danh sách các chuyên khoa đang hoạt động tại bệnh viện để chọn khi đăng ký khám. |
| **Tiền điều kiện** | Bệnh nhân đã đăng nhập. |
| **Hậu điều kiện** | Không thay đổi dữ liệu. |

**Luồng chính:**
1. Hệ thống gọi API `GET /api/appointments/departments`.
2. Hiển thị danh sách chuyên khoa: tên, mô tả, vị trí (tầng/khu), trạng thái hoạt động.

> [!NOTE]
> Use case này thường được **include** (bao gồm) trong UC-A01 (Đăng ký khám bệnh) ở bước 2. Tuy nhiên, bệnh nhân cũng có thể xem danh sách chuyên khoa độc lập từ Trang chủ để tìm hiểu trước khi đặt lịch.

---

### UC-E01: Upload hồ sơ bệnh án

| Mục | Chi tiết |
|-----|----------|
| **Mã UC** | UC-E01 |
| **Tên** | Upload hồ sơ bệnh án |
| **Actor** | Bệnh nhân |
| **Mô tả** | Bệnh nhân tải lên hồ sơ bệnh án cũ (kết quả xét nghiệm, phim chụp X-quang, giấy ra viện...) từ điện thoại. Đây là yêu cầu trực tiếp từ đề bài: "upload hồ sơ bệnh án (lịch sử khám điều trị và các xét nghiệm)". |
| **Tiền điều kiện** | Bệnh nhân đã đăng nhập, đã có hồ sơ bệnh nhân. |
| **Hậu điều kiện** | Bản ghi `MedicalRecord` được tạo trong DB. File được lưu trữ (local storage hoặc cloud). |

**Luồng chính:**
1. Bệnh nhân vào mục "Hồ sơ bệnh án" và nhấn nút "Thêm hồ sơ".
2. Hệ thống hiển thị form:
   - Loại hồ sơ (dropdown): Kết quả xét nghiệm, Phim chụp, Giấy ra viện, Đơn thuốc cũ, Khác
   - Tiêu đề (text): Ví dụ "Kết quả xét nghiệm máu 15/06/2026"
   - Ngày khám (date picker)
   - Mô tả / Ghi chú (text, không bắt buộc)
   - Đính kèm file (chọn ảnh từ thư viện hoặc chụp mới)
3. Bệnh nhân điền thông tin và đính kèm file (ảnh/PDF).
4. Bệnh nhân nhấn "Lưu".
5. Hệ thống upload file lên server (API `POST /api/emr/records/{id}/upload`).
6. Hệ thống tạo bản ghi `MedicalRecord` trong DB với `file_url` trỏ đến file đã upload.
7. Hiển thị "Upload thành công".

**Luồng thay thế:**
- **5a.** File quá lớn (>10MB): Hiển thị lỗi "File quá lớn, vui lòng chọn file dưới 10MB".
- **5b.** Định dạng không hỗ trợ: Hiển thị lỗi "Chỉ hỗ trợ ảnh (JPG, PNG) hoặc PDF".
- **5c.** Lỗi mạng khi upload: Hiển thị "Upload thất bại" + nút "Thử lại".

---

### UC-E02: Xem danh sách hồ sơ bệnh án

| Mục | Chi tiết |
|-----|----------|
| **Mã UC** | UC-E02 |
| **Tên** | Xem danh sách hồ sơ bệnh án |
| **Actor** | Bệnh nhân |
| **Mô tả** | Bệnh nhân xem tất cả hồ sơ bệnh án của mình, bao gồm cả hồ sơ tự upload và hồ sơ do bác sỹ tạo sau khi khám. |
| **Tiền điều kiện** | Bệnh nhân đã đăng nhập. |
| **Hậu điều kiện** | Không thay đổi dữ liệu. |

**Luồng chính:**
1. Bệnh nhân vào mục "Hồ sơ bệnh án".
2. Hệ thống gọi API `GET /api/emr/records/patient/{patientId}`.
3. Hiển thị danh sách hồ sơ, sắp xếp theo ngày (mới nhất trước). Mỗi hồ sơ hiển thị: loại (icon + màu), tiêu đề, ngày, nguồn (tự upload / bác sỹ tạo).
4. Bệnh nhân có thể lọc theo loại hồ sơ (tất cả, xét nghiệm, phim chụp, kết quả khám...).

**Luồng thay thế:**
- **3a.** Chưa có hồ sơ nào: Hiển thị empty state + nút "Upload hồ sơ đầu tiên".

---

### UC-E03: Xem chi tiết hồ sơ bệnh án

| Mục | Chi tiết |
|-----|----------|
| **Mã UC** | UC-E03 |
| **Tên** | Xem chi tiết hồ sơ bệnh án |
| **Actor** | Bệnh nhân |
| **Mô tả** | Bệnh nhân xem chi tiết một hồ sơ bệnh án cụ thể, bao gồm ảnh/file đính kèm. |
| **Tiền điều kiện** | Bệnh nhân đã đăng nhập, có ít nhất 1 hồ sơ. |
| **Hậu điều kiện** | Không thay đổi dữ liệu. |

**Luồng chính:**
1. Từ danh sách (UC-E02), bệnh nhân nhấn vào một hồ sơ.
2. Hệ thống gọi API `GET /api/emr/records/{id}`.
3. Hiển thị: tiêu đề, loại hồ sơ, ngày, mô tả, file đính kèm (ảnh hiển thị trực tiếp, PDF có nút "Mở").

---

### UC-E04: Tra cứu hồ sơ bệnh án của bệnh nhân

| Mục | Chi tiết |
|-----|----------|
| **Mã UC** | UC-E04 |
| **Tên** | Tra cứu hồ sơ bệnh án của bệnh nhân |
| **Actor** | Bác sỹ |
| **Mô tả** | Bác sỹ tra cứu toàn bộ hồ sơ bệnh án của một bệnh nhân trước/trong khi khám để nắm tiền sử bệnh. Bác sỹ thao tác trên Web App (của Người C), Web App gọi API của EMR Service. |
| **Tiền điều kiện** | Bác sỹ đã đăng nhập, có role `DOCTOR`. |
| **Hậu điều kiện** | Không thay đổi dữ liệu. |

**Luồng chính:**
1. Bác sỹ chọn bệnh nhân (từ hàng đợi hoặc tìm kiếm).
2. Web App gọi API `GET /api/emr/records/patient/{patientId}`.
3. Hiển thị danh sách hồ sơ bệnh án: chẩn đoán cũ, kết quả xét nghiệm, phim chụp...
4. Bác sỹ nhấn vào từng hồ sơ để xem chi tiết + file đính kèm.

> [!NOTE]
> **Phân quyền:** API này chỉ cho phép role `DOCTOR`. Bệnh nhân chỉ được xem hồ sơ của chính mình qua UC-E02.

---

### UC-E05: Ghi kết quả khám vào hồ sơ

| Mục | Chi tiết |
|-----|----------|
| **Mã UC** | UC-E05 |
| **Tên** | Ghi kết quả khám vào hồ sơ |
| **Actor** | Hệ thống (Doctor Consultation Service của Người C) |
| **Mô tả** | Sau khi bác sỹ khám xong và ghi chẩn đoán trên Web App, Doctor Consultation Service (C) gửi event hoặc gọi API để lưu kết quả khám vào EMR Service (B). |
| **Tiền điều kiện** | Buổi khám đã diễn ra, bác sỹ đã ghi chẩn đoán trên hệ thống. |
| **Hậu điều kiện** | Bản ghi `MedicalRecord` mới được tạo với `record_type = EXAMINATION`. Bệnh nhân có thể xem kết quả khám này trên Mobile App. |

**Luồng chính:**
1. Doctor Consultation Service (C) publish event `ConsultationCompleted` lên RabbitMQ.
2. EMR Service (B) nhận event.
3. EMR Service tạo bản ghi `MedicalRecord` mới:
   - `patient_id`: từ event
   - `record_type`: `EXAMINATION`
   - `title`: "Khám Nội khoa — BS. Nguyễn Văn X"
   - `description`: chẩn đoán + ghi chú của bác sỹ
   - `record_date`: ngày khám
4. Bệnh nhân mở app → thấy hồ sơ mới trong danh sách bệnh án (UC-E02).

**Event nhận:**

```json
// ConsultationCompletedEvent — nhận từ RabbitMQ (Người C gửi)
{
    "eventType": "CONSULTATION_COMPLETED",
    "consultationId": "uuid",
    "patientId": "uuid",
    "doctorId": "uuid",
    "doctorName": "BS. Nguyễn Văn X",
    "department": "Nội khoa",
    "diagnosis": "Viêm họng cấp",
    "notes": "Bệnh nhân cần nghỉ ngơi, uống nhiều nước",
    "consultationDate": "2026-07-20",
    "appointmentId": "uuid"
}
```

---

## 1.5 Activity Diagram — Luồng chính

### Luồng Đăng ký khám bệnh (UC-A01) — End-to-end

```mermaid
flowchart TD
    Start([Bệnh nhân mở app]) --> A1[Nhấn 'Đăng ký khám']
    A1 --> A2[Hiển thị danh sách chuyên khoa]
    A2 --> A3[Chọn chuyên khoa]
    A3 --> A4[Hiển thị lịch chọn ngày]
    A4 --> A5[Chọn ngày khám]
    A5 --> A6[Hiển thị ca khám còn trống]
    A6 --> A7{Còn ca trống?}
    A7 -->|Không| A4
    A7 -->|Có| A8[Chọn ca khám]
    A8 --> A9[Hiển thị màn xác nhận]
    A9 --> A10{Xác nhận?}
    A10 -->|Hủy| A8
    A10 -->|Xác nhận| A11[Tạo Appointment trong DB]
    A11 --> A12[Publish AppointmentCreated Event]
    A12 --> A13[Queue Service nhận event]
    A13 --> A14[Cấp số thứ tự]
    A14 --> A15[Notification Service gửi thông báo]
    A15 --> A16[Mobile nhận: Số thứ tự + thời gian chờ]
    A16 --> End([Hoàn thành])

    style A11 fill:#4CAF50,color:#fff
    style A12 fill:#FF9800,color:#fff
    style A13 fill:#2196F3,color:#fff
    style A14 fill:#2196F3,color:#fff
    style A15 fill:#2196F3,color:#fff
    style A16 fill:#4CAF50,color:#fff
```

> **Chú thích màu:**
> - 🟢 Xanh lá = Service của B xử lý
> - 🟠 Cam = RabbitMQ Event
> - 🔵 Xanh dương = Service của A xử lý

### Luồng Upload hồ sơ bệnh án (UC-E01)

```mermaid
flowchart TD
    Start([Bệnh nhân]) --> E1[Nhấn 'Thêm hồ sơ']
    E1 --> E2[Chọn loại hồ sơ]
    E2 --> E3[Nhập tiêu đề + ghi chú]
    E3 --> E4[Chọn file đính kèm]
    E4 --> E5{File hợp lệ?}
    E5 -->|Quá lớn / Sai định dạng| E6[Hiển thị lỗi]
    E6 --> E4
    E5 -->|Hợp lệ| E7[Upload file lên server]
    E7 --> E8{Upload thành công?}
    E8 -->|Lỗi mạng| E9[Hiển thị 'Thử lại']
    E9 --> E7
    E8 -->|Thành công| E10[Tạo MedicalRecord trong DB]
    E10 --> E11[Hiển thị 'Upload thành công']
    E11 --> End([Hoàn thành])
```

---

## 1.6 Ma trận Actor × Use Case (CRUD Matrix)

| Use Case | Bệnh nhân | Bác sỹ | Hệ thống |
|----------|-----------|--------|----------|
| **Patient Service** | | | |
| UC-P01: Tạo hồ sơ bệnh nhân | ✅ Create | — | — |
| UC-P02: Xem hồ sơ cá nhân | ✅ Read (chỉ của mình) | — | — |
| UC-P03: Cập nhật hồ sơ | ✅ Update (chỉ của mình) | — | — |
| UC-P04: Tra cứu bệnh nhân | — | ✅ Read (bất kỳ) | — |
| **Appointment Service** | | | |
| UC-A01: Đăng ký khám | ✅ Create | — | 📩 Nhận event |
| UC-A02: Xem danh sách lịch hẹn | ✅ Read (chỉ của mình) | — | — |
| UC-A03: Xem chi tiết lịch hẹn | ✅ Read (chỉ của mình) | — | — |
| UC-A04: Hủy lịch hẹn | ✅ Update (chỉ của mình) | — | 📩 Nhận event |
| UC-A05: Xem chuyên khoa | ✅ Read | — | — |
| **EMR Service** | | | |
| UC-E01: Upload hồ sơ bệnh án | ✅ Create | — | — |
| UC-E02: Xem danh sách hồ sơ | ✅ Read (chỉ của mình) | — | — |
| UC-E03: Xem chi tiết hồ sơ | ✅ Read (chỉ của mình) | — | — |
| UC-E04: Tra cứu hồ sơ bệnh nhân | — | ✅ Read (bất kỳ) | — |
| UC-E05: Ghi kết quả khám | — | — | ✅ Create (qua Event) |

---

## Các điểm cần thống nhất với Người A và Người C

> [!WARNING]
> Những điểm dưới đây **BẮT BUỘC** phải thống nhất với nhóm trước khi bắt tay vào code. Nếu không thống nhất sẽ dẫn đến lỗi tích hợp.

### Thống nhất với Người A (Phân hệ Hệ thống)

| # | Nội dung cần thống nhất | Lý do |
|---|------------------------|-------|
| 1 | Cấu trúc JWT Token chứa những trường gì? (`user_id`, `role`, `patient_id`?) | Patient Service cần lấy `user_id` từ token để biết bệnh nhân nào đang gọi API |
| 2 | Payload của event `AppointmentCreated` gồm những gì? | Queue Service cần nhận đúng format để tạo số thứ tự |
| 3 | Payload của event `AppointmentCancelled` gồm những gì? | Queue Service cần biết để xóa/cập nhật hàng đợi |
| 4 | Tên Exchange, Routing Key trên RabbitMQ? | Hai bên phải dùng chung tên để message đi đúng đích |
| 5 | API lấy số thứ tự: `GET /api/queues/patient/{patientId}/status` — response trả gì? | Mobile App của B cần hiển thị số thứ tự, vị trí, thời gian chờ |

### Thống nhất với Người C (Phân hệ Bác sỹ)

| # | Nội dung cần thống nhất | Lý do |
|---|------------------------|-------|
| 1 | API tra cứu bệnh nhân: `GET /api/patients/{id}` — response trả những gì? | Web App của C cần hiển thị thông tin bệnh nhân khi bác sỹ khám |
| 2 | API tra cứu hồ sơ bệnh án: `GET /api/emr/records/patient/{patientId}` — response trả gì? | Bác sỹ cần xem lịch sử bệnh trước khi khám |
| 3 | Payload event `ConsultationCompleted` gồm những gì? | EMR Service (B) cần nhận đúng format để lưu kết quả khám |
| 4 | Phân quyền: role `DOCTOR` được gọi API nào của B? | Tránh việc Web App gọi nhầm API chỉ dành cho bệnh nhân |

---

# PHASE 2: THIẾT KẾ (Design)

---

## 2.1 Domain Model (Class Diagram)

### Tổng quan quan hệ giữa 3 Service

```mermaid
classDiagram
    direction LR

    namespace PatientService {
        class Patient {
            +UUID id
            +UUID userId
            +String fullName
            +LocalDate dateOfBirth
            +Gender gender
            +String phone
            +String idCardNumber
            +String insuranceNumber
            +String occupation
            +String address
            +String avatarUrl
            +LocalDateTime createdAt
            +LocalDateTime updatedAt
        }

        class Gender {
            <<enumeration>>
            MALE
            FEMALE
            OTHER
        }
    }

    namespace AppointmentService {
        class Appointment {
            +UUID id
            +UUID patientId
            +UUID departmentId
            +LocalDate appointmentDate
            +String timeSlot
            +AppointmentStatus status
            +String notes
            +LocalDateTime createdAt
            +LocalDateTime updatedAt
        }

        class Department {
            +UUID id
            +String name
            +String description
            +String location
            +Boolean isActive
        }

        class AppointmentStatus {
            <<enumeration>>
            PENDING
            CONFIRMED
            CHECKED_IN
            IN_PROGRESS
            COMPLETED
            CANCELLED
        }
    }

    namespace EMRService {
        class MedicalRecord {
            +UUID id
            +UUID patientId
            +UUID appointmentId
            +RecordType recordType
            +String title
            +String description
            +String fileUrl
            +LocalDate recordDate
            +RecordSource source
            +LocalDateTime createdAt
            +LocalDateTime updatedAt
        }

        class RecordType {
            <<enumeration>>
            EXAMINATION
            LAB_RESULT
            IMAGING
            DISCHARGE_SUMMARY
            PRESCRIPTION_OLD
            OTHER
        }

        class RecordSource {
            <<enumeration>>
            PATIENT_UPLOAD
            DOCTOR_CREATED
            SYSTEM_GENERATED
        }
    }

    Patient "1" ..> "*" Appointment : patientId
    Patient "1" ..> "*" MedicalRecord : patientId
    Appointment "1" ..> "0..1" MedicalRecord : appointmentId
    Appointment "*" --> "1" Department : departmentId
    Appointment --> AppointmentStatus
    Patient --> Gender
    MedicalRecord --> RecordType
    MedicalRecord --> RecordSource
```

> [!NOTE]
> **Đường nét đứt (`..>`)** thể hiện mối quan hệ **liên service** — chỉ tham chiếu qua UUID, KHÔNG có Foreign Key thật trong DB (nguyên tắc Database-per-Service của Microservices). Đường nét liền (`-->`) là quan hệ **trong cùng service**.

---

### Chi tiết từng Domain Class

#### Patient (Patient Service)

| Thuộc tính | Kiểu | Ràng buộc | Mô tả |
|-----------|------|-----------|-------|
| `id` | UUID | PK, auto-gen | Mã bệnh nhân |
| `userId` | UUID | NOT NULL, UNIQUE | FK logic tới Identity Service (A) |
| `fullName` | String(100) | NOT NULL | Họ và tên |
| `dateOfBirth` | LocalDate | nullable | Ngày sinh |
| `gender` | Gender (enum) | nullable | Giới tính |
| `phone` | String(15) | nullable | Số điện thoại |
| `idCardNumber` | String(20) | nullable, UNIQUE | CMND/CCCD |
| `insuranceNumber` | String(20) | nullable | Số thẻ BHYT |
| `occupation` | String(100) | nullable | Nghề nghiệp (hỗ trợ chẩn đoán — bác sĩ biết môi trường tiếp xúc) |
| `address` | String(500) | nullable | Địa chỉ |
| `avatarUrl` | String(255) | nullable | URL ảnh đại diện |
| `createdAt` | LocalDateTime | auto | Thời điểm tạo |
| `updatedAt` | LocalDateTime | auto | Thời điểm cập nhật |

#### Appointment (Appointment Service)

| Thuộc tính | Kiểu | Ràng buộc | Mô tả |
|-----------|------|-----------|-------|
| `id` | UUID | PK, auto-gen | Mã lịch hẹn |
| `patientId` | UUID | NOT NULL | ID bệnh nhân (tham chiếu logic) |
| `departmentId` | UUID | NOT NULL | FK tới bảng departments |
| `appointmentDate` | LocalDate | NOT NULL | Ngày khám |
| `timeSlot` | String(20) | NOT NULL | Ca khám, ví dụ: "08:00-08:30" |
| `status` | AppointmentStatus | NOT NULL, default PENDING | Trạng thái lịch hẹn |
| `notes` | String(500) | nullable | Ghi chú của bệnh nhân |
| `createdAt` | LocalDateTime | auto | Thời điểm tạo |
| `updatedAt` | LocalDateTime | auto | Thời điểm cập nhật |

#### Department (Appointment Service)

| Thuộc tính | Kiểu | Ràng buộc | Mô tả |
|-----------|------|-----------|-------|
| `id` | UUID | PK, auto-gen | Mã chuyên khoa |
| `name` | String(100) | NOT NULL, UNIQUE | Tên chuyên khoa |
| `description` | String(500) | nullable | Mô tả |
| `location` | String(100) | nullable | Vị trí: "Tầng 2, Khu A" |
| `isActive` | Boolean | default true | Đang hoạt động hay không |

#### MedicalRecord (EMR Service)

| Thuộc tính | Kiểu | Ràng buộc | Mô tả |
|-----------|------|-----------|-------|
| `id` | UUID | PK, auto-gen | Mã hồ sơ bệnh án |
| `patientId` | UUID | NOT NULL | ID bệnh nhân (tham chiếu logic) |
| `appointmentId` | UUID | nullable | ID lịch hẹn liên quan (nếu có) |
| `recordType` | RecordType | NOT NULL | Loại hồ sơ |
| `title` | String(200) | NOT NULL | Tiêu đề |
| `description` | Text | nullable | Mô tả chi tiết / Chẩn đoán |
| `fileUrl` | String(500) | nullable | Đường dẫn file đính kèm |
| `recordDate` | LocalDate | NOT NULL | Ngày khám / ngày xét nghiệm |
| `source` | RecordSource | NOT NULL | Nguồn: bệnh nhân tự upload hay bác sỹ tạo |
| `createdAt` | LocalDateTime | auto | Thời điểm tạo |
| `updatedAt` | LocalDateTime | auto | Thời điểm cập nhật |

---

### Sơ đồ chuyển trạng thái Appointment (State Machine)

```mermaid
stateDiagram-v2
    [*] --> PENDING : Bệnh nhân đặt lịch

    PENDING --> CONFIRMED : Queue Service cấp số thứ tự
    PENDING --> CANCELLED : Bệnh nhân hủy

    CONFIRMED --> CHECKED_IN : Bệnh nhân quét QR tại viện
    CONFIRMED --> CANCELLED : Bệnh nhân hủy

    CHECKED_IN --> IN_PROGRESS : Bác sỹ gọi vào phòng khám
    CHECKED_IN --> CONFIRMED : Bệnh nhân lỡ lượt (quay lại hàng đợi)

    IN_PROGRESS --> COMPLETED : Bác sỹ khám xong

    COMPLETED --> [*]
    CANCELLED --> [*]
```

> [!IMPORTANT]
> **Ai cập nhật trạng thái?**
> - `PENDING → CONFIRMED`: Queue Service (A) gửi event `QueueNumberAssigned` → Appointment Service (B) cập nhật
> - `CONFIRMED → CHECKED_IN`: Queue Service (A) xác nhận QR → gửi event → Appointment Service (B) cập nhật
> - `CHECKED_IN → IN_PROGRESS`: Doctor Consultation Service (C) bắt đầu khám → gửi event → Appointment Service (B) cập nhật
> - `IN_PROGRESS → COMPLETED`: Doctor Consultation Service (C) hoàn thành → gửi event → Appointment Service (B) cập nhật
> - `→ CANCELLED`: Bệnh nhân tự hủy trên Mobile App → Appointment Service (B) xử lý trực tiếp

---

## 2.2 Sequence Diagram

### Luồng 1: Đăng ký khám bệnh (UC-A01) — Cross-service

```mermaid
sequenceDiagram
    actor BN as Bệnh nhân (Mobile)
    participant GW as API Gateway
    participant AS as Appointment Service (B)
    participant DB_A as DB Appointment
    participant MQ as RabbitMQ
    participant QS as Queue Service (A)
    participant NS as Notification Service (A)

    BN->>GW: GET /api/appointments/departments
    GW->>AS: Forward request
    AS->>DB_A: SELECT * FROM departments WHERE is_active = true
    DB_A-->>AS: Danh sách chuyên khoa
    AS-->>GW: 200 OK + departments[]
    GW-->>BN: Hiển thị danh sách chuyên khoa

    BN->>BN: Chọn khoa, ngày, ca khám

    BN->>GW: POST /api/appointments
    Note right of BN: {patientId, departmentId,<br/>appointmentDate, timeSlot, notes}
    GW->>AS: Forward request + JWT
    AS->>AS: Validate: trùng lịch? Ca còn trống?
    AS->>DB_A: SELECT active ClinicRoom theo khoa
    DB_A-->>AS: Đúng một roomId trong MVP
    AS->>DB_A: INSERT INTO appointments (..., room_id)
    DB_A-->>AS: Appointment created

    AS->>MQ: Publish "AppointmentConfirmed" có roomId
    Note right of MQ: Exchange: appointment.exchange<br/>Routing Key: appointment.confirmed

    AS-->>GW: 201 Created + appointment
    GW-->>BN: Hiển thị "Đặt lịch thành công"

    MQ->>QS: Consume event
    QS->>QS: Tạo Visit Ticket cho đúng roomId
    QS->>MQ: Publish "QueueNumberAssigned"
    Note right of MQ: Exchange: queue.exchange<br/>Routing Key: queue.number.assigned

    MQ->>AS: Consume "QueueNumberAssigned"
    AS->>DB_A: UPDATE status = CONFIRMED

    MQ->>NS: Consume "QueueNumberAssigned"
    NS->>BN: WebSocket: "Số thứ tự của bạn: A-042"
```

---

### Luồng 2: Hủy lịch hẹn (UC-A04)

```mermaid
sequenceDiagram
    actor BN as Bệnh nhân (Mobile)
    participant GW as API Gateway
    participant AS as Appointment Service (B)
    participant DB_A as DB Appointment
    participant MQ as RabbitMQ
    participant QS as Queue Service (A)

    BN->>GW: PUT /api/appointments/{id}/cancel
    GW->>AS: Forward request + JWT

    AS->>DB_A: SELECT * FROM appointments WHERE id = {id}
    DB_A-->>AS: Appointment data

    AS->>AS: Validate: chủ sở hữu? Trạng thái cho phép hủy?

    alt Trạng thái không cho phép hủy (CHECKED_IN, IN_PROGRESS, COMPLETED)
        AS-->>GW: 400 Bad Request "Không thể hủy lịch hẹn ở trạng thái này"
        GW-->>BN: Hiển thị lỗi
    else Trạng thái cho phép hủy (PENDING, CONFIRMED)
        AS->>DB_A: UPDATE status = CANCELLED
        AS->>MQ: Publish "AppointmentCancelled"
        AS-->>GW: 200 OK
        GW-->>BN: "Hủy lịch thành công"
        MQ->>QS: Consume → Xóa khỏi hàng đợi
    end
```

---

### Luồng 3: Upload hồ sơ bệnh án (UC-E01)

```mermaid
sequenceDiagram
    actor BN as Bệnh nhân (Mobile)
    participant GW as API Gateway
    participant EMR as EMR Service (B)
    participant DB_E as DB EMR
    participant FS as File Storage

    BN->>GW: POST /api/emr/records (multipart/form-data)
    Note right of BN: {patientId, recordType, title,<br/>description, recordDate, file}
    GW->>EMR: Forward request + JWT

    EMR->>EMR: Validate: file size ≤ 10MB? Đúng định dạng?

    alt File không hợp lệ
        EMR-->>GW: 400 Bad Request "File quá lớn / Sai định dạng"
        GW-->>BN: Hiển thị lỗi
    else File hợp lệ
        EMR->>FS: Lưu file (local disk hoặc cloud)
        FS-->>EMR: fileUrl
        EMR->>DB_E: INSERT INTO medical_records (... fileUrl, source='PATIENT_UPLOAD')
        DB_E-->>EMR: Record created
        EMR-->>GW: 201 Created + medicalRecord
        GW-->>BN: "Upload thành công"
    end
```

---

### Luồng 4: Ghi kết quả khám — Cross-service (UC-E05)

```mermaid
sequenceDiagram
    participant DC as Doctor Consultation (C)
    participant MQ as RabbitMQ
    participant EMR as EMR Service (B)
    participant DB_E as DB EMR
    participant AS as Appointment Service (B)
    participant DB_A as DB Appointment

    DC->>MQ: Publish "ConsultationCompleted"
    Note right of MQ: Exchange: consultation.exchange<br/>Routing Key: consultation.completed

    par EMR Service xử lý
        MQ->>EMR: Consume event
        EMR->>DB_E: INSERT INTO medical_records<br/>(recordType=EXAMINATION, source=DOCTOR_CREATED)
        DB_E-->>EMR: Record created
    and Appointment Service xử lý
        MQ->>AS: Consume event
        AS->>DB_A: UPDATE status = COMPLETED<br/>WHERE id = appointmentId
    end
```

---

## 2.3 Database Schema (SQL)

> [!IMPORTANT]
> **Nguyên tắc Database-per-Service**: Mỗi service có database riêng trên NeonDB. Không có Foreign Key xuyên service. Các service chỉ tham chiếu nhau qua UUID.

### Patient Service Database: `careflow_patient`

```sql
-- ============================================
-- DATABASE: careflow_patient
-- SERVICE:  Patient Service (:8082)
-- ============================================

CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TYPE gender_enum AS ENUM ('MALE', 'FEMALE', 'OTHER');

CREATE TABLE patients (
    id                UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id           UUID NOT NULL UNIQUE,           -- Tham chiếu logic → Identity Service (A)
    full_name         VARCHAR(100) NOT NULL,
    date_of_birth     DATE,
    gender            gender_enum,
    phone             VARCHAR(15),
    id_card_number    VARCHAR(20) UNIQUE,             -- CMND/CCCD
    insurance_number  VARCHAR(20),                    -- Số thẻ BHYT
    address           VARCHAR(500),
    avatar_url        VARCHAR(255),
    created_at        TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Index để tra cứu nhanh
CREATE INDEX idx_patients_user_id ON patients(user_id);
CREATE INDEX idx_patients_phone ON patients(phone);
CREATE INDEX idx_patients_id_card ON patients(id_card_number);
```

---

### Appointment Service Database: `careflow_appointment`

```sql
-- ============================================
-- DATABASE: careflow_appointment
-- SERVICE:  Appointment Service (:8083)
-- ============================================

CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TYPE appointment_status AS ENUM (
    'PENDING',       -- Vừa đặt, chờ xếp hàng
    'CONFIRMED',     -- Đã được cấp số thứ tự
    'CHECKED_IN',    -- Đã quét QR tại viện
    'IN_PROGRESS',   -- Đang khám
    'COMPLETED',     -- Khám xong
    'CANCELLED'      -- Đã hủy
);

CREATE TABLE departments (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name            VARCHAR(100) NOT NULL UNIQUE,
    description     VARCHAR(500),
    location        VARCHAR(100),                    -- "Tầng 2, Khu A"
    is_active       BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE clinic_rooms (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    department_id   UUID NOT NULL REFERENCES departments(id),
    room_code       VARCHAR(50) NOT NULL UNIQUE,
    display_name    VARCHAR(100) NOT NULL,
    is_active       BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE appointments (
    id                UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    patient_id        UUID NOT NULL,                   -- Tham chiếu logic → Patient Service
    department_id     UUID NOT NULL REFERENCES departments(id),
    room_id           UUID NOT NULL REFERENCES clinic_rooms(id),
    appointment_date  DATE NOT NULL,
    time_slot         VARCHAR(20) NOT NULL,            -- "08:00-08:30"
    status            appointment_status NOT NULL DEFAULT 'PENDING',
    notes             VARCHAR(500),
    created_at        TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMP NOT NULL DEFAULT NOW(),

    -- Ràng buộc: 1 bệnh nhân không được đặt 2 lịch cùng khoa cùng ngày
    CONSTRAINT uk_patient_dept_date UNIQUE (patient_id, department_id, appointment_date)
);

-- Index để tra cứu nhanh
CREATE INDEX idx_appointments_patient_id ON appointments(patient_id);
CREATE INDEX idx_appointments_date ON appointments(appointment_date);
CREATE INDEX idx_appointments_status ON appointments(status);
CREATE INDEX idx_clinic_rooms_department ON clinic_rooms(department_id);

-- Mô hình cho phép Department 1:N ClinicRoom. Seed MVP chỉ tạo đúng một phòng
-- active cho mỗi khoa; create appointment báo lỗi nếu truy vấn trả 0 hoặc >1.

-- ============================================
-- SEED DATA: Danh sách chuyên khoa mặc định
-- ============================================
INSERT INTO departments (id, name, description, location) VALUES
    (uuid_generate_v4(), 'Nội khoa',        'Khám và điều trị các bệnh nội khoa',          'Tầng 2, Khu A'),
    (uuid_generate_v4(), 'Ngoại khoa',      'Khám và điều trị các bệnh ngoại khoa',        'Tầng 3, Khu A'),
    (uuid_generate_v4(), 'Nhi khoa',        'Khám và điều trị bệnh trẻ em',                'Tầng 2, Khu B'),
    (uuid_generate_v4(), 'Sản phụ khoa',    'Khám và chăm sóc sức khỏe phụ nữ',           'Tầng 4, Khu B'),
    (uuid_generate_v4(), 'Tai Mũi Họng',    'Khám và điều trị bệnh tai mũi họng',         'Tầng 3, Khu A'),
    (uuid_generate_v4(), 'Da liễu',         'Khám và điều trị các bệnh về da',             'Tầng 5, Khu A'),
    (uuid_generate_v4(), 'Mắt',             'Khám và điều trị các bệnh về mắt',            'Tầng 5, Khu B'),
    (uuid_generate_v4(), 'Răng Hàm Mặt',   'Khám và điều trị răng miệng',                 'Tầng 6, Khu A');
```

---

### EMR Service Database: `careflow_emr`

```sql
-- ============================================
-- DATABASE: careflow_emr
-- SERVICE:  EMR Service (:8088)
-- ============================================

CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TYPE record_type AS ENUM (
    'EXAMINATION',          -- Kết quả khám bệnh (bác sỹ tạo)
    'LAB_RESULT',           -- Kết quả xét nghiệm
    'IMAGING',              -- Phim chụp (X-quang, CT, MRI)
    'DISCHARGE_SUMMARY',    -- Giấy ra viện
    'PRESCRIPTION_OLD',     -- Đơn thuốc cũ (bệnh nhân upload)
    'OTHER'                 -- Khác
);

CREATE TYPE record_source AS ENUM (
    'PATIENT_UPLOAD',       -- Bệnh nhân tự upload
    'DOCTOR_CREATED',       -- Bác sỹ tạo sau khi khám
    'SYSTEM_GENERATED'      -- Hệ thống tự tạo (từ event)
);

CREATE TABLE medical_records (
    id                UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    patient_id        UUID NOT NULL,                   -- Tham chiếu logic → Patient Service
    appointment_id    UUID,                            -- Tham chiếu logic → Appointment Service (nullable)
    record_type       record_type NOT NULL,
    title             VARCHAR(200) NOT NULL,
    description       TEXT,
    file_url          VARCHAR(500),
    record_date       DATE NOT NULL,
    source            record_source NOT NULL,
    created_at        TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Index để tra cứu nhanh
CREATE INDEX idx_records_patient_id ON medical_records(patient_id);
CREATE INDEX idx_records_appointment_id ON medical_records(appointment_id);
CREATE INDEX idx_records_type ON medical_records(record_type);
CREATE INDEX idx_records_date ON medical_records(record_date DESC);
```

---

## 2.4 API Contract

### Quy ước chung

| Quy ước | Chi tiết |
|---------|----------|
| **Base URL** | Tất cả API đều đi qua API Gateway: `http://localhost:8080/api/...` |
| **Authentication** | Mọi request đều cần header `Authorization: Bearer <JWT_TOKEN>` |
| **Response format** | Wrapper `ApiResponse<T>` chuẩn cho tất cả response |
| **Error format** | Wrapper `ApiResponse` với `success=false` và `message` mô tả lỗi |

**Response Wrapper chuẩn:**

```json
{
    "success": true,
    "message": "Thao tác thành công",
    "data": { ... },
    "timestamp": "2026-07-20T10:30:00"
}
```

**Error Response:**

```json
{
    "success": false,
    "message": "Không tìm thấy bệnh nhân",
    "data": null,
    "timestamp": "2026-07-20T10:30:00"
}
```

---

### 🔵 Patient Service APIs (:8082)

#### `POST /api/patients` — Tạo hồ sơ bệnh nhân

| Mục | Chi tiết |
|-----|----------|
| **UC** | UC-P01 |
| **Actor** | Bệnh nhân |
| **Auth** | JWT (role: PATIENT) |
| **Mô tả** | Tạo hồ sơ bệnh nhân sau khi đăng ký tài khoản. `userId` lấy từ JWT token. |

**Request Body:**

```json
{
    "fullName": "Nguyễn Văn A",
    "dateOfBirth": "1990-05-15",
    "gender": "MALE",
    "phone": "0901234567",
    "idCardNumber": "012345678901",
    "insuranceNumber": "HS4012345678",
    "address": "123 Nguyễn Huệ, Q.1, TP.HCM"
}
```

**Response — 201 Created:**

```json
{
    "success": true,
    "message": "Tạo hồ sơ bệnh nhân thành công",
    "data": {
        "id": "a1b2c3d4-...",
        "userId": "x1y2z3-...",
        "fullName": "Nguyễn Văn A",
        "dateOfBirth": "1990-05-15",
        "gender": "MALE",
        "phone": "0901234567",
        "idCardNumber": "012345678901",
        "insuranceNumber": "HS4012345678",
        "address": "123 Nguyễn Huệ, Q.1, TP.HCM",
        "avatarUrl": null,
        "createdAt": "2026-07-15T10:30:00"
    }
}
```

**Errors:**
- `400` — Thiếu `fullName` hoặc dữ liệu không hợp lệ
- `409` — `userId` đã có hồ sơ (Conflict)

---

#### `GET /api/patients/me` — Xem hồ sơ cá nhân

| Mục | Chi tiết |
|-----|----------|
| **UC** | UC-P02 |
| **Actor** | Bệnh nhân |
| **Auth** | JWT (role: PATIENT) |
| **Mô tả** | Lấy hồ sơ của chính bệnh nhân đang đăng nhập. `userId` lấy từ JWT token. |

**Response — 200 OK:** Giống response của POST, trả về object `Patient`.

**Errors:**
- `404` — Chưa có hồ sơ → Mobile chuyển tới form tạo hồ sơ (UC-P01)

---

#### `PUT /api/patients/{id}` — Cập nhật hồ sơ

| Mục | Chi tiết |
|-----|----------|
| **UC** | UC-P03 |
| **Actor** | Bệnh nhân |
| **Auth** | JWT (role: PATIENT). Chỉ cho phép cập nhật hồ sơ của chính mình. |

**Request Body:** (chỉ gửi các trường muốn cập nhật)

```json
{
    "phone": "0909876543",
    "address": "456 Lê Lợi, Q.3, TP.HCM",
    "insuranceNumber": "HS4099999999"
}
```

**Response — 200 OK:** Trả về object `Patient` sau khi cập nhật.

**Errors:**
- `403` — Cố cập nhật hồ sơ người khác (Forbidden)
- `404` — Không tìm thấy hồ sơ

---

#### `GET /api/patients/{id}` — Tra cứu thông tin bệnh nhân

| Mục | Chi tiết |
|-----|----------|
| **UC** | UC-P04 |
| **Actor** | Bác sỹ |
| **Auth** | JWT (role: DOCTOR) |
| **Mô tả** | Bác sỹ tra cứu thông tin bệnh nhân. Chỉ role DOCTOR mới được gọi với `{id}` bất kỳ. |

**Response — 200 OK:** Trả về object `Patient`.

**Errors:**
- `403` — Không phải DOCTOR và cố tra cứu người khác
- `404` — Không tìm thấy bệnh nhân

---

### 🟢 Appointment Service APIs (:8083)

#### `GET /api/appointments/departments` — Danh sách chuyên khoa

| Mục | Chi tiết |
|-----|----------|
| **UC** | UC-A05 |
| **Actor** | Bệnh nhân |
| **Auth** | JWT (bất kỳ role) |

**Response — 200 OK:**

```json
{
    "success": true,
    "data": [
        {
            "id": "dept-uuid-1",
            "name": "Nội khoa",
            "description": "Khám và điều trị các bệnh nội khoa",
            "location": "Tầng 2, Khu A",
            "isActive": true
        },
        {
            "id": "dept-uuid-2",
            "name": "Ngoại khoa",
            "description": "Khám và điều trị các bệnh ngoại khoa",
            "location": "Tầng 3, Khu A",
            "isActive": true
        }
    ]
}
```

---

#### `POST /api/appointments` — Đăng ký khám bệnh

| Mục | Chi tiết |
|-----|----------|
| **UC** | UC-A01 |
| **Actor** | Bệnh nhân |
| **Auth** | JWT (role: PATIENT) |
| **Side effect** | Publish event `AppointmentCreated` lên RabbitMQ |

**Request Body:**

```json
{
    "patientId": "patient-uuid",
    "departmentId": "dept-uuid-1",
    "appointmentDate": "2026-07-20",
    "timeSlot": "08:00-08:30",
    "notes": "Đau bụng kéo dài 3 ngày"
}
```

**Response — 201 Created:**

```json
{
    "success": true,
    "message": "Đăng ký khám bệnh thành công",
    "data": {
        "id": "appt-uuid",
        "patientId": "patient-uuid",
        "department": {
            "id": "dept-uuid-1",
            "name": "Nội khoa",
            "location": "Tầng 2, Khu A"
        },
        "appointmentDate": "2026-07-20",
        "timeSlot": "08:00-08:30",
        "status": "PENDING",
        "notes": "Đau bụng kéo dài 3 ngày",
        "createdAt": "2026-07-15T10:30:00"
    }
}
```

**Errors:**
- `400` — Thiếu trường bắt buộc
- `409` — Đã có lịch hẹn cùng khoa cùng ngày (Conflict)
- `422` — Ngày khám nằm trong quá khứ

---

#### `GET /api/appointments/patient/{patientId}` — Danh sách lịch hẹn

| Mục | Chi tiết |
|-----|----------|
| **UC** | UC-A02 |
| **Actor** | Bệnh nhân |
| **Auth** | JWT (role: PATIENT). Chỉ xem lịch của chính mình. |
| **Query params** | `?status=PENDING,CONFIRMED` (lọc theo trạng thái, tùy chọn) |

**Response — 200 OK:**

```json
{
    "success": true,
    "data": [
        {
            "id": "appt-uuid-1",
            "department": {
                "id": "dept-uuid-1",
                "name": "Nội khoa"
            },
            "appointmentDate": "2026-07-20",
            "timeSlot": "08:00-08:30",
            "status": "CONFIRMED",
            "createdAt": "2026-07-15T10:30:00"
        },
        {
            "id": "appt-uuid-2",
            "department": {
                "id": "dept-uuid-3",
                "name": "Nhi khoa"
            },
            "appointmentDate": "2026-07-22",
            "timeSlot": "14:00-14:30",
            "status": "PENDING",
            "createdAt": "2026-07-16T09:00:00"
        }
    ]
}
```

---

#### `GET /api/appointments/{id}` — Chi tiết lịch hẹn

| Mục | Chi tiết |
|-----|----------|
| **UC** | UC-A03 |
| **Actor** | Bệnh nhân |
| **Auth** | JWT. Chỉ chủ sở hữu mới được xem. |

**Response — 200 OK:** Giống 1 object trong danh sách, nhưng bao gồm thêm `notes`.

---

#### `PUT /api/appointments/{id}/cancel` — Hủy lịch hẹn

| Mục | Chi tiết |
|-----|----------|
| **UC** | UC-A04 |
| **Actor** | Bệnh nhân |
| **Auth** | JWT (role: PATIENT). Chỉ chủ sở hữu mới được hủy. |
| **Side effect** | Publish event `AppointmentCancelled` lên RabbitMQ |

**Response — 200 OK:**

```json
{
    "success": true,
    "message": "Hủy lịch hẹn thành công",
    "data": {
        "id": "appt-uuid",
        "status": "CANCELLED"
    }
}
```

**Errors:**
- `400` — Trạng thái không cho phép hủy (CHECKED_IN, IN_PROGRESS, COMPLETED)
- `403` — Không phải chủ sở hữu
- `404` — Không tìm thấy lịch hẹn

---

### 🟠 EMR Service APIs (:8088)

#### `POST /api/emr/records` — Upload hồ sơ bệnh án

| Mục | Chi tiết |
|-----|----------|
| **UC** | UC-E01 |
| **Actor** | Bệnh nhân |
| **Auth** | JWT (role: PATIENT) |
| **Content-Type** | `multipart/form-data` |

**Request (multipart form):**

| Field | Type | Bắt buộc | Mô tả |
|-------|------|----------|-------|
| `patientId` | UUID | ✅ | ID bệnh nhân |
| `recordType` | String | ✅ | LAB_RESULT, IMAGING, DISCHARGE_SUMMARY, PRESCRIPTION_OLD, OTHER |
| `title` | String | ✅ | Tiêu đề hồ sơ |
| `description` | String | ❌ | Mô tả / ghi chú |
| `recordDate` | Date | ✅ | Ngày khám / xét nghiệm |
| `file` | File | ❌ | File đính kèm (JPG, PNG, PDF, ≤10MB) |

**Response — 201 Created:**

```json
{
    "success": true,
    "message": "Upload hồ sơ bệnh án thành công",
    "data": {
        "id": "record-uuid",
        "patientId": "patient-uuid",
        "recordType": "LAB_RESULT",
        "title": "Kết quả xét nghiệm máu 15/06/2026",
        "description": "Xét nghiệm tại BV Đại học Y Dược",
        "fileUrl": "/uploads/emr/record-uuid/blood_test.pdf",
        "recordDate": "2026-06-15",
        "source": "PATIENT_UPLOAD",
        "createdAt": "2026-07-15T10:30:00"
    }
}
```

**Errors:**
- `400` — Thiếu trường bắt buộc
- `413` — File quá lớn (>10MB)
- `415` — Định dạng file không hỗ trợ

---

#### `GET /api/emr/records/patient/{patientId}` — Danh sách hồ sơ bệnh án

| Mục | Chi tiết |
|-----|----------|
| **UC** | UC-E02 (Bệnh nhân), UC-E04 (Bác sỹ) |
| **Actor** | Bệnh nhân hoặc Bác sỹ |
| **Auth** | JWT. PATIENT chỉ xem của mình, DOCTOR xem bất kỳ. |
| **Query params** | `?recordType=LAB_RESULT` (lọc theo loại, tùy chọn) |

**Response — 200 OK:**

```json
{
    "success": true,
    "data": [
        {
            "id": "record-uuid-1",
            "recordType": "EXAMINATION",
            "title": "Khám Nội khoa — BS. Nguyễn Văn X",
            "recordDate": "2026-07-10",
            "source": "DOCTOR_CREATED",
            "fileUrl": null
        },
        {
            "id": "record-uuid-2",
            "recordType": "LAB_RESULT",
            "title": "Kết quả xét nghiệm máu",
            "recordDate": "2026-06-15",
            "source": "PATIENT_UPLOAD",
            "fileUrl": "/uploads/emr/record-uuid-2/blood_test.pdf"
        }
    ]
}
```

---

#### `GET /api/emr/records/{id}` — Chi tiết hồ sơ bệnh án

| Mục | Chi tiết |
|-----|----------|
| **UC** | UC-E03 |
| **Actor** | Bệnh nhân hoặc Bác sỹ |
| **Auth** | JWT. PATIENT chỉ xem của mình, DOCTOR xem bất kỳ. |

**Response — 200 OK:** Trả về đầy đủ tất cả trường của `MedicalRecord`, bao gồm `description` và `fileUrl`.

---

## 2.5 Event Design (RabbitMQ)

### Topology tổng quan

```mermaid
flowchart LR
    subgraph "Appointment Service (B)"
        PUB1["Publish"]
    end

    subgraph "RabbitMQ"
        EX1["appointment.exchange<br/>(topic)"]
        Q1["queue.appointment.created"]
        Q2["queue.appointment.cancelled"]
        EX2["consultation.exchange<br/>(topic)"]
        Q3["emr.consultation.completed"]
        Q4["appointment.consultation.completed"]
        EX3["queue.exchange<br/>(topic)"]
        Q5["appointment.queue.assigned"]
    end

    subgraph "Queue Service (A)"
        SUB1["Consume"]
    end

    subgraph "Doctor Consultation (C)"
        PUB2["Publish"]
    end

    subgraph "EMR Service (B)"
        SUB2["Consume"]
    end

    PUB1 --> EX1
    EX1 -->|"appointment.created"| Q1 --> SUB1
    EX1 -->|"appointment.cancelled"| Q2 --> SUB1

    PUB2 --> EX2
    EX2 -->|"consultation.completed"| Q3 --> SUB2
    EX2 -->|"consultation.completed"| Q4 -->|"Cập nhật status"| PUB1

    EX3 -->|"queue.number.assigned"| Q5 -->|"Cập nhật status CONFIRMED"| PUB1
```

---

### Events do Người B PUBLISH (gửi đi)

#### Event 1: `AppointmentCreated`

| Mục | Chi tiết |
|-----|----------|
| **Exchange** | `appointment.exchange` (type: `topic`) |
| **Routing Key** | `appointment.created` |
| **Ai nhận** | Queue Service (A) |
| **Khi nào** | Bệnh nhân đặt lịch khám thành công (UC-A01) |

```json
{
    "eventType": "APPOINTMENT_CREATED",
    "appointmentId": "appt-uuid",
    "patientId": "patient-uuid",
    "patientName": "Nguyễn Văn A",
    "departmentId": "dept-uuid-1",
    "departmentName": "Nội khoa",
    "appointmentDate": "2026-07-20",
    "timeSlot": "08:00-08:30",
    "priority": "APPOINTMENT",
    "timestamp": "2026-07-15T10:30:00"
}
```

#### Event 2: `AppointmentCancelled`

| Mục | Chi tiết |
|-----|----------|
| **Exchange** | `appointment.exchange` (type: `topic`) |
| **Routing Key** | `appointment.cancelled` |
| **Ai nhận** | Queue Service (A) |
| **Khi nào** | Bệnh nhân hủy lịch hẹn (UC-A04) |

```json
{
    "eventType": "APPOINTMENT_CANCELLED",
    "appointmentId": "appt-uuid",
    "patientId": "patient-uuid",
    "departmentName": "Nội khoa",
    "appointmentDate": "2026-07-20",
    "reason": "Bệnh nhân tự hủy",
    "timestamp": "2026-07-15T11:00:00"
}
```

---

### Events do Người B SUBSCRIBE (nhận về)

#### Event 3: `QueueNumberAssigned` (từ Người A)

| Mục | Chi tiết |
|-----|----------|
| **Exchange** | `queue.exchange` |
| **Routing Key** | `queue.number.assigned` |
| **Ai gửi** | Queue Service (A) |
| **B làm gì** | Cập nhật `appointment.status` = `CONFIRMED` |

```json
{
    "eventType": "QUEUE_NUMBER_ASSIGNED",
    "appointmentId": "appt-uuid",
    "patientId": "patient-uuid",
    "queueNumber": "A-042",
    "estimatedWaitMinutes": 25,
    "timestamp": "2026-07-15T10:31:00"
}
```

#### Event 4: `ConsultationCompleted` (từ Người C)

| Mục | Chi tiết |
|-----|----------|
| **Exchange** | `consultation.exchange` |
| **Routing Key** | `consultation.completed` |
| **Ai gửi** | Doctor Consultation Service (C) |
| **B làm gì** | (1) EMR Service tạo `MedicalRecord` mới. (2) Appointment Service cập nhật `status` = `COMPLETED`. |

```json
{
    "eventType": "CONSULTATION_COMPLETED",
    "consultationId": "consult-uuid",
    "appointmentId": "appt-uuid",
    "patientId": "patient-uuid",
    "doctorId": "doctor-uuid",
    "doctorName": "BS. Nguyễn Văn X",
    "departmentName": "Nội khoa",
    "diagnosis": "Viêm họng cấp",
    "notes": "Bệnh nhân cần nghỉ ngơi, uống nhiều nước",
    "consultationDate": "2026-07-20",
    "timestamp": "2026-07-20T09:15:00"
}
```

---

## 2.6 Tổng hợp kết nối giữa các Service

### Service của B cần gọi ra ngoài

| Từ Service (B) | Gọi đến Service | Phương thức | Mục đích |
|----------------|-----------------|-------------|----------|
| Patient Service | Identity Service (A) | Đọc JWT token | Lấy `userId` và `role` |
| Appointment Service | — | RabbitMQ (publish) | Gửi event tạo/hủy lịch |

### Service bên ngoài gọi vào B

| Từ Service | Gọi đến Service (B) | Phương thức | Mục đích |
|------------|---------------------|-------------|----------|
| Doctor Consultation (C) | Patient Service | REST API | Tra cứu thông tin bệnh nhân |
| Doctor Consultation (C) | EMR Service | REST API | Tra cứu hồ sơ bệnh án |
| Doctor Consultation (C) | EMR Service | RabbitMQ (event) | Ghi kết quả khám |
| Queue Service (A) | Appointment Service | RabbitMQ (event) | Cập nhật trạng thái lịch hẹn |

---

## Checklist trước khi code

- [ ] Tạo 3 database trên NeonDB: `careflow_patient`, `careflow_appointment`, `careflow_emr`
- [ ] Chạy SQL schema cho cả 3 database
- [ ] Thống nhất với Người A: cấu trúc JWT Token, tên Exchange/Queue/RoutingKey
- [ ] Thống nhất với Người C: payload event `ConsultationCompleted`, API response format
- [ ] Tạo 3 Spring Boot projects: `patient-service`, `appointment-service`, `emr-service`
- [ ] Cấu hình `application.yml` kết nối NeonDB + RabbitMQ
