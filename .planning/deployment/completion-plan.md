# Deployment, Eureka và vận hành — Kế hoạch hoàn thiện

**CURRENT21/10/2026 — R7:** [bàn giao kỹ thuật và ma trận18ca](r7-acceptance-2026-10-21.md) cập nhật hiện trạng kiểm thử, migration, restore và rollout. Các mục vận hành production chưa có bằng chứng vẫn mở; bảng đề xuất ngày14/10 bên dưới là baseline lịch sử.

Ngày: 2026-10-14. Baseline: develop / 8bf77e2. Trạng thái: đề xuất, chưa triển khai.
Theo [kế hoạch tổng](../system-completion-plan.md); G0–G5 tương ứng các giai đoạn trong kế hoạch tổng. Không dùng bảng này để xác nhận tính năng đã làm xong.

## Phạm vi và khoảng trống

Ranh giới mạng, config/keys, khởi động, nhiều instance, giám sát và phục hồi.

Khoảng trống/điểm cần đối chiếu: Compose publish mọi service; config ưu tiên remote DB; initializer reset queue/appointments; start-local chạy trùng và kill toàn Java/Node.

## Phụ thuộc

ID keys/JWKS/session; COM caller; tất cả service readiness/event/audit.

## Backlog và nghiệm thu

| ID | Giai đoạn | Công việc | Tiêu chí nghiệm thu |
| --- | --- | --- | --- |
| OPS-01 | G0 | Môi trường audit DB/broker riêng, explicit per-service URLs, fixture/test account; JDK phù hợp compiler target. | Không thử CRUD/delete/reset trên DB shared remote; secrets không vào Git/log. |
| OPS-02 | G1 | Ingress chỉ Gateway/public endpoints; service/Eureka/DB/broker private; debug port bind loopback. | Client ngoài network không gọi direct business port; internal endpoint vẫn caller auth. |
| OPS-03 | G1 | HTTPS external + authenticated service connections/credentials hoặc mTLS theo contract; secret/key mount và rotation. | Service giả không được trust chỉ vì biết header; key rotation có rollout/cutover rõ. |
| OPS-04 | G2 | Sửa starter theo process IDs sở hữu, readiness health, chạy từng service một lần, cleanup có phạm vi. | Không kill Java/Node khác; missing service trả availability error, không tự spawn qua request. |
| OPS-05 | G3 | Profiles seed rõ dev/test vs production; migration và backup/restore rehearsal. | Restart production không deleteAll; schema migration không auto ddl update phá dữ liệu. |
| OPS-06 | G4 | Hai instance/máy, shared revocation/limiter, clock sync, broker ACL/credentials theo service. | Quyết định quyền đồng nhất, claim không trùng; Eureka registration không thay xác thực. |
| OPS-07 | G4 | Monitor latency/queue wait/error/denied/DLQ, audit redaction, dependency outages; runbooks phục hồi giấy/mạng. | Không nới quyền khi lỗi; replay dữ liệu giấy/event đối soát không trùng; alert có owner. |
| OPS-08 | G5 | Release checklist, backup rollback compatibility, smoke/UAT/network evidence. | Đường core và security must-have pass trước phát hành; rollback không mất bản ghi bệnh án. |

## Schema và dữ liệu

Infra không sở hữu schema lâm sàng; migration từng DB service, quyền DB/broker tối thiểu và môi trường audit riêng.

Seed/fixture: Test fixtures no-account/multi-room/multi-instance; production master idempotent và không queue reset.

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

