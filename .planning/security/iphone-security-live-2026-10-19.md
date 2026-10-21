# Nghiệm thu bản bảo mật trên môi trường iPhone — 19/10/2026

## Phạm vi đã triển khai

- Cài đè và mở **CareFlow 1.0.0 (9)**, Release, trên iPhone 11 Pro Max đang kết nối. `devicectl` xác nhận phiên bản đã cài là 9; giữ bundle và dữ liệu ứng dụng.
- Cập nhật 12 tiến trình backend test trên Mac từ `feature/dangkhoii/security-integration`: hai Identity, Gateway và các service directory/patient/appointment/queue/consultation/prescription/emr/lab/notification. Cả 12 health check UP qua TLS/mTLS có xác minh CA.
- Sao lưu PostgreSQL test vào thư mục private trước khi cập nhật; V13/V14 được áp dụng trên DB loopback. Không truy cập hoặc thay đổi DB remote/production.
- Tạo khóa outbox riêng trong file private, không đưa vào Git. **Giữ nguyên Resend key theo yêu cầu chủ dự án.** Không gửi email bên ngoài trong các kiểm tra tự động của đợt này.
- AI để sau, không triển khai hoặc nghiệm thu AI. Web mới và hạ tầng production chưa được phát hành đồng bộ.

## Lỗi thực tế phát hiện và sửa

Sau nâng Spring Cloud, Gateway vẫn có thể báo health UP nhưng API trả 500 do cấu hình CA của HTTP client không được nạp đúng. Đã chuyển cấu hình sang namespace `spring.cloud.gateway.server.webflux` trong YAML, manifest secure, script rollout và cấu hình private của môi trường test. Không tắt xác minh TLS.

Sau khi build và khởi động lại Gateway, request qua chính địa chỉ HTTPS dành cho iPhone tới Identity trả mã từ chối 401 đúng cho tài khoản không tồn tại, thay cho lỗi TLS/500. Kiểm tra Common/Gateway lại đạt **81 test, 0 skip/failure/error**. Đây là kiểm tra bổ sung cho 736 test backend và 196 test Flutter đã ghi nhận ở đợt vá trước.

## Kết quả API thật

**14 nhóm đạt** trên Gateway/Identity/PostgreSQL thật của môi trường test, sử dụng tài khoản tổng hợp riêng:

1. Đăng nhập mật khẩu và đọc danh tính với proof đúng.
2. Access token sao chép không dùng được từ khóa thiết bị khác.
3. Danh sách thiết bị không trả secret hoặc thumbprint khóa.
4. Đăng xuất thu hồi phiên nhưng credential thiết bị vẫn đăng nhập lại được.
5. Token sinh trắc học bị sao chép không dùng được với khóa khác.
6. Credential hợp lệ gỡ được khóa tạm sau năm lần sai mật khẩu.
7. Credential không vượt qua trạng thái tài khoản DISABLED do quản trị.
8. Thu hồi thiết bị chặn credential/phiên của nó, giữ phiên thiết bị khác.
9. Tài khoản khác không thể thu hồi thiết bị của chủ tài khoản.
10. Setup/đăng nhập/khôi phục qua câu hỏi đều trả 410.
11. Không thể bỏ qua xác thực mã email; mã sai bị từ chối; gửi lại có cooldown; email không tồn tại không tạo mail outbox.
12. Replay refresh token thu hồi phiên cùng client; tài khoản khác vẫn hoạt động.
13. Đăng xuất toàn bộ thu hồi cả credential sinh trắc học.
14. Migration V13/V14 hiện diện và thành công trên DB test.

Bằng chứng: [kết quả API](iphone-security-live-2026-10-19.json), [trạng thái runtime](iphone-security-runtime-2026-10-19.json). JSON chỉ chứa kết quả, không chứa token/mật khẩu/OTP. Các lần chạy fixture trước kết quả cuối có lỗi kỳ vọng HTTP và vị trí bảng Flyway trong script kiểm tra; đã chỉnh theo contract thật, không đổi API để làm test đạt.

## Phạm vi chưa nghiệm thu

- Xác thực/hủy Face ID trên cảm biến thật, đổi tài khoản, mất mạng/khởi động lại và hành vi khóa khi trở lại app: chờ người dùng thao tác trên iPhone (9). Cài/mở thành công không đồng nghĩa đã xác nhận Face ID.
- Nhận email thật, mã hết hạn/gửi lại và toàn bộ reset mật khẩu trên giao diện iPhone: chưa nghiệm thu lại. Nhánh API từ chối mã sai/skip verify đã đạt; không lấy OTP từ DB/log để giả lập việc nhận email.
- Vân tay/Android, backup/restore, Secure Enclave/Keystore non-exportable và UI quản lý thiết bị chưa hoàn tất. API quản lý/thu hồi thiết bị đã triển khai, chưa có màn quản lý trong app.
- Scanner/volume tệp, browser E2E nhiều tab, onboarding passkey nhân viên và hạ tầng production vẫn theo các mục mở của [báo cáo khắc phục](bao-cao-khac-phuc-bao-mat-2026-10-19.md). Cấu hình test giữ chính sách đăng nhập nội bộ hiện tại, không tự bật chính sách production trước onboarding.

Các bước thao tác người dùng chỉ quản lý ở [hướng dẫn cài iPhone](../../huong-dan-su-dung/11-cai-app-iphone.md), [đăng nhập mobile](../../huong-dan-su-dung/12-dang-nhap-mobile.md) và [bản vá bảo mật](../../huong-dan-su-dung/13-ban-va-bao-mat-va-van-hanh.md).

## Dấu vết cục bộ

Artifact ký/cài và cấu hình riêng ở `~/Library/Developer/CareFlow/iphone11pm-20261018/`; snapshot, khóa outbox, artifact backend và log riêng ở `~/Library/Developer/CareFlow/security-device-20261019/`. Không chia sẻ file cấu hình, snapshot hoặc log thô khi chưa rà dữ liệu nhạy cảm. Source chưa stage/commit/push.
