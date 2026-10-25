# 06 — Kê toa → quầy thuốc

[Quay về mục lục](README.md) · [Tài khoản/phân công](01-tai-khoan-va-du-lieu.md) · [Tiếp: sau khám](08-sau-kham.md)

Điều kiện: đúng bác sĩ phụ trách, ca cho phép kê toa, danh mục thuốc thật; STAFF cấp phát được phân công đúng điểm phục vụ. **Đã thao tác tạo/lưu/ký/cấp phát qua Web trên ba ca CXK ngày 17/10/2026, đối chiếu toa DISPENSED bằng API. Thanh toán/quyết toán thật chưa được nghiệm thu.**

## Bác sĩ tạo và phát hành toa

1. Mở đúng ca **đang khám**, chọn **+ THÊM THUỐC**, tìm/chọn thuốc có trong API. Khung thêm thuốc mở bên phải.
2. Điền liều dùng, tần suất, thời điểm, số ngày, số lượng và lời dặn theo bộ dữ liệu demo. Bấm **XÁC NHẬN THÊM** ở đáy khung bên phải; thuốc mới được đưa vào danh sách kê toa. Nội dung dài cuộn ở giữa khung, nút **HỦY BỎ / XÁC NHẬN THÊM** luôn nằm dưới. Nếu mở sửa thuốc đã thêm, nút tương ứng là **LƯU CẬP NHẬT**.
3. Đóng khung thêm thuốc rồi bấm **LƯU NHÁP ĐƠN** trên thanh cố định phía dưới màn hình khám. Tải lại để xác nhận đã lưu trong DB. Thanh thao tác tự xuống dòng khi cửa sổ hẹp.
4. Kiểm tra tác giả, bệnh nhân, thuốc và trạng thái DRAFT. Không ký/sửa thay toa nháp của bác sĩ trước khi nhận bàn giao; lập toa dưới tên mình.
5. Sau khi đã lưu nháp, bấm **KÝ & XÁC NHẬN ĐƠN** trên cùng thanh phía dưới, kiểm tra danh sách thuốc trong hộp xác nhận rồi xác nhận. Toa phải CONFIRMED; chỉ lưu nháp không tạo điều kiện cấp phát. Sau đó mới **HOÀN TẤT PHIÊN KHÁM** khi đủ điều kiện.
6. Ghi mã toa. Sự kiện PrescriptionIssued tạo lượt PHARMACY_DISPENSING tại điểm cấu hình.

Ca COMPLETED/CANCELLED chỉ xem lại, không còn nút thêm/lưu/ký/hoàn tất. Khi toa đã CONFIRMED, nút ký và thêm thuốc bị khóa/ẩn để giữ toa đã phát hành. Đây là kiểm tra trạng thái, không phải nút bị mất do bố cục; dùng ca mới hợp lệ khi muốn trình diễn từ đầu.

## STAFF đối chiếu và cấp phát

1. Vào `/staff/pharmacy` bằng nhân viên đúng phân công, chọn đúng quầy.
2. Kiểm tra cùng bệnh nhân/mã toa xuất hiện trong hàng đợi. Bấm **Gọi số tiếp theo** theo FIFO.
3. Đối chiếu danh tính và nội dung toa, bấm **Bắt đầu đối chiếu** để lượt đạt IN_PROGRESS.
4. Kiểm tra đúng toa CONFIRMED, danh mục/số lượng và lượt quầy đang IN_PROGRESS. Runtime kiểm thử hiện chưa tích hợp điều kiện thu tiền/quyết toán thật.
5. Chỉ khi đủ điều kiện, bấm **Xác nhận đã phát thuốc**.
6. Đối chiếu toa DISPENSED, lượt cấp phát hoàn tất và mobile hiển thị đúng toa được phép xem.

## Ngoại lệ và chạy lại

Bệnh nhân không check-in thêm lần nữa tại quầy thuốc. Toa nháp không hiển thị như toa đã phát hành. Toa CONFIRMED không sửa trực tiếp; thay đổi phải qua quy trình hủy/thay thế giữ lịch sử và đúng quyền.

Các trạng thái PAYMENT_DUE/SETTLED trong thiết kế quyết toán chưa phải điều kiện đã thực thi/kiểm chứng của runtime Mac này. Không dùng chúng để mô tả chức năng đang chạy. Thực thi đầy đủ luồng thanh toán/quyết toán thật chưa được nghiệm thu. Không coi giá mẫu, ONLINE_MOCK hoặc biên lai cục bộ là đã thu/hoàn tiền thật.

Lặp lại bằng toa/lượt mới hợp lệ. Nếu toa đã DISPENSED thì đọc lịch sử thay vì bấm cấp phát lại. Khi không có lượt quầy thuốc, kiểm tra toa đã CONFIRMED, event và phân công quầy.

**R3 — 20/10/2026:** ký toa lưu chữ ký và sự kiện bền vững trước khi thử đặt tái khám. Toa **CONFIRMED** vẫn giữ nguyên nếu lịch hết chỗ hoặc Appointment lỗi. Giao diện hiển thị riêng trạng thái lịch; chỉ **SCHEDULED** cùng mã lịch là đã đặt. **Thử lại tạo lịch đã chọn** giữ nguyên kế hoạch và kiểm tra lại quyền bác sĩ hiện tại. Nếu cần đổi kế hoạch sau ký, kiểm tra các lịch của ca trong mục tạo tái khám riêng; không bấm tạo thêm khi chưa rõ kết quả. Luồng ký/phát thuốc không đồng nghĩa push đã tới điện thoại. Xem [trang20](20-ho-so-va-hanh-trinh-lam-sang-r3.md).
