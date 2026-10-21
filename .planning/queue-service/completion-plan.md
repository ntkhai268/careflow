# Queue Service — Kế hoạch hoàn thiện

Ngày: 2026-10-14. Baseline: develop / 8bf77e2. Trạng thái: đề xuất, chưa triển khai.
Theo [kế hoạch tổng](../system-completion-plan.md); G0–G5 tương ứng các giai đoạn trong kế hoạch tổng. Không dùng bảng này để xác nhận tính năng đã làm xong.

## Phạm vi và khoảng trống

Tiếp nhận Visit, phiếu, hàng chờ theo room/session/service point và điều phối bước phục vụ. Không quyết định chẩn đoán hay mức cấp cứu.

Khoảng trống/điểm cần đối chiếu: manual intake ADMIN-only/bắt userId; EMERGENCY thành NORMAL; route khoa bypass assignment; nhiều phòng theo khoa chưa rõ; ba lane/docs lệch.

## Phụ thuộc

PAT hồ sơ/no-account/rights; DIR assignment/room/session; APT capacity; CON/LAB/RX events.

## Backlog và nghiệm thu

| ID | Giai đoạn | Công việc | Tiêu chí nghiệm thu |
| --- | --- | --- | --- |
| QUE-01 | G1 | Đóng bypass ở route khoa và các route cũ; guard role/capability/assignment tại mọi command/read. | DOCTOR phòng B không dashboard/call/claim A qua bất kỳ alias nào. |
| QUE-02 | G1 | Không dùng EMERGENCY như NORMAL; thiết kế ghi nhận transfer chuyên môn ngoài scheduler theo policy. | Không tự phân loại cấp cứu bằng tuổi/enum; giao diện có người xác nhận và audit. |
| QUE-03 | G2 | Visit record/ticket dùng chung cho APPOINTMENT/WALK_IN; staff scoped tiếp nhận, patientId bắt buộc, userId tùy theo liên kết. | Người không tài khoản nhận phiếu và CHECKED_IN; IDs không giả; creator không owner. |
| QUE-04 | G2 | Phối hợp capacity allocation; chống trùng active intake theo visit/step và quy tắc bệnh viện; idempotency khóa cạnh tranh. | Cùng key replay; key khác không lách duplicate; allocation fail có compensation. |
| QUE-05 | G2 | Room/session là scope queue; lưu source, eligible time, priority reason/verifier và support needs snapshot tối thiểu. | Hai phòng cùng khoa không trộn; ưu tiên do actor được phép; nhu cầu hỗ trợ riêng. |
| QUE-06 | G3 | Chốt và triển khai một chính sách xen lane có trọng số, lịch hẹn và ngưỡng chờ; giải thích recommended next. | Không starvation trong fixture theo policy; tránh khẳng định chờ tối đa khi capacity vượt tải; cảnh báo overload. |
| QUE-07 | G3 | Recall/miss/requeue, tạm rời hàng có kiểm soát, transfer phòng, cuối phiên và override có reason/audit. | Người không nghe gọi không mất lượt vô cớ; giữ original wait/history; không tự chen đầu. |
| QUE-08 | G3 | Lab/review/pharmacy bước tự động theo event + aggregate version; khóa claim/call/start/complete. | Hai instance không claim trùng; đủ kết quả mới review; review cùng consultation, không new INITIAL. |
| QUE-09 | G3 | Ticket printable/display DTO không PHI; ETA là khoảng dự kiến, hỗ trợ không-app. | Màn hình/âm thanh/giấy/mobile cùng nguồn trạng thái; mất printer có fallback. |
| QUE-10 | G4 | Outbox/inbox/DLQ, reconciliation và seed profile giới hạn dev/test. | Restart production không deleteAll; event retry/mất broker không duplicate step. |

## Schema và dữ liệu

Dự kiến Visit, visit-step uniqueness, room/session scope, actor/priority/support/transfer audit và userId tùy chọn. queue-db.dbml đang lệch QueueType/code và appointment required cần reconcile. Migration bảo toàn lượt cũ.

Seed/fixture: Online + no-account walk-in, ưu tiên, hỗ trợ không ưu tiên, review, missed, transfer, hai phòng, lab/pharmacy; reset ngày chỉ DB test/dev.

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

