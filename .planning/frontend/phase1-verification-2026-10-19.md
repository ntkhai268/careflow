# CareFlow — Kết quả triển khai đợt 1 ngày 19/10/2026

## Kết quả và phạm vi

Đã triển khai MOB-01–04 theo [kế hoạch](../product-completion-plan-2026-10-19.md): tải/mở/chia sẻ tệp, quản lý thiết bị sinh trắc học, bảng giá từ API có snapshot lịch hẹn, ngừng danh bạ bác sĩ giả cũ và chặn cấu hình mock/demo trong Release.

Đã cập nhật Directory/Appointment/Consultation trên môi trường test Mac qua TLS/mTLS, khởi động lại web test bằng Next 16.4.2 và **cài đè/mở app iPhone 1.0.0 (11)**. Giữ bundle/dữ liệu app và thiết lập đăng nhập hiện có. Source chưa stage/commit/push; không triển khai production. AI và các đợt 2–6 vẫn theo kế hoạch, key Resend không thay đổi.

**Giới hạn nghiệm thu:** API thật, widget, HTTP transport và web đã kiểm tra; việc iOS thực sự mở trình xem/chia sẻ, thao tác danh sách thiết bị và đặt lịch trên màn native mới còn chờ người dùng xác nhận. Cài/mở app thành công không được tính thay cho các thao tác này.

## Thay đổi

| ID | Đã thực hiện | Trạng thái còn lại |
|---|---|---|
| MOB-01 | Tải stream qua ApiService có JWT/DPoP; giới hạn 10MB; kiểm tra chữ ký PDF/JPEG/PNG và dữ liệu thiếu; hủy/progress; không dùng fileUrl/tên tệp làm đích tin cậy; mở bằng native viewer hoặc chia sẻ theo thao tác người dùng. Dọn cache khi đổi phiên/đăng xuất/khởi động mới; từ chối kết quả tải thuộc phiên cũ. | Cần nghiệm thu trình xem/chia sẻ iOS thật; Word legacy chưa hỗ trợ mở. File người dùng đã chủ động xuất ra ngoài app không thuộc cache để tự xóa. |
| MOB-02 | Directory quản lý phiên bản giá VND có thời điểm hiệu lực; ADMIN có màn `/admin/services`; mobile lấy/chọn/tìm dịch vụ từ API, không dùng giá cố định; Appointment kiểm tra phiên bản và lưu snapshot, giữ lịch sử, bỏ qua số tiền client tự khai. | Dữ liệu giá hiện vẫn là giá mẫu trong DB test; chưa tích hợp thanh toán/hóa đơn thật. |
| MOB-03 | Danh sách đăng ký sinh trắc học, ngày đăng ký/sử dụng/hết hạn; xác định máy hiện tại bằng credential ID local; xác nhận thu hồi; thu hồi máy hiện tại dọn local/đăng xuất, máy khác giữ phiên hiện tại; đối soát đăng ký local hết hạn/đã bị thu hồi. | Chưa phải inventory mọi phiên password/web; tên model thiết bị chưa có metadata. Thử UI thật còn chờ. |
| MOB-04 | Caller web đang dùng Directory; controller `/api/doctors` cũ ngừng trả ba bác sĩ hardcode và không fallback ID sai. Booking không tạo biên lai ONLINE_MOCK. Có guard khởi động Release và công cụ kiểm tra build-config để chặn DEMO_MODE/USE_MOCK_AUTH. | Demo repository/mock auth/receipt fixtures vẫn còn cho test/debug; không tuyên bố đã xóa mọi mock. Dữ liệu test hiện có được giữ lại. |

## Contract và migration

- Directory: bảng `medical_service_prices`, lịch sử append-only, unique `(code, version)`, khóa giao dịch PostgreSQL khi công bố, optimistic version từ ADMIN; GET chỉ lấy phiên bản đã có hiệu lực rồi mới lọc active. Flyway V1 tạo bảng; baseline V0 cho DB Directory đã có master tables.
- Appointment: V6 thêm `service_code`, `service_name`, `price_version`, `quoted_amount`, `quoted_currency`, `price_effective_at`. Lịch cũ giữ NULL, không tự bịa giá để backfill. Client cũ thiếu serviceCode dùng mã dịch vụ mặc định **trên server**, server vẫn bắt buộc tra bảng giá có hiệu lực. Không có fallback số tiền khi Directory lỗi.
- Cập nhật DBML Directory/Appointment ở nhánh tích hợp và thiết kế gốc; bảng giá có seed idempotent trong DataInitializer chỉ cho demo/test. Môi trường test hiện tại được tạo master giá qua ADMIN API, không bật lại seed toàn stack hoặc reset queue.
- Quyền giá dùng principal/header đã được lớp xác thực service kiểm tra; PATIENT không được ghi hoặc đọc administration. Tệp và thiết bị giữ kiểm tra ownership phía server.
- Web proxy dùng fallback rewrite để route BFF `/api/session/[action]` được xử lý trước proxy backend. Đã kiểm tra login và reload bằng cookie trên runtime thật.

Contract chi tiết: [service-pricing-contract.md](../hospital-directory-service/service-pricing-contract.md).

## Kiểm chứng

| Kiểm tra | Kết quả |
|---|---|
| Maven toàn reactor, có PostgreSQL riêng cho recovery/biometric và catalog | 752 test đạt, 0 skip/failure/error |
| Bổ sung kiểm tra legacy doctor retirement, chạy Common/Consultation | 2 test retirement mới đạt; tổng case trong báo cáo đã thực thi hiện tại: **754**, 0 skip/failure/error |
| Flutter toàn suite | **207 đạt, 13 skip**, không có test thất bại; một skip mới là HTTP fixture có endpoint riêng |
| HTTP fixture tải tệp chạy riêng | 1 đạt: stream có Authorization/DPoP, bytes khớp, không có credential trong URL |
| Flutter analyze | Không error/warning; 2 style info có trước ở test ngoài phạm vi sửa |
| Web TypeScript và production build | Đạt |
| API thật qua Gateway/Identity/Directory/Appointment/Patient | **12 nhóm đạt** |
| Chrome web với backend thật | Login ADMIN, link quản trị giá, công bố revision, reload phục hồi phiên và UI đạt; đã xem ảnh desktop/narrow (narrow dùng nút thu gọn sidebar hiện có) |
| iOS Release | Build/ký/cài/mở **1.0.0 (11)** thành công trên iPhone 11 Pro Max; source lib trùng bản sao dùng build |

Các test mới bao phủ dữ liệu tải thiếu/sai/quá giới hạn, dọn tệp khi đổi phiên, hủy thu hồi thiết bị, thu hồi current/remote, đối soát credential local, stale price, dịch vụ inactive, giữ snapshot, idempotency, giá tương lai, công bố đồng thời, quyền ADMIN và retirement endpoint.

12 nhóm API thật xác nhận: master giá qua ADMIN; PATIENT bị chặn administration/publish; catalog trả giá/phiên bản; client giả amount/name không được tin; lịch cũ giữ giá; stale quote bị từ chối; stale admin write bị từ chối; future price chưa áp sớm; inactive không đặt mới; metadata thiết bị/khác chủ; PDF bytes/headers/khác chủ; thu hồi phiên/biometric hiện tại và đăng nhập lại bằng mật khẩu.

Số case lấy theo log các phiên chạy, không cộng báo cáo XML của lớp TOTP đã ngừng hỗ trợ còn sót trong target từ trước.

## An toàn dữ liệu test

Trước rollout đã snapshot DB test vào thư mục private. Test PostgreSQL catalog dùng DB riêng `careflow_phase1_catalog_test` trên loopback. Live regression dùng hai tài khoản tổng hợp riêng, không gửi email bên ngoài; đã hủy lịch test, xóa bản ghi/tệp test và ngừng dịch vụ giá tổng hợp sau khi kiểm tra. Dịch vụ Khám thường 150.000đ là master mẫu đã có/khởi tạo cho môi trường test; web UI tạo một revision mới cùng số tiền để nghiệm thu, không tự đổi phí đang dùng.

Không truy cập/áp migration vào DB remote/production; không reset hàng đợi, sửa trạng thái SQL để bỏ qua quy trình hoặc thu hồi thiết bị của người dùng thật.

GitNexus đã phân tích upstream trước sửa các symbol hiện có. Model lịch hẹn mobile (16 caller trực tiếp) và mapper lịch hẹn server (7 caller, 4 nhóm luồng) báo HIGH và đã được cảnh báo trước khi sửa; các trường báo giá mới giữ optional/nullable để bảo toàn lịch cũ. Callback UI không được graph giải hết được kiểm tra bằng widget/browser thay vì suy ra không có ảnh hưởng.

## Source và bằng chứng

- Backend/web: `.worktrees/security-integration`, nhánh `feature/dangkhoii/security-integration`.
- Native: `.worktrees/patient-mobile-ios-login`, nhánh `feature/dangkhoii/patient-mobile-ios-login`.
- [Kết quả API](phase1-live-results-2026-10-19.json), [kết quả web](phase1-browser-results-2026-10-19.json), [tóm tắt nghiệm thu](phase1-results-2026-10-19.json).
- Log/Surefire/fixture tạm: `/tmp/careflow-phase1-20261019/`. Artifact/config/log riêng: `~/Library/Developer/CareFlow/phase1-20261019/` và `~/Library/Developer/CareFlow/iphone11pm-20261018/`. Snapshot và secret không nằm trong Git; không chia sẻ log thô khi chưa rà dữ liệu nhạy cảm.
- Hướng dẫn thao tác duy nhất: [trang 14](../../huong-dan-su-dung/14-tep-thiet-bi-va-bang-gia.md); CHANGELOG và các trang 01/03/08/11/12 đã cập nhật. Các bản diff của worktree có nhiều thay đổi từ trước đợt 1, không quy toàn bộ diff cho đợt này.
