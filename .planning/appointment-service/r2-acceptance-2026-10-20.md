# R2 — Biên bản triển khai và kiểm thử, 20/10/2026

**Kết quả:** đã triển khai R2 trên cụm test cục bộ, kiểm thử PostgreSQL/API/web và cài/mở native **1.0.0 (17)** trên iPhone 11 Pro Max. **Chưa đóng nghiệm thu toàn bộ R2:** thao tác liên kết/chấp thuận, camera, GPS và quay lại app trên thiết bị còn chờ người dùng xác nhận. Chưa bắt đầu R3; AI và eKYC bên ngoài không thuộc thay đổi này.

Đặc tả đối chiếu: [kế hoạch đầy đủ, mục R2](../full-requirements-plan-2026-10-19.md). Hướng dẫn thao tác duy nhất: [trang19](../../huong-dan-su-dung/19-tiep-nhan-lich-va-hang-doi-r2.md); tài khoản/dữ liệu demo chỉ ở [trang01](../../huong-dan-su-dung/01-tai-khoan-va-du-lieu.md). Biên bản này mô tả thiết kế và bằng chứng kiểm thử, không thay thế hướng dẫn sử dụng.

## 1. Phạm vi mã nguồn và bản chạy

- Backend/web: worktree `security-integration`, nhánh `feature/dangkhoii/security-integration`, nền `5a8c6e5`.
- Native: worktree `patient-mobile-ios-login`, nhánh `feature/dangkhoii/patient-mobile-ios-login`, nền `e5fc9c1`. Không dùng bản mobile cũ trong worktree backend để đóng nghiệm thu.
- Đã cập nhật `origin main` trước triển khai. Cả hai worktree có thay đổi từ các đợt trước; các thay đổi đó được bảo toàn. Chưa stage, commit hoặc push.
- Backend dùng JVM JDK25 và các JAR đã đóng gói; web đang chạy dev từ nguồn cập nhật, đồng thời build production thành công ở thư mục riêng. Bản iOS release được ký, cài đè và mở thành công, không gỡ app/xóa dữ liệu đăng nhập.
- Chỉ tác động cụm test riêng trên máy: PostgreSQL loopback `127.0.0.1:15432`, RabbitMQ riêng và các dịch vụ HTTPS/mTLS. Không đổi DB dùng chung từ xa.
- Sao lưu sáu DB dạng `pg_dump` trước migration, cấu hình runtime và artifact trước R2 ở khu vực private ngoài Git. Không đổi khóa hoặc cấu hình gửi thư Resend; kết quả so sánh cấu hình nằm trong bằng chứng runtime.

## 2. Đối chiếu từng yêu cầu

| Mã | Kết quả triển khai | Bằng chứng / giới hạn |
|---|---|---|
| R2-01 | Lịch theo phòng lưu PostgreSQL, ngày hiệu lực, thứ làm việc, khung giờ, thời lượng/capacity, ngoại lệ và lịch sử phiên bản; kiểm tra lịch đã đặt trước khi sửa | Kiểm thử PG, lưu/tải lại qua API và web; ngày nghỉ thực sự loại slot khỏi API rồi khôi phục được |
| R2-02 | Policy hàng đợi theo phòng có version/audit: phút dự kiến, ngưỡng gần lượt, missed policy và tạm ngừng tiếp nhận. Geofence dùng cấu hình thật | Giữ scheduler ba lane; bỏ điều khiển tỷ lệ/check-in minutes không được thuật toán áp dụng. Khi tạm ngừng vẫn đọc được hàng đợi, giữ phiếu lịch tương lai, hoàn tất ca đang phục vụ; chặn cấp/check-in/gọi lượt mới |
| R2-03 | Sức chứa tổng hợp nhiều phòng, chọn phân công Directory hợp lệ, khóa giao dịch theo phòng/slot/bệnh nhân; giữ snapshot phòng/bác sĩ đã xác nhận | PG kiểm tra nhiều phòng, booking cạnh tranh/capacity và chống sửa lịch gây mất chỗ. Luồng UI thực tế dùng ROOM-11; chưa thử đồng thời nhiều phòng trên iPhone |
| R2-04 | STAFF tìm/tạo hồ sơ tối thiểu, chọn phòng/dịch vụ từ API, cấp lượt không hẹn idempotent; bác sĩ tạo/resume/hoàn tất ca theo queueEntryId | API thật và Chrome: tạo hồ sơ → cấp số → gọi → mở ca → lưu chẩn đoán → hoàn tất; appointmentId có thể null, không tạo lịch hẹn giả |
| R2-05 | Liên kết chính chủ qua đối chiếu trực tiếp, mã một lần, nhân viên đề xuất, bệnh nhân chấp thuận; trạng thái trùng và thu hồi/audit | PG/API kiểm tra xác minh, consent, sai tài khoản, cạnh tranh và thu hồi quyền. Web/mobile có giao diện; chấp thuận trên iPhone còn chờ xác nhận. Không tự gộp bệnh án/cấp quyền đại diện |
| R2-06 | Hiện riêng lượt MISSED, gọi lại đủ ba lần, xếp FRONT/BACK theo policy, manual cần position; RESULT_REVIEW về cuối | API/web thật đã qua gọi → gọi lại → MISSED → BACK → gọi lại; PG/unit kiểm tra các policy và lượt đọc kết quả |
| R2-07 | QR/GPS/check-in kiểm tra trên máy chủ, quyền nhân viên/bác sĩ theo phòng; native lấy đúng phiếu theo appointment và tải lại khi resume | API thật đạt QR sai/hết hạn, thiếu GPS/ngoài vùng, check-in lặp, hủy lịch, sai phân công, thu hồi. Widget test đạt WAITING/CANCELLED/resume. Camera/GPS/UX thiết bị vẫn mở |

## 3. Các quyết định bảo mật và tính nhất quán

**Quyền hồ sơ hiện tại thuộc Patient.** Appointment/Queue giữ snapshot lịch sử nhưng snapshot không được cấp quyền sau khi thu hồi liên kết. Appointment, Queue, Consultation và Notification hỏi Patient qua endpoint nội bộ không cache, chỉ trả `patientId/userId`. Endpoint ngoài Gateway, yêu cầu đúng CN mTLS và khóa service độc lập; lỗi provider từ chối truy cập hoặc để consumer retry, không lấy quyền cũ thay thế.

**Liên kết không dựa riêng vào thông tin tự khai.** Identity phải có kết quả đối chiếu trực tiếp `CONFIRMED`, có nhân viên duyệt và tài khoản PATIENT đang hoạt động; số giấy tờ/ngày sinh trùng chính xác. Tên dùng NFC, chuẩn hóa chữ hoa và khoảng trắng, không bỏ dấu hoặc so khớp gần đúng. HMAC cũ hỗ trợ một số biến thể chữ hoa/thường phổ biến; dữ liệu cũ khác biệt ngoài các biến thể đó vẫn bị từ chối, không tự đoán danh tính. Không khôi phục plaintext đã xóa để chuyển đổi hash.

Mã ngẫu nhiên 32 byte chỉ lưu SHA-256, một lần/5 phút. Nhân viên đúng phòng đối chiếu hồ sơ gốc rồi mở yêu cầu 10 phút; chủ tài khoản phải chấp thuận lần nữa. Xung đột hồ sơ dẫn tới `DUPLICATE_REVIEW`, không cấp quyền và không gộp dữ liệu lâm sàng. Thu hồi xóa liên kết chủ hiện tại, giữ hồ sơ và audit. Không seed sẵn consent/định danh/liên kết đã duyệt.

**Tiếp nhận giới hạn dữ liệu và thẩm quyền.** Tìm kiếm dùng POST body, chỉ trả hành chính tối thiểu/điện thoại che bớt, có giới hạn tần suất và audit không ghi nguyên truy vấn. Tạo/cấp số có idempotency bền vững. STAFF không tự chọn ưu tiên y khoa, không được bắt đầu/hoàn tất ca khám; check-in bằng mã phiếu cũng kiểm tra phân công phòng trước tìm phiếu.

**Chống cạnh tranh ở DB.** Booking/cấp lượt/link có khóa giao dịch, scope idempotency và ràng buộc unique thích hợp. Có thể có hai phiếu WAITING cho hai lịch tương lai, nhưng chỉ một lượt khám ban đầu hoạt động cho cùng bệnh nhân/khoa/ngày. Đường tạo consultation khóa theo visit/doctor, replay đúng queueEntry thay vì dùng cùng patientId để coi là cùng ca.

**Consumer không giả danh người dùng.** Background Queue chỉ được gọi GET danh mục phòng/khoa công khai qua đúng Feign client; các API riêng vẫn đòi actor/caller. Đã tìm và sửa lỗi interceptor chạy trước gắn tiền tố URL, từng làm hai sự kiện thử bị đẩy vào DLQ. Hai sự kiện gốc được phát lại không thay nội dung; phiếu hội tụ đúng. Bản gốc vẫn được giữ để đối chiếu/idempotency; không purge broker hoặc sửa trạng thái DB để ép đạt.

## 4. Migration, DBML và rollout

| Service | Migration R2 | DBML |
|---|---|---|
| Appointment | V7: lịch phòng/revision và booking command | [appointment-db.dbml](appointment-db.dbml) |
| Patient | V9: command/audit/giới hạn tìm tiếp nhận; V10: authorization/link/audit và điều chỉnh unique hồ sơ chưa có chủ | [patient-db.dbml](../patient-service/patient-db.dbml) |
| Queue | V13: cấu hình riêng từng phòng, version/audit, snapshot tên/dịch vụ và unique lượt hoạt động phù hợp | [queue-db.dbml](../queue-service/queue-db.dbml) |
| Consultation | V4: queueEntry liên kết duy nhất và appointment tùy chọn; V5: căn chỉnh cột ICD | [consultation-db.dbml](../consultation-service/consultation-db.dbml) |

DBML được dựng từ schema sau Flyway trên DB kiểm thử riêng, có constraint/index/quan hệ thực; không vẽ bảng lịch/phiếu giả. Identity dùng schema document check hiện có, không thêm cột cho phép đối chiếu.

Rollout theo thứ tự provider Patient/Identity → client quyền hiện tại → Appointment/Queue/Consultation/Notification → web/native. Cấu hình private bổ sung `security.patient-owner.url`, `patient.internal-readers-json` và scope patient-service đúng một endpoint confirmed-match ở cả hai Identity instance, giữ nguyên các khóa đã có. Cấu hình sai/thiếu không được fallback quyền.

Lần chạy migration Consultation đầu gặp DB cũ chỉ có `icd10code/icd10name`. V5 đã được sửa để thêm cột chuẩn nếu thiếu rồi sao chép dữ liệu khi không xung đột; xung đột dừng migration. Kiểm thử gồm nâng cấp legacy-only và xung đột. Runtime sau đó đã khởi động thành công, ghi/đọc ICD qua API/UI thật.

**Giới hạn rollback:** giữ DB dump và JAR/config trước R2 ngoài Git; chưa diễn tập khôi phục toàn cụm. Sau khi bản mới ghi ICD vào cột chuẩn, chỉ quay JAR cũ có thể đọc dữ liệu legacy thiếu cập nhật; cần đối soát cột lâm sàng hoặc forward-fix có kiểm thử trước rollback. Không chạy SQL đảo migration/xóa dữ liệu trên cụm đang dùng.

## 5. Bằng chứng kiểm thử

Bằng chứng công khai đã loại token, khóa, cấu hình private, ID thiết bị và dữ liệu giấy tờ. [Tổng hợp test/build](evidence/r2-20261020/test-summary.json) có số lượng và hash log; log gốc/private fixtures nằm ngoài Git.

| Nhóm | Kết quả |
|---|---|
| Backend | Package 11 module thành công; đóng gói/lặp kiểm thử Identity và Appointment/Queue sau các sửa cuối đều thành công |
| PostgreSQL | 30 test R2 đạt: Identity3, Patient10, Appointment5, Queue7, Walk-in3, nâng cấp Consultation2; thêm 10 test tương thích vòng đời giấy tờ đạt. Đây là tập hợp các lần chạy chuyên biệt, không tuyên bố tất cả trong một lệnh |
| Flutter | Suite chính237 đạt; thêm3 widget queue mới đạt; 14 HTTP fixture test có điều kiện được chạy riêng đều đạt. Còn1 skip assertion cũ phần pharmacy/payment ngoài R2; không tính là đạt |
| Phân tích native | Các file mới/sửa được kiểm tra không có issue; HTTP fixtures/unit không chứng minh API runtime hoặc cảm biến |
| API luồng chính | [13 nhóm kiểm tra đạt](evidence/r2-20261020/runtime-results.json): policy/schedule, tiếp nhận, ca không hẹn, xác minh/consent/liên kết và thu hồi |
| API nhánh từ chối | [7 nhóm đạt](evidence/r2-20261020/queue-negative-results.json): hai lịch/hai phiếu, QR/GPS, sai phân công, check-in lặp, hủy lịch và snapshot sau thu hồi |
| Ranh giới nội bộ | [4 nhóm đạt](evidence/r2-20261020/transport-boundary-results.json): sai CN, sai khóa, thiếu client certificate, kết quả tối thiểu sau thu hồi. Thiếu chứng thư bị chặn ở TLS handshake, không nhất thiết trả HTTP401 |
| Chính sách trực tiếp cuối | [6 nhóm đạt](evidence/r2-20261020/final-live-policy-results.json): nghỉ phòng tác động availability, dashboard khi ngừng, phiếu tương lai, chặn cấp lượt, hủy phiếu và khôi phục policy gốc |
| Chrome thật | [4 nhóm màn hình/quyền](evidence/r2-20261020/browser-results.json) và [5 nhóm walk-in](evidence/r2-20261020/walkin-ui-results.json) đạt; không mock HTTP hoặc đổi DB trạng thái. Harness chẩn đoán được tiếp tục ở đúng tab bằng nút resume sau selector đầu sai |
| iPhone | Release1.0.0(17) build/ký/cài/mở thành công; staging lib khớp nguồn native. Chưa có xác nhận thao tác màn hình/camera/GPS/consent R2 của người dùng |
| Cụm test cuối | [12 dịch vụ health UP](evidence/r2-20261020/closing-runtime-results.json), gồm Identity phụ; mail/recovery config được so sánh giữ nguyên |

Ngày nghỉ và Queue policy thử được khôi phục bằng API versioned. Các lịch thử có hẹn đã hủy, ca không hẹn hoàn tất, liên kết thử thu hồi; dữ liệu/audit tổng hợp được giữ để đối chiếu. Không đổi mật khẩu/vai trò/phương thức đăng nhập của tài khoản demo.

## 6. Phân tích ảnh hưởng và phạm vi chưa nghiệm thu

Đã dùng GitNexus CLI vì MCP không khả dụng, reindex sau sửa và chạy `detect-changes` ở cả hai worktree. Trước sửa có upstream impact/context và cảnh báo HIGH/CRITICAL cho các đường booking, Queue, DTO, consultation và xác thực. Ví dụ QueueEntry có18 caller trực tiếp, `requireLockedConfig`7 caller/5process, `requireConfig`5caller/10process; các con số là phạm vi graph tìm được, không bao quát hoàn toàn HTTP/Spring/Feign/event.

Một số method Java mới hoặc overload không phân giải dù chỉ rõ file/repository; được ghi UNKNOWN và kiểm tra caller/route/client thủ công, không suy ra là không có ảnh hưởng. Thay đổi quyền hiện tại có phạm vi cao và được kiểm chứng qua API mTLS/DPoP thực, PostgreSQL và hồi quy các module liên quan.

Kết quả `detect-changes` toàn worktree backend: **214 file,347 symbol,209 process, CRITICAL**; native: **36 file,163 symbol,9process, HIGH**. Đây là diff tích lũy so với Git HEAD, chứa cả R1 và công việc đã có trước R2, **không phải số file riêng R2**. [Snapshot văn bản](evidence/r2-20261020/source-snapshot.json) chỉ đối chiếu các file có baseline trước R2; không coi ảnh/log/binary cũ chưa track là file mới R2. Không stage/commit/push các thay đổi tích lũy. `git diff --check` sau dọn khoảng trắng R2 chỉ còn một khoảng trắng cuối dòng đã có từ trước ở `docs/service-contracts/README.md`; không chỉnh tài liệu ngoài phạm vi đó.

Các phần vẫn mở:

- Nghiệm thu trên iPhone theo trang19: mục liên kết/chấp thuận/thu hồi, đúng phiếu từng lịch, camera/QR/GPS, quay từ nền và lỗi mất quyền. Đã cài/mở app không đồng nghĩa các nhánh này đạt.
- Nhiều phòng/cạnh tranh và fail-closed có bằng chứng code/DB; chưa có nghiệm thu đồng thời nhiều phòng trên thiết bị và chưa thử tải production.
- Trùng hồ sơ chỉ giữ kiểm tra/từ chối an toàn; không có tự gộp EMR hoặc quyền người đại diện. Cấu hình runtime current-owner phải hiện diện ở môi trường triển khai tiếp theo.
- Thông báo background/APNs, vận hành reconciliation/DLQ, khôi phục DB toàn cụm và những nhánh phần cứng R1 chưa xác nhận thuộc các mốc R4/R7 tương ứng. Polling/resume không thay thế push native.
- Khám/toa/CLS/tái khám liên tục thuộc R3; R2 chỉ xác nhận ca không hẹn cơ bản, không tuyên bố nghiệm thu toàn bộ hành trình lâm sàng.

**Điều kiện bàn giao R2:** giữ kết quả API/web/DB hiện có, nhận bằng chứng thao tác iPhone còn mở và xử lý lỗi nếu phát sinh; sau đó mới đánh dấu nghiệm thu đủ môi trường và chuyển R3 theo yêu cầu tiếp theo.
