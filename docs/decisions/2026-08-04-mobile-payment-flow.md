# Mobile MVP — Trả trước phí khám và quyết toán cuối lượt

> Trạng thái: Superseded in part by `2026-08-05-two-stage-self-pay-settlement.md`
> Ngày chốt ban đầu: 2026-08-04

## Quyết định còn hiệu lực

1. Mobile hiển thị đúng một dịch vụ khám mặc định:
   - code: `GENERAL_CONSULTATION`;
   - tên: `Khám thường`;
   - giá demo: `150.000 ₫`;
   - thời lượng tham khảo: 15 phút.
2. Người bệnh chọn `ONLINE_MOCK` hoặc `CASH_AT_HOSPITAL` cho phí khám.
   `ONLINE_MOCK` tạo khoản trả trước; tiền mặt là `DUE_AT_HOSPITAL`, không được
   trình bày là đã thu.
3. Appointment Service chưa nhận payment/service contract. Mobile lưu
   `AppointmentPaymentReceipt` qua local adapter; lỗi lưu local không rollback
   Appointment đã tạo thành công.
4. Phiên bản báo cáo chỉ xét người bệnh tự chi trả, không đưa BHYT vào phương
   thức, công thức hoặc trạng thái payment.

## Quyết định được thay thế từ ngày 05/08/2026

- Không thanh toán tiền thuốc bằng một journey riêng và không thanh toán riêng
  trước cận lâm sàng.
- Sau khi bác sĩ kết luận/kê toa, Mobile mở quyết toán cuối lượt, tổng hợp phí
  khám, cận lâm sàng, thuốc và dịch vụ phát sinh rồi khấu trừ khoản trả trước.
- Kết quả là `PAYMENT_DUE`, `SETTLED`, `REFUND_PENDING` hoặc `REFUNDED`; không
  biểu diễn số tiền phải trả âm.
- Trạng thái hoàn tiền trong MVP là mock; không tự ghi nhận đã hoàn tiền qua
  ngân hàng production.

## Ranh giới backend

- Appointment receipt và Visit Settlement là capability adapter/demo, không tạo
  Payment microservice production trong MVP.
- Laboratory Order không còn payment contract hoặc trạng thái
  `PAYMENT_PENDING`; order hợp lệ tạo queue thực hiện ngay.
- Queue phát thuốc được tạo từ toa `CONFIRMED`; chỉ hoàn tất dispense khi
  settlement không còn `PAYMENT_DUE`.
