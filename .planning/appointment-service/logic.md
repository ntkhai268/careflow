# Appointment Service Logic & Rules

## 1. Scope & Responsibility
- Quản lý lịch làm việc của Bác sĩ (`doctor_schedules`), khung giờ và capacity.
- Tiếp nhận yêu cầu đặt lịch khám từ Patient Mobile / Staff Web.
- Tự động xác nhận (`CONFIRMED`) khi slot còn capacity. Không dùng bước duyệt `PENDING` thủ công.
- Xử lý đặt lịch tái khám (`follow-up`).

## 2. Rules & Constraints
- Capacity khóa/giữ nguyên tử theo `department + date + timeSlot`.
- Ngăn chặn overbook khi có nhiều request đồng thời.
- Một bệnh nhân không thể có 2 lịch hẹn chưa hủy trong cùng một khung giờ.
- Khi nhận event hủy hoặc hoàn tất từ Queue/Consultation Service, cập nhật trạng thái `CANCELLED`, `FULFILLED`, `NO_SHOW`.
