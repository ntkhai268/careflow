# R7 — Biên bản kiểm thử tích hợp và bàn giao kỹ thuật

Ngày21/10/2026. Nền `907cba222c299329de6d87f9acbb9ec3b9e4c4f8`, nhánh `feature/dangkhoii/release-r7`, worktree `release-r7`. Đã cập nhật main trước triển khai. Chưa stage, commit hoặc push; develop và worktree R6 được bảo toàn.

**Kết luận: bàn giao kỹ thuật thử nghiệm; nghiệm thu đầy đủ R7 còn OPEN.** Không dùng số test đạt để kết luận phần cứng, push hoặc chất lượng lâm sàng đã đạt. Hướng dẫn thao tác chuẩn duy nhất là [trang25](../../huong-dan-su-dung/25-nghiem-thu-va-ban-giao-r7.md).

Candidate `r7-929e88327e54c311`: **8/18 ca PASS,9 PARTIAL,1 BLOCKED**. Các ca PASS là E08–E10,E13–E15,E17,E18; phần còn lại cần nghiệm thu thiết bị, push hoặc chuyên môn theo bảng cuối. PARTIAL không đồng nghĩa toàn bộ chức năng chưa được lập trình.

## Thay đổi riêng R7

- Bật toàn bộ bộ hồi quy PostgreSQL khi chạy Maven reactor; bổ sung fixture outbox/completion guard thật cho `WalkInVisitPostgresTest`, không nới policy production.
- Sửa lỗi Gateway trả500 khi upstream mất kết nối/DNS: trả503 có envelope an toàn, không lộ địa chỉ nội bộ; không ghi đè lỗi quyền/nghiệp vụ hoặc response đã commit. Bốn regression mới và phép thử upstream thật bổ trợ nhau.
- Phép thử requeue hai phiên tái hiện optimistic locking trả500 dù DB ngăn ghi trùng. Bổ sung handler lỗi optimistic write conflict dùng chung trả409 với thông báo tải lại, không lộ entity/ID/SQL. Bốn ca MockMvc kiểm409, giữ403 và lỗi500 không liên quan; hai ca409 thất bại đúng trước sửa. Kiểm lại concurrent API thực nhận200/409, không tạo thêm lượt.
- Phép thử sự kiện thật tái hiện hủy đến trước xác nhận vẫn cấp phiếu chờ. Queue V14 bổ sung dấu hủy bền vững và khóa giao dịch theo lịch để ngăn tái tạo phiếu sau cancel/restart/cạnh tranh. Sáu hồi quy PostgreSQL và phát lại envelope thật đều đạt. Xem [contract thứ tự sự kiện](../queue-service/r7-event-ordering-contract-2026-10-21.md).
- Đồng bộ deployment target iOS15 trong project/Pods. Android release bắt buộc cấu hình ký riêng, bỏ fallback debug signing. Sửa hai cảnh báo lint ở test native.
- Thêm runner kiểm backend, snapshot/restore scratch có guard, manifest source/artifact và cổng nghiệm thu18ca. Cổng deploy backend/frontend từ chối candidate chưa đủ bằng chứng. Thêm workflow kiểm tra thủ công; chưa chạy workflow trên GitHub.
- R7 thêm bảng `queue.appointment_cancellations` bằng V14 và đồng bộ DBML; backfill lịch hủy cũ. Migration/DBML AI V2 và Prescription V3 là phần R6 mang theo, không sửa migration cũ. Không thay key Resend hoặc gửi dữ liệu định danh tới provider ngoài. Mail bị tắt riêng trên clone Identity dùng thử; cấu hình live được giữ nguyên.

## Kết quả có thể đối chiếu

Các JSON đã loại credential/PHI ở [thư mục bằng chứng](evidence/r7-20261021/). Manifest ghi source dirty và hash artifact; không giả lập một commit chưa tồn tại. Quyền truy cập backup, signing và raw log riêng nằm ngoài Git.

| Phạm vi | Kết quả và giới hạn |
|---|---|
| Backend | `clean verify` cuối: **1.000 test,0failure,0error,0skip**, các bộ PostgreSQL bật; Queue88/Common53. Log/JSON lưu bền vững ngoài `/tmp`. Các lần990/994 trước đó là mốc lịch sử, không dùng thay kết quả candidate cuối. |
| Web | Production webpack build/TypeScript đã đạt; kiểm phiên trong bộ nhớ, npm dependency và browser đăng nhập/reload/logout được kiểm lại trên runtime R7. Không coi web là nghiệm thu app native. |
| Mobile | iOS1.0.0(21) ký/cài/mở thật; Android release21 build/verify chữ ký thử riêng. Flutter analyze sạch; bộ widget/unit có test skip ghi riêng. Chưa nghiệm thu cảm biến/OS/native user flows. |
| Runtime |14 JVM nghiệp vụ/Gateway/Directory/Identity replica dùng JAR R7; đủ12service yêu cầu cùng Directory hỗ trợ. Eureka được build nhưng môi trường này dùng discovery cấu hình tĩnh, không tuyên bố đã chạy Eureka. |
| Phục hồi |12 DB đã dump/restore scratch nhất quán và kiểm hash/số dòng; lần kiểm sau reboot có4 tệp upload cùng19file khóa tham chiếu được sao lưu riêng. Không restore đè DB gốc. |
| Tích hợp trên clone |Đã chạy hành trình upload→CLS→đọc kết quả→bàn giao→toa→tái khám→quầy thuốc; sai bác sĩ bị403, replay không tạo trùng. Analytics đối chiếu nguồn qua API thật. Một phần raw output nằm trong `/tmp` đã mất sau reboot; báo cáo phục dựng chỉ là tường thuật phép thử, **không thay bằng chứng máy đọc bền vững**. Các ca tương ứng giữ PARTIAL nếu chưa lặp lại đủ. |
| Kiểm lại E08/E09/E10 |Sau bản vá, sáu DB scratch cùng bảy JVM thật kiểm tiếp nhận có/không tài khoản→bác sĩ hoàn tất, idempotency/race, bốn nhánh requeue và pause; lịch/slot/ngày nghỉ, chống sửa lịch mất chỗ đã đặt, event tạo phiếu thật và hủy trả chỗ. Bằng chứng mới không phụ thuộc log tạm đã mất. Không thay cho nghiệm thu native. |
| Kiểm lại E13 |Cắt TCP sau header201 nhưng trước khi đọc body, gửi lại cùng key chỉ có một lịch. Lần hủy lặp trả400 đã hủy theo contract hiện hành, đọc lại xác nhận CANCELLED. Bắt đúng envelope nguồn và đưa hủy tới Queue trước xác nhận chưa từng xử lý: không cấp phiếu. Kết hợp chứng cứ outage/restart/replay và hồi quy PostgreSQL. Không ép trạng thái bằng SQL. |
| Kiểm lại E14 |Bàn giao chấp nhận đồng thời giữ tác giả, thu hồi ca/hồ sơ/Lab/PDF khỏi bác sĩ cũ dù token còn sống; người nhận đọc đúng byte PDF và không ký toa kế thừa. Thu hồi điều phối chặn handover ngoại lệ đang chờ. Clone Identity/Directory/Patient/Appointment kiểm xác minh/consent/liên kết/thu hồi: mã hoặc đề xuất không tự cấp quyền; revoke chặn cả lịch hẹn đã có, giữ hồ sơ/audit. Attestation danh tính là fixture tổng hợp, không phải nghiệm thu giấy tờ vật lý. |
| Kiểm lại E15 |DB Analytics scratch, Gateway/broker riêng; nguồn Appointment/Queue/Consultation thật chỉ đọc. Toàn bộ headline/rate/timing/percentile/quality KPI khớp oracle từ export; phân trang/whitelist/quyền, ba delivery/hai receipt, late hint không đảo cancellation, journal rebuild và JVM restart giữ mọi metric. Identity phục vụ phiên kiểm thử riêng rồi logout; không sửa tài khoản hoặc dữ liệu nghiệp vụ ở stack gốc. |
| Nâng Queue V13→V14 |Khôi phục dump V13 vào DB mới rồi boot JVM candidate, backfill đúng22 lịch hủy cũ, hash nội dung mọi queue entry giữ nguyên. Dừng listener broker riêng trong phép thử migration để không trộn dữ liệu sự kiện. V14 là additive; không gọi rollback Queue cũ là đã bảo toàn invariant mới. |
| Outage/rollback |AI upstream dừng từng tái hiện500 trước sửa; sau reboot đã kiểm lại503 trên Gateway tạm trong khi Identity/Directory vẫn200. Prescription JAR cũ/mới đều boot và giữ nguyên hash/số dòng mọi bảng trên DB scratch cùng schema; broker thử tách riêng và được dừng sau phép thử. Đây không phải thử hạ migration hay failover production. |

## Sự cố môi trường và khả năng lặp lại

Chạy nhiều JVM/build trên Mac16GB từng làm Docker không phản hồi; nguyên nhân gốc chưa được chứng minh. Đã dừng các JVM clone theo PID/JAR sở hữu, khởi động lại Docker và chỉ bật hai container audit. Không prune/xóa volume hoặc bật container dự án khác. Các mốc990/994 có trước bản sửa thứ tự sự kiện; lượt cuối đạt1.000/1.000. Không chạy full Maven/native build đồng thời với cụm clone.

Mac reboot sau lần kiểm ban đầu làm mất runtime/log trong `/tmp`. Đã phục hồi TLS/key/environment từ backup riêng, chép lại JAR R7 đã kiểm, giữ nguyên DB volume, mở lại web/cầu nối/worker và chạy smoke mới. Snapshot runtime mới được lưu bền vững ngoài Git. Bằng chứng công khai ghi rõ phần lấy từ Surefire/device JSON còn tồn tại và phần kiểm mới; không tạo lại log giả.

Chứng chỉ test Analytics/AI cũ thiếu Authority Key Identifier từng bị Python3.14 strict từ chối. R7 đã cấp lại leaf có AKI/SKI, SAN, server/client EKU, giữ nguyên root CA và private key, khởi động lại hai service/worker và cập nhật backup. Chain/hostname strict kiểm trực tiếp đạt; không tắt TLS verification và không cần cài lại iPhone vì CA/Gateway không đổi. Đây vẫn là chứng chỉ test, không phải chứng nhận TLS production.

## Dependency và giới hạn bảo mật

Npm production audit tại thời điểm kiểm không có advisory. OSV kiểm304 tọa độ Maven; vẫn có ba advisory Spring được đánh giá **có điều kiện**, không phải backend sạch lỗ hổng. Điều kiện hiện tại không có SSE view fragments, XsltView, hoặc tổ hợp WebFlux functional/DispatcherServlet tương ứng; guard quét surface và thời hạn review18/11/2026 vẫn áp dụng. Nguồn vendor: [CVE-2026-47890](https://spring.io/security/cve-2026-47890/), [CVE-2026-47884](https://spring.io/security/cve-2026-47884/), [CVE-2026-47892](https://spring.io/security/cve-2026-47892/). Khi dùng các surface đó hoặc đổi dependency phải đánh giá lại/nâng bản phù hợp; không mặc định miễn trừ vĩnh viễn.

GitNexus đã phân tích phạm vi R6 mang theo và R7:178symbol/39process, aggregate **CRITICAL** liên quan AI, Prescription và UI khám. Kết quả đầy đủ `partial=false`, `truncated=false`; đã force rebuild graph/FTS sau lỗi FTS incremental. Cảnh báo không bị hạ mức vì test đạt. Common error handler:7symbol upstream/MEDIUM. Callback/constructor RabbitMQ UNKNOWN được xác nhận qua `@RabbitListener`, topology, test constructor và broker thật; `create/cancel` LOW,1caller. File mới/migration/DBML được rà bổ sung vì diff graph không thay rà file untracked. Launcher start/status LOW/lower-bound, smoke browser UNKNOWN đã đối chiếu entrypoint/route thực. Không có commit nên không bỏ qua cổng pre-commit.

## Phần chưa thể đóng

| Mục | Điều kiện còn thiếu | Chịu trách nhiệm nghiệm thu |
|---|---|---|
| E01–E07,E11 và passkey/khóa thiết bị |Thao tác iPhone21/camera/GPS/Face ID/OS authenticator thật, bằng chứng cùng candidate |Chủ dự án cùng người kiểm thử |
| E12 push |Firebase/APNs server/native config và entitlement `aps-environment`; bản21 hiện không có |Chủ tài khoản Apple/Firebase, nhóm mobile |
| E16 AI |Nguồn bệnh viện và bộ ca do chuyên gia duyệt, chuyên gia đối chiếu đáp án, giọng/micro bác sĩ độc lập |Bác sĩ phụ trách và nhóm AI |
| Phát hành thực tế |TLS chuẩn, custody/backup độc lập, RPO/RTO/load/monitoring, signing distribution, staging UAT |Nhóm vận hành và đơn vị sử dụng |

OCR/QR local + đối chiếu trực tiếp là phương thức đã chốt, không gọi eKYC tự động hoàn toàn. Prototype voice lịch sử0/313 exact chưa được thay bằng một bộ đánh giá mới; integration pass không xóa hạn chế. Clinical assistant hiện là tra cứu trích đoạn có nguồn, chưa phải mô hình tự suy luận phác đồ.

[cases.json](evidence/r7-20261021/cases.json) là ma trận18ca; [gate.json](evidence/r7-20261021/gate.json) phải để OPEN cho đến khi đủ điều kiện. Không tuyên bố dự án100% hoàn thành hoặc cho phép deploy production từ bản này.
