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
| `PUT` | `/api/notifications/devices` | Đăng ký/cập nhật thiết bị nhận FCM |
| `DELETE` | `/api/notifications/devices/{deviceId}` | Ngừng gửi FCM đến thiết bị |

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
- FCM mặc định tắt. Khi bật, mỗi notification được fan-out thành các push delivery
  bền vững theo thiết bị, có retry/backoff và tự vô hiệu hóa token không còn hợp lệ.

## Firebase Cloud Messaging

Backend dùng Application Default Credentials; tuyệt đối không commit service-account JSON.

```powershell
$env:FIREBASE_PROJECT_ID="careflow-your-project"
$env:FIREBASE_CREDENTIALS_FILE="C:\secrets\careflow-firebase-admin.json"
docker compose -f docker-compose.infra.yml -f docker-compose.yml -f docker-compose.firebase.yml up -d --build careflow-notification-service
```

Mobile Android cần file `frontend/patient-mobile/android/app/google-services.json` có
package `com.careflow.careflow_patient`, sau đó chạy:

```powershell
flutter run --dart-define=FIREBASE_ENABLED=true --dart-define=API_BASE_URL=http://localhost:8080/api
```

Nếu chưa có hai file Firebase, để `FIREBASE_ENABLED=false`; inbox và WebSocket vẫn hoạt động.

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
