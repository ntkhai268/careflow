# Trang chủ — Override

## Mục tiêu

Trong 5 giây, bệnh nhân phải biết:

1. Hôm nay có lịch/hành trình nào không?
2. Việc tiếp theo cần làm là gì?
3. Có thông báo quan trọng nào chưa đọc?

## Thứ tự nội dung

1. Header gọn: tên bệnh nhân, notification badge, không đặt ô tìm kiếm giả.
2. Active Journey card nếu có; đây là thành phần nổi bật nhất.
3. Primary CTA `Đặt lịch khám` nếu không có hành trình hoặc CTA phụ nếu đã có.
4. Quick actions thật: Lịch khám, Hồ sơ sức khỏe, Thông báo, Kết quả khám và toa thuốc.
5. Hướng dẫn chuẩn bị đi khám có nội dung tĩnh, không giả làm dữ liệu cá nhân hóa.

## Quy tắc

- Không hiển thị dịch vụ chưa có backend như video call, giúp việc, khám doanh nghiệp.
- Quick action dùng 2 cột hoặc list; không dùng grid 4 cột với nhãn nhiều dòng.
- Active Journey luôn nói rõ trạng thái và CTA tiếp theo.
- Khi không có hành trình, empty state không tạo cảm giác lỗi.
- `Kết quả khám và toa thuốc` là quick action dẫn tới danh sách các lượt đã hoàn tất; không thêm mục thứ sáu vào bottom navigation.
