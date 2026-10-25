# 15 — Đọc giấy tờ trên điện thoại và đối chiếu trực tiếp

[Quay về mục lục](README.md)

Mốc 19/10/2026: CareFlow iOS **1.0.0 (16)** đã build/ký/cài đè và mở trên iPhone 11 Pro Max. Backend test trên Mac đã áp dụng V17; web có trang nhân viên và quản trị. Đã nghiệm thu API thật và web với dữ liệu giả chuyên dùng kiểm thử; bản16 đã chạy native OCR trên iPhone với ảnh tổng hợp và điền đủ ba trường. Camera, độ chính xác OCR trên căn cước thật/tiếng Việt và đối chiếu bản gốc ngoài đời vẫn cần người dùng xác nhận. Source chưa commit/push; không triển khai production hoặc DB remote.

**Luồng VNPT đã ngừng.** Máy chủ không còn adapter gọi VNPT; các API cũ trả410. Không yêu cầu chụp selfie, không upload ảnh giấy tờ lên CareFlow hoặc nhà cung cấp. Việc ngừng tích hợp không tự xóa ảnh đã gửi sang VNPT trước đó; việc xóa dữ liệu cũ cần xác nhận riêng với VNPT.

## Bệnh nhân: đọc và kiểm tra thông tin

Điều kiện: app bản15 trở lên (khuyến nghị bản16), đăng nhập PATIENT, cho phép camera khi chụp; kết nối máy chủ CareFlow để gửi thông tin. Bước đọc ảnh chạy trên điện thoại, không cần gọi API OCR. iPhone dùng Apple Vision; Android dùng model Latin OCR đóng gói trong app. iOS có thêm đọc QR trong ảnh; không dùng QR/OCR làm bằng chứng giấy tờ thật.

1. Vào **Tài khoản → Xác minh danh tính**. Màn mới có tiêu đề **Đối chiếu danh tính** và nút **Đọc căn cước trên máy**.
2. Bấm nút đọc, chụp mặt có số căn cước, họ tên và ngày sinh, đủ sáng và rõ chữ. App đọc ảnh tại máy rồi xóa tệp tạm sau xử lý thông thường; không hiển thị lại ảnh trên trang kết quả.
3. Kiểm tra và sửa **Số căn cước** (12 chữ số), **Họ tên trên giấy tờ**, **Ngày sinh** (`dd/MM/yyyy`). OCR có thể thiếu dấu hoặc đọc thiếu; các ô luôn cho sửa. Bản16 hỗ trợ thêm nhãn thẻ mới/Fullname, số chia nhóm bằng khoảng trắng và ngày sinh có dấu chấm/gạch ngang. App hiện thông báo đã xử lý trên máy và nhắc bổ sung ô thiếu; nếu chưa đọc rõ, chụp lại hoặc nhập tay. Nếu camera/OCR không hoạt động, có thể nhập các thông tin này bằng tay. Không dùng dữ liệu người thật trong ảnh chụp báo lỗi/chat.
4. Tích xác nhận đã kiểm tra thông tin và đồng ý gửi **ba trường trên** tới CareFlow. Sửa một ô sau khi tích sẽ bỏ tích, yêu cầu kiểm tra lại. Không gửi ảnh, selfie, địa chỉ, giới tính, mã QR gốc hoặc toàn bộ đoạn OCR.
5. Bấm **Gửi yêu cầu đối chiếu**. Kết quả phải là **Chờ đối chiếu giấy tờ gốc tại cơ sở y tế**, kèm số đã che; không tự hiện đã xác minh. Thông tin đầy đủ được mã hóa ở backend để nhân viên có quyền đối chiếu.
6. Mang căn cước bản gốc đến cơ sở y tế, xuất trình cho nhân viên. Bấm **Tải lại trạng thái** sau khi được xử lý để nhận kết quả.

Đọc OCR/QR hoặc gửi form chưa xác nhận căn cước thật và chưa cấp quyền vào bệnh án. Face ID đăng nhập vẫn là tính năng riêng; không thay thế bước đối chiếu giấy tờ.

## Nhân viên: đối chiếu bản gốc

Điều kiện: tài khoản STAFF hoặc ADMIN hợp lệ; người bệnh có mặt cùng giấy tờ bản gốc. Không xác nhận thay cho người vắng mặt.

1. STAFF mở **Đối chiếu danh tính** trong menu, tại `/staff/identity`; ADMIN mở `/admin/identity`. Trên Mac test hiện tại: [nhân viên](http://localhost:5173/staff/identity) hoặc [quản trị](http://localhost:5173/admin/identity).
2. Danh sách chờ chỉ hiện tài khoản, số căn cước đã che và thời điểm gửi. Mỗi trang tối đa25 yêu cầu; dùng **Trang trước/Trang sau/Tải lại hàng chờ**.
3. Chọn **Đối chiếu** ở đúng tài khoản. Trang chi tiết mới mở ba trường đầy đủ. Không có ảnh từ điện thoại. Xác định đúng người bệnh/tài khoản trước khi tiếp tục.
4. Đối chiếu bản gốc và người xuất trình; chỉ tích khi thực sự hoàn tất từng bước: **Đã xem căn cước bản gốc tại quầy**, **Người xuất trình khớp ảnh trên giấy tờ**, **Số căn cước, họ tên và ngày sinh đều khớp**.
5. Nhập **Mã quầy hoặc mã biên bản** từ3 đến80 ký tự chữ Latin/số/khoảng trắng hoặc `._:/-`, ví dụ mã quầy nội bộ. Không nhập tên/số căn cước hay dữ liệu cá nhân vào ô này.
6. Đủ ba bước và thông tin khớp: bấm **Xác nhận đã đối chiếu**. Không khớp: nhập mã đối chiếu và bấm **Từ chối do không khớp**; không tích giả các điều kiện để cho qua.
7. Sau quyết định, yêu cầu rời hàng chờ, thông tin đầy đủ bị xóa khỏi bảng hoạt động. Hệ thống giữ trạng thái, số đã che, người/thời điểm/mã đối chiếu. Mobile tải lại thấy **Đã đối chiếu trực tiếp tại cơ sở y tế** hoặc thông báo chưa khớp.

Không được tự xác nhận danh tính của mình. PATIENT/DOCTOR/LAB không có quyền đọc chi tiết hoặc duyệt; API trả403 và route web ngoài quyền hiển thị AccessDenied. Hai nhân viên quyết định cùng lúc: chỉ một quyết định thành công, người còn lại tải lại trạng thái. Đóng chi tiết hoặc chuyển tab sẽ ẩn dữ liệu đang mở.

## Hủy, hết hạn và chạy lại

- Khi đang chờ, bệnh nhân bấm **Hủy yêu cầu và xóa thông tin**: backend xóa thông tin đầy đủ, giữ metadata WITHDRAWN. Tài khoản khác không hủy được.
- Yêu cầu chờ có hiệu lực7 ngày. Sau hạn, nhân viên không đọc/duyệt được; tác vụ dọn chạy mỗi giờ xóa payload quá hạn. Yêu cầu bị từ chối/hủy/hết hạn có thể gửi lại; tối đa3 yêu cầu mới/24 giờ. Không tạo yêu cầu mới khi còn một yêu cầu chờ hoặc đã được xác nhận.
- Mất phản hồi sau gửi: **Tải lại trạng thái** trước. Gửi lại cùng thông tin/khóa yêu cầu không tạo bản ghi mới; không chụp lại chỉ để chữa lỗi mạng. Hủy/sửa thông tin rồi gửi lại phải theo trạng thái mới nhất.
- Không thấy yêu cầu trên web: kiểm tra tài khoản/role, trang danh sách, yêu cầu đã hủy/hết hạn hay chưa và cùng môi trường backend. Không dùng SQL sửa trạng thái để nghiệm thu.
- Dịch vụ chưa sẵn sàng: operator kiểm tra khóa mã hóa riêng và DB migration; không bật lại VNPT để bỏ qua lỗi.
- Bản14 trở xuống không dùng được luồng cũ (410); cập nhật bản16. Chỉ đọc giấy tờ khi offline được; gửi yêu cầu và nhận quyết định cần kết nối CareFlow.

## Cấu hình operator và phạm vi dữ liệu

`IDENTITY_DOCUMENT_KEY_FILE` trỏ tới file base64 chứa32 byte ngẫu nhiên, giữ ngoài Git với quyền0600. Hai Identity phải dùng cùng khóa để đọc cùng DB. Trên Mac này file nằm ở `~/Library/Developer/CareFlow/local-document-20261019/document.key`. Khóa này độc lập với khóa email/outbox; không thay key Resend. Chưa cấu hình khóa: luồng tiếp nhận báo chưa sẵn sàng. Không tạo đè khóa khi đang có yêu cầu chờ; backup/khôi phục phải giữ đúng khóa.

V17 chỉ thêm bảng metadata/payload mã hóa; không lưu ảnh. Nội dung chi tiết chỉ nhân viên có quyền đọc trong thời gian chờ. Xóa payload ở DB hoạt động không tự xóa các bản backup; chính sách backup, phân quyền nhân viên và thời hạn lưu nhật ký phải chốt trước production. Không seed yêu cầu giả hoặc trạng thái xác nhận giả. Dùng dữ liệu giả tự tạo cho kiểm thử, không thu căn cước người thật để huấn luyện model.

Bước tiếp theo nếu muốn tự động hóa: đánh giá chống giả mạo/liveness và xác thực nguồn giấy tờ trong hạ tầng được kiểm soát. Phiên bản này chưa tự động xác minh thật/giả, chưa NFC và không tự liên kết hồ sơ bệnh án.

[Báo cáo bản15](../.planning/identity-service/local-document-review-verification-2026-10-19.md) và [bổ sung bản16](../.planning/identity-service/iphone-faceid-document-fix-2026-10-19.md).
