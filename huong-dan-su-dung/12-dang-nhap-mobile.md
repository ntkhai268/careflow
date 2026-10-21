# 12 — Đăng nhập và quên mật khẩu trên app điện thoại

**Cập nhật 19/10/2026:** đã cài/mở app **1.0.0 (13)** cùng backend test V16. Đăng ký email, khóa Secure Enclave, chuyển khóa giữ Face ID và chính sách khóa/idle xem [đợt 2](16-bao-mat-dot-2.md). Chữ ký phần cứng đã qua kiểm tra trên iPhone; Face ID trên cảm biến, nhận mã trong inbox và reset hoàn chỉnh vẫn chờ người dùng xác nhận. Mốc bản (8)/(9)/(11) bên dưới là lịch sử; AI và VNPT tiếp tục hoãn.

[Quay về mục lục](README.md) · [Cài app iPhone](11-cai-app-iphone.md) · [Tài khoản demo](01-tai-khoan-va-du-lieu.md)

## Bổ sung bản16 — sửa dữ liệu Keychain cũ và đồng bộ Face ID

Ngày19/10/2026, bản iPhone **1.0.0(16)** đã build/ký/cài đè và mở. Sửa việc xóa dữ liệu cũ khi chuyển từ mức bảo vệ Keychain trước đây sang cấu hình hiện tại: không để khóa ký cũ hoặc metadata Face ID đã thu hồi bị giữ lại và chuyển khóa lặp sau mỗi lần mở app. Chỉ xóa đúng từng khóa thuộc app; không xóa toàn bộ Keychain, không giảm mức bảo vệ của dữ liệu ghi mới.

Nếu đăng ký Face ID đã bị thu hồi/hết hạn: **đăng nhập bằng mật khẩu → Tài khoản → Bảo mật đăng nhập**. Màn này đối chiếu đăng ký cục bộ với danh sách thiết bị còn hiệu lực trên server, bỏ thiết lập đã thu hồi và giữ phiên mật khẩu hiện tại. Chọn Face ID, nhập lại mật khẩu và bật theo hộp thoại iOS. Sau đó thử **Đăng xuất → biểu tượng Face ID → xác thực**. Không tự phục hồi một đăng ký mà server đã thu hồi.

Đăng ký đang hoạt động được giữ; lỗi mạng khi kiểm tra danh sách không được làm mất đăng ký. Đã kiểm tra trực tiếp trên iPhone bằng namespace riêng: tái hiện lỗi xóa Keychain cũ và xác nhận cách sửa, Face ID khả dụng, khóa Secure Enclave thử ổn định qua hai lần mở app. Chưa dùng bài kiểm tra này để xác nhận người dùng đã hoàn tất Face ID đăng nhập thật. Xem [biên bản sửa lỗi](../.planning/identity-service/iphone-faceid-document-fix-2026-10-19.md).

## Phạm vi

**Lịch sử bản vá trước đợt 2:** các thay đổi ngừng câu hỏi, thu hồi credential sau reset và bảo vệ backup xem [trang 13](13-ban-va-bao-mat-va-van-hanh.md). Bản cài Release 8 bên dưới là mốc trước bản vá.

Chức năng nằm trong app Flutter native `frontend/patient-mobile`, bản **1.0.0 (8)**, nhánh `feature/dangkhoii/patient-mobile-ios-login`. Đăng ký Face ID/vân tay độc lập với phiên đăng nhập; các thao tác trên điện thoại không mở trình duyệt hoặc gọi browser bridge. Identity và Gateway của môi trường test hiện chạy bản từ `feature/dangkhoii/identity-email-recovery`, trên nền bảo mật `security-integration` chưa commit, có đăng ký thiết bị sinh trắc học V12, API email và đăng nhập thiết bị, HTTPS và DPoP; các service khác giữ runtime hiện hành. Source và cấu hình ký/runtime hiện chưa commit/push; clone nhánh cũ không tự có các phần này.

iPhone có Face ID sẽ hiện **Face ID**; iPhone có cảm biến vân tay hiện **Touch ID**. Android dùng sinh trắc học bảo mật mà hệ điều hành hỗ trợ; nếu hệ điều hành chỉ báo loại chung, app hiện **Sinh trắc học**. Máy không hỗ trợ, chưa đăng ký khuôn mặt/vân tay hoặc chưa có quyền sẽ hiện hướng dẫn và giữ cách đăng nhập bằng mật khẩu. Không dùng ảnh chụp khuôn mặt làm thông tin đăng nhập.

**Ghi nhận lịch sử Release (8):** đã ký, cài đè và mở Release (8) trên iPhone 11 Pro Max iOS 18.1.1, giữ dữ liệu và đăng ký Face ID của Release 7; kiểm tra chữ ký bundle đạt. Cập nhật từ Release 7 lên 8 không yêu cầu bật lại Face ID.

**Giới hạn nghiệm thu:** kiểm tra tự động và API thật từ Mac không thay cho thao tác Face ID trên iPhone. Cần hoàn tất bảng nghiệm thu cuối trang trên chính điện thoại; Android đã build APK kiểm tra thành công nhưng chưa được nghiệm thu bằng cảm biến thật.

Xem và thu hồi đăng ký tại **Tài khoản → Thiết bị sinh trắc học** theo [hướng dẫn 14](14-tep-thiet-bi-va-bang-gia.md).

## Bật Face ID hoặc vân tay

1. Thiết lập khuôn mặt/vân tay và mã khóa trong Cài đặt của điện thoại. Giữ kết nối backend theo [trang cài iPhone](11-cai-app-iphone.md).
2. Mở CareFlow và đăng nhập bằng mật khẩu của tài khoản PATIENT.
3. Vào tab **Tài khoản → Bảo mật đăng nhập**.
4. Chọn **Face ID**, **Touch ID** hoặc **Sinh trắc học** tùy máy; nhập **Mật khẩu hiện tại**, nhấn **Bật …**.
5. Cho phép Face ID nếu iOS hỏi và xác thực trong hộp thoại hệ điều hành. Nếu hủy, tính năng chưa bật và phiên mật khẩu hiện tại vẫn tiếp tục.
6. Kết quả cần thấy: thông báo đã bật trên điện thoại và trạng thái đã bật cho đúng tài khoản.

App không lưu mật khẩu, ảnh khuôn mặt hay mẫu vân tay. Kho yêu cầu sinh trắc học giữ một thông tin đăng ký thiết bị riêng; server chỉ giữ bản băm. Access/refresh token của phiên nằm trong bộ nhớ khi bật tính năng. Face ID mở khóa thông tin thiết bị để server tạo phiên mới, không khôi phục phiên đã đăng xuất. Đăng ký có hiệu lực tối đa 90 ngày; quá hạn cần đăng nhập bằng mật khẩu và bật lại.

**Sau cập nhật Release 7:** đăng ký cũ của Releases 4–6 cần bật lại **một lần**. Đăng nhập bằng mật khẩu → **Tài khoản → Bảo mật đăng nhập → Face ID/Touch ID → Bật …** và xác thực. Từ đó đăng xuất thông thường vẫn giữ đăng ký.

## Đăng nhập, đăng xuất và khóa app

1. Sau khi bật, nhấn **Đăng xuất** ở Tài khoản. Kết quả: ra màn đăng nhập; phiên vừa dùng đã bị thu hồi, đăng ký Face ID/vân tay vẫn còn.
2. Màn đăng nhập tự điền tài khoản đã bật sinh trắc học. Ô **Mật khẩu** thu gọn theo chiều ngang, cạnh bên là nút biểu tượng **khuôn mặt** trên máy có Face ID hoặc **vân tay** với Touch ID/sinh trắc học khác. Chạm biểu tượng để đăng nhập, không cần nhập mật khẩu; không tự bật hộp thoại khi vừa mở màn. Vẫn có thể gõ mật khẩu và nhấn **Đăng nhập**. Không còn nút **Đăng nhập bằng cách khác**; sinh trắc học dùng trực tiếp biểu tượng cạnh ô mật khẩu.
3. Xác thực trong hộp thoại hệ điều hành. Chỉ sau khi server chấp nhận thiết bị và cấp phiên mới, app mới vào Trang chủ. Lặp lại đăng xuất → Face ID để kiểm tra tính năng không tự tắt.
4. Thử **Hủy**: vẫn ở màn đăng nhập, đăng ký được giữ để thử lại. Mất mạng: không tạo phiên cục bộ; kết nối lại rồi thử. Không dùng mã khóa thay cho bước sinh trắc học này.
5. Đóng app rồi mở lại: đăng ký vẫn còn; access/refresh của phiên cũ không tự trở lại từ kho sinh trắc học. Đưa app đang dùng xuống nền rồi mở lại: app khóa nội dung và yêu cầu xác thực; các form đăng nhập/quên mật khẩu/đăng ký đang nhập được giữ.

**Dùng tài khoản khác:** nhấn liên kết này dưới ô tài khoản để xóa tên đăng nhập/mật khẩu đang nhập và đưa con trỏ về ô tài khoản. Cũng có thể sửa trực tiếp tên đăng nhập/email. Khi tên khác tài khoản đã nhớ, ô mật khẩu mở đủ chiều ngang và nút sinh trắc học ẩn; nhập thông tin tài khoản mới rồi nhấn **Đăng nhập**. Mật khẩu đang nhập được xóa khi sửa tài khoản để tránh dùng nhầm. App không ghi đè tên vừa nhập nếu thông tin tài khoản đã nhớ tải chậm hoặc sau khi hủy Face ID. Chỉ việc sửa form chưa xóa đăng ký sinh trắc học; nhập lại đúng tên tài khoản đã nhớ sẽ hiện lại nút. Email khác với tên đăng nhập đã lưu dùng chế độ mật khẩu.

**Đăng xuất** chỉ kết thúc phiên đang dùng. Nếu thực hiện khi mất mạng, app vẫn xóa phiên cục bộ và giữ đăng ký; việc thu hồi trên server chỉ được xác nhận khi yêu cầu thành công. Không giữ refresh token đã đăng xuất để dùng lại cho Face ID.

Đăng nhập bằng mật khẩu vào **cùng tài khoản** vẫn giữ thiết lập Face ID. Đổi sang tài khoản khác sẽ xóa đăng ký cục bộ của tài khoản trước; thiết lập cho tài khoản mới nếu muốn sử dụng. App hiện nhớ một tài khoản sinh trắc học trên mỗi cài đặt.

**Tắt trên điện thoại này:** vào **Tài khoản → Bảo mật đăng nhập → phương thức đã bật → Tắt … trên điện thoại này → Tắt**. Server vô hiệu hóa đăng ký và app xóa thông tin trong kho sinh trắc học. Phiên hiện tại vẫn tiếp tục; lần đăng nhập sau dùng mật khẩu. Cần có kết nối để xác nhận việc tắt trên server.

**Đặt lại mật khẩu, đăng xuất toàn bộ phiên hoặc quản trị viên khóa/đổi quyền tài khoản** sẽ thu hồi đăng ký thiết bị. Điện thoại phát hiện khi đăng nhập lần sau và yêu cầu dùng mật khẩu/bật lại. Thay đổi bộ khuôn mặt/vân tay hoặc mất dữ liệu kho bảo mật cũng có thể cần thiết lập lại.

## Câu hỏi bí mật đã ngừng hỗ trợ

Source bản vá 19/10/2026 không còn cho cấu hình hoặc đăng nhập bằng câu hỏi. App cũ gọi endpoint nhận 410. Dùng mật khẩu hoặc sinh trắc học đã bật; xem [phạm vi bản vá](13-ban-va-bao-mat-va-van-hanh.md). Không thử lại câu hỏi cũ.

## Quên mật khẩu qua email

Chỉ đặt lại mật khẩu bằng mã gửi về email đã đăng ký. Câu hỏi bí mật đã ngừng dùng để đăng nhập trong bản vá 19/10; các API khôi phục bằng câu hỏi/passkey đã bị đóng (410). Không gửi SMS. Sender: **CareFlow <careflow@supersignal-resend.id.vn>**. Domain đã Verified theo xác nhận của chủ dự án.

1. Từ màn đăng nhập, chọn **Quên mật khẩu?**, nhập **Email đã đăng ký** và nhấn **Gửi mã xác nhận**. Dùng tài khoản thử riêng có email mình truy cập được; email demo giả không nhận được thư. Không reset tài khoản demo đang dùng chung.
2. Ở bước **Xác thực email**, mở Mail để đọc thư **Mã xác nhận đặt lại mật khẩu CareFlow**; kiểm tra cả Thư rác. Quay lại app vẫn giữ màn nhập mã và khóa phiên sinh trắc học cũ.
3. Nhập/dán **Mã xác nhận** gồm 6 số rồi nhấn **Xác thực mã**. Bước này **chưa có trường nhập mật khẩu**. Mã sai/hết hạn: ở lại bước xác thực và hiện lỗi. Đúng mã: mới sang bước **Tạo mật khẩu mới**; mật khẩu và các phiên hiện tại chưa thay đổi.
4. Nhập **Mật khẩu mới** và **Xác nhận mật khẩu mới**, nhấn **Đặt lại mật khẩu**. Mật khẩu từ 8 ký tự, có chữ hoa, chữ thường, số và ký tự đặc biệt; tối đa 72 byte UTF-8. Hoàn tất trong 5 phút kể từ khi xác thực mã. Nếu hết thời gian, nhấn **Yêu cầu mã mới** và bắt đầu lại.
5. Khi hiện **Đã đặt lại mật khẩu**, chọn **Đăng nhập lại**, dùng mật khẩu mới. Server đã thu hồi toàn bộ phiên đăng nhập/refresh token cũ và gửi email thông báo. App không tự đăng nhập sau reset; điện thoại khác phải đăng nhập lại. Thiết lập sinh trắc học cần bật lại.

**Giới hạn:** mã email có hiệu lực 5 phút, chỉ xác thực một lần, tối đa 5 lần nhập sai. Gửi lại sau 60 giây, tối đa 5 yêu cầu mỗi email/giờ; giới hạn lưu trên server. **Gửi lại mã** thay mã và quyền đặt lại cũ; dùng thư mới nhất trên đúng app/thiết bị đã yêu cầu. Quyền đặt mật khẩu sau xác thực cũng chỉ dùng một lần và hết hạn sau 5 phút. **Dùng email khác** quay về bước đầu. Đóng hẳn app hoặc mất phản hồi khi xác thực có thể cần yêu cầu mã mới sau thời gian chờ.

**Email mới từ Release 6:** thương hiệu CareFlow dùng HTML, không gửi ảnh logo/CID/tệp đính kèm. Email đã nhận trước khi sửa vẫn giữ ảnh đính kèm; hãy gửi mã mới và mở đúng thư mới nhất để kiểm tra. Không chia sẻ mã hoặc mật khẩu.

**Không thấy thư/lỗi gửi:** kiểm tra email, Thư rác và kết nối server theo trang 11; chờ gửi lại rồi thử. App dùng thông báo chung cho mọi email để không lộ tài khoản tồn tại. Thiếu cấu hình mail: server báo dịch vụ chưa sẵn sàng; provider từ chối gửi: không được xác thực mã. Tài khoản bị quản trị viên khóa/vô hiệu hóa cần hỗ trợ; luồng này chỉ gỡ khóa tạm do nhập sai đăng nhập. Nếu không yêu cầu khôi phục, bỏ qua email: mật khẩu chưa thay đổi.

**Phạm vi kiểm tra 18/10/2026:** luồng email đã kiểm tra từ Release 7, đã ký/cài/mở trên iPhone 11 Pro Max; API thật từ Mac → Gateway → hai Identity → PostgreSQL → Resend test sink đã qua luồng ba API riêng, mã sai/hết hạn/đã dùng, chặn đặt mật khẩu trực tiếp, xác thực mã không đổi mật khẩu, thu hồi phiên và đăng nhập lại. Sáu kiểm thử PostgreSQL riêng xác nhận giao dịch/race; UI/HTTP/lifecycle có kiểm thử tự động. Ảnh Gmail do người dùng cung cấp đã xác nhận nhận được thư trước đó và lỗi ảnh đính kèm. **Chờ nghiệm thu lại email mới và toàn bộ luồng chia bước trên UI iPhone Release 7**; test sink Delivered không thay cho kiểm tra Inbox cá nhân.

**Bổ sung 19/10/2026, bản (14):** đã gửi ba email thật tới địa chỉ người dùng cung cấp bằng tài khoản thử trong DB tách riêng; Resend báo Delivered cho đăng ký, mã reset và thông báo đổi mật khẩu. Payload không có logo ảnh/tệp đính kèm; mã sai/dùng lại bị chặn, mật khẩu cũ và access/refresh cũ bị thu hồi. Không thay đổi tài khoản đang dùng trên iPhone. Hiển thị Gmail và thao tác trọn luồng trên UI vẫn cần người dùng xác nhận. Xem [báo cáo bổ sung](../.planning/identity-service/vnpt-ekyc-live-verification-2026-10-19.md).

## Nghiệm thu trên thiết bị

| Trường hợp | Kết quả mong đợi | Trạng thái 18/10/2026 |
|---|---|---|
| Nhận Face ID trên iPhone 11 Pro Max | Hiện Face ID, hộp thoại iOS thật | Chờ thao tác trên máy |
| Bật, khóa, mở bằng Face ID | Đúng tài khoản, tải API thật | Chờ thao tác trên máy |
| Hủy Face ID hoặc mất mạng | Không vào phiên đăng nhập | Logic tự động đạt; chờ cảm biến thật |
| Đưa app xuống nền rồi trở lại | Khóa và yêu cầu xác thực | Chờ thao tác trên máy |
| Đổi bộ sinh trắc học/hết phiên | Không mở phiên bằng thiết lập cũ | Logic hết phiên đạt; chờ kiểm tra OS |
| Đăng xuất → Face ID nhiều lần | Thu hồi phiên cũ, giữ đăng ký và tạo phiên mới | API thật/kiểm thử tự động đạt; chờ Face ID thật trên Release 8 |
| Chuyển tài khoản trên màn đăng nhập | Tự điền tài khoản cũ; đổi tên ẩn icon, mở rộng mật khẩu, không đăng nhập nhầm | 9 kiểm thử màn đăng nhập đạt; chờ UI Release 8 trên iPhone |
| Tắt Face ID | Thu hồi đăng ký; phiên đang dùng vẫn tiếp tục | API thật/kiểm thử tự động đạt; chờ thao tác trên máy |
| Câu hỏi đã ngừng hỗ trợ | Không còn thẻ; API trả 410 | API trả 410 đã triển khai; app mới đã cài, chờ nghiệm thu UI |
| Email: gửi mã, reset, đăng nhập lại | Nhận đúng thư, mật khẩu mới hoạt động; phiên cũ bị thu hồi | API thật/Resend test sink đạt; đã nhận thư cũ, chờ thư mới và UI iPhone |
| Mở Mail rồi quay lại app | Giữ màn nhập mã; phiên sinh trắc học cũ vẫn khóa | Kiểm thử lifecycle đạt; chờ thao tác trên máy |
| Vân tay Android/Touch ID | Nhãn và hộp thoại phù hợp thiết bị | Android build đạt; chưa nghiệm thu thiết bị thật |

Lặp lại bằng tài khoản thử của mình, không reset DB/hàng đợi và không công khai câu trả lời. Ghi nhận bản app, thiết bị và kết quả từng hàng trước khi đánh dấu nghiệm thu đầy đủ.

**Bổ sung Release 8 (18/10/2026):** 52 kiểm thử hồi quy mobile đạt, trong đó 9 kiểm thử màn đăng nhập mới; phân tích Dart không có lỗi. Đã cài và mở trên iPhone thật; chờ người dùng nghiệm thu giao diện mới và xác thực bằng cảm biến thật. Backend và luồng quên mật khẩu giữ bản đã kiểm tra ở Release 7.
