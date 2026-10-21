# Identity Service — Kế hoạch hoàn thiện

Ngày: 2026-10-14. Baseline: develop / 8bf77e2. Trạng thái: đề xuất, chưa triển khai.
Theo [kế hoạch tổng](../system-completion-plan.md); G0–G5 tương ứng các giai đoạn trong kế hoạch tổng. Không dùng bảng này để xác nhận tính năng đã làm xong.

## Phạm vi và khoảng trống

Danh tính đăng nhập, credentials, session, role/capability, khóa ký và MFA. Không là nơi sở hữu hồ sơ người bệnh.

Khoảng trống/điểm cần đối chiếu: Access JWT hiện giữ quyền cũ sau disable/role change/logout; HMAC có fallback secret; eKYC mock chưa phải biometric auth.

## Phụ thuộc

COM principal; deployment secret store/TLS/time sync; frontend login/recovery.

## Backlog và nghiệm thu

| ID | Giai đoạn | Công việc | Tiêu chí nghiệm thu |
| --- | --- | --- | --- |
| ID-01 | G0 | Chốt session lifetime, logout current/all, revocation SLA, token audiences và scope service. | Có bảng quy tắc access/refresh/service token và expected 401/403. |
| ID-02 | G1 | Dùng private key ký JWT, public JWKS, key id/rotation; yêu cầu config secret/key đúng. | Không có fallback key để deployment tiếp tục chạy; verifier không có khả năng ký. |
| ID-03 | G1 | Lưu session/version; disable/change-role/logout revoke theo policy; kiểm tra online/cache phù hợp SLA. | Token cũ mất hiệu lực trên Gateway và service theo SLA; refresh rotation/reuse vẫn đúng. |
| ID-04 | G1 | Credentials cho service caller với scope tối thiểu; giữ public register luôn PATIENT; quản trị role có audit. | Service credential không biến thành tài khoản bác sĩ/admin; không nâng quyền bằng body. |
| ID-05 | G4 | MFA ADMIN bằng TOTP hoặc passkey theo lựa chọn triển khai; enrollment/recovery/step-up có re-auth và audit. | Password đúng chưa cấp quyền đầy đủ khi cần MFA; không bypass qua refresh/recovery. |
| ID-06 | G4 | Giới hạn thử, session listing/revoke, recovery một lần; nếu passkey: challenge/origin/RP ID/replay validation. | Mất thiết bị có đường phục hồi kiểm soát; câu hỏi bí mật không thay MFA. |
| ID-07 | G4 | Giữ eKYC mock hiển thị rõ; chỉ triển khai SSO/biometrics thật khi có contract riêng. | Ảnh mock VERIFIED không tự đăng nhập/nâng role. |

## Schema và dữ liệu

Dự kiến sessions/token version và MFA credential/recovery data; migration kèm identity-db.dbml, chỉ lưu hash/credential cần thiết. Không lưu template khuôn mặt để làm demo.

Seed/fixture: Tài khoản audit đủ role, locked/disabled, MFA chưa enroll/enrolled; master idempotent, mật khẩu test riêng.

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

