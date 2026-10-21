# Thành viên A — kết quả sửa và kiểm thử bảo mật

Ngày thực hiện: 2026-10-15, Asia/Ho_Chi_Minh. Phạm vi: yêu cầu kiểm tra và sửa cơ chế đăng nhập, phiên phân tán, truy cập trực tiếp service, service identity, token binding và xác thực bổ sung. User đã cho phép tiếp tục sửa các lỗi HIGH/CRITICAL. Không tự stage/commit/push/merge.

## Kết luận có bằng chứng

Nhóm lỗi phiên/quyền và luồng API của thành viên A đã được sửa trên nhánh tích hợp và kiểm thử với server, PostgreSQL, RabbitMQ và Chrome thật. Runtime được nâng cấp sang HTTPS/mTLS. Đây là kết quả audit cục bộ; không tuyên bố toàn hệ thống production đã hoàn tất hoặc an toàn tuyệt đối.

| Yêu cầu | Kết quả hiện tại |
|---|---|
| Gateway biết phiên active; service khác xác minh ra sao? | Identity DB là nguồn phiên duy nhất. Gateway và từng service tự xác minh online; header user không tự cấp quyền. |
| Gọi trực tiếp service/giả header | Cổng riêng yêu cầu mTLS; qua được transport vẫn phải có JWT/DPoP hoặc delegation có scope, rồi role/ownership/phân công. Chín service đã được gọi trực tiếp với header giả và từ chối. |
| Request hợp lệ được route thế nào? | Login qua Gateway tới Identity; request nghiệp vụ qua Gateway tới resource. Private HTTPS discovery được cấu hình rõ; Gateway không giữ private JWT signing key. |
| Khi nào từ chối? | 401 với token/proof/phiên sai; 403 với quyền/ownership/phân công/scope sai; 503 khi không xác minh được. Request bị từ chối không được ghi nghiệp vụ. |
| Nhiều instance | Hai Identity JVM dùng chung session/replay DB; replay, thu hồi và lockout đồng thời được kiểm thử. Chưa có kiểm thử hai máy vật lý hoặc HA failover. |
| Token bound | JWT/refresh gắn public-key thumbprint; P-256 non-extractable key tại browser; proof theo method/path/time/nonce/token hash; chống replay trong DB. Copy token sang điện thoại có khóa khác bị từ chối. |
| Thu hồi | Logout/refresh/logout-all/lock/disable/đổi role làm phiên cũ mất hiệu lực, không sống lại khi mở khóa/đổi lại role. WebSocket cũ bị đóng. |
| Ngoài password | Đã triển khai TOTP thật, secret mã hóa, mã recovery một lần, password reauthentication, lockout và audit events. MFA hiện opt-in. |
| Đặc điểm cá nhân/câu hỏi bí mật | Chưa có passkey/Face ID/vân tay; không thay thế bằng mô phỏng. Không thêm câu hỏi bí mật làm yếu tố xác thực/khôi phục. eKYC cũ vẫn công khai `mock:true`, không cấp quyền và không được tính là xác thực thật. |

## Những thay đổi chính

- **Identity**: RS256 thay shared-HMAC, issuer/audience/kid chặt chẽ, private key chỉ ở Identity; không fallback. JWT `jti` gắn phiên DB và `cnf.jkt` gắn khóa client. Refresh/logout xác minh cùng khóa; sai khóa không được thu hồi phiên người khác. Chống replay dùng bảng DB chung, kiểm tra role/status hiện tại và account lock đồng thời.
- **MFA**: RFC 6238 TOTP 6 số/30 giây, secret AES-256-GCM gắn user, pending setup 10 phút, 10 recovery codes ngẫu nhiên 144 bit chỉ lưu hash và dùng một lần. TOTP counter không được dùng lại; 5 lần sai bị khóa 15 phút. Bật/tắt MFA yêu cầu password, ghi security event và thu hồi phiên. Flyway V8 đã áp dụng lên Identity audit DB; DBML được cập nhật theo V7/V8.
- **Common/Gateway/services**: xác minh phiên độc lập, credential riêng có scope, mTLS certificate CN khớp caller, TLS hostname/CA validation. Public headers bị xóa; delegated request vẫn giữ user authorization. Các JVM không giữ signing/MFA secret nếu không phải Identity. Không tiếp tục nghiệp vụ khi authority lỗi.
- **Notification**: CONNECT có proof, xác minh trước MESSAGE và quét idle socket mỗi giây. Logout, đổi quyền, disable, hết hạn hoặc Identity outage đóng socket đang mở. Mapping nhiều hồ sơ của cùng tài khoản và thông báo thật đã hồi quy.
- **Các sửa nghiệp vụ A được tích hợp**: prescription consultation lookup fail-closed, role/ownership/phân công Health Records/Consultation/EMR/Prescription, tiếp nhận staff không có user account, idempotency và bộ đếm hàng chờ an toàn giữa hai JVM, phân biệt người tạo với chủ hồ sơ.
- **Mobile emulator**: khóa riêng mỗi điện thoại/môi trường trong IndexedDB, REST/refresh/retry và STOMP ký proof thật. Giao diện bật TOTP và nhận recovery codes, login có yếu tố bổ sung. Proxy hỗ trợ CA riêng và HTTPS/WSS, không tắt xác minh TLS. Dataset ban đầu giữ nguyên.
- **Hospital Web**: browser proof, refresh rotation, tải lại trang xác minh `/api/auth/me` thật, logout thu hồi phía server, nhận TOTP/recovery, bỏ nhánh login giả và log chứa token. Production build đã kiểm thử Chrome.
- **Deployment**: `application-secure.yml`, shared secure configuration và compose riêng. Chỉ publish Gateway, cert/credential riêng mỗi service, private signing key chỉ mount Identity, secure profile tắt known-password demo initializers. Compose mới chỉ validate cấu hình; chưa triển khai container stack.

## Kiểm thử đã chạy

| Nhóm | Kết quả | Bằng chứng cục bộ |
|---|---|---|
| Maven toàn reactor, lần chạy cuối | **597 tests**, 0 failures/errors/skips, exit 0 | `/tmp/careflow-security-reactor-acceptance.log`, từng `target/surefire-reports/` |
| Emulator unit / network integration | **28 + 9**, 0 lỗi | `/tmp/careflow-security-mobile-unit-final.log`, `/tmp/careflow-security-mobile-integration-final.log` |
| HTTPS/mTLS, JWT binding, caller scopes, MFA | **46 ca** | `/tmp/careflow-security-primary-http.json` |
| Vòng đời, đa instance, trực tiếp service, Identity outage | **43 ca** | `/tmp/careflow-security-lifecycle.json` |
| Hồi quy nghiệp vụ A, HTTP/STOMP/DB thật | **114 ca** ở lần chạy cuối, 0 findings | emulator `.runtime/member-a-repair-results.json`, `/tmp/careflow-security-member-a-final.log` |
| Dataset thật | **25 request** | emulator `.runtime/mobile-dataset-verification.json` |
| Booking/check-in từ chối/cancel/session thật | **23 ca** | emulator `.runtime/live-api-results.json` |
| Chrome: enrollment/recovery/bound keys/STOMP | **8 ca** | emulator `.runtime/security-browser-results.json` |
| Chrome: ba điện thoại, đúng trạng thái và chủ hồ sơ | **7 ca** | emulator `.runtime/dataset-browser-results.json` |
| Chrome: Hospital Web production login/reload/logout | **4 ca** | `/tmp/careflow-security-hospital-browser.json` |

Tổng check nghiệp vụ A bao gồm cả request polling inbox nên có thể thay đổi giữa các lần chạy (115 rồi 114). Danh sách assertion PASS được in ở hai lần giống nhau; lần cuối 0 FAIL và 0 findings. Không cộng tất cả thành “HTTP requests”: một số là WebSocket, DB assertion, browser hoặc kiểm tra trạng thái. Unit/integration fixture của proxy không được dùng làm bằng chứng backend thật.

Các tình huống xấu đã kiểm tra: token bị lấy nhưng thiếu proof, khóa khác, sai method/URI/ath, proof lặp giữa hai Identity, sai client certificate CN, caller hợp lệ thiếu scope, giả private headers qua Gateway, refresh/logout sai khóa, OTP sai/thiếu/dùng lại, recovery dùng lại, mã MFA sai đồng thời, thu hồi/tắt/mở tài khoản và expiry thật.

Outage có kiểm soát: dừng Identity audit chính → Gateway và Patient trả 503, socket idle bị đóng; phục hồi Identity → token còn hợp lệ dùng lại được. Identity đã phục hồi. Probe expiry/Queue concurrency đã được dừng sau test. Không dừng service/DB remote.

Lần kiểm tra runtime cuối: **12 JVM HTTPS** health 200, emulator `/` và `/mobile/` HTTP 200, health proxy reachable=true. Identity/Patient từ chối TLS không có client certificate. Tất cả embedded Common cùng một hash; bytecode service và Common trong 12 JAR đang chạy trùng final build. Leaf TLS audit 90 ngày, có helper renewal; không coi CA audit là CA production.

## GitNexus Impact Assessment

Đã query/context/impact trước khi sửa symbol; bằng chứng `/tmp/careflow-security-hardening-impact/`. Cảnh báo CRITICAL đã gửi trước sửa RefreshTokenService.revoke và Hospital Web request API; user đã cho phép sửa bảo mật.

| Symbol/phạm vi | Risk | Phạm vi quan sát |
|---|---|---|
| RefreshTokenService.revoke | CRITICAL | 3 direct dependants, 6 execution flows |
| Hospital Web request | CRITICAL | 5 direct, 31 flows, 9 modules, 116 symbols |
| Mobile API | MEDIUM | 12 direct callers |
| JwtService issue/parse; AuthService lifecycle | LOW theo graph | 1–2 direct, luồng login/refresh/logout; security impact thực tế lớn hơn số caller |
| ActiveSessionClient.verify | LOW theo graph | Consumer thật gồm Gateway và mọi protected servlet service |
| Notification authenticate | LOW | 2 direct callers, CONNECT và socket guard |
| Spring filter/initializer và proxy test helper | UNKNOWN | Framework/test entrypoints; không suy diễn là unused, kiểm tra HTTP và source bổ sung |

Index tích hợp: **14,338 nodes / 32,621 edges / 745 flows**. Detect-changes ban đầu sau tích hợp: **95 tracked files, 387 symbols, 455 processes, CRITICAL**; thêm tài liệu/config không đổi kết luận phạm vi. Graph cảnh báo giới hạn trace và inference liên ngôn ngữ: không có edge không có nghĩa không có caller. Các file mới chưa tracked được kiểm tra source riêng; danh sách SHA-256 và tổng hợp test đã lọc token/secret trong [evidence JSON](member-a-hardening-evidence.json). Final detect-changes: **101 tracked files, 381 mapped symbols, 455 processes, CRITICAL**, lưu `/tmp/careflow-security-integration-changes.json`. Số mapped symbols thay đổi sau re-index; không đồng nghĩa thu hẹp kiểm thử. FTS/BM25 không tạo được ở lần index cuối, graph/impact/detect-changes vẫn chạy được; truy vấn không có kết quả không được xem là bằng chứng không có phụ thuộc.

## Trạng thái mã và cách dùng

- Canonical source: `/Users/dangkhoii/careflow/.worktrees/security-integration`, nhánh **`feature/dangkhoii/security-integration`**, base develop `5a8c6e5`.
- Root `/Users/dangkhoii/careflow` vẫn **develop**. Các sửa Doctor Web riêng của user và các nhánh/worktree khác được giữ. Runtime emulator/Hospital Web lấy source từ nhánh tích hợp, không tuyên bố security v2 đã merge vào develop.
- Mở **http://localhost:3000** để dùng điện thoại giả lập và **http://localhost:5173/login** cho Hospital Web. Gateway là **https://localhost:18080**; HTTP 8080/18080 không còn là địa chỉ audit đúng.
- Dataset giữ tài khoản `mobile_case01`, `mobile_case02`, `mobile_case03`, mật khẩu `Password123@`; đây là audit demo. Không tự bật MFA cho chúng.
- Không stage/commit/push/merge các sửa đổi bảo mật trong đợt này. Key/password thật nằm ngoài Git. Root develop không bị sửa bởi integration build.

## Giới hạn và việc vận hành còn lại

Chưa có xác thực sinh trắc/passkey thật hoặc câu hỏi bí mật. MFA hiện opt-in; chưa áp chính sách bắt buộc tài khoản đặc quyền. eKYC MVP mock không đáp ứng định danh thật. Đã kiểm tra hai JVM cùng host, chưa phải hai máy/vùng mạng. Audit RabbitMQ/PostgreSQL loopback không là bằng chứng transport encryption remote; compose chưa deploy. Production cần DB/Rabbit TLS + ACL thực, CA/key rotation/backup, first-admin provisioning, firewall, public endpoint abuse/rate controls, HA/load/partition tests và kiểm tra bảo mật độc lập. DPoP không giải quyết XSS trong cùng origin. Xem [runbook](security-runbook.md) để cấu hình và tái chạy.
