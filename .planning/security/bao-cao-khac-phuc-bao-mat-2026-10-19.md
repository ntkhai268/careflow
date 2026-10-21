# CareFlow — Kết quả khắc phục bảo mật ngày 19/10/2026

## Kết luận và phạm vi

Đã sửa các đường xác thực, thu hồi credential, quyền hồ sơ, phiên web, file, email và cấu hình triển khai được nêu trong báo cáo gốc. Một số tính năng chưa đủ điều kiện đã được **chặn ở phía server trên production**, không được tính là đã hoàn thiện tính năng. Một số công việc cần hạ tầng/thiết bị/người quản trị vẫn còn mở.

**Cập nhật nghiệm thu cục bộ ngày 19/10/2026:** đã cập nhật 12 tiến trình backend của môi trường test trên Mac, áp migration V13/V14 trên PostgreSQL test loopback và cài/mở app Release **1.0.0 (9)** trên iPhone 11 Pro Max. 14 nhóm kiểm tra bảo mật qua API thật đạt. Web mới chưa được triển khai đồng bộ; Face ID trên cảm biến thật và email/reset hoàn chỉnh còn chờ người dùng thao tác. Chưa commit/push hoặc triển khai production; AI để sau theo yêu cầu. Xem [biên bản iPhone](iphone-security-live-2026-10-19.md).

Báo cáo gốc được giữ làm mốc trước sửa: [Đánh giá bảo mật](bao-cao-bao-mat-production-2026-10-19.md). Hướng dẫn thao tác và chuyển đổi chỉ quản lý ở [hướng dẫn 13](../../huong-dan-su-dung/13-ban-va-bao-mat-va-van-hanh.md).

## Nơi có mã sửa

| Phần | Worktree / nhánh |
|---|---|
| Bộ backend/web tích hợp để review/build | `.worktrees/security-integration` / `feature/dangkhoii/security-integration` |
| Identity/Common/Gateway phát triển gốc | `.worktrees/identity-email-recovery` / `feature/dangkhoii/identity-email-recovery` |
| Mobile native | `.worktrees/patient-mobile-ios-login` / `feature/dangkhoii/patient-mobile-ios-login` |
| Báo cáo và hướng dẫn tập trung | `.planning/security/`, `huong-dan-su-dung/` tại repository gốc, được đồng bộ tài liệu liên quan về các nhánh sửa |

Đã `git pull origin main` ở ba worktree trước khi viết mã; đều báo up to date. Không sửa `main`, không stage/commit/push. Các thay đổi local có trước được giữ lại. Bộ tích hợp được bổ sung các phần Identity email/biometric hiện hành để tránh build production với bản Identity cũ.

## Trạng thái từng phát hiện

| ID | Kết quả trong source | Phần còn lại |
|---|---|---|
| F01 | Câu hỏi trả 410 ở service cho cả đăng nhập, setup và reset; UI web/mobile bỏ thẻ câu hỏi. Secure production yêu cầu tài khoản nội bộ dùng passkey có user verification; introspection/refresh từ chối cả phiên legacy password/question. | Nghiệm thu onboarding/cấp lại passkey; không bật production trước khi tài khoản nội bộ có credential hợp lệ. |
| F02 | Reset email xóa `login_questions`, `passkeys`, challenge và thu hồi phiên/biometric trong cùng giao dịch. | Nhân viên mất passkey cần hỗ trợ xác minh; email không tự cấp lại factor. |
| F03 | API cập nhật hồ sơ đầy đủ chỉ cho tài khoản PATIENT sở hữu; STAFF/ADMIN không thể dùng PUT rỗng để lấy response lâm sàng. | Luồng chỉnh sửa hành chính theo phân công cần API/DTO riêng nếu sản phẩm muốn hỗ trợ rộng hơn. Không mở lại endpoint đầy đủ cho STAFF. |
| F04 | Bỏ ADMIN bypass trong đọc/sửa hồ sơ đầy đủ, hủy toa và cấp phát. Lặp cấp phát yêu cầu đúng nhân viên đã cấp phát. | Quyền tiếp nhận/tóm tắt hành chính quản trị còn theo nghiệp vụ hiện có; chưa có break-glass. Chưa coi toàn bộ ma trận tổ chức được duyệt. |
| F05 | AI mặc định bị chặn; không cho bật chế độ AI demo dưới profile `secure/prod/production`. | ABAC theo ca, ownership interaction, chính sách provider/PHI, prompt-injection và AI thật chưa triển khai. |
| F06 | eKYC mock chỉ trả UNVERIFIED; profile secure/prod/production trả 503. | Nhà cung cấp xác minh thật và quy trình claim danh tính chưa triển khai. |
| F07 | Manifest production tắt đăng ký công khai chưa xác minh; startup guard không cho bật lại dưới profile production. | Đăng ký có OTP và quy trình người đại diện/claim/merge hồ sơ còn mở; việc tắt không thay cho các tính năng này. |
| F08 | Nhập sai không xóa thiết bị đáng tin; credential sinh trắc học hợp lệ có thể gỡ khóa tạm, không gỡ khóa/vô hiệu hóa do quản trị. | Nghiệm thu cảm biến thật và tình huống mất thiết bị. |
| F09 | Gateway có bộ đếm nguồn được giới hạn dung lượng; không tin X-Forwarded-For. Identity có ngân sách request chung trong PostgreSQL, áp dụng trước BCrypt/proof. Dummy hash cho username không tồn tại; dọn DPoP replay chuyển sang nền. | Load test, quota theo ingress thực tế, rate limit IP phân tán và điều chỉnh cho NAT/proxy còn cần nghiệm thu. Passkey options không được coi là cơ chế giấu tên tài khoản. |
| F10 | Nâng Boot/Cloud, Next và các patch dependency; npm production audit không còn cảnh báo. Quét CycloneDX/OSV có gate trong CI. | Ba advisory Spring có điều kiện còn khớp, xem phần dependency bên dưới. Image/JDK/native SDK scan và kế hoạch nâng Boot 4/patch thương mại vẫn cần hoàn tất. |
| F11 | Thay deploy mặc định bằng workflow thủ công có test/SCA, pin SHA actions, compose secure rõ ràng. Profile production bắt buộc secure, không demo/test; seed dữ liệu demo chỉ demo/test; giữ bootstrap danh mục CLS/khóa hỗ trợ dẫn xuất idempotent. DB production bắt buộc verify-full; cấu hình file lưu bền vững. | Chưa chạy pipeline trên runner thật; chưa ký/pin digest mọi image, nghiệm thu firewall, rollback/HA và secrets thật. Directory còn bootstrap DDL/idempotent hỗ trợ từ trước; cần chuyển vào migration trước khi chốt quyền DB tối thiểu. |
| F12 | Refresh token ở cookie HttpOnly/Secure/SameSite Strict; access token chỉ trong memory; xóa token legacy; CSRF Origin check, allowlist BFF route, CSP nonce và header. Refresh được serialize giữa các tab bằng Web Locks khi hỗ trợ. | Browser E2E nhiều tab, passkey thật, cấu hình HTTPS/origin production; XSS vẫn có thể thao tác trong phiên nếu có lỗ hổng, không coi HttpOnly là triệt tiêu XSS. |
| F13 | Extension + định dạng ảnh/PDF, giới hạn pixel, bắt buộc ClamAV ở secure; scan trước lưu, reject khi scanner lỗi, safe path, không symlink, attachment/nosniff/no-store. Quota 5 file/50MB/request và 100 file/200MB/bệnh nhân có khóa DB; dọn file khi rollback. | ClamAV/signature và restore volume thật; file cũ không tự được quét lại. Chưa chuyển object storage. |
| F14 | iOS secure storage dùng ThisDeviceOnly; Android tắt backup và device transfer dữ liệu app. Có API liệt kê/thu hồi thiết bị của chính tài khoản, không trả secret. | Khóa DPoP hiện vẫn là JWK phần mềm trong secure storage, **chưa chuyển sang Secure Enclave/Keystore non-exportable**. UI quản lý đăng ký sinh trắc học đã có ở app (11); kiểm thử thao tác trên máy thật còn chờ xác nhận. |
| F15 | Refresh kế thừa hạn tuyệt đối; replay chỉ thu hồi các phiên cùng khóa client. Keyring JWT hỗ trợ khóa hiện tại và retiring để rolling rotation. | Idle timeout/step-up theo vai trò, KMS, diễn tập rotation và retry trên nhiều tab/mạng thật còn cần hoàn tất. |
| F16 | Outbox mã hóa AES-256-GCM, đưa vào cùng transaction trước commit, worker claim SKIP LOCKED, retry/backoff, idempotency provider, xóa payload khi xong/hết hạn; giữ template HTML không ảnh đính kèm. | Key outbox private, theo dõi delivery/bounce và cảnh báo vận hành. Không gửi thử tới mailbox bên ngoài trong đợt vá. |
| F17 | Không chép key vào source/tài liệu; đã yêu cầu chủ dự án thay key ở cấu hình private. | Chủ dự án yêu cầu giữ nguyên key cho đợt test này; assistant không thay/thu hồi key. Khuyến nghị xoay khóa trước production trong báo cáo gốc vẫn là hạng mục chưa thực hiện. |
| F18 | Có stream audit API với actor đã xác minh, action/path/status/correlation/thời gian; không log body/query/token. Production guard kiểm tra TLS DB và profile. | Lưu audit chống sửa/xóa, SIEM/cảnh báo, backup/PITR/restore, RPO/RTO, retention/consent/quản trị nhà cung cấp vẫn cần hạ tầng/quy trình thật. |

## Các thay đổi ảnh hưởng trải nghiệm

- Câu hỏi bí mật bị ngừng hỗ trợ có chủ đích. Client cũ sẽ nhận 410; không thể chỉ ẩn UI và để API yếu còn cấp phiên.
- STAFF không còn chỉnh hồ sơ đầy đủ bằng endpoint cũ. Đọc/ghi trường lâm sàng không được cấp cùng quyền tiếp nhận hành chính.
- Reset email thu hồi mọi phương thức đăng nhập cũ; phải bật lại Face ID/passkey. Điều này khác với đăng xuất phiên thông thường.
- Tài khoản nội bộ production cần passkey đã được cấp; đã chặn đường password-only. Không triển khai policy này khi chưa nghiệm thu onboarding và khôi phục cho nhân viên.
- Người dùng web có thể cần đăng nhập lại sau nâng cấp vì token localStorage bị dọn. Cookie cần đúng HTTPS/origin.
- File Word không còn được nhận; cần chuyển PDF. Upload bị từ chối khi scanner không sẵn sàng, không lưu file rồi báo thành công giả.
- Đăng ký công khai, AI và eKYC mock không được mở cho production. Môi trường demo có thể dùng các tính năng thử nghiệm theo cấu hình riêng; guard ngăn trộn demo/test với production.

## Migration, cấu hình và dữ liệu

Migration mới **V13__auth_abuse_and_recovery_outbox.sql** và **V14__session_authentication_method.sql** thêm rate window, mail outbox, `refresh_tokens.absolute_expires_at` và phương thức xác thực phiên. V14 cố ý gán PASSWORD cho phiên cũ; không được tự nâng phiên legacy thành PASSKEY. Backfill giữ hạn cũ; trigger hỗ trợ các writer cũ khi rolling upgrade chưa điền cột mới. DBML đã cập nhật. Không seed OTP, credential hoặc mail outbox mẫu.

Mail outbox cần file key private chứa Base64 32 byte, cấu hình `RECOVERY_OUTBOX_KEY_FILE`; sender đã cấu hình mà thiếu key sẽ không khởi động. Payload outbox được mã hóa, ràng buộc ID hàng làm AAD; chỉ worker giải mã trong memory. Mail CODE hết hạn 5 phút, thông báo đổi mật khẩu 24 giờ; payload được xóa khi sent/failed/expired, metadata dọn sau thời hạn ngắn. Không xoay/mất khóa khi còn payload chờ.

Manifest secure giữ remote DB mặc định nhưng thêm `sslmode=verify-full` và mount CA riêng. Nếu URL private override thiếu xác minh TLS, production guard từ chối startup. Đã áp V13/V14 và khóa outbox riêng trên runtime test Mac dùng DB loopback; không áp vào DB production/remote. TLS/mTLS giữa các tiến trình test và CA đóng gói cho app đã kiểm tra; cấu hình production và CA DB vẫn chưa nghiệm thu.

## Phân tích ảnh hưởng và kiểm thử

GitNexus đã được dùng trước thay đổi và trước tích hợp. Các điểm HIGH/CRITICAL đã được báo cho người dùng: policy đọc hồ sơ, các hàm cấp/thu hồi phiên và API web chung. `request` phía web có 139 symbol/26 nhóm luồng liên quan; policy hồ sơ tác động cả file/booking. Có thêm 103 phép phân tích symbol trước đồng bộ Identity vào bộ tích hợp. `detect-changes` trên toàn bộ trạng thái dirty báo 175 file/655 symbol/527 flow, mức CRITICAL; số này gồm nhiều thay đổi có trước đợt vá, không được quy hết cho lần sửa này. Kết quả đầy đủ tạm ở `/tmp/careflow-hardening-20261019/`.

### Kết quả chạy

| Kiểm tra | Kết quả |
|---|---|
| Toàn bộ Maven reactor, có PostgreSQL riêng cho recovery/biometric | **736 ca đạt, 0 skip/failure/error; BUILD SUCCESS** |
| Native Flutter full suite | 196 đạt, 12 skip; không có ca thất bại |
| Flutter analyze | 0 error/warning; 2 thông báo style có trước trong test không thuộc phần sửa |
| Next production build / TypeScript | Đạt |
| BFF/CSP qua HTTPS fixture | 8 nhóm kiểm tra đạt, không dùng token/dữ liệu thật |
| Phiên web trong memory | Dọn legacy, race đổi tài khoản/đăng xuất và single-flight refresh đạt |
| npm audit production | 0 advisory |
| CycloneDX/OSV gate | 304 tọa độ, 3 advisory có điều kiện; gate đạt với điều kiện được ghi rõ |
| Compose secure syntax | Đạt với giá trị mẫu, không khẳng định secrets/CA thật hợp lệ |
| Git diff whitespace checks | Đạt ở các worktree sửa |


Các kiểm thử backend chạy trên JDK 25, target Java 21; workflow được cấu hình JDK 21 nhưng chưa chạy trên runner thực. Test PostgreSQL dùng DB riêng `careflow_email_recovery_test` trên loopback; không sử dụng DB runtime hoặc dữ liệu bệnh nhân thật. Suite dùng sender ghi nhận trong test, không gửi thư bên ngoài.

BFF/CSP được kiểm tra qua bản Next production chạy local và Gateway HTTPS fixture có CA được xác minh: sai Origin 403, action lạ 404, refresh cookie HttpOnly/Secure/Strict, token không có trong JSON response, refresh lấy đúng cookie, logout xóa cookie, CSP nonce khớp HTML và không unsafe-eval ở production. Đây không thay cho browser E2E với backend thật.

Test mới bao phủ: endpoint câu hỏi 410; staff password không được cấp phiên khi policy bật; username lạ vẫn qua hash; fail password không revoke thiết bị; reset xóa credential; giới hạn absolute expiry; chồng khóa JWT; chống giả X-Forwarded-For; quyền PUT rỗng/ADMIN; nội dung file giả, scanner lỗi, traversal/symlink; quota có khóa DB và guard production; ADMIN không hủy/cấp phát và STAFF khác không đọc lại toa qua thao tác cấp phát lặp.

### Dependency

- Next **16.4.2**; `npm audit --omit=dev --package-lock-only`: **0 advisory** tại thời điểm chạy.
- Boot **3.5.16**, Cloud **2025.0.3**, springdoc **2.8.17**. Patch levels của Jackson/Tomcat/Netty/AMQP/HTTP client/PG/crypto và các BOM liên quan được chốt ở parent POM và đã chạy reactor test; có SCA từ SBOM để phát hiện drift.
- CycloneDX SBOM có **304 tọa độ Maven** được đối chiếu OSV. Sau patch, còn **4 package-advisory matches, tương ứng 3 advisory duy nhất** của Spring MVC/WebFlux 6.2.19. Không báo thành “0 cảnh báo Maven”.
- OSV gán CRITICAL cho các mục này, nhưng [CVE-2026-47890](https://spring.io/security/cve-2026-47890/) được Spring xếp LOW; [CVE-2026-47884](https://spring.io/security/cve-2026-47884/) và [CVE-2026-47892](https://spring.io/security/cve-2026-47892/) được Spring xếp MEDIUM. Điều kiện liên quan là SSE view fragments, XsltView và WebFlux functional endpoint/DispatcherServlet. Source CareFlow hiện không dùng các bề mặt đó; `check_backend_surface.py` chặn việc bổ sung khi còn baseline này.
- File `dependency-conditions.json` ghi từng điều kiện và nguồn chính thức, hết hạn **18/11/2026**. Gate sẽ thất bại nếu hết hạn mà advisory vẫn HIGH/CRITICAL theo OSV, hoặc nếu thêm bề mặt bị chặn. Đây là triage kỹ thuật có điều kiện, chưa thay cho nghiệm thu release/hạ tầng. Bản vá Spring 6.2.20 trong advisory thuộc hỗ trợ thương mại; phương án nguồn mở là nâng Spring 7/Boot 4, chưa thực hiện trong đợt này.

## Điều kiện còn chặn công bố production

1. Đợt test này giữ nguyên key Resend theo yêu cầu chủ dự án; outbox key riêng đã cấu hình trên Mac. Gửi/nhận email thực tế và quyết định quản lý khóa khi công bố production còn chưa nghiệm thu.
2. Hoàn thành/nghiệm thu cấp passkey và khôi phục tài khoản nội bộ; xác nhận việc tạm tắt đăng ký/AI/eKYC phù hợp phạm vi release.
3. Kiểm tra các advisory có điều kiện, chọn kế hoạch nâng framework/patch phù hợp; quét image/native SDK và đúng artifact cuối.
4. Deploy staging với TLS/mTLS/CA DB thật, ClamAV, volume, firewall, secrets, backup/restore và giám sát; có rollback được diễn tập.
5. Đã cài/mở app (9); còn cần xác nhận Face ID/vân tay, backup/restore và thu hồi trên điện thoại thật. UI quản lý đăng ký thiết bị đã có ở app (11); khóa phần cứng non-exportable còn chưa triển khai.
6. Hoàn thiện ma trận quyền và E2E cho các vai trò trên runtime mới; quản lý đại diện hồ sơ, retention và yêu cầu bảo vệ dữ liệu thực tế.

Không nên chuyển các mục này thành trạng thái “đã xong” chỉ bằng test unit hoặc cấu hình mẫu. Source đã được làm cụ thể để review và chạy nghiệm thu; báo cáo giữ rõ các giới hạn còn lại.

## Bằng chứng lưu cùng báo cáo

- Tóm tắt kết quả máy đọc được: [security-hardening-results-2026-10-19.json](security-hardening-results-2026-10-19.json).
- Log chi tiết/SBOM/fixture cục bộ: `/tmp/careflow-hardening-20261019/`; không cam kết thư mục tạm tồn tại lâu dài. Chỉ chia sẻ log thô sau khi rà dữ liệu nhạy cảm.
- SBOM được sinh bằng CycloneDX 2.9.1; dependency gate và kiểm tra bề mặt Spring nằm tại `scripts/security/` của nhánh tích hợp. Mốc ngoại lệ và nguồn chính thức nằm trong `.planning/security/dependency-conditions.json` của nhánh đó.
