# 02 — Đăng nhập, passkey và quên mật khẩu

[Quay về mục lục](README.md) · [Tài khoản](01-tai-khoan-va-du-lieu.md) · [App điện thoại](12-dang-nhap-mobile.md) · [Bản vá bảo mật](13-ban-va-bao-mat-va-van-hanh.md)

**Phạm vi source 19/10/2026:** hướng dẫn này mô tả bản vá chưa triển khai/cài lại trên máy thật. iPhone đang cài Release 1.0.0 (8) và runtime trước bản vá có thể còn giao diện cũ. MFA/TOTP cũ đã gỡ; email quên mật khẩu không phải MFA. Câu hỏi bí mật đã ngừng hỗ trợ trong bản vá, cả cấu hình và đăng nhập đều trả 410.

## Đăng nhập

1. Mở đúng app hoặc Hospital Web bằng phiên riêng.
2. Bệnh nhân nhập tài khoản/mật khẩu, hoặc chọn Face ID/vân tay đã bật trên thiết bị. Máy không hỗ trợ sinh trắc học vẫn dùng mật khẩu.
3. Trên web, chọn **Đổi hình thức đăng nhập → Vân tay / Khuôn mặt**, nhập tài khoản và hoàn tất passkey trong hộp xác thực trình duyệt.
4. Production yêu cầu tài khoản nội bộ dùng passkey đã đăng ký. Không thử mở production khi chưa cấp passkey và nghiệm thu quy trình cấp lại cho nhân viên.
5. Sai thông tin không tạo phiên. Gặp 429 cần chờ theo thông báo; không thử liên tục trên tài khoản trình diễn khám.

## Thiết lập và gỡ passkey trên web

1. Trong phiên hợp lệ, mở menu tài khoản → **Hình thức đăng nhập**.
2. Chọn **Vân tay / Khuôn mặt**, nhập mật khẩu hiện tại rồi **Thêm passkey**.
3. Hoàn tất xác thực thiết bị. Chỉ coi thành công khi server lưu khóa; hủy hộp thoại không tạo khóa.
4. Danh sách hiển thị passkey và ngày thêm. Chọn **Gỡ**, xác nhận bằng mật khẩu hiện tại. Các phiên bị thu hồi; tài khoản nội bộ production phải còn cách xác thực được cấp hợp lệ trước khi gỡ khóa cuối cùng.

App native sử dụng đăng ký thiết bị, không gọi browser passkey; xem [trang 12](12-dang-nhap-mobile.md).

## Quên mật khẩu

Chỉ dùng mã gửi tới email đã đăng ký theo [trang 12](12-dang-nhap-mobile.md#quên-mật-khẩu-qua-email): nhập email → xác thực mã → nhập mật khẩu mới. Không reset bằng câu hỏi, passkey hoặc SMS. Xác thực mã riêng chưa đổi mật khẩu.

Sau reset, đăng nhập lại và thiết lập lại Face ID/passkey; các phiên và credential cũ không còn dùng được. Nhân viên production mất passkey cần quy trình hỗ trợ xác minh, không thể dùng email để tự cấp lại passkey.

## Đăng xuất

Đăng xuất kết thúc phiên hiện tại, giữ đăng ký Face ID trên app nếu người dùng đã bật. Web xóa cookie phiên; tải lại trang/tài nguyên bảo vệ phải yêu cầu đăng nhập. Token vẫn ràng buộc DPoP; sao chép token không tạo quyền truy cập. Route ngoài vai trò phải hiện 403.

## Phạm vi nghiệm thu

Bản vá có kiểm thử tự động và kiểm tra web cục bộ; Face ID/passkey thật, nhiều tab trình duyệt trên production, cấp lại tài khoản nội bộ và hạ tầng thật chưa được nghiệm thu đầy đủ. Kết quả kiểm tra câu hỏi trước 19/10 là lịch sử, không phải hướng dẫn tiếp tục sử dụng phương thức đã ngừng.
