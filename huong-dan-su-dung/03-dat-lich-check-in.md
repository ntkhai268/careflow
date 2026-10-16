# 03 — Đặt lịch → phiếu → check-in → hàng đợi

[Quay về mục lục](README.md) · [Tài khoản](01-tai-khoan-va-du-lieu.md) · [Tiếp: khám](04-kham-khong-cls.md)

## Chuẩn bị

Bệnh nhân chưa có lượt hoạt động trùng khoa/ngày, có hồ sơ thật; danh mục khoa/phòng/bác sĩ và slot còn chỗ. STAFF và DOCTOR dùng phiên riêng, có phân công đúng phòng. Muốn check-in ngay, chọn lịch **đúng ngày và phiên đủ điều kiện**. Nếu slot hợp lệ đã hết, dừng và chuẩn bị lịch khác, không đổi đồng hồ hoặc sửa ngày DB.

## Đặt lịch và lấy phiếu

1. Trên điện thoại chọn **Đặt lịch khám**.
2. Chọn hồ sơ, khoa, ngày và ca còn chỗ từ dữ liệu API. Kiểm tra người khám trước khi xác nhận.
3. Xác nhận đặt lịch. Mở **Lịch khám → Chi tiết**: lịch phải được lưu với trạng thái CONFIRMED.
4. Mở **Xem phiếu khám**, đối chiếu mã phiếu, bệnh nhân, khoa/phòng, ngày và giờ.
5. Ghi mã phiếu để thao tác tại quầy. Nếu chưa có phiếu ngay, chờ ngắn và tải lại: Queue nhận sự kiện qua RabbitMQ sau response đặt lịch.
6. Nếu 404 kéo dài, xem [xử lý lỗi](09-ngoai-le-va-xu-ly-loi.md); không bấm tạo thêm lịch để né lỗi.

**Có lịch/phiếu chưa đồng nghĩa đã check-in.** Bác sĩ chưa thấy bệnh nhân trong hàng đợi phục vụ có thể là đúng ở bước này.

## Cách A — Nhân viên hỗ trợ check-in trên Mac

1. STAFF vào `/staff/checkin`, chọn đúng phòng từ danh mục.
2. Nhập **mã phiếu** lấy trên điện thoại; không nhập UUID lịch hẹn.
3. Đối chiếu bệnh nhân và bấm **Hỗ trợ check-in**.
4. Kết quả phải thành công và lượt đạt CHECKED_IN.
5. DOCTOR đúng phòng vào `/dashboard/queue`, tải lại; đối chiếu cùng bệnh nhân và mã phiếu.
6. Điện thoại tải lại hành trình để xem đã tiếp nhận. Sau đó sang luồng gọi/khám.

Cách này dùng nghiệp vụ tiếp nhận thật, không cần camera hoặc GPS.

## Cách B — Bệnh nhân nhập QR trên giả lập

Thử trên một lịch đủ điều kiện **chưa check-in**, không dùng lại lượt đã hoàn tất cách A để giả làm một check-in mới.

1. STAFF tạo QR phòng/phiên còn hiệu lực trên trang tiếp nhận, chọn **Sao chép nội dung QR**. Nếu clipboard bị chặn, chọn phần văn bản QR rồi sao chép thủ công.
2. Trên phiếu điện thoại mở **Quét QR check-in tại bệnh viện**.
3. Dán vào ô **Nội dung QR check-in**. Đây là chuỗi QR do Queue ký, không phải ảnh QR, token đăng nhập hoặc mã phiếu.
4. ADMIN đọc cấu hình vị trí ở `/admin/schedule`. Nhập latitude/longitude/accuracy thử nghiệm đúng cấu hình hoặc dùng vị trí trình duyệt.
5. Bấm **Xác nhận check-in**. Server phải kiểm tra người sở hữu, ngày/phiên/phòng, chữ ký/hạn QR và geofence.
6. Đối chiếu CHECKED_IN ở điện thoại, quầy và hàng đợi bác sĩ.

Mốc cấu hình đã kiểm tra trên Mac: tâm `10.7769, 106.7009`, bán kính 150 m, accuracy tối đa 50 m. Chỉ dùng nếu cấu hình hiện tại vẫn khớp; accuracy 10 m là đầu vào thử nghiệm. Tọa độ nhập tay không chứng minh vị trí vật lý của thiết bị.

## Kết thúc và lặp lại

Ghi lại appointment, phiếu và phòng cho chặng tiếp theo. Mỗi lần demo mới đọc trạng thái thật. Ngày/QR cũ không dùng cho ngày mới. QR sai/hết hạn hoặc vị trí ngoài điều kiện phải bị từ chối và không chuyển trạng thái.
