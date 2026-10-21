# Frontend Web + Patient Mobile — Kế hoạch hoàn thiện

Ngày: 2026-10-14. Baseline: develop / 8bf77e2. Trạng thái: đề xuất, chưa triển khai.
Theo [kế hoạch tổng](../system-completion-plan.md); G0–G5 tương ứng các giai đoạn trong kế hoạch tổng. Không dùng bảng này để xác nhận tính năng đã làm xong.

## Phạm vi và khoảng trống

Luồng người dùng theo quyền và hỗ trợ tiếp nhận không công nghệ; backend là nguồn quyền và trạng thái.

Khoảng trống/điểm cần đối chiếu: AuthContext tin localStorage; thiếu walk-in full staff flow, giấy/display support; patient default route không rõ; docs đội web/mobile tách scope.

## Phụ thuộc

PAT, DIR, APT, QUE contracts; ID session/MFA; mỗi nhóm web/mobile giữ implementation riêng.

## Backlog và nghiệm thu

| ID | Giai đoạn | Công việc | Tiêu chí nghiệm thu |
| --- | --- | --- | --- |
| UI-01 | G1 | Bootstrap /me hoặc session validation, 401 login/refresh theo policy; role exact matching, protected layout 403 và default route đúng. | Sửa localStorage không được tải dữ liệu trái quyền; token hết hạn không giả authenticated. |
| UI-02 | G2 | Staff tiếp nhận: tìm/verify patient -> tạo no-account nếu cần -> chọn khoa/phòng -> capacity -> cấp lượt/in phiếu. | Không native select/free-text master; real API searchable chips, outside-click; actor scope rõ. |
| UI-03 | G2 | Quên ticket/phone hết pin: tra cứu tối thiểu có xác minh, dùng lượt đã có; người nhà và contact permissions. | Không hiển thị bệnh án trong lookup reception; không tự grant vì trùng phone. |
| UI-04 | G2 | Phiếu chữ lớn/tương phản, phòng/nơi chờ/hướng dẫn; không bắt QR/GPS/app ở quầy; nghe/khiếm thính được hỗ trợ. | Người cao tuổi đi một mình làm xong UAT có staff, không phụ thuộc app. |
| UI-05 | G3 | Điều phối: recommended next + lý do, waiting-long, missed/recall/transfer/override reason; phòng full/inactive. | Không kéo đổi thứ tự tự do không audit; ETA dự kiến có cập nhật, không hứa giờ tuyệt đối. |
| UI-06 | G3 | Display/âm thanh dữ liệu tối thiểu theo phòng; no-printer/no-audio fallback; lab/review/pharmacy chỉ dẫn giấy. | Không lộ PHI nơi công cộng; status nguồn đồng nhất. |
| UI-07 | G3 | Mobile dùng patientId được chọn/verified relation; trạng thái/notifications tách request, mock auth chỉ test mode rõ. | A2 không lẫn A1; thông báo lỗi không xóa hành trình; không cần mobile cho walk-in. |
| UI-08 | G4 | MFA/recovery UI, session management, bàn giao accessibility UAT; frontend không log token login response. | Keyboard/screen reader/focus có kiểm tra; recovery không bypass backend. |

## Schema và dữ liệu

Không schema frontend; không cache bệnh án nhạy cảm để bypass auth/offline. Quyết định token storage/HttpOnly-cookie+BFF cho web có CSRF contract, mobile secure storage, trong hardening Identity/UI.

Seed/fixture: Dùng fixture backend; không master fake trong production UI. Web và mobile thực hiện trên branch cá nhân tương ứng.

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

