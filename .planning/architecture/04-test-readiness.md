# Điều kiện kiểm thử và phụ thuộc — R0-1.0

**CURRENT21/10/2026 — R7:** backend1.000/1.000 không skip sau bản vá xung đột ghi và thứ tự sự kiện hủy; E08–E10/E13–E15 đã kiểm lại qua API thật trên DB scratch. Queue V14/DBML bổ sung dấu hủy bền vững; nâng từ V13 không đổi dữ liệu lượt khám. Artifact backend/web/native cùng worktree, iPhone21 đã cài/mở, restore scratch và phục hồi runtime sau reboot. [Biên bản R7](../deployment/r7-acceptance-2026-10-21.md) và ma trận18ca ghi phần còn OPEN; mốc R0/R5/R6 bên dưới là lịch sử. Không quy đổi test hoặc bản cài thành nghiệm thu phần cứng/push/chuyên môn.

**CURRENT21/10/2026 — R6:** đã kiểm Java199/Python12, Gateway/mTLS/worker local và browser với dữ liệu tổng hợp. [Biên bản R6](../ai-service/r6-acceptance-2026-10-21.md) nêu phần tích hợp đã đạt và phần chuyên môn/mic thật còn mở; không dùng bảng baseline để suy ra AI vẫn chỉ có prototype hoặc đã đạt chất lượng lâm sàng.


**Cập nhật R5 ngày21/10/2026:** Analytics đã có module/runtime,30 test service (14 PostgreSQL thật),3 test guard export, đối soát API/broker/browser và restart. [Biên bản R5](../analytics-service/r5-acceptance-2026-10-21.md) cập nhật hiện trạng; bảng R0 dưới đây giữ lịch sử baseline, không mô tả tình trạng Analytics hiện nay.

## 1. Ranh giới môi trường

R0 chỉ đọc health/Flyway metadata trên DB audit riêng loopback15432 và build ở bản sao ngoài repository. Không chạy seeder, không reset queue, không thay trạng thái lâm sàng và không gửi mail/CCCD tới provider. DB remote100.116.233.60 không thuộc phạm vi phép thử này.

Baseline native là app16 trên iPhone đang kết nối. Bản iOS build lại trong R0 không ký và không cài thay bản16. Web production build trong scratch không thay dev server5173. Build Maven bỏ chạy test để xác minh toàn bộ source đóng gói được; kết quả test các đợt trước là lịch sử riêng, không nhận là test mới của R0.

## 2. Bộ fixture cho R1–R7

Đây là **đặc tả fixture chưa được seed**. Manifest máy đọc được ở [acceptance-fixture-plan.json](evidence/r0-20261019/acceptance-fixture-plan.json). Mỗi lần chạy cần runId riêng, LocalDate.now theo timezone nghiệp vụ, và mapping ID thực tạo qua API hợp lệ. Không hardcode ID của ca đã hoàn tất hoặc sửa SQL để ép thành công.

| Nhóm | Dữ liệu tổng hợp cần chuẩn bị | Điều kiện phải giữ |
|---|---|---|
| Người dùng | Hai PATIENT khác owner, hai DOCTOR để phân công/bàn giao, STAFF tiếp nhận và cấp phát, LAB_TECHNICIAN, ADMIN | Tài khoản hoạt động, credential hợp lệ; passkey thật/ảo được ghi đúng loại; mật khẩu demo chỉ ở hướng dẫn01 |
| Hồ sơ | Hồ sơ bản thân, người nhà thuộc owner, hồ sơ người khác, hồ sơ walk-in chưa có user | Không auto liên kết/gộp; nhân viên chỉ xem view được phép |
| Directory | Một khoa có hai phòng active, một phòng inactive, hai bác sĩ/phân công hợp lệ, LAB và PHARMACY | Chọn từ API/searchable controls; không nhập master ID/text tự do; ngoài phân công phải403 |
| Lịch | Ngày hiện tại/còn slot, slot đầy, ngày nghỉ, cấu hình tương lai, giá dịch vụ hiệu lực | Đủ thời gian để chạy đúng ngày; snapshot booking không đổi theo giá mới |
| Queue | Lịch đã check-in/chưa check-in, WALK_IN, MISSED với từng policy, RESULT_REVIEW, LAB_EXECUTION, PHARMACY_DISPENSING | Tạo qua event/command thật; không dùng dữ liệu quá hạn để báo luồng hôm nay đã đạt |
| Lâm sàng | Ca không CLS, ca CBC, ca nhiều order, toa nháp/đã ký, yêu cầu tái khám hợp lệ/hết chỗ | Cùng patient/consultation; chỉ bác sĩ đúng quyền ký; ca hoàn tất chỉ đọc |
| Tệp | PDF/ảnh tổng hợp nhỏ, quá quota, sai MIME/đuôi, file cần cách ly | Không dùng bệnh án thật hoặc malware thật; scanner outage ở môi trường riêng |
| Thiết bị/thông báo | Hai installation/tài khoản, token đổi, quyền bị từ chối, foreground/background/không chạy | Không gửi chéo owner; ghi trạng thái thực tế của OS và thời điểm thử |
| Analytics/AI | Dataset event có đáp án tính sẵn; ca AI tổng hợp có nguồn đã rà soát | Replay/backfill chống trùng; không coi mock là provider hoặc đánh giá chuyên môn thật |

Fixture do service sở hữu tạo/seed theo contract khi đợt đó triển khai. Không bật reset demo vào môi trường vận hành để có dữ liệu nghiệm thu. Nhóm background event không có user token dùng cơ chế service/event được cấp quyền, không endpoint vô danh.

## 3. Điều kiện đã kiểm tra và còn thiếu

| Hạng mục | Bằng chứng/điều kiện | Trạng thái R0 |
|---|---|---|
| Source/backend/web/native | Snapshot + hash; main pull ff-only; build lại ngoài source tree | Chi tiết [biên bản baseline](05-r0-baseline-results.md) |
| Backend/bridge/web | GET health/login với kiểm tra CA và client cert khi cần | Evidence [runtime-readiness](evidence/r0-20261019/runtime-readiness.json); health không thay giao dịch E2E |
| iPhone | Devicectl thấy máy nối dây và app1.0.0 build16; entitlement đọc từ artifact đã lưu | Thiết bị sẵn cho R1–R3; chưa chạy lại Face ID/camera/CCCD thật trong R0 |
| APNs/Firebase | App16 không có aps-environment; không thấy GoogleService-Info.plist/google-services.json trong workspace build | **Phụ thuộc R4 còn thiếu**: provisioning/entitlement và cấu hình private phù hợp; không thử giao push |
| Staging | Đang dùng bridge Mac/local và origin dev | **Chưa có staging ổn định**: domain/HTTPS/network, RP ID/origin passkey, cookie/BFF và private deployment config |
| Email | Có bằng chứng Delivered các đợt trước, không kiểm tra mới bằng gửi thư | R1 cần nghiệm thu UI/hộp thư; không tự gửi hoặc thay key trong R0 |
| Analytics | Có contract lịch sử, chưa có module/runtime | R5 cần consumer/projection/backfill và KPI chuẩn |
| AI | Có prototype nhưng chưa có bộ ca/nguồn/chuyên gia nghiệm thu cho bàn giao | R6 cần chọn model/môi trường và nguồn được phép; giữ hoãn, không gọi provider bằng dữ liệu thật |
| Danh tính tự động | Không có liveness/đối chiếu khuôn mặt/nguồn giấy tờ tự động | Scope hiện tại thủ công; KYC-A là nhánh riêng nếu phải đạt tự động |

Provisioning hiện có ngày hết hạn trong evidence; việc ký/cấp lại phải được quản lý trước khi nghiệm thu dài ngày. Không suy ra quyền APNs từ việc app cài được, cũng không suy ra loại tài khoản Apple chỉ từ việc thiếu entitlement.

## 4. Gate từng cấp

1. **Build:** checkout/snapshot đúng, lockfile đúng, compile/package thành công; lưu version công cụ và hash artifact.
2. **Functional:** unit/contract phù hợp cho validation/state mới; ghi rõ mock/test double.
3. **Integration:** API/DB/broker thật trong môi trường riêng; quyền, race/idempotency, dependency outage và dữ liệu bền vững.
4. **Native:** iPhone camera/GPS/Face ID/files/push theo luồng; kết quả browser emulator không nâng thành đạt native.
5. **Full acceptance:** E01–E18, cùng manifest mobile/web/backend, tài liệu khớp; AI có rà soát chuyên môn và tự động eKYC chỉ khi thuộc scope đã chốt.

## 5. Khoảng trống mới ghi nhận khi chuẩn hóa

- GAP-R2-WALKIN: nullable appointmentId ở intake chưa đi qua validation/context Consultation hiện tại; cần integration test thật E08, không chỉ UI intake.
- GAP-R2-ROOM: unique QueueConfig.departmentId và kiểm tra event theo một phòng phải được migration trước mở nhiều phòng.
- GAP-R5-VERSION: AppointmentEventService hiện ghi aggregateVersion=1; không dùng làm thứ tự cập nhật projection tin cậy trước nâng contract/producer.
- GAP-R7-SCHEMA: một số service dùng Hibernate update hoặc Flyway baseline marker; inventory không chứng minh mọi bảng có migration hoặc DBML khớp entity. Chốt migration/DBML trước nâng môi trường release.
- GAP-R7-DEPS: npm audit trong bản cài lockfile R0 có cảnh báo high, được lưu trong evidence; cần rà tác động/vá ở đợt bảo mật phù hợp, không chạy audit fix tự động làm đổi baseline trong R0.

Các gap là kết quả đọc code/công cụ, không phải tính năng đã được sửa. Không có dữ liệu nghiệp vụ mới được tạo bởi bộ fixture ở trên.
