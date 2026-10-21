# Biên bản R4 — Thông báo và đồng bộ native

Bản cập nhật tiếp theo: [biên bản21/10 — iOS build20](r4-acceptance-2026-10-21.md).

Ngày 20/10/2026. **Triển khai và kiểm chứng kỹ thuật đạt trên môi trường test local; iOS 1.0.0(19) đã cài và mở trên iPhone thật. Chưa đóng R4 vì chưa có cấu hình Firebase/APNs và chưa nghiệm thu tương tác/push trên thiết bị.**

Phạm vi mã: backend trên `feature/dangkhoii/security-integration`, app thật trên `feature/dangkhoii/patient-mobile-ios-login`. Bảo toàn thay đổi có từ trước; không `git add`, commit hay push. Không thay Resend/caller keys, không đưa giấy tờ thật vào kiểm thử, không triển khai AI. Chỉ khởi động lại các JVM thuộc môi trường local được quản lý; không đổi DB remote dùng chung.

## Kết quả theo kế hoạch

| Mục | Đã thực hiện | Còn nghiệm thu |
|---|---|---|
| R4-01 — Hạ tầng push | Adapter FCM/APNs, TTL, quyền theo thao tác người dùng, chờ APNs token, nội dung màn khóa chung; kiểm tra profile/config; bản19 ký/cài/mở đạt | Firebase project, ADC phía server, cấu hình native và profile có aps-environment chưa có; push thật iOS/Android chưa kiểm tra |
| R4-02 — Đồng bộ native | Coordinator toàn app, STOMP + DPoP, subscribe cá nhân, reconnect/backoff, pause/resume, tải bù và kiểm tra 30s foreground; chỉ invalidation rồi đọc API; chống callback cũ khi đổi tài khoản | Thao tác UI thực tế trên iPhone khi ở màn khác, mất mạng/lên nền; tải/độ trễ thực tế |
| R4-03 — Token và phiên | Trusted principal/session, PostgreSQL lock + version, chuyển token giữ audit, binding riêng, expiry, kiểm tra rotation/revocation/current ownership trước gửi; inbox phân trang bền vững | Token do SDK/APNs/FCM thật, logout/đổi tài khoản/thu hồi thiết bị với giao nhận push thật |
| R4-04 — Thông báo và điều hướng | Inbox và template gần lượt/gọi lượt/lab/kết quả/toa/tái khám; whitelist action và API đích có phân quyền, hỗ trợ người thân/không hẹn; màn khóa không có dữ liệu y tế | Foreground/background/terminated, nhấn thông báo, quyền bị từ chối và trường hợp OS hạn chế trên máy thật |

Thiết kế, chính sách phiên idle và giới hạn mở rộng: [contract R4](r4-implementation-2026-10-20.md). Cách sử dụng/thiết lập/lặp lại duy nhất: [hướng dẫn21](../../huong-dan-su-dung/21-thong-bao-va-dong-bo-r4.md).

## Kiểm thử tự động

| Phạm vi | Đạt | Bỏ qua | Lỗi |
|---|---:|---:|---:|
| Common | 46 | 0 | 0 |
| Identity | 118 | 57 | 0 |
| Queue | 75 | 7 | 0 |
| Notification | 41 | 0 | 0 |
| Flutter đầy đủ, gồm R4 | 266 | 15 | 0 |

Backend reactor `package` đạt trên JDK25 với tùy chọn Byte Buddy tương thích. 64 ca backend bỏ qua thuộc các bộ PostgreSQL có điều kiện của đợt trước; không được tính là pass. 15 ca Flutter bỏ qua gồm 14 ca HTTP fixture có dart-define riêng và một ca pharmacy có sẵn. Phân tích toàn bộ `lib` và các test R4/hai fixture demo đã sửa: không còn issue. Hai lint trong các test quên mật khẩu/biometric cũ nằm ngoài thay đổi này, không sửa lan sang đó.

Các ca mới trực tiếp cho R4:

- PostgreSQL Notification **6 ca**, chạy Flyway V1–V4 và Hibernate validate: refresh cùng token giữ binding; token đổi tạo binding mới; đăng ký đồng thời cùng token chỉ còn một chủ enabled và giữ audit; chuyển chủ chặn thông báo đã xếp trước đó; lỗi provider vẫn giữ inbox và hàng retry; hai worker claim cùng lúc chỉ gọi sender một lần; cursor khi thời gian trùng không lặp và từ chối cursor tài khoản khác. Một test kiểm tra nhiều điều kiện trong cùng kịch bản.
- PostgreSQL Identity **2 ca**: kiểm tra root→rotated refresh trong giao dịch chỉ đọc rồi revoke leaf phải từ chối; liên kết rotation khác tài khoản phải bị chặn. Truy vấn đọc mới riêng cho push, không thay truy vấn khóa dùng để refresh/logout.
- Unit: binding/token/expiry/thu hồi phiên/thay chủ hồ sơ/outage Identity; 6 ca chính sách session, 3 ca endpoint nội bộ, 3 ca privacy/error-code FCM và 2 ca realtime tối giản. Sender/mock dependency chỉ dùng mô phỏng lỗi có kiểm soát; không tính là giao nhận FCM thật.
- Flutter: 15 ca kênh realtime, binding, token refresh/logout/cold start/quyền bị từ chối và API đích; thêm một widget test coordinator khi ở màn khác, debounce nhiều tín hiệu, pause/resume tải bù và logout. Test event chứa trạng thái giả và ID trùng chỉ tạo tín hiệu; không áp trạng thái payload vào hồ sơ.

## API/broker/realtime thật

Sáu nhóm kiểm chứng đạt qua Gateway HTTPS/mTLS, DPoP, PostgreSQL và RabbitMQ đang chạy:

1. Đăng ký installation lấy session từ principal; gọi endpoint nội bộ bằng certificate/key Notification hợp lệ. Caller khác bị403 và user/session không khớp trả inactive.
2. Tạo lịch tổng hợp ở ngày +2 qua API nghiệp vụ: outbox/broker tạo inbox và STOMP hint riêng cho chủ tài khoản. Đọc đúng detail/queue-context đạt; tài khoản khác bị404/403.
3. Cursor history có dữ liệu tiếp, không trùng trang đầu, cursor người khác bị404; đánh dấu đã đọc và tải lại vẫn READ.
4. Subscribe topic dùng chung bị từ chối.
5. Refresh token thật vẫn giữ hiệu lực push của root session đã đăng ký.
6. Logout làm root rotation inactive và đóng WebSocket đang xác thực.

Lịch kiểm thử được hủy bằng API được phân quyền; installation tổng hợp được disable bằng API, giữ audit. Không sửa SQL ép trạng thái nghiệp vụ. Không tác động lịch thật 07:30 ngày mai đã có từ trước. R3 đã kiểm chứng API/web cho CLS/toa/tái khám; đợt này không gán kết quả đó thành nghiệm thu APNs hoặc thao tác UI R4.

## Runtime và thiết bị

- Identity chính + phụ, Queue và Notification đã chạy mã R4 trong stack local; 12 endpoint health mTLS đạt. Bản sửa truy vấn read-only của Identity đã phát hành lại trước lần nghiệm thu API cuối.
- Global environment và toàn bộ config service khớp snapshot trước R4, ngoại trừ thêm đúng scope `POST:/internal/notifications/session-status` cho Notification trong hai Identity instance. Resend, biometric, passkey và credential khác được giữ nguyên.
- Native `lib` khớp hoàn toàn thư mục build private; codesign verify đạt. iPhone báo bundle đã cài **1.0.0, build19** và lệnh launch thành công. Không gỡ app/xóa dữ liệu.
- Chưa có thao tác chạm notification, Face ID, background/terminated hoặc kiểm tra hiển thị trực quan được ghi nhận trên iPhone trong R4. Android chưa build/nghiệm thu ở đợt này. Máy nhận diện/cài/mở được không đồng nghĩa các luồng UI đã đạt.
- FIREBASE_ENABLED vẫn false ở server và bản app. Chưa có ADC/project/native files; profile hiện tại thiếu aps-environment. Inbox/WebSocket không thay thế điều kiện nghiệm thu push nền.

## Phạm vi GitNexus và bằng chứng

Đã chạy impact trước sửa; các service trung tâm ở mức HIGH/CRITICAL đã được cảnh báo trước khi triển khai. Các quan hệ Java không giải được được đối chiếu controller/repository/transaction/client thực tế.

Đã reindex và chạy `detect-changes` cả hai worktree: backend **239 files /417 symbols /843 affected processes, CRITICAL**; native **51 files /232 symbols /10 affected processes, HIGH**. Đây là toàn bộ diff chưa commit, bao gồm thay đổi trước R4. Không coi tất cả là mã mới của R4. Một số tên symbol Java bị lỗi metadata và traversal bị giới hạn; kiểm tra thủ công và manifest SHA-256 so snapshot đầu đợt được dùng để đối chiếu phạm vi. Native FTS bị lỗi sau incremental/repair, đã full rebuild thành công.

Bằng chứng đã loại thông tin private: [JSON nghiệm thu](evidence/r4-verification-2026-10-20.json). Snapshot mã/cấu hình, DB dump, log đầy đủ, app ký và manifest delta riêng của R4 nằm trong thư mục private trên Mac; không sao chép key/token/profile/định danh thiết bị vào Git.

## Điều kiện đóng R4

Cần hoàn tất cấu hình Firebase/APNs theo hướng dẫn21 rồi nghiệm thu máy thật: thông báo khi mở app/ở nền/không chạy, cấp-từ chối quyền, tap mở đúng tài nguyên, rotate token, đổi tài khoản và thu hồi thiết bị. Đo độ trễ và thử mạng chập chờn; ghi riêng trường hợp OS hạn chế. Nhiều Notification instance còn cần broker relay/fan-out, kiểm tra tải và giám sát worker; bản local này chỉ nghiệm thu một instance Notification. Chưa đánh dấu R4 hoặc mốc dùng thử R0–R4 đạt trọn vẹn.
