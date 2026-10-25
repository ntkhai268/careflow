# Phạm vi và kết quả xác minh

Kết quả mới nhất: [MEMBER-A-REPAIR.md](MEMBER-A-REPAIR.md). Bốn vấn đề HIGH đã sửa và nghiệm thu lại bằng API/broker/DB thật; xem [báo cáo trước sửa](MEMBER-A-AUDIT.md) để đối chiếu. Các ghi chú "Notification chưa chạy" và kết quả kiểm tra cũ bên dưới giữ lại làm lịch sử.

Ngày thực hiện: 2026-10-15. Nguồn `careflow`: `develop@8bf77e2ffb6ab85fe53cb43b959188191e965084`.

## Mã nguồn và nhánh

- Không sửa file, checkout/pull/merge, add/commit hoặc push trong `/Users/dangkhoii/careflow` hay `.worktrees`.
- Kiểm tra `git status --short --branch` trước/sau cho cùng trạng thái: `develop`, 3 file doctor-web đã sửa từ trước và các tài liệu chưa được track sẵn có. Không xuất hiện thay đổi mới từ tác vụ này.
- Mọi mã giả lập ở `/Users/dangkhoii/gia_lap_mobile`; đây là thư mục độc lập, không phải nhánh tích hợp vào CareFlow. Không khẳng định đã chuyển sang nhánh `dangkhoii` (repository gốc có nhiều nhánh `feature/dangkhoii/*`).
- `app/lib` giữ nguyên 65 file Dart từ mobile. Các file thêm/thích nghi được liệt kê tại `source-snapshot.json`.
- SDK Flutter và pub cache được sao chép riêng để công cụ không ghi cache vào SDK gốc. Build/run scripts không gọi Git trong CareFlow.

## Các thay đổi trong bản sao

- API base URL đi qua proxy cùng origin, Gateway local là mặc định; không fallback sang production.
- Khung điện thoại và bảng kết nối/hoạt động API bên ngoài Flutter.
- Bật demo bằng query của iframe thay vì yêu cầu build lại. Mặc định API thật.
- Adapter browser cho file bytes, GPS và QR token nhập bằng tay.
- Thông báo STOMP có JWT và inbox polling; không giả lập FCM device/push.
- eKYC bổ sung điểm vào cho API Identity hiện có, tôn trọng cờ `mock` của backend.
- Roboto được bundle để không tải font từ Google/CDN. CanvasKit cũng được bundle (`--no-web-resources-cdn`).

## Đã kiểm tra

- `flutter analyze --no-pub`: **No issues found**.
- `flutter build web --release`: **thành công**, build JavaScript, base `/mobile/`, tài nguyên CanvasKit cục bộ.
- `npm test`: **18/18 đạt**; thực thi handler thật và stream proxy trong bộ nhớ, không bind cổng. Bao gồm bảo toàn JWT/body/query/multipart boundary; không chuyển header giả danh; giữ 403; thông báo 502; chặn truy cập source/env; đổi Gateway; che thông tin trong activity; STOMP CONNECT/SUBSCRIBE/message/rotation/reconnect/heartbeat; GPS; chọn/hủy tệp và tính toàn vẹn bytes.
- Node syntax và shell syntax checks: đạt.
- Không thực hiện thao tác ghi API/database khi kiểm tra.

## Giới hạn kiểm thử trong phiên này

- Gateway `http://127.0.0.1:8080/actuator/health` chưa có dịch vụ lắng nghe khi kiểm tra ban đầu.
- Sandbox từ chối mở server socket (`listen EPERM`), nên không thể duy trì preview local hoặc chạy HTTP/WebSocket integration qua cổng thật.
- `flutter test` cũng cần server socket giữa test runner và Flutter tester; 37 test file không khởi chạy được vì cùng lỗi `Operation not permitted`. Không tính đây là test đã đạt và chưa kết luận regression UI.
- Chưa kiểm tra trực quan trên trình duyệt hoặc end-to-end với backend thật. Chạy `npm start`, `npm run test:integration` và `flutter test` từ Terminal ngoài sandbox để xác minh các phần này. Bộ integration ở `tests/integration/server.integration.mjs` dùng backend fixture cục bộ, không ghi dữ liệu CareFlow.
- Native push, camera scan và môi trường Android/iOS không nằm trong khả năng mô phỏng của bản Flutter Web này. QR được dán trực tiếp, vị trí có lựa chọn browser/nhập test; backend vẫn quyết định kết quả.

## Cập nhật sau khi cấp quyền mở cổng

- Server đã chạy tại `http://localhost:3000`; trang chính và `/mobile/` đều trả HTTP 200.
- Kiểm tra bằng Chromium ở kích thước 1440 × 1050: Flutter render thành công, chuyển đến onboarding, không có lỗi JavaScript (`pageerror`). Ảnh kiểm tra lưu tại `.runtime/preview.png`.
- `npm run test:integration`: **9/9 đạt**, bao gồm upgrade WebSocket và truyền STOMP bytes qua cổng loopback thật. Đã sửa phần teardown fixture để đóng cả socket upgraded khi kết thúc bài test.
- Gateway local `127.0.0.1:8080` vẫn chưa kết nối được; chưa xác nhận nghiệp vụ với backend thật. Giới hạn sandbox nêu trên là trạng thái phiên kiểm tra ban đầu, không còn chặn preview/HTTP integration sau khi cấp quyền.

## Kết nối API thật qua giả lập — cập nhật 2026-10-15

Phần này thay thế kết luận cũ rằng chưa kết nối backend. Lỗi do giả lập chọn `127.0.0.1:8080` trong khi Gateway audit thực tế lắng nghe ở `127.0.0.1:18080`. Đã đổi mặc định trong `server.mjs`, placeholder và hướng dẫn sang 18080; vẫn cho ghi đè bằng `CAREFLOW_GATEWAY` hoặc bảng kết nối. Không tự dò/fallback sang server khác. Server giả lập đã được khởi động lại trên loopback cổng 3000.

- `/` và `/mobile/` trả 200; `/__emulator/config` chọn Gateway 18080; `/__emulator/health` báo reachable=true, status=200. Đây là kiểm tra Gateway thật, không chỉ file web.
- `npm test`: 18/18 đạt; `npm run test:integration`: 9/9 đạt. Bản Flutter đã build được giữ nguyên vì không thay đổi Dart.
- `npm run test:api -- --audit-db`: 23/23 kiểm tra HTTP qua proxy giả lập đạt, gửi Origin `http://localhost:3000`. Có đăng ký/đăng nhập thật, tài khoản hiện tại, hồ sơ nhiều người, khoa/ca còn chỗ, đặt lịch idempotent, nhận ticket từ event thật, QR từ ADMIN thật, check-in bị từ chối đúng khi QR sai/ngày sai, phiếu không đổi trạng thái, hủy lịch và session bị thu hồi. Báo cáo an toàn `.runtime/live-api-results.json`. Các lần poll chờ ticket là request bổ sung, không tính thành 23 assertion riêng.
- Ca test lấy slot ngày mai từ API vì thời điểm kiểm tra đã ngoài ca khám hôm nay; không tự tạo ca ngoài danh mục, sửa ngày DB hoặc giả check-in thành công. Happy path check-in đúng ngày/geofence chưa được chứng minh trong đợt này.
- Tài khoản/hồ sơ giả của smoke được giữ riêng trong DB audit; lịch đã hủy và session test đã thu hồi. Không truy cập/ghi DB remote chung và không sửa/reset/seed backend.
- Mọi thay đổi source thuộc `/Users/dangkhoii/gia_lap_mobile`. Không sửa source, Git index/commit/branch/worktree của CareFlow. Notification/AI chưa chạy trong audit; không tuyên bố toàn hệ thống hoàn tất.
- Đã đăng nhập từ giao diện Flutter trên Chrome headless bằng seed PATIENT: API `/auth/login` 200, hồ sơ 200, lịch hẹn 200 và điều hướng về trang chủ; không có JavaScript page error. Báo cáo `.runtime/browser-api-results.json`, ảnh `.runtime/patient-api-connected.png`. Inbox trả 404 đúng với việc Notification chưa chạy; không tính đây là chức năng đã đạt. Browser context độc lập, session đăng nhập thành công đã được thu hồi sau kiểm tra.

## Sửa lỗi hành trình bị chặn bởi Notification — 2026-10-15

Ảnh người dùng gửi lúc 19:12 hiển thị lỗi chung “Kiểm tra kết nối”. Kiểm tra thực tế cho thấy Gateway UP, Patient/Appointment/Consultation trả 200; Notification inbox trả 404. `BackendJourneyRepository._load` chờ inbox như điều kiện bắt buộc, nên dữ liệu lịch khám đã tải thành công vẫn bị loại bỏ khi thông báo chưa chạy.

Đã sửa **chỉ bản sao giả lập**: lỗi Notification 404/5xx hoặc lỗi kết nối/timeout không làm hỏng việc tải lịch khám/hành trình. Giao diện Home giữ hành trình thật và hiện riêng “Thông báo hiện chưa khả dụng. Lịch khám của bạn vẫn được cập nhật.” Tab thông báo vẫn phản ánh lỗi thực tế. Lỗi 401/403 không bị nuốt; lỗi API lịch khám/khám vẫn làm tải hành trình thất bại. Lời nhắc lỗi tải lượt khám cũng không còn mặc định quy lỗi cho mạng.

- Test tái hiện trước sửa: 7 ca lỗi do Notification; 4 ca kiểm tra quyền/dữ liệu bắt buộc đã đạt.
- Sau sửa: 18 test repository/Home/shell đạt, gồm phục hồi Notification, quyền 401/403, lỗi Appointment/Consultation và cảnh báo riêng trên Home.
- Flutter analyzer: No issues found. Flutter Web build thành công; không thay đổi source/Git hoặc runtime backend CareFlow.
- Source snapshot cập nhật: 65 file Dart giữ nguyên, 18 file thích nghi/thêm trong giả lập. Không tạo dữ liệu hành trình/thông báo giả để che lỗi backend.

- Kiểm tra Chrome với API thật: đăng nhập PATIENT 200; hành trình đang khám vẫn hiển thị khi inbox tiếp tục trả 404, kèm cảnh báo riêng. Không có JavaScript page error. Báo cáo `.runtime/journey-notification-browser-results.json`; ảnh `.runtime/journey-notification-fixed.png`. Session kiểm tra đã đăng xuất sau khi xác minh.

## Workspace nhiều điện thoại — 2026-10-15

Đã thay khung một máy bằng workspace nhiều phiên Flutter Web. Mặc định 3 máy, tối đa 8; thêm/gỡ, đổi tên, tạm dừng/tiếp tục, phóng to một máy, thu phóng 60/78/90/100%, thao tác Trang chủ/Lịch khám/Tải lại theo checkbox và nhật ký API theo thiết bị. Thao tác gõ/chạm trực tiếp vẫn riêng từng máy. Bố cục/nhãn/chế độ được lưu trên trình duyệt.

- `WebOptions` của FlutterSecureStorage và prefix SharedPreferences đều tách theo `(storageScope của Gateway, deviceId)`. Khóa JWT/refresh token, khóa mã hóa và hành trình không dùng chung giữa các máy. URL thiếu một phần định danh không được rơi về phiên cũ. `/mobile/` độc lập không có tham số thiết bị vẫn tương thích namespace cũ.
- Marker `X-Emulator-Device` được gắn tại client để phân loại activity và bị xóa trước khi proxy tới Gateway. Header này không có quyền xác thực; JWT và các payload nghiệp vụ giữ nguyên.
- Parent controls chỉ nhận từ đúng cửa sổ cha, cùng origin, trong danh sách lệnh cho phép. Parent chỉ nhận trạng thái phiên/realtime/ready, không nhận token qua postMessage.
- `npm test`: **25/25 đạt**. `npm run test:integration`: **9/9 đạt**. Flutter analyzer **No issues found**. Flutter test toàn bộ **165 đạt, 1 skip**; 4 kiểm tra DeviceConfig (gồm namespace thiếu một phần) cũng đạt. Flutter Web release build thành công.
- `npm run test:browser`: **11/11 kịch bản đạt** trên Chrome. Kiểm tra 3 Flutter view cùng lúc ở 390 × 780; hai tài khoản khác nhau, encrypted token và API/profile không lẫn; thêm máy không remount máy cũ; pause/resume giữ phiên; lệnh chỉ tới máy được chọn; logout riêng; tải lại workspace giữ bố cục và phiên còn lại; gỡ máy không xóa khóa máy khác; lọc activity; focus ở viewport 390px không tràn ngang; không có uncaught JavaScript error.
- Browser test dùng API fixtures trong browser context riêng, không tạo hoặc thu hồi tài khoản/phiên thật trên CareFlow. Kết quả `.runtime/fleet-browser-results.json`; ảnh `.runtime/fleet-desktop.png`, `.runtime/fleet-mobile.png`. Đây là kiểm chứng cô lập phiên/giao diện, không phải phép thử backend hàng đợi đa bệnh nhân đầu cuối.
- Server đã cập nhật tại `http://localhost:3000`, Gateway giữ `http://127.0.0.1:18080` và health trả 200. Không sửa source, branch/index/worktree hoặc runtime backend của `careflow`.

Nếu dùng cùng một tài khoản cho nhiều máy, chính sách active-session của Identity có thể thu hồi phiên cũ. Kiểm thử song song nên dùng các tài khoản PATIENT khác nhau. Nhiều phiên cùng origin ở đây không phải sandbox bảo mật giữa những người sử dụng máy tính khác nhau; đây là workspace giả lập ứng dụng, không kết nối thiết bị Android/iOS vật lý.

## Dataset thật theo điện thoại — 2026-10-15

Đã tạo `mobile_case01`, `mobile_case02`, `mobile_case03` (PATIENT) cùng ba hồ sơ thật bằng API, giữ dữ liệu trong PostgreSQL audit riêng. Máy 01 chưa có lịch; máy 02 có lịch CONFIRMED và phiếu TICKET_ISSUED; máy 03 có lịch/phiếu CANCELLED sau khi hủy bằng API. Hai lịch có ngày 16/10/2026, 07:30–08:00, khoa Nội tổng quát. Không dùng SQL để dựng trạng thái, không mock/intercept API. Hướng dẫn và mật khẩu: [MOBILE-DATASET.md](MOBILE-DATASET.md).

- Lần tạo đầu: 46 request HTTP thật, gồm chờ sự kiện cấp/hủy phiếu và kiểm tra tài khoản khác không đọc được hồ sơ. Chạy lại: 25 request đạt. Đối chiếu DB sau hai lần vẫn đúng 3 user, 3 profile, 2 appointment, 2 queue entry; không có bản ghi trùng.
- `npm run test:dataset`: **7/7 đạt**, Chrome đăng nhập ba tài khoản thật đồng thời, đọc đúng hồ sơ theo từng máy, hiển thị lần lượt chưa có lịch/đã xác nhận/đã hủy. Không phát request lịch khám của bệnh nhân thuộc máy khác; không có uncaught JavaScript error. Kiểm tra này không dùng route interception hoặc API fixtures.
- `npm test`: **27/27 đạt**; `npm run test:integration`: **9/9 đạt**. Đây là kiểm tra kỹ thuật riêng, không dùng thay bằng chứng dữ liệu thật ở trên.
- Bàn điều khiển có phần **Tài khoản kiểm thử** dưới từng máy, luôn mở `demo=false` và chuyển cấu hình Demo đã lưu về API thật. Chọn ca trong phần tài khoản chỉ đổi hướng dẫn, không tự đăng nhập hoặc thay đổi dữ liệu nghiệp vụ.
- Bằng chứng: `.runtime/mobile-dataset-initial-verification.json`, `.runtime/mobile-dataset-verification.json`, `.runtime/dataset-db-verification.json`, `.runtime/dataset-browser-results.json`, ảnh `.runtime/dataset-real-phones.png`. Manifest `.runtime/mobile-dataset.json` chứa thông tin test/tham chiếu DB, không thay thế API nghiệp vụ và không chứa token.
- Chưa tạo hoặc tuyên bố đạt check-in thành công, khám/xét nghiệm/toa thuốc/quyết toán. Check-in phải đúng ngày khám và đủ QR/vị trí hợp lệ; Notification vẫn chưa chạy. Chỉ sửa thư mục giả lập, không sửa source hoặc Git của CareFlow, không ghi DB remote dùng chung.

## Cố định API thật và cải thiện giao diện — 2026-10-15

Yêu cầu mới thay thế chế độ trước: tất cả máy luôn gọi API thật. `demoModeProvider` cố định false, không đọc query/build flag; AuthService mặc định không đọc `USE_MOCK_AUTH`. Tương thích các test cũ bằng explicit provider/service injection trong test; các fixture không phải chế độ sử dụng của workspace. Cấu hình máy lưu Demo cũ được chuyển về real. Không còn nút hoặc bộ chọn Demo.

Giao diện bỏ sidebar, tăng chữ và nút điều khiển có nhãn, đưa Quay lại/Trang chủ/Tải lại/Tạm dừng/Đăng xuất lên trước màn hình, thêm bố cục Tự động/2 cột/3 cột và cỡ Vừa khung/80%/100%. Cấu hình Gateway nằm trong dialog ở góc trên; có hướng dẫn sử dụng. Phiên và bố cục giữ nguyên khi thay số cột. Tài khoản audit đã có được giữ trong mục thu gọn dưới từng máy. Không tự seed/reset backend.

Trong luồng đặt lịch API thật, bỏ giá mẫu 150.000 VND khỏi phần phí khám; hiển thị theo bảng giá bệnh viện cho đến khi backend có contract giá. Không sinh biên lai hoặc trạng thái thanh toán thành công cục bộ. Quy tắc backend, ownership, QR/geofence và quyền người dùng vẫn giữ nguyên.

Xác minh trên bản cuối:

- Flutter analyzer: **No issues found**. Flutter test: **168 đạt, 1 skip**. Hai test policy còn được chạy với `DEMO_MODE=true` và `USE_MOCK_AUTH=true` để xác nhận vẫn dùng BackendJourneyRepository, Queue thật và gửi login tới Identity.
- Flutter Web release build thành công; đã cập nhật artifact tại `app/build/web`.
- Node handler/bridge/layout: **27/27 đạt**. HTTP/WebSocket integration: **9/9 đạt**.
- Chrome fixture kiểm tra UI/cô lập phiên: **13/13 kịch bản đạt**, gồm không có nút Demo, chuyển 2/3 cột không remount máy, config/help dialog, thao tác nhóm đúng máy, logout riêng và mobile 390px không tràn ngang. Báo cáo `.runtime/fleet-browser-results.json`; ảnh `.runtime/fleet-desktop.png`, `.runtime/fleet-mobile.png`.
- Chrome với **API thật**, không chặn request: **7/7 kịch bản đạt** cho 3 tài khoản audit, hồ sơ đúng ownership và các lịch chưa đặt/CONFIRMED/CANCELLED. Sau kiểm tra đã thu hồi các session test. Dataset không bị tạo lại hoặc thay trạng thái lịch. Báo cáo `.runtime/dataset-browser-results.json`.
- Gateway 18080 hoạt động. Notification vẫn trả **404** trong môi trường audit; hiển thị lỗi/chưa khả dụng thực tế, không thay bằng thông báo mô phỏng. Queue current 404 khi hồ sơ chưa check-in là trạng thái chưa có hàng đợi; không suy ra service hỏng.
- Không tuyên bố đã hoàn tất luồng nhận thuốc/thanh toán online hoặc check-in thành công đúng ngày khi backend/time window chưa cung cấp điều kiện tương ứng. Những phần đó giữ thông tin/trạng thái thực tế từ hệ thống.
- Tất cả source/build/UI thay đổi ở `gia_lap_mobile`; `careflow` giữ nguyên trạng thái Git so với trước tác vụ. Source snapshot cập nhật: 63 file Dart giữ nguyên, 21 file thêm/thích nghi.

## Thu nhỏ toàn bộ ô thiết bị — 2026-10-15

Thanh **Cỡ khung** chỉnh toàn bộ `device-card` từ 50–100%, mặc định 75%. Ô bao ngoài giữ đúng chiều rộng/chiều cao sau thu nhỏ, không kéo giãn theo cột. Các nút điều khiển và màn hình cùng thu phóng; iframe vẫn có viewport 390 × 780. Kích thước được lưu khi tải lại, không remount máy hoặc đổi phiên. Dùng transform trên cả card và theo dõi chiều cao thực tế, tránh lỗi vùng bấm của CSS zoom với Flutter Web.

Kiểm tra lại trên bản đang phục vụ tại localhost:3000: Node **28/28**, HTTP/WebSocket integration **9/9**, Chrome kiểm tra giao diện và cô lập phiên **14/14**; Chrome dùng API thật **7/7** với ba tài khoản audit. Đã kiểm tra trực quan desktop/mobile, tỉ lệ cả chiều rộng và chiều cao ở 50% so với 100%, giữ viewport, giữ phiên khi đổi kích thước/tải lại, thao tác đăng nhập và không tràn ngang ở 390px. Gateway health 200. Không thay đổi Flutter hoặc source/nhánh CareFlow trong đợt chỉnh khung này.

Bộ Chrome giao diện dùng fixture trong context riêng; bộ dataset đăng nhập và đọc hồ sơ/lịch khám qua API thật, không seed hoặc sửa lịch. Không suy rộng kết quả thành toàn bộ nghiệp vụ đã đạt: Notification vẫn trả 404 trong môi trường audit.
