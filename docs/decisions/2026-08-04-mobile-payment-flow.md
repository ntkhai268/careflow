# Mobile MVP — Phí khám và tiền thuốc

> Trạng thái: Accepted for Mobile MVP  
> Ngày chốt: 2026-08-04

## Quyết định

1. Luồng đặt khám được trình bày theo thứ tự thực tế:
   `chọn hồ sơ/khoa/ngày/ca → chọn dịch vụ → thanh toán phí khám → nhận phiếu khám`.
2. Trong lúc Hospital Directory/Appointment chưa có catalog dịch vụ, Mobile
   dùng đúng một mục cố định:
   - code: `GENERAL_CONSULTATION`;
   - tên: `Khám thường`;
   - giá demo: `150.000 ₫`;
   - thời lượng tham khảo: 15 phút.
3. Người bệnh có thể chọn `ONLINE_MOCK` hoặc `CASH_AT_HOSPITAL`. Cả hai lựa
   chọn đều được lưu cùng lịch hẹn; tiền mặt được hiển thị là
   `DUE_AT_HOSPITAL`, không tuyên bố đã thu tiền.
4. Appointment Service hiện chưa nhận payment/service contract. Mobile lưu
   `AppointmentPaymentReceipt` qua local adapter để diễn tả UX và có thể thay
   adapter bằng API thanh toán sau này. Việc local storage lỗi không được làm
   hỏng một lịch hẹn đã tạo thành công.
5. Sau khi bác sĩ kê toa, hành trình có bước riêng:
   `prescribed → prescriptionPaymentPending → prescriptionPaid → medicationReady → completed`.
   Tổng tiền thuốc demo là `85.000 ₫`; đây là fixture cho Mobile, chưa phải
   bảng giá bệnh viện.
6. Chưa có Pharmacy/Dispensing backend contract. Màn hình Nhà thuốc hiện dùng
   `PrescriptionPaymentRepository` capability và chỉ hoạt động trong
   `DEMO_MODE`; production hiển thị ranh giới backend thay vì giả lập giao dịch.
7. `HEALTH_INSURANCE` không phải phương thức thanh toán. Thông tin BHYT chỉ
   còn ở hồ sơ hành chính cho tới khi có nghiệp vụ quyết toán riêng.

## Việc thay thế khi backend sẵn sàng

- Appointment Service/Directory trả catalog dịch vụ và giá theo cơ sở, thay
  fixture `AppointmentServiceOption.catalog`.
- Payment API trả receipt/idempotency/status; bỏ local
  `AppointmentPaymentStore` nhưng giữ model hiển thị.
- Pharmacy/Dispensing Service phát hành giá thuốc, trạng thái thu tiền và
  xác nhận phát thuốc; thay `DemoJourneyRepository` bằng adapter thật.
