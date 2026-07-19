# CareFlow 🏥

> Hệ thống phần mềm trợ giúp khám chữa bệnh tại bệnh viện công theo kiến trúc Microservices

## Branch hiện tại: Queue Management Service

Branch: `feature/dangkhoii/queue-algorithm` · Port: `8084` · Database: `careflow_queue`

### Chức năng

- Nhận `AppointmentCreated`/`AppointmentCancelled` từ RabbitMQ theo cơ chế idempotent inbox.
- Cấp số tăng dần theo khoa và ngày, hỗ trợ tiếp nhận thủ công P0/P1/P3.
- Sinh và xác thực QR token stateless để bệnh nhân check-in.
- Gọi lượt theo bốn mức P0–P3 và thuật toán xen kẽ N:M động.
- Luân phiên `APPOINTMENT`/`WALK_IN`, fallback khi một nhóm trống và chống starvation.
- Xử lý `MISSED`, requeue front/back/manual, bắt đầu và hoàn thành lượt khám.
- Tính vị trí hiệu dụng, thời gian chờ và moving average thời gian khám.
- Dùng row locking, optimistic version, transactional outbox và RabbitMQ publisher confirm.

### API chính

| Method | Endpoint | Quyền |
|---|---|---|
| `GET` | `/api/queues/me/status` | PATIENT |
| `POST` | `/api/queues/entries` | ADMIN |
| `GET` | `/api/queues/appointments/{id}/qr` | PATIENT |
| `POST` | `/api/queues/check-in` | PATIENT |
| `GET` | `/api/queues/departments/{id}/dashboard` | DOCTOR, ADMIN |
| `POST` | `/api/queues/departments/{id}/next` | DOCTOR, ADMIN |
| `POST` | `/api/queues/entries/{id}/miss` | DOCTOR, ADMIN |
| `POST` | `/api/queues/entries/{id}/requeue` | DOCTOR, ADMIN |
| `POST` | `/api/queues/entries/{id}/start` | DOCTOR, ADMIN |
| `POST` | `/api/queues/entries/{id}/complete` | DOCTOR, ADMIN |
| `GET`, `PUT` | `/api/queues/configs/{departmentId}` | ADMIN |

### Yêu cầu môi trường

- JDK 21 và Maven 3.9+.
- PostgreSQL 16, RabbitMQ và API Gateway; Eureka được khuyến nghị khi chạy đủ hệ thống.
- `JWT_SECRET` phải giống Identity/API Gateway; `QR_SECRET` nên là secret riêng ở production.

### Cài đặt và khởi chạy

```bash
git clone <repository-url>
cd careflow
git switch feature/dangkhoii/queue-algorithm

docker compose -f docker-compose.infra.yml up -d postgres rabbitmq
export JWT_SECRET='<same-secret-as-identity-and-gateway>'
export QR_SECRET="$(openssl rand -base64 48)"

mvn -pl careflow-queue-service -am clean package
java -jar careflow-queue-service/target/careflow-queue-service-*.jar
```

Service chạy ở `http://localhost:8084`. Flyway tự tạo/cập nhật schema khi kết nối
database thành công. Các biến cấu hình chính:

| Biến | Mặc định | Ý nghĩa |
|---|---|---|
| `QUEUE_DB_URL` | `jdbc:postgresql://localhost:5432/careflow_queue` | JDBC URL |
| `QUEUE_DB_USERNAME` / `QUEUE_DB_PASSWORD` | `careflow` / `careflow` | Tài khoản DB |
| `RABBITMQ_HOST` / `RABBITMQ_PORT` | `localhost` / `5672` | Message broker |
| `EUREKA_URL` | `http://localhost:8761/eureka/` | Eureka Server |
| `QUEUE_BUSINESS_ZONE` | `Asia/Ho_Chi_Minh` | Múi giờ cấp số theo ngày |
| `QR_EXPIRATION_MINUTES` | `30` | Thời hạn QR check-in |

### Hướng dẫn sử dụng

Các ví dụ dưới đây gọi qua Gateway tại port `8080`. Thay UUID và JWT bằng dữ liệu
thực tế; token phải có đúng role.

1. Admin tạo cấu hình cho khoa:

```bash
curl -X PUT http://localhost:8080/api/queues/configs/<DEPARTMENT_ID> \
  -H 'Authorization: Bearer <ADMIN_JWT>' \
  -H 'Content-Type: application/json' \
  -d '{
    "departmentName": "Khoa Nội",
    "queuePrefix": "NOI",
    "roomCode": "P101",
    "priorityRatioN": 2,
    "normalRatioM": 3,
    "avgConsultationMinutes": 10,
    "nearTurnThreshold": 3,
    "missedPolicy": "REQUEUE_BACK",
    "active": true
  }'
```

2. Admin tiếp nhận thủ công một lượt khám. `priorityLevel` nhận
`EMERGENCY`, `PRIORITY` hoặc `WALK_IN`:

```bash
curl -X POST http://localhost:8080/api/queues/entries \
  -H 'Authorization: Bearer <ADMIN_JWT>' \
  -H 'Content-Type: application/json' \
  -d '{
    "patientId": "<PATIENT_ID>",
    "userId": "<USER_ID>",
    "departmentId": "<DEPARTMENT_ID>",
    "priorityLevel": "WALK_IN"
  }'
```

3. Bác sĩ/Admin xem dashboard và gọi lượt tiếp theo:

```bash
curl http://localhost:8080/api/queues/departments/<DEPARTMENT_ID>/dashboard \
  -H 'Authorization: Bearer <DOCTOR_OR_ADMIN_JWT>'

curl -X POST http://localhost:8080/api/queues/departments/<DEPARTMENT_ID>/next \
  -H 'Authorization: Bearer <DOCTOR_OR_ADMIN_JWT>'
```

4. Bệnh nhân xem trạng thái của chính mình:

```bash
curl http://localhost:8080/api/queues/me/status \
  -H 'Authorization: Bearer <PATIENT_JWT>'
```

Lượt `APPOINTMENT` được tạo từ sự kiện RabbitMQ của Appointment Service. Sau đó bệnh
nhân lấy QR bằng `/api/queues/appointments/{appointmentId}/qr` và gửi `qrToken` tới
`POST /api/queues/check-in`.

### Chạy kiểm thử

```bash
export JWT_SECRET="replace-with-at-least-32-random-bytes"
mvn -pl careflow-queue-service -am test
```

Java chỉ dùng Spring Data derived queries; SQL chỉ nằm trong Flyway migration quản lý schema.

## Tổng quan

CareFlow là hệ thống quản lý quy trình khám bệnh tại bệnh viện công, xây dựng theo kiến trúc Microservices. Hệ thống hỗ trợ:

- **Bệnh nhân** (Mobile App): Đăng ký khám, theo dõi hàng đợi, nhận thông báo, xem toa thuốc
- **Bác sỹ** (Web App): Tra cứu hồ sơ, chẩn bệnh, kê toa, ChatBot AI gợi ý phác đồ
- **Hệ thống** (Core): Quản lý hàng đợi đa độ ưu tiên, xác thực, thông báo real-time

## Kiến trúc

```
Mobile App (Flutter)  ──┐
                        ├──▶ API Gateway ──▶ Microservices ──▶ PostgreSQL
Web App (React)       ──┘         │                │
                            Eureka Server     RabbitMQ
```

## Tech Stack

| Layer | Công nghệ |
|-------|-----------|
| Backend | Spring Boot 3, Java 21 |
| Mobile | Flutter |
| Web | React + Vite |
| Database | PostgreSQL |
| Message Broker | RabbitMQ |
| Service Discovery | Spring Cloud Eureka |
| API Gateway | Spring Cloud Gateway |
| Container | Docker + Docker Compose |

## Services

| Service | Trạng thái |
|---------|------------|
| API Gateway | 🔧 Planned |
| Identity & Auth | 🔧 Planned |
| Patient Service | 🔧 Planned |
| Appointment Service | 🔧 Planned |
| Queue Management ⭐ | ✅ Implemented on this branch |
| Notification Service | 🔧 Planned |
| Doctor Consultation | 🔧 Planned |
| Prescription Service | 🔧 Planned |
| EMR Service | 🔧 Planned |
| Laboratory Order | 🔧 Mock |
| Analytics Service | 🔧 Mock |
| AI Clinical Assistant | 🔧 Mock |

## Tài liệu

- [Implementation Plan](docs/implementation_plan.md)

## Team

Đề tài thực tập tốt nghiệp — PTIT
