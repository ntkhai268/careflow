# Consultation Service — Kế hoạch hoàn thiện

Ngày: 2026-10-14. Baseline: develop / 8bf77e2. Trạng thái: đề xuất, chưa triển khai.
Theo [kế hoạch tổng](../system-completion-plan.md); G0–G5 tương ứng các giai đoạn trong kế hoạch tổng. Không dùng bảng này để xác nhận tính năng đã làm xong.

## Phạm vi và khoảng trống

Ca khám, bác sĩ phụ trách, dữ liệu lâm sàng và lifecycle initial/wait/review/complete.

Khoảng trống/điểm cần đối chiếu: Legacy PUT/status/complete bỏ guard; create/complete thiếu actor fallback; Feign tự giả bác sĩ; ownership đọc dựa appointment.

## Phụ thuộc

QUE Visit/assignment/claim; PAT quyền hồ sơ; DIR provider; LAB required/review; RX confirm policy.

## Backlog và nghiệm thu

| ID | Giai đoạn | Công việc | Tiêu chí nghiệm thu |
| --- | --- | --- | --- |
| CON-01 | G1 | Guard legacy giống API mới hoặc deprecate có contract; không bỏ kiểm tra khi thiếu actor. | PATIENT/unassigned doctor không sửa/status/complete; denied không publish event. |
| CON-02 | G1 | Bỏ Feign context fallback DOCTOR/seed user; dùng caller+user delegation rõ. | Không có context bị từ chối; system event xử lý bằng danh tính service có scope. |
| CON-03 | G2 | Create từ visitId/queue claim; validate patient/doctor/room và appointment optional. | Walk-in không appointment vẫn tạo ca hợp lệ; client không tự gắn bác sĩ khác. |
| CON-04 | G3 | Quyền đọc theo patient access hoặc clinical assignment hợp lệ, không theo appointment có/không. | Chủ hồ sơ no-account qua staff authorized và đại diện verified có đúng quyền; nhiều hồ sơ đúng. |
| CON-05 | G3 | State machine wait/resume/review/complete với LAB/RX required policy; kết quả đến muộn/hủy order theo contract. | Không complete khi review bắt buộc chưa xong; không duplicate active consultation cho cùng bước. |
| CON-06 | G3 | Bàn giao bác sĩ/phòng có version/audit; snapshot người khám khi ký vẫn giữ lịch sử. | Chỉ người được giao mới sửa; hai bác sĩ cập nhật đồng thời không mất dữ liệu. |
| CON-07 | G3 | Outbox completion và reconciliation appointment/queue, không success giả khi lỗi đồng bộ. | Một completion nghiệp vụ, các downstream hội tụ khi retry. |

## Schema và dữ liệu

Dự kiến visitId, assignment version/handover/audit, uniqueness active step, revision optimistic lock; consultation-db.dbml đi cùng migration.

Seed/fixture: Online/walk-in no-appointment, waiting/review/completed và reassigned doctor; không trộn lab implementation vào service.

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

