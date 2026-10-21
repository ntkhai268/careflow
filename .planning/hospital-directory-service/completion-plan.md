# Hospital Directory Service — Kế hoạch hoàn thiện

Ngày: 2026-10-14. Baseline: develop / 8bf77e2. Trạng thái: đề xuất, chưa triển khai.
Theo [kế hoạch tổng](../system-completion-plan.md); G0–G5 tương ứng các giai đoạn trong kế hoạch tổng. Không dùng bảng này để xác nhận tính năng đã làm xong.

## Phạm vi và khoảng trống

Master data cơ sở/khoa/phòng/nhân sự và khả năng/phân công phục vụ. Appointment sở hữu lịch đặt và capacity tiêu thụ.

Khoảng trống/điểm cần đối chiếu: Public GET quá rộng khi qua Gateway; phân công chủ yếu phòng hiện tại, cần phiên/hiệu lực và nhiều phòng cùng khoa.

## Phụ thuộc

Identity User IDs/capability; Gateway allowlist; Appointment và Queue contract room/session.

## Backlog và nghiệm thu

| ID | Giai đoạn | Công việc | Tiêu chí nghiệm thu |
| --- | --- | --- | --- |
| DIR-01 | G1 | Phân biệt DTO public với assignment/profile nội bộ; admin và internal APIs xác thực caller/user. | GET assignments ADMIN qua Gateway hoạt động; public không lộ thông tin nội bộ. |
| DIR-02 | G2 | Assignment có ngày/phiên hiệu lực, room/servicePoint và capability; scoped STAFF reception/pharmacy/coordinator thay vì mọi STAFF cùng quyền. | Hết phân công bị từ chối; LAB ở A không dùng assignment B; role và capability cùng được xét. |
| DIR-03 | G2 | Room capability, trạng thái active/paused và doctor active; định nghĩa phòng thay thế cùng năng lực. | Queue/Appointment không chuyển bệnh nhân tới phòng không đủ khả năng hay bác sĩ inactive. |
| DIR-04 | G2 | Chốt room/session keys với Appointment; không duy trì booked_count cạnh tranh ở hai service. | Một khoa nhiều phòng có IDs rõ; nguồn capacity duy nhất. |
| DIR-05 | G3 | Quy trình ngừng phòng/đổi bác sĩ báo các service, không xóa master đang được tham chiếu. | Có impact/bàn giao các lượt hiện hữu và audit; không orphan consultation. |
| DIR-06 | G4 | Policy version/cache invalidation và fail-closed khi không xác minh được assignment. | Revoke/assignment change áp dụng nhất quán giữa instance theo SLA. |

## Schema và dữ liệu

Dự kiến assignment effective time/capability và room service capability; hospital-directory-service-db.dbml đi cùng migration. Giữ IDs chuẩn.

Seed/fixture: Hai phòng cùng khoa, bác sĩ/phòng inactive, staff phân công ca khác, coordinator capability và lab/pharmacy assignment.

Mọi thay đổi entity/migration phải đi cùng DBML service, migration dữ liệu cũ và kiểm tra rollback/compatibility. Kế hoạch này chưa đổi schema/DBML.

## Kiểm tra và bàn giao

- Unit test policy/state và regression cho lỗi nêu trên; contract test request/event với dependency ngoài mock được ghi rõ.
- HTTP integration qua Gateway và direct service; mỗi action có ca đúng/sai role, ownership, assignment và trạng thái. Ca bị từ chối không ghi DB/file/outbox hoặc lộ dữ liệu.
- UAT online + walk-in không tài khoản + nhiều hồ sơ/người đại diện theo phạm vi service; dependency deny/timeout và retry/duplicate/concurrent nếu có command.
- G4 kiểm tra nhiều instance và quyền/session/caller đã thu hồi theo SLA; không suy ra network safety từ unit test.
- Deliverable: API/event contract version, ma trận quyền, migration/DBML nếu có, seed test hợp lệ, bằng chứng expected/actual, runbook/known limitation có owner.
- Owner và effort chưa gán; từng ID được đưa vào issue/backlog khi phân công. Không coi policy ưu tiên/cấp cứu/MFA chưa duyệt là requirement đã được bệnh viện chốt.

## Quy tắc thực hiện

Feature branch theo service/nhóm; không push main, không tự add/commit. GitNexus impact trước sửa symbol, detect_changes trước commit. Cập nhật baseline main theo quy tắc dự án và tích hợp develop mới nhất trước code. Test ghi/xóa chỉ DB audit, không remote chung; seed production không reset queue.

