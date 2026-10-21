# 19 — Lịch phòng, tiếp nhận và hàng đợi R2

[Quay về mục lục](README.md) · [Tài khoản demo](01-tai-khoan-va-du-lieu.md)

**Phạm vi:** nhánh tích hợp R2 ngày 20/10/2026, cụm test cục bộ và mobile **1.0.0 (17)** đã cài/mở trên iPhone 11 Pro Max. Kết quả API/web không thay thế nghiệm thu camera, GPS và thao tác người dùng trên iPhone. Không sử dụng AI hoặc VNPT.

## 1. Quản trị lịch phục vụ

1. Đăng nhập ADMIN, mở **Lịch phòng và hàng đợi** (`/admin/schedule`). Chọn khoa và phòng từ danh mục. Nếu chưa có phòng, tạo trong quản lý cơ sở, phân công bác sĩ rồi quay lại.
2. Chọn ngày hiệu lực từ hôm nay theo giờ Việt Nam; chọn các thứ làm việc, khoảng giờ, phút mỗi ca và số chỗ mỗi ca. Các khoảng giờ phải không chồng nhau và chia hết cho thời lượng ca.
3. Thêm ngày nghỉ hoặc ngoại lệ. Ngoại lệ mở cửa có thể dùng khung giờ riêng; nếu để trống sẽ dùng khung giờ thường.
4. Bấm **Lưu lịch phục vụ**, kiểm tra phiên bản mới; tải lại trang và xem lịch sử. Trên mobile, mở lại bước chọn ngày/ca để lấy slot mới từ máy chủ.
5. Nếu đổi lịch làm mất slot đã đặt hoặc giảm sức chứa xuống dưới số đã đặt, hệ thống từ chối. Chọn ngày hiệu lực khác hoặc giữ ca/sức chứa. Không có thao tác âm thầm chuyển/hủy lịch cũ.
6. Hai quản trị viên cùng lưu: người dùng phiên bản cũ sẽ bị yêu cầu tải lại. Đọc lại cấu hình trước khi chỉnh tiếp.

Một khoa có thể có nhiều phòng khám đang hoạt động. Mỗi phòng nhận lịch cần có bác sĩ hợp lệ; nếu phòng có nhiều bác sĩ, cần đúng một bác sĩ chính khi đặt lịch. Máy chủ chọn phòng còn chỗ theo lịch; lịch đã tạo giữ thông tin phòng/bác sĩ đã xác nhận.

## 2. Chính sách hàng đợi và QR

Trong cùng trang, lưu **Phút khám dự kiến**, **Ngưỡng báo gần lượt**, **Chính sách xếp lại lượt lỡ** và trạng thái tiếp nhận/gọi lượt mới của phòng. Thời gian dự kiến được điều chỉnh theo các ca đã hoàn tất. Khi tạm ngừng, lịch đã đặt vẫn giữ phiếu; chặn check-in/cấp số/gọi lượt mới, vẫn xem và hoàn tất được ca đang phục vụ. Thuật toán vẫn luân phiên ba luồng ưu tiên → thông thường → đọc kết quả, bỏ qua luồng rỗng; không có nút chỉnh tỷ lệ giả.

Phần **Khu vực check-in bệnh viện** lưu cơ sở, tọa độ, bán kính và sai số GPS tối đa. QR phải còn hạn, đúng cơ sở/ngày; điện thoại cần cấp quyền camera/vị trí và ở trong vùng được phép. Không có chính sách khoảng phút sớm/muộn chưa được máy chủ áp dụng.

## 3. Tiếp nhận người không có lịch hẹn

1. STAFF được phân công phòng mở **Tiếp nhận** (`/staff/checkin`), chọn phòng. ADMIN cũng có thể hỗ trợ tiếp nhận; vai trò không thay thế quyền khám bệnh của bác sĩ.
2. Trong **Tiếp nhận không hẹn trước**, tìm theo tên, điện thoại hoặc căn cước. Kết quả chỉ có thông tin hành chính tối thiểu, không mở toàn bộ bệnh án.
3. Chọn đúng hồ sơ từ kết quả. Nếu chưa có, chọn **Tạo hồ sơ mới không có tài khoản**, nhập thông tin đã kiểm tra; không cần nhập UUID và không gán tài khoản nhân viên làm chủ hồ sơ.
4. Chọn dịch vụ từ danh mục máy chủ, bấm **Cấp số khám không hẹn**. Nhận số tại đúng phòng và thấy lượt trong hàng đợi. Gửi lại cùng thao tác khi mất phản hồi sẽ không cấp thêm số.
5. Bác sĩ đúng phân công mở hàng đợi, gọi số rồi bắt đầu khám. Phiên khám gắn với phiếu hàng đợi; không tạo lịch hẹn giả. Hoàn tất khám và lượt phục vụ qua giao diện hiện có.
6. Khi có một lượt đã check-in/đang phục vụ/lỡ lượt trong cùng khoa và ngày, cần xử lý lượt đó trước; không cấp lượt hoạt động thứ hai cho cùng bệnh nhân.

## 4. Liên kết hồ sơ tiếp nhận với chính tài khoản bệnh nhân

1. Hoàn tất **Xác minh danh tính** theo luồng đọc giấy tờ tại máy và đối chiếu trực tiếp tại quầy. Nhân viên phải xác nhận giấy tờ gốc/người xuất trình/thông tin; tên hoặc căn cước tự khai không đủ để nhận bệnh án.
2. Trên iPhone, vào **Tài khoản → Liên kết hồ sơ tại viện → Tạo mã liên kết**. Mã dùng một lần, hết hạn sau 5 phút; chỉ sao chép và đưa cho nhân viên đang tiếp nhận.
3. Nhân viên chọn đúng hồ sơ chưa có tài khoản ở màn tiếp nhận, nhập mã, mã quầy/biên bản không chứa thông tin cá nhân và xác nhận đã đối chiếu hồ sơ gốc. Bấm gửi yêu cầu.
4. Điện thoại tải lại yêu cầu; kiểm tra họ tên/ngày sinh/phòng rồi bấm **Đây là hồ sơ của tôi** và xác nhận quyền xem dữ liệu khám bệnh. Chỉ sau bước này hồ sơ mới thuộc tài khoản. Yêu cầu chờ xác nhận hết hạn sau 10 phút.
5. Nếu sai thông tin, bấm **Từ chối**. Nếu có hồ sơ trùng trong tài khoản, trạng thái là **Hồ sơ trùng, chưa cấp quyền**; nhân viên kiểm tra rồi từ chối yêu cầu không đủ điều kiện. Không có chức năng tự gộp bệnh án hoặc tự xử lý xung đột lâm sàng.
6. Với liên kết đã chấp thuận, bệnh nhân hoặc nhân viên được phân công có thể **Thu hồi liên kết**. Quyền hồ sơ bị thu hồi nhưng dữ liệu khám vẫn được lưu; lịch/phiếu cũ không tự cấp lại quyền.

Chức năng này chỉ nhận hồ sơ của chính người đã được đối chiếu, chưa cấp quyền người đại diện. Không dùng giấy tờ thật trong bộ test tổng hợp; không gửi ảnh/giấy tờ sang VNPT hoặc dịch vụ eKYC bên ngoài.

## 5. Lỡ lượt và xếp lại

1. Bác sĩ gọi lượt, dùng **Gọi lại** cho tới đủ ba lần. Trước đủ số lần, máy chủ từ chối đánh dấu lỡ lượt.
2. Chọn **Đánh dấu lỡ lượt**. Lượt xuất hiện trong phần **Lượt đã gọi và lỡ lượt**, tách khỏi các luồng đang chờ.
3. Xếp lại theo chính sách phòng: đầu, cuối hoặc nhân viên/bác sĩ chọn đầu/cuối nếu cấu hình yêu cầu chọn thủ công. Lượt đọc kết quả luôn về cuối.
4. Tải lại hàng đợi và gọi tiếp. Gửi cạnh tranh không tạo thêm lượt; nếu người khác đã xử lý, tải lại trạng thái trước khi thao tác tiếp.

## 6. Các nhánh cần thử trên điện thoại

- Mở hai lịch của cùng bệnh nhân: màn hàng đợi phải hiển thị đúng phiếu của lịch đang mở.
- Chuyển app ra nền rồi quay lại: màn hàng đợi tải lại; vẫn có kéo xuống/nút cập nhật.
- QR hết hạn/sai cơ sở hoặc ngày, lịch hủy, ngoài vùng GPS, không cấp quyền vị trí: thông báo lỗi rõ và không báo đã check-in.
- Check-in lặp: giữ cùng một lượt. Bác sĩ sai phòng không được bắt đầu khám.
- Sau thu hồi liên kết, mở lại hồ sơ/lịch/phiếu/ca khám phải bị từ chối; tài khoản khác không thấy yêu cầu của bạn.

## Bản phát hành và nghiệm thu

Đã build backend/web, kiểm thử PostgreSQL riêng, API thật và luồng web tạo hồ sơ → cấp số → gọi/gọi lại → lỡ lượt → xếp lại → mở/hoàn tất ca không hẹn. Đã thử QR hết hạn, ngoài vùng GPS, check-in lặp, hủy lịch và thu hồi quyền qua API thật.

Bản native 1.0.0 (17) đã cài và mở trên thiết bị thật. Người dùng cần xác nhận riêng màn liên kết hồ sơ, quét camera/vị trí và trải nghiệm sau quay từ nền. Chưa có xác nhận đầy đủ các nhánh phần cứng này; không đánh dấu đạt dựa vào API hoặc unit. Báo cáo kỹ thuật và bằng chứng nằm trong `.planning/appointment-service/r2-acceptance-2026-10-20.md`.
