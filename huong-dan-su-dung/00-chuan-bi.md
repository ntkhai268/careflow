# 00 — Chuẩn bị trên Mac

[Quay về mục lục](README.md) · [Tiếp: tài khoản](01-tai-khoan-va-du-lieu.md)

## Đúng phiên bản đang chạy

Runtime hiện tại ngày21/10/2026 dùng source `.worktrees/release-r7`, nhánh `feature/dangkhoii/release-r7`, nền907cba2 kèm thay đổi chưa commit. Xem [bàn giao R7](25-nghiem-thu-va-ban-giao-r7.md). Clone source vẫn cần build và cấp cấu hình private, chứng thư, DB/dữ liệu phù hợp; bộ runtime Mac đã provision không phải bộ cài cho máy mới.

Máy Mac hiện tại đã có PostgreSQL, RabbitMQ, JAR, Flutter Web build, cấu hình CA và backup riêng ngoài Git. PostgreSQL thật của bộ kiểm thử chạy tại localhost:15432, Gateway HTTPS/mTLS localhost:18080; không ghi vào DB production/remote. Không có response nghiệp vụ giả trong buổi E2E. Đây là nhiều tiến trình trên một Mac, chưa phải kiểm chứng triển khai nhiều máy vật lý.

## Mở stack hiện có

1. Mở Docker Desktop và đợi Docker sẵn sàng.
2. Mở Terminal ở gốc **worktree R7**, nơi có launcher đã chuẩn bị:

   ```sh
   cd /Users/dangkhoii/careflow/.worktrees/release-r7
   ./check-mac-demo.command
   ./start-mac-demo.command
   ```

3. Hoặc kiểm tra bằng `/usr/bin/python3 scripts/mac-demo/demo.py status`. Có14 backend gồm Analytics/AI/Identity phụ và Hospital Web; giả lập web chỉ kiểm khi đã build. App iPhone hoạt động độc lập với giả lập. Cầu nối iPhone và worker AI khởi động riêng theo trang11/23/25.
4. Mở các giao diện bên dưới. Launcher phục hồi thành phần thiếu và giữ tiến trình đang khỏe; không reset DB/hàng đợi. Nếu file launcher chưa tồn tại trong checkout, đang dùng nhầm source hoặc chưa có bộ runtime Mac, không thể dùng lệnh này như bộ cài từ Git.
5. Đăng nhập tài khoản tại [trang 01](01-tai-khoan-va-du-lieu.md). Kiểm tra tên tài khoản/phòng trước khi thao tác.

| Giao diện | Địa chỉ |
|---|---|
| Mobile Emulator (tùy chọn, cần build riêng) | http://localhost:3000 |
| Hospital Web | http://localhost:5173/login |
| Tiếp nhận | http://localhost:5173/staff/checkin |
| Hàng đợi bác sĩ | http://localhost:5173/dashboard/queue |
| Hàng đợi xét nghiệm | http://localhost:5173/lab/queue |
| Quầy thuốc | http://localhost:5173/staff/pharmacy |
| Quản lý cơ sở/phân công | http://localhost:5173/admin/facility |
| Cấu hình lịch/check-in | http://localhost:5173/admin/schedule |
| Bàn giao bác sĩ | http://localhost:5173/dashboard/handovers |
| Thống kê lượt khám ADMIN | http://localhost:5173/admin/analytics |
| Cấp quyền điều phối | http://localhost:5173/admin/handovers |

Gateway runtime này là **HTTPS localhost:18080**. Browser dùng proxy cùng origin của Web/giả lập và CA đã cấu hình. Cổng 8080 không phải Gateway đang dùng trên Mac. Trang giao diện HTTP 200 chưa chứng minh API nghiệp vụ hoạt động; xem thêm Nhật ký API.

## Tách phiên để trình diễn nhiều vai trò

1. Mỗi điện thoại trong giả lập đăng nhập một bệnh nhân khác nhau. Phiên tách theo điện thoại và Gateway.
2. Hospital Web dùng chung đăng nhập giữa các tab cùng hồ sơ Chrome. Dùng hồ sơ Chrome riêng cho bác sĩ B, bác sĩ C và ADMIN; STAFF/LAB dùng hồ sơ riêng khi cần thao tác đồng thời.
3. Trong giả lập dùng **Phóng to**, **Trang chủ**, **Lịch khám**, **Quay lại**, **Tải lại** trên đúng máy. Checkbox dành cho thao tác nhóm. **Tạm dừng/Gỡ** thực hiện theo hộp xác nhận của từng máy.
4. Mở **Nhật ký API**, chọn đúng máy; chỉ ghi đường dẫn, phương thức, trạng thái và thời điểm khi báo lỗi.
5. Sau cập nhật bản build, tải lại Web và điện thoại. MFA đã được gỡ; đăng nhập mật khẩu không có bước OTP. Passkey chỉ dùng được trên origin/RP đã cấu hình, localhost trên Mac này được hỗ trợ.

## Khi chỉ chạy riêng giả lập

`npm start` trong `careflow-mobile-emulator-service` chỉ mở frontend/proxy; không khởi động backend hoặc tạo dữ liệu. Máy clone mới cần build Flutter Web theo [README kỹ thuật của service](../careflow-mobile-emulator-service/README.md) và cấu hình Gateway/CA của môi trường được cấp. Không tắt xác minh chứng thư để xử lý lỗi TLS. Trên Mac hiện có, ưu tiên launcher để dùng đúng cấu hình đã chuẩn bị.

## Sao lưu và mở lại sau khi tắt máy

1. Tại worktree tích hợp, chạy `/usr/bin/python3 scripts/mac-demo/demo.py backup` để lưu DB và snapshot cấu hình/JAR/tệp hiện tại.
2. Dữ liệu riêng nằm ở `~/Library/Application Support/CareFlow/mac-demo/`: `runtime/`, `backups/`, `logs/`. Backup chứa dữ liệu/khóa riêng, giữ ngoài Git.
3. Giữ Docker containers/volumes hiện có. Sau khi mở Mac, mở Docker Desktop rồi chạy launcher. Source và bản build trong worktree vẫn phải còn.
4. Launcher không tự restore DB, tái tạo container đã xóa hoặc build code mới. Sau thay đổi backend, build/deploy đúng phiên bản và sao lưu lại.
5. Chưa kiểm chứng restore DB đầy đủ hoặc reboot toàn máy trong các lần kiểm tra này; không coi việc tạo backup là bằng chứng đã thử phục hồi thành công.

## Build lại đúng môi trường đã kiểm tra

Dừng tiến trình Hospital Web do launcher quản lý trước khi build để tránh trộn chunk cũ/mới. Tại worktree tích hợp:

```sh
CAREFLOW_GATEWAY_URL=https://localhost:18080 \
NEXT_PUBLIC_API_URL=http://localhost:5173 \
NODE_EXTRA_CA_CERTS=/tmp/careflow-dangkhoii-audit/security-hardening/tls/ca.crt \
npm --prefix frontend/doctor-web run build
npm --prefix careflow-mobile-emulator-service run build
/usr/bin/python3 scripts/mac-demo/demo.py start
```

Chỉ khởi động lại khi build thành công. Không chạy build Web thiếu Gateway URL: giá trị mặc định có thể trỏ sang môi trường khác. Launcher không tự triển khai JAR mới; cập nhật backend bằng quy trình runtime riêng rồi kiểm tra health và sao lưu.
