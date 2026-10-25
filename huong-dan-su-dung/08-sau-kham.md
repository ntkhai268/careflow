# 08 — Sau khám, hồ sơ, thông báo và tái khám

[Quay về mục lục](README.md) · [Tài khoản](01-tai-khoan-va-du-lieu.md)

Điều kiện: đúng bệnh nhân/hồ sơ; lượt khám đã có dữ liệu được phát hành. Với các bước chưa có dữ liệu, không giả phản hồi để làm đầy giao diện.

## Xem kết quả sau khám

1. Trên mobile vào **Kết quả khám và toa thuốc**, chọn **Theo lịch hẹn** hoặc **Khám không hẹn**, rồi chọn đúng lượt khám. Nhánh không hẹn liệt kê ca đã hoàn tất của các hồ sơ thuộc tài khoản; không dựng lịch hẹn giả.
2. Đối chiếu chẩn đoán, lời dặn, xét nghiệm đã phát hành và toa được phép xem.
3. Dùng ba bệnh nhân CXK trong bộ E2E ngày 17/10 ở trang tài khoản để xem các ca đã hoàn tất; ca cũ khác phải đọc lại trạng thái.
4. Mở lại lịch và lịch sử; bệnh nhân/consultation/phòng phải khớp. Việc toa được phát hành không đồng nghĩa đã phát thuốc hoặc đã thanh toán.

## Hồ sơ sức khỏe và tài liệu

**App iPhone (11):** tệp có nút **Mở** và **Chia sẻ/Lưu tệp**, tải bằng phiên được cấp quyền. Xem [hướng dẫn 14](14-tep-thiet-bi-va-bang-gia.md). Các bước trình duyệt dưới đây giữ phạm vi nghiệm thu web trước đó; chúng không thay thế nghiệm thu trình xem iOS.

1. Vào **Hồ sơ sức khỏe**, chọn đúng hồ sơ thuộc tài khoản.
2. Mở thêm hồ sơ sức khỏe, kiểm tra tên bệnh nhân tải từ API đúng hồ sơ đang chọn; nếu chưa tải được tên, thử lại trước khi gửi. Thêm tài liệu bằng file picker của trình duyệt; gửi và chờ API thành công.
3. Tải lại, mở bản ghi và bấm chip tên tệp. Tệp được tải bằng phiên có quyền, xuất hiện trong danh sách tải xuống của trình duyệt. Đã đối chiếu bytes/SHA-256 của file tải xuống với bản upload thật ngày 17/10. Tài khoản khác nhận 403. Với hồ sơ thứ hai, dùng **Xem sức khỏe bệnh nhân khác** và chọn hồ sơ thuộc tài khoản. Dùng tệp demo không chứa thông tin người thật khi trình diễn.
4. Đối chiếu phần hồ sơ tự cung cấp với thông tin khám lâm sàng: Patient và EMR có trách nhiệm khác nhau. Upload thành công chưa chứng minh toàn bộ EMR đã đồng bộ.

## Thông báo

1. Mở **Thông báo**, kiểm tra inbox của đúng tài khoản.
2. Chạm một tin để đánh dấu đã đọc; API `/read` đã trả 200 trong kiểm thử. Nội dung phải khớp lượt liên quan. Không giả định mọi tin tự điều hướng sang trang khác.
3. Trong lúc hệ thống có sự kiện thật, theo dõi inbox/WebSocket và tải lại khi cần. Bản Web không chứng minh FCM/native push trên điện thoại vật lý.
4. Bàn giao hiện dùng tải lại danh sách thủ công; không chờ thông báo đẩy của handover để kết luận nhận yêu cầu hay chưa.

## Tái khám

1. Bác sĩ phụ trách mở ca, vào **Tóm tắt lâm sàng → Hẹn tái khám từ ca khám**, hoặc chọn **Lịch hẹn tái khám** trên toa trước khi lưu/ký.
2. Chọn ngày và ca còn chỗ. Khoa lấy từ phân công thực của bác sĩ, ca lấy từ lịch phòng của chính bác sĩ đó; không có mặc định Nội tổng quát/09:00.
3. Với lịch riêng, kiểm tra danh sách lịch của ca trước khi xác nhận. Với toa, lưu nháp rồi ký và đọc trạng thái lịch. Không coi lời dặn tái khám hoặc ngày dự kiến là đã đặt lịch.
4. Lỗi hết chỗ/kết nối: đọc trạng thái, tải lại lịch nguồn và thử lại theo [trang20](20-ho-so-va-hanh-trinh-lam-sang-r3.md). Không đổi ngày/ca của lệnh đã tạo để cố gửi lại. Chỉ khi API xác nhận có mã lịch mới báo thành công.
5. Bệnh nhân mở **Lịch khám**, đối chiếu ngày/ca, bác sĩ và hồ sơ. Tới ngày hẹn, tiếp nhận/check-in theo [trang03](03-dat-lich-check-in.md); lần tái khám tạo **ca mới**, giữ liên kết ca nguồn, không mở lại ca cũ.

Bằng chứng 17/10 là mốc API/web cũ. R3 bổ sung chọn ca và đặt lịch qua web bác sĩ, báo riêng các bước chưa hội tụ; phạm vi thực tế ở [biên bản R3](../.planning/consultation-service/r3-acceptance-2026-10-20.md). Native push/background và reminder trước ngày hẹn thuộc R4, chưa được chứng minh bởi lịch hoặc inbox hiện có.

Cập nhật R4 (20/10/2026): cách nhận thông báo, mở đúng tài nguyên sau kiểm tra quyền, tải bù và phạm vi push thực tế xem [hướng dẫn 21](21-thong-bao-va-dong-bo-r4.md).
