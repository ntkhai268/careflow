# CareFlow — baseline và đặc tả R0

Bắt đầu chuẩn hóa: 19/10/2026; hoàn tất: 20/10/2026. Phiên bản thiết kế: `R0-1.0`. Phạm vi: yêu cầu đề tài đầy đủ, không giới hạn ở MVP cũ.

## Nguồn chuẩn

| Nội dung | Tài liệu |
|---|---|
| Yêu cầu/use case và điều kiện nghiệm thu | [01 — yêu cầu và use case](01-requirements-use-cases.md) |
| Service, quyền sở hữu dữ liệu và trust boundary | [02 — kiến trúc](02-service-architecture.md) |
| Contract hiện hành và thay đổi mục tiêu | [03 — contract và trạng thái](03-contracts-and-states.md) |
| Fixture, môi trường, phụ thuộc nghiệm thu | [04 — điều kiện kiểm thử](04-test-readiness.md) |
| Phiên bản source/artifact/migration và kết quả R0 | [05 — biên bản baseline](05-r0-baseline-results.md) |
| Manifest có hash từng build input | [baseline-manifest.json](evidence/r0-20261019/baseline-manifest.json) |
| Lộ trình sau R0 | [Kế hoạch R0–R7](../full-requirements-plan-2026-10-19.md) |

Các contract chi tiết theo service hiện có tiếp tục có giá trị ở phần không xung đột với bảng CURRENT/TARGET của R0. Tài liệu cũ trong `docs/` phải trỏ về đây khi mô tả scope/ownership đã thay đổi. `TARGET` là thiết kế đã chốt cho đợt sau, không phải endpoint đang chạy. Bằng chứng runtime/code là nguồn xác nhận CURRENT; có bản đặc tả không có nghĩa đã triển khai.

Hướng dẫn thao tác duy nhất: [huong-dan-su-dung](../../huong-dan-su-dung/README.md). Tài khoản demo chỉ ở trang01; secret/cấu hình runtime private không nằm trong bộ tài liệu này.

## Quyết định được giữ

- Mobile native từ worktree `patient-mobile-ios-login`; backend/web từ `security-integration`. Commit đơn lẻ chưa biểu diễn toàn bộ baseline vì cả hai có thay đổi chưa commit.
- Định danh: OCR/QR tại máy và đối chiếu trực tiếp, không dùng VNPT. Chưa có xác minh giấy tờ tự động hoàn toàn.
- AI làm ở R6. Analytics R5 đã chạy/kiểm chứng trên Mac ngày21/10/2026; [contract](../analytics-service/r5-contract-2026-10-21.md) và [biên bản](../analytics-service/r5-acceptance-2026-10-21.md) là cập nhật CURRENT cho Analytics. Baseline R0 và evidence lịch sử giữ nguyên.
- API/data test thật khác response mock. Native cảm biến/push phải có nghiệm thu thiết bị; health200/build đạt không thay thế E2E.
- R0 chỉ chuẩn hóa và kiểm chứng baseline. Lỗ hổng tích hợp/tính năng mục tiêu không tự được đánh dấu đã sửa.
