# Kiểm tra chức năng thành viên A — 15/10/2026

**Cập nhật sau sửa:** Bốn vấn đề HIGH bên dưới đã được khắc phục và chạy lại trên backend thật; xem [kết quả sửa và giới hạn còn lại](MEMBER-A-REPAIR.md). Nội dung tiếp theo giữ nguyên làm bằng chứng trước sửa.

**Kết luận: chưa ổn hết, chưa đủ điều kiện nghiệm thu toàn bộ phần A.** Kiểm tra thực tế tìm thấy lỗi dù 195 unit test đều đạt.

Phạm vi theo `careflow/docs/implementation_plan.md`, mục 5–6: Gateway, Identity/Auth/eKYC, Queue, Notification, Common, Eureka/RabbitMQ/Docker. Mobile là giao diện kiểm tra API. Đường đi: giả lập 3000 → Gateway 18080 → service → PostgreSQL/RabbitMQ audit riêng. Không mock/intercept API, không chèn SQL dựng trạng thái khám/thông báo. SQL chỉ đọc đối chiếu, ngoại lệ hạ tầng là tạo database Notification audit trống để Flyway tạo schema.

## Kết quả theo chức năng

| Nhóm | Kết quả | Đánh giá |
|---|---|---|
| Gateway | Routing đã thử, JWT sửa/sai/thiếu, header giả, encoded/matrix path | Đạt phạm vi kiểm tra |
| Identity | Register chỉ PATIENT, login, refresh rotation/replay/concurrent, logout current/all, role change, disable/lock/revocation trên hai JVM | 50/50 HTTP mới đạt |
| Phân quyền Queue | Doctor được phân công đọc ROOM-01 và alias khoa; doctor khác/patient bị chặn; direct service không tin header giả | Đạt các ca đã thử |
| Phiếu/QR | Lịch thật ngày mai có phiếu từ event thật; sai QR/ngày bị từ chối và phiếu không đổi | Đạt; check-in thành công chưa kiểm chứng |
| Tiếp nhận ROOM-01 | ADMIN gửi dữ liệu hợp lệ nhận 500 vì trùng số seed | Không đạt — HIGH |
| Tiếp nhận STAFF/no-account | STAFF bị 403; userId null bị 400 | Chưa đáp ứng luồng hỗ trợ — HIGH |
| Vòng đời Queue | Queue NHI audit: ADMIN tiếp nhận, retry, call/recall/miss/requeue/start/complete; sai quyền/transition bị chặn | Đạt luồng riêng; không phải E2E khám/CLS/thuốc |
| Inbox/đọc thông báo | Queue event thật tạo inbox; unread count; read/read-all; chặn user khác | Đạt |
| WebSocket | CONNECT/nhận tin thật, chặn thiếu token/destination khác | Cơ bản đạt; thu hồi phiên không đạt — HIGH |
| Notification nhiều hồ sơ | Hồ sơ thứ hai đặt lịch/có phiếu nhưng không có thông báo | Không đạt — HIGH |
| eKYC thật | Backend vẫn trả trường cố định và mock=true | Chưa triển khai, không tính mock là đạt |
| Ba điện thoại mẫu | Đăng nhập riêng, đúng hồ sơ và trạng thái chưa có lịch/xác nhận/hủy | Chrome thật 7/7 đạt lại |
| Hạ tầng | Health, Eureka registration, broker và DB thật hoạt động | Đạt môi trường audit |
| Docker | Ghép infra + application config hợp lệ; application file riêng thiếu dependency postgres | Chưa chứng minh deploy toàn stack |
| FCM, MFA/recovery, signing production, failover/multi-host, Lab/review/pharmacy E2E | Chưa có bằng chứng thực thi đầy đủ trong đợt này | Không đánh dấu hoàn tất |

## Bốn vấn đề HIGH

### A-NOT-SESSION — WebSocket bỏ qua thu hồi phiên

Dùng token đăng nhập thật mở STOMP, logout, kiểm tra REST Gateway/direct Notification trả 401. Dùng token đã thu hồi mở STOMP mới vẫn CONNECTED. Socket mở trước logout tiếp tục nhận CHECK_IN_SUCCESS từ tiếp nhận thật thực hiện sau logout.

`JwtAuthenticator.authenticate` chỉ kiểm tra JWT cục bộ, không kiểm tra active session; `WebSocketConfig` chỉ xác thực CONNECT và kiểm tra destination khi SUBSCRIBE. Cần xử lý cả kết nối mới và socket đang mở, gồm logout, expiry, disable và đổi quyền.

Nguồn: `careflow-notification-service/src/main/java/com/careflow/notification/security/JwtAuthenticator.java:30`, `config/WebSocketConfig.java:47`.

### A-QUEUE-SEED-SEQUENCE — Tiếp nhận mặc định trả 500

ADMIN tạo entry với patient/user thật ở Nội tổng quát. DB có NOI-001…NOI-005 nhưng không có counter tương ứng cho ngày/cấu hình. Entry tiếp theo thử NOI-001, vi phạm `uk_queue_number_per_day`; rollback nên retry không tự vượt được số trùng.

Giữ nguyên seed/counter để không che lỗi. Phần Queue còn lại được thử trên cấu hình audit NHI/ROOM-02 lấy từ danh mục thật. ADMIN thực hiện các command ở cấu hình này; kết quả không chứng minh doctor được phân công thực hiện command NHI, không sửa được lỗi ROOM-01.

Nguồn: `careflow-queue-service/src/main/java/com/careflow/queue/config/QueueDataInitializer.java:154`, `service/QueueManagementService.java` (`newEntry`/counter).

### A-NOT-MULTI-PROFILE — Mất thông báo hồ sơ thứ hai

Tạo hai hồ sơ thật trong một tài khoản. Hồ sơ đầu nhận thông báo Queue. Hồ sơ thứ hai đặt lịch CONFIRMED, Queue có TICKET_ISSUED nhưng inbox thiếu APPOINTMENT_CONFIRMED tương ứng. Đối chiếu DB: hai patient, một mapping; log đúng user mới có lỗi `patient_recipients_user_id_key`.

`notification.patient_recipients.user_id` có UNIQUE, trái quan hệ một tài khoản nhiều hồ sơ. Consumer rollback vì lỗi DB. Cần migration/DBML và đối soát/replay sự kiện lỗi có kiểm tra idempotency; sửa UI không giải quyết được.

Nguồn: `careflow-notification-service/src/main/resources/db/migration/V1__notification_schema.sql:5`, `domain/PatientRecipientProjection.java:21`, `service/NotificationService.java`.

### A-QUEUE-STAFF — Tiếp nhận hỗ trợ chưa sẵn sàng

Manual intake chỉ cho ADMIN; DTO bắt buộc cả patientId/userId. STAFF bị 403, người bệnh không có tài khoản bị 400. Đây là khoảng trống nghiệp vụ đã nêu trong kế hoạch; không đề nghị mở quyền STAFF toàn cục mà thiếu kiểm tra phân công/điểm tiếp nhận.

Nguồn: `careflow-queue-service/src/main/java/com/careflow/queue/controller/QueueController.java:64`, `dto/ManualIntakeRequest.java:8`.

## Bằng chứng

- Unit chạy lại: Gateway 34, Identity 40, Queue 59, Notification 20, Common 42: **195/195**, không fail/error/skip. Unit không thay bằng chứng API thật.
- Identity lifecycle mới: `/tmp/careflow-member-a-identity-http.json`, **50/50** HTTP qua Gateway/hai JVM.
- Role/direct-service/path/JWT mới: `.runtime/member-a-access-results.json`, **12/12**.
- HTTP/STOMP thật: `.runtime/member-a-audit-results.json`, bốn assertion nghiệp vụ không đạt: WS mới sau logout, socket cũ sau logout, intake mặc định, notification hồ sơ thứ hai. STAFF/no-account ghi riêng là thiếu chức năng. Poll 404 chờ ticket được ghi EXPECTED_ASYNC_WAIT, không tính lỗi.
- DB đối chiếu: `.runtime/member-a-db-evidence.json`. Chrome 3 máy: `.runtime/dataset-browser-results.json`, ảnh `.runtime/dataset-real-phones.png`.
- Check-in thành công cần lịch đúng ngày, QR/vị trí hợp lệ. Không đổi ngày DB hoặc tạo slot giả để đánh dấu đạt. Không dùng FCM token giả hay dữ liệu khám/CLS/toa thuốc giả để chứng minh E2E.
- Call-next/fairness nhiều instance, Lab/result-review/pharmacy, expiry socket theo thời gian và phục hồi sự cố chưa có E2E thật đầy đủ trong đợt này.

## Môi trường và phạm vi thay đổi

Notification trước đợt này chưa chạy và Gateway audit chưa có route. Đã build/test source hiện có, chạy Notification thật tại 127.0.0.1:18085, DB mới `careflow_notification_audit`, broker audit hiện có, Firebase tắt. Thêm route REST/WS chỉ trong cấu hình Gateway audit rồi khởi động lại Gateway. Eureka có NOTIFICATION-SERVICE UP; inbox/mobile hiện gọi được service thật.

Đã thêm script audit/báo cáo trong gia_lap_mobile; tạo cấu hình Queue NHI test và dữ liệu kiểm tra qua API. Giữ nguyên ba ca mẫu ban đầu. Không sửa mã nghiệp vụ CareFlow, stage/commit/push/merge hoặc DB remote chung. Các sửa bảo mật cũ vẫn ở worktree riêng chưa tích hợp develop; kết quả runtime không chứng minh checkout develop/Docker mặc định đã chứa các sửa đó.

Ưu tiên xử lý: WebSocket session; Queue counter/seed; recipient nhiều hồ sơ và phục hồi event; tiếp nhận STAFF/no-account. Sau đó chạy lại UAT check-in và các luồng Queue xuyên hệ thống trước nghiệm thu.
