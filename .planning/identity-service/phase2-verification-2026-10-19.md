# Đợt 2 — Báo cáo triển khai và kiểm tra bảo mật

Ngày đối chiếu: **19/10/2026**. Đợt này thực hiện AUTH-01–06 của kế hoạch hoàn thiện sản phẩm; VNPT eKYC và AI tiếp tục hoãn.

## Kết quả và giới hạn

Đã triển khai source, áp dụng V16 trên DB test riêng, khởi động lại hai Identity instance/Gateway và cài/mở CareFlow **1.0.0 (13)** trên iPhone 11 Pro Max. Secure Enclave trên chính iPhone đã ký được proof ES256 hợp lệ; bài kiểm tra dùng namespace test riêng, không đọc tài khoản, khóa đang dùng hoặc bí mật Face ID.

**Chưa ký nghiệm thu trọn đợt:** người dùng chưa xác nhận đăng nhập Face ID trên cảm biến, đăng ký/nhận OTP/reset hoàn chỉnh trên UI iPhone. Webhook Resend chưa có callback HTTPS công khai/signing secret trong runtime test. Passkey kiểm thử bằng CTAP2 ảo của Chrome, chưa thay cho khóa vật lý và cấp tài khoản nhân viên thật. Không coi đây là nghiệm thu production.

## Phạm vi source và runtime

| Thành phần | Phạm vi |
|---|---|
| Backend/web | Worktree `.worktrees/security-integration`, nhánh `feature/dangkhoii/security-integration`, nền `5a8c6e5`, thay đổi chưa commit |
| Mobile | Worktree `.worktrees/patient-mobile-ios-login`, nhánh `feature/dangkhoii/patient-mobile-ios-login`, nền `e5fc9c1`, thay đổi chưa commit |
| DB runtime | PostgreSQL loopback `127.0.0.1:15432`; schema Identity sao lưu riêng trước V16; không truy cập DB remote |
| DB kiểm thử | `careflow_account_lifecycle_test`, `careflow_email_recovery_test`, `careflow_vnpt_ekyc_test`, `careflow_phase2_browser_test`; tách khỏi DB người dùng |
| Triển khai | Identity 18081/18092 và Gateway 18080; TLS/mTLS kiểm tra health; cầu nối iPhone 18443 trả 200 với CA đóng gói trong app |
| iPhone | Release (13), ký bundle hợp lệ, cài đè giữ dữ liệu, `lib/main.dart` đã được phục hồi/cài/mở sau diagnostic |
| Android | APK debug build thành công, bridge Kotlin đã biên dịch; chưa thử Keystore/StrongBox trên thiết bị Android |
| Dữ liệu riêng | Giữ nguyên Resend API key và khóa AES outbox hiện có; secret, mã, DB dump và signing config nằm ngoài Git |

Đã pull `origin main` trên các nhánh cá nhân trước triển khai. Không stage/commit/push. Worktree đã có nhiều thay đổi từ các đợt trước; không đồng nhất toàn bộ diff hiện tại với đợt 2.

## Thay đổi bảo mật

### AUTH-01: đăng ký có xác minh email

- Hai endpoint `POST /api/auth/registration/email/request` và `/verify`; ngừng tạo tài khoản qua `/auth/register` cũ (410/503).
- Chưa có tài khoản dùng được khi chỉ gửi mã. Verify thành công mới insert PATIENT ACTIVE với `email_verified_at`; tài khoản demo/nội bộ cũ giữ provenance NULL, không giả lập đã xác minh.
- Challenge riêng theo mailbox, mã sáu số an toàn, BCrypt mã/mật khẩu chờ, năm phút, năm lần thử, cooldown 60 giây, năm lần gửi/email/giờ; có admission budget chung của auth.
- DPoP gắn mã với app yêu cầu; gửi lại thay request/mã cũ; row lock và unique constraints xử lý cạnh tranh. Phản hồi yêu cầu giống nhau cho email/tên đã tồn tại.
- Sau verify xóa dữ liệu chờ nhạy cảm; outbox mã hóa AES-GCM. Chưa triển khai hệ thống chống bot/phân tích gian lận đa chiều; giới hạn ứng dụng không thay thế chống DDoS ở biên.

### AUTH-02: recovery/Face ID

- Giữ ba bước: request → verify đổi mã lấy quyền reset → confirm mật khẩu mới. Verify một mình không đổi mật khẩu hoặc thu hồi phiên.
- Reset thu hồi session/refresh, Face ID credential, passkey và **mọi quyền cấp passkey đang chờ**. Đổi vai trò/trạng thái cũng hủy grant chờ, tránh dùng lại quyền cũ khi mở khóa/đổi vai trò.
- Logout thông thường giữ đăng ký Face ID độc lập; UI vẫn dùng icon cạnh mật khẩu và tài khoản nhớ sẵn. Hủy OS prompt/lỗi mạng không tự xóa đăng ký.
- Migration mất phản hồi có thể thử lại với khóa phần cứng và cùng credential; mất mạng trong nhánh phục hồi không bị hiểu nhầm thành credential bị thu hồi.
- Template HTML/text dùng thương hiệu bằng chữ và ký hiệu, không `img`, `cid` hoặc attachments. Đã kiểm tra hiển thị 320px; inbox Gmail thật còn chờ.

### AUTH-03: passkey nhân viên

- Staff không thêm/gỡ khóa qua mật khẩu chung. ADMIN phải có phiên PASSKEY xác thực trong năm phút để cấp/khôi phục.
- Kiểm tra danh tính ngoài hệ thống do người được phân công thực hiện; server lưu người cấp và mã tham chiếu. Checkbox không tự xác minh danh tính và không phải eKYC.
- Grant 15 phút, hai bí mật 256 bit: email và kích hoạt trao riêng sau kiểm tra; chỉ lưu hash. Năm lần sai, challenge WebAuthn hai phút, DPoP, kiểm tra origin/RP/signature/user verification và dùng một lần.
- Recovery thu hồi khóa/phiên cũ ngay khi cấp quyền mới. Có backup key; không gỡ khóa cuối; gỡ khóa khác thu hồi session.
- Bootstrap ADMIN đầu tiên chỉ qua operator có quyền DB, script ngoài HTTP; chặn khi đã có passkey của ADMIN ACTIVE. Mã kích hoạt chỉ ghi file mới mode 0600; email qua outbox mã hóa; audit event. Đã kiểm tra cả cấp lần đầu và từ chối bootstrap lặp khi có ADMIN key.
- Khi PATIENT được chuyển thành nhân viên, xóa passkey/challenge bệnh nhân đã có để không dùng khóa từng cấp bằng mật khẩu vượt onboarding nhân viên. Đã có ca PostgreSQL riêng xác nhận.
- Secure profile tiếp tục bắt buộc passkey. Môi trường demo dùng chung giữ ngoại lệ mật khẩu cũ đến khi có ADMIN/passkey thực tế; không bật bắt buộc sớm làm khóa toàn bộ nhân viên. Fixture riêng bật bắt buộc và đã chứng minh mật khẩu không vượt được policy.

### AUTH-04: khóa ký thiết bị

- iOS tạo P-256 trong Secure Enclave với `WhenUnlockedThisDeviceOnly`; bridge chỉ có public JWK và sign, không có API export private key. Không yêu cầu Face ID cho từng API.
- Android dùng AndroidKeyStore, ưu tiên StrongBox, có fallback Keystore và hiển thị mức bảo vệ thực tế; private key `encoded` phải NULL. Không gọi fallback phần mềm là phần cứng.
- Client có key phần mềm cũ giữ liên kết đến khi xác thực hợp lệ; endpoint `/api/auth/device-key/rotate` yêu cầu proof khóa cũ, proof khóa mới gắn cùng access token và password hoặc OS-unlocked biometric credential.
- Chỉ thu hồi session của liên kết cũ và chuyển credential đúng thiết bị; giữ credential secret và đăng ký Face ID. Release không tự sinh khóa phần mềm khi không dùng được native bridge. Lỗi keychain có thể thử lại sau mở khóa điện thoại.
- Chưa nghiệm thu restore, thay tập sinh trắc học, máy mất, thiết bị Android thật và các phiên bản Android khác.

### AUTH-05: khóa/nhàn rỗi/step-up

- Step-up năm phút trong đợt này áp dụng cấp/khôi phục/thêm/gỡ passkey nhân viên; không khẳng định mọi thao tác nghiệp vụ khác đều đã có step-up.
- Server lưu `authenticated_at` và `last_interactive_at`; refresh kế thừa cả hai. Patient idle 30 phút, staff 15 phút; JWT/introspection/refresh kiểm tra và từ chối phiên hết idle.
- Web/native heartbeat chỉ theo thao tác người dùng, throttle một phút; không gọi từ polling. Server không thể chứng minh cử chỉ vật lý từ một client đã bị kiểm soát; đây là chính sách phiên nhàn rỗi, không phải chống malware trên thiết bị.
- App che nội dung khi inactive; pause ≥60 giây khóa phiên, giữ Face ID enrollment. Hộp thoại hệ thống không gây vòng lặp khóa. Bộ đếm được khởi động cả khi widget mở với phiên đăng nhập sẵn.
- Web giám sát trên mọi route trong AuthProvider; hết phiên/đăng xuất xóa trạng thái UI ngay, thu hồi mạng tiếp tục riêng. 401/403 refresh báo hết phiên; 503 không giả thành thu hồi; phản hồi cũ không đăng xuất nhầm tài khoản vừa đổi.

### AUTH-06: email có thể vận hành

- Lưu provider message ID; phân biệt QUEUED/ACCEPTED/DELIVERED/DELAYED/BOUNCED/COMPLAINED/FAILED/EXPIRED; ACCEPTED không phải inbox/read.
- Raw-body webhook xác thực Svix HMAC, id/timestamp/chữ ký, cửa sổ năm phút, giới hạn 64KiB; event idempotent, không lưu raw nội dung/người nhận. Lỗi DB trả 503 để provider thử lại.
- Trạng thái terminal không bị callback ACCEPTED đến muộn ghi đè. Outbox retry tối đa năm lần/backoff/idempotency; challenge hết hiệu lực/thay thế không gửi mã cũ; payload bị xóa sau kết thúc.
- ADMIN có bảng tổng trạng thái và số pending quá hai phút. Chưa có paging/cảnh báo chủ động qua hệ thống giám sát bên ngoài; webhook thật chưa được cấu hình. Không ghi “DELIVERED” từ kết quả API gửi mail.

## Kiểm chứng

| Kiểm tra | Kết quả | Phạm vi thực tế |
|---|---|---|
| Maven Common/Gateway/Identity | **241/241**, không bỏ qua | 46 Common + 35 Gateway + 160 Identity; ba DB test conditional được bật, không dùng số XML cũ còn dư |
| AccountLifecyclePostgresTest | **18/18** | Race signup, cooldown/thử sai, purpose, grant, step-up, idle, migration, webhook và thay trạng thái/vai trò |
| Recovery PostgreSQL | **7/7** | Race confirm/verify, rollback, resend, unknown mailbox và hủy staff grant sau reset |
| Flutter toàn bộ | **222 đạt, 13 bỏ qua theo điều kiện** | Không có lỗi; 12 ca HTTP được chạy riêng ở dòng tiếp theo |
| Flutter HTTP fixture riêng | **12/12** | Refresh 3, recovery 4, biometric 4, medical download 1; chạy tách file vì fixture recovery/download cùng cổng 19844 |
| Ca demo cũ | **1 còn bỏ qua** | Assertion demo lịch sử tách tiền quầy thuốc; đã có ca settlement thay thế đạt, giữ skip cũ ngoài phạm vi đợt này |
| Flutter analyzer | **0 error, 0 warning** | Hai info curly-braces đã tồn tại ở test cũ, không sửa ngoài phạm vi |
| Web | **Production build đạt** | Next build và TypeScript; session-memory/expiry regression đạt |
| WebAuthn | **8 nhóm đạt** | Chrome CTAP2 ảo → Identity thật qua TLS/mTLS → PostgreSQL riêng; request UI enrollment được chuyển đến fixture, không giả kết quả xác thực |
| Chuyển khóa API | **3 nhóm đạt** | Controller/DPoP thật: proof sai, reauth sai, thu hồi key cũ, token sao chép, credential giữ qua logout |
| Web quản trị/mail | **3 nhóm đạt** | UI lấy account/mail operations thật; password ADMIN không cấp grant; cookie HttpOnly; bốn template vừa 320px không ảnh |
| iPhone phần cứng | **PASS secure_enclave** | Chữ ký ES256 được xác minh độc lập bằng public JWK; đọc lại cùng key ổn định; namespace test riêng |
| iOS Release (13) | **Build/ký/cài/mở đạt** | Sau diagnostic đã trở lại target `lib/main.dart`; giữ bundle/dữ liệu app |
| Android debug | **Build đạt** | Kotlin bridge biên dịch bằng toolchain cần thiết; chưa nghiệm thu phần cứng Android |
| Health sau rollout | **Hai Identity + Gateway UP** | DB test riêng đã backup; iPhone bridge qua CA của app trả 200 |

Test sender/local HTTP fixtures không gửi thư thật. Phiên browser riêng bật `require-staff-passkey=true`, tắt outbound Resend; đọc mã từ outbox fixture mã hóa để kiểm tra WebAuthn. Điều chỉnh timestamp chỉ dùng làm fixture kiểm tra ranh giới hết hạn trên DB tách riêng, không sửa trạng thái nghiệp vụ runtime để đánh dấu PASS.

Bằng chứng không chứa OTP/token/secret nằm trong [phase2-verification-results-2026-10-19.json](phase2-verification-results-2026-10-19.json). Log đầy đủ, build/cài đặt và fixture riêng ở `/tmp/careflow-phase2-20261019/`; cấu hình riêng không đưa lên Git. Không tính số test từ các đợt trước vào lần chạy này.

## Rà soát ảnh hưởng

Đã dùng GitNexus query/context/impact trước các sửa auth/session. Cảnh báo HIGH/CRITICAL đã thông báo: ApiService, refresh-session creation/model, shared web request/refresh và UserRepository. Query hủy grant mới không đổi các contract repository đang có. Đã chạy `git diff --check` và `gitnexus detect-changes --scope all` ở cả hai worktree. Phân tích toàn bộ tracked diff cho backend/web ở mức CRITICAL và mobile HIGH, gồm nhiều sửa tồn đọng từ trước; các hàm chung auth đã được cảnh báo trước sửa. File mới chưa tracked được đọc/kiểm thử riêng, không dùng detect-changes để khẳng định đã bao phủ chúng. Không stage/commit.

## Những phần còn chờ

1. Người dùng xác nhận Face ID trên iPhone (đăng xuất → icon Face ID → đồng ý/hủy → thử lại), và việc giữ enrollment sau chuyển khóa của tài khoản thực tế.
2. Chọn email test nhận thư; nghiệm thu đăng ký hai bước và recovery ba bước trên app (13), Gmail không có ảnh đính kèm, reset thu hồi credential cũ rồi bật lại Face ID.
3. Callback HTTPS công khai + signing secret riêng cho Resend ở cả Identity, thử event thật và thiết lập cảnh báo vận hành chủ động.
4. ADMIN/nhân viên với passkey vật lý và khóa dự phòng; diễn tập operator/recovery trước bật bắt buộc trên môi trường dùng chung.
5. Thay/restore bộ sinh trắc học, mất máy, Android thật và phát hành staging/production đồng bộ. Chưa thực hiện commit/push hay deploy production.

Mọi thao tác người dùng/E2E chỉ quản lý tại [hướng dẫn 16](../../huong-dan-su-dung/16-bao-mat-dot-2.md); báo cáo này không thay thế nguồn hướng dẫn chuẩn.
