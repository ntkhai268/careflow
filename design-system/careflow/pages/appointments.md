# Lịch khám và phiếu khám — Override

## Mô hình thông tin

- Appointment là lịch đặt khám.
- Visit Ticket là phiếu do Queue Service cấp sau khi Appointment được xác nhận.
- Một Appointment có thể hiện ngay cả khi Visit Ticket đang được tạo.

## Cấu trúc màn hình

1. Tiêu đề `Lịch khám` và CTA `Đặt lịch`.
2. Segment `Sắp tới` / `Đã hoàn tất & đã hủy`.
3. Appointment card: ngày, khung giờ, khoa/phòng, trạng thái và hành động chính.
4. Nếu ticket có sẵn: `Xem phiếu khám`; nếu chưa: `Đang cấp phiếu` và retry nền.

## Trạng thái bắt buộc

- Loading skeleton không xóa dữ liệu cũ khi refresh.
- Empty state `Bạn chưa có lịch khám sắp tới` + `Đặt lịch khám`.
- Appointment đã tạo nhưng ticket chưa có không được biến mất khỏi danh sách.
- Không hiển thị exception kỹ thuật; dùng thông báo tiếng Việt và nút thử lại.
