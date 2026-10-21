# Kết quả triển khai bàn giao ca khám — 16/10/2026

## Kết quả

Cập nhật sau triển khai: [kiểm tra thủ công trên Chrome ngày 16/10/2026](handover-manual-test-2026-10-16.md) đạt 28 mục, phát hiện và sửa lỗi hiển thị biểu mẫu khi API ca khám trả 403/404. Web đã build và triển khai lại. Các số liệu dưới đây là lần nghiệm thu triển khai ban đầu.

Đã lập kế hoạch, thực hiện và triển khai chức năng bàn giao giữa bác sĩ lên stack audit thật trên Mac: Hospital Web `http://localhost:5173`, Mobile Emulator `http://localhost:3000`, Gateway HTTPS `18080`. Không giả phản hồi API trong kiểm thử end-to-end. [Hướng dẫn và tài khoản demo](../../careflow-mobile-emulator-service/docs/CLINICAL-HANDOVER-GUIDE.md).

Source tại `.worktrees/security-integration`, nhánh `feature/dangkhoii/security-integration`. Không stage/commit/push trong lần triển khai này. `develop` ở worktree gốc và ba chỉnh sửa frontend có sẵn của người dùng được giữ nguyên. Worktree tích hợp đã có nhiều thay đổi bảo mật từ trước; báo cáo này không nhận toàn bộ diff đó là tính năng bàn giao mới.

## Hành vi đã triển khai

- Bác sĩ phụ trách gửi yêu cầu có lý do, SBAR, người nhận xác định; chỉ bác sĩ nhận tự tiếp nhận/từ chối. Trong lúc chờ, trách nhiệm không đổi.
- Bác sĩ có quyền điều phối chuyên môn được ADMIN cấp rõ ràng có thể khởi tạo ngoại lệ cùng khoa/phòng; không giả danh bác sĩ giao hoặc ký thay người nhận. Điều phối cùng phạm vi được hủy yêu cầu chờ mắc kẹt, ghi người hủy và lý do. ADMIN chỉ quản lý quyền, không có quyền ca lâm sàng từ vai trò quản trị.
- Kiểm tra hồ sơ bác sĩ hoạt động, cùng khoa/phòng; đối chiếu phòng thật, bệnh nhân, lịch hẹn bằng Queue lúc gửi và tiếp nhận. Khi không xác minh được thì từ chối và không chuyển trách nhiệm.
- Một yêu cầu chờ cho mỗi ca; khóa ca, version và khóa tải công việc theo bác sĩ bảo vệ thao tác đồng thời. Gửi lại cùng yêu cầu/tiếp nhận lặp an toàn. Hai ca IN_PROGRESS cùng gửi đến một bác sĩ chỉ có một lần tiếp nhận thành công.
- Ca giữ tác giả gốc; bác sĩ phụ trách hiện tại có trường riêng. Snapshot lâm sàng trước lúc nhận được lưu phía server, không trả ra workspace. Lịch sử, người thao tác, thời điểm và lý do được giữ trong DB.
- Quyền Consultation/Patient/EMR/Lab/Prescription theo bác sĩ hiện tại. Người nhận được đọc chỉ định/toa cũ; không được sửa/ký/hủy toa nháp của tác giả khác. Người cũ mất quyền lâm sàng của ca vừa giao. Trường tác giả toa/chỉ định không bị đổi.
- Giữ nguyên lượt khám, lịch hẹn, phiếu và thứ tự hàng đợi. Không check-in lại hoặc tạo phiếu mới. Hoàn tất Appointment bởi bác sĩ nhận phải xác minh Consultation đã hoàn tất, thực hiện sau commit để tránh đọc trạng thái cũ.
- Phòng có nhiều bác sĩ có cấu hình một bác sĩ chính khi đặt lịch; không lựa chọn bác sĩ ngẫu nhiên. Không thay bác sĩ của các lịch hẹn cũ chỉ vì đổi cấu hình này.
- Web có trang gửi/nhận/lịch sử, hộp thoại xác nhận kèm ghi chú, bộ chọn tìm kiếm dữ liệu thật và trang quản lý quyền điều phối. Trang khám phân biệt bác sĩ phụ trách với tác giả gốc, hiển thị toa nháp kế thừa chỉ đọc; header dùng đúng tài khoản đăng nhập.

## Thay đổi dữ liệu và contract

Consultation: Flyway V1–V3 bổ sung người phụ trách, version, bảng handover/khóa tải công việc/quyền điều phối/audit, tên bệnh nhân và snapshot. Migration được áp dụng trên DB audit đã có dữ liệu, không reset. Không sửa nội dung migration sau khi đã áp dụng. Directory có script idempotent bổ sung `primary_for_booking` và unique index bác sĩ chính đang hoạt động theo phòng. Hai sơ đồ DBML đã cập nhật.

Contract cập nhật: `08-doctor-consultation.md`, `05-appointment.md`, `09-prescription.md`, `10-laboratory-order.md`, cùng tài liệu luồng hệ thống. Caller registry của **cả hai Identity instance** đã bổ sung hai quyền provider cho appointment-service:

```
GET:/api/consultations/handovers/patient-access/**
GET:/api/consultations/appointment/**
```

Đây là quyền gọi liên dịch vụ theo provider, không thay thế kiểm tra token/session/DPoP và quyền bệnh nhân của người dùng. Khóa, token, cấu hình DB/caller runtime được giữ ngoài Git. Khi đưa sang môi trường khác phải triển khai các service/contract và caller scopes tương ứng.

## Bằng chứng kiểm tra

| Kiểm tra | Kết quả |
|---|---|
| Toàn bộ Maven reactor backend | **652 tests**, 89 XML reports, 0 failure/error/skipped |
| Next production build | Thành công, gồm route bàn giao doctor và admin |
| API workflow thật | **20 nhóm kiểm tra**: đặt lịch/check-in, ca/toa/lab, gửi/nhận/từ chối/hủy, concurrent acceptance, quyền trước/sau, coordinator, hoàn tất và bệnh nhân |
| API hồi quy coordinator/phòng | **5 checks**: đọc pending cùng phạm vi, đổi phòng thật rồi nhận bị từ chối, khôi phục phân công, coordinator hủy đúng actor, nhận lại hợp lệ |
| API bảo vệ cuối | **6 checks**: token bệnh nhân + header bác sĩ giả qua giả lập/Gateway/trực tiếp service bị 403, không token 401, snapshot không xuất JSON, NOI-010 giữ nguyên |
| Browser thật | **11 checks**: bộ chọn, gửi, nhận có xác nhận, admin cấp/thu hồi, pending không đọc ca, toa cũ chỉ đọc, header đúng tài khoản/tác giả, bác sĩ cũ không sửa được, Mobile giữ hành trình, không JS exception |
| Runtime cuối | **14/14**: 11 service chính, Identity phụ, Mobile→Gateway và Hospital Web |
| Whitespace/diff | `git diff --check` đạt |

API/browser dùng tài khoản riêng, DB local `127.0.0.1:15432`, các phiên test được đăng xuất sau khi dùng. Playwright không intercept/mock phản hồi. Các báo cáo JSON chỉ ghi tên ca kiểm tra, thời điểm và PASS, không lưu token/bệnh án:

- [20 nhóm API](handover-api-results.json)
- [5 kiểm tra coordinator/phòng](handover-coordinator-results.json)
- [6 kiểm tra bảo vệ cuối](handover-security-final-results.json)
- [11 kiểm tra trình duyệt](handover-browser-results.json)

Lệnh backend cần JDK 25 trong môi trường hiện tại:

```sh
JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-25.jdk/Contents/Home \
  mvn -DargLine=-Dnet.bytebuddy.experimental=true test -q
```

Lệnh thao tác dữ liệu thật được lưu tại `scripts/security/handover-e2e.mjs`, `handover-coordinator-regression.mjs`, `handover-browser.mjs`; đọc hướng dẫn trước khi chạy lại. Chúng tạo/chuyển trách nhiệm ca demo, không phải smoke chỉ đọc. Unit test dùng mock dependency trong phạm vi test; runtime/end-to-end dùng API thật.

Đọc DB chỉ để xác minh: Flyway V1–V3 thành công; không trùng yêu cầu chờ; không bác sĩ nào có hai ca IN_PROGRESS; 15 quyết định ACCEPTED tại thời điểm kiểm tra. Chín quyết định sau V3 đều có snapshot, tác giả và bác sĩ trước bàn giao khớp bản ghi lịch sử. **Sáu quyết định demo phát sinh trước V3 chưa có snapshot**, giữ nguyên dữ liệu lịch sử thay vì lấy bệnh án hiện tại giả làm quá khứ. Bộ demo ban đầu ở ROOM-07 vẫn giữ các tài khoản/lượt khám kiểm thử và lịch sử; bộ hướng dẫn hiện tại ở ROOM-08. Không chạy SQL reset/xóa các lượt đó.

Snapshot cấu hình/JAR/tệp và PostgreSQL đã lưu ngoài Git. Backup cuối:

```
~/Library/Application Support/CareFlow/mac-demo/backups/postgres-20261016-131939.sql.gz
```

Ca gốc `NOI-010 / Demo Nội c1d9dc`, Consultation `5f0a2c7b-ec39-4e39-9083-bcf694df27cd`, vẫn IN_PROGRESS; không hủy, hoàn tất hoặc bàn giao ca này để test. Một lịch hẹn gốc trùng khác chưa có phiếu là vấn đề cũ ngoài phạm vi, không được coi đã sửa bởi tính năng bàn giao.

## GitNexus và giới hạn kiểm chứng

Đã chạy query/context/impact trước chỉnh sửa các symbol; cảnh báo CRITICAL liên quan quyền Consultation/Lab/Prescription/Appointment đã được thông báo trước khi tiếp tục theo ủy quyền toàn hệ thống. Kế hoạch ghi phạm vi chi tiết. Index cuối đã làm mới: 15.176 nodes, 39.536 edges, 752 flows. Full-text/BM25 vẫn có lỗi xây index; graph/context hoạt động, không dùng việc thiếu kết quả tìm kiếm làm bằng chứng tính năng không tồn tại.

`detect-changes --scope all` báo 134 file, 177 symbols, 748 affected processes, CRITICAL cho **toàn bộ diff tracked của worktree tích hợp**, gồm thay đổi bảo mật cũ. Kết quả không tự tách phạm vi theo lượt làm việc và không bao quát đầy đủ file untracked; các file mới của handover đã được đọc source, index, build và kiểm thử bổ sung. Không stage/commit chỉ để chạy công cụ. Output chi tiết nằm trong `/tmp/careflow-handover-*` trên máy, không phải artifact có sẵn khi clone repository.

Phạm vi triển khai hiện tại là bàn giao cá nhân **cùng khoa/phòng**, chưa chuyển khoa/phòng hoặc bàn giao toàn bộ ca trực. Trang dùng tải lại thủ công, chưa có notification đẩy cho handover. Callback hoàn tất Appointment sau commit có retry idempotent, **chưa có durable outbox** tự khôi phục mọi lỗi liên dịch vụ. Kiểm thử đã bao gồm bác sĩ nhận lập/xác nhận toa và hoàn tất lượt không có CLS; không coi là chứng nhận toàn bộ thực hiện xét nghiệm, thanh toán và phát thuốc.
