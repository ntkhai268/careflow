# Yêu cầu Dự án (Project Requirements)

**Nhóm:** C35
- **Nguyễn Đức Vĩ** - N22DCCN096 (D22CQCNHT01-N)
- **Nguyễn Thanh Khai** - N21DCCN041 (D22CQCNPM01-N)
- **Phạm Đăng Khôi** - N22DCCN044 (D22CQCNPM01-N)

## Tên đề tài
**Xây dựng hệ thống phần mềm trợ giúp khám chữa bệnh tại bệnh viện công theo kiến trúc Microservices**

---

## 1. Nội dung lý thuyết
- Tìm hiểu quy trình khám điều trị tại bệnh viện công.
- Tìm hiểu các giải pháp xử lý hàng đợi tại bệnh viện (hàng đợi đa độ ưu tiên, hàng đợi xen kẽ động $N:M$, ước tính thời gian khám bác sỹ-bệnh,...).
- Nghiên cứu kiến trúc Microservices, các pattern liên quan (API Gateway, Inter-service communication, Distributed Data) và so sánh lựa chọn (Spring Boot / Node.js / Go, Docker, RabbitMQ/Kafka...).

---

## 2. Nội dung thực hành

### a) Phân tích & Thiết kế
- Phân tích yêu cầu nghiệp vụ, đặc tả use case, xác định các service độc lập và thiết kế kiến trúc Microservices.

### b) Xây dựng các tương tác trên hệ thống
- **Phân hệ Bệnh nhân (Mobile app):**
  - Đăng ký khám bệnh.
  - Upload hồ sơ bệnh án (lịch sử khám điều trị và các xét nghiệm).
  - Quét mã QR Code tại bệnh viện để xác nhận.
  - Theo dõi lượt khám.
  - Nhận thông báo đến lượt khám.
  - Nhận toa thuốc / chỉ định / lịch tái khám của bác sĩ.
- **Phân hệ Bác sỹ (Web App):**
  - Tra cứu hồ sơ bệnh án, chẩn bệnh.
  - Ghi toa thuốc / chỉ định xét nghiệm.
  - Hẹn tái khám.
  - Tích hợp ChatBot gợi ý phác đồ điều trị cho bệnh nhân.
- **Phân hệ Hệ thống (Core):**
  - Đăng ký thành viên, quản lý định danh (eKYC/Auth).
  - Cấu hình tự động quản lý hàng đợi để cung cấp số thứ tự vào khám bệnh cho bệnh nhân (có/không có hẹn trước).
  - Cập nhật thứ tự vào khám nếu bệnh nhân lỡ lượt khám.

### c) Các service chính của hệ thống
1. API Gateway Service
2. Identity & eKYC Service
3. Patient Service
4. Electronic Medical Record Service
5. Appointment Service
6. Queue Management Service
7. Analytics Service
8. Doctor Consultation Service
9. Prescription Service
10. Laboratory Order Service
11. Notification Service
12. AI Clinical Assistant Service
