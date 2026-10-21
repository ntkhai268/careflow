# Patient Service — Kế hoạch hoàn thiện

Ngày: 2026-10-14. Baseline: develop / 8bf77e2. Trạng thái: đề xuất, chưa triển khai.
Theo [kế hoạch tổng](../system-completion-plan.md); G0–G5 tương ứng các giai đoạn trong kế hoạch tổng. Không dùng bảng này để xác nhận tính năng đã làm xong.

## Phạm vi và khoảng trống

Hồ sơ người bệnh, định danh, quan hệ truy cập và health records/file cá nhân; phục vụ cả người chưa có tài khoản.

Khoảng trống/điểm cần đối chiếu: user_id bắt buộc; STAFF create chưa có luồng walk-in; Health Records thiếu actor authorization; hồ sơ đầu tiên bị dùng thay mọi hồ sơ.

## Phụ thuộc

COM/Identity auth; Directory scope; Queue visit và Consultation clinical assignment.

## Backlog và nghiệm thu

| ID | Giai đoạn | Công việc | Tiêu chí nghiệm thu |
| --- | --- | --- | --- |
| PAT-01 | G1 | Áp quyền cho mọi Health Records CRUD/download; xác minh actor được quyền patientId, không chỉ file thuộc path patientId. | A không đọc/sửa/xóa/download B; denied không ghi file/DB; service trực tiếp cũng bảo vệ. |
| PAT-02 | G1 | API quyền patientId cụ thể cho service được phép; kiểm tra nhiều hồ sơ và đại diện. | A1/A2 đều truy cập đúng; không trả toàn hồ sơ khi chỉ cần boolean/minimal context. |
| PAT-03 | G2 | Tách Patient khỏi login: tạo hồ sơ chưa có User, staff scoped tiếp nhận; không yêu cầu phone nếu quy trình không bắt buộc. | Người già không tài khoản có hồ sơ hợp lệ; creator staff không trở thành owner bệnh án. |
| PAT-04 | G2 | Quan hệ user-patient/đại diện có loại quyền, verifiedBy/time và revoke; quá trình claim hồ sơ có xác minh. | Nhập CCCD/phone trùng không tự cấp quyền hoặc tự merge; revoke có hiệu lực. |
| PAT-05 | G2 | Tìm và chống hồ sơ trùng; hồ sơ tạm/thiếu giấy tờ theo quy trình; merge chỉ người được phép và có audit. | Không tự gộp người cùng tên/CCCD khai báo; lịch sử references được bảo toàn khi reconcile. |
| PAT-06 | G2 | Nhu cầu hỗ trợ: hearing/mobility/non-digital, contact preference và người liên hệ; tối thiểu hóa dữ liệu. | Không tự chuyển PRIORITY chỉ vì không dùng công nghệ; kênh liên hệ có quyền/đồng ý. |
| PAT-07 | G3 | Tách quyền đọc lâm sàng, tóm tắt tiếp nhận và cập nhật demographics. | STAFF không mặc nhiên được sửa toàn bệnh sử/dị ứng ngoài scope; DOCTOR phải có quan hệ hợp lệ. |

## Schema và dữ liệu

Dự kiến nullable User link + bảng patient access/representation; đối soát user_id hiện có và duplicate cross-account, không auto-grant. Cập nhật patient-db.dbml và health records đang thiếu trong sơ đồ khi reconcile.

Seed/fixture: No-account elderly, multi-profile owner, verified/revoked representative, similar-name duplicate fixture; health record/file audit riêng.

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

