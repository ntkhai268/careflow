# Báo cáo Tiến độ Phát triển Phân hệ Prescription (Kê đơn thuốc - UC8)

Tài liệu này tóm tắt toàn bộ các tính năng, thay đổi cấu trúc, API Endpoints, cấu hình CORS và các luồng nghiệp vụ đã được triển khai hoàn tất cho phân hệ **Kê đơn thuốc điện tử (Prescription Service)** trên nhánh `feature/vi/prescription-service`.

---

## 1. Các hạng mục công việc đã hoàn thành

### 1.1. Backend (Spring Boot Java)
* **Cấu hình Event Broker (RabbitMQ):**
  * Tạo lớp `RabbitMQConfig.java` định nghĩa Exchange `prescription.exchange`, Queue `prescription.created.queue` và liên kết bằng Routing Key `prescription.created`.
  * Tích hợp việc tự động publish event DTO sang RabbitMQ ngay khi bác sĩ nhấn nút ký xác nhận đơn thuốc thành công (từ trạng thái `DRAFT` chuyển sang `CONFIRMED`).
* **Giao tiếp chéo giữa các dịch vụ (Inter-service Call):**
  * Tạo lớp `ConsultationClient.java` sử dụng `RestTemplate` gọi chéo sang `consultation-service` để lấy trạng thái lâm sàng của phiên khám.
  * Ngăn chặn các thao tác tạo/sửa đổi/xác nhận đơn thuốc nếu phiên khám đã bị đóng (`COMPLETED` hoặc `CANCELLED`).
* **Ràng buộc Danh mục thuốc (Medicine Catalog Validation):**
  * Kiểm tra mã thuốc (`medicineCode`) gửi lên từ client bắt buộc phải nằm trong danh mục thuốc mẫu của bệnh viện (`drugs_dictionary`).

### 1.2. Frontend (Next.js React)
* **API Client Layer**: `prescription-api.ts` kết nối API Kê đơn, danh mục thuốc.
* **Màn hình Kê đơn Lâm sàng**: Thêm/xóa thuốc trực quan, tùy chỉnh liều lượng, tần suất uống, số ngày, số lượng.
* **Trang Lịch sử đơn thuốc**: Hiển thị danh sách các đơn thuốc đã kê trong hệ thống từ Database.

---

## 2. API Endpoints của phân hệ Prescription

| HTTP Method | URL Endpoint | Chức năng | Trạng thái bảo mật |
| :--- | :--- | :--- | :--- |
| **POST** | `/api/prescriptions` | Tạo đơn thuốc nháp mới (`DRAFT`) | Yêu cầu JWT |
| **GET** | `/api/prescriptions/{id}` | Lấy chi tiết đơn thuốc theo ID đơn | Yêu cầu JWT |
| **PUT** | `/api/prescriptions/{id}` | Cập nhật đơn thuốc (chỉ áp dụng khi còn `DRAFT`) | Yêu cầu JWT |
| **PUT** | `/api/prescriptions/{id}/confirm` | Ký xác nhận đơn thuốc (`DRAFT` -> `CONFIRMED`) và đẩy sự kiện RabbitMQ | Yêu cầu JWT |
| **GET** | `/api/prescriptions/consultation/{consultationId}` | Tìm danh sách đơn thuốc của một phiên khám | Yêu cầu JWT |
| **GET** | `/api/prescriptions/patient/{patientId}` | Xem lịch sử đơn thuốc của một bệnh nhân | Yêu cầu JWT |
| **GET** | `/api/prescriptions/medicines` | Lấy danh mục thuốc từ `drugs_dictionary` | Yêu cầu JWT |
