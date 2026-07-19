# CareFlow 🏥

> Hệ thống phần mềm trợ giúp khám chữa bệnh tại bệnh viện công theo kiến trúc Microservices

## Branch hiện tại: Eureka & System Infrastructure

Branch: `feature/dangkhoii/eureka-infrastructure`

### Thành phần

- Eureka Server tại port `8761` làm service discovery.
- PostgreSQL 16 và script khởi tạo database-per-service.
- RabbitMQ Management với AMQP `5672` và dashboard `15672`.
- Multi-stage Dockerfile dùng Java 21 cho các Maven module.
- Docker Compose cho Gateway, Identity, Queue, Notification và hạ tầng liên quan.
- Health check và dependency ordering cho PostgreSQL, RabbitMQ và Eureka.
- `.env.example` để cấu hình JWT secret, database, RabbitMQ và allowed origins.

### Khởi chạy

```bash
cp .env.example .env
# Thay JWT_SECRET trong .env bằng secret ngẫu nhiên tối thiểu 32 byte
docker compose up --build
```

| Thành phần | Địa chỉ |
|---|---|
| API Gateway | `http://localhost:8080` |
| Eureka | `http://localhost:8761` |
| PostgreSQL | `localhost:5432` |
| RabbitMQ Management | `http://localhost:15672` |

### Kiểm tra cấu hình

```bash
docker compose config --quiet
mvn -pl careflow-eureka-server -am test
```

Compose đạt đầy đủ chức năng sau khi các branch service của Người A được merge vào `develop`.

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
