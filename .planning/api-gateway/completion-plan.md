# API Gateway — Kế hoạch hoàn thiện

Ngày: 2026-10-14. Baseline: develop / 8bf77e2. Trạng thái: đề xuất, chưa triển khai.
Theo [kế hoạch tổng](../system-completion-plan.md); G0–G5 tương ứng các giai đoạn trong kế hoạch tổng. Không dùng bảng này để xác nhận tính năng đã làm xong.

## Phạm vi và khoảng trống

Ingress, xác thực token, routing và giới hạn request. Quyền tài nguyên vẫn ở service.

Khoảng trống/điểm cần đối chiếu: GET directory wildcard làm mất role ADMIN; gateway không kiểm tra revocation, token profile chưa đủ; discovery route tự động cần kiểm kê.

## Phụ thuộc

COM principal; Identity JWT/keys/revocation; Directory public DTO; deployment private network.

## Backlog và nghiệm thu

| ID | Giai đoạn | Công việc | Tiêu chí nghiệm thu |
| --- | --- | --- | --- |
| GW-01 | G0 | Kiểm kê explicit route và discovery locator; lập allowlist endpoint public theo method/path. | Mỗi endpoint có loại public/auth/internal và owner; không wildcard GET toàn directory. |
| GW-02 | G1 | Sửa public allowlist; giữ kiểm tra token ở staff assignments/doctors me; vô hiệu hóa route discovery ngoài contract nếu không cần. | ADMIN GET assignments thành công, PATIENT bị từ chối; không bỏ JWT do path gần giống. |
| GW-03 | G1 | Xác minh token profile/JWKS và session state theo SLA; strip header spoof, propagate Authorization/context theo contract mới. | JWT hết hạn/giả/sai audience bị 401; token PATIENT không thành ADMIN bằng header. |
| GW-04 | G1 | Định tuyến internal endpoint không ra public ingress; authenticate Gateway khi gọi downstream theo phương án chốt. | External không truy cập internal, kể cả qua alias service-id. |
| GW-05 | G4 | Rate limit login và ingress, request size/timeouts, correlationId; lỗi downstream rõ. | Dependency lỗi không cho qua quyền; 5xx availability không biến thành login success; limiter chung nhiều instance. |
| GW-06 | G4 | Test Gateway thật với service thật trên môi trường audit, rồi hai instance. | Không chỉ unit filter: HTTP headers/status/body và side effect đúng. |

## Schema và dữ liệu

Không cần schema nghiệp vụ. Nếu limiter/session cache dùng store chung, xác định key/TTL theo contract.

Seed/fixture: Route fixture test và token từ Identity audit; không tự cấp token production.

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

