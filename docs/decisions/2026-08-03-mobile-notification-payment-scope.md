# Giai đoạn 0 — Chốt phạm vi Mobile, Notification và Payment

> Trạng thái: Accepted
>
> Ngày chốt: 2026-08-03
>
> Áp dụng cho: Patient Mobile, Notification Service, Laboratory Order contract

## 1. Quyết định

1. Nhóm CareFlow tiếp quản Notification Service; nhánh
   `feature/dangkhoii/notification-websocket` chỉ dùng làm tài liệu tham khảo,
   không merge nguyên trạng vì đã lệch xa `develop` và chưa có persistent inbox.
2. Notification MVP gồm inbox lưu bền vững, REST read/unread và WebSocket STOMP
   cho chính user. Không làm FCM, SMS, email, Doctor Web topic hoặc màn hình gọi
   số công cộng trong vertical slice này.
3. WebSocket handshake dùng `/ws/notifications`; client subscribe
   `/user/queue/notifications`. Principal lấy từ JWT, không lấy user ID từ URL.
4. Notification xác định patient recipient bằng `recipientUserId`/`userId` có
   trong event hoặc projection `patientId → userId` từ `PatientProfileCreated`.
   Không gọi Patient Service qua internal API vô danh.
5. Payment không phải microservice độc lập và không tích hợp cổng thanh toán
   production. Laboratory journey chỉ mô hình hóa:
   - `ONLINE_MOCK`;
   - `CASH_AT_HOSPITAL`.
6. `HEALTH_INSURANCE` bị loại khỏi payment method và payment state của MVP.
   Số BHYT vẫn có thể tồn tại như dữ liệu hành chính trong Patient Profile,
   nhưng CareFlow không tính quyền lợi hoặc quyết toán BHYT.
7. Mobile không tính thuật toán/vị trí Queue. Nó hiển thị queue state và
   `recommendedNext` do Queue Service trả về.
8. Appointment vẫn tự `CONFIRMED` khi slot hợp lệ và không bị chặn bởi Payment.
   Payment mock trong quyết định này chỉ áp dụng cho Laboratory Order có
   `paymentRequired=true`.

## 2. Ranh giới implementation tiếp theo

- Giai đoạn 1 được phép thay đổi presentation/design system của Patient Mobile,
  nhưng phải giữ repository boundary để backend thật thay mock mà không viết lại
  màn hình.
- Notification Service mới phải bắt đầu từ `develop`; chỉ port phần JWT/STOMP
  hữu ích từ nhánh cũ sau khi đối chiếu contract `CF-SVC-11 v1.1`.
- Consultation, Laboratory, Prescription và Doctor Web do nhánh khác sở hữu;
  Mobile dùng fixture/adapter cho tới khi contract thật sẵn sàng.
- Hospital Directory được hoãn để không làm thay đổi nguồn khoa/phòng/bác sĩ
  trong lúc nhóm Hospital Web đang phát triển.

## 3. Điều kiện kết thúc Giai đoạn 0

- Contract Payment chỉ còn `ONLINE_MOCK` và `CASH_AT_HOSPITAL`.
- REST/WebSocket Notification, recipient resolution, offline và idempotency đã
  có một cách hiểu duy nhất.
- Appointment fixture có owner `userId`; clinical event có thể dùng patient
  projection.
- Các implementation cũ còn `insurance` được xem là technical debt phải xóa ở
  Giai đoạn 1, không còn là contract hợp lệ.
