# Quyết định: đặt lịch khám chỉ thanh toán trực tuyến

> Trạng thái: Accepted
> Ngày chốt: 2026-08-08

## Quy tắc

Patient Mobile chỉ hoàn tất bước đặt lịch sau khi ghi nhận thanh toán trực
tuyến `ONLINE_MOCK` cho fixture `GENERAL_CONSULTATION` trị giá `150.000 ₫`.
Receipt vẫn được lưu local vì Appointment Service chưa có Payment contract.

`CASH_AT_HOSPITAL` không còn là lựa chọn ở màn đặt lịch. Người không thanh toán
trước sẽ thực hiện thủ tục trực tiếp tại bệnh viện và nhận số theo quy trình
tiếp nhận, không đi qua flow đặt lịch online này.

Nếu sau khám phát sinh chi phí cận lâm sàng hoặc thuốc, khoản phải thu thuộc
`VisitSettlement` cuối lượt; đây là nghiệp vụ khác với khoản trả trước khi đặt
lịch và không làm sống lại lựa chọn tiền mặt trong booking.

## Lý do

- Tránh tạo appointment online nhưng chưa có cam kết thanh toán.
- Không tạo hai cách xếp hàng cho cùng một nhu cầu: đặt lịch trước và đến quầy
  lấy số trực tiếp.
- Giữ rõ ranh giới giữa payment receipt local của Mobile và settlement cuối
  lượt.
