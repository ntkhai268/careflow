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
