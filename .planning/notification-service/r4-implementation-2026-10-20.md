# R4 — Đồng bộ native và thông báo

Ngày: 20/10/2026. Phạm vi: Identity, Queue, Notification và app Flutter thật tại `frontend/patient-mobile`. Không triển khai AI hoặc eKYC bên ngoài. Biên bản kiểm thử nằm ở `r4-acceptance-2026-10-20.md`; hướng dẫn vận hành duy nhất tại [hướng dẫn 21](../../huong-dan-su-dung/21-thong-bao-va-dong-bo-r4.md).

## Luồng và ranh giới

1. Giao dịch nghiệp vụ lưu trạng thái và outbox; Notification nhận sự kiện, xác định chủ hồ sơ hiện tại từ Patient Service và lưu inbox. Unique `(source_event_id, recipient_user_id, notification_type)` chống tạo trùng khi broker giao lại. Cạnh tranh insert có thể khiến một giao dịch rollback; broker retry sẽ gặp bản ghi đã tồn tại.
2. Hàng đợi RabbitMQ `notification.sync.events` nhận thay đổi appointment/queue/lab/prescription/consultation, gửi tín hiệu cá nhân `{id, invalidate:true}`. Thay đổi không có template inbox vẫn kích hoạt tải lại. Tín hiệu không phải nguồn trạng thái; mất tín hiệu được bù bằng API.
3. Coordinator gắn với vòng đời toàn app: STOMP CONNECT kèm bearer + DPoP, chỉ subscribe inbox cá nhân; debounce 400 ms, tải lại sau reconnect/resume, kiểm tra bù mỗi 30 giây khi foreground. Không có cam kết realtime nền khi WebSocket bị hệ điều hành dừng. Snapshot API đã phân quyền là nguồn sự thật, nên event trùng/trễ/đảo thứ tự không ghi đè trạng thái mới.
4. `PUT /api/notifications/devices` dùng principal đã được ActiveSessionFilter xác minh, chỉ PATIENT. Client không được tự chỉ định user/session. Khóa advisory PostgreSQL theo token và user/device giữ một chủ token; optimistic version phát hiện cập nhật đụng nhau. Binding ổn định trong cùng cài đặt/token đang bật, đổi khi token thay đổi hoặc được bật lại. Chuyển token vô hiệu hóa chủ cũ và giữ lịch sử.
5. Worker claim hàng đợi bằng `FOR UPDATE SKIP LOCKED`, khóa installation; trước gửi kiểm tra thời hạn 5 phút, token/binding/recipient, chuỗi refresh session và chủ hồ sơ hiện tại. Không lưu JWT dài hạn. Endpoint nội bộ chỉ chấp nhận mTLS đúng service, caller key độc lập và scope POST chính xác. Thu hồi/logout/reset/khóa tài khoản chặn gửi; outage xác minh chỉ retry.
6. Push chung chỉ chứa tên CareFlow, lời nhắc chung, notificationId và bindingId; không chứa tên bệnh nhân, chẩn đoán, toa, phòng hay route tùy ý. Inbox có nội dung chi tiết trong ứng dụng. `DELIVERED` là trạng thái inbox, không xác nhận điện thoại đã nhận/hiện push.
7. Chạm thông báo: kiểm tra binding tại client, đọc notification của tài khoản từ server, ánh xạ action whitelist tới API tài nguyên chính xác, kiểm tra binding ID/hồ sơ. API tài nguyên và Patient Service xác minh quyền hiện tại trước hiển thị. Hỗ trợ hồ sơ người thân và lượt không hẹn mà không cần chọn làm hồ sơ hiện hành.

## Chính sách phiên và lỗi

- Push chung được phép với refresh session còn hiệu lực dù phiên tương tác đã idle: mục đích là nhắc người dùng mở app. Đây không phải quyền đọc hồ sơ; API y tế vẫn yêu cầu xác thực tương tác và kiểm tra quyền mới. Thiết bị bị thu hồi/tài khoản khóa/refresh session hết hạn không được gửi.
- Vòng refresh rotation được theo tối đa 256 nút, chặn cycle, khác user/role và thời hạn tuyệt đối. Vượt giới hạn fail closed, cần đăng nhập/đăng ký lại.
- FCM `UNREGISTERED` mới vô hiệu token vĩnh viễn. `INVALID_ARGUMENT` có thể do payload/config nên không tự xóa token hợp lệ. Lỗi lưu trong DB dùng mã chung, không lưu nguyên văn lỗi nhà cung cấp/token.
- Retry có giới hạn và hết hạn push; thông báo quá hạn không được dồn lên màn khóa sau mất mạng lâu. Inbox vẫn tồn tại. Crash sau FCM nhận nhưng trước commit có thể gửi lại: hệ thống không tuyên bố exactly-once ngoài provider; foreground client khử trùng ID trong phiên, API/inbox unique.
- Worker hiện giữ khóa giao dịch trong lúc gọi dependency/FCM, batch tối đa 50. Chạy tải và đo timeout/lock trước mở rộng quy mô. Realtime broker của Spring là in-memory trên một Notification instance; triển khai nhiều instance cần broker relay hoặc cơ chế fan-out phù hợp, chưa được nghiệm thu trong R4 local.
- V4 bổ sung patient_id, session_id, binding_id, version và expires_at. Installation cũ chưa có session được tắt; pending push cũ thiếu binding chuyển DEAD, không xóa audit. Không seed token thiết bị giả; token chỉ do SDK thật đăng ký sau xác thực.

## Cấu hình và phần phụ thuộc

Code dùng Firebase Admin ADC phía server và cấu hình Firebase native tương ứng bundle/package. iOS cần APNs key phía Firebase và provisioning có `aps-environment`; FCM token chỉ lấy sau APNs token. Credential private không đưa lên Git. Không bật flag khi chưa có cấu hình đúng.

Bản kiểm tra đầu R4 chưa có Firebase project/ADC, GoogleService-Info.plist/google-services.json hoặc entitlement APNs trong profile đang ký; server và app giữ FIREBASE_ENABLED=false. Điều này không đáp ứng nghiệm thu push nền. [Firebase Flutter setup](https://firebase.google.com/docs/cloud-messaging/flutter/get-started), [receive messages](https://firebase.google.com/docs/cloud-messaging/flutter/receive-messages), [token lifecycle](https://firebase.google.com/docs/cloud-messaging/manage-tokens?hl=en).
