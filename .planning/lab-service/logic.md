# Lab Service Logic & Rules

## 1. Scope & Responsibility
- Tiếp nhận các chỉ định cận lâm sàng (xét nghiệm, siêu âm, X-quang) từ Bác sĩ trong ca khám (`consultationId`).
- Quản lý danh mục dịch vụ cận lâm sàng và điểm thực hiện (`servicePointId`).
- Quản lý trạng thái thanh toán/BHYT demo (`paymentStatus`).
- Tiếp nhận kết quả cận lâm sàng do Kỹ thuật viên nhập và phát hành kết quả.
- Gửi event `AllRequiredResultsAvailable` khi toàn bộ chỉ định của một ca khám đã sẵn sàng để Queue Service xếp lịch đọc kết quả.

## 2. Rules & Constraints
- Không yêu cầu bệnh nhân check-in lần hai tại khu cận lâm sàng. Lượt xếp hàng cận lâm sàng do Queue Service tự động tạo khi nhận event chỉ định đủ điều kiện.
- Lưu giữ nguyên bản giá trị kết quả (`result_value`), khoảng tham chiếu và cờ cảnh báo (`result_flag`).
