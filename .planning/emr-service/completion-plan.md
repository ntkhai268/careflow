# EMR Service — Kế hoạch hoàn thiện

Ngày: 2026-10-14. Baseline: develop / 8bf77e2. Trạng thái: đề xuất, chưa triển khai.
Theo [kế hoạch tổng](../system-completion-plan.md); G0–G5 tương ứng các giai đoạn trong kế hoạch tổng. Không dùng bảng này để xác nhận tính năng đã làm xong.

## Phạm vi và khoảng trống

Bệnh án chủ và tổng hợp thông tin được phép từ các service. Không là cách bypass ownership của dữ liệu nguồn.

Khoảng trống/điểm cần đối chiếu: Controller không actor authorization; GET summary tự tạo record; Feign không truyền context hợp lệ, lỗi downstream bị nuốt thành thiếu dữ liệu.

## Phụ thuộc

PAT rights; CON clinical context; LAB/RX scoped read; COM service+user delegation.

## Backlog và nghiệm thu

| ID | Giai đoạn | Công việc | Tiêu chí nghiệm thu |
| --- | --- | --- | --- |
| EMR-01 | G1 | Guard create/read/update/summary theo role/capability, patientId và assignment trước fetch hoặc mutation. | A không đọc/ghi B; direct không token không thực thi; GET denied không tạo record. |
| EMR-02 | G1 | GET summary chỉ đọc; tạo master record ở command/event được phép, idempotent. | Refresh trang không tạo bệnh án trái quyền hoặc tăng record number. |
| EMR-03 | G1 | Downstream authenticates caller và delegated actor; không gửi header rỗng/doctor giả. | Không gọi endpoint bỏ guard để aggregate; provider filter vẫn được giữ. |
| EMR-04 | G3 | Phân biệt patient-visible, clinician summary và reception summary; partial data có nhãn unavailable rõ. | 403 không chuyển thành hồ sơ trống; thiếu dị ứng/kết quả phải hiển thị chưa xác minh. |
| EMR-05 | G3 | Liên kết visit/consultation và provenance/revision; giữ lịch sử và audit truy cập tối thiểu. | Summary của walk-in/multi-profile đúng patient; không trộn dữ liệu theo user. |
| EMR-06 | G4 | Test aggregation với dependency deny/timeout, và audit user/service/resource. | Không lộ dữ liệu từ cache user trước; logging không ghi toàn bệnh án. |

## Schema và dữ liệu

Dự kiến provenance/audit/version và visit references nếu cần; emr-db.dbml. Không dùng GET migration dữ liệu hoặc viết vào DB service khác.

Seed/fixture: No-account, multi-profile, partial dependency, records revisions, assigned/unassigned actors.

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

