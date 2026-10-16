# 01 — Tài khoản và dữ liệu demo

[Quay về mục lục](README.md) · [Tiếp: đăng nhập/MFA](02-dang-nhap-mfa.md)

Các tài khoản dưới đây thuộc **DB audit cục bộ**, cùng mật khẩu demo **`Password123@`**, chỉ còn đúng nếu chưa đổi mật khẩu/bật MFA. Không phải thông tin production. Secret TOTP, OTP, token và mã khôi phục không lưu trong hướng dẫn.

| Mục đích | Tài khoản | Vai trò/phân công |
|---|---|---|
| Bệnh nhân Nội đang khám | `mobile_noi_c1d9dc` | Phiếu NOI-010, ROOM-01; giữ nguyên ca gốc khi thử chức năng khác |
| Các điện thoại mẫu | `mobile_case01`, `mobile_case02`, `mobile_case03` | PATIENT; đọc trạng thái mới nhất từ API |
| Bác sĩ Nội An | `an@careflow.vn` | DOCTOR, Nội tổng quát, ROOM-01 |
| Bác sĩ seed khác | `doctor` | DOCTOR, seed gán ROOM-06; không dùng thay An ở ROOM-01 |
| Nhân viên tiếp nhận/cấp phát | `staff` | STAFF; seed phân công ROOM-01 và PHARMACY-MAIN-01, kiểm tra lại phân công hiện tại |
| Kỹ thuật viên | `lab` | LAB_TECHNICIAN; chọn đúng điểm phục vụ được phân công |
| Quản trị | `admin` | ADMIN; quản lý danh mục/phân công và quyền điều phối |
| Bác sĩ bàn giao A | `handover_a_9bbb8a` | DOCTOR, Da liễu ROOM-08; tác giả gốc ca/toa/chỉ định; bác sĩ chính khi đặt lịch |
| Bác sĩ bàn giao B | `handover_b_9bbb8a` | DOCTOR, Da liễu ROOM-08; đang phụ trách ca demo 01 sau kiểm thử |
| Bác sĩ bàn giao C | `handover_c_9bbb8a` | DOCTOR, Da liễu ROOM-08; đã cấp quyền điều phối; có toa nháp riêng |
| Bệnh nhân bàn giao 01 | `handover_patient01_9bbb8a` | Ca đang IN_PROGRESS, phiếu DAL-001 |
| Bệnh nhân bàn giao 02 | `handover_patient02_9bbb8a` | Ca đã hoàn tất sau bàn giao, dùng đối chiếu lịch sử |

## Đọc đúng trạng thái trước buổi demo

1. Đăng nhập bệnh nhân, mở Lịch khám và phiếu; ghi bệnh nhân, khoa/phòng, ngày/ca, mã phiếu.
2. Đăng nhập bác sĩ đúng phòng, đối chiếu hàng đợi và ca đang phục vụ.
3. Chỉ dùng ca đủ điều kiện cho bước muốn trình diễn. Mô tả seed ban đầu “chưa đặt lịch/đã hủy” đã thay đổi sau thao tác thật; không dùng nhãn cũ thay trạng thái API.
4. Lịch demo ngày 16/10/2026 và QR cũ không dùng để chứng minh check-in thành công ngày khác. Khi cần, tạo tài khoản/hồ sơ và lịch mới đúng ngày bằng giao diện/API thật.
5. Nếu phòng có nhiều bác sĩ hoạt động, ADMIN cấu hình đúng một **bác sĩ chính khi đặt lịch**. Không đổi bác sĩ trên ca cũ bằng cách sửa phân công danh mục.

## Ca sẵn để tiếp tục

- **DAL-001 / Bệnh nhân bàn giao 01**: Consultation `c0e4d579-a1df-4cbb-803e-ae6d5384a508`, B phụ trách, A tác giả gốc. Ca có chỉ định Lab của A, toa nháp của A và C, ghi chú kiểm thử do C lưu. Theo [luồng bàn giao](07-ban-giao-ca-kham.md).
- **NOI-010 / Demo Nội c1d9dc**: Consultation `5f0a2c7b-ec39-4e39-9083-bcf694df27cd`, An phụ trách, vẫn IN_PROGRESS. Giữ ca này để quan sát; dùng bộ Da liễu riêng khi thử bàn giao.
- Một lịch Nội thứ hai của bệnh nhân gốc chưa có phiếu do xung đột quy tắc lượt hoạt động. Không dùng lịch đó để check-in và không gán phiếu NOI-010 vào lịch khác.

Dữ liệu thay đổi sau mỗi lần chạy. Tạo mẫu mới phải là dữ liệu có quan hệ hợp lệ giữa tài khoản, hồ sơ, lịch, phiếu và ca khám. Không reset hàng đợi hoặc sửa SQL trạng thái của ca đang dùng chỉ để qua bước.
