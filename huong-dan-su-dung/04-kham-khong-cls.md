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
10. Kiểm tra Consultation hoàn tất, hàng đợi/lịch liên quan được cập nhật. Nếu Appointment tạm chưa đồng bộ, tải lại; xem lỗi kết nối và thử lại thao tác hợp lệ thay vì sửa DB.
11. Bệnh nhân mở kết quả/lịch khám: cùng lượt khám, chẩn đoán và toa đã phát hành được phép xem. Nếu có thuốc cần cấp phát, sang quầy thuốc.

Appointment, phiếu, QueueEntry và Consultation là các tài nguyên khác nhau; không yêu cầu mọi bảng có cùng chuỗi trạng thái. Kiểm tra đúng tài nguyên tại từng chặng.

**Bằng chứng hiện có:** API thật đã chạy một ca demo sau bàn giao không CLS đến lập/xác nhận toa và hoàn tất Consultation/Queue/Appointment; bệnh nhân thấy lịch hoàn tất. Chưa coi đây là một buổi trình diễn mới đi qua mọi màn từ đăng ký đến phát thuốc. Khi nghiệm thu lại phải ghi từng chặng của cùng một bệnh nhân.

Nếu bác sĩ đang có ca khác, tiếp tục/hoàn tất ca đó theo nghiệp vụ trước. Không reset queue hoặc hoàn tất ca gốc chỉ để nút gọi người tiếp theo hoạt động.
