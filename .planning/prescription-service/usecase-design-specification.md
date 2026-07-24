# BÁO CÁO PHÂN TÍCH THIẾT KẾ USE CASE: KÊ ĐƠN THUỐC ĐIỆN TỬ (UC8)

* **Tên dự án:** Hệ thống Quản lý Phòng khám CareFlow
* **Phân hệ:** Prescription Service (Kê đơn thuốc)
* **Người thực hiện:** Developer (Nhánh `feature/vi/prescription-service`)

---

## 1. TỔNG QUAN VÀ PHẠM VI (OVERVIEW & SCOPE)

Phân hệ **Prescription Service** đảm nhận nhiệm vụ xử lý toàn bộ vòng đời của đơn thuốc điện tử trong hệ thống CareFlow. Dịch vụ được phát triển theo kiến trúc **Microservices**, độc lập về cơ sở dữ liệu (`careflow_prescription` trên PostgreSQL Remote), giao tiếp đồng bộ (REST API via RestTemplate) và bất đồng bộ (Event-Driven via RabbitMQ).

### Sơ đồ Cơ sở Dữ liệu (DBML Schema)
Chi tiết thiết kế DBML của Prescription Service nằm tại: [.planning/prescription-service/prescription-db.dbml](file:///home/levi/Desktop/careflow/.planning/prescription-service/prescription-db.dbml).

### Mạng lưới chức năng chính:
1. Tra cứu danh mục thuốc bệnh viện (`drugs_dictionary`).
2. Lập đơn thuốc nháp (`DRAFT`) gắn liền với phiên khám lâm sàng (`consultation_id`).
3. Chỉnh sửa và cập nhật chi tiết các loại thuốc trong đơn.
4. Ký và xác nhận đơn thuốc (`CONFIRMED`) -> Khóa đơn thuốc -> Phát sự kiện RabbitMQ tới Quầy thuốc (Pharmacy).
5. Tự động liên thông đặt lịch tái khám với `appointment-service` nếu có cấu hình ngày tái khám (`followUpDate`).
6. Kiểm tra khóa dữ liệu chặt chẽ dựa trên trạng thái của phiên khám từ `consultation-service`.
