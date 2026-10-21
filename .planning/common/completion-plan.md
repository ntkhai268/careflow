# Common — Kế hoạch hoàn thiện

Ngày: 2026-10-14. Baseline: develop / 8bf77e2. Trạng thái: đề xuất, chưa triển khai.
Theo [kế hoạch tổng](../system-completion-plan.md); G0–G5 tương ứng các giai đoạn trong kế hoạch tổng. Không dùng bảng này để xác nhận tính năng đã làm xong.

## Phạm vi và khoảng trống

Chuẩn hóa bằng chứng xác thực, error/audit và hợp đồng event. Không đưa policy patient/room vào common.

Khoảng trống/điểm cần đối chiếu: Các service đang đọc header và trả lỗi không đồng nhất; thiếu principal/caller chuẩn.

## Phụ thuộc

Identity chốt token profile/JWKS; tất cả nhóm chốt EventEnvelope.

## Backlog và nghiệm thu

| ID | Giai đoạn | Công việc | Tiêu chí nghiệm thu |
| --- | --- | --- | --- |
| COM-01 | G0 | Chốt AuthenticatedPrincipal: userId, role/capability, sessionId; ServicePrincipal riêng cho caller; rõ user delegation và background call. | Không suy ra danh tính từ header client; không có fallback DOCTOR. |
| COM-02 | G1 | Tạo cơ chế xác minh token dùng chung cho servlet service, cấu hình audience/scope và thuật toán bắt buộc; hỗ trợ rotation theo contract Identity. | Thiếu/sai issuer/audience/exp/chữ ký bị từ chối; principal chỉ từ bằng chứng đã kiểm chứng. |
| COM-03 | G1 | Chuẩn hóa 401/403/404/409/503 và correlationId; redaction audit. | Denied không lộ token/PHI, log có actor/action/resource/outcome. |
| COM-04 | G3 | Chốt event version/aggregateVersion; mẫu outbox/inbox và idempotency dựa theo pattern hiện có. | Duplicate/reordered event không tạo thêm nghiệp vụ; consumer xử lý version không hỗ trợ rõ ràng. |
| COM-05 | G4 | Bộ contract test token/caller/error/envelope cho từng service. | Mỗi service chạy bộ chung cùng test policy riêng; không mock qua guard trong test boundary. |

## Schema và dữ liệu

Không tạo bảng chung cho các service. Outbox/inbox nằm DB service sở hữu; migration/DBML thực hiện tại service.

Seed/fixture: Cung cấp fixture/key test; không production secret và không reset DB.

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

