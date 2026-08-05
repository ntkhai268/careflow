# Quyết định thanh toán tự chi trả theo hai giai đoạn

> Trạng thái: Accepted for CareFlow report/MVP design
> Ngày chốt: 2026-08-05

## Phạm vi giả định

Phiên bản báo cáo chỉ xét người bệnh **không sử dụng BHYT**. Toàn bộ chi phí do
người bệnh tự chi trả. Không tính mức hưởng, đồng chi trả, bảo lãnh hay quyết
toán BHYT trong Use Case, trạng thái, công thức và test case của CareFlow MVP.

## Quyết định

CareFlow mô hình hóa hai giai đoạn thanh toán:

1. **Trả trước phí khám khi đặt lịch:** phí `GENERAL_CONSULTATION` mặc định
   `150.000 ₫`. `ONLINE_MOCK` tạo khoản đã trả trước; `CASH_AT_HOSPITAL` tạo
   khoản còn phải nộp tại bệnh viện. Khoản này là phí khám, không phải toàn bộ
   chi phí của lượt khám.
2. **Quyết toán cuối lượt:** sau khi bác sĩ kết luận và xác nhận toa, hệ thống
   tổng hợp phí khám, cận lâm sàng, thuốc và dịch vụ phát sinh; sau đó khấu trừ
   khoản đã trả trước. Không có bước thanh toán riêng trước cận lâm sàng.

Hai giai đoạn là hai thời điểm nghiệp vụ, không bắt buộc là hai địa điểm vật lý;
cả hai có thể được thao tác trên Mobile hoặc tại quầy.

## Công thức quyết toán

```text
patientPayable = totalVisitCost
amountDue      = max(0, patientPayable - prepaidAmount)
refundDue      = max(0, prepaidAmount - patientPayable)
```

Không lưu số phải trả âm. Kết quả quyết toán dùng các trạng thái:

```text
PAYMENT_DUE → SETTLED
SETTLED
REFUND_PENDING → REFUNDED
```

- `PAYMENT_DUE`: người bệnh còn phải thanh toán thêm.
- `SETTLED`: đã đủ tiền hoặc số còn phải trả bằng 0.
- `REFUND_PENDING`: khoản trả trước lớn hơn chi phí thực tế; bệnh viện phải hoàn
  phần dư. Trạng thái này không chặn nhận thuốc vì người bệnh không còn nợ.
- `REFUNDED`: khoản dư đã được hoàn về phương thức ban đầu hoặc tại quầy.

Số âm chỉ thường phát sinh khi hủy/giảm dịch vụ, điều chỉnh chi phí hoặc thu dư.
Trong lượt khám bình thường, phí khám trả trước là một dòng của tổng chi phí nên
không bị thu lại khi quyết toán.

## Ảnh hưởng đến hành trình và queue

- Laboratory Order tạo `LAB_EXECUTION` ngay sau khi order hợp lệ; bỏ
  `paymentRequired`, `PaymentEligibility`, `PAYMENT_PENDING` và các endpoint
  chọn/xác nhận payment riêng của Lab.
- Prescription `CONFIRMED` vẫn tạo `PHARMACY_DISPENSING` ngay để bệnh nhân có
  lượt chờ. Chỉ thao tác xác nhận phát thuốc bị chặn khi settlement còn
  `PAYMENT_DUE`; `SETTLED`, `REFUND_PENDING` và `REFUNDED` đều đủ điều kiện.
- `UC-PAY-01` chỉ hỗ trợ `UC-APT-02` ở bước trả trước và `UC-PHA-01` ở bước
  quyết toán cuối lượt; không còn `extend` `UC-LAB-01`.

## Ranh giới MVP

Payment trên Mobile vẫn dùng adapter/mock có trạng thái rõ ràng. Cổng thanh toán
production, kế toán viện phí và hoàn tiền ngân hàng thật nằm ngoài phạm vi; báo
cáo chỉ mô phỏng `REFUND_PENDING/REFUNDED` và không tuyên bố đã chuyển tiền thật.
