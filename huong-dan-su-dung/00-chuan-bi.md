# 00 — Chuẩn bị trên Mac

[Quay về mục lục](README.md) · [Tiếp: tài khoản](01-tai-khoan-va-du-lieu.md)

## Đúng phiên bản đang chạy

Hướng dẫn mô tả runtime đã kiểm tra trên máy Mac hiện tại, source tại `.worktrees/security-integration`, nhánh `feature/dangkhoii/security-integration`. Các sửa bảo mật, MFA và bàn giao ở đó chưa được commit/push đồng thời với bộ tài liệu này. Clone nhánh tài liệu không tự có đầy đủ các chức năng, dữ liệu, chứng thư hoặc bản build hiện đang chạy.

Máy Mac hiện tại đã có PostgreSQL, RabbitMQ, JAR, Flutter Web build, cấu hình CA và backup riêng ngoài Git. Đây là nhiều tiến trình trên một Mac, chưa phải kiểm chứng triển khai nhiều máy vật lý.

## Mở stack hiện có

1. Mở Docker Desktop và đợi Docker sẵn sàng.
2. Mở Terminal ở gốc **worktree tích hợp**, nơi có launcher đã chuẩn bị:

   ```sh
   cd /Users/dangkhoii/careflow/.worktrees/security-integration
   ./check-mac-demo.command
   ./start-mac-demo.command
   ```

3. Hoặc kiểm tra bằng `python3 scripts/mac-demo/demo.py status`. Runtime hiện có 14 đầu kiểm tra: các backend, Identity phụ, Mobile→Gateway và Hospital Web.
4. Mở các giao diện bên dưới. Launcher phục hồi thành phần thiếu và giữ tiến trình đang khỏe; không reset DB/hàng đợi. Nếu file launcher chưa tồn tại trong checkout, đang dùng nhầm source hoặc chưa có bộ runtime Mac, không thể dùng lệnh này như bộ cài từ Git.
5. Đăng nhập tài khoản tại [trang 01](01-tai-khoan-va-du-lieu.md). Kiểm tra tên tài khoản/phòng trước khi thao tác.

| Giao diện | Địa chỉ |
|---|---|
| Mobile Emulator | http://localhost:3000 |
| Hospital Web | http://localhost:5173/login |
| Tiếp nhận | http://localhost:5173/staff/checkin |
| Hàng đợi bác sĩ | http://localhost:5173/dashboard/queue |
| Hàng đợi xét nghiệm | http://localhost:5173/lab/queue |
| Quầy thuốc | http://localhost:5173/staff/pharmacy |
| Quản lý cơ sở/phân công | http://localhost:5173/admin/facility |
| Cấu hình lịch/check-in | http://localhost:5173/admin/schedule |
| Bàn giao bác sĩ | http://localhost:5173/dashboard/handovers |
| Cấp quyền điều phối | http://localhost:5173/admin/handovers |

Gateway runtime này là **HTTPS localhost:18080**. Browser dùng proxy cùng origin của Web/giả lập và CA đã cấu hình. Cổng 8080 không phải Gateway đang dùng trên Mac. Trang giao diện HTTP 200 chưa chứng minh API nghiệp vụ hoạt động; xem thêm Nhật ký API.

## Tách phiên để trình diễn nhiều vai trò

1. Mỗi điện thoại trong giả lập đăng nhập một bệnh nhân khác nhau. Phiên tách theo điện thoại và Gateway.
2. Hospital Web dùng chung đăng nhập giữa các tab cùng hồ sơ Chrome. Dùng hồ sơ Chrome riêng cho bác sĩ B, bác sĩ C và ADMIN; STAFF/LAB dùng hồ sơ riêng khi cần thao tác đồng thời.
3. Trong giả lập dùng **Phóng to**, **Trang chủ**, **Lịch khám**, **Quay lại**, **Tải lại** trên đúng máy. Checkbox dành cho thao tác nhóm. **Tạm dừng/Gỡ** thực hiện theo hộp xác nhận của từng máy.
4. Mở **Nhật ký API**, chọn đúng máy; chỉ ghi đường dẫn, phương thức, trạng thái và thời điểm khi báo lỗi.
5. Sau cập nhật bản build, tải lại Web và điện thoại. Nếu đang ở bước MFA, tải mới document yêu cầu bắt đầu lại đăng nhập.

## Khi chỉ chạy riêng giả lập

`npm start` trong `careflow-mobile-emulator-service` chỉ mở frontend/proxy; không khởi động backend hoặc tạo dữ liệu. Máy clone mới cần build Flutter Web theo [README kỹ thuật của service](../careflow-mobile-emulator-service/README.md) và cấu hình Gateway/CA của môi trường được cấp. Không tắt xác minh chứng thư để xử lý lỗi TLS. Trên Mac hiện có, ưu tiên launcher để dùng đúng cấu hình đã chuẩn bị.

## Sao lưu và mở lại sau khi tắt máy

1. Tại worktree tích hợp, chạy `python3 scripts/mac-demo/demo.py backup` để lưu DB và snapshot cấu hình/JAR/tệp hiện tại.
2. Dữ liệu riêng nằm ở `~/Library/Application Support/CareFlow/mac-demo/`: `runtime/`, `backups/`, `logs/`. Backup chứa dữ liệu/khóa riêng, giữ ngoài Git.
3. Giữ Docker containers/volumes hiện có. Sau khi mở Mac, mở Docker Desktop rồi chạy launcher. Source và bản build trong worktree vẫn phải còn.
4. Launcher không tự restore DB, tái tạo container đã xóa hoặc build code mới. Sau thay đổi backend, build/deploy đúng phiên bản và sao lưu lại.
5. Chưa kiểm chứng restore DB đầy đủ hoặc reboot toàn máy trong các lần kiểm tra này; không coi việc tạo backup là bằng chứng đã thử phục hồi thành công.
