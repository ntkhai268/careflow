# 03 — Đặt lịch → phiếu → check-in → hàng đợi

[Quay về mục lục](README.md) · [Tài khoản](01-tai-khoan-va-du-lieu.md) · [Tiếp: khám](04-kham-khong-cls.md)

## Chuẩn bị

Bệnh nhân chưa có lượt hoạt động trùng khoa/ngày, có hồ sơ thật; danh mục khoa/phòng/bác sĩ và slot còn chỗ. STAFF và DOCTOR dùng phiên riêng, có phân công đúng phòng. Muốn check-in ngay, chọn lịch **đúng ngày và phiên đủ điều kiện**. Nếu slot hợp lệ đã hết, dừng và chuẩn bị lịch khác, không đổi đồng hồ hoặc sửa ngày DB.

## Đặt lịch và lấy phiếu

Được chọn hồ sơ bản thân hoặc người thân thuộc cùng tài khoản. Sau đặt lịch, ứng dụng chuyển ngữ cảnh sang đúng người được chọn; **Lịch khám** liệt kê lịch của các hồ sơ thuộc tài khoản và chi tiết phiếu kiểm tra quyền chủ tài khoản, không chỉ hồ sơ mặc định.

Khi tải lại tab Lịch khám hoặc đường dẫn chi tiết phiếu, ứng dụng khôi phục và xác minh phiên đăng nhập trước khi tải hồ sơ/lịch. Chờ xác thực hoàn tất; nếu phiên hết hạn, đăng nhập lại. Không tạo lịch khác chỉ vì trang đang tải.

1. Trên điện thoại chọn **Đặt lịch khám**.
2. Chọn hồ sơ, khoa, ngày và ca còn chỗ từ dữ liệu API. Kiểm tra người khám trước khi xác nhận.
3. Trên app (11), chờ bảng giá tải từ API, chọn dịch vụ nếu có nhiều lựa chọn rồi kiểm tra giá và xác nhận. Không tải được giá thì chưa thể gửi; giá thay đổi cần đọc/xác nhận lại. Xem [bảng giá](14-tep-thiet-bi-va-bang-gia.md). Mở **Lịch khám → Chi tiết**: lịch phải được lưu với trạng thái CONFIRMED và báo giá khi đặt.
4. Mở **Xem phiếu khám**, đối chiếu mã phiếu, bệnh nhân, khoa/phòng, ngày và giờ.
5. Ghi mã phiếu để thao tác tại quầy. Nếu chưa có phiếu ngay, chờ ngắn và tải lại: Queue nhận sự kiện qua RabbitMQ sau response đặt lịch.
6. Nếu 404 kéo dài, xem [xử lý lỗi](09-ngoai-le-va-xu-ly-loi.md); không bấm tạo thêm lịch để né lỗi.

**Có lịch/phiếu chưa đồng nghĩa đã check-in.** Bác sĩ chưa thấy bệnh nhân trong hàng đợi phục vụ có thể là đúng ở bước này.

## Cách A — Nhân viên hỗ trợ check-in trên Mac

1. STAFF vào `/staff/checkin`. Hệ thống tự chọn phòng được phân công và chỉ cho chọn trong các phòng có quyền. **Bộ demo chính ở [trang 01](01-tai-khoan-va-du-lieu.md) dùng ROOM-01, Nội tổng quát**. Bộ E2E Cơ xương khớp trước đó dùng ROOM-11; không trộn hai bộ.
2. Trong **Danh sách tiếp nhận trong ngày**, lịch đã có phiếu ở trạng thái **Đang chờ/Chờ tiếp nhận** phải xuất hiện dù chưa check-in. Danh sách tự cập nhật mỗi 10 giây hoặc bấm **Tải lại**. Có thể bấm **Duyệt vào** trên dòng đúng bệnh nhân, hoặc nhập **mã phiếu** lấy trên điện thoại; không nhập UUID lịch hẹn.
3. Đối chiếu bệnh nhân và bấm **Hỗ trợ check-in**.
4. Kết quả phải thành công và lượt đạt CHECKED_IN.
5. DOCTOR đúng phòng vào `/dashboard/queue`; danh sách tự cập nhật mỗi 10 giây. Đối chiếu cùng bệnh nhân và mã phiếu rồi **Gọi số**. Phiếu chưa được tiếp nhận không xuất hiện trong hàng đợi được gọi. Bác sĩ/STAFF dùng các hồ sơ trình duyệt riêng để tránh thay tài khoản giữa các tab.
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

Đối chiếu ngày 17/10/2026: đặt lịch mới trên mobile trả 201; check-in STAFF trả 200; QR sai trả 401, QR ký hợp lệ với tọa độ nhập thử trả 200. Kiểm tra bổ sung hồ sơ người thân với CXK-007: đặt lịch → STAFF thấy trước check-in → Duyệt vào → bác sĩ thấy và Gọi số thành công; tải lại chi tiết và danh sách vẫn hiển thị lịch. Lượt thử được gọi đủ ba lần rồi chuyển MISSED để không giữ lượt đang gọi. Không dùng lượt này làm ca khám mới. Camera/GPS vật lý chưa được kiểm tra; các ca CXK trước đó đã hoàn tất/hủy. Để lặp lại cần hồ sơ không có lượt trùng khoa/ngày và slot đúng ngày thực tế còn chỗ.
