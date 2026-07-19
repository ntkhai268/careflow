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

### Yêu cầu môi trường

- Docker Desktop hoặc Docker Engine đang chạy.
- Docker Compose v2 (`docker compose version`).
- Tối thiểu khoảng 4 GB RAM trống cho toàn bộ stack.
- Các port `5432`, `5672`, `8080`, `8761`, `15672` chưa bị ứng dụng khác chiếm.

Không cần cài Java/Maven nếu chỉ chạy bằng Docker. JDK 21 và Maven 3.9+ chỉ cần
khi muốn build/test trực tiếp trên máy.

### Cài đặt và khởi chạy toàn bộ stack

```bash
git clone <repository-url>
cd careflow
git switch feature/dangkhoii/eureka-infrastructure

cp .env.example .env
# Mở .env và thay JWT_SECRET bằng kết quả: openssl rand -base64 48

docker compose config --quiet
docker compose up -d --build
```

`JWT_SECRET` là bắt buộc và phải dùng chung cho Identity, Gateway, Queue và
Notification. Không commit file `.env` hoặc secret thật lên Git.

Nếu chỉ cần hạ tầng để chạy service bằng IDE/Maven:

```bash
docker compose up -d postgres rabbitmq eureka-server
```

| Thành phần | Địa chỉ |
|---|---|
| API Gateway | `http://localhost:8080` |
| Eureka | `http://localhost:8761` |
| PostgreSQL | `localhost:5432` |
| RabbitMQ Management | `http://localhost:15672` |

RabbitMQ Management dùng username `careflow` và password lấy từ
`RABBITMQ_PASSWORD` trong `.env`. PostgreSQL dùng username `careflow`, password từ
`POSTGRES_PASSWORD`; script `scripts/init-databases.sql` tạo database cho từng service
ở lần khởi tạo volume đầu tiên.

### Hướng dẫn sử dụng và vận hành

Kiểm tra container và health endpoint:

```bash
docker compose ps
curl http://localhost:8080/actuator/health
curl http://localhost:8761/
```

Xem log toàn bộ stack hoặc một service:

```bash
docker compose logs -f
docker compose logs -f api-gateway
docker compose logs -f identity-service
```

Sau khi Identity đã đăng ký trên Eureka, có thể đăng ký tài khoản qua Gateway:

```bash
curl -X POST http://localhost:8080/api/auth/register \
  -H 'Content-Type: application/json' \
  -d '{
    "username": "patient01",
    "email": "patient01@example.com",
    "password": "Patient@123"
  }'
```

Dừng stack nhưng giữ dữ liệu PostgreSQL/RabbitMQ:

```bash
docker compose down
```

### Kiểm tra cấu hình

```bash
docker compose config --quiet
mvn -pl careflow-eureka-server -am test
```

Branch này cung cấp cấu hình hạ tầng độc lập. Compose chỉ có đầy đủ nghiệp vụ sau khi
code từ các branch service tương ứng được tích hợp vào cùng một nhánh triển khai; thao
tác tích hợp không được thực hiện tự động bởi branch này.

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
