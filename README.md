# CareFlow — Healthcare Platform

## Tổng quan

CareFlow là hệ thống quản lý khám chữa bệnh tại bệnh viện, gồm nhiều microservices.

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

## Trạng thái hiện tại

| Service | Trạng thái |
|---------|------------|
| API Gateway | ✅ Hoạt động, port `8080` |
| Identity & Auth | ✅ Hoạt động, port `8081` |
| Patient Service | ✅ Hoạt động, port `8082` |
| Appointment Service | ✅ Hoạt động, port `8083` |
| Queue Management | ✅ Hoạt động, port `8084` |
| Notification Service | ✅ Inbox/WebSocket, port `8085` |
| Doctor Consultation | 🔧 Planned |
| Prescription Service | 🔧 Planned |
| EMR Service | 🔧 Planned |
| Laboratory Order | 🔧 Planned |

## Kiến trúc và trust boundary

```text
Flutter / Web
      │  Authorization: Bearer <access-token>
      ▼
API Gateway :8080 ── Eureka ── Identity Service :8081 ── PostgreSQL
      │                            │
      ├─ kiểm tra chữ ký/issuer    └─ kiểm tra lại JWT và phân quyền
      └─ xóa header giả mạo
```

Client không được tự gửi `X-User-Id` hoặc `X-User-Role`. Gateway luôn xóa các
header này trước khi tạo lại từ JWT cho downstream.

`JWT_SECRET` và issuer phải đồng nhất giữa Gateway và Identity Service. Secret phải
có ít nhất 32 byte và không được commit vào repository.

## API contract

Tất cả JSON response dùng envelope:

```json
{
  "status": 200,
  "message": "Mô tả kết quả",
  "data": {},
  "timestamp": "2026-07-20T16:00:00Z"
}
```

### Identity Service API

| Method | Endpoint | Input | Output chính | Auth |
|---|---|---|---|---|
| `POST` | `/api/auth/register` | `username`, `email`, `password` | `201`, `UserResponse` | Public |
| `POST` | `/api/auth/login` | `usernameOrEmail`, `password` | `200`, cặp token và user | Public |
| `POST` | `/api/auth/refresh` | `refreshToken` | `200`, cặp token mới | Public |
| `POST` | `/api/auth/logout` | `refreshToken` | `200` | Public |
| `GET` | `/api/auth/me` | Bearer access token | `200`, `UserResponse` | Authenticated |
| `POST` | `/api/auth/ekyc` | multipart field `image` | `200`, `EkycResponse` | Authenticated |
| `PATCH` | `/api/users/{id}/status` | `ACTIVE`, `LOCKED` hoặc `DISABLED` | `200`, `UserResponse` | ADMIN |

### Queue Service API

| Method | Endpoint | Quyền |
|---|---|---|
| `GET` | `/api/queues/me/status` | PATIENT |
| `POST` | `/api/queues/entries` | ADMIN |
| `GET` | `/api/queues/rooms/{roomId}/check-in-qr?session=MORNING` | STAFF, ADMIN — Hospital Web lấy QR để hiển thị |
| `POST` | `/api/queues/check-in` | PATIENT — gửi `appointmentId`, `checkInQrToken` và vị trí thiết bị |
| `GET` | `/api/queues/departments/{id}/dashboard` | DOCTOR, ADMIN |
| `POST` | `/api/queues/departments/{id}/next` | DOCTOR, ADMIN |
| `POST` | `/api/queues/entries/{id}/miss` | DOCTOR, ADMIN |
| `POST` | `/api/queues/entries/{id}/requeue` | DOCTOR, ADMIN |
| `POST` | `/api/queues/entries/{id}/start` | DOCTOR, ADMIN |
| `POST` | `/api/queues/entries/{id}/complete` | DOCTOR, ADMIN |
| `GET`, `PUT` | `/api/queues/configs/{departmentId}` | ADMIN |

Sự kiện `APPOINTMENT_CREATED` bắt buộc có `appointmentDate` và `timeSlot`
(`HH:mm-HH:mm`, ví dụ `08:00-08:30`). Queue Service bảo vệ giờ bắt đầu đã hẹn:
bệnh nhân ưu tiên thông thường và walk-in chỉ được gọi nếu không làm lịch hẹn kế
tiếp bị trễ; ca `EMERGENCY` vẫn luôn được gọi trước.

## Cơ chế refresh token

Refresh token đã được triển khai theo cơ chế rotation:

1. `/login` cấp một access token và một refresh token ngẫu nhiên 256-bit.
2. Database chỉ lưu SHA-256 hash, không lưu raw refresh token.
3. `/refresh` kiểm tra token tồn tại, chưa hết hạn, chưa bị thu hồi và user còn
   `ACTIVE`.
4. Token cũ bị revoke và được liên kết với token thay thế; response trả một access
   token và refresh token hoàn toàn mới.
5. Dùng lại refresh token cũ trả `401` và thu hồi các refresh token còn hoạt động
   của user để giới hạn token theft/replay.
6. `/logout` thu hồi refresh token được gửi lên.

## Khởi chạy

Yêu cầu: JDK 21, Maven 3.9+, PostgreSQL 16, Docker Engine có Compose v2.

```bash
# Infrastructure
docker compose -f docker-compose.infra.yml up -d

# Build & Run
export JWT_SECRET="$(openssl rand -base64 48)"
mvn clean package -DskipTests
java -jar careflow-identity-service/target/careflow-identity-service-1.0.0-SNAPSHOT.jar
```

## Kiểm thử

```bash
# Backend
mvn -pl careflow-queue-service -am test
mvn -pl careflow-identity-service -am test

# Mobile
cd frontend/patient-mobile
flutter analyze
flutter test --no-test-assets
```

## Tài liệu liên quan

- [Implementation plan](docs/implementation_plan.md)
- [Tài liệu trong thư mục docs](docs/)
