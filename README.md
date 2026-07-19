# CareFlow 🏥

> Hệ thống phần mềm trợ giúp khám chữa bệnh tại bệnh viện công theo kiến trúc Microservices

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
| Notification Service | ✅ Implemented on this branch |
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
