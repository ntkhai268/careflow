# CareFlow Postman

## Import

Import hai file sau vào Postman:

1. `CareFlow.postman_collection.json`
2. `CareFlow-Local.postman_environment.json`

Chọn environment **CareFlow - Local Docker**.

Nếu Postman chạy trên máy đang bật Docker, giữ:

```text
baseUrl=http://localhost:8080
```

Nếu gọi từ thiết bị khác, đổi `baseUrl` thành IP máy chạy Docker.

## Thứ tự test nhanh

1. **Identity / Đăng ký** — tự sinh username và email.
2. **Identity / Đăng nhập** — tự lưu `patientToken`, `refreshToken`, `userId`.
3. **Patient / Tạo hồ sơ** — tự lưu `patientId`.
4. **Appointment / Đặt lịch** — tự lưu `appointmentId`, `departmentId`, `roomId`.
5. Chờ 1–2 giây cho RabbitMQ.
6. **Queue / Lấy Visit Ticket** — tự lưu `ticketId`, `qrToken`.
7. Điền `staffToken`, chạy **Staff check-in QR**.
8. Điền `doctorToken`, chạy dashboard hoặc call-next.

## Token clinical staff

Đăng ký tài khoản mới luôn tạo role `PATIENT`. Để test API protected, cần token tương ứng:

- `adminToken`
- `staffToken`
- `doctorToken`

Admin có thể dùng request **Admin gán role user**, sau đó đăng nhập lại tài khoản được gán role và copy access token vào biến tương ứng.

## Lưu ý

- Visit Ticket được tạo bất đồng bộ, vì vậy GET ngay sau đặt lịch có thể trả 404 trong khoảng ngắn.
- `call-next` có thể trả 204 nếu chưa đến giờ hẹn.
- Các request hủy lịch, xóa hồ sơ và cập nhật config có thay đổi dữ liệu thật.
- Collection chỉ chứa API có controller trong code hiện tại. Consultation, Prescription, Laboratory, EMR, Notification và AI chưa có HTTP controller.
