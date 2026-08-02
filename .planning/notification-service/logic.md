# Notification Service Logic & Rules

## 1. Scope & Responsibility
- Quản lý Notification Inbox của từng User.
- Lắng nghe các sự kiện hệ thống (`AppointmentConfirmed`, `QueueNearTurn`, `PatientCalled`, `LabResultAvailable`, `PrescriptionIssued`, v.v.) để đẩy thông báo realtime qua WebSocket và Push Notification.
- Quản lý trạng thái đọc (`READ`/`UNREAD`) và số lượng thông báo chưa đọc (`unread-count`).

## 2. Rules & Constraints
- Notification gửi thất bại không làm dừng hoặc rollback giao dịch nghiệp vụ cốt lõi.
- Bảo mật thông tin: Không đưa dữ liệu chẩn đoán nhạy cảm hoặc PII cá nhân vào nội dung thông báo công cộng.
