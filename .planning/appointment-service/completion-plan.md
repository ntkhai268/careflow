# Appointment Service — Kế hoạch hoàn thiện

Ngày: 2026-10-14. Baseline: develop / 8bf77e2. Trạng thái: đề xuất, chưa triển khai.
Theo [kế hoạch tổng](../system-completion-plan.md); G0–G5 tương ứng các giai đoạn trong kế hoạch tổng. Không dùng bảng này để xác nhận tính năng đã làm xong.

## Phạm vi và khoảng trống

Đặt lịch, khung giờ, công suất và lịch bác sĩ; cung cấp xác nhận khả năng tiếp nhận walk-in. Không sở hữu queue hay dữ liệu khám.

Khoảng trống/điểm cần đối chiếu: Appointment đang làm cơ sở ownership/assignment cho nhiều service; cần phối hợp Visit không appointment và chính sách online/walk-in.

## Phụ thuộc

Patient authorization, Directory room/session; Queue Visit/slot allocation contract.

## Backlog và nghiệm thu

| ID | Giai đoạn | Công việc | Tiêu chí nghiệm thu |
| --- | --- | --- | --- |
| APT-01 | G1 | Rà soát endpoint list/read/status/cancel/follow-up và input actor; patientId cụ thể có quyền. | PATIENT khác không truy cập; assigned doctor và ADMIN đúng scope; không trusting client doctorId. |
| APT-02 | G2 | Chốt capacity theo room/session/slot: reservation online, phân bổ walk-in, release/no-show. | Tỷ lệ và time windows được bệnh viện duyệt; không mặc định 50/50 hay online ưu tiên tuyệt đối. |
| APT-03 | G2 | API tiếp nhận capacity cho walk-in dành Queue/authorized staff, transactional allocation token/idempotency. | Hai quầy không oversell; không tạo appointment online giả để nhận walk-in. |
| APT-04 | G2 | Quy tắc đến sớm/muộn/tìm lịch quên mã; danh sách scoped reception và release đúng điều kiện. | Check-in lại dùng lượt cũ; staff chỉ thấy operational data cần thiết. |
| APT-05 | G3 | Chuyển slot/phòng/bác sĩ khi ngừng phục vụ và compensating release nếu visit creation lỗi. | Không mất đặt chỗ hoặc tiêu thụ công suất hai lần; giữ audit và patient communication. |
| APT-06 | G3 | Follow-up liên kết ca đã được phép, chống booking trùng; event version và retry. | Event duplicate không tạo ticket đôi; ngày/khoảng hợp lệ; không phụ thuộc UI guard. |

## Schema và dữ liệu

Dự kiến slot allocation/source/release và room/session keys; appointment-db.dbml cần reconcile enum/state với code trước thay đổi. Không thêm EMR/lab data.

Seed/fixture: Online/walk-in allocation, full slot, early/late/no-show, concurrent reserve và hai phòng.

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

