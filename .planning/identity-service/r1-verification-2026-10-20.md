# R1 — Biên bản triển khai tài khoản và quản trị, 20/10/2026

**Kết quả:** đã triển khai và kiểm thử phần tài khoản/quản trị, phân công/passkey và hiển thị lỗi bệnh sử trên máy chủ test Mac. R1 chưa đóng nghiệm thu toàn bộ: cảm biến Face ID, luồng email/UI iPhone, camera/giấy tờ thật và passkey nhân viên vật lý còn chờ thao tác trực tiếp. Không coi unit test hay Chrome authenticator ảo là nghiệm thu các phần đó.

## Kết quả theo kế hoạch

| Mã | Kết quả và bằng chứng | Trạng thái |
|---|---|---|
| R1-01 | Danh sách/tạo/khóa/mở/vô hiệu hóa lưu tại Identity; idempotency, audit, self-guard và kiểm tra ADMIN sau khi chờ khóa; Directory kiểm tra đúng user ID/role/status | Đạt kiểm thử code và triển khai test; tạo/khóa qua UI với DB riêng; đọc/quyền/lỗi qua runtime Gateway thật |
| R1-02 | Tiếp tục phân công còn dở, danh mục thật, phân biệt profile ID/user ID, hiển thị thiếu passkey; enrollment WebAuthn hai kênh, đăng nhập và thu hồi phiên trên Identity/Directory thật | Đạt với Chrome CTAP2 ảo + PostgreSQL/TLS thật; passkey vật lý còn mở |
| R1-03 | Lỗi hồ sơ/dị ứng và bệnh sử có cảnh báo/thử lại; bỏ tuổi/giờ/mã EMR giả và chỉ báo PostgreSQL luôn xanh; dashboard lỗi không thành0 | Đạt TypeScript/build và test browser; retry lấy được hồ sơ/bệnh sử từ service thật |
| R1-04 | Regression đăng ký email, reset, mã/grant/replay, thiết bị/phiên và app lock đã chạy; server test với PostgreSQL thật; native không đổi | Chưa nghiệm thu UI email và cảm biến Face ID trên iPhone trong R1 |
| R1-05 | Regression đọc/JSON local, consent, quyền review/approve/reject/withdraw/expiry; không gọi VNPT, không gửi ảnh ra provider | Chưa nghiệm thu camera/CCCD thật và đối chiếu trực tiếp trong R1 |

Các kết quả lịch sử bản16 vẫn được giữ trong [biên bản trước](iphone-faceid-document-fix-2026-10-19.md), không tự chuyển thành kiểm thử mới trong R1. AI giữ đợt sau; không khởi động R2 tự động.

## Phiên bản và thay đổi

Backend/web: `feature/dangkhoii/security-integration`, nền HEAD `5a8c6e5` kèm thay đổi chưa commit. Native: `feature/dangkhoii/patient-mobile-ios-login`, nền `e5fc9c1`, app1.0.0(16), không sửa source/cài lại trong R1. Đã pull main ff-only, up to date trước code. Không stage/commit/push. [Baseline R0](../architecture/05-r0-baseline-results.md) vẫn là hồ sơ lịch sử bất biến.

- [Hợp đồng R1](r1-account-administration-contract.md): API/quyền/idempotency/audit/ủy quyền Directory.
- V18 và [DBML](identity-db.dbml): provisioning metadata + nhật ký quản trị. Không đổi schema Directory; sửa lỗi cấp ID mới của DoctorProfile gây Hibernate hiểu nhầm cập nhật bản ghi không tồn tại.
- UI tài khoản nối API thật, cần passkey ADMIN trong5 phút trước tạo/khóa/đổi quyền; tài khoản nội bộ mới luôn dùng passkey. Directory lỗi giữ được tài khoản đã tạo và trạng thái chờ.
- Cập nhật consultation/general/admin dashboard cho dữ liệu thiếu và lỗi truy vấn. Đây không phải audit toàn bộ thông báo/fallback ở mọi màn hình; các nhánh CLS/upload/AI tiếp tục theo R3/R6.
- Hướng dẫn thao tác và các giới hạn phát hành chỉ ở [hướng dẫn17](../../huong-dan-su-dung/17-tai-khoan-va-quan-tri-r1.md), cùng cập nhật trang04/16/mục lục/changelog.

## Kiểm thử và phạm vi bằng chứng

[Evidences](evidence/r1-20261020/) chỉ có kết quả rút gọn và hash, không có OTP/token/key, email người thật hoặc ảnh giấy tờ.

| Kiểm tra | Kết quả | Giới hạn |
|---|---|---|
| Maven Common/Gateway/Identity/Directory |280 test,0 lỗi,0 fail,0 skip; PostgreSQL có điều kiện đều bật | Chỉ các module bị ảnh hưởng, không gọi đây là full14 module R0 |
| PostgreSQL quản trị mới |14 test: normalize/idempotency/duplicate/rollback, password/stale/revoked/đổi role actor, khóa/thu hồi, tự khóa, hai ADMIN cạnh tranh | DB riêng loopback; không reset DB đang phục vụ người dùng |
| Delegation mới |6 filter test +7 verifier test; test mTLS thật qua Directory→Identity | Unit test transport có mock; không thay cho CA/cert test tích hợp |
| Persistence bác sĩ |1 PostgreSQL regression tạo ID mới, reload, cập nhật inactive cùng profile; UI tạo/phân công/reload đạt | Thử trên DB riêng; không thay profile của bác sĩ đang dùng |
| TypeScript và Next production |typecheck và build webpack đạt, thư mục build riêng | Không ghi đè `.next` của web đang chạy; chưa phát hành production |
| Tuổi/giờ |10 assertion ranh giới sinh nhật/múi giờ/ngày sai/tương lai/thiếu | Helper test có thể lặp lại từ script trong repo |
| Browser tài khoản + passkey |6 nhóm đạt: tạo thật/replay/Directory role+ID/enroll/khóa+unlock/UI tạo bác sĩ | Fixture adapter chuyển request browser tới hai service thật; không gọi đây là full BFF/Gateway E2E; Chrome CTAP2 ảo, mail outbox đọc riêng, outbound email tắt |
| Browser runtime đã triển khai |6 nhóm đạt: list thực, mật khẩu demo403/không lưu, lỗi list/retry, lỗi Directory/unknown, DOCTOR403 | GET và kiểm tra từ chối qua BFF/Gateway thật; không cấp passkey cho tài khoản thật để ép test thành công |
| Browser clinical |4 nhóm đạt: hồ sơ503→retry thật; EMR503→không giả rỗng→retry thật | Chèn lỗi503 trên browser, không dừng service; không sửa bệnh án |
| Browser bảo mật/email template |3 nhóm đạt: cookie HttpOnly/Strict, token không localStorage, password ADMIN không cấp passkey;4 template320px không ảnh/CID | Không gửi thư mới/không chứng minh Gmail hoặc webhook đã nghiệm thu trong R1 |
| Flutter |233 đạt full suite;14 HTTP fixture chạy riêng đạt | Full suite ghi15 skip:14 HTTP cần baseURL riêng đã chạy lại;1 assertion demo cũ bỏ vì mô hình thanh toán cũ, có test settlement thay thế.247 lượt đạt, không che phần skip |

R1-04 kế thừa test PostgreSQL đăng ký/reset/biometric; R1-05 kế thừa DocumentIdentityPostgres/Authorization và Flutter local-document/HTTP. Các fixture dùng dữ liệu tổng hợp, không có căn cước thật. Hướng hiện tại là đọc trên máy + nhân viên đối chiếu; không phải eKYC tự động hoàn toàn, không NFC/liveness và không tự cấp quyền hồ sơ bệnh án.

## Triển khai test và cấu hình

Hai Identity ở18081/18092 và Directory18090 được thay JAR sau test; Gateway18080 giữ cấu hình. HTTPS health kiểm chứng CA/client cert đạt. Web dev5173 dùng source mới; production build chỉ kiểm chứng tại thư mục riêng. Bridge iPhone dùng backend này; app16 không có thay đổi native nên không cài lại.

V18 success trong Flyway history runtime; đã sao lưu Identity DB trước migration. Các bảng mới nằm schema identity; runtime cũ giữ lịch sử Flyway ở public. Không đọc/ghi DB remote `100.116.233.60`. Không xóa hoặc thay data seed/hàng đợi runtime trong R1.

Thay đổi private duy nhất về cấu hình là thêm scope `GET:/api/users/staff/**` cho caller Directory tại cả Identity. Đã so sánh trước/sau: credential/caller secret/Resend/outbox key/document key/DB connection và toàn bộ cấu hình còn lại giữ nguyên. Trong lượt triển khai kỹ thuật ban đầu không phát sinh thư thật, ảnh thật, VNPT hoặc AI call; phiên kết nối thiết bị bổ sung ở cuối biên bản có một email bootstrap ADMIN thật.

Artifact, log và backup private giữ ngoài Git tại `~/Library/Developer/CareFlow/r1-20261020/`; [runtime evidence](evidence/r1-20261020/runtime-results.json) ghi kiểm chứng V18/UP và không tồn tại tài khoản từ lệnh tạo bị từ chối403. Phục hồi chỉ với môi trường/backup thuộc sở hữu, theo trang17; backup đã tạo nhưng chưa diễn tập restore R1 nên không tuyên bố đạt DR.

## Kiểm soát phạm vi và phần còn mở

GitNexus upstream impact được chạy trước sửa symbol. UserRepository HIGH17 caller đã cảnh báo trước; login LOW1 caller/1 process; các mutation Directory LOW1 caller/1 process. React/HTTP một số symbol UNKNOWN; không diễn giải thiếu graph thành không ảnh hưởng. Phát hiện persistence bác sĩ được phân tích lại LOW trước sửa. Index được làm mới; detect-changes toàn worktree báo CRITICAL (202 file/848 symbol/544 flow), đã cảnh báo, có cả thay đổi từ các đợt trước. Đối chiếu hash R0 xác nhận đúng11 file source/test có sẵn thay đổi trong R1,10 file source/script/test mới và262 input native giữ nguyên; xem [manifest](evidence/r1-20261020/source-manifest.json). Không coi toàn bộ diff là R1.

Chưa đóng các mục sau:

- Đăng ký/reset trên UI iPhone với inbox thật; Face ID thật (logout/cancel/đổi tài khoản/khóa nền/credential thu hồi) và camera OCR/QR/sửa tay/consent, nhân viên nhìn giấy tờ gốc. Hướng dẫn đã có ở trang15–17; cần ghi kết quả người dùng, không gửi ảnh/OTP vào chat.
- Passkey vật lý nhân viên và RP ID/origin/cookie/HTTPS trên staging ổn định; local test không thay chứng nhận production.
- Layout web quản trị tối ưu cho desktop; sidebar chung chưa tối ưu viewport390px, không coi browser mobile là đã nghiệm thu. Ứng dụng bệnh nhân native là luồng iPhone hiện hành.
- APNs/Firebase/push, Analytics, lịch/queue/walk-in, upload/CLS/toa/tái khám và AI tiếp tục R2–R7 theo kế hoạch. Các cảnh báo dependency/migration từ R0 vẫn mở; R1 không được gọi là đã an toàn production toàn hệ thống.

**Điều kiện đóng R1:** bổ sung nghiệm thu thực tế các mục phần cứng/UI nêu trên hoặc chủ dự án chấp nhận bàn giao mốc kỹ thuật có giới hạn. Không đổi trạng thái thành hoàn thành đầy đủ chỉ dựa vào số test.

## Bổ sung — iPhone đã kết nối, 20/10/2026

Đã đọc metadata thiết bị vật lý: iPhone11 Pro Max, CareFlow1.0.0(16). Bridge HTTPS `/health`, Directory departments/rooms và health Gateway/hai Identity/Directory trả200. Không cài lại app hoặc thay đổi credential/DB trong lượt xác nhận kết nối. Mẫu OCR chữ tổng hợp được mở trên Mac; [hướng dẫn18](../../huong-dan-su-dung/18-nghiem-thu-r1-thiet-bi.md) tập trung các bước A–D cần người cầm thiết bị. [Kết quả kết nối](evidence/r1-device-20261020/connection-results.json).

SELECT metadata lúc bắt đầu phiên ghi nhận0 passkey đang hoạt động trên runtime test. Cần bootstrap ADMIN thật trước khi người dùng tự thực hiện tạo/khóa bằng UI; phiên mật khẩu demo bị403 đúng thiết kế. Không coi test CTAP2 ảo ở DB riêng là đã cấp passkey cho runtime này. Cảm biến/camera/email UI/passkey thực tế tiếp tục chờ phản hồi người dùng; không đánh dấu PASS từ việc cắm cáp.

### Bootstrap ADMIN runtime đã chuẩn bị

Do runtime chưa có passkey ADMIN, operator đã cấu hình địa chỉ nhận thư cho tài khoản ADMIN demo bằng hộp thư do chủ dự án cung cấp (dùng alias cùng hộp thư khi tránh trùng PATIENT). Bản contact trước thay đổi giữ private; cập nhật có security event `ADMIN_BOOTSTRAP_CONTACT_CONFIGURED`, không thay password/role/status và không tự gắn email_verified_at. Script bootstrap chuẩn đã tạo grant và audit; Resend chấp nhận một email, chưa khẳng định người nhận đã đọc. Mã kích hoạt được giữ ở file0600 và điền vào form local riêng, không có trong repo/evidence.

Chrome thật trên Mac mở `/staff-enrollment`, chờ người dùng tự nhập mã từ email và xác nhận WebAuthn hệ thống. Không dùng virtual authenticator hoặc chèn proof/session. Hoàn tất enrollment mới thử đăng nhập; trước khi có response thành công, trạng thái tiếp tục PENDING. Các bước Face ID/camera/UI email trên iPhone cũng chờ người cầm thiết bị.

## Chấp nhận chuyển R2 — 20/10/2026

Chủ dự án xác nhận “đã oke” và yêu cầu triển khai R2. Runtime ghi nhận enrollment/passkey login ADMIN thật HTTP200, một passkey active và phiên BIOMETRIC mới. [Bằng chứng passkey](evidence/r1-device-20261020/physical-admin-results.json), [checkpoint](evidence/r1-device-20261020/transition-checkpoint.json). Chấp nhận mốc kỹ thuật R1 để chuyển đợt; không tự đánh PASS từng ca camera/đăng ký/reset UI chưa có phản hồi riêng và không gọi đây là chứng nhận production. Những mục đó được giữ trong danh sách nghiệm thu tổng R7.
