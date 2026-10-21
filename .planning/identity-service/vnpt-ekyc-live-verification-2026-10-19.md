# Nghiệm thu bổ sung VNPT eKYC thật — 19/10/2026

> Lịch sử, đã được thay thế: VNPT ngừng từ bản15 theo yêu cầu người dùng. Adapter đã bỏ, API cũ410. Contract hiện hành: [đọc trên máy và đối chiếu trực tiếp](local-document-review-contract.md). Các số liệu phía dưới chỉ đúng tại mốc kiểm tra cũ.


## Kết luận và giới hạn

Đã kết nối dự án **VNPT Free thật**, bật hai instance Identity test và cài/mở **CareFlow iOS 1.0.0 (14)** trên iPhone 11 Pro Max. Kiểm tra âm tính Gateway → Identity → VNPT đã từ chối đúng ảnh không phải căn cước; replay không gọi nhà cung cấp thêm. Không đánh dấu eKYC hoàn thành nghiệm thu người thật: căn cước/selfie thật, OCR thành công, passive liveness và face compare trên iPhone vẫn cần người dùng thực hiện. Không dùng kết quả mock để thay bằng chứng.

Đã nghiệm thu bổ sung email thật cho đợt 2: Resend báo **Delivered** cho ba thư đăng ký, OTP quên mật khẩu và thông báo đổi mật khẩu. Kiểm tra API với tài khoản/DB tách riêng xác nhận OTP/grant một lần và thu hồi phiên cũ. Hiển thị trong Gmail và toàn bộ hành trình UI iPhone vẫn chờ người dùng xác nhận.

Phạm vi runtime: máy Mac hiện tại, DB PostgreSQL loopback riêng; không triển khai production/DB remote. Backend/web ở `feature/dangkhoii/security-integration` (`5a8c6e5` + thay đổi chưa commit); native ở `feature/dangkhoii/patient-mobile-ios-login` (`e5fc9c1` + thay đổi chưa commit). Cả hai đã `git pull origin main` trước triển khai, kết quả up to date. Không add/commit/push.

## Thay đổi trong lượt này

| Thành phần | Thay đổi và mục đích |
|---|---|
| `VnptEkycSettings` | Access token đã dán có sẵn Bearer; chuẩn hóa để không gửi hai tiền tố, trim ngoài token ID/key, chặn rỗng/khoảng trắng nội bộ/ký tự điều khiển trước khi cho bật dịch vụ và tiêu quota. Giữ hai cờ enable/Free confirmation và trần100. |
| Flutter `ApiService` | Clone FormData đã gửi trước khi thử lại sau 401/refresh. Giữ đủ ba ảnh, khóa idempotency cũ và DPoP mới. Một lần retry cho hết hạn phiên; không tự retry lỗi quota/VNPT. |
| Flutter eKYC | Chặn submit khi đang bận/chưa đồng ý/chưa đủ ảnh; hiển thị lý do kiểm tra giấy tờ, liveness, so khớp hoặc gián đoạn rõ hơn. Trạng thái xác minh vẫn do backend quyết định. |
| Runtime riêng | Nạp ba thông tin VNPT vào hai Identity sau khi xác nhận Free; không đổi Resend key, outbox AES key hoặc bật dịch vụ trả phí. |
| Hướng dẫn | Cập nhật trang11/12/15/16, mục lục/changelog, contract và kế hoạch. Hướng dẫn thao tác chỉ ở `huong-dan-su-dung/`. |

Không thêm Entity/migration/bảng mới trong lượt này. V15/quota và V16/email của các lượt trước giữ nguyên; DBML hiện có không cần đổi. Bản trạng thái trước khi bật được sao lưu ngoài Git. Dữ liệu bảo mật không seed giả VERIFIED.

## Gói Free và request thật

Đọc metadata dự án, gói và danh sách API bằng HTTPS với token server riêng: Token ID và Token Key khớp cùng dự án, Free đang active, thời hạn gói trả về đến `2026-11-19T23:59:59Z`; không đồng nghĩa token không thể hết hạn trước đó. Bốn API bắt buộc có entitlement và phí API trả về0: card liveness, OCR ID, face liveness, face compare. Chỉ gọi API đọc; không gọi mua, nâng gói hoặc đổi token.

Kiểm tra âm tính dùng tài khoản demo riêng chưa có kết quả eKYC, không dùng tài khoản đang mở trên iPhone. Ảnh JPEG640×400 chứa chữ xác định ảnh kiểm tra, không có người/số căn cước:

1. API status xác nhận VNPT available.
2. POST đủ ba phần ảnh, consent và UUID request key qua Gateway có DPoP/TLS thật.
3. VNPT upload + kiểm tra mặt trước trả kết quả không đạt. CareFlow trả HTTP200 với `REJECTED / DOCUMENT_CHECK_FAILED`, không có số giấy tờ đã che, không VERIFIED.
4. Bộ đếm bền vững tăng0→2, dừng trước OCR/liveness/compare tiếp theo.
5. Replay cùng key/nội dung trả bản ghi cũ; tăng thêm0 request.
6. Sau rollout cuối, status có xác thực trả200/available=true trên Gateway và cả hai Identity.

Còn98 theo **bộ đếm cục bộ tại thời điểm kiểm tra**, không phải số dư nhà cung cấp nếu token được dùng nơi khác. Một lượt qua đủ bước dùng tối đa8 outbound calls; trần100 không có nghĩa100 người. Metadata GET đọc cấu hình không phải lượt inference eKYC; bằng chứng quota ghi riêng hai outbound verification calls. Không tự trả lại quota khi lỗi hoặc nâng gói.

## Email thật và dữ liệu kiểm thử

Dùng Identity thử riêng tại cổng18093 và DB `careflow_email_delivery_acceptance_test`, cùng adapter Resend/template thực tế và khóa outbox hiện có. VNPT tắt trong fixture này. Tạo tài khoản ngẫu nhiên với địa chỉ người dùng cung cấp; không sửa email/mật khẩu/Face ID của tài khoản trên điện thoại.

- Email đăng ký được gửi thật; đúng mã mới tạo PATIENT và đăng nhập được.
- Email quên mật khẩu được gửi thật; mã sai trả400; đúng mã mới được quyền reset.
- Quyền reset dùng một lần, lần confirm lại trả400.
- Sau reset: access cũ401, refresh cũ401, mật khẩu cũ401; mật khẩu mới đăng nhập200.
- Email thông báo reset được gửi thật; ba bản ghi outbox SENT, provider ID có giá trị, payload mã hóa đã xóa.
- Đối chiếu read-only `GET /emails/{id}` của Resend: cả ba `last_event=delivered`, sender CareFlow đúng, không thẻ ảnh trong HTML, không attachment trong metadata. Không suy ra người dùng đã đọc hoặc render Gmail đạt.
- Mã OTP chỉ được giải mã trong harness riêng ở DB fixture để chạy kiểm thử; không đưa mã, email, token, mật khẩu hoặc provider payload thô vào Git/log công khai. Kiểm tra đọc outbox không thay thế nghiệm thu nhập OTP trên UI.
- Sau kiểm tra đã dừng đúng tiến trình fixture và xóa DB thử riêng; runtime chính tiếp tục chạy. Không reset DB chính.

Harness ban đầu chờ trạng thái theo challenge ID ở bước cuối gặp hai dòng CODE/CHANGED cùng ID; sửa điều kiện chờ theo từng dòng, hoàn tất xác nhận trực tiếp ba message và giữ bằng chứng. Không gửi lại thư hoặc chạy lại luồng tạo tài khoản để chữa lỗi harness.

## Kiểm thử và bản cài

| Kiểm tra | Kết quả |
|---|---|
| Maven Common/Gateway/Identity, có kiểm thử PostgreSQL thật riêng | 247 đạt =46+35+166; lỗi0, bỏ qua0 |
| Flutter toàn bộ suite | 225 đạt,16 bỏ qua có điều kiện |
| Flutter HTTP fixture chạy riêng | 15 đạt: refresh3, email recovery4, download1, biometric device4, eKYC upload3 |
| Tổng Flutter đã thực thi | 240 đạt; còn1 test demo cũ bỏ qua (assert thanh toán quầy thuốc cũ, luồng settlement thay thế có test đạt) |
| Flutter analyzer | Lỗi0/cảnh báo0; còn2 info curly braces có trước ở test khác; test mới không thêm info |
| VNPT thật | Âm tính3 kiểm tra đạt,2 outbound verification calls; replay thêm0 |
| Email thật | Sáu nhóm kiểm tra đạt, ba thư Resend Delivered |
| Android debug | Assemble APK đạt; chưa nghiệm thu camera/liveness trên thiết bị Android |
| iOS Release14 | Build/ký, codesign verify strict, cài đè/mở trên iPhone thật đạt; giữ bundle/dữ liệu |
| Health cuối | Gateway, hai Identity và cầu nối iPhone200 với TLS/mTLS xác minh; Resend/outbox settings không đổi |
| `git diff --check` | Backend và mobile đạt |

Các HTTP fixture tách tiến trình/port để bật đúng bài có điều kiện; không gọi đó là bỏ qua đã nghiệm thu. Bản iPhone dùng `lib/main.dart`, không bản diagnostic Secure Enclave; bài chữ ký phần cứng riêng đã ghi trong báo cáo đợt2.

## Phân tích ảnh hưởng

Đã chạy GitNexus upstream trước sửa các symbol và báo người dùng. Settings có2 phụ thuộc trực tiếp/2 luồng, LOW. `ApiService` có20 phụ thuộc trực tiếp,64 đối tượng trong phạm vi upstream, HIGH; đã cảnh báo trước sửa và chạy lại toàn bộ Flutter cùng các HTTP fixture. Graph Dart không resolve flow cho constructor/fromJson/message; UNKNOWN/0 không coi là không có ảnh hưởng. Test `main` mới chưa có trong index lúc kiểm tra, không có caller production.

Đã analyze lại hai index với `--force --no-parse-cache --skip-agents-md --skip-skills`. `detect-changes --scope all`: backend185 file/790 symbol/522 luồng **CRITICAL**, mobile32 file/137 symbol/7 luồng **HIGH**. Đây là tổng diff đang có của nhiều đợt trước, không phải chỉ lượt VNPT này. Index còn giới hạn cross-language/flow truncation; không coi thiếu liên kết là an toàn. Các file untracked và test mới được đọc/kiểm tra riêng. Không commit hoặc phát hành toàn bộ diff này.

## Chưa đủ điều kiện kết luận production

- Chưa có bằng chứng xác minh thành công căn cước/selfie thật, điều chỉnh ngưỡng theo tenant và false accept/reject trên bộ dữ liệu được phép; không hạ kiểm tra để đạt.
- Luồng REST hiện dùng passive liveness; chưa tích hợp SDK active liveness hoặc NFC. Không tự gộp hồ sơ/cấp quyền bệnh án khi VERIFIED.
- Điều khoản lưu trữ/xử lý dữ liệu VNPT và quy trình đồng ý/xóa dữ liệu cần chốt cho phạm vi phát hành thực tế.
- Gmail render, nhập OTP/reset UI iPhone, Face ID cảm biến và khóa/passkey vật lý của đợt2 còn cần người dùng xác nhận; API/automated test không thay thế.
- Webhook Resend công khai/production chưa nghiệm thu; ba trạng thái Delivered trong lượt này được đối chiếu bằng GET read-only, không giả cập nhật webhook vào DB chính.
- Profile ký Apple thử nghiệm và CA/backend Mac có thời hạn; không phải phân phối App Store. AI tiếp tục để sau.

## Bằng chứng

Bản JSON đã loại dữ liệu riêng: [thư mục evidence](evidence/vnpt-live-20261019/). Chỉ lưu các tóm tắt Free entitlement, VNPT âm tính, availability, health, mail acceptance/delivery/cleanup; không có keys, ảnh người thật, OTP, recipient hoặc response thô. Logs build/test và cấu hình riêng nằm ngoài Git; hướng dẫn thao tác chuẩn tại [trang15](../../huong-dan-su-dung/15-vnpt-ekyc.md).

Nguồn API đối chiếu: [VNPT API](https://ekyc.vnpt.vn/admin-dashboard/vi/documents/api), [VNPT Free](https://vnptai.io/ekyc/vi/price), [Resend retrieve email](https://resend.com/docs/api-reference/emails/retrieve-email).
