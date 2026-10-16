# 08 — Sau khám, hồ sơ, thông báo và tái khám

[Quay về mục lục](README.md) · [Tài khoản](01-tai-khoan-va-du-lieu.md)

Điều kiện: đúng bệnh nhân/hồ sơ; lượt khám đã có dữ liệu được phát hành. Với các bước chưa có dữ liệu, không giả phản hồi để làm đầy giao diện.

## Xem kết quả sau khám

1. Trên mobile vào **Kết quả khám và toa thuốc**, chọn đúng lượt khám.
2. Đối chiếu chẩn đoán, lời dặn, xét nghiệm đã phát hành và toa được phép xem.
3. Nếu cần kiểm tra kết quả đã hoàn tất sẵn, dùng bệnh nhân bàn giao 02 ở trang tài khoản. Không coi ca bàn giao 01 đang IN_PROGRESS là đã hoàn tất.
4. Mở lại lịch và lịch sử; bệnh nhân/consultation/phòng phải khớp. Việc toa được phát hành không đồng nghĩa đã phát thuốc hoặc đã thanh toán.

## Hồ sơ sức khỏe và tài liệu

1. Vào **Hồ sơ sức khỏe**, chọn đúng hồ sơ thuộc tài khoản.
2. Thêm tài liệu bằng file picker của trình duyệt; gửi và chờ API thành công.
3. Tải lại, mở tệp để kiểm tra tài liệu lưu thật. Dùng tệp demo không chứa thông tin người thật khi trình diễn.
4. Đối chiếu phần hồ sơ tự cung cấp với thông tin khám lâm sàng: Patient và EMR có trách nhiệm khác nhau. Upload thành công chưa chứng minh toàn bộ EMR đã đồng bộ.

## Thông báo

1. Mở **Thông báo**, kiểm tra inbox của đúng tài khoản.
2. Mở một tin và đối chiếu nội dung/đường dẫn với lượt liên quan, kiểm tra trạng thái đã đọc.
3. Trong lúc hệ thống có sự kiện thật, theo dõi inbox/WebSocket và tải lại khi cần. Bản Web không chứng minh FCM/native push trên điện thoại vật lý.
4. Bàn giao hiện dùng tải lại danh sách thủ công; không chờ thông báo đẩy của handover để kết luận nhận yêu cầu hay chưa.

## Tái khám

1. Khi có chỉ định/lịch tái khám, chọn đúng hồ sơ và lịch liên kết ca trước theo giao diện hiện có.
2. Chọn ngày/slot còn chỗ, xác nhận và kiểm tra liên kết lịch sử.
3. Tới ngày tái khám, thực hiện phiếu → check-in → hàng đợi → khám như lượt mới.
4. Ghi bằng chứng từng chặng nếu đang nghiệm thu; chưa coi toàn bộ chu kỳ tái khám là đã kiểm chứng chỉ vì chọn được ngày.
