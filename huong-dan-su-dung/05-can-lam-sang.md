# 05 — Cận lâm sàng → đọc kết quả

[Quay về mục lục](README.md) · [Tài khoản/phân công](01-tai-khoan-va-du-lieu.md) · [Tiếp: toa thuốc](06-toa-va-cap-phat-thuoc.md)

**Đã chạy xuyên suốt bằng giao diện ngày 17/10/2026:** CXK-004 và CXK-002 (có bàn giao) từ chỉ định CBC → LAB thực hiện/phát hành → gọi đọc kết quả trên ca cũ → xác nhận đã đọc → ký toa → hoàn tất/phát thuốc. Danh mục lấy từ API/DB thật. Hiện có CBC và GLUCOSE ở các điểm LAB tương ứng; chẩn đoán hình ảnh không thuộc phạm vi đã hỗ trợ/kiểm tra này.

## Chuẩn bị

Ca khám đang diễn ra; danh mục dịch vụ và điểm CLS hợp lệ; kỹ thuật viên được phân công đúng điểm. Bác sĩ và LAB dùng hồ sơ trình duyệt riêng. Dùng bộ dữ liệu xét nghiệm demo, không dùng số liệu này làm kết quả lâm sàng cho người thật.

## Thao tác

1. Bác sĩ mở ca, chọn tab **Chỉ định Cận lâm sàng**, tìm và chọn hạng mục trong bộ chọn từ API `/api/labs/services`, gửi chỉ định. Chọn xong hiện mục đã chọn; click ra ngoài đóng danh sách. Mã/điểm không có trong danh mục bị server từ chối 422.
2. Ghi mã order, đối chiếu đúng consultation/patient và tác giả. Chuyển ca sang chờ kết quả. Chặng hàng đợi khám ban đầu hoàn tất, ca AWAITING_CLS vẫn mở để bác sĩ phục vụ lượt khác.
3. LAB vào `/lab/queue`, chọn đúng điểm phục vụ. Phải thấy lượt LAB_EXECUTION từ sự kiện của chỉ định; nếu chưa có, kiểm tra event/consumer trước khi thao tác tiếp.
4. LAB bấm **Gọi số**, đối chiếu bệnh nhân, rồi **Bắt đầu**. Lượt Queue và order phải đạt trạng thái cho phép thực hiện.
5. Nhập giá trị, khoảng tham chiếu và đơn vị cho các hạng mục. Khi đủ dữ liệu, bấm **Phát hành kết quả**.
6. Đối chiếu kết quả đã phát hành và lượt CLS đã hoàn tất. Bệnh nhân chỉ thấy kết quả được phép công bố.
7. Khi đủ mọi kết quả bắt buộc, bác sĩ xem làn **đọc kết quả / RESULT_REVIEW** tại phòng khám ban đầu.
8. Gọi bệnh nhân từ làn đó, mở lại **ca cũ** theo consultationId của lượt đọc kết quả. Không tạo ca mới từ appointmentId rỗng. Resume chỉ thành công khi mọi order đã có kết quả/đã đọc/đã hủy; còn chờ trả 409. Đọc kết quả, kết luận và kê toa nếu cần.
9. Bác sĩ đọc nội dung rồi chủ động bấm **Tôi đã đọc kết quả chỉ định này** trên từng order. Bấm hoàn tất không tự đánh dấu đã đọc. Server chặn hoàn tất nếu còn order chưa được đọc (409). Lượt RESULT_REVIEW phải hoàn tất cùng ca; không đóng nhầm lượt khám ban đầu. Hoàn tất khám theo [luồng 04](04-kham-khong-cls.md); bệnh nhân kiểm tra kết quả đúng lượt trên mobile.

## Kết quả cần thấy

Không đặt lịch/check-in lần hai tại xét nghiệm hoặc để đọc kết quả. Các làn PRIORITY/NORMAL/RESULT_REVIEW được điều phối theo Round Robin, FIFO trong từng làn; không giả định đọc kết quả luôn đứng đầu mọi hàng đợi.

Bác sĩ nhận bàn giao đọc được order cũ theo trách nhiệm hiện tại; tác giả chỉ định không đổi. Mobile dùng API xét nghiệm của chính bệnh nhân và lọc đúng ca; endpoint theo consultation dành cho bác sĩ không phải API cho PATIENT.

Nếu một bước thất bại, dừng ở bước đó và ghi order/phiếu, trạng thái, HTTP theo [trang 09](09-ngoai-le-va-xu-ly-loi.md). Không gán kết quả giả hoặc cập nhật SQL để ép sinh lượt đọc kết quả.

**R3 — 20/10/2026:** chỉ định mới lưu phiếu hàng đợi gốc của ca, kể cả không hẹn. Nếu kết quả đã phát hành mà chưa thấy lượt đọc, bác sĩ phụ trách bấm **Đồng bộ lượt CLS**, sau đó tải lại hàng đợi. Với dữ liệu cũ thiếu nguồn, server chỉ đối chiếu đúng phiếu của lịch gốc; không chọn lượt gần nhất theo bệnh nhân. Thiếu liên kết cần cơ sở y tế kiểm tra, không tạo lịch giả hoặc sửa trạng thái SQL. Bản ghi sự kiện được giữ để thử giao lại khi broker lỗi. Các kết quả R3 và phần chưa nghiệm thu ở [trang20](20-ho-so-va-hanh-trinh-lam-sang-r3.md).
