# 06 — Kê toa → quầy thuốc

[Quay về mục lục](README.md) · [Tài khoản/phân công](01-tai-khoan-va-du-lieu.md) · [Tiếp: sau khám](08-sau-kham.md)

Điều kiện: đúng bác sĩ phụ trách, ca cho phép kê toa, danh mục thuốc thật; STAFF cấp phát được phân công đúng điểm phục vụ. **Đã kiểm tra tạo/xác nhận toa bằng API và tạo toa nháp qua Web sau bàn giao; toàn bộ cấp phát và quyết toán chưa được nghiệm thu xuyên suốt.**

## Bác sĩ tạo và phát hành toa

1. Mở đúng ca, chọn **THÊM THUỐC**, tìm/chọn thuốc có trong API.
2. Điền liều dùng, tần suất, thời điểm, số ngày, số lượng và lời dặn theo bộ dữ liệu demo. Lưu nháp, tải lại để xác nhận đã lưu trong DB.
3. Kiểm tra tác giả, bệnh nhân, thuốc và trạng thái DRAFT. Không ký/sửa thay toa nháp của bác sĩ trước khi nhận bàn giao; lập toa dưới tên mình.
4. Sau khi đã lưu nháp, bấm **KÝ & XÁC NHẬN ĐƠN**, kiểm tra danh sách thuốc trong hộp xác nhận rồi xác nhận. Toa phải CONFIRMED; chỉ lưu nháp không tạo điều kiện cấp phát. Sau đó mới **HOÀN TẤT PHIÊN KHÁM** khi đủ điều kiện.
5. Ghi mã toa. Sự kiện PrescriptionIssued tạo lượt PHARMACY_DISPENSING tại điểm cấu hình.

## STAFF đối chiếu và cấp phát

1. Vào `/staff/pharmacy` bằng nhân viên đúng phân công, chọn đúng quầy.
2. Kiểm tra cùng bệnh nhân/mã toa xuất hiện trong hàng đợi. Bấm **Gọi số tiếp theo** theo FIFO.
3. Đối chiếu danh tính và nội dung toa, bấm **Bắt đầu đối chiếu** để lượt đạt IN_PROGRESS.
4. Kiểm tra điều kiện phát thuốc và quyết toán thực tế của môi trường. Nếu chưa xác minh được điều kiện, dừng tại đây.
5. Chỉ khi đủ điều kiện, bấm **Xác nhận đã phát thuốc**.
6. Đối chiếu toa DISPENSED, lượt cấp phát hoàn tất và mobile hiển thị đúng toa được phép xem.

## Ngoại lệ và chạy lại

Bệnh nhân không check-in thêm lần nữa tại quầy thuốc. Toa nháp không hiển thị như toa đã phát hành. Toa CONFIRMED không sửa trực tiếp; thay đổi phải qua quy trình hủy/thay thế giữ lịch sử và đúng quyền.

Contract quyết toán quy định PAYMENT_DUE chặn phát thuốc; SETTLED/REFUND_PENDING/REFUNDED cho phép tiếp tục theo chính sách. Thực thi đầy đủ luồng thanh toán/quyết toán thật chưa được nghiệm thu tại runtime Mac. Không coi giá mẫu, ONLINE_MOCK hoặc biên lai cục bộ là đã thu/hoàn tiền thật.

Lặp lại bằng toa/lượt mới hợp lệ. Nếu toa đã DISPENSED thì đọc lịch sử thay vì bấm cấp phát lại. Khi không có lượt quầy thuốc, kiểm tra toa đã CONFIRMED, event và phân công quầy.
