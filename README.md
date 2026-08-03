# CareFlow — Healthcare Platform

## Branch hiện tại: Notification WebSocket Service

Branch: `feature/dangkhoii/notification-websocket` · Port: `8085`

### Chức năng

- Khai báo durable RabbitMQ queue và DLQ cho các sự kiện `queue.#`.
- Consume sự kiện cấp số, check-in, gần lượt, gọi lượt, missed và completed.
- Cung cấp STOMP WebSocket endpoint `/ws`.
- Xác thực JWT trong STOMP `CONNECT` và dùng `sub` làm WebSocket principal.
- Gửi thông báo riêng tới `/user/queue/notifications`.
- Gửi cập nhật dashboard tới `/topic/queues/departments/{departmentId}`.
- Chỉ cho `DOCTOR`/`ADMIN` subscribe topic khoa; bệnh nhân chỉ nhận user destination.

### Contract realtime

| Thành phần | Giá trị |
|---|---|
| Handshake | `/ws` |
| Patient destination | `/user/queue/notifications` |
| Doctor/Admin topic | `/topic/queues/departments/{departmentId}` |
| RabbitMQ input | `queue.exchange` với routing key `queue.#` |
| REST source of truth | Queue Management API |

### Yêu cầu môi trường

- JDK 21 và Maven 3.9+.
- RabbitMQ; API Gateway và Eureka khi chạy theo kiến trúc đầy đủ.
- JWT hợp lệ do Identity Service cấp và `JWT_SECRET` giống Identity/API Gateway.
- Client hỗ trợ WebSocket + STOMP, ví dụ package `@stomp/stompjs`.

### Cài đặt và khởi chạy

```bash
git clone <repository-url>
cd careflow
git switch feature/dangkhoii/notification-websocket

docker compose -f docker-compose.infra.yml up -d rabbitmq
export JWT_SECRET='<same-secret-as-identity-and-gateway>'
export ALLOWED_ORIGINS='http://localhost:3000,http://localhost:5173'

mvn -pl careflow-notification-service -am clean package
java -jar careflow-notification-service/target/careflow-notification-service-*.jar
```

Service chạy ở `http://localhost:8085`; endpoint trực tiếp là `ws://localhost:8085/ws`.
Khi dùng đủ hệ thống, client nên kết nối qua Gateway tại `ws://localhost:8080/ws`.

| Biến | Mặc định | Ý nghĩa |
|---|---|---|
| `RABBITMQ_HOST` / `RABBITMQ_PORT` | `localhost` / `5672` | Message broker |
| `RABBITMQ_USERNAME` / `RABBITMQ_PASSWORD` | `guest` / `guest` | Tài khoản RabbitMQ |
| `EUREKA_URL` | `http://localhost:8761/eureka/` | Eureka Server |
| `JWT_SECRET` | Bắt buộc | Secret xác minh JWT ở STOMP `CONNECT` |
| `ALLOWED_ORIGINS` | `http://localhost:3000,http://localhost:5173` | Origin được phép handshake |

### Hướng dẫn sử dụng

Cài thư viện cho web client:

```bash
npm install @stomp/stompjs
```

Kết nối và nhận thông báo riêng của bệnh nhân:

```javascript
import { Client } from '@stomp/stompjs';

const client = new Client({
  brokerURL: 'ws://localhost:8080/ws',
  connectHeaders: {
    Authorization: `Bearer ${accessToken}`,
  },
  reconnectDelay: 5000,
});

client.onConnect = () => {
  client.subscribe('/user/queue/notifications', (frame) => {
    const notification = JSON.parse(frame.body);
    console.log(notification);
  });
};

client.activate();
```

Với JWT role `DOCTOR` hoặc `ADMIN`, có thể subscribe dashboard theo khoa:

```javascript
client.subscribe(
  `/topic/queues/departments/${departmentId}`,
  (frame) => console.log(JSON.parse(frame.body)),
);
```

JWT phải nằm trong native header `Authorization` của frame STOMP `CONNECT`, không chỉ
trong HTTP handshake. Sau khi reconnect, gọi lại Queue REST API để lấy trạng thái mới
nhất vì WebSocket không lưu lịch sử thông báo.

### Chạy kiểm thử

```bash
export JWT_SECRET="replace-with-at-least-32-random-bytes"
mvn -pl careflow-notification-service -am test
```

WebSocket là kênh best-effort; sau reconnect client phải gọi lại Queue REST API để đồng bộ.

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
| Notification Service | 🔧 Planned |
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
| `GET` | `/api/queues/appointments/{id}/qr` | PATIENT |
| `POST` | `/api/queues/check-in` | PATIENT |
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
