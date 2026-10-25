# R5 — Biên bản kiểm chứng kỹ thuật,21/10/2026

**Kết luận:** R5-01–04 đã triển khai và kiểm chứng trên stack Mac riêng. Có Analytics Service/schema riêng, API và dashboard ADMIN thật, snapshot export nguồn được cấp quyền, consumer chống trùng, đối soát và dựng lại bền vững. Không phải xác nhận đã triển khai production hoặc nghiệm thu tải lớn.

## Phiên bản và phạm vi

- Backend/web: `feature/dangkhoii/security-integration`, HEAD `5a8c6e5` cộng thay đổi chưa commit R0–R5. Root vẫn `develop/e5fc9c1`; không chuyển nhánh hoặc git add/commit/push trong R5.
- Native: không chỉnh sửa trong R5, vẫn iOS1.0.0(20) từ worktree riêng. Không đóng nghiệm thu iPhone/push còn mở ở R3–R4. AI vẫn dành cho R6.
- Runtime: PostgreSQL container loopback15432, RabbitMQ15673/management15674, Gateway HTTPS18080, Analytics HTTPS18091; ba nguồn Appointment18083/Queue18084/Consultation18086. mTLS, caller key và phiên/proof đều hoạt động thật. Không chạm DB remote100.116.233.60.
- Nguồn dữ liệu là DB vận hành của môi trường test, gồm dữ liệu demo/các lượt nghiệm thu trước. Không dùng dữ liệu người thật, CCCD/bệnh án thật hoặc nhà cung cấp AI/eKYC. Nhãn REAL phân biệt nguồn API thật với seed tổng hợp riêng của Analytics, không khẳng định dữ liệu này là hoạt động bệnh viện thật.

## Các phần hoàn thành

| Hạng mục | Bằng chứng |
|---|---|
| R5-01 — KPI | [Contract R5_V1](r5-contract-2026-10-21.md): cohort ngày lịch/INITIAL visit, Việt Nam, tách walk-in/missed/review, mẫu số và mốc thời gian hợp lệ |
| R5-02 — ingestion | Journal/projection/receipt/checkpoint riêng; source-owned export mTLS+key, không đọc xuyên DB; version/timestamp không lùi; event cũ đọc current snapshot; cursor tiếp tục sau lỗi |
| R5-03 — dashboard | `/admin/analytics`, bộ lọc ngày/khoa/phòng thật, biểu đồ/bảng; ADMIN-only GET/reconcile/rebuild, no-store; browser loading/empty/error/retry/403 và ngoài-click đều đạt |
| R5-04 — đối soát | Dataset biết trước, duplicate/concurrent/late, missed2lần, review, qua nửa đêm, source outage, cursor, restart và journal rebuild; chín chỉ số live khớp oracle độc lập |
| DB/build/vận hành | Migration5 bảng; [DBML](analytics-db.dbml) sinh/đối chiếu PostgreSQL thật. Root Maven, Dockerfile module, Gateway/discovery, compose secure dùng bởi CI production và launcher đã nối Analytics |

Bộ7 snapshot tổng hợp cho kết quả:2 lịch,1 hủy,2 lượt đến (1có hẹn/1không hẹn),2hoàn tất;1 lượt từng lỡ nhưng2 lần lỡ;1review. Chờ ban đầu trung bình7.5phút/P50=7.5/P95=9.75, chờ review5phút, thời gian ca trung bình30phút. Seed có nhãn synthetic, restart không nhân đôi và REAL không đọc seed này.

## Kiểm thử thực hiện

| Bộ kiểm tra | Đạt | Bỏ qua | Ghi chú |
|---|---:|---:|---|
| Analytics |30|0|14 ca PostgreSQL thật,7 tính KPI,5 security/HTTP,4 consumer |
| Common |49|0|Gồm3 ca guard export mới |
| Appointment |82|5|Bộ conditional DB riêng đã có từ trước không bật trong reactor này |
| Queue |75|7|Conditional DB riêng có từ trước |
| Consultation |102|7|Conditional DB riêng có từ trước |
| Gateway |35|0|Build/package và regression route/auth |
| Tổng Java |373|19|392 test được phát hiện; không tính skip là đạt |

- Maven reactor và Gateway package thành công. Analytics14 PostgreSQL không bị skip; test DB riêng `careflow_r5_analytics_test`, runtime DB riêng `careflow_r5_analytics`.
-7 nhóm API live: sai role kể cả forged header, sai cert/key, bounds/phân trang, whitelist payload, chín KPI đối chiếu nguồn, range/empty/internal-route, RabbitMQ duplicate/late và journal rebuild. [Kết quả](evidence/r5-20261021/live-api-results.json).
-2 luồng nghiệp vụ mới: PATIENT tạo lịch còn slot ở ngày tương lai qua API thực; Analytics nhận event nhưng không tính WAITING là arrived. Hủy chính lịch thử bằng API, cancellation tăng1, booked không tăng lần nữa, arrived giữ nguyên. Lịch thử đã CANCELLED; không xóa hoặc sửa SQL các lượt cũ. [Kết quả](evidence/r5-20261021/booking-event-results.json).
-8 nhóm browser: ADMIN đọc thật/loading, custom picker Directory/ngoài-click, date/chart/freshness, roomfilter, empty,503/retry, hai nút vận hành thật, DOCTOR403. [Kết quả](evidence/r5-20261021/web-results.json).
- Next16.4.2 production `build --webpack` đạt trong bản sao scratch; typecheck production đạt. Dev cache `.next/dev` ban đầu có type artifact lỗi, không sửa generated file của source để che lỗi. Browser dùng source thực trên5173; ảnh dashboard đã xem trực quan, không có dữ liệu y tế cá nhân.
- Restart JVM Analytics thật giữ nguyên các metrics PostgreSQL; endpoint thiếu ngày trả400 qua Gateway, ngày/UUID sai cũng400. [Kết quả](evidence/r5-20261021/restart-results.json).
- Compose secure kiểm tra cấu trúc `config --no-interpolate --quiet` đạt. Base+prod legacy được giữ nguyên từ trước R5: không ghép Analytics secure vào stack legacy HTTP. Chưa build/chạy image Docker; không chạy production compose.
- Mac launcher `start` giữ toàn bộ tiến trình khỏe và có Analytics; `status`15/15OK. Chọn Python hệ thống trong `.command`, không sửa TLS verifier/CA cũ. JAR runtime đã gom vào thư mục audit private để snapshot/khởi động lại đúng artifact, giữ nguyên bytes các JAR không thuộc R5. Backup cấu hình/JAR/tệp và pg_dumpall nén đã tạo thành công ngoài Git; không diễn tập restore.

**Test double được tách rõ:** source outage/cursor-interruption dùng SourceClient mock trên PostgreSQL thật để kiểm chứng giữ dữ liệu/cursor; concurrent/version test cũng dùng facts tổng hợp. UI503 là response được chặn có chủ đích trong browser test. Phân trang, mTLS, broker duplicate/late, đặt/hủy, dashboard và rebuild/restart được thử riêng trên kết nối thật. Không coi mock là bằng chứng ngắt mạng toàn hệ thống.

Hai lỗi trong vòng kiểm thử đã sửa: nhóm khoa/phòng không còn tách đôi chỉ vì nguồn thiếu departmentCode khi cùng departmentId; thiếu ngày báo cáo trả400 thay vì500. Initial readiness check private gặp Python strict X.509 với CA test thiếu AKI, JVM/service vẫn UP; launcher dùng Python hệ thống giữ xác minh CA/hostname. Test cursor từng có lỗi restub Mockito, đã sửa cách mô phỏng phục hồi và chạy lại đạt; không thay logic nguồn để ép test.

## Bảo toàn source và cấu hình

Trước R5 đã có backup source/private state và dump ba DB nguồn ngoài Git. Đối chiếu hash baseline riêng cho R5 cho thấy các file cũ chỉ đổi ở module/build/deployment/Gateway/discovery/Sidebar/launcher/tài liệu liên quan; không sửa hàm nghiệp vụ R0–R4 hoặc native. Không add/commit/push. Những thay đổi cũ vẫn nguyên trạng chưa commit. Khoảng trắng thừa trong source export Queue đã dọn; package lại và so sánh toàn bộ classes/resources trong JAR xác nhận không đổi bytecode/resource runtime.

Cấu hình private chỉ thêm caller Analytics, ba scope Analytics cho Gateway ở hai Identity instance, export key ba nguồn, discovery/route Analytics và service mới. Khóa Resend/JWT/caller cũ không đổi; không gửi email mới trong R5. [Delta chỉ có tên đường dẫn, không có secret](evidence/r5-20261021/private-config-delta.json).

GitNexus đã reindex và force rebuild graph/FTS sau khi chỉ mục incremental có dấu hiệu mất/corrupt metadata; force rebuild thành công với18980nodes/44717edges/855flows. Công cụ báo thiếu/truncated một số Java/cross-language edges; symbol R5 có lúc UNKNOWN ngay cả sau index, đã đối chiếu trực tiếp controller→service→store/source và import web. Impact Sidebar LOW,1caller/0process. Impact hàm TLS launcher có kết quả CRITICAL với2caller/132process và metadata lẫn; không sửa hàm này. Full-worktree detect-changes báo CRITICAL vì tính cả toàn bộ R0–R4 chưa commit, không chỉ R5; dùng manifest/hash trước R5 để đối chiếu phần delta. Không bỏ qua cảnh báo hoặc coi graph thiếu cạnh là không có ảnh hưởng.

## Giới hạn còn lại

- Chưa production/staging đa máy, stress/load, chạy image mới, reboot hoặc restore DB toàn Mac. Có compile/build, config validation và runtime host thật, không đồng nhất chúng với nghiệm thu deployment production.
- Projection là eventual consistency: sự kiện kích hoạt đọc nguồn; quét định kỳ có cursor/deadline. Nguồn đã xóa record trước Analytics quan sát không thể backfill. Dashboard hiển thị coverage/độ mới và giữ snapshot khi lỗi.
- Không có doanh thu, no-show rate chuẩn, lab-TAT, thống kê AI/thông báo hoặc role quản lý khoa riêng trong R5; những endpoint lịch sử TARGET trong docs cũ không được trình bày là API đã chạy.
- Cần chính sách lưu trữ/giám sát journal, source-event DLQ và nguồn khi triển khai production. Broker có thể giữ payload sự kiện gốc, không đưa lên báo cáo hoặc Git.
- Không nâng trạng thái R3/R4: Firebase/APNs và các tương tác iPhone chưa nghiệm thu riêng vẫn còn mở. Không tích hợp AI trong R5.

Hướng dẫn thao tác duy nhất: [trang22](../../huong-dan-su-dung/22-thong-ke-va-doi-soat-r5.md).
