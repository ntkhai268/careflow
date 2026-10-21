# Lab Service — Kế hoạch hoàn thiện

Ngày: 2026-10-14. Baseline: develop / 8bf77e2. Trạng thái: đề xuất, chưa triển khai.
Theo [kế hoạch tổng](../system-completion-plan.md); G0–G5 tương ứng các giai đoạn trong kế hoạch tổng. Không dùng bảng này để xác nhận tính năng đã làm xong.

## Phạm vi và khoảng trống

Chỉ định CLS, thực hiện, kết quả và điều kiện đủ kết quả; queue movement do Queue quản lý.

Khoảng trống/điểm cần đối chiếu: Role rộng nhưng create/review/cancel chưa kiểm tra assigned consultation; updateResult chưa kiểm tra technician claim; patient scope lấy hồ sơ đầu.

## Phụ thuộc

CON context/assignment; PAT patient rights; DIR service points; QUE claim/step; COM caller.

## Backlog và nghiệm thu

| ID | Giai đoạn | Công việc | Tiêu chí nghiệm thu |
| --- | --- | --- | --- |
| LAB-01 | G1 | Create/review/cancel kiểm tra doctor/capability và quan hệ consultation/visit/patient. | Không dùng doctor B hay staff không capability thay bác sĩ A; IDs body phải khớp. |
| LAB-02 | G1 | Update/finalize kiểm tra assigned service point, claim user và order state; ADMIN exception có audit. | LAB_B không sửa kết quả A chỉ vì order IN_PROGRESS; mỗi command tái kiểm tra quyền. |
| LAB-03 | G1 | Read/list theo patientId cụ thể có quyền hoặc clinical/lab assignment, DTO đúng scope. | A2 đọc đúng, B bị từ chối; staff reception không xem mọi lab. |
| LAB-04 | G3 | Chỉ định nhiều nhóm service point; idempotent execution step, trạng thái cancel/reopen và đủ required results theo ca. | Một order sẵn sàng chưa tự kết luận mọi order của consultation đều đủ. |
| LAB-05 | G3 | Kết quả revision/correction và người duyệt; notify/review event theo version. | Không overwrite kết quả đã phát hành im lặng; kết quả sửa có audit và review cần thiết. |
| LAB-06 | G3 | Outbox và reconciliation Queue/Consultation khi hoàn tất, retry/delay. | Không tạo nhiều RESULT_REVIEW vì duplicate event; không bỏ notification nghiệp vụ do push lỗi. |

## Schema và dữ liệu

Dự kiến assignment/claim validation refs, result revisions/finalizedBy và aggregate version; lab-db.dbml. Không triển khai thanh toán CLS mới trong scope này.

Seed/fixture: LAB_A/B different service points, multi-order partial/complete/cancel/correction và walk-in same consultation.

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

