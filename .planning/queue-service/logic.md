# Queue Management Service Logic & Rules

## 1. Scope & Responsibility
- Quản lý Visit Ticket, QR token và Số thứ tự (queue number).
- Xử lý Check-in bằng QR tại kiosk/bàn tiếp nhận để chuyển bệnh nhân từ `TICKET_ISSUED` sang `CHECKED_IN`.
- Quản lý Active FIFO Queue theo cặp `roomId + sessionDate + sessionCode`.
- Quản lý các loại Queue:
  1. `INITIAL_CONSULTATION`: Khám ban đầu tại phòng khám bác sĩ.
  2. `LAB_EXECUTION`: Tự động tạo lượt xếp hàng khu cận lâm sàng khi có Lab Order.
  3. `RESULT_REVIEW`: Tự động tạo lượt đọc kết quả khi đủ kết quả xét nghiệm, tự động chèn ngay sau 1 bệnh nhân khám ban đầu tiếp theo (`QUEUED_AFTER_NEXT_INITIAL`).

## 2. Rules & Constraints
- **Chỉ hiển thị `CHECKED_IN` trong Active Queue**: Bệnh nhân chưa check-in không xuất hiện trong danh sách bác sĩ gọi.
- **Không dùng mô hình N:M**: Mô hình cố định 1 Khoa - 1 Phòng - 1 Bác sĩ / phiên.
- **Idempotent Check-in**: Quét QR nhiều lần trả về cùng một trạng thái đã `CHECKED_IN`.
