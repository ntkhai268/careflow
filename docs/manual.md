# Hướng dẫn Khởi chạy và Vận hành CareFlow (Local Manual)

Tài liệu này hướng dẫn chi tiết cách khởi động, cấu hình tối ưu tài nguyên (RAM, CPU) và vận hành các microservices trong hệ sinh thái **CareFlow** trên môi trường phát triển cục bộ (Local Development).

---

## 1. Yêu cầu Môi trường

- **JDK**: Java 21 LTS (OpenJDK hoặc Oracle JDK)
- **Build Tool**: Maven 3.9+
- **Node.js**: Node 18+ hoặc 20+ và `npm`
- **Database & Message Broker**:
  - PostgreSQL 16 (Port `5432`)
  - RabbitMQ (Port `5672`, Management UI: `15672`)
- **Docker** (tùy chọn để chạy hạ tầng qua `docker-compose.infra.yml`).

---

## 2. Khởi chạy Hạ tầng Nền tảng (Infrastructure)

Trước khi khởi động các microservices, đảm bảo PostgreSQL và RabbitMQ đã sẵn sàng:

```bash
# Chạy Postgres và RabbitMQ qua Docker
docker compose -f docker-compose.infra.yml up -d
```

---

## 3. Khởi chạy Microservices bằng Script Tối ưu (`start-local.sh`)

Để tránh hiện tượng ngốn RAM và CPU gây treo cứng màn hình (freeze/hang), script [start-local.sh](../start-local.sh) đã được tích hợp sẵn:
- **Giới hạn RAM cho JVM**: Mỗi service chỉ cấp phát tối đa `-Xmx256m` (thay vì 1-2GB mặc định).
- **Tăng tốc khởi động**: Bật cờ `-XX:+TieredCompilation -XX:TieredStopAtLevel=1`.
- **Khởi động giãn cách (Staggered Startup)**: Giãn cách thời gian giữa các service để tránh quá tải CPU lúc biên dịch ban đầu.
- **Hỗ trợ chạy theo cụm phân hệ**: Chỉ bật các service thực sự cần thiết cho công việc hiện tại.

### Cách 1: Chạy có Menu tương tác
Chỉ cần thực hiện lệnh sau trong thư mục gốc dự án:
```bash
./start-local.sh
```
Hệ thống sẽ hiển thị menu để bạn chọn (tự động chọn chế độ `ALL` sau 5 giây nếu không nhập):
```text
Chọn cụm phân hệ muốn khởi chạy để tiết kiệm RAM & CPU:
  1) ALL          - Toàn bộ 13 services + Frontend (Dành cho máy >= 16GB RAM rảnh)
  2) PRESCRIPTION - Kê đơn thuốc (Gateway, Identity, Patient, Consultation, Prescription, Directory, Frontend)
  3) APPOINTMENT  - Đặt khám & Hàng đợi (Gateway, Identity, Patient, Appointment, Queue, Directory, AI, Frontend)
  4) EMR & LAB    - Bệnh án & Xét nghiệm (Gateway, Identity, Patient, Consultation, EMR, Lab, Directory, Frontend)
  5) CORE ONLY    - Cốt lõi (Eureka, Gateway, Identity, Patient, Frontend)
```

### Cách 2: Truyền trực tiếp tên cụm phân hệ (Khuyên dùng)
Bạn có thể chỉ định trực tiếp mode khi gọi lệnh:

| Lệnh | Phân hệ / Các Service khởi chạy | Mục đích sử dụng |
|---|---|---|
| `./start-local.sh prescription` | `eureka`, `gateway`, `identity`, `patient`, `directory`, `consultation`, `prescription`, `doctor-web` | Lập trình & kiểm thử tính năng Kê đơn thuốc |
| `./start-local.sh appointment` | `eureka`, `gateway`, `identity`, `patient`, `directory`, `queue`, `appointment`, `ai`, `doctor-web` | Lập trình & kiểm thử Đặt lịch khám, Hàng đợi & Trợ lý AI |
| `./start-local.sh emr-lab` | `eureka`, `gateway`, `identity`, `patient`, `directory`, `consultation`, `emr`, `lab`, `doctor-web` | Lập trình & kiểm thử Bệnh án điện tử và Chỉ định Xét nghiệm |
| `./start-local.sh core` | `eureka`, `gateway`, `identity`, `patient`, `doctor-web` | Kiểm thử xác thực, phân quyền và thông tin bệnh nhân |
| `./start-local.sh all` | Toàn bộ 13 Spring Boot microservices + Doctor Web Frontend | Kiểm thử toàn hệ sinh thái E2E |

---

## 4. Danh mục Cổng (Ports) & Đường dẫn Dịch vụ

Khi các service được khởi chạy, bạn có thể truy cập các đường dẫn tương ứng:

| Dịch vụ / Ứng dụng | Cổng (Port) | URL kiểm tra |
|---|---|---|
| **Eureka Server** | `8761` | [http://localhost:8761](http://localhost:8761) |
| **API Gateway** | `8080` | [http://localhost:8080](http://localhost:8080) |
| **Doctor Web Frontend** | `3000` | [http://localhost:3000](http://localhost:3000) |
| Identity Service | `8081` | [http://localhost:8081/actuator/health](http://localhost:8081/actuator/health) |
| Patient Service | `8082` | [http://localhost:8082/actuator/health](http://localhost:8082/actuator/health) |
| Appointment Service | `8083` | [http://localhost:8083/actuator/health](http://localhost:8083/actuator/health) |
| Queue Service | `8084` | [http://localhost:8084/actuator/health](http://localhost:8084/actuator/health) |
| Notification Service | `8085` | [http://localhost:8085/actuator/health](http://localhost:8085/actuator/health) |
| Consultation Service | `8086` | [http://localhost:8086/actuator/health](http://localhost:8086/actuator/health) |
| Prescription Service | `8087` | [http://localhost:8087/actuator/health](http://localhost:8087/actuator/health) |
| EMR Service | `8088` | [http://localhost:8088/actuator/health](http://localhost:8088/actuator/health) |
| Lab Service | `8089` | [http://localhost:8089/actuator/health](http://localhost:8089/actuator/health) |
| Hospital Directory Service | `8090` | [http://localhost:8090/actuator/health](http://localhost:8090/actuator/health) |
| AI Service (Gemini API) | `8091` | [http://localhost:8091/actuator/health](http://localhost:8091/actuator/health) |

---

## 5. Xem Logs và Tắt Dịch vụ

- **Xem Log trong thời gian thực**: Script `start-local.sh` sẽ tự động `tail -f` các log đang hoạt động trên màn hình terminal. Các file log chi tiết được lưu trong thư mục `logs/` (ví dụ: `logs/logs_identity-service.log`).
- **Dừng toàn bộ hệ thống**: Nhấn `Ctrl + C` trên cửa sổ terminal đang chạy script. Bộ dọn dẹp (`cleanup trap`) sẽ tự động tắt toàn bộ tiến trình Java và Node.js chạy ngầm một cách an toàn.
