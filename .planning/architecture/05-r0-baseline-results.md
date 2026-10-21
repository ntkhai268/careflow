# Biên bản hoàn thành chuẩn hóa R0 — bắt đầu 19/10/2026, hoàn tất 20/10/2026

**Kết quả:** đã hoàn thành các đầu ra R0-01–05: baseline source/artifact, yêu cầu/use case, kiến trúc, contract/state và điều kiện kiểm thử. Các thiếu sót nghiệp vụ vẫn thuộc R1–R7; R0 không triển khai endpoint, đổi schema, phát hành hoặc nghiệm thu lại toàn bộ ứng dụng.

## 1. Kết quả theo công việc

| Mã | Đầu ra đã thực hiện | Bằng chứng |
|---|---|---|
| R0-01 | Hai source snapshot có hash từng input; private backup JAR/app16; build lại backend/web/iOS ngoài repo; bản đang chạy đối chiếu lớp application | [manifest](evidence/r0-20261019/baseline-manifest.json), [artifact verification](evidence/r0-20261019/artifact-verification.json), các build JSON |
| R0-02 |22 use case có actor/tiền-hậu điều kiện/luồng chính/lỗi/service; ma trận24 mã yêu cầu + Analytics; tham chiếu báo cáo/contract cũ đã cập nhật | [Yêu cầu/use case](01-requirements-use-cases.md), [kế hoạch tổng](../full-requirements-plan-2026-10-19.md) |
| R0-03 | Ownership đủ12service + Directory/Eureka; sơ đồ logic, deployment và trust boundary; state dùng đúng enum source | [Kiến trúc](02-service-architecture.md), [state/sequence](03-contracts-and-states.md) |
| R0-04 | Chốt CURRENT/TARGET cho lịch/slot, nhiều phòng, walk-in, requeue, tái khám, idempotency và event | [Contract](03-contracts-and-states.md); chưa có API TARGET được triển khai |
| R0-05 | Đặc tả fixture18 nhóm nghiệm thu; kiểm tra runtime/device/push/migration; ghi phụ thuộc staging/AI | [Điều kiện kiểm thử](04-test-readiness.md), [fixture plan](evidence/r0-20261019/acceptance-fixture-plan.json), [runtime](evidence/r0-20261019/runtime-readiness.json) |

## 2. Baseline chính xác

| Phần | Branch / HEAD | Source và artifact |
|---|---|---|
| Backend/web | feature/dangkhoii/security-integration / 5a8c6e574c1249ce2a6dc791e7415a2bbc33b006 |869 input source/build có hash; có thay đổi chưa commit và file untracked từ các đợt trước |
| Native | feature/dangkhoii/patient-mobile-ios-login / e5fc9c172cb6a5752e4562fd1b885317fa965ab7 |262 input có hash; lib/native M là nguồn chuẩn |
| Main tham chiếu | origin/main / 9da55d3 | Đã fetch và pull ff-only cả hai worktree, Already up to date; không merge/stage/commit/push |
| iPhone đang dùng | com.careflow.careflowPatient / 1.0.0(16) | Devicectl xác nhận build16 đang cài; snapshot có ký giữ ngoài Git; không cài bản build R0 |

Đã kiểm tra lại1.131 input: không thay đổi sau snapshot trong lượt R0. Hai archive khớp SHA-256 manifest. Snapshot đủ tái dựng source; cấu hình/khóa/bản overlay private giữ riêng, không nằm trong archive source public. Dependency cache/registry, SDK và công cụ vẫn là điều kiện cần khi build lại, không cam kết build offline hoặc bit-for-bit độc lập mọi môi trường.

Private workspace cũ có ba khác biệt Info.plist mang tính overlay (display name, mô tả quyền thư viện ảnh và mạng local) và một số file Android cũ. Lib/AppDelegate iOS khớp nguồn M; build iOS R0 dùng lại ios/Runner/lib/test/tool từ snapshot M. Pubspec/certificates/build defines overlay phục vụ môi trường test được lưu riêng, không giả rằng mọi file workspace build cũ đều trùng checkout M.

Snapshot private tại `$HOME/Library/Developer/CareFlow/r0-20261019/`: source archives, runtime-artifacts/Runner-16.app,12 JAR thực đang chạy, private runtime state và build defines. Quyền thư mục0700/file private0600 theo mục đích; không đưa bản sao hoặc khóa lên Git. Cách đối chiếu/khôi phục ở [hướng dẫn13](../../huong-dan-su-dung/13-ban-va-bao-mat-va-van-hanh.md#đối-chiếu-và-khôi-phục-baseline-r0).

## 3. Build và kiểm chứng đã chạy

| Kiểm tra R0 | Kết quả | Giới hạn |
|---|---|---|
| Maven package toàn reactor từ source snapshot | **Đạt14 module**, có14 JAR gồm Common | `-DskipTests`; không chạy lại test suite. Maven thực dùng JDK26.0.1, target21; JVM java mặc định25.0.3. Không suy ra đã thử runtime JDK21 |
| Doctor Web | `npm ci` theo lockfile + `npm run build` **đạt** | Build trong scratch, không thay dev server. Lần đầu dùng symlink node_modules bị Turbopack từ chối ngoài root; đã cài dependencies riêng và build lại |
| Flutter dependency/analyzer | Enforce lockfile **đạt**; analyzer **0 error/0 warning, 2 info có sẵn** | Hai info định dạng ở test; dùng no-fatal-infos, không xóa cảnh báo để nhận đạt |
| iOS Release compile | **Đạt**, build-number16, no-codesign | Artifact mới chưa ký/chưa cài. Không thay kiểm tra Face ID/camera/push thật |
| Runtime application classes | **12/12 JAR snapshot khớp digest lớp application với bản build mới** | Không chứng minh thư viện nhúng/config/JVM đang chạy giống toàn bộ build mới; runtime không restart |
| Source/archive/app snapshot hash | **Đạt**, không có build input đổi; binary snapshot app16 khớp hash và codesign --verify --deep --strict đạt | R0 chưa restore DB hoặc rollback cả hệ thống |
| JSON/link/traceability | **Đạt**: 25 Markdown, 114 link local, 10 JSON evidence và 2 JSON example; đủ 24 mã yêu cầu/22 use case/18 nhóm nghiệm thu | Không phải test nghiệp vụ; xem documentation-verification.json |

Lần analyzer đầu chứa tool/test phụ cũ của workspace private, dừng ở 4 info; sau khi dùng bộ test/tool từ snapshot M còn 2 info và exit0. Giữ lịch sử trong log private, không coi lỗi thiết lập build ban đầu là lỗi ứng dụng đã được sửa.

Toolchain: [toolchain.json](evidence/r0-20261019/toolchain.json). Kết quả riêng: [backend](evidence/r0-20261019/backend-build.json), [web](evidence/r0-20261019/web-build.json), [iOS](evidence/r0-20261019/ios-build.json). Hash artifact nằm trong [artifact-verification](evidence/r0-20261019/artifact-verification.json). Kiểm tra tài liệu: [documentation-verification.json](evidence/r0-20261019/documentation-verification.json). Logs đầy đủ/private build input ngoài Git.

## 4. Runtime, dữ liệu và khả năng kiểm thử

- 12 endpoint backend thực (gồm hai Identity instance) trả200/UP qua HTTPS với xác minh CA/client certificate tương ứng; bridge iPhone và trang login web trả200. Đây không phải12service của đề bài: Analytics chưa có module/runtime, AI chưa được nghiệm thu; Directory là service bổ sung.
- Eureka entry trong private state không phản hồi health ở port18761; ghi UNVERIFIED, không tự khởi động hay xem như UP. Các route/bridge đang phục vụ điện thoại vẫn kiểm tra được. Deployment dùng discovery cần nghiệm thu riêng.
- iPhone kết nối dây/tunnel; app16 cài sẵn. Provisioning artifact hết hạn25/10/2026 theo metadata, cần quản lý ký lại trước các nghiệm thu dài ngày. Không xuất tên người/UDID/certificate hoặc credential vào evidence công khai.
- App16 **không có aps-environment**; FIREBASE_ENABLED=false; workspace build thiếu cấu hình Firebase iOS/Android private. R4 còn phụ thuộc provisioning/entitlement/config; không thử push và không tuyên bố OS sẽ giao thông báo khi app bị hạn chế/force-quit.
- Runtime test phụ thuộc Mac/mạng local. Chưa có domain/staging ổn định; origin/RP ID, HTTPS, cookie/BFF phải xác minh trên môi trường được chọn trước R4/R7.
- Fixture mới chỉ được đặc tả, **không seed hay tạo tài khoản/lượt khám** trong R0. Không gửi email, dùng ảnh thật hoặc gọi VNPT/AI provider.

## 5. Inventory migration

Chỉ SELECT metadata trên 10 DB audit thuộc container riêng loopback15432; không chạm remote. [Chi tiết](evidence/r0-20261019/migration-inventory.json).

| Service | Mốc history hiện tại |
|---|---|
| Identity | V17 |
| Directory | V1; có baseline marker lịch sử |
| Patient | V8 |
| Consultation | V3; có baseline marker lịch sử |
| Lab | V2 |
| Appointment | V6 |
| Queue | V12 |
| Notification | V3, schema history notification |
| EMR / Prescription | Không tìm thấy Flyway history; cần chuẩn hóa migration trước release |

Các SQL migration đã áp dụng đều có tên trong source và history ghi success. Baseline/schema marker không phải script bị mất. Đây không phải kiểm tra checksum Flyway đầy đủ hoặc so sánh từng bảng với entity/DBML; không chứng minh mọi bảng đều đã có migration. R0 không sửa JPA/DDL nên không tạo migration/DBML giả; debt DBML cũ, nhất là Queue, được ghi vào contract và R2/R7.

## 6. Phát hiện cần theo dõi ở các đợt sau

| Mã | Phát hiện | Đợt xử lý |
|---|---|---|
| GAP-R1-TRUTH | Admin account thao tác local; history load lỗi có thể thành rỗng | R1 |
| GAP-R2-WALKIN | Intake appointmentId=null nhưng Consultation vẫn bắt buộc lịch và ticket lookup theo lịch | R2, thêm context theo entry và kiểm tra quyền liên service |
| GAP-R2-ROOM | QueueConfig unique theo khoa, consumer ràng buộc một phòng | R2 trước mở nhiều phòng |
| GAP-R5-VERSION | Appointment producer aggregateVersion luôn1 | Chuẩn hóa contract/producer ở R2–R3, gate trước R5 |
| GAP-R4-PUSH | Chưa có entitlement/config push; staging chưa sẵn | R4/R7, chuẩn bị từ R0 |
| GAP-R7-SCHEMA | EMR/Prescription thiếu Flyway history, DBML một số nơi là thiết kế cũ | R2/R3 theo feature và gate release R7 |
| GAP-R7-DEPS | npm audit fresh lockfile trả 8 high, 0 critical, chủ yếu chuỗi tooling; chưa kết luận khả năng khai thác runtime | Rà/vá trong đợt bảo mật trước release; không auto audit fix ở R0 |

[Kết quả npm audit đã rút gọn](evidence/r0-20261019/web-dependency-audit.json) ghi package/severity/fixAvailable, không lưu response nhạy cảm. Các 8 cảnh báo của công cụ không được diễn giải thành 8 lỗ hổng ứng dụng đã tái hiện hoặc đã khắc phục.

## 7. Phạm vi thay đổi và bước tiếp theo

Chỉ tạo/cập nhật tài liệu, evidence đã loại secret và hướng dẫn liên quan baseline; source nghiệp vụ/build input được kiểm tra không đổi. Đã dùng GitNexus query/context cho context walk-in/event; không sửa function/class/method nên không chạy impact giả. Không stage/commit/push; mọi dirty code có trước giữ nguyên.

R0 là **đã chuẩn hóa baseline và thiết kế**, không phải hoàn thành R1–R7. Bước tiếp theo theo kế hoạch là R1: nối quản trị tài khoản Identity thật, sửa trạng thái lỗi bệnh sử, kiểm chứng auth/định danh đúng môi trường. Các phụ thuộc bên ngoài đã được xác định, không âm thầm chuyển thành đạt nghiệm thu.
