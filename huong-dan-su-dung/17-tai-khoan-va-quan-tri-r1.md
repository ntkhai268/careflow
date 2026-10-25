# 17 — Tài khoản và quản trị R1

[Quay về mục lục](README.md) · [Passkey và tài khoản mobile](16-bao-mat-dot-2.md) · [Định danh local](15-doc-giay-to-va-doi-chieu.md)

Mốc 20/10/2026: backend/web từ worktree `security-integration`, nền `5a8c6e5` kèm thay đổi chưa commit. Native vẫn là bản16 từ `patient-mobile-ios-login`; đợt này không thay mã native. Phạm vi và bằng chứng xem [biên bản R1](../.planning/identity-service/r1-verification-2026-10-20.md). Môi trường này là máy chủ test trên Mac, không phải production hoặc DB remote. Chưa đóng nghiệm thu Face ID/camera/giấy tờ thật trên iPhone.

## Tạo tài khoản nội bộ và tiếp tục phần còn dở

Điều kiện: ADMIN đã có passkey hợp lệ; khoa/phòng đang hoạt động có sẵn trong danh mục. Tài khoản demo chỉ xem tại [trang01](01-tai-khoan-va-du-lieu.md). Phiên mật khẩu demo không đủ quyền tạo/khóa/đổi vai trò/cấp passkey.

1. Đăng nhập bằng passkey tại `/login/methods`. Trước thao tác quản trị nhạy cảm, lần xác thực passkey phải còn trong5 phút; quá hạn thì đăng nhập lại.
2. Vào **Quản lý Tài khoản**, `/admin/accounts` ([web test trên Mac](http://localhost:5173/admin/accounts)). Danh sách lấy từ Identity, gồm email, trạng thái, số passkey và phân công. Tìm theo tên tài khoản, họ tên hoặc email; dùng phân trang khi có hơn25 kết quả.
3. Chọn **Thêm tài khoản**, nhập họ tên, tên tài khoản, email và chọn vai trò có sẵn. Tên tài khoản gồm3–50 ký tự chữ Latin/số/`._-`. Không có mật khẩu tạm. Không dùng form này để đăng ký bệnh nhân.
4. Chọn **Tạo tài khoản**. Chỉ khi Identity xác nhận lưu mới hiện thành công. Nếu chưa nhận được phản hồi, giữ form và thử lại cùng thông tin; hệ thống chống tạo trùng. Nếu sửa thông tin sau một yêu cầu đã được lưu, tìm tài khoản trước hoặc đóng rồi bắt đầu yêu cầu mới.
5. Bác sĩ/nhân viên được mở tiếp hộp phân công. Chọn khoa và phòng từ danh sách tìm kiếm API; không nhập UUID hay phòng ngoài danh mục. Bác sĩ chọn phòng khám; STAFF chọn phòng tiếp nhận/pharmacy; LAB_TECHNICIAN chọn LAB/IMAGING. Chứng chỉ bác sĩ nhập khi có thông tin phù hợp.
6. Chọn **Lưu phân công**. Nếu Directory lỗi, tài khoản Identity vẫn tồn tại. Chọn **Để sau**, tải lại danh mục và bấm **Phân công** trên đúng tài khoản để tiếp tục. Hồ sơ bác sĩ đã có được cập nhật theo profile ID; liên kết người dùng luôn dùng Identity user ID.
7. Tải lại trang: tài khoản, trạng thái và phân công vẫn còn. **Chưa có passkey** và **Cần phân công** là hai điều kiện riêng; hoàn tất một bước không tự hoàn tất bước kia. Khi tải Directory lỗi, màn hình báo chưa xác định phân công, không kết luận nhân viên chưa được phân công.
8. Mở **Cấp / khôi phục passkey** tại `/admin/security`; tiếp tục quy trình hai kênh ở [trang16](16-bao-mat-dot-2.md#cấp-passkey-nhân-viên-lần-đầu). Nhân viên hoàn tất tại `/staff-enrollment`, sau đó đăng nhập bằng passkey và kiểm tra đúng vai trò/phòng. Không gửi mã kích hoạt cùng kênh email chứa mã cấp quyền.

Nếu hiện403 cần passkey mới: giữ thông tin đã nhập riêng trên máy hoặc tìm lại tài khoản sau khi đăng nhập lại. Không đổi cấu hình để cho phép mật khẩu quản trị vượt bước xác thực. Tài khoản tạo từ form mới luôn yêu cầu passkey, kể cả môi trường demo còn cho tài khoản nội bộ cũ đăng nhập mật khẩu.

## Khóa, mở khóa và vô hiệu hóa

1. Tìm tài khoản theo email/tên, đối chiếu đúng người và vai trò.
2. Chọn **Khóa** hoặc **Vô hiệu hóa**, đọc hộp xác nhận rồi chọn **Xác nhận**. Không được tự khóa hoặc đổi quyền chính tài khoản đang quản trị; máy chủ giữ điều kiện có ADMIN hoạt động với passkey.
3. Tải lại trang và kiểm tra trạng thái lấy từ server. Phiên đã cấp cho tài khoản bị thay đổi trạng thái bị thu hồi; API đang có quyền phải từ chối yêu cầu tiếp theo. Giao diện trên tab cũ có thể còn nội dung đã tải, không đồng nghĩa còn quyền API.
4. Chọn **Mở khóa** khi muốn cho tài khoản hoạt động lại. Phiên/token cũ vẫn không dùng lại được; nhân viên đăng nhập passkey mới. Mở khóa không tự tạo phân công hoặc passkey bị thu hồi.
5. Thử người sai vai trò mở `/admin/accounts`: phải thấy403; API cũng phải từ chối. Không chỉ kiểm tra việc ẩn menu.

Chạy lại bằng một tài khoản tổng hợp khác hoặc tài khoản nội bộ thử riêng. Không khóa tài khoản đang sử dụng để trình diễn, không sửa DB hay cấp quyền giả để làm test đạt. Sau test, khóa/vô hiệu hóa tài khoản thử theo chính sách của môi trường.

## Bệnh sử, dữ liệu thiếu và mất kết nối

- Mở ca khám có quyền. Khi hồ sơ/dị ứng không tải được, có cảnh báo và **Thử lại thông tin hồ sơ**; chưa được coi bệnh nhân không có tiền sử.
- Ở **Tóm tắt lâm sàng**, một trong các nguồn khám/toa/EMR lỗi thì hiện cảnh báo và **Thử lại bệnh sử**. Kết nối lại và bấm thử; chỉ hiển thị kết quả mới sau phản hồi API hợp lệ.
- Tuổi thiếu/sai ngày sinh, giới tính chưa ghi nhận, thời điểm khám thiếu và mã EMR thiếu được thể hiện chưa biết; không thay bằng tuổi35, giờ mẫu hoặc mã EMR tự tạo. Giờ hợp lệ hiển thị theo múi giờ Việt Nam.
- Dashboard quản trị hiển thị trạng thái truy vấn Directory; không dùng nhãn này để khẳng định PostgreSQL hoặc mọi service đều khỏe. Truy vấn lỗi không tự biến thành danh sách rỗng/số0.

Bài kiểm tra lỗi UI dùng phản hồi503 chủ động trong trình duyệt; bước thử lại đã truy vấn service thật. Đây không phải diễn tập service toàn hệ thống mất điện. Lỗi LAB hoặc các nhánh ngoài thay đổi R1 phải đối chiếu riêng theo đợt nghiệp vụ tương ứng.

## Phần cần thao tác trên iPhone

Giữ bản16, kết nối máy chủ test cùng mạng như [trang11](11-cai-app-iphone.md). Thực hiện lần lượt quy trình đăng ký/reset/Face ID/khóa app ở [trang16](16-bao-mat-dot-2.md) và OCR/QR/đối chiếu/hủy ở [trang15](15-doc-giay-to-va-doi-chieu.md). Ghi bản app, thời gian, bước và kết quả; không chụp mã email, token, mật khẩu hoặc thông tin căn cước vào báo cáo.

Các test tự động đạt không thay cho cảm biến Face ID, hộp thoại iOS, camera chụp giấy tờ thật hoặc nhân viên trực tiếp đối chiếu. Không bật lại VNPT và không gửi ảnh ra provider. Passkey Chrome dùng authenticator ảo trong test R1, chưa thay cho nghiệm thu khóa vật lý của nhân viên.

## Khôi phục môi trường test cho operator

Trên Mac này bản sao state/artifact/DB trước V18 ở `~/Library/Developer/CareFlow/r1-20261020/`; baseline nguồn và app16 trước R1 ở thư mục private R0. Chỉ operator đang quản lý stack loopback mới khôi phục: dừng đúng tiến trình thuộc stack, dùng state/artifact và source snapshot tương ứng, giữ khóa outbox/document/caller hiện có, rồi kiểm tra health và đăng nhập theo vai trò. Không dùng `git reset --hard` vì worktree còn các thay đổi trước R1.

V18 là thay đổi cộng thêm; rollback application ưu tiên giữ hai bảng mới để bảo toàn audit. Chỉ restore DB khi đã dừng ghi, có quyết định chấp nhận mất dữ liệu phát sinh sau backup và đã thử restore vào DB tách riêng. Bản backup R1 đã tạo nhưng chưa diễn tập restore; không tự xóa bảng mới hoặc ghi đè DB đang dùng. Rollback web và backend phải cùng phiên bản; không giữ UI gọi endpoint R1 khi chạy backend cũ. Không lấy cấu hình demo/passkey localhost áp dụng thẳng vào production.
