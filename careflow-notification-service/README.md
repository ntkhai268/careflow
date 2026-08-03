# CareFlow Notification Service

Notification Service sở hữu inbox bền vững của từng user, nhận event nghiệp vụ
qua RabbitMQ và đẩy item mới qua WebSocket/STOMP. WebSocket là tín hiệu realtime;
REST inbox vẫn là nguồn sự thật khi app reconnect hoặc từng offline.

## API

| Method | Path | Mục đích |
|---|---|---|
| `GET` | `/api/notifications?status=&limit=` | Inbox của user hiện tại |
| `GET` | `/api/notifications/unread-count` | Số item chưa đọc |
| `POST` | `/api/notifications/{id}/read` | Đánh dấu đã đọc |
| `POST` | `/api/notifications/read-all` | Đọc tất cả |

Gateway xác thực JWT và gắn `X-User-Id`; client không tự gửi header này.

## WebSocket

- Handshake qua Gateway: `ws://localhost:8080/ws/notifications`.
- STOMP `CONNECT` phải có native header `Authorization: Bearer <JWT>`.
- Subscribe: `/user/queue/notifications`.
- Sau reconnect, gọi lại REST inbox để đồng bộ item có thể đã nhận khi offline.

## Hạ tầng

- PostgreSQL database: `careflow_notification`.
- RabbitMQ queues: `notification.patient.events`,
  `notification.appointment.events`, `notification.queue.events`,
  `notification.lab.events`, `notification.prescription.events` và DLQ tương ứng.
- Flyway tự tạo schema `notification`.

Nếu volume PostgreSQL local đã tồn tại trước khi `careflow_notification` được
thêm vào `init-dbs.sql`, tạo database một lần trước khi start service:

```text
CREATE DATABASE careflow_notification;
```

## Kiểm thử

```bash
mvn -pl careflow-notification-service -am test
docker compose -f docker-compose.infra.yml -f docker-compose.yml config
```

Contract chuẩn: [`../docs/service-contracts/11-notification.md`](../docs/service-contracts/11-notification.md).
