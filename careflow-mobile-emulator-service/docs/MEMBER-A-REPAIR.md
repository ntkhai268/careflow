# Sửa các lỗi thành viên A — 15/10/2026

**Bốn vấn đề HIGH trong báo cáo kiểm tra đã được sửa và chạy lại bằng backend thật.** Gateway audit đang chạy tại 18080, Notification 18085, Queue 18084 và Patient 18082. Các class của bốn dịch vụ triển khai khớp bản build từ source hiện tại.

## Kế hoạch và thay đổi

Kế hoạch trước sửa: `careflow/.worktrees/notification-session-recipient/.planning/notification-service/member-a-repair-plan.md`. GitNexus đã phân tích trước sửa; cảnh báo HIGH/CRITICAL và authorization tiếp tục đã được ghi nhận. Mỗi nhánh đã pull origin main và đều already up to date. Không làm thay đổi develop.

| Lỗi | Sửa và điều kiện kiểm tra |
|---|---|
| Token đã thu hồi vẫn dùng WebSocket | CONNECT đối chiếu phiên Identity với JWT; mỗi outbound MESSAGE kiểm tra lại; quét đóng socket nhàn rỗi. Chặn khi logout current/all, refresh, disable, role change, token hết hạn hoặc Identity không xác minh được. Socket mới cũng bị từ chối. |
| Seed trùng số NOI-001 làm intake 500 | Cấp số dưới khóa QueueConfig, dùng max(counter, số đã lưu)+1. Thiếu/cũ counter được phục hồi qua logic cấp số, không xóa seed hay sửa trực tiếp counter. Lượt đầu đã nhận NOI-006; hai JVM được kiểm tra đồng thời. |
| Hồ sơ thứ hai mất thông báo | Notification V3 bỏ UNIQUE user_id, giữ PK patient_id và index tra user; native atomic upsert bảo vệ sự kiện đồng thời/đến trễ. Đặt lịch thật trên hồ sơ mới nhận được inbox; nhiều hồ sơ cùng user có mapping riêng. |
| STAFF/no-account không tiếp nhận được | STAFF phải có phân công phòng active, chỉ tạo WALK_IN; Queue lấy owner thật từ Patient và chặn giả userId. API tiếp nhận Patient tạo hồ sơ thật userId=null, ghi actor/phòng. Queue giữ userId=null, ghi người tiếp nhận riêng. Không tạo account/appointment giả hoặc đưa nhân viên thành chủ hồ sơ. |

Đã sửa thêm Gateway CORS thiếu PATCH, phát hiện khi thử đổi quyền/khóa user từ origin giả lập. Giữ nguyên allowlist origin; chỉ bổ sung HTTP method cần thiết.

## API tiếp nhận

STAFF có phân công hoặc ADMIN:

```text
POST /api/patients/reception
{roomId, fullName, dateOfBirth?, gender?, phone?, idCardNumber?}
→ 201 {id,userId:null,fullName,dateOfBirth,gender}

POST /api/queues/entries
Idempotency-Key: <unique key>
{patientId, departmentId, priorityLevel:"WALK_IN", userId?: <verified owner>}
→ 201 QueueEntry, CHECKED_IN
```

Phòng/actor phải được Directory xác minh. Patient không tồn tại, sai phân công, userId giả hoặc dependency lỗi đều không được tạo entry/event. Replay cùng key vẫn kiểm tra lại quyền; key khác không vượt được kiểm tra trùng lượt. STAFF tiếp nhận không tự có quyền đọc/sửa bệnh án hoặc phân loại cấp cứu/ưu tiên.

Patient V8 cho phép owner null và thêm audit phòng/actor; DBML Patient/Notification đã cập nhật. Contract Patient v1.3, Queue v1.5, Notification v1.3. Queue dùng cột nullable/audit đã có, không cần migration mới. Các thuật toán 1:1:1 và policy ưu tiên cũ không được thay đổi.

## Kết quả kiểm chứng

| Bộ kiểm tra | Kết quả |
|---|---|
| Unit + build Notification / Queue / Patient / Gateway | **189/189**: 25 + 66 + 64 + 34; zero failure/error/skip |
| Nghiệm thu HTTP/STOMP/DB thật sau khởi động lại | **117 kiểm tra đạt**, không có finding; số poll chờ event có thể khác giữa các lần chạy |
| Mất Identity thật và phục hồi | **11 kiểm tra đạt**: đóng socket cũ, chặn kết nối mới/REST, cùng token hợp lệ dùng lại sau phục hồi |
| Identity lifecycle sau sửa | **50/50 HTTP đạt** trên hai Identity JVM |
| 3 điện thoại mẫu qua Chrome/API thật | **7/7 đạt**: đúng tài khoản/hồ sơ, chưa có lịch/đã xác nhận/đã hủy |
| Test hạ tầng giả lập | **28/28 đạt**; đây là unit, không dùng thay bằng chứng backend thật |
| Migration từ DB trống | Notification V1–V3 và Patient V1–V8 đều chạy thành công, Hibernate validate/health đạt |
| Upgrade DB audit hiện có | V3/V8 áp dụng thành công, không reset dữ liệu mẫu |
| Sau restart Queue | Giữ nguyên sáu entry của lượt nghiệm thu đầu, gồm NOI-006; cấp tiếp không trùng số |

Kiểm tra đồng thời dùng hai Queue JVM thật cùng DB/broker: nhiều bệnh nhân nhận số khác nhau; cùng key trả cùng entry và chỉ một PatientCheckedIn event; cùng patient với key khác nhau chỉ một request được tạo và request còn lại 409. Không dùng script sửa trạng thái, đổi ngày hoặc tạo slot ngoài danh mục.

Token kiểm tra hết hạn được cấp bởi một Identity JVM thật có TTL 5 giây; không tự ký JWT làm bằng chứng thành công. Các tiến trình probe được dừng sau kiểm tra. Primary Identity đã phục hồi và Gateway health hiện 200.

## Phục hồi thông báo cũ

Đã replay **một sự kiện AppointmentConfirmed gốc** có lỗi do mapping nhiều hồ sơ, đối chiếu với outbox nguồn và giữ nguyên eventId/payload. Publish confirm trước khi ack DLQ; DB sau phục hồi có đúng một notification cho source event đó. Hai message DLQ không thuộc allowlist được giữ để điều tra, không purge/replay bừa.

`scripts/ReplayNotificationDlq.java` nằm trên nhánh Notification. Chỉ chạy audit loopback, yêu cầu allowlist event IDs; default dry run, thêm --apply mới replay. Credentials lấy từ environment riêng. Không đưa token/password runtime vào báo cáo.

## Nhánh và phạm vi bàn giao

| Nhánh | Đường dẫn |
|---|---|
| feature/dangkhoii/notification-session-recipient | careflow/.worktrees/notification-session-recipient |
| feature/dangkhoii/queue-access | careflow/.worktrees/queue-access |
| feature/dangkhoii/patient-health-record-access | careflow/.worktrees/patient-health-record-access |
| feature/dangkhoii/gateway-access | careflow/.worktrees/gateway-directory-access |

Các sửa phân quyền có sẵn ở các nhánh được giữ nguyên. Notification mới phụ thuộc bản Common active-session của nhánh `feature/dangkhoii/common-session-validation`, như các consumer đã sửa trước đó. Cách build hiện tại dùng module POM và thư viện Common đã cài local; chưa phải chứng nhận root-reactor CI sau merge.

GitNexus full rebuild/detect-changes đã chạy để review. Tracked diff Queue/Patient vẫn CRITICAL do thay đổi auth/entity; các mục untracked (guard mới, reception API, migrations/tests/scripts) cần review riêng vì detect-changes không tính chúng. WebSocket framework edges không được nhận diện đầy đủ; không dùng risk LOW/zero flow của graph thay cho test thật.

Không stage, commit, push hoặc merge. Source develop và các sửa doctor-web của người dùng được giữ nguyên. Chỉ DB/broker audit riêng được ghi dữ liệu; không ghi DB remote chung. Ba tài khoản mobile_case01/02/03 và các ca gốc không bị đổi trạng thái.

## Chạy lại và bằng chứng

```sh
cd careflow-mobile-emulator-service
npm run test:member-a -- --audit-db
npm run test:dataset
```

Runner chỉ nhận Gateway 127.0.0.1:18080 và private audit state hiện có. Nó khởi động hai JVM probe tại 18093/18094, tạo dữ liệu test mới qua API thật, thu hồi các phiên do chính test tạo và dừng probe khi xong. Cần DB/broker và các service audit đang chạy, Java/Chrome/Playwright cục bộ. Không dùng lại các tài khoản mẫu cho thao tác role/disable.

Bằng chứng trong `.runtime/`: `member-a-repair-results.json`, `member-a-identity-outage-results.json`, `member-a-dlq-recovery.json`, `member-a-fresh-schema-results.json`, `member-a-queue-after-restart.json`, `member-a-runtime-classes.json`, `dataset-browser-results.json`, `dataset-real-phones.png`. Unit/build logs và GitNexus reports nằm tại `/tmp/careflow-member-a-*`.

**Giới hạn còn lại:** eKYC thật/FCM cần tích hợp và credential/provider riêng; check-in thành công đúng ngày và hành trình Consultation–CLS–Prescription–settlement/no-account đầy đủ chưa được nghiệm thu ở đợt sửa bốn lỗi này. Không có dữ liệu hoặc phản hồi mock để đánh dấu những phần đó đã xong. Hai DLQ message ngoài allowlist vẫn chưa được kết luận. Triển khai production, key/JWKS, MFA, TLS và tích hợp các nhánh vẫn cần kiểm tra riêng.
