# Đợt 2 — Email đăng ký, Face ID, passkey nhân viên và khóa thiết bị

Mốc source: 19/10/2026, hai nhánh cá nhân `feature/dangkhoii/security-integration` (backend/web) và `feature/dangkhoii/patient-mobile-ios-login` (native), thay đổi chưa commit. Đây là mốc lịch sử đợt 2; VNPT đã ngừng ở bản (15), thay bằng đọc trên máy và đối chiếu trực tiếp; xem [trang15](15-doc-giay-to-va-doi-chieu.md). AI tiếp tục để sau.

**Phạm vi nghiệm thu:** các kiểm tra tự động được ghi trong [báo cáo kỹ thuật](../.planning/identity-service/phase2-verification-2026-10-19.md). Bản iPhone 1.0.0 (13) đã build/ký/cài đè và mở. Bài kiểm tra Secure Enclave trên máy thật dùng khóa test riêng đã xác minh chữ ký ES256; không kiểm tra thay cảm biến Face ID. Bổ sung nghiệm thu 19/10 bản (14): ba thư thật đăng ký/reset/thông báo được Resend báo Delivered; mã sai, mã dùng lại và phiên cũ bị chặn trên tài khoản thử trong DB riêng. Hiển thị Gmail, luồng UI iPhone, cảm biến Face ID và passkey vật lý vẫn cần người dùng xác nhận. Xem [biên bản bổ sung](../.planning/identity-service/vnpt-ekyc-live-verification-2026-10-19.md). Môi trường test trên Mac dùng DB loopback riêng; không triển khai lên DB remote hay production.

**R1 — 20/10/2026:** trước cấp passkey, tạo tài khoản và phân công theo [trang17](17-tai-khoan-va-quan-tri-r1.md). Tạo/khóa/đổi quyền tài khoản nội bộ yêu cầu ADMIN xác thực passkey trong5 phút. Bản native vẫn là16; nghiệm thu cảm biến/camera thật chưa đóng.

## Đăng ký bệnh nhân bằng email

Điều kiện: mobile kết nối máy chủ mới, Resend và khóa mã hóa outbox đã cấu hình riêng, có email nhận được thư.

1. Ở Đăng nhập, chọn đăng ký; nhập tên đăng nhập, email và mật khẩu mới đủ yêu cầu.
2. Chọn **Gửi mã xác minh email**. App chuyển sang bước nhập mã; chưa có tài khoản dùng được ở bước này.
3. Mở email từ **CareFlow <careflow@supersignal-resend.id.vn>**, lấy mã sáu số; trở lại app và nhập mã trong năm phút.
4. Chọn **Xác minh và tạo tài khoản**. Máy chủ xác minh mã rồi mới tạo tài khoản PATIENT và app đăng nhập.
5. Gửi lại sau ít nhất 60 giây, tối đa năm lần mỗi email/giờ. Mã/yêu cầu trước đó hết hiệu lực. Sai năm lần hoặc quá hạn phải yêu cầu mã mới theo hạn mức.

Email/tên đã tồn tại nhận cùng thông báo công khai; không tạo thêm tài khoản. Dùng Quên mật khẩu hoặc tên/email khác. Mã gắn với app đã yêu cầu, không chuyển sang trình duyệt/điện thoại khác để xác minh. Thông tin đăng ký chờ chỉ nằm trong bộ nhớ app và bản ghi bảo mật; đóng app có thể cần bắt đầu lại. Tài khoản demo/nội bộ cũ không bị gắn nhãn giả là đã xác minh email. API đăng ký một bước cũ không còn tạo tài khoản (410 hoặc 503 nếu tắt đăng ký).

## Quên mật khẩu và Face ID trên iPhone

Điều kiện: một tài khoản test của bạn với email kiểm soát được; mật khẩu demo công khai chỉ xem ở [trang 01](01-tai-khoan-va-du-lieu.md). Không đưa mã hay mật khẩu vào chat/báo cáo.

1. Quên mật khẩu: nhập email → gửi mã → nhập mã và **xác minh** → mới xuất hiện ô mật khẩu mới/xác nhận → đặt lại. Chỉ xác minh mã chưa thay mật khẩu hay thu hồi phiên.
2. Thử mã sai, mã hết hạn, mã cũ sau gửi lại và mã đã dùng: đều không được chuyển tới bước đặt mật khẩu. Không gửi lại liên tục khi đang trong thời gian chờ.
3. Sau đặt lại thành công, mật khẩu cũ, refresh token, passkey và đăng ký Face ID cũ phải bị thu hồi. Đăng nhập bằng mật khẩu mới rồi bật lại Face ID nếu muốn.
4. Bật Face ID trong bảo mật tài khoản bằng mật khẩu hiện tại. Đăng xuất thông thường → tài khoản được điền sẵn và có icon Face ID cạnh mật khẩu → xác nhận trên iPhone để vào lại. Hủy hộp thoại vẫn giữ thiết lập, không đăng nhập.
5. Khi nâng cấp app, lần đăng nhập bằng mật khẩu hoặc Face ID hợp lệ chuyển khóa ký cũ sang Secure Enclave, giữ đăng ký Face ID. Mất mạng có thể phải thử lại; không cần tắt/bật lại chỉ vì chuyển khóa.
6. Vào tài khoản → thiết bị để xem loại khóa: iPhone hiển thị Secure Enclave; Android hiển thị mức thực tế StrongBox/TEE/Android Keystore. Không mô tả mọi Android là có phần cứng bảo mật.

Email dùng chữ CareFlow trong nội dung, không đính kèm file logo/CID. Kiểm tra inbox và mục đính kèm trên Gmail; kiểm thử template không thay thế kiểm tra thư nhận thật. Face ID vẫn dùng hộp thoại và kho bí mật của iOS; ứng dụng không nhận ảnh khuôn mặt hay mẫu vân tay.

## Khóa ứng dụng và phiên nhàn rỗi

- Rời app: nội dung được che khi chuyển ứng dụng. Quay lại trong 60 giây giữ phiên; từ 60 giây khóa phiên cục bộ, vẫn giữ đăng ký Face ID. Chỉ hộp thoại xác thực hệ thống ở trạng thái inactive không làm khóa vòng lặp.
- Không thao tác: bệnh nhân 30 phút; nhân viên 15 phút. Đăng nhập lại để tiếp tục. Mật khẩu cũng bị khóa nhàn rỗi, không chỉ phiên có Face ID.
- Trên web, chuột/phím/chạm/cuộn của người dùng mới gia hạn hoạt động. Polling và refresh nền không giữ phiên sống. Máy chủ áp dụng cùng thời hạn nên sửa đồng hồ/bỏ bộ đếm UI không vượt được giới hạn server.
- Máy chủ thu hồi hoặc xác nhận phiên hết hạn: app/web trở về đăng nhập. Lỗi mạng đơn thuần không được hiểu là thu hồi đăng ký Face ID.

## Cấp passkey nhân viên lần đầu

Điều kiện: web localhost hoặc HTTPS, trình duyệt hỗ trợ passkey, email nhân viên đã kiểm tra đúng; ADMIN có phiên passkey xác thực trong năm phút. Chỉ PATIENT còn dùng màn thiết lập passkey bằng mật khẩu. Khi nâng PATIENT thành nhân viên, passkey/challenge bệnh nhân cũ bị hủy; cần cấp khóa theo quy trình nhân viên bên dưới.

1. ADMIN vào **Quản trị → Bảo mật nhân viên** (`/admin/security`), tìm tài khoản từ Identity API thật.
2. Xác minh trực tiếp danh tính theo quy trình tổ chức, nhập mã tham chiếu hồ sơ kiểm tra, xác nhận rồi cấp quyền. Không ghi giấy tờ hay thông tin nhạy cảm vào mã tham chiếu.
3. Email gửi một mã cấp quyền; trang ADMIN chỉ hiện **mã kích hoạt riêng** một lần. Trao mã này sau kiểm tra danh tính qua kênh riêng, không gửi cả hai trong cùng email.
4. Nhân viên mở `/staff-enrollment`, nhập mã email và mã kích hoạt, xác nhận passkey trong hộp thoại hệ điều hành/trình duyệt. Hai mã hết hạn sau 15 phút; sai năm lần không dùng tiếp được.
5. Thiết lập xong chưa tự cấp phiên. Nhân viên vào `/login/methods`, chọn passkey và đăng nhập bằng khóa vừa tạo.
6. Trong bảo mật tài khoản (`/account/security`), đăng nhập passkey trong năm phút rồi thêm khóa dự phòng. Không thể gỡ khóa cuối cùng; gỡ khóa khác kết thúc các phiên và yêu cầu đăng nhập lại.

Khôi phục khi mất passkey: ADMIN khác kiểm tra danh tính, chọn **Khôi phục**, xác nhận hậu quả. Thao tác này thu hồi ngay các phiên/khóa cũ rồi gửi quyền mới theo hai kênh. Reset mật khẩu email của nhân viên cũng thu hồi passkey và quyền cấp khóa đang chờ; đổi vai trò/trạng thái hủy quyền cấp khóa đang chờ; email hay mật khẩu mới một mình không đủ cấp lại khóa.

### Khởi tạo ADMIN đầu tiên — thao tác vận hành

Không có endpoint HTTP bỏ qua xác thực. Operator có quyền DB dùng `scripts/security/bootstrap-staff-passkey.py`; cần `psql`, Python `cryptography`, cấu hình PG* riêng và **đúng khóa outbox hiện có**, file mode 0600. Không truyền mật khẩu DB trên dòng lệnh hay tạo khóa outbox khác.

```sh
python3 scripts/security/bootstrap-staff-passkey.py \
  --user-id '<UUID tài khoản ADMIN có sẵn>' \
  --identity-reference '<mã hồ sơ kiểm tra danh tính>' \
  --outbox-key-file '<file khóa outbox hiện có, ngoài Git>' \
  --activation-file '<đường dẫn file mới, ngoài Git>'
```

Script chỉ cho phép khi chưa có passkey của ADMIN đang hoạt động, không xóa khóa/phiên cũ, ghi audit, xếp email mã hóa và ghi mã kích hoạt ra file mới 0600. Operator trao mã riêng; ADMIN làm bước 4–6 phía trên và có khóa dự phòng. Xóa file chứa mã khi dùng xong/hết hạn. Không seed passkey hay grant dùng được.

**Trước bật bắt buộc passkey:** thử đăng nhập với ADMIN và nhân viên, có khóa dự phòng và quy trình khôi phục đã diễn tập. Secure profile yêu cầu passkey; stack demo cục bộ giữ ngoại lệ mật khẩu cũ cho đến khi khóa thật được cấp. Không coi ngoại lệ demo là cấu hình phát hành; tuyệt đối không bật bắt buộc trước khi có ADMIN sử dụng được passkey.

## Theo dõi email và thử lại

ADMIN xem tổng trạng thái gửi tại `/admin/security`. `ACCEPTED` là Resend nhận yêu cầu; `DELIVERED` là sự kiện giao thư, không khẳng định người nhận đã đọc/inbox. Bounce, complaint, delay và failed được hiển thị riêng. Hàng đợi mã hóa tự thử tối đa năm lần với backoff; mã cũ/hết hạn không tiếp tục gửi.

Để nhận trạng thái thật, cấu hình Resend webhook đến URL HTTPS công khai `/api/auth/mail/events`, chọn các event sent/delivered/bounced/complained/failed/suppressed/delivery_delayed, lưu signing secret riêng thành `RESEND_WEBHOOK_SECRET` ở cả Identity instance. Không đưa secret lên Git. Endpoint kiểm tra chữ ký và thời gian trước lưu event, chống ghi trùng; lỗi DB trả 503 để nhà cung cấp thử lại. Stack chỉ chạy localhost chưa có callback công khai, nên trạng thái giao thư thật còn chờ cấu hình/kiểm tra.

Thử lại quy trình bằng email test khác hoặc sau cooldown; dùng một tài khoản test riêng cho reset vì hành động sẽ thu hồi đăng ký hiện có. Ghi PASS/FAIL, bản app, thời gian và thông báo nhìn thấy; không ghi OTP/token/mật khẩu.

[Quay về mục lục](README.md)
