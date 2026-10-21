# 09 — Ngoại lệ và xử lý lỗi

[Quay về mục lục](README.md)

## Đọc Nhật ký API

1. Mở Nhật ký API trong giả lập, chọn đúng điện thoại.
2. Ghi thời điểm, phương thức, đường dẫn và HTTP; xác định request đầu tiên thất bại trước khi bấm lại nhiều lần.
3. Đối chiếu trạng thái hiện tại ở giao diện vai trò liên quan. Với request bị từ chối, kiểm tra dữ liệu không đổi.
4. Gửi thông tin tái hiện: tài khoản demo/vai trò, phòng, bước trước đó, trạng thái mong đợi/thực tế và mã tham chiếu cần thiết. Không ghi Authorization, proof, token, OTP, secret/recovery MFA hoặc bệnh án người thật.

| Hiện tượng | Thao tác kiểm tra |
|---|---|
| Web 200 nhưng API không chạy | Kiểm tra Mobile→Gateway và backend health; Web 200 mới chứng minh frontend được phục vụ |
| Gateway không kết nối | Đúng HTTPS localhost:18080 của runtime này, CA và tiến trình; không đổi sang 8080/HTTP để né TLS |
| 401 | Đăng nhập lại nếu phiên hết/thu hồi, kiểm tra đúng máy/Gateway; MFA đã gỡ, API MFA cũ trả 410 |
| 403 | Đúng vai trò, chủ hồ sơ, phân công khoa/phòng/điểm phục vụ và bác sĩ phụ trách hiện tại |
| 404 phiếu sau đặt lịch | Chờ consumer ngắn rồi tải lại; nếu kéo dài kiểm tra sự kiện và trùng lượt, không tạo lịch liên tiếp |
| Không thấy bệnh nhân trên bác sĩ | Đã check-in, đúng ngày/phiên/phòng; phân công bác sĩ và phiếu có khớp không |
| 409 gọi/bắt đầu/nhận bàn giao | Tải lại trạng thái; kiểm tra ca đang phục vụ, yêu cầu chờ, giới hạn gọi và thay đổi phân công |
| 409 resume/hoàn tất CLS | Chờ đủ kết quả; đọc và xác nhận các order đã phát hành trước khi hoàn tất ca |
| 422 chỉ định | Chọn mã và điểm từ danh mục thật, không gửi mã tùy ý |
| 422 bàn giao | Hồ sơ bác sĩ đang hoạt động, cùng khoa/phòng và có liên kết phiếu hợp lệ |
| 503/5xx | Dependency hoặc quyền liên dịch vụ không xác minh được; ghi thời điểm/correlation ID nếu có để truy log |
| Home không tải hành trình | Xem từng API Appointment/Queue/Consultation/Lab/Prescription; lỗi một API có thể chặn phần tổng hợp |
| Mở ca bị 403/404 | Hiện màn lỗi cố định và nút quay lại/thử lại; không được hiện form trống với trạng thái Hoàn tất |

## Vắng mặt, gọi lại và hủy lịch

1. Chọn đúng lượt, đọc trạng thái trước khi thao tác.
2. **Gọi lại** tại quầy tiếp nhận giữ tên nút thống nhất: lần đầu ở lượt đủ điều kiện dùng thao tác gọi; lượt CALLED mới recall. Giới hạn hiện tại là ba lần.
3. Đánh dấu vắng/đưa lại hàng chỉ khi trạng thái cho phép; kiểm tra thứ tự/lịch sử sau thao tác.
4. Hủy lịch còn được phép hủy từ mobile, xác nhận và đối chiếu lịch/phiếu/lượt liên quan; giữ lịch sử.
5. QR sai/hết hạn/sai phòng hoặc vị trí không đạt phải bị từ chối, không đổi CHECKED_IN.
6. Không ép CHECKED_IN → CALLED hoặc trạng thái khác bằng SQL để che lỗi transition. Nếu bị chặn ngoài dự kiến, ghi bước tái hiện để sửa và kiểm tra lại.

## Dữ liệu cần tránh dùng nhầm

Lịch Nội thứ hai của ca gốc chưa có phiếu do quy tắc lượt hoạt động chưa nhất quán. Đây là lỗi còn tồn tại, không phải bệnh nhân cần check-in lại. Giữ phiếu gốc với lịch gốc.

Khi ca đang ở bác sĩ khác do bàn giao, bác sĩ cũ bị từ chối là đúng. Người nhận phải tự tiếp nhận yêu cầu chờ; ADMIN đổi phân công không chuyển trách nhiệm ca.
