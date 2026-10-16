# 02 — Đăng nhập, MFA và đăng xuất

[Quay về mục lục](README.md) · [Tài khoản](01-tai-khoan-va-du-lieu.md) · [Tiếp: đặt lịch/check-in](03-dat-lich-check-in.md)

## Đăng nhập thường hoặc hai bước

Điều kiện: API đang hoạt động, đúng tài khoản và đúng hồ sơ trình duyệt/điện thoại.

1. Bệnh nhân mở điện thoại giả lập; nhân viên nội bộ mở Hospital Web.
2. Nhập tài khoản và mật khẩu. Trang đăng nhập đầu tiên không yêu cầu điền OTP cùng mật khẩu.
3. Sai mật khẩu: nhận lỗi, không có phiên sử dụng chức năng.
4. Đúng mật khẩu và chưa bật MFA: vào màn hình chính theo vai trò.
5. Đúng mật khẩu và đã bật MFA: chuyển sang màn **Xác thực hai bước**. Bước kiểm tra mật khẩu chưa hoàn tất đăng nhập.
6. Nhập mã 6 số từ ứng dụng xác thực trên Mac hoặc chọn **Dùng mã khôi phục**, nhập một mã chưa dùng rồi **Xác nhận đăng nhập**.
7. Mã đúng: vào ứng dụng. Mã sai: ở lại bước 2. Không cố thử sai liên tục vì có lockout thật.
8. Hết khoảng chờ 5 phút, hủy hoặc tải mới document: bắt đầu lại tài khoản/mật khẩu.

Kết quả cần thấy: tên tài khoản và giao diện đúng vai trò; API bảo vệ thành công sau đủ các bước. `401 MFA_REQUIRED:` sau kiểm tra mật khẩu là tín hiệu chuyển bước, không phải mất kết nối Gateway.

## Bật MFA sau khi đã đăng nhập

Dùng tài khoản demo riêng để có thể thay đổi cấu hình mà không ảnh hưởng các ca khác.

1. Trên Mobile vào **Tài khoản → Bảo mật đăng nhập**.
2. Nhập mật khẩu hiện tại, chọn thiết lập xác thực hai bước.
3. Lưu setup key vào ứng dụng TOTP của bạn trên Mac. Setup key là secret thiết lập; OTP là mã 6 số thay đổi theo thời gian. Không cần điện thoại vật lý.
4. Lấy OTP hiện tại, nhập và xác nhận bật MFA trước khi secret thiết lập hết hạn.
5. Lưu các mã khôi phục vào nơi riêng ngay khi hiện ra, trước khi chọn đăng nhập lại. Mỗi mã chỉ dùng một lần.
6. Đăng nhập lại: đúng mật khẩu phải tới bước MFA; thử OTP hợp lệ hoặc một mã khôi phục chưa dùng.
7. Nếu cần tắt MFA, làm theo màn Bảo mật với thông tin xác thực hợp lệ rồi đăng nhập lại. Bật/tắt MFA thu hồi các phiên cũ.

## Đăng xuất và kiểm tra quyền

1. Đăng xuất riêng điện thoại/hồ sơ trình duyệt đã dùng. Phiên đó bị thu hồi.
2. Mở lại tài nguyên bảo vệ: yêu cầu đăng nhập lại; không tiếp tục bằng token cũ.
3. Các tài khoản trên điện thoại độc lập khác vẫn dùng phiên riêng của chúng.

Request đi qua Gateway rồi service nghiệp vụ; service vẫn xác minh phiên, quyền sở hữu và phân công. Đăng nhập không khởi động service đang tắt. DPoP ràng buộc token với khóa trình duyệt; chép token sang công cụ khác không thay cho đăng nhập hợp lệ. Passkey/Touch ID/Face ID và eKYC production chưa thuộc luồng đã nghiệm thu.
