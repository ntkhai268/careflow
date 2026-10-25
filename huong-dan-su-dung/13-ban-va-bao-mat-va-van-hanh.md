# 13 — Bản vá bảo mật và điều kiện vận hành

[Quay về mục lục](README.md) · [Đăng nhập](02-dang-nhap-mfa.md) · [App điện thoại](12-dang-nhap-mobile.md)

## Phạm vi phiên bản

Bản vá ngày **19/10/2026** đã được cập nhật vào môi trường backend test trên Mac và app iPhone **1.0.0 (9)**. Backend/web source nằm ở `feature/dangkhoii/security-integration`; Identity gốc ở `feature/dangkhoii/identity-email-recovery`; mobile ở `feature/dangkhoii/patient-mobile-ios-login`. Chưa commit/push hoặc triển khai production; web mới chưa được phát hành đồng bộ. 14 nhóm API bảo mật thật đạt trên DB test; Face ID phần cứng và nhận mã/reset email đầy đủ còn chờ nghiệm thu. AI để sau theo yêu cầu.

## Đăng nhập và khôi phục

- Câu hỏi bí mật đã ngừng hỗ trợ cả cấu hình, đăng nhập và khôi phục. App/web mới không còn thẻ chọn câu hỏi; app cũ gọi API nhận 410. Dùng mật khẩu, passkey hoặc Face ID/vân tay đã đăng ký.
- Bệnh nhân vẫn dùng mật khẩu hoặc sinh trắc học. Đăng xuất phiên hiện tại giữ đăng ký sinh trắc học; thu hồi thiết bị, đặt lại mật khẩu hoặc đăng xuất toàn bộ sẽ vô hiệu hóa đăng ký.
- Nhập sai mật khẩu từ nơi khác không xóa vĩnh viễn đăng ký Face ID. Thiết bị có credential hợp lệ có thể gỡ khóa tạm do đoán sai mật khẩu; tài khoản bị quản trị viên khóa/vô hiệu hóa vẫn bị từ chối.
- Production yêu cầu tài khoản nội bộ dùng passkey có xác nhận người dùng; phiên cũ tạo bằng mật khẩu/câu hỏi bị từ chối cả khi JWT chưa hết hạn. Phải đăng ký và nghiệm thu passkey cho các tài khoản này trước khi bật policy production; không dùng tài khoản demo để khởi tạo người dùng thật. Quy trình cấp lại passkey cho nhân viên sau mất credential còn cần người phụ trách nghiệm thu.
- Quên mật khẩu vẫn chỉ qua email: yêu cầu mã → xác thực mã → nhập mật khẩu mới. Thành công sẽ thu hồi phiên, đăng ký thiết bị và passkey/câu hỏi cũ. Đăng nhập lại bằng mật khẩu rồi thiết lập lại phương thức phù hợp. Nhân viên bị mất passkey cần quy trình hỗ trợ xác minh; email không tự cấp lại passkey.
- Email có thể chờ hàng đợi; không lấy mã từ log/DB. Gửi lại mã làm mã cũ mất hiệu lực; chỉ dùng thư mới nhất.
- Web dùng cookie HttpOnly cho refresh token; JavaScript không lưu access/refresh token vào localStorage. Khi nâng cấp, thông tin phiên cũ được xóa và có thể cần đăng nhập lại. Tải lại trang sẽ kiểm tra phiên qua cookie; request từ website khác bị từ chối.

## Quyền hồ sơ và toa thuốc

- Endpoint hồ sơ đầy đủ chỉ cho chủ tài khoản bệnh nhân và bác sĩ đúng phân công khi đọc. STAFF/ADMIN không được dùng endpoint cập nhật hồ sơ đầy đủ để đọc hoặc sửa tiền sử/dị ứng.
- STAFF dùng các màn tiếp nhận/tóm tắt được cấp quyền; ADMIN quản trị không mặc nhiên có quyền đọc bệnh án hay hủy/cấp phát toa.
- Hủy toa cần bác sĩ phụ trách; cấp phát cần nhân viên quầy thuốc và hàng đợi hợp lệ. Lặp lại thao tác trên toa đã cấp phát chỉ chấp nhận đúng nhân viên đã cấp phát.
- Nếu nhận 403, kiểm tra vai trò/phân công; không đổi ID trong URL hoặc dùng tài khoản ADMIN để vượt quy trình.

## Tính năng đang bị giới hạn trên production

AI lâm sàng bị chặn. Bản vá VNPT ngày 19/10 ngừng endpoint eKYC một ảnh (410), thay bằng luồng xác minh qua nhà cung cấp; nguồn trước bản vá chỉ trả **UNVERIFIED** và không cấp quyền bệnh án. Tích hợp VNPT hiện tắt để chờ thông tin kết nối/gói Free và nghiệm thu thật; xem [trang 15](15-vnpt-ekyc.md). Đăng ký công khai chưa xác minh email cũng bị tắt trong manifest production; cần hoàn thiện luồng đăng ký có OTP trước khi mở lại. Không coi việc chặn tính năng là đã triển khai xong AI/eKYC/đăng ký xác minh.

## Tệp hồ sơ

Chỉ nhận JPEG, PNG và PDF; tối đa 10MB/tệp, 5 tệp/50MB mỗi lần tạo hồ sơ. Mỗi bệnh nhân tối đa 100 tệp hoặc 200MB. Ảnh tối đa 20 triệu pixel. File Word không còn được nhận; xuất PDF trước khi tải lên.

Production phải có dịch vụ quét ClamAV hoạt động. Nếu scanner chưa sẵn sàng, upload báo 503 và không xuất bản file. File bị phát hiện không an toàn báo 422. Download luôn là tệp đính kèm, không cache. Xóa bớt tệp qua chức năng được cấp quyền khi chạm quota; không sửa đường dẫn lưu trên DB.

## Chuẩn bị triển khai cho người vận hành

1. Chọn đúng commit/artifact đã nghiệm thu; sao lưu DB và volume tệp, thử phục hồi trên môi trường riêng. Không áp migration vào DB production để thử.
2. Dùng cấu hình private, không đưa vào Git. Theo yêu cầu chủ dự án, đợt test này giữ nguyên Resend key; cấu hình key vẫn ở file private. Quyết định xoay khóa trước production chưa thực hiện; không gửi key vào hội thoại. Sender là `CareFlow <careflow@supersignal-resend.id.vn>`.
3. Tạo khóa outbox AES-256 riêng dưới thư mục private được mount vào container. Lưu bản sao an toàn trong secret manager; không xoay/mất khóa khi còn mail chờ mã hóa bằng khóa đó. Ví dụ tạo file riêng: `openssl rand -base64 32 > "$CAREFLOW_SECURITY_DIR/recovery-outbox.key"`, rồi giới hạn quyền đọc file. Không in nội dung khóa.
4. Chuẩn bị TLS/mTLS và `db-ca.crt` thực tế; DB URL phải dùng `sslmode=verify-full`, đúng hostname/SAN và CA. Không hạ xuống bỏ xác minh chứng chỉ để vượt lỗi. Broker dùng TLS và tài khoản riêng theo service.
5. Cấu hình `CAREFLOW_WEB_ORIGIN`, `CAREFLOW_API_ORIGIN`, `CAREFLOW_PASSKEY_RP_ID` đúng domain HTTPS; cấu hình RP/origin sai sẽ làm passkey thất bại. `CAREFLOW_GATEWAY_URL` của web trỏ Gateway HTTPS; cookie Secure cần HTTPS tại trình duyệt.
6. Production chạy `secure,production`; không kèm `demo/test`. Seed dữ liệu demo chỉ chạy khi chủ động bật `demo` hoặc `test` trong môi trường riêng. Không bật seed trên dữ liệu thật.
7. Dùng `docker-compose.security.yml`; pipeline deploy mặc định cũ đã được thay bằng workflow chạy thủ công với kiểm thử và quét dependency. Biến `CAREFLOW_PRODUCTION_ENV_FILE` trên runner trỏ file private. Không dùng compose demo để deploy production.
8. Kiểm tra ClamAV có signature cập nhật, volume `patient-files` bền vững, outbox worker và log audit được thu thập. Chưa có chứng cứ nghiệm thu hạ tầng thật, backup/PITR/HA, retention, ký image và rollback tự động.

## Nghiệm thu sau triển khai

1. Dùng tài khoản test riêng; không đổi dữ liệu bệnh nhân đang dùng để thử từ chối quyền.
2. Xác nhận câu hỏi trả 410; password bệnh nhân hoạt động; tài khoản nội bộ trên production chỉ đăng nhập bằng passkey đã cấp hợp lệ.
3. Chạy gửi mã, mã sai/hết hạn, xác thực mã, đặt mật khẩu mới; credential/phiên cũ ở máy khác không được cấp hoặc dùng phiên mới. Không gửi OTP hoặc mật khẩu vào ảnh/log nghiệm thu.
4. Trên iPhone thật: bật Face ID → đăng xuất → Face ID; hủy; mất mạng; đổi tài khoản; reset từ máy khác. Trên Android kiểm tra vân tay/thiết bị không hỗ trợ và backup/restore. Các nhánh phần cứng này chưa được nghiệm thu lại trong đợt vá.
5. STAFF ngoài phân công và ADMIN phải bị từ chối dữ liệu lâm sàng ngoài quyền; bác sĩ đúng ca vẫn đọc/khám được; queue/quầy thuốc vẫn kiểm tra phân công.
6. Thử file giả đuôi, quá quota, scanner ngừng hoạt động và download khác bệnh nhân; kiểm tra dữ liệu vẫn còn sau thay container.
7. Trên web kiểm tra cookie HttpOnly/Secure/SameSite, tải lại trang, nhiều tab, đăng xuất và chặn request khác Origin.
8. Xác nhận cảnh báo dependency còn điều kiện được rà đúng source và chưa hết hạn. Ba advisory Spring phụ thuộc tính năng chưa được dùng có mốc rà lại 18/11/2026; thay đổi kiến trúc phải vá framework hoặc đánh giá lại trước khi mở tính năng.

Các kiểm thử tự động không thay thế diễn tập production và thao tác cảm biến trên máy thật. Xem trạng thái chính xác trong báo cáo khắc phục tại `.planning/security/`.

## Đối chiếu và khôi phục baseline R0

R0 bắt đầu 19/10 và hoàn tất 20/10/2026, giữ app1.0.0(16) và runtime hiện hành. [Manifest/kết quả chuẩn](../.planning/architecture/05-r0-baseline-results.md) ghi phiên bản backend/web/native, migration và các phần còn chưa nghiệm thu.

1. Trước triển khai đợt sau, đối chiếu branch/HEAD **và** sourceTreeSha256 trong manifest; có thay đổi chưa commit nên HEAD không đủ xác định source.
2. Snapshot riêng nằm ngoài Git tại `$HOME/Library/Developer/CareFlow/r0-20261019/`: hai source archive, runtime-artifacts và runtime-state.private.json. Chỉ người vận hành được cấp quyền truy cập; không gửi file cấu hình/private state lên Git hoặc kênh công khai.
3. Khi cần dựng lại, giải nén archive vào thư mục thử mới, kiểm tra SHA-256 theo manifest rồi build với toolchain/lockfile đã ghi. Cấu hình runtime, khóa, chứng chỉ và build defines private phải lấy từ nơi quản lý riêng; archive public source không thay secret backup.
4. Khi cần trở lại artifact đã lưu, đối chiếu digest và tương thích DB trước thay tiến trình; dùng cấu hình riêng tương ứng. Không chạy migration ngược hoặc reset hàng đợi để thử rollback. Bản Runner-16.app đã lưu là artifact có ký; bản iOS build kiểm chứng R0 chưa ký không dùng để cài trực tiếp.
5. R0 chỉ kiểm chứng archive/hash và build độc lập, chưa diễn tập restore DB/rollback cả môi trường. Thực hiện diễn tập ở môi trường test riêng trước release; không ghi đã đạt production từ kết quả này.
6. Push chưa sẵn: artifact16 không có aps-environment và workspace chưa có cấu hình Firebase private. Việc này được theo dõi ở R4; staging ổn định và phạm vi nghiệm thu Face ID/camera vẫn theo báo cáo, không tự chuyển sang đã đạt.
