# Notification Service — Kế hoạch hoàn thiện

Ngày: 2026-10-14. Baseline: develop / 8bf77e2. Trạng thái: đề xuất, chưa triển khai.
Theo [kế hoạch tổng](../system-completion-plan.md); G0–G5 tương ứng các giai đoạn trong kế hoạch tổng. Không dùng bảng này để xác nhận tính năng đã làm xong.

## Phạm vi và khoảng trống

Thông báo cá nhân và tín hiệu gọi lượt tối thiểu qua các kênh; không quyết định trạng thái queue hay thay thế kênh hỗ trợ trực tiếp.

Khoảng trống/điểm cần đối chiếu: Cần route người nhận khi patient không user, verified representative; WS expiry/revocation dài phiên; công cộng và cá nhân phải tách.

## Phụ thuộc

PAT contact/access preferences; ID session revocation; QUE call DTO; COM envelope.

## Backlog và nghiệm thu

| ID | Giai đoạn | Công việc | Tiêu chí nghiệm thu |
| --- | --- | --- | --- |
| NOT-01 | G1 | Inbox/token/device/subscribe kiểm tra principal và owner; socket expiration/revocation policy. | A không nhận inbox B; token hết hiệu lực không giữ socket nhận nội dung vô thời hạn. |
| NOT-02 | G2 | Resolve recipient từ quan hệ đã xác minh, không bắt mọi patient có userId. | No-account không mất hành trình vì không có push recipient; không gửi cho người nhà bị revoke. |
| NOT-03 | G3 | Tín hiệu màn hình/âm thanh chỉ số lượt và phòng; staff display client xác thực theo điểm phục vụ. | Không public broadcast diagnosis/tên đầy đủ/PII; replay đúng scope. |
| NOT-04 | G3 | Inbox bền vững, retry push/device, dedup event/revision; kênh gửi thất bại không rollback khám. | Notification lỗi không làm bệnh nhân mắc ở trạng thái cũ; UI lấy business state từ API nguồn. |
| NOT-05 | G3 | Hỗ trợ staff nhắc trực tiếp và in lại hướng dẫn; SMS/phone là tích hợp tùy chọn sau, không tự xây provider. | Người không app vẫn được gọi/hướng dẫn; consent/contact đúng. |
| NOT-06 | G4 | Monitor delivery/DLQ, expired installations và multi-instance websocket routing. | Không hứa exactly-once push; dedup nghiệp vụ/inbox và reconciliation kiểm chứng. |

## Schema và dữ liệu

Dự kiến recipient resolution/delivery channel trạng thái và phiên bản; notification-db.dbml. Không lưu bản sao đầy đủ bệnh án.

Seed/fixture: User-linked/no-user/representative-revoked, device invalid, delivery failure và duplicate event.

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

