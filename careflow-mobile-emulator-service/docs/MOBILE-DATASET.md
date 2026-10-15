# Dataset thật cho 3 điện thoại

Mở **http://localhost:3000**, tải lại trang và đăng nhập trên từng điện thoại theo bảng dưới. Mở **Tài khoản kiểm thử** dưới máy để xem tài khoản/mật khẩu và hướng dẫn. Nếu máy đang đăng nhập tài khoản khác, đăng xuất riêng máy đó trước. Chọn ca mẫu chỉ đổi phần hướng dẫn/tài khoản; không đổi phiên đang đăng nhập hoặc tự sửa trạng thái khám.

| Điện thoại | Ca mẫu | Tài khoản | Mật khẩu |
|---|---|---|---|
| 01 | Bệnh nhân mới, có hồ sơ và chưa đặt lịch | `mobile_case01` | `Password123@` |
| 02 | Đã đặt lịch, có phiếu khám | `mobile_case02` | `Password123@` |
| 03 | Đã đặt rồi hủy lịch | `mobile_case03` | `Password123@` |

Lịch mẫu tạo ngày 15/10/2026: **16/10/2026, 07:30–08:00, khoa Nội tổng quát, Phòng 01**. Điện thoại 02 giữ lịch `CONFIRMED`, phiếu `TICKET_ISSUED` (NOI-002). Điện thoại 03 có lịch và phiếu `CANCELLED` (NOI-003). Trạng thái có thể thay đổi khi thao tác thật; xem trạng thái mới nhất trong ứng dụng.

## Các thao tác tương ứng

1. **Máy 01:** vào Hồ sơ để xem Mẫu Mobile 01; vào Lịch khám thấy chưa có lịch; thử Đặt lịch, chọn khoa/ngày/slot từ danh mục API thật.
2. **Máy 02:** vào Lịch khám → lịch đã xác nhận → xem phiếu khám. Check-in cần đúng ngày khám, QR bệnh viện còn hiệu lực và tọa độ hợp lệ. Check-in ngày 15/10 cho lịch 16/10 bị từ chối theo nghiệp vụ.
3. **Máy 03:** vào Lịch khám xem lịch đã hủy; thử đặt lịch mới. Lịch hủy giữ lại làm lịch sử, không bị xóa.

Các máy mới thêm có nút chọn một trong ba ca để xem tài khoản. Để thử nhiều bệnh nhân cùng lúc, dùng ba tài khoản khác nhau. Chọn cùng tài khoản trên nhiều máy có thể chịu chính sách session của Identity.

## Nguồn dữ liệu và tạo lại

Toàn bộ tài khoản, hồ sơ, lịch và phiếu được ghi vào **PostgreSQL audit riêng** qua `giả lập:3000 → Gateway:18080 → API service`; không ghi vào DB remote dùng chung. Đăng ký/đăng nhập, tạo hồ sơ, chọn slot, đặt và hủy lịch đều dùng API thật. Phiếu khám và hủy phiếu được Queue xử lý từ sự kiện thật của Appointment. Không chèn SQL đổi trạng thái, không chặn request để trả fixture, không dựng kết quả khám/toa thuốc.

```sh
cd careflow-mobile-emulator-service
npm run dataset:seed -- --audit-db
npm run test:dataset
```

Lệnh seed chỉ nhận Gateway audit `http://127.0.0.1:18080`, chọn slot còn chỗ trong 7 ngày tới khi cần tạo lịch. Chạy lại dùng tài khoản/hồ sơ/lịch hiện có, không tự reset hoặc xóa dữ liệu. Nếu đã dùng máy để thay đổi ca mẫu, lệnh báo trạng thái không còn khớp để bạn kiểm tra, thay vì sửa ngược dữ liệu.

`.runtime/mobile-dataset.json` chỉ chứa tài khoản test và tham chiếu bản ghi, không chứa JWT/refresh token. Endpoint `/__emulator/dataset` cung cấp phần hướng dẫn này cho bàn điều khiển cục bộ; các API nghiệp vụ vẫn chuyển nguyên request đến backend. Đổi sang Gateway khác sẽ ngừng hiển thị dataset này. Bàn điều khiển luôn mở `demo=false`, kể cả khi khôi phục cấu hình Demo cũ.

Các ca này chưa xác nhận check-in thành công, đang khám, xét nghiệm, toa thuốc hoặc quyết toán. Các trạng thái đó cần được thực hiện bằng luồng backend tương ứng. Notification hiện chưa chạy, nên thông báo không có dữ liệu thật để cung cấp.
