# Prescription Service — Kế hoạch hoàn thiện

Ngày: 2026-10-14. Baseline: develop / 8bf77e2. Trạng thái: đề xuất, chưa triển khai.
Theo [kế hoạch tổng](../system-completion-plan.md); G0–G5 tương ứng các giai đoạn trong kế hoạch tổng. Không dùng bảng này để xác nhận tính năng đã làm xong.

## Phạm vi và khoảng trống

Kê, xác nhận, sửa đổi/hủy và phát thuốc đúng bác sĩ, ca và quầy.

Khoảng trống/điểm cần đối chiếu: Controller confirm thiếu actor còn fallback; patient ownership lấy hồ sơ đầu; event/queue dispense cần đối soát retry.

## Phụ thuộc

CON ca/assignment; PAT quyền/dị ứng; DIR quầy; QUE claim/dispense; COM caller.

## Backlog và nghiệm thu

| ID | Giai đoạn | Công việc | Tiêu chí nghiệm thu |
| --- | --- | --- | --- |
| RX-01 | G1 | Bỏ confirm no-actor bypass, rà soát legacy/read/list/command với principal. | Không thiếu actor mà giả doctor prescription; mọi endpoint cùng policy. |
| RX-02 | G1 | Quyền patientId cụ thể cho multi-profile/đại diện; doctor assignment; staff quầy đã phân công. | A2 xem đơn đã phát hành của A2; reception không mặc nhiên xem/phát thuốc. |
| RX-03 | G2 | Walk-in prescription dựa consultation/visit không appointment; snapshot patient/provider hợp lệ. | Không dùng userId dummy; trạng thái/IDs khớp và xác minh được. |
| RX-04 | G3 | Kiểm tra master drug, allergy source, draft/confirm/cancel/amend và review prerequisite theo contract. | Chỉ doctor hợp lệ ký; không dùng dữ liệu dị ứng chưa xác minh để coi như trống; policy duyệt rõ. |
| RX-05 | G3 | Dispense phải claimed/assigned quầy, đơn confirmed, event idempotent; lưu actor/time và đối soát queue. | Hai staff/instance không phát hai lần; lỗi complete queue không khiến phát lại thuốc. |
| RX-06 | G3 | Phiếu thuốc dễ đọc cho no-account patient, hướng dẫn nhận và audit revision. | Không lộ đơn qua ticket public; thuốc xác nhận in đúng revision. |
| RX-07 | G4 | Contract tests và offline dependency; không đưa lab/EMR logic vào branch RX. | Unavailable quyền/dữ liệu bắt buộc không auto-approve; external mock có tài liệu. |

## Schema và dữ liệu

Dự kiến visit ref, dispense idempotency/audit, revisions nếu cần đối soát; prescription-db.dbml khi schema thay. Không xây inventory/payment service trong đợt này.

Seed/fixture: Confirmed/draft/amend/dispensed, no-account walk-in, quầy A/B, multi-profile và duplicate delivery.

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

