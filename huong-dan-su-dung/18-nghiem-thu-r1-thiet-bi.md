# 18 — Nghiệm thu R1 trên iPhone và passkey thật

[Quay về mục lục](README.md) · [Quản trị R1](17-tai-khoan-va-quan-tri-r1.md) · [Định danh local](15-doc-giay-to-va-doi-chieu.md)

Mốc20/10/2026: iPhone11 Pro Max đã được nhận qua kết nối thiết bị, app CareFlow1.0.0(19) đã cài và mở trong R4. HTTPS bridge `/health`, danh mục khoa/phòng và health của Gateway/hai Identity/Directory trả200. Kết nối thành công chưa phải là nghiệm thu Face ID/camera. Các bước dưới cần người đang cầm thiết bị thao tác; báo kết quả theo mã, không gửi mật khẩu, OTP hay ảnh giấy tờ vào chat.

Dùng tài khoản thử riêng cho reset/thu hồi thiết bị vì các thao tác này kết thúc phiên và đăng ký cũ của chính tài khoản thử. Tài khoản demo chỉ xem tại [trang01](01-tai-khoan-va-du-lieu.md). Giữ thứ tự sau để thử Face ID trước khi reset thu hồi thiết lập.

## A — Face ID, đăng xuất và khóa app

| Mã | Thao tác trên iPhone | Kết quả cần thấy |
|---|---|---|
| A01 | Bật Face ID trong bảo mật bằng mật khẩu hiện tại; đăng xuất; nhấn icon Face ID cạnh mật khẩu; xác thực | Tài khoản được điền sẵn, Face ID thành công và vào đúng tài khoản |
| A02 | Đăng xuất; nhấn Face ID rồi hủy hộp thoại iOS | Vẫn ở đăng nhập; lần thử tiếp còn dùng Face ID được |
| A03 | Ở đăng nhập, sửa tài khoản sang tài khoản khác | Không dùng credential tài khoản trước để đăng nhập vào tài khoản mới; đăng nhập mật khẩu của tài khoản mới hoạt động |
| A04 | Khi đang đăng nhập, đưa app xuống nền hơn60 giây rồi trở lại | Phiên cục bộ khóa, nội dung không tự mở; đăng nhập/Face ID lại theo thiết lập |
| A05 | Với tài khoản thử, thu hồi chính thiết bị ở danh sách thiết bị rồi thử lại đăng nhập Face ID | Credential cũ bị chặn; đăng nhập mật khẩu và bật lại mới có thiết lập dùng được |

Ghi rõ lỗi nếu không có biểu tượng, hộp thoại bị hủy, sai mật khẩu khi bật, lỗi mạng hoặc máy chủ báo credential thu hồi. Đây là các trường hợp khác nhau. Không thử sai mật khẩu liên tiếp để dò; xem cách xử lý ở [trang12](12-dang-nhap-mobile.md).

## B — Email đăng ký và quên mật khẩu

1. **B01 — Đăng ký:** dùng email thử bạn nhận được thư và tên tài khoản chưa dùng. Gửi mã, kiểm tra app còn ở bước xác minh, nhập mã tại app rồi hoàn tất tạo PATIENT. Đóng app/tải lại không được có tài khoản hoàn tất chỉ từ việc gửi mã.
2. **B02 — Reset ba bước:** đăng xuất tài khoản thử, Quên mật khẩu → gửi mã email → xác minh mã → lúc này mới thấy hai ô mật khẩu mới. Nhập hai lần mật khẩu mới và hoàn tất; đăng nhập mới phải thành công.
3. **B03 — Thu hồi:** phiên và Face ID/passkey cũ của tài khoản vừa reset không dùng lại được. Sau đăng nhập mật khẩu mới, bật lại Face ID để tiếp tục sử dụng. Không dùng tài khoản nhân viên đang phục vụ để thử reset.
4. **B04 — Nội dung thư:** kiểm tra người gửi CareFlow, template đọc được trên iPhone/Gmail và không có logo nằm trong mục tệp đính kèm. Chỉ báo kết quả, không gửi ảnh có OTP.

Mã sai/hết hạn/dùng lại, grant sai/đã dùng, hạn mức và thu hồi phiên đã có kiểm thử tự động; không phải gửi hàng chục email thật để lặp các test đó. Một lần nhận thư và luồng UI thành công vẫn cần người dùng xác nhận. Chi tiết cooldown/expiry theo [trang16](16-bao-mat-dot-2.md).

## C — Camera/OCR với dữ liệu giả

Mẫu tại [thẻ OCR tổng hợp](../.planning/identity-service/evidence/r1-device-20261020/ocr-test-card.svg). Mẫu chỉ là bảng chữ số cho kiểm thử, không phải ảnh hoặc bản mô phỏng giấy tờ do cơ quan nhà nước cấp.

1. **C01:** mở mẫu trên màn hình Mac, iPhone vào **Tài khoản → Xác minh danh tính → Đọc căn cước trên máy**. Chụp mẫu và dùng ảnh. Kiểm tra ba trường: số `000000000001`, họ tên `NGUYEN VAN KIEM THU`, ngày sinh `01/01/1990`. OCR không tự gửi yêu cầu.
2. **C02:** sửa một trường và kiểm tra có thể sửa tay; nếu đã tích đồng ý thì sửa phải bỏ tích. Camera bị từ chối/hủy không làm app treo hoặc tự tạo yêu cầu.
3. **C03:** ở tài khoản thử riêng, gửi dữ liệu tổng hợp khi bạn chủ động đồng ý; trạng thái phải chờ đối chiếu, không tự VERIFIED. Hủy yêu cầu thử và tải lại; trạng thái hủy phải lưu. Chỉ thao tác trong môi trường test hiện tại, không gửi mẫu này làm giấy tờ thật ở cơ sở y tế.
4. Quyền review/approve/reject/expiry và cạnh tranh hai người đã kiểm chứng với fixture riêng. Nhân viên chỉ xác nhận giấy tờ thật khi đã nhìn bản gốc và người xuất trình theo [trang15](15-doc-giay-to-va-doi-chieu.md). Chụp mẫu giả không chứng minh danh tính người dùng.

Để thử xử lý ảnh offline, có thể đọc mẫu khi ngắt mạng rồi bật lại kết nối trước các bước cần API. Không gửi ảnh căn cước thật cho người hỗ trợ để kiểm tra camera. App đọc tại máy, chỉ gửi ba trường sau đồng ý.

## D — ADMIN/passkey nhân viên thật

Kiểm tra20/10 ghi nhận chưa có passkey ADMIN đang hoạt động trên runtime test; phiên mật khẩu demo vì vậy bị chặn hợp lệ khi tạo/khóa tài khoản. Cần operator khởi tạo ADMIN đầu tiên theo [trang16](16-bao-mat-dot-2.md#khởi-tạo-admin-đầu-tiên--thao-tác-vận-hành), với địa chỉ email có người nhận thực tế. Không tạo credential hoặc session giả bằng SQL để vượt điều kiện passkey.

Phiên kết nối20/10 đã chuẩn bị bootstrap runtime: cửa sổ Chrome riêng mở trang enrollment, mã kích hoạt được điền sẵn qua operator; người nhận tự nhập toàn bộ mã cấp quyền trong email. Mã có hiệu lực15 phút, không phải mã OTP sáu số của reset. Không gửi mã vào chat. Cấp xong cần xác nhận lần nữa để thử đăng nhập. Khi mã hết hạn, operator cấp lượt mới có kiểm soát, không gửi lại liên tục.

- **D01:** ADMIN hoàn tất hai mã riêng tại trang enrollment và xác nhận passkey bằng hộp thoại hệ điều hành trên Mac hoặc iPhone. Mã email nhập tại web; mã kích hoạt được trao qua kênh riêng. Touch ID/Face ID/mã khóa phải do chủ thiết bị xác nhận.
- **D02:** đăng nhập passkey vừa tạo, tạo tài khoản nội bộ thử, chọn phân công có thật, tải lại và kiểm tra dữ liệu vẫn còn.
- **D03:** cấp quyền passkey cho nhân viên thử; nhân viên enrollment/đăng nhập đúng vai trò. Người sai vai trò bị403.
- **D04:** khóa nhân viên thử → API/phiên cũ bị chặn → mở khóa → phiên cũ vẫn không dùng được → đăng nhập passkey mới thành công.

Nếu dùng Safari trên iPhone cho web nhân viên, cần HTTPS/origin/RP ID hợp lệ và CA được Safari tin cậy; việc app native có CA riêng chưa cấu hình lòng tin cho Safari. Web localhost trên Mac có thể dùng cho mốc test R1. Không bỏ kiểm tra certificate hoặc đổi policy xác thực để cho qua.

## Ghi kết quả và điều kiện chuyển đợt

Ghi từng mã, PASS/FAIL/CHƯA THỬ, thời gian và phiên bản; chỉ mô tả thông báo đã thấy. Cột chưa thử giữ nguyên cho đến khi có thao tác thật. Backend log hoặc bản ghi session chỉ hỗ trợ đối chiếu, không tự chứng minh người dùng đã quét mặt hoặc nhìn giấy tờ gốc.

R1 đã đạt mốc kỹ thuật theo [biên bản](../.planning/identity-service/r1-verification-2026-10-20.md). Khi A–D được xác nhận trong phạm vi test đã chọn, cập nhật biên bản và kế hoạch tổng rồi chuyển R2. Nghiệm thu staging/production, Android vật lý nếu phát hành và quy trình đối chiếu giấy tờ thật vẫn ghi đúng môi trường tương ứng; không nhập nhằng với bài kiểm tra mẫu giả trên Mac.

Cập nhật R4 (20/10/2026): cách nhận thông báo, mở đúng tài nguyên sau kiểm tra quyền, tải bù và phạm vi push thực tế xem [hướng dẫn 21](21-thong-bao-va-dong-bo-r4.md).

Mốc cập nhật21/10/2026: bản1.0.0(20) đã cài/mở, bổ sung xử lý thông báo khi khóa/chuyển nền; các thao tác nghiệm thu máy thật vẫn chưa tự được tính là đạt. Xem [biên bản R4 mới nhất](../.planning/notification-service/r4-acceptance-2026-10-21.md).
