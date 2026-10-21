# Nghiệm thu đọc giấy tờ trên máy và đối chiếu trực tiếp — 19/10/2026

## Kết quả

Theo yêu cầu người dùng, đã **ngừng VNPT**, thay bằng đọc giấy tờ trên điện thoại và nhân viên đối chiếu căn cước bản gốc. Bản iPhone **1.0.0(15)** đã build/ký/cài đè/mở trên iPhone11ProMax; Android debug APK build đạt. API và web thực tế đã qua kiểm thử với thông tin giả TEST PERSON, không ảnh/CCCD người thật. Chưa được người dùng xác nhận camera/OCR trên iPhone và quy trình đối chiếu người thật ngoài đời.

Không xem đây là eKYC tự động hoàn toàn, không kết luận giấy tờ thật từ OCR/QR, không tự liên kết hồ sơ hoặc cấp quyền bệnh án. Face ID đăng nhập không thay thế đối chiếu danh tính.

Backend/web ở worktree `security-integration`, nhánh `feature/dangkhoii/security-integration`, nền5a8c6e5; native ở `patient-mobile-ios-login`, nhánh `feature/dangkhoii/patient-mobile-ios-login`, nềne5fc9c1. Thay đổi chưa commit/push. Đã pull origin/main trên cả hai nhánh trước triển khai, up to date. Runtime test trên Mac/DB loopback riêng; không triển khai production/DB remote. AI tiếp tục để sau.

## Những phần thay đổi

- Xóa package adapter VNPT và các bài kiểm thử adapter đã ngừng; clean Maven build để không sót class cũ trong jar. Thay API cũ bằng410, không nhận tham số ảnh/gọi provider. Tắt hai cờ VNPT và loại ba giá trị kết nối khỏi environment/JSON của cả hai Identity trước khi thay luồng. File key VNPT riêng ngoài Git giữ nguyên, không tự gọi mua/nâng gói/xóa tài khoản provider.
- iOS dùng Apple Vision text/QR, Android dùng model Latin OCR đóng gói. Flutter chỉ đưa bytes ảnh qua platform channel; không có request ảnh/selfie, không cloud OCR. Ba ô thông tin cho kiểm tra/sửa tay, chỉnh sửa bỏ tích đồng ý. Dữ liệu thiếu/sai không được tự coi đã xác minh.
- API `/api/auth/identity-document` tiếp nhận ba trường JSON, lưu PENDING_REVIEW. Bệnh nhân chỉ xem summary của mình; không có quyền duyệt hoặc đọc thông tin chờ của người khác.
- Web STAFF/ADMIN có menu/hàng chờ25 mục/trang, xem chi tiết có quyền, ba điều kiện bản gốc/người xuất trình/thông tin khớp, mã quầy/biên bản và quyết định. Người duyệt/thời điểm được ghi lại; tự duyệt và duyệt lặp bị chặn.
- V17/DBML bổ sung `identity.document_checks`. Payload mã hóa bằng khóa riêng, HMAC chống dò fingerprint, row-AAD chống tráo ciphertext. Mã email/outbox/Resend không thay đổi.
- Payload đầy đủ bị xóa sau xác nhận/từ chối/hủy; quá7ngày không được xem/duyệt, cleanup mỗi giờ xóa vật lý payload quá hạn. Summary confirmed còn số đã che và nhật ký; không giữ ảnh hay tự sửa bảng patient/users.

Không seed thông tin đồng ý/trạng thái xác nhận giả: đây là bằng chứng bảo mật phát sinh theo yêu cầu, tương tự challenge đăng ký/reset. Dữ liệu kiểm thử tổng hợp được tạo rõ trong test, không giả làm người đã được đối chiếu thật.

## Kiểm thử

| Phạm vi | Kết quả |
|---|---|
| Maven clean package Common/Gateway/Identity | **222 đạt** (46+35+141),0 lỗi/0 bỏ qua |
| Kiểm thử mới về document |14 trong222:10 PostgreSQL/encryption/concurrency/retention,4 authorization/retired API |
| Flutter full suite cuối |225 đạt,15 bỏ qua có điều kiện |
| Flutter HTTP fixture chạy riêng |14 đạt: refresh3, recovery4, download1, biometric4, document2 |
| Tổng Flutter thực thi |**239 đạt**; còn1 test demo cũ bỏ qua;14 fixture đã chạy riêng |
| Widget/parser local |7 đạt, đã nằm trong full suite; sửa thông tin bỏ consent, pending không tự confirmed, hủy và replay |
| HTTP local-document |Chỉ đúng ba trường + consent/JSON + DPoP + request key, không ảnh/selfie/OCR text/provider key; hủy không gửi thông tin giấy tờ |
| TypeScript và Next production build |Đạt; hai route identity xuất hiện trong build; không dùng native select |
| Native builds |iOS Release15 và Android debug đạt; iOS codesign strict đạt, cài đè và launch thành công |
| Flutter analyzer |0 lỗi/0 warning;2 info curly-brace có trước ở test khác, không thêm info trong code mới |
| Diff whitespace |Hai worktree `git diff --check` đạt |

Số test backend giảm so với bản14 vì39 bài riêng cho adapter VNPT đã bị xóa cùng adapter, thêm14 bài về quy trình mới; không phải vô hiệu hóa bài thất bại. Một test demo cũ bị bỏ qua liên quan thanh toán quầy thuốc theo mô hình cũ, có bài settlement thay thế đạt; không phải test bảo mật hoặc camera đã được miễn nghiệm thu.

## Kiểm thử Gateway + hai Identity + web thật

Dùng hai tài khoản demo riêng trong trang01, không dùng tài khoản đang mở trên điện thoại. Chỉ tạo hai bản ghi tổng hợp rồi xóa đúng những ID đó trong finally; không thay mật khẩu/role/Face ID/hồ sơ của tài khoản nào. Một quyết định giả lập đối chiếu tại quầy chỉ nhằm test luồng, không khẳng định danh tính người thật.

1. Hai API VNPT cũ trả410; API mới available=true. Bộ đếm provider trước4, sau4, tăng0.
2. Bệnh nhân gửi thông tin TEST PERSON → PENDING_REVIEW; DB lưu ciphertext, không chứa chuỗi họ tên plaintext. Cùng key/nội dung trả cùng ID, đổi nội dung/key cũ409.
3. Bệnh nhân đọc hàng chờ/chi tiết403; tài khoản khác hủy409 và không thấy kết quả của nhau. Nhân viên xác nhận thiếu một điều kiện400.
4. Đăng nhập ADMIN trên web thực tế → mở trang identity → chọn đúng hàng → thấy ba trường tổng hợp; chưa đủ ba checkbox thì nút xác nhận disabled. Đủ ba điều kiện/mã đối chiếu, POST qua Gateway200 và hàng rời danh sách. Đã quan sát bố cục bằng ảnh chụp chỉ chứa dữ liệu tổng hợp ngoài Git.
5. Mobile/API cùng tài khoản đọc CONFIRMED từ Gateway và cả hai Identity. DB đã NULL payload, có reviewer/mã tham chiếu; quyết định lặp409.
6. Tài khoản còn lại gửi rồi hủy, trạng thái WITHDRAWN và payload NULL. Dọn đúng hai bản ghi kiểm thử, không ảnh người thật.

Các bài PostgreSQL riêng kiểm tra hai reviewer cạnh tranh chỉ một người thành công, advisory-lock idempotency, quota3/24h, AES nonce khác nhau, tráo row/tamper bị từ chối, expired không đọc/duyệt và cleanup xóa payload. Đây không thay thế đối chiếu bản gốc ngoài đời.

## Runtime và dữ liệu riêng

- Sao lưu schema identity trước V17 ngoài Git, không đụng DB remote. Hai instance đã áp dụng V17 và health200 qua TLS/mTLS; cầu nối iPhone200 với xác minh CA.
- Kiểm tra cả hai jar đang chạy không có class adapter `identity/ekyc`, có controller document mới. Hai environment/JSON không còn giá trị token VNPT; cờ disabled. Không phát sinh thêm verification request VNPT trong toàn bộ kiểm thử mới.
- Khóa mã hóa document32byte riêng được tạo ngoài Git, quyền0600, dùng chung đúng hai instance. Không ghi key, token, mã OTP, email cá nhân hoặc ảnh thật trong báo cáo/Git. Outbox và Resend settings đối chiếu không đổi; lượt này không gửi email.
- Giữ bundle/data iPhone; nguồn AppDelegate/Dart từ worktree native, entrypoint `lib/main.dart`, build15. Đã cài/mở sau build cuối, không cài bản chẩn đoán thay app chính.
- Dữ liệu đã gửi lên VNPT trước khi ngừng không được thu hồi bằng việc xóa adapter; chưa xác nhận provider đã xóa. Không tái gửi ảnh thật để chẩn đoán hoặc huấn luyện.

## GitNexus và phạm vi rủi ro

Đã query/context luồng liên quan và chạy upstream impact cho các class/method production bị thay hoặc bỏ, menu và điểm đăng ký native. Kết quả đã nhận diện chủ yếu LOW (1–4 phụ thuộc trực tiếp); một số constructor/điểm native/React không có caller resolve là UNKNOWN, không coi đó là an toàn mặc định. Có đọc nguồn, build native và kiểm thử luồng/role để bù phạm vi graph.

Đã index lại hai worktree bằng `--force --no-parse-cache --skip-agents-md --skip-skills` và chạy detect-changes cho toàn bộ diff. Diff bao gồm nhiều đợt trước, backend **CRITICAL**, native **HIGH**; đây không phải chỉ lượng thay đổi của document-review. Có hạn chế cross-language/flow truncation. Các file mới untracked cũng được đọc/build/test, không dựa riêng vào detect-changes. Không add/commit/push toàn bộ diff.

## Còn cần nghiệm thu trước phát hành

- Camera, OCR/QR thật trên iPhone; tên có dấu/thẻ mới, điều kiện ánh sáng và fallback sửa tay. Android hiện compile đạt, chưa nghiệm thu thiết bị vật lý.
- Nhân viên đối chiếu đúng người, bản gốc và thao tác hoàn chỉnh trên điện thoại của người dùng; bài tự động chỉ dùng thông tin tổng hợp.
- Chính sách backup/khôi phục/xóa dữ liệu, quyền nhân viên và lưu nhật ký cần chốt. Xóa ciphertext khỏi DB hoạt động không tự xóa bản backup; crash có thể để lại tệp camera trong cache OS, không cam kết xóa vật lý toàn bộ dấu vết trên thiết bị.
- Không có kiểm tra chống giả mạo/liveness/NFC hoặc đối chiếu nguồn cấp giấy tờ trong phiên bản này. Nếu tự động hóa tiếp, phải giữ quyền kiểm soát dữ liệu theo quyết định không dùng provider ngoài, đánh giá riêng độ tin cậy và chống gian lận; không bật lại VNPT ngầm.
- Các nghiệm thu Face ID/inbox/UI reset và hạ tầng production đã ghi ở đợt2 vẫn giữ phạm vi riêng, không tự chuyển thành đạt nhờ thay luồng danh tính.

## Tài liệu và bằng chứng

Contract: [local document review](local-document-review-contract.md). Hướng dẫn duy nhất: [trang15 hiện hành](../../huong-dan-su-dung/15-doc-giay-to-va-doi-chieu.md); đường dẫn VNPT cũ chỉ chuyển về trang này. DBML đã đồng bộ. Các biên bản VNPT cũ được đánh dấu lịch sử/đã thay thế.

Bằng chứng JSON đã loại dữ liệu riêng: [thư mục evidence](evidence/local-document-20261019/). Logs private tại `/tmp/careflow-local-identity-20261019/`; backup/key/artifact runtime ngoài Git. Không đưa hình giấy tờ hoặc response cá nhân vào bằng chứng.

Nguồn native đối chiếu: [Apple Vision](https://developer.apple.com/documentation/vision/recognizing-text-in-images), [Android bundled OCR](https://developers.google.com/ml-kit/vision/text-recognition/v2/android).
