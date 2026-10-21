# 04 — Khám không cần cận lâm sàng

[Quay về mục lục](README.md) · [Trước: check-in](03-dat-lich-check-in.md) · [Toa và quầy thuốc](06-toa-va-cap-phat-thuoc.md)

Điều kiện: bệnh nhân CHECKED_IN đúng phòng, bác sĩ đúng phân công và không đang phục vụ một ca IN_PROGRESS khác. Cần thử từ đầu thì dùng lịch mới hợp lệ; ca đang khám được mở để tiếp tục.

1. Bác sĩ vào **Hàng đợi khám** tại `/dashboard/queue`.
2. Đối chiếu bệnh nhân/mã phiếu, bấm **Gọi số** hoặc nút gọi tương ứng. Lượt chuyển CALLED, mobile nhận thay đổi.
3. Khi bệnh nhân tới, bấm **Bắt đầu khám**. Hệ thống tạo/khôi phục đúng Consultation và lượt phục vụ IN_PROGRESS.
4. Kiểm tra tên bệnh nhân, phòng, trạng thái và bác sĩ phụ trách. Không mở ca người khác bằng cách thay URL.
5. Nhập sinh hiệu, triệu chứng, nhận định khám, chẩn đoán. Nếu cần thuốc, chọn thuốc từ danh mục API và điền cách dùng/số lượng.
6. Bấm **LƯU NHÁP ĐƠN** để lưu dữ liệu đang nhập; tải lại và kiểm tra các trường vừa lưu. Toa DRAFT chưa phải toa đã phát hành cho bệnh nhân.
7. Nếu cần bàn giao trước khi kết luận, dùng [luồng 07](07-ban-giao-ca-kham.md). Nếu cần CLS, chuyển sang [luồng 05](05-can-lam-sang.md).
8. Nếu có toa nháp của mình, sau khi lưu bấm **KÝ & XÁC NHẬN ĐƠN**, đối chiếu thuốc trong hộp thoại và xác nhận. Toa phải CONFIRMED trước khi hoàn tất; chỉ lưu nháp sẽ bị chặn.
9. Khi đã đủ điều kiện, bấm **HOÀN TẤT PHIÊN KHÁM**, đọc hộp xác nhận và xác nhận.
10. Kiểm tra Consultation hoàn tất, hàng đợi/lịch liên quan được cập nhật. Nếu giao diện báo ca đã đóng nhưng còn đồng bộ, bấm **Thử lại đồng bộ**; sau tải lại ca hoàn tất có **Đối soát hoàn tất**. Thao tác chỉ tiếp tục các bước còn thiếu, không tạo ca/toa/lịch lần hai. Không sửa DB để ép hoàn tất.
11. Bệnh nhân mở kết quả/lịch khám: cùng lượt khám, chẩn đoán và toa đã phát hành được phép xem. Nếu có thuốc cần cấp phát, sang quầy thuốc.

Appointment, phiếu, QueueEntry và Consultation là các tài nguyên khác nhau; không yêu cầu mọi bảng có cùng chuỗi trạng thái. Kiểm tra đúng tài nguyên tại từng chặng.

**Bằng chứng 17/10/2026:** CXK-001 đã đi qua giao diện đặt lịch mobile → check-in STAFF → bác sĩ gọi/khám/lưu và ký toa → hoàn tất → STAFF phát thuốc. API đối chiếu Appointment/Consultation/phiếu COMPLETED và toa DISPENSED. Mobile hiển thị chẩn đoán/toa của cùng ca. Đây là dữ liệu kiểm thử, không phải chỉ định cho người thật.

Nếu bác sĩ đang có ca khác, thông báo giữ trên trang Tổng quan/Hàng đợi kèm **Vào ca khám**. Mở lại ca đó để tiếp tục xử lý theo nghiệp vụ trước khi gọi bệnh nhân khác; hệ thống không tự bỏ qua quy tắc một ca đang khám. Không reset queue hoặc hoàn tất ca gốc chỉ để nút gọi người tiếp theo hoạt động.

Bộ demo chính ROOM-01 ở [trang 01](01-tai-khoan-va-du-lieu.md) đã kiểm tra thêm trên NOI-001: bác sĩ gọi/bắt đầu/lưu nháp/ký toa/hoàn tất, STAFF gọi quầy/đối chiếu/phát thuốc đều qua giao diện thật. Trạng thái lượt đang demo được cập nhật ở trang 01; NOI-003 trước đó đã hoàn tất và chỉ dùng xem lịch sử.

Kiểm tra bổ sung CXK-005: sau ký toa, hoàn tất chỉ lưu phần bệnh án và giữ toa đã ký. Nếu lưu thất bại, không đóng ca; sửa kết nối rồi thử lại. Đã dừng thật Consultation Service để kiểm tra: request lưu lỗi, không có POST complete, ca còn IN_PROGRESS; khởi động lại thì hoàn tất và phát thuốc thành công trên cùng ca.

**R1 — 20/10/2026:** hồ sơ/dị ứng lỗi có **Thử lại thông tin hồ sơ**; tóm tắt khám/toa/EMR lỗi có **Thử lại bệnh sử**. Chưa tải được không có nghĩa là không có bệnh sử. Tuổi, giới tính, giờ và mã EMR thiếu không được thay bằng giá trị mẫu. Kiểm tra lại sau kết nối; phạm vi thử nghiệm xem [trang17](17-tai-khoan-va-quan-tri-r1.md).
