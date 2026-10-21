# AI Service — Kế hoạch hoàn thiện

Ngày: 2026-10-14. Baseline: develop / 8bf77e2. Trạng thái: đề xuất, chưa triển khai.
Theo [kế hoạch tổng](../system-completion-plan.md); G0–G5 tương ứng các giai đoạn trong kế hoạch tổng. Không dùng bảng này để xác nhận tính năng đã làm xong.

## Phạm vi và khoảng trống

Hỗ trợ bác sĩ theo context được phép, có sự xác nhận của người dùng; không tự phân cấp cứu hay điều phối quyền.

Khoảng trống/điểm cần đối chiếu: Clinical/voice/interaction APIs thiếu guard role/ownership; pageData client có thể chứa dữ liệu không được phép.

## Phụ thuộc

CON actor/context; PAT scoped data; ID/COM auth; frontend consent/context.

## Backlog và nghiệm thu

| ID | Giai đoạn | Công việc | Tiêu chí nghiệm thu |
| --- | --- | --- | --- |
| AI-01 | G1 | Chốt capability sử dụng clinical AI theo vai trò; guard API và interaction owner/scope. | PATIENT/không phụ trách không đọc interaction hay context ca khác. |
| AI-02 | G1 | Context server kiểm chứng consultation/visit/patient, không tin pageData tùy ý; minimize PHI ra provider. | Client không nhúng hồ sơ B để AI truy xuất/bộc lộ; khóa provider không ở frontend. |
| AI-03 | G3 | Suggestion và voice extraction phải được doctor review trước lưu; tách demo/mock từ provider thật. | AI không tự ký toa, đổi queue priority hoặc cập nhật hồ sơ. |
| AI-04 | G4 | Timeout/fallback/quota, upload size/type, audit redacted và retention tương tác. | AI lỗi vẫn khám được; không log voice/raw PHI/token vô hạn. |
| AI-05 | G4 | Contract tests prompt/context và authorization; tắt AI vẫn hoàn tất core UAT. | Không dùng AI availability làm gate nghiệm thu tiếp nhận/khám. |

## Schema và dữ liệu

Nếu persistent interaction thêm actor/visit/scope/retention metadata, có DBML AI riêng khi tạo schema; hiện plan không tự tạo bảng.

Seed/fixture: Interaction test owned/unowned và provider mock fail; không gửi dữ liệu thật ra provider để test.

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

