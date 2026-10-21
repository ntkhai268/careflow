# 20 — Hồ sơ và hành trình lâm sàng R3

[Quay về mục lục](README.md) · [Tài khoản demo duy nhất](01-tai-khoan-va-du-lieu.md) · [Biên bản kỹ thuật](../.planning/consultation-service/r3-acceptance-2026-10-20.md)

Mốc20/10/2026: backend/web trên `feature/dangkhoii/security-integration`, native trên `feature/dangkhoii/patient-mobile-ios-login`, có thay đổi chưa commit. API và web chạy thật trên stack local riêng. Bản R3 iOS1.0.0(18) đã build có ký; sau đó R4 cài và mở thành công bản1.0.0(19), chứa source R3/R4. R1/R2 phần cứng còn mở; push/background thuộc R4. Android vật lý và bản Android R3 chưa nghiệm thu.

## Hồ sơ tải lên → bác sĩ đọc

1. Bệnh nhân đăng nhập app, chọn hồ sơ mình sở hữu/được liên kết hợp lệ. Mở **Hồ sơ sức khỏe → Tải lên**; chờ đúng tên từ server. Nhập tiêu đề, cơ sở và ngày.
2. Chọn PDF/JPEG/PNG (tối đa5tệp,10MB/tệp,50MB/lần), bấm **Hoàn tất**. Theo dõi tiến trình/hủy. Chỉ server xác nhận mới hiện thành công; sau lỗi không rõ kết quả, tải lại danh sách trước khi gửi lại.
3. Bác sĩ phụ trách ca mở **Tóm tắt lâm sàng → Hồ sơ bệnh nhân tải lên**. Mở bản ghi, đọc thông tin và **Tải tệp để xem**. Tệp tải bằng phiên có quyền; mở PDF/ảnh bằng ứng dụng trên máy của bác sĩ. Không có đường dẫn công khai hoặc trình xem ngoài nhận token.
4. Lỗi tải có thông báo và tải lại; không kết luận “không có bệnh sử” khi API lỗi. Đăng xuất/đổi tài khoản phải dừng tác vụ và không hiện hồ sơ phiên trước. Xem giới hạn chia sẻ tại [trang14](14-tep-thiet-bi-va-bang-gia.md).

Đã đối chiếu byte PDF qua API và nút tải trên web; bác sĩ ngoài ca/STAFF bị chặn. Picker, trình xem/chia sẻ iOS và tải lại sau mở app trên bản19 còn cần người dùng thử trực tiếp.

## Khám không CLS và có CLS

- Không CLS: gọi/bắt đầu đúng phiếu, lưu chẩn đoán/sinh hiệu, lập toa nếu cần; **LƯU NHÁP ĐƠN → KÝ & XÁC NHẬN ĐƠN → HOÀN TẤT PHIÊN KHÁM**. Chẩn đoán thiếu/toa của mình còn nháp bị chặn. Chi tiết [trang04](04-kham-khong-cls.md).
- Có CLS: chọn chỉ định catalog thật → chuyển chờ CLS → kỹ thuật viên thực hiện/phát hành → gọi lượt **RESULT_REVIEW**, tiếp tục **cùng ca**. Bác sĩ đọc rồi bấm **Tôi đã đọc kết quả chỉ định này** trên từng order, sau đó kết luận/ký/hoàn tất. Không tự đánh dấu đã đọc khi bấm hoàn tất. Chi tiết [trang05](05-can-lam-sang.md).
- Nếu kết quả đã phát hành nhưng thiếu lượt đọc, bác sĩ phụ trách chọn **Đồng bộ lượt CLS** và tải lại hàng đợi. Lượt đọc phải liên kết phiếu gốc, không chọn lượt gần nhất của cùng bệnh nhân.
- Có thể bàn giao ca không hẹn/chờ CLS theo [trang07](07-ban-giao-ca-kham.md). Người nhận tự chấp nhận và dùng ca cũ; tác giả toa/chỉ định giữ nguyên. Người giao mất quyền theo trách nhiệm hiện tại. Toa nháp cũ chỉ đọc, bác sĩ nhận lập toa của mình.

## Tái khám và xử lý từng bước chưa xong

1. Trước ký toa, chọn **Ngày tái khám**, kiểm tra khoa theo phân công và chọn **Ca tái khám còn chỗ**. Bỏ ngày trống nếu không hẹn. Không có ca phù hợp thì chọn ngày khác.
2. Có thể tạo riêng không cần thuốc tại **Tóm tắt lâm sàng → Hẹn tái khám từ ca khám**. Kiểm tra danh sách lịch nguồn trước khi tạo. Server giữ liên kết ca nguồn và chống tạo trùng khi gửi lại cùng lệnh.
3. **Toa CONFIRMED** và **lịch đã tạo** là hai kết quả riêng. Đọc trạng thái lịch: **SCHEDULED** kèm mã lịch mới là xác nhận; PENDING/FAILED/NEEDS_SELECTION chưa xác nhận kế hoạch đó. Toa đã ký vẫn giữ nguyên khi bước đặt lịch lỗi.
4. Nếu còn PENDING/FAILED, kiểm tra lịch nguồn rồi bấm **Thử lại tạo lịch đã chọn**; nút giữ nguyên ngày/ca và kiểm tra lại quyền hiện tại. Kế hoạch đã ký không sửa trực tiếp. Nếu phải đổi ca, xem các lịch hiện có và tạo lịch riêng hợp lệ; tránh đặt hai lịch vì chưa đọc kết quả cũ.
5. Hoàn tất có thể báo ca đã đóng nhưng Queue/Appointment chưa hội tụ. Dùng **Thử lại đồng bộ**; sau tải lại ca hoàn tất có **Đối soát hoàn tất**. Chỉ báo hoàn tất toàn bộ khi từng bước được xác nhận. Không tạo ca mới hoặc sửa SQL để chữa trạng thái.
6. Bệnh nhân xem **Lịch khám**, đối chiếu đúng người/ngày/ca/bác sĩ. Tới lượt tái khám tạo **ca mới**, giữ liên kết ca trước. Trong **Kết quả khám và toa thuốc**, chọn **Theo lịch hẹn / Khám không hẹn** để xem kết quả đã công bố của đúng hồ sơ.

Ký toa lưu sự kiện bền vững để chuyển quầy thuốc khi kết nối hồi phục; không đồng nghĩa đã giao thông báo đẩy tới máy. Không cấp phát lại toa DISPENSED. Luồng quầy thuốc ở [trang06](06-toa-va-cap-phat-thuoc.md).

## Lặp lại và phần còn chờ

Dùng tài khoản ở trang01, hồ sơ/tệp **SYNTHETIC** không chứa người thật, ngày/slot hợp lệ và ca mới. Mỗi lần ghi bệnh nhân, phiếu, ca, order, toa, lịch nguồn và kết quả từng bước. Không reset toàn bộ hàng đợi hoặc sửa DB để ép đạt. Các ca đã hoàn tất chỉ dùng xem lịch sử.

R3 đã có kiểm thử API/web thật cho không CLS, CLS cùng ca, bàn giao/thu hồi quyền, tái khám và quầy thuốc; xem biên bản để biết phạm vi/cách phục hồi các lỗi phát hiện. Chưa gọi R3 nghiệm thu trọn vẹn trên điện thoại: bản19 đã cài/mở nhưng còn chọn PDF/ảnh thật qua picker, mở/chia sẻ, hủy khi mất mạng, đổi tài khoản và xem hai nhóm kết quả. Không dùng unit/widget/API để thay các bước thiết bị này. Multi-round CLS và nhiều order đồng thời cần nghiệm thu mở rộng; ca CBC được thử không chứng minh mọi tổ hợp xét nghiệm.

Cập nhật R4 (20/10/2026): cách nhận thông báo, mở đúng tài nguyên sau kiểm tra quyền, tải bù và phạm vi push thực tế xem [hướng dẫn 21](21-thong-bao-va-dong-bo-r4.md).

Mốc cập nhật21/10/2026: bản1.0.0(20) đã cài/mở, bổ sung xử lý thông báo khi khóa/chuyển nền; các thao tác nghiệm thu máy thật vẫn chưa tự được tính là đạt. Xem [biên bản R4 mới nhất](../.planning/notification-service/r4-acceptance-2026-10-21.md).
