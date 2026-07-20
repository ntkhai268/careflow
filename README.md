# CareFlow 🏥

> Hệ thống phần mềm trợ giúp khám chữa bệnh tại bệnh viện công theo kiến trúc Microservices

## Branch hiện tại: Identity & eKYC Service

Branch: `feature/dangkhoii/identity-service` · Port: `8081` · Database: `careflow_identity`

### Chức năng

- Đăng ký tài khoản công khai với role cố định `PATIENT`.
- Đăng nhập bằng username/email, mã hóa mật khẩu BCrypt và cấp access/refresh token.
- Xoay refresh token khi làm mới phiên, hỗ trợ thu hồi khi đăng xuất và phát hiện token bị tái sử dụng.
- Khóa tài khoản tạm thời 15 phút sau 5 lần đăng nhập sai.
- Xem tài khoản hiện tại và cho phép Admin cập nhật trạng thái tài khoản.
- Mock eKYC bằng API upload ảnh CCCD.
- Flyway migration cho schema `identity.users` và `identity.refresh_tokens`.

### API chính

| Method | Endpoint | Quyền |
|---|---|---|
| `POST` | `/api/auth/register` | Public |
| `POST` | `/api/auth/login` | Public |
| `POST` | `/api/auth/refresh` | Public |
| `POST` | `/api/auth/logout` | Public |
| `GET` | `/api/auth/me` | Authenticated |
| `POST` | `/api/auth/ekyc` | Authenticated |
| `PATCH` | `/api/users/{id}/status` | ADMIN |

### Yêu cầu môi trường

- JDK 21 và Maven 3.9+.
- Docker và Docker Compose để chạy PostgreSQL/RabbitMQ cục bộ.
- `JWT_SECRET` tối thiểu 32 byte và phải giống secret của API Gateway.

### Cài đặt và khởi chạy

```bash
git clone <repository-url>
cd careflow
git switch feature/dangkhoii/identity-service

docker compose -f docker-compose.infra.yml up -d postgres rabbitmq
export JWT_SECRET="$(openssl rand -base64 48)"

mvn -pl careflow-identity-service -am clean package
java -jar careflow-identity-service/target/careflow-identity-service-*.jar
```

Mặc định service dùng PostgreSQL tại `localhost:5432/careflow_identity`, tài khoản
`careflow/careflow`, RabbitMQ tại `localhost:5672` và chạy ở `http://localhost:8081`.
Có thể ghi đè bằng các biến:

| Biến | Mặc định | Ý nghĩa |
|---|---|---|
| `IDENTITY_DB_URL` | `jdbc:postgresql://localhost:5432/careflow_identity` | JDBC URL |
| `IDENTITY_DB_USERNAME` | `careflow` | Database username |
| `IDENTITY_DB_PASSWORD` | `careflow` | Database password |
| `EUREKA_URL` | `http://localhost:8761/eureka/` | Eureka Server |
| `JWT_SECRET` | Bắt buộc | Secret ký JWT |
| `JWT_EXPIRATION_MS` | `86400000` | Thời hạn access token (ms) |
| `JWT_REFRESH_EXPIRATION_MS` | `2592000000` | Thời hạn refresh token (ms, mặc định 30 ngày) |

### Hướng dẫn sử dụng

Đăng ký bệnh nhân:

```bash
curl -X POST http://localhost:8081/api/auth/register \
  -H 'Content-Type: application/json' \
  -d '{
    "username": "patient01",
    "email": "patient01@example.com",
    "password": "Patient@123"
  }'
```

Đăng nhập bằng username hoặc email:

```bash
curl -X POST http://localhost:8081/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{
    "usernameOrEmail": "patient01",
    "password": "Patient@123"
  }'
```

Lấy trường `data.accessToken` trong response và gọi endpoint được bảo vệ qua API
Gateway:

```bash
curl http://localhost:8080/api/auth/me \
  -H 'Authorization: Bearer <ACCESS_TOKEN>'

curl -X POST http://localhost:8080/api/auth/ekyc \
  -H 'Authorization: Bearer <ACCESS_TOKEN>' \
  -F 'image=@/absolute/path/to/cccd.jpg'
```

Khi access token hết hạn, gửi refresh token nhận được từ `/login` để lấy một cặp
token mới. Refresh token cũ sẽ bị thu hồi ngay sau lần sử dụng này:

```bash
curl -X POST http://localhost:8081/api/auth/refresh \
  -H 'Content-Type: application/json' \
  -d '{"refreshToken":"<REFRESH_TOKEN>"}'
```

Đăng xuất và thu hồi refresh token hiện tại:

```bash
curl -X POST http://localhost:8081/api/auth/logout \
  -H 'Content-Type: application/json' \
  -d '{"refreshToken":"<REFRESH_TOKEN>"}'
```

Không gửi trực tiếp `X-User-Id`/`X-User-Role` từ client; API Gateway sẽ xác minh JWT
và tự tạo các trusted header này.

### Chạy kiểm thử

```bash
export JWT_SECRET="replace-with-at-least-32-random-bytes"
mvn -pl careflow-identity-service -am test
```

Service nhận trusted headers `X-User-Id` và `X-User-Role` từ API Gateway.

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
| Identity & eKYC | ✅ Implemented on this branch |
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
