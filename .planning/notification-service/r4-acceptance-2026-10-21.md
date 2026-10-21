# R4 — Kiểm chứng bổ sung 21/10/2026

**Bản iOS1.0.0(20) đã ký, cài đè, xác minh phiên bản từ iPhone và mở thành công. Mobile đạt272 test; chưa đóng R4 vì cấu hình/giao nhận Firebase/APNs và nghiệm thu thao tác máy thật còn thiếu.**

Đây là phần tiếp tục của [biên bản20/10](r4-acceptance-2026-10-20.md). Không sửa backend/schema trong đợt bổ sung21/10, không đổi key/credential, không tác động dữ liệu lịch khám/hàng đợi. Hướng dẫn thao tác duy nhất là [trang21](../../huong-dan-su-dung/21-thong-bao-va-dong-bo-r4.md).

## Sửa bổ sung

- Khóa phiên chuyển UI sang chưa xác thực ngay trước bước xóa credential bất đồng bộ. Coordinator ngừng đọc dữ liệu/kết nối của tài khoản cũ ngay khi khóa, tránh tiếp tục refresh trong thời gian Keychain đang xử lý.
- Push service giữ thao tác chạm cho đến khi điều hướng nhận nó. Nếu app còn ở nền/đang khóa, thao tác được xét lại khi đã đăng nhập và bind thiết bị thành công; payload có binding của tài khoản khác bị loại. Không giữ dữ liệu bệnh án trong thao tác chờ.
- Kết quả getInitialMessage trả về chậm không ghi đè callback OS vừa tới. SDK initialization xong sau khi scope đã đổi không được tiếp tục gọi API theo scope cũ.

## Kiểm chứng

- Flutter đầy đủ: **272 đạt,15 bỏ qua,0 lỗi**. Các ca bỏ qua giữ đúng phạm vi đã ghi trong biên bản20/10.
- Riêng R4: **22 ca đạt**, gồm sáu ca bổ sung về khóa trước cleanup, giữ/chấp nhận tap sau đăng nhập lại, bỏ tap khác tài khoản, acknowledgement không xóa tap mới hơn, callback lúc cold start và hủy bind cũ. SDK/push là fake có kiểm soát; không tính các ca này thành giao nhận FCM thật.
- Phân tích toàn bộ lib và test R4: **không có issue**. Đã chạy kiểm tra diff whitespace.
- Runtime21/10: đăng nhập Gateway/active-session, đọc inbox/detail được phân quyền, STOMP + DPoP vào inbox cá nhân, logout đóng WebSocket đều đạt. Chỉ mở/đóng phiên thử, không tạo phiếu/hồ sơ/lịch mới.
- **12 health mTLS đạt**, source lib khớp staging ký, codesign verify đạt. Devicectl xác minh app đang cài build20 và launch thành công. Không gỡ app/xóa dữ liệu. Bản19 đã được lưu backup private trước thay đổi.
- Backend280 test đạt/64 bỏ qua là kết quả20/10, không chạy lại chỉ vì sửa native. Các endpoint được đọc lại trong smoke21/10; không đồng nhất smoke với việc chạy lại toàn bộ backend tests.

## Phạm vi ảnh hưởng và phần còn mở

GitNexus trước sửa: AuthNotifier **HIGH,22 nơi gọi trực tiếp**; PushNotificationService **MEDIUM,4 nơi**; coordinator **LOW,1 nơi**; không có process edge được resolve ở các truy vấn này. Hai call-site của lockSession không suy ra kiểu receiver được; đã kiểm tra BiometricAppLock và các fixture trực tiếp. Đã cảnh báo HIGH, reindex và chạy detect-changes; số liệu toàn worktree gồm R1/R2/R3 và thay đổi trước đó, không chỉ patch bổ sung này. Không add/commit/push.

Kiểm tra cấu hình lại21/10: native/server Firebase flag vẫn false; chưa có file cấu hình native, ADC ở vị trí runtime/chuẩn hoặc aps-environment trong profile đang ký. Điều này chỉ mô tả môi trường local, không kết luận người dùng chưa tạo project trên Firebase Console. Chưa nghiệm thu Face ID/tap thông báo/background/terminated bằng thao tác trên máy; Android vẫn chưa nghiệm thu. Theo [trang21](../../huong-dan-su-dung/21-thong-bao-va-dong-bo-r4.md) để chuẩn bị cấu hình và thử các ca còn mở.

Bằng chứng đã loại secret/định danh thiết bị: [JSON21/10](evidence/r4-verification-2026-10-21.json). Snapshot/SDK logs/bản ký/cấu hình chỉ lưu private trên Mac.
