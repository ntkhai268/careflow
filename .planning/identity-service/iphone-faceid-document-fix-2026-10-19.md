# Sửa Face ID/Keychain và đọc giấy tờ trên iPhone — bản16

## Kết quả và giới hạn

Đã sửa lỗi xóa dữ liệu Keychain cũ, thêm đồng bộ đăng ký Face ID với máy chủ, cải thiện đọc thông tin giấy tờ và cài/mở **CareFlow1.0.0(16)** trên iPhone11ProMax. Tái hiện lỗi và xác nhận bản sửa trực tiếp trên iPhone bằng khóa scratch riêng. Native OCR điền đủ ba trường từ ảnh tổng hợp trên chính iPhone; không dùng ảnh CCCD người thật, không gọi mạng trong diagnostic.

Face ID được hệ điều hành báo khả dụng. Khóa Secure Enclave thử ổn định qua hai lần chạy app. Đây không thay thế nghiệm thu quét mặt đăng nhập, camera và đọc căn cước thật của người dùng. Người dùng chưa mô tả chính xác bước đọc bị lỗi; không kết luận mọi lỗi ảnh thực tế đã được giải quyết bằng ảnh tổng hợp.

Source ở `feature/dangkhoii/patient-mobile-ios-login`, nềne5fc9c1 + thay đổi chưa commit. Backend/DB không đổi trong lượt này, V17 và luồng đối chiếu thủ công giữ nguyên. Hai Identity/cầu nối vẫn hoạt động, VNPT disabled và không có khóa VNPT trong runtime; bộ đếm lịch sử giữ4, tăng0. Không thay Resend/outbox, không gửi mail, không add/commit/push.

## Nguyên nhân đã xác minh

Phiên bản trước dùng `FlutterSecureStorage` mặc định WhenUnlocked để lưu khóa phần mềm/metadata. Bản bảo mật chuyển sang AfterFirstUnlockThisDeviceOnly khi ghi mới. Native plugin9.2.4 đọc mà không lọc accessibility, nhưng delete có lọc accessibility. Do đó app vẫn đọc được mục cũ nhưng xóa bằng tùy chọn mới trả thành công kiểu item-not-found, để mục cũ tồn tại.

Kết quả: khóa phần mềm có thể được nạp lại ở lần chạy sau và chuyển sang phần cứng lặp lại; metadata đăng ký cũ có thể còn dù server đã thu hồi. Nhật ký server có các lần chuyển khóa lặp và một đăng ký cũ bị thu hồi qua API trước khi sửa. Không tự sửa `revoked_at` hoặc phục hồi quyền đăng nhập đã thu hồi.

Kiểm tra trên iPhone tái hiện bằng khóa scratch tạo với mức cũ: đọc bằng store mới được; delete cũ vẫn để lại; delete bỏ bộ lọc accessibility xóa đúng khóa. Không đọc/sao chép credential, token, tài khoản hoặc vault Face ID thật trong diagnostic.

## Thay đổi

- `secure_storage_cleanup.dart`: xóa đúng key/service của app bằng `IOSOptions(accessibility:null)`, bỏ bộ lọc accessibility trên delete; không dùng deleteAll, không giảm mức bảo vệ khi ghi, không thay khóa phần cứng.
- Dùng helper cho dọn khóa DPoP cũ/pending migration, metadata đăng ký và access/refresh cục bộ trong `ApiService`/`BiometricSessionStore`.
- `AuthService.reconcileBiometrics`: tại màn thiết lập đã đăng nhập, đọc danh sách thiết bị đang hiệu lực. Đăng ký local không còn trên server được gỡ local, giữ access/refresh của phiên mật khẩu. Kiểm tra lại credential ID trước thao tác; lỗi mạng không xóa đăng ký. Đăng ký đang hoạt động không gọi thêm xác thực cảm biến.
- Đăng ký đã thu hồi phải bật lại bằng mật khẩu và Face ID theo luồng thông thường; không tự bật hoặc bỏ qua cảm biến.
- Parser giấy tờ hỗ trợ thêm nhãn Họ/chữ đệm/tên, Fullname/bilingual inline labels, số có khoảng trắng, ngày sinh dấu chấm/gạch ngang. UI báo ảnh đã xử lý tại máy và hướng dẫn bổ sung/chụp lại khi chưa đọc rõ. Không coi OCR thành xác minh thật.
- Mô tả quyền camera không còn đề cập selfie; cập nhật hướng dẫn12/15/11, mục lục/changelog.

## Bằng chứng trực tiếp trên thiết bị

Diagnostic target riêng ngoài Git, không phải main app. Ảnh được vẽ trong RAM với thông tin TEST PERSON/số thử/ngày sinh thử; không đọc camera/thư viện ảnh/tài khoản thật. Dùng namespace Secure Enclave và keychain scratch riêng, dọn scratch sau phép thử. Chỉ copy file kết quả boolean/capability ra máy Mac, không copy container/Keychain/vault.

Hai lần chạy xác nhận:

| Kiểm tra | Kết quả |
|---|---|
| Đọc mục Keychain cũ | true |
| Delete với tùy chọn mới vẫn để sót | true — tái hiện lỗi |
| Helper mới xóa mục cũ | true |
| Bộ bảo vệ khóa thử | secure_enclave |
| Khóa thử ổn định sau khởi động app lại | true ở lần2 |
| Sinh trắc học | available=true, Face ID |
| Native OCR + parser | đủ số/họ tên/ngày sinh từ ảnh tổng hợp |
| Mạng/ảnh thật/credential thật trong diagnostic |0 request/không sử dụng/không truy cập |

So sánh khóa giữa hai lần chạy dùng biểu diễn JWK với thứ tự trường cố định. Harness ban đầu hash JSON theo thứ tự Dictionary thay đổi giữa process, gây báo false; đã sửa harness và chạy lại hai lần. Không sửa thuật toán DPoP server vì server vốn canonicalize đúng. Hash khóa thử không đưa vào evidence công khai.

Sau diagnostic đã build lại với `lib/main.dart`, ký strict, cài và mở app chính bản16. Đối chiếu file native/Dart của workspace build khớp source. Không để target diagnostic thay app chính sau khi hoàn tất.

## Kiểm thử tự động

- Flutter toàn bộ: **233 đạt**,15 bỏ qua có điều kiện. Chạy riêng14 HTTP fixture: refresh3, recovery4, download1, biometric4, document2; tổng **247 test đã thực thi đạt**. Còn1 test demo thanh toán cũ bỏ qua, không phải kiểm tra Face ID/camera được miễn.
- Bổ sung8 test:2 về xóa mục legacy/key không liên quan,3 về đồng bộ revoked/active/offline,2 định dạng OCR mới,1 UI thông báo khi ảnh không đọc được. Chạy lại các kiểm tra migration, vault/session, hủy/thu hồi và lỗi mạng.
- Regression đầu tiên ở harness unit dùng platform channel trên macOS nên không đi qua iOptions; đổi sang fixture mô phỏng semantics iOS và dùng phép thử iPhone làm bằng chứng native. Fixture override GET được sửa khớp tên tham số base API. Các lỗi harness không bị bỏ qua; bộ cuối đạt.
- Analyzer:0 error/0 warning;2 info curly braces đã có ở test khác, code/test mới không thêm info.
- Android debug build đạt. iOS Release16/code signing/install/launch đạt. Không rebuild backend vì không có thay đổi backend/schema.
- Git diff whitespace đạt. GitNexus query/context + upstream impact trước sửa production: ApiService HIGH/20 direct, đã cảnh báo; BiometricSessionStore MEDIUM/8 direct/2 flows; AuthService MEDIUM/5 direct, settings load LOW/3 direct. Một số Dart symbols UNKNOWN không coi là không có ảnh hưởng.
- Đã reindex mobile và detect-changes toàn bộ diff; mức HIGH gồm nhiều đợt trước. Phạm vi các file mới cũng được đọc/test/native-build riêng; không dựa riêng graph.

## Còn cần người dùng xác nhận

Sau đăng nhập mật khẩu, mở thiết lập Face ID để đồng bộ và bật lại nếu đăng ký cũ bị thu hồi, rồi đăng xuất/đăng nhập bằng cảm biến. Thử camera/OCR với giấy tờ trên điện thoại và kiểm tra các ô được điền; không gửi giấy tờ hoặc dữ liệu thật vào chat. Thao tác đầy đủ được quản lý duy nhất tại [hướng dẫn12](../../huong-dan-su-dung/12-dang-nhap-mobile.md) và [hướng dẫn15](../../huong-dan-su-dung/15-doc-giay-to-va-doi-chieu.md).

[Evidence đã loại dữ liệu riêng](evidence/iphone-regression-20261019/). Logs/build/diagnostic source ngoài Git tại `/tmp/careflow-iphone-regression-20261019/` và workspace build private. Không lấy request ghi nhận ở máy chủ làm bằng chứng người dùng đã quét Face ID thành công.
