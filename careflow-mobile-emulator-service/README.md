# CareFlow Mobile Emulator Service

Service giả lập nhiều điện thoại, dùng snapshot **Flutter Patient Mobile** từ `frontend/patient-mobile` (`develop@8bf77e2`) với adapter trình duyệt. Mã nguồn nằm trong `careflow-mobile-emulator-service/`, theo quy ước tên service của CareFlow. Đây là service Node.js phục vụ Flutter Web và proxy API/WebSocket thật đến Gateway; chạy riêng bằng npm, không thuộc các module Maven Java.

Thư mục trước đây mang tên `gia_lap_mobile`. Khi chuyển vào repository, giữ nguyên source, bộ dữ liệu cục bộ và các ca kiểm thử. Thay đổi ở service này không tự cập nhật snapshot `frontend/patient-mobile` hay các service backend.

## Chạy ngay

Cần Node.js 20 trở lên. Trên máy hiện tại đã có bản Flutter Web tại `app/build/web`, có thể mở ngay. Khi clone Git trên máy mới, cần Flutter 3.41+ để build trước; thư mục build và cache không được đưa lên Git.

```sh
cd careflow-mobile-emulator-service
npm start
```

Mở **http://localhost:3000** (dùng đúng hostname `localhost` để khớp allowed origins của cả Gateway và Notification). Có thể nhấp đúp `start.command` trên macOS.

Gateway mặc định là **`http://127.0.0.1:18080`**, môi trường audit riêng đang chạy trên máy này. Cổng `8080` hiện không có Gateway lắng nghe, nên cấu hình cũ không kết nối được. Ứng dụng luôn gửi request thật qua proxy của giả lập; không cần thiết bị hoặc Android emulator.

Nhấn **Gateway / Kết nối hệ thống** ở góc trên, nhập Gateway khác rồi chọn **Lưu và kết nối**. Không tự chuyển sang máy chủ khác khi kết nối thất bại. Đổi Gateway sẽ chuyển sang namespace phiên riêng theo môi trường; token của Gateway cũ không được nạp cho Gateway mới. Khởi động lại bằng `npm start` hoặc `start.command` dùng cổng `18080`, trừ khi đặt biến môi trường bên dưới.

Nếu muốn chọn Gateway từ Terminal:

```sh
CAREFLOW_GATEWAY=http://127.0.0.1:18080 npm start
```

Nếu cổng 3000 đã dùng, chạy `PORT=5173 npm start` và mở `http://localhost:5173`. Gateway hiện chỉ cho phép một số origin, nên cần cấu hình allowed origins tại **môi trường backend test riêng** khi đổi cổng. Notification hiện cho `http://localhost:5173`; Gateway trên snapshot chỉ có `http://localhost:3000`, `http://127.0.0.1:3000` và domain demo. Không chỉnh source backend/nhánh khác chỉ để chạy giả lập này.

## Dùng nhiều điện thoại cùng lúc

Mở `http://localhost:3000` rồi đăng nhập tài khoản PATIENT khác nhau trên từng máy. Không cần điện thoại vật lý. Đây là nhiều phiên Flutter Web trên một workspace; bộ nhớ và phiên độc lập, cùng gọi Gateway đã chọn.

- **Thêm điện thoại**: tạo máy có phiên mới. Các máy cũ tiếp tục hoạt động.
- Nhấp tên để đổi nhãn máy; bấm **Phóng to** để thao tác lớn hơn, **Gỡ** để đóng máy. Các nút Quay lại, Trang chủ, Tải lại, Tạm dừng và Đăng xuất nằm ngay trên màn hình máy.
- **Tạm dừng** giải phóng ứng dụng đang chạy, giữ phiên để tiếp tục sau.
- Checkbox trên máy chọn đối tượng cho các nút **Trang chủ / Lịch khám / Tải lại** ở thanh phía trên. Nhấp/chạm, nhập văn bản trên một máy vẫn chỉ áp dụng máy đó.
- **Nhật ký API** mở bảng theo dõi; chọn tên máy để lọc request của máy đó.
- Tất cả máy dùng **API THẬT**. Cấu hình Demo cũ được chuyển về API thật khi tải workspace.

## Tài khoản và ca mẫu

Đã tạo 3 ca trên DB audit riêng: `mobile_case01` (chưa đặt lịch), `mobile_case02` (đã đặt lịch/có phiếu), `mobile_case03` (đã hủy lịch). Mật khẩu chung **`Password123@`**. Tài khoản và hướng dẫn hiển thị dưới từng điện thoại; xem [dataset và cách kiểm tra](docs/MOBILE-DATASET.md).

Chạy `npm run dataset:seed -- --audit-db` để tạo/kiểm tra dữ liệu; chạy `npm run test:dataset` để kiểm tra cả ba điện thoại bằng Chrome và API thật, không chặn request hoặc dùng fixture.

Namespace tách trạng thái ứng dụng trong cùng trình duyệt, không phải sandbox bảo mật cho người dùng máy tính khác nhau. Nếu dùng **cùng một tài khoản** trên nhiều máy, chính sách phiên của Identity có thể thu hồi phiên cũ; dùng nhiều tài khoản test khác nhau để kiểm thử đồng thời.

## Các chức năng

- Giao diện Flutter và luồng điều hướng gốc: onboarding, đăng nhập/đăng ký, trang chủ, nhiều hồ sơ bệnh nhân, hồ sơ sức khỏe, đặt/hủy lịch khám, phiếu khám, check-in, hàng đợi, khám/xét nghiệm/toa thuốc, quyết toán và thông báo.
- **Chỉ dùng API thật**: không có lựa chọn Demo. URL và cờ build cũ không thể bật hành trình hay đăng nhập mô phỏng. Dữ liệu được lấy/ghi qua đúng Gateway và Bearer JWT của mobile; quyền sở hữu và phân quyền do backend kiểm tra.
- Phí khám chỉ hiển thị khi có contract giá từ backend; bản này không lấy giá mẫu 150.000 VND làm phí thật và không tạo biên lai cục bộ. Các ca kiểm thử đọc trạng thái từ API thật.
- Mặc định mở 3 điện thoại, thêm tối đa 8 máy; mỗi màn hình Flutter có viewport 390 × 780. Thanh **Cỡ khung** chỉnh toàn bộ ô thiết bị từ **50–100%**, mặc định **75%**, gồm khung, nút và màn hình. Bố cục tự động xếp các ô sát nhau theo kích thước đã chọn; có thể chọn 2/3 cột hoặc phóng to một máy. Mức thu nhỏ được lưu và không tải lại phiên.
- JWT/refresh token mã hóa và SharedPreferences tách theo thiết bị **và Gateway**. Đăng xuất/tải lại một máy không tác động máy khác. Bố cục và các phiên được giữ sau khi tải lại workspace; gỡ máy chỉ xóa dữ liệu cục bộ của máy đó.
- Thao tác trực tiếp từng điện thoại; chọn các máy để gửi lệnh Trang chủ, Lịch khám hoặc tải lại. Nhật ký API lọc theo máy và không ghi body/token/mật khẩu.
- Thông báo thật qua STOMP `/ws/notifications` → `/user/queue/notifications`, xác thực JWT trong `CONNECT`; inbox được tải lại khi có tin và mỗi 15 giây khi shell đang mở. Không có FCM/native push ở bản web này.
- Tải tệp hồ sơ sức khỏe bằng file picker của trình duyệt, truyền multipart giữ nguyên field `request` / `files`.
- **Tài khoản → Xác minh eKYC** là điểm vào bổ sung để thành viên A kiểm thử API sẵn có `POST /api/auth/ekyc` với field `image`. Kết quả `mock=true` được ghi rõ.
- **Check-in tại bệnh viện** thay thao tác camera bằng dán **nội dung QR thực**, lấy GPS trình duyệt hoặc nhập latitude/longitude/accuracy. Vẫn gửi đúng `/api/queues/check-in`; QR hết hạn, sai bệnh nhân, ngoài geofence, accuracy không đạt đều do backend từ chối. Không có cơ chế bỏ qua kiểm tra.

Màu, bố cục và các màn hình nghiệp vụ được giữ từ Flutter. Font Be Vietnam Pro cần tải từ Internet được thay bằng Roboto đóng gói cục bộ để app không phụ thuộc Google Fonts/CDN. Đây là Flutter Web với adapter thiết bị, không phải Android emulator.

## Kiểm thử phần A

1. Khởi động backend test của bạn (Gateway, Identity, Patient, Appointment, Queue, Notification cùng các dependency cần thiết). `npm start` chỉ chạy frontend/proxy, không khởi động/reset/seed backend hoặc database.
2. Kết nối Gateway. Đăng nhập bằng tài khoản `PATIENT` của môi trường test. Seed mặc định trong source là username `patient`, password `Password123@`; chỉ đúng nếu seed chưa được đổi.
3. Tạo/chọn hồ sơ bệnh nhân, đặt lịch bằng khoa/slot thật từ API. Mở chi tiết → phiếu khám.
4. Lấy QR đang hiệu lực từ Hospital Web bằng tài khoản STAFF/ADMIN đúng quyền; dán **giá trị QR**, không phải appointment QR hoặc ID phiếu. Nhập tọa độ test hợp lệ theo cấu hình server.
5. Snapshot Queue mặc định có tâm `10.7769, 106.7009`, bán kính `150m`; cấu hình runtime có thể khác. Chỉ dùng giá trị này nếu đúng backend test của bạn. Có thể kiểm thử ca ngoài vùng/sai accuracy bằng thay đổi tọa độ.
6. Từ Hospital/Doctor Web test, gọi bệnh nhân để kiểm tra hàng đợi và thông báo. Web giả lập không giả danh bác sĩ hoặc tự cấp quyền nhân viên. Màn hình hàng đợi gốc cũng tự cập nhật mỗi 20 giây.
7. Trong tab Thông báo, kiểm tra inbox/đọc tin; ở Tài khoản, thử eKYC và đăng xuất. Đăng nhập tài khoản khác để xác nhận dữ liệu được tách theo tài khoản/hồ sơ.

Thao tác ở **API thật** có ghi dữ liệu vào Gateway bạn đã chọn. Frontend không sửa Git, nhưng đặt lịch/check-in/eKYC vẫn là thao tác thật trên môi trường backend đó.

## Sửa source và build lại

Đã chuẩn bị SDK và pub cache riêng để không ghi vào Flutter SDK gốc:

```sh
# Chỉ cần nếu SDK tạm chưa có; truyền đường dẫn SDK Flutter đã cài:
./scripts/prepare-sdk.sh /duong/dan/flutter

# Lần đầu sau khi clone, tải các package trước khi build offline:
(cd app && ../scripts/flutter.sh pub get)

npm run build
npm start
```

Có thể dùng SDK sẵn có bằng `CAREFLOW_FLUTTER_SDK=/duong/dan/flutter`; `CAREFLOW_PUB_CACHE` thay pub cache. Build dùng `flutter pub get --offline` vì các gói cần thiết đã có trong `.runtime/pub-cache`. Trên máy mới, chạy `scripts/flutter.sh pub get` trong thư mục `app` khi có Internet trước khi build.

```sh
npm test                         # test handler/proxy và adapter trình duyệt, không cần bind cổng
npm run test:integration          # HTTP/WebSocket với cổng loopback thật
npm run test:browser              # Chrome/Playwright: nhiều máy, fixture API riêng; không ghi CareFlow DB
cd app
../scripts/flutter.sh analyze --no-pub
../scripts/flutter.sh test --no-pub
```

Các báo cáo trong `docs/` lưu cả kết quả lịch sử trước khi chuyển thư mục; đường dẫn `gia_lap_mobile` trong phần lịch sử là tên cũ. `.runtime/`, `app/build/`, `app/.dart_tool/`, file môi trường và cache được Git bỏ qua. Dataset cục bộ vẫn được giữ trên máy; người clone mới tạo dataset thật bằng `npm run dataset:seed -- --audit-db` trên môi trường audit đúng cấu hình. Không đưa token, cấu hình DB hay state của môi trường audit lên Git. Các script audit thành viên A cần môi trường backend và công cụ audit riêng được mô tả trong báo cáo; clone service không tự tạo môi trường đó.

Kiểm thử trình duyệt cần Playwright (cài riêng bằng `npm install --no-save --package-lock=false playwright`) và Chrome; có thể đặt `PLAYWRIGHT_MODULE` và `CHROME_PATH` để dùng bản đã cài.

Chi tiết contract: [docs/API-MAP.md](docs/API-MAP.md). Phạm vi thay đổi và xác minh: [docs/VERIFICATION.md](docs/VERIFICATION.md).

## Kiểm tra API audit qua chính giả lập

Gateway audit hiện ở `127.0.0.1:18080`; giả lập đang phục vụ tại **http://localhost:3000**. Đăng nhập trên điện thoại trong trang bằng tài khoản seed `patient` / `Password123@` nếu seed chưa đổi. Ứng dụng luôn dùng API thật. Không cần mở cổng 8080, cài điện thoại hoặc Android emulator.

Có thể kiểm tra tự động đường đi `giả lập:3000 → Gateway:18080 → service → DB audit`:

```sh
npm run test:api -- --audit-db
```

Lệnh chỉ chấp nhận giả lập đang trỏ đúng Gateway audit loopback ở trên. Nó tạo tài khoản/hồ sơ giả, chọn ca khám còn chỗ ngày mai từ API, đặt lịch và chờ Queue cấp phiếu bằng event thật, kiểm tra QR sai/ngày sai, hủy lịch và thu hồi các session đã tạo. Tài khoản/hồ sơ test được giữ trong DB audit; lịch test được hủy. Đây là thao tác API thật, không phải backend fixture của `test:integration`.

Kết quả chỉ gồm tên ca kiểm tra/mã HTTP, lưu ở `.runtime/live-api-results.json`; không ghi token, mật khẩu, body bệnh án hoặc QR. Mật khẩu tài khoản test có thể đặt bằng `CAREFLOW_AUDIT_PASSWORD`. Check-in thành công cần lịch đúng ngày, QR bệnh viện còn hạn và vị trí hợp lệ. Kiểm tra đặt lịch ngày mai không được dùng làm bằng chứng đã check-in thành công hôm nay.

Notification/inbox và WebSocket hiện chạy thật trên môi trường audit. Bốn lỗi thành viên A đã sửa và nghiệm thu lại; xem [kết quả và cách chạy lại](docs/MEMBER-A-REPAIR.md). Kiểm tra toàn bộ backend A: `npm run test:member-a -- --audit-db`. eKYC thật, FCM và toàn bộ hành trình lâm sàng chưa được coi là hoàn tất.
