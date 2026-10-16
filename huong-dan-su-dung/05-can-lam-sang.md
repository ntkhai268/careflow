# 05 — Cận lâm sàng → đọc kết quả

[Quay về mục lục](README.md) · [Tài khoản/phân công](01-tai-khoan-va-du-lieu.md) · [Tiếp: toa thuốc](06-toa-va-cap-phat-thuoc.md)

**Phạm vi:** có implementation/contract, đã kiểm tra tạo và đọc chỉ định thật trong ca bàn giao; chưa có bằng chứng nghiệm thu liền mạch toàn bộ nhánh xét nghiệm → đọc kết quả → phát thuốc trên bộ demo hiện tại. Các bước dưới đây là kịch bản thao tác và tiêu chí cần kiểm tra, chưa mặc định đánh dấu đạt.

## Chuẩn bị

Ca khám đang diễn ra; danh mục dịch vụ và điểm CLS hợp lệ; kỹ thuật viên được phân công đúng điểm. Bác sĩ và LAB dùng hồ sơ trình duyệt riêng. Dùng bộ dữ liệu xét nghiệm demo, không dùng số liệu này làm kết quả lâm sàng cho người thật.

## Thao tác

1. Bác sĩ mở ca, chọn tab **Chỉ định Cận lâm sàng**, chọn hạng mục/điểm phục vụ từ danh mục thật và gửi chỉ định.
2. Ghi mã order, đối chiếu đúng consultation/patient và tác giả. Chuyển ca sang chờ kết quả theo thao tác có trên màn hình.
3. LAB vào `/lab/queue`, chọn đúng điểm phục vụ. Phải thấy lượt LAB_EXECUTION từ sự kiện của chỉ định; nếu chưa có, kiểm tra event/consumer trước khi thao tác tiếp.
4. LAB bấm **Gọi số**, đối chiếu bệnh nhân, rồi **Bắt đầu**. Lượt Queue và order phải đạt trạng thái cho phép thực hiện.
5. Nhập giá trị, khoảng tham chiếu và đơn vị cho các hạng mục. Khi đủ dữ liệu, bấm **Phát hành kết quả**.
6. Đối chiếu kết quả đã phát hành và lượt CLS đã hoàn tất. Bệnh nhân chỉ thấy kết quả được phép công bố.
7. Khi đủ mọi kết quả bắt buộc, bác sĩ xem làn **đọc kết quả / RESULT_REVIEW** tại phòng khám ban đầu.
8. Gọi bệnh nhân từ làn đó, mở lại **ca cũ**, đọc kết quả, kết luận và kê toa nếu cần.
9. Hoàn tất khám theo [luồng 04](04-kham-khong-cls.md); bệnh nhân kiểm tra kết quả đúng lượt trên mobile.

## Kết quả cần thấy

Không đặt lịch/check-in lần hai tại xét nghiệm hoặc để đọc kết quả. Các làn PRIORITY/NORMAL/RESULT_REVIEW được điều phối theo Round Robin, FIFO trong từng làn; không giả định đọc kết quả luôn đứng đầu mọi hàng đợi.

Bác sĩ nhận bàn giao đọc được order cũ theo trách nhiệm hiện tại; tác giả chỉ định không đổi. Mobile dùng API xét nghiệm của chính bệnh nhân và lọc đúng ca; endpoint theo consultation dành cho bác sĩ không phải API cho PATIENT.

Nếu một bước thất bại, dừng ở bước đó và ghi order/phiếu, trạng thái, HTTP theo [trang 09](09-ngoai-le-va-xu-ly-loi.md). Không gán kết quả giả hoặc cập nhật SQL để ép sinh lượt đọc kết quả.
