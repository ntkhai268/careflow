# Chạy Patient Mobile hybrid demo tại local

## 1. Phạm vi

Hybrid demo dùng API thật cho:

- Identity/Auth;
- Patient và hồ sơ sức khỏe;
- khoa, ca khám và Appointment.

Phiếu/QR, Queue, Consultation, Laboratory, Prescription và Notification sau
Appointment được mô phỏng cục bộ khi `DEMO_MODE=true`.

## 2. Container tối thiểu

```text
careflow-eureka-server        :8761
careflow-api-gateway          :8080
careflow-identity-service     :8081
careflow-patient-service      :8082
careflow-appointment-service  :8083
```

Kiểm tra:

```powershell
docker ps --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"
```

### Dùng PostgreSQL và RabbitMQ local

```powershell
cd D:\PTIT\CareFlow

docker compose -f docker-compose.infra.yml up -d
docker compose -f docker-compose.yml up -d --build `
  careflow-eureka-server `
  careflow-api-gateway `
  careflow-identity-service `
  careflow-patient-service `
  careflow-appointment-service
```

### Dùng PostgreSQL và RabbitMQ remote

Tạo file riêng `docker-compose.override.yml`. File này bị Git ignore và không
được commit credential. Override tối thiểu các biến:

```yaml
services:
  careflow-identity-service:
    environment:
      IDENTITY_DB_URL: jdbc:postgresql://<db-host>:5432/careflow_identity
      SPRING_RABBITMQ_HOST: <rabbit-host>
  careflow-patient-service:
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://<db-host>:5432/careflow_patient
      SPRING_RABBITMQ_HOST: <rabbit-host>
  careflow-appointment-service:
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://<db-host>:5432/careflow_appointment
      SPRING_RABBITMQ_HOST: <rabbit-host>
```

Username/password phải truyền bằng environment hoặc file `.env` không commit.
Khởi động:

```powershell
docker compose -f docker-compose.yml -f docker-compose.override.yml up -d --build `
  careflow-eureka-server `
  careflow-api-gateway `
  careflow-identity-service `
  careflow-patient-service `
  careflow-appointment-service
```

## 3. Kiểm tra backend

```powershell
Invoke-WebRequest http://localhost:8761/actuator/health
Invoke-WebRequest http://localhost:8080/actuator/health
Invoke-WebRequest http://localhost:8081/actuator/health
Invoke-WebRequest http://localhost:8082/actuator/health
Invoke-WebRequest http://localhost:8083/actuator/health
```

`GET /api/appointments/departments` qua Gateway có thể trả `401` khi không có
JWT; điều đó chứng minh security filter hoạt động. Mobile đã đăng nhập sẽ gửi
Bearer token.

## 4. Điện thoại Android thật qua USB

Tìm `adb`:

```powershell
where.exe adb
```

Nếu chưa có trong `PATH`, vị trí Windows thường dùng:

```text
C:\Users\<user>\AppData\Local\Android\Sdk\platform-tools\adb.exe
```

Kiểm tra thiết bị và tạo reverse tunnel:

```powershell
adb devices -l
adb reverse tcp:8080 tcp:8080
adb reverse --list
```

Port reverse phải chạy lại sau khi rút USB, restart điện thoại hoặc restart ADB.

Chạy đúng worktree/branch:

```powershell
cd D:\PTIT\CareFlow\.worktrees\patient-mobile\frontend\patient-mobile

flutter run `
  --dart-define=DEMO_MODE=true `
  --dart-define=REAL_QUEUE=true `
  --dart-define=API_BASE_URL=http://127.0.0.1:8080/api `
  --dart-define=WS_BASE_URL=ws://127.0.0.1:8080/ws
```

Không dùng chỉ `--dart-define=DEMO_MODE=true` khi muốn Gateway local, vì app sẽ
dùng `https://api.careflow-demo.online/api`.

## 5. Smoke test

1. Đăng nhập.
2. Mở tab **Phiếu khám**; danh sách phải tải mà không có `404`.
3. Chọn **Đặt khám**; hồ sơ, khoa và ca phải đến từ API.
4. Ca đã qua trong ngày không được hiển thị.
5. Appointment mới trả `CONFIRMED`.
6. Mở **Hành trình khám**; `DEMO_MODE=true` hiển thị **Điều khiển mô phỏng**.

## 6. Lỗi thường gặp

| Hiện tượng | Nguyên nhân | Cách xử lý |
|---|---|---|
| `404 /api/appointments/...` | App đang gọi Gateway public/cũ hoặc Gateway thiếu route | Truyền `API_BASE_URL`, kiểm tra Gateway và Eureka |
| `401` | JWT thiếu/hết hạn hoặc secret giữa Gateway và Identity lệch | Đăng nhập lại, dùng cùng `JWT_SECRET` |
| Điện thoại không gọi được `localhost:8080` | Chưa có ADB reverse | Chạy `adb reverse tcp:8080 tcp:8080` |
| Lịch ở `PENDING` | Dữ liệu cũ tạo trước auto-confirm | Tạo lịch mới hoặc chuyển hợp lệ qua Appointment API |
| `409` khi đặt lịch | Bệnh nhân đã có lịch chưa hủy cùng ngày/ca | Chọn ca khác hoặc hủy lịch cũ |
