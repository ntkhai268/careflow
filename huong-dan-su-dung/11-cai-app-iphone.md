# 11 — Cài app CareFlow lên iPhone

[Quay về mục lục](README.md)

## Phạm vi bản cài trên Mac này

**Bản hiện tại: 1.0.0 (25)** đã ký, cài đè và mở trên iPhone 11 Pro Max ngày24/10/2026 từ nguồn R7 hiện tại. Đã đối chiếu mã trong `lib/` với worktree `feature/dangkhoii/release-r7`, giữ bundle ID và cài đè không gỡ app. Deployment target iOS15 đồng bộ trong project/Pods. Bản chẩn đoán tạm24 đã kiểm trực tiếp trên iPhone: HTTPS200, khóa Secure Enclave/ký yêu cầu, đăng nhập API200, AuthService đăng nhập và đọc đúng danh tính. Sau kiểm tra đã cài bản thường25, target `lib/main.dart`, có CA đúng trong bundle và không còn entrypoint/tham số tài khoản chẩn đoán. Không đổi mật khẩu hoặc backend. Thao tác picker/Face ID/GPS và push nền vẫn phải nghiệm thu riêng. Firebase/APNs và entitlement push còn thiếu; xem [trang21](21-thong-bao-va-dong-bo-r4.md) và [bàn giao R7](25-nghiem-thu-va-ban-giao-r7.md). Bản build mới ở `~/Library/Developer/CareFlow/r7-20261021/mobile/app`; các mốc dùng staging cũ bên dưới là lịch sử.

Provisioning của bản25 vẫn hết hạn **25/10/2026 lúc15:04 giờ Việt Nam**, đã đọc trực tiếp từ bundle ký. Xcode dùng lại profile còn hiệu lực; cài lại không tự kéo dài hạn profile. Sau mốc này cần ký/cài lại với profile còn hạn để tiếp tục mở app bằng Personal Team.

**Lỗi đóng gói bản22:** cấu hình có `LOCAL_TEST_CA_ASSET` nhưng file CA không được khai báo trong assets của `pubspec.yaml`, nên không có trong bundle. App lỗi khi nạp CA trước lúc gửi đăng nhập và hiện thông báo chung; tài khoản/mật khẩu vẫn hợp lệ. Đã sửa khai báo assets trong staging riêng và kiểm file CA/hash/chữ ký trước khi cài bản25. Chứng chỉ của môi trường test và cấu hình ký vẫn nằm ngoài Git; không tắt xác minh HTTPS để tránh lỗi.

Chuẩn bị ngày **18/10/2026**, từ `frontend/patient-mobile` tại `develop@e5fc9c1`, với bản sửa đăng nhập ở nhánh `feature/dangkhoii/patient-mobile-ios-login`, Flutter 3.41.6, Xcode 27.1 và CocoaPods 1.17.0. Bản sao để build nằm ngoài Git tại `~/Library/Developer/CareFlow/iphone11pm-20261018/app`; cấu hình ký, endpoint và CA thử nghiệm riêng của Mac không nằm trong source. Bản này dùng iOS 15 trở lên và tên hiển thị **CareFlow**.

Đây là app Flutter iOS gốc. Các sửa riêng của `careflow-mobile-emulator-service/app` trong runtime tích hợp chưa tự có trong bản cài này. Không dùng kết quả E2E của giả lập trình duyệt để kết luận app iPhone đã đạt.

`DEMO_MODE=false`, `FIREBASE_ENABLED=false`; API trỏ tới môi trường test trên Mac qua HTTPS, không dùng dữ liệu hành trình mô phỏng. Native push chưa được cấu hình. Các luồng chưa có backend/adapter trong app gốc có thể hiện chưa khả dụng.

**Trạng thái nghiệm thu:** bản đầu đã cài/mở trên iPhone 11 Pro Max iOS 18.1.1 nhưng chưa đăng nhập được: thiếu xác thực thiết bị và CA của môi trường test. Bản Release **1.0.0 (2)** bổ sung hai phần này, đã ký/cài/mở thành công và đã qua kiểm tra đăng nhập/đọc hồ sơ/làm mới phiên/đăng xuất trên Gateway thật từ Mac. Sau đó phát hiện IP Wi-Fi của Mac đổi từ `192.168.1.9` sang `192.168.1.10`; bản **1.0.0 (3)** chuyển endpoint sang tên máy `.local` để không phụ thuộc IP cố định và đã ký/cài/mở thành công trên iPhone. Sáu bước API (đăng nhập, danh tính, hồ sơ, refresh, danh tính, đăng xuất) qua endpoint mới từ Mac đều trả HTTP 200. Người dùng đã xác nhận bản (3) kết nối/đăng nhập ổn. Chức năng đăng nhập và quên mật khẩu qua email của bản **1.0.0 (8)** và các bước cần nghiệm thu được ghi tại [đăng nhập mobile](12-dang-nhap-mobile.md). QR/GPS và toàn bộ hành trình chưa được nghiệm thu trên điện thoại. Bản sửa source chưa commit/push; clone repo chưa tự có patch và cấu hình runtime này. Profile ký hiện có hạn đến **25/10/2026, 15:01 giờ Việt Nam**.

**Mốc R1: 1.0.0 (16)** đã build/ký/cài đè và mở ngày19/10/2026, giữ bundle/dữ liệu. Sửa xóa Keychain cũ và đồng bộ trạng thái Face ID khi mở thiết lập; xem [hướng dẫn12](12-dang-nhap-mobile.md). Native OCR trên iPhone đã đọc ảnh tổng hợp thành công, có thêm định dạng nhãn/số/ngày sinh và thông báo khi cần bổ sung; xem [hướng dẫn15](15-doc-giay-to-va-doi-chieu.md). Không upload ảnh/selfie, VNPT vẫn ngừng. Toàn bộ Face ID/camera với người dùng thật còn cần người dùng xác nhận. Các ghi nhận bản15/14 trở xuống là lịch sử; AI tiếp tục để sau.

## Bản bảo mật cài ngày 19/10/2026

Đã cài đè và mở bản **1.0.0 (9)** trên iPhone 11 Pro Max, giữ nguyên bundle/dữ liệu. Backend test trên Mac đã nhận bản vá bảo mật và migration V13/V14 trên DB loopback. Kết nối tới `https://MacBook-Pro-cua-DangKhoii.local:18443` đã xác minh TLS. Không triển khai AI trong đợt này; web/production chưa được phát hành đồng bộ.

Bản (9) không còn lựa chọn câu hỏi bí mật. Kiểm tra trên iPhone: đăng nhập bằng mật khẩu → Tài khoản → thiết lập Face ID nếu chưa bật → đăng xuất → bấm biểu tượng khuôn mặt trên màn đăng nhập → xác thực. Thử hủy Face ID rồi đăng nhập lại để bảo đảm thiết lập không bị mất. Xem thêm [đăng nhập mobile](12-dang-nhap-mobile.md). Chỉ đánh dấu cảm biến/email đạt khi thực sự thao tác trên máy; kiểm tra API từ Mac không thay thế bước này.

## Chuẩn bị iPhone và ký ứng dụng

1. Cắm iPhone vào Mac bằng cáp truyền dữ liệu, mở khóa và chọn **Tin cậy máy tính này**.
2. Với iOS 16 trở lên, bật **Cài đặt → Quyền riêng tư & Bảo mật → Chế độ nhà phát triển**, khởi động lại và xác nhận bật. Nếu chưa thấy mục này, kết nối thiết bị với Xcode trước.
3. Trong **Xcode → Settings → Apple Accounts**, tự đăng nhập Apple Account. Mật khẩu và OTP chỉ nhập trực tiếp trong Xcode.
4. Mở workspace của bản build:

   ```sh
   open ~/Library/Developer/CareFlow/iphone11pm-20261018/app/ios/Runner.xcworkspace
   ```

5. Chọn project **Runner → target Runner → Signing & Capabilities**, bật **Automatically manage signing** và chọn **Team** của mình. Apple Account miễn phí có thể dùng Personal Team; nếu Bundle Identifier đã được đăng ký bởi tài khoản khác, chọn identifier riêng rồi cập nhật bản build.
6. Chọn iPhone làm thiết bị chạy. Đợi Xcode hoàn thành ghép đôi và chuẩn bị thiết bị.

Kết quả cần thấy: Xcode nhận đúng iPhone, Team có giá trị và không còn lỗi provisioning/certificate. Xác nhận **Tin cậy nhà phát triển** trên iPhone vẫn cần thiết để mở app lần đầu.

## Kết nối backend trên Mac qua Wi-Fi

Mac và iPhone phải ở cùng mạng, cho phép kết nối giữa các thiết bị. Môi trường backend hiện có phải đang chạy theo [trang chuẩn bị](00-chuan-bi.md). Khi bật lại môi trường ngày 18/10/2026, IP Wi-Fi của Mac là `192.168.1.10`; địa chỉ này có thể đổi khi kết nối lại mạng.

Từ bản **1.0.0 (3)**, cầu nối riêng cho app dùng `https://MacBook-Pro-cua-DangKhoii.local:18443`, chuyển API/WebSocket tới Gateway HTTPS `localhost:18080` với xác minh chứng chỉ. Cầu nối nghe cả IPv4/IPv6; mạng Wi-Fi phải cho phép phân giải tên máy `.local` (mDNS). Bản cũ trỏ tới IP `192.168.1.9` cần được cài đè bằng bản mới. Bản **1.0.0 (2)** đóng gói CA công khai của môi trường test trong app và giữ xác minh TLS. Người dùng không cần cài profile CA vào toàn hệ thống để đăng nhập bằng bản này.

1. Cho iPhone kết nối cùng Wi-Fi với Mac; giữ backend và cầu nối trên Mac đang chạy.
2. Mở CareFlow và chọn **Cho phép** khi iOS hỏi quyền truy cập **Mạng cục bộ**. Nếu từng từ chối, bật lại tại **Cài đặt → Quyền riêng tư & Bảo mật → Mạng cục bộ → CareFlow**.
3. Đăng nhập bằng tài khoản PATIENT tại [trang tài khoản](01-tai-khoan-va-du-lieu.md). Kết quả cần thấy là Trang chủ cùng hồ sơ bệnh nhân và lịch hẹn từ API thật.
4. Đóng rồi mở lại app để kiểm tra giữ phiên. Nếu vẫn lỗi, ghi thời điểm và thông báo đang hiển thị để đối chiếu; không đổi mật khẩu hoặc reset DB khi chưa xác định nguyên nhân.

CA có hiệu lực 30 ngày từ khi tạo. Đổi IP trên cùng mạng không cần build lại khi tên máy `.local` vẫn phân giải được. Nếu đổi tên máy, mạng chặn mDNS hoặc chứng chỉ hết hạn, cần cập nhật endpoint/chứng chỉ và build lại. Cổng HTTP `18444` giữ profile CA công khai để kiểm tra Safari riêng; profile này không bắt buộc cho bản app đã đóng gói CA. Nếu đã cài profile thử nghiệm ở bước chuẩn bị cũ, có thể gỡ trong **VPN & Quản lý thiết bị** khi kết thúc kiểm thử.

## Cài bản Release và mở từ biểu tượng

Sau khi Team và thiết bị sẵn sàng, kiểm tra staging có file CA tại `assets/certificates/local-test-ca.pem`. Trong `pubspec.yaml` của staging riêng trên Mac, bổ sung file này vào danh sách `flutter.assets`:

```yaml
flutter:
  assets:
    - assets/images/
    - assets/icons/
    - assets/certificates/local-test-ca.pem
```

Giữ khai báo CA riêng này khi đồng bộ lại source vào staging. Không chép chứng chỉ/cấu hình private của Mac vào source Git. Sau đó dùng Terminal; các kiểm tra sau build phải đạt trước khi cài:

```sh
set -e
cd ~/Library/Developer/CareFlow/r7-20261021/mobile/app
flutter devices
flutter pub get --offline
flutter build ios --release --config-only --no-pub --target=lib/main.dart --build-number=25 --dart-define-from-file="$HOME/Library/Developer/CareFlow/r0-20261019/build-config.private.json"
xcodebuild -workspace ios/Runner.xcworkspace -scheme Runner -configuration Release -destination "id=<device-id>" -allowProvisioningUpdates -allowProvisioningDeviceRegistration -quiet BUILD_DIR="$PWD/build/ios" DEVELOPMENT_TEAM="<Team-ID>" CODE_SIGN_STYLE=Automatic
test -f build/ios/Release-iphoneos/Runner.app/Frameworks/App.framework/flutter_assets/assets/certificates/local-test-ca.pem
cmp assets/certificates/local-test-ca.pem build/ios/Release-iphoneos/Runner.app/Frameworks/App.framework/flutter_assets/assets/certificates/local-test-ca.pem
codesign --verify --deep --strict build/ios/Release-iphoneos/Runner.app
xcrun devicectl device install app --device <device-id> build/ios/Release-iphoneos/Runner.app
```

Thay `<device-id>` bằng ID của iPhone trong danh sách, `<Team-ID>` bằng Team đã ký bản đang cài để giữ cùng định danh ứng dụng. Lệnh trên dùng staging và cấu hình riêng có sẵn trên Mac này. Trên Xcode 27 của Mac này, bundle tạo ở `Release-iphoneos`; dùng lệnh cài trực tiếp phía trên để tránh lỗi Flutter tìm nhầm `build/ios/iphoneos/Runner.app`. Nếu iPhone yêu cầu tin cậy nhà phát triển, xác nhận tài khoản ký tại **Cài đặt chung → VPN & Quản lý thiết bị** rồi mở lại.

Kết quả cần kiểm tra trên điện thoại:

1. Có biểu tượng **CareFlow** trên Màn hình chính hoặc Thư viện ứng dụng.
2. Rút cáp, đóng app rồi chạm biểu tượng để mở lại. Bản Release vẫn mở được; Mac và backend vẫn cần chạy để dùng API.
3. Cho phép truy cập mạng cục bộ khi được hỏi. Đăng nhập bằng tài khoản test tại [trang tài khoản](01-tai-khoan-va-du-lieu.md), kiểm tra tải hồ sơ và lịch hẹn thật.
4. Kiểm tra riêng quyền camera/vị trí khi vào tính năng tương ứng. Chưa đánh dấu QR/GPS hoặc toàn bộ hành trình đạt nếu chưa thao tác trên iPhone thật.

Apple Personal Team miễn phí có provisioning profile hết hạn sau 7 ngày. Khi app không còn mở được do hết hạn, kết nối lại với Mac và chạy lại lệnh Release để ký/cài lại. Không cần xóa app để bắt đầu bước này.

## Mở lại cầu nối sau khi dừng hoặc khởi động lại Mac

Khởi động backend trước, bảo đảm iPhone cùng Wi-Fi, tên `MacBook-Pro-cua-DangKhoii.local` vẫn đúng và cổng `18443`/`18444` chưa có tiến trình khác. Trong Terminal riêng chạy:

```sh
node ~/Library/Developer/CareFlow/iphone11pm-20261018/bridge.mjs
```

Giữ Terminal mở trong lúc test; `Ctrl+C` dừng cầu nối của phiên đó. Nếu báo cổng đã dùng, kiểm tra tiến trình hiện có trước khi chạy thêm. Các file build, khóa TLS và cấu hình runtime nằm ngoài Git; clone repo không tự có bộ cài này.

Nguồn: [Flutter — thiết lập thiết bị iOS](https://docs.flutter.dev/platform-integration/ios/setup), [Apple — giới hạn Personal Team](https://developer.apple.com/help/account/basics/about-your-developer-account).
