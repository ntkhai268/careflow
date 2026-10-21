# 14 — Tệp hồ sơ, thiết bị sinh trắc học và bảng giá

[Quay về mục lục](README.md) · [Tài khoản test](01-tai-khoan-va-du-lieu.md)

## Phạm vi phiên bản

Áp dụng app iPhone **1.0.0 (11)** và backend/web test từ nhánh `feature/dangkhoii/security-integration`, ngày 19/10/2026. Mobile source ở `feature/dangkhoii/patient-mobile-ios-login`. Bản app đã cài/mở trên iPhone; API thật, widget test và web quản trị đã kiểm tra. Mở trình xem/chia sẻ của iOS và thao tác trực tiếp trên màn thiết bị vẫn cần người dùng xác nhận. Chưa phát hành production; checkout khác không tự có các thay đổi chưa commit này.

Tài khoản/mật khẩu demo chỉ lấy tại [trang 01](01-tai-khoan-va-du-lieu.md). Dùng dữ liệu và tệp test, không dùng bệnh án người thật. AI và thanh toán điện tử/eKYC chưa nằm trong đợt này.

## Mở hoặc lưu tài liệu trên iPhone

1. Đăng nhập, chọn đúng hồ sơ bệnh nhân rồi mở bản ghi sức khỏe có tệp đính kèm.
2. Chọn **Mở** tại tệp PDF/ảnh. App hiển thị tiến trình tải và mở trình xem của điện thoại sau khi tải đủ dữ liệu.
3. Chọn **Chia sẻ/Lưu tệp** nếu muốn dùng bảng chia sẻ của iOS hoặc lưu ra ứng dụng Tệp. Chỉ chọn nơi nhận mà mình chủ động muốn chia sẻ.
4. Trong lúc tải, chọn biểu tượng **Hủy tải** để dừng. Sau lỗi mạng có thể chọn Mở/Chia sẻ để thử lại; không coi thông báo lỗi là đã lưu thành công.

Hỗ trợ JPEG, PNG và PDF tối đa 10MB/tệp. File Word cũ chưa được mở bằng luồng này; cần bản PDF/ảnh phù hợp. Tệp được tải bằng phiên có quyền, không dùng đường dẫn công khai. Tệp tạm trong app được dọn khi đăng xuất/đổi tài khoản hoặc lần khởi động app mới. Bản sao đã được người dùng chủ động lưu/chia sẻ ra ngoài app do người dùng quản lý.

Kết quả mong đợi: mở đúng nội dung/tên tệp; tài khoản khác bị từ chối; tệp tải thiếu hoặc sai định dạng không được mở như đã thành công. Nếu điện thoại chưa có trình mở tương ứng, dùng Chia sẻ/Lưu tệp. Việc nhận 403 cần kiểm tra quyền hồ sơ, không đổi ID trong URL.

## Xem và thu hồi thiết bị

1. Vào **Tài khoản → Thiết bị sinh trắc học**.
2. Xem ngày đăng ký, lần dùng cuối và ngày hết hạn. **Điện thoại này** chỉ hiện khi khớp đăng ký trên máy; thiết bị khác được phân biệt bằng mã rút gọn và thời điểm, không giả định nhận diện được model.
3. Chọn **Thu hồi** ở đúng thiết bị và đọc hộp xác nhận. **Giữ lại** sẽ hủy thao tác.
4. Thu hồi thiết bị khác: danh sách tải lại; thiết bị bị thu hồi mất các phiên gắn với nó và quyền đăng nhập Face ID/vân tay. Thiết bị hiện tại tiếp tục hoạt động nếu không thuộc đăng ký bị thu hồi.
5. Thu hồi **Điện thoại này**: app đăng xuất và xóa đăng ký local. Đăng nhập bằng mật khẩu, sau đó bật lại Face ID/vân tay nếu muốn tiếp tục sử dụng.
6. Kéo xuống để làm mới. Nếu đăng ký local đã hết hạn hoặc bị thu hồi từ nơi khác, danh sách thành công sẽ dọn thiết lập local cũ; không tự xóa thiết lập chỉ vì mất mạng.

Đây là danh sách **đăng ký sinh trắc học**, chưa phải danh sách mọi phiên đăng nhập bằng mật khẩu/web. Nếu chưa bật Face ID/vân tay thì danh sách trống là đúng. Không thu hồi thiết bị đang dùng chỉ để xem giao diện nếu chưa nhớ mật khẩu.

## Quản trị dịch vụ và bảng giá

1. Trên web `http://localhost:5173`, đăng nhập ADMIN. Vào **Cấu hình Cơ sở Y tế → Dịch vụ & bảng giá**, hoặc mở `/admin/services`.
2. Chọn **Thêm dịch vụ**, nhập mã, tên, giá VND, thời lượng dự kiến và mô tả. Mã dùng chữ hoa/số/gạch dưới, ví dụ `GENERAL_CONSULTATION`; mã không đổi khi cập nhật dịch vụ cũ.
3. Để trống **Hiệu lực từ** nếu áp dụng ngay; hoặc chọn thời điểm tương lai. Chọn trạng thái **Hoạt động / nhận lịch mới**.
4. Chọn **Công bố bảng giá**. Mỗi lần lưu tạo phiên bản mới; giá của lịch đã đặt giữ nguyên. Nếu có người khác vừa sửa, tải lại và mở lại bản mới nhất trước khi tiếp tục.
5. Muốn ngừng nhận lịch, mở **Cập nhật**, bỏ chọn Hoạt động và công bố. Không xóa lịch sử để ngừng dịch vụ.

Chỉ ADMIN quản lý bảng giá; PATIENT/STAFF/DOCTOR không được công bố. Màn danh sách quản trị hiển thị phiên bản công bố gần nhất; phiên bản tương lai được ghi rõ chưa tới thời điểm hiệu lực. Bản mới hơn thay thế lịch công bố trước của cùng dịch vụ khi nó có hiệu lực. Trên cửa sổ hẹp, dùng nút **Thu gọn** thanh bên và cuộn ngang bảng khi cần.

Môi trường test hiện có dịch vụ Khám thường với mức giá mẫu 150.000đ trong DB. Đây là dữ liệu test do server trả về, không phải mức phí chính thức của bệnh viện.

## Xác nhận giá khi đặt lịch trên mobile

1. Chọn hồ sơ, khoa, ngày và ca khám như [trang 03](03-dat-lich-check-in.md).
2. Ở bước xác nhận, chờ **Chi phí khám dự kiến** tải từ server. Khi có nhiều dịch vụ, chọn từ danh sách và gõ tìm để lọc; không nhập dịch vụ tự do.
3. Kiểm tra dịch vụ/giá rồi chọn **Xác nhận đặt khám**. Nếu chưa tải được bảng giá, dùng **Tải lại bảng giá**; nút xác nhận bị tắt tới khi có dữ liệu hợp lệ.
4. Nếu giá đã thay đổi, app báo lỗi và tải lại bảng giá; đọc giá mới rồi xác nhận lại. Dịch vụ ngừng hoạt động không được đặt mới.
5. Mở **Lịch khám → Chi tiết** để xem báo giá đã lưu khi đặt. Lịch lịch sử chưa có báo giá sẽ không được tự điền 0đ hoặc giá hiện tại.

Đặt lịch và có báo giá **chưa phải đã thanh toán**. Thực hiện thanh toán theo hướng dẫn tại bệnh viện; ứng dụng chưa tạo giao dịch điện tử trong đợt này.

## Nghiệm thu và cách lặp lại

| Phần | Đã kiểm tra | Còn chờ trên iPhone |
|---|---|---|
| Tệp | API upload/download thật khớp bytes, khác tài khoản 403; kiểm tra dữ liệu thiếu/sai, hủy phiên, header token/DPoP | Mở PDF/ảnh bằng trình xem iOS, bảng Chia sẻ/Lưu và trải nghiệm hủy/tải lại |
| Thiết bị | API thu hồi và quyền sở hữu; widget hủy xác nhận, thu hồi máy khác/máy hiện tại, dọn đăng ký cũ | Thao tác trên màn danh sách và đăng nhập lại sau thu hồi có chủ đích |
| Bảng giá | PostgreSQL, version/effective time, giá lịch sử, dữ liệu client giả; web ADMIN công bố/reload | Kiểm tra hiển thị, chọn dịch vụ và lịch mới trên điện thoại |

Lặp lại bằng hồ sơ test và lịch hợp lệ mới; không sửa trạng thái SQL hoặc mở lại ca đã hoàn tất. Ghi phiên bản app, thời điểm và kết quả; không đưa token, OTP, khóa hay mật khẩu vào ảnh/log nghiệm thu.

## Cập nhật upload R3 — bản 1.0.0 (18)

Bản18 đã build có ký iOS, **chưa xác nhận cài lên iPhone** do thiết bị chưa sẵn sàng kết nối tại mốc nghiệm thu này. Hướng dẫn phần cũ (11) là lịch sử; các bước mở/chia sẻ cần kiểm lại trên bản18.

1. Chọn đúng hồ sơ thuộc tài khoản → **Hồ sơ sức khỏe → Tải lên**. Tên người sở hữu phải tải từ server; chưa xác minh được thì thử lại trước khi gửi.
2. Nhập tiêu đề, cơ sở, ngày thực tế. Chọn tối đa5tệp PDF/JPEG/PNG, tối đa10MB/tệp và50MB/lần. File rỗng/sai định dạng bị chặn.
3. Bấm **Hoàn tất**, theo dõi tiến trình; chọn hủy nếu cần. Hủy/mất mạng không báo thành công. Nếu đã gửi rồi mà không nhận phản hồi, tải lại danh sách trước khi gửi lại để tránh bản ghi lặp.
4. Khi server xác nhận thành công, tải lại danh sách hoặc mở lại app, mở bản ghi và dùng **Mở / Chia sẻ/Lưu tệp**. Bác sĩ phụ trách xem ở **Tóm tắt lâm sàng → Hồ sơ bệnh nhân tải lên**.
5. Đăng xuất/đổi tài khoản giữa tác vụ: bản ghi và phản hồi của tài khoản trước không được hiện; danh sách phải tải lại theo quyền mới. Bản sao chủ động chia sẻ ra ứng dụng khác vẫn do người dùng quản lý.

Upload, kiểm tra phiên/quyền và byte PDF đã được kiểm tra tự động/API/web; chọn ảnh/PDF bằng picker iOS, xem/chia sẻ và hủy khi mất mạng trên máy thật **còn chờ nghiệm thu**. Xem [trang20](20-ho-so-va-hanh-trinh-lam-sang-r3.md).
