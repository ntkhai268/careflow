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

### Yêu cầu môi trường

- JDK 21 và Maven 3.9+.
- Eureka Server và service đích đang chạy.
- `JWT_SECRET` phải giống chính xác secret của Identity và Notification Service.

### Cài đặt và khởi chạy

```bash
git clone <repository-url>
cd careflow
git switch feature/dangkhoii/api-gateway
export JWT_SECRET='<same-secret-as-identity-service>'

mvn -pl careflow-api-gateway -am clean package
java -jar careflow-api-gateway/target/careflow-api-gateway-*.jar
```

Gateway chạy tại `http://localhost:8080` và mặc định tìm Eureka tại
`http://localhost:8761/eureka/`. Có thể đổi địa chỉ bằng `EUREKA_URL`.

Nếu chưa có Eureka Server, build và chạy ở một terminal khác:

```bash
mvn -pl careflow-eureka-server -am package
java -jar careflow-eureka-server/target/careflow-eureka-server-*.jar
```

### Hướng dẫn sử dụng

Kiểm tra Gateway đã sẵn sàng:

```bash
curl http://localhost:8080/actuator/health
```

Hai API đăng ký/đăng nhập là public và được chuyển tới Identity Service:

```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{
    "usernameOrEmail": "patient01",
    "password": "Patient@123"
  }'
```

Với endpoint protected, gửi JWT nhận từ API đăng nhập:

```bash
curl http://localhost:8080/api/auth/me \
  -H 'Authorization: Bearer <ACCESS_TOKEN>'
```

Kết quả mong đợi khi không có token là HTTP `401`. Gateway luôn xóa
`X-User-Id`, `X-User-Role`, `X-Correlation-Id` do client tự gửi rồi tạo lại trusted
headers từ JWT. Vì vậy client không được dùng các header này để tự khai báo danh tính.

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
