# CareFlow 🏥

> Hệ thống phần mềm trợ giúp khám chữa bệnh tại bệnh viện công theo kiến trúc Microservices

## Branch hiện tại: API Gateway

Branch: `feature/dangkhoii/api-gateway` · Port: `8080`

### Chức năng

- Định tuyến request tới các microservice thông qua Eureka service discovery.
- Cho phép public path đăng ký, đăng nhập, health check và WebSocket handshake.
- Xác minh chữ ký, issuer, thời hạn và role trong JWT cho protected request.
- Xóa `X-User-Id`, `X-User-Role`, `X-Correlation-Id` do client tự gửi.
- Gắn trusted identity headers và correlation ID trước khi chuyển tiếp request.
- Route WebSocket riêng bằng `lb:ws://notification-service`.

### Route chính

| Path | Service đích |
|---|---|
| `/api/auth/**`, `/api/users/**` | Identity |
| `/api/patients/**` | Patient |
| `/api/appointments/**` | Appointment |
| `/api/queues/**` | Queue |
| `/api/notifications/**`, `/ws/**` | Notification |

### Chạy kiểm thử

```bash
export JWT_SECRET="replace-with-at-least-32-random-bytes"
mvn -pl careflow-api-gateway -am test
```

Gateway chỉ là lớp kiểm tra đầu tiên; service đích vẫn phải kiểm tra role và ownership.

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
| API Gateway | ✅ Implemented on this branch |
| Identity & Auth | 🔧 Planned |
| Patient Service | 🔧 Planned |
| Appointment Service | 🔧 Planned |
| Queue Management ⭐ | 🔧 Planned |
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
