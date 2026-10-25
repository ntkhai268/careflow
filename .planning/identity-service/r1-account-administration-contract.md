# R1 — Hợp đồng quản trị tài khoản nội bộ

Mốc20/10/2026, nguồn hiện hành `security-integration`. Hợp đồng này mô tả API và tính nhất quán; hướng dẫn thao tác chỉ ở [trang17 chuẩn](../../huong-dan-su-dung/17-tai-khoan-va-quan-tri-r1.md).

## Quyền sở hữu và định danh

Identity sở hữu tài khoản, role/status, passkey, phiên, thông tin provisioning và audit. Directory sở hữu hồ sơ bác sĩ và phân công khoa/phòng. `DoctorProfile.id` là ID hồ sơ; `DoctorProfile.userId` và `StaffAssignment.userId` là Identity user ID. Không dùng profile ID để cấp passkey, khóa tài khoản hoặc đổi quyền. Không ghi logic Patient/EMR/Lab vào quản trị Identity.

Vai trò nội bộ: DOCTOR, STAFF, LAB_TECHNICIAN, ADMIN. Đăng ký PATIENT vẫn qua xác minh email, không qua form nội bộ. Tài khoản mới có mật khẩu ngẫu nhiên không được cung cấp ra ngoài; cờ provisioning bắt buộc passkey kể cả môi trường demo cho một số tài khoản nội bộ cũ đăng nhập mật khẩu. Thiết lập passkey dùng quy trình hai kênh có sẵn, không tự sinh passkey thay nhân viên.

## API Identity

| API | Quyền và dữ liệu |
|---|---|
| GET `/api/users/staff?query=&role=&status=&page=0` | ADMIN đang có phiên;25 bản ghi/trang, truy vấn tối đa100 ký tự, page0..10000; tìm literal theo username/email/displayName; không trả PATIENT |
| GET `/api/users/staff/roles` | ADMIN; tập vai trò nội bộ từ enum server |
| GET `/api/users/staff/{userId}` | ADMIN; metadata một tài khoản nội bộ,404 nếu không tồn tại/thuộc PATIENT |
| POST `/api/users/staff` | ADMIN xác thực passkey trong5 phút; `Idempotency-Key` UUID; username/email/displayName/role;201 sau commit |
| PATCH `/api/users/{id}/status` | ADMIN passkey mới; ACTIVE/LOCKED/DISABLED; thu hồi phiên khi policy hiện có yêu cầu |
| PATCH `/api/users/{id}/role` | ADMIN passkey mới; dùng policy thu hồi phiên và credential hiện có; không có UI đổi role trong R1 |

Các response danh sách/metadata/tạo mới `Cache-Control: no-store`. Metadata gồm id, username, email, role, status, displayName nullable cho tài khoản cũ, lastLoginAt và passkeyCount; không có hash mật khẩu, mã email, secret hoặc refresh token. Danh sách Identity không lấy họ tên/email giả từ Directory; có thể bổ sung tên hiển thị thực từ hồ sơ Directory khi tài khoản cũ thiếu displayName.

Tạo mới chuẩn hóa username/email về chữ thường, displayName NFC. Kiểm tra trùng và lưu user/provisioning/audit trong cùng transaction. Khóa advisory PostgreSQL dùng chung cho các lệnh quản trị, sau khi chờ khóa phải xác minh lại actor ADMIN ACTIVE và phiên PASSKEY chưa thu hồi/hết hạn, authenticated_at trong5 phút. Hai ADMIN thao tác khóa nhau đồng thời không được cùng thành công làm mất mọi ADMIN hoạt động.

Idempotency gắn `(created_by, request_id)` cùng SHA-256 fingerprint các trường chuẩn hóa. Gửi lại cùng nội dung trả cùng user ID; đổi nội dung trên cùng key trả409. Username/email đã tồn tại trả409; không tạo tài khoản thứ hai sau mất phản hồi. Mỗi tạo/status/role thay đổi có audit trong transaction; retry không thêm audit tạo thứ hai. Chặn tự khóa/đổi quyền tài khoản quản trị hiện tại và giữ ADMIN hoạt động có passkey. Mở khóa không làm sống lại phiên đã thu hồi.

## Directory và xác minh liên service

POST/PUT hồ sơ bác sĩ xác minh Identity user ID, role DOCTOR và trạng thái ACTIVE khi kích hoạt. STAFF gắn phòng CONSULTATION/PHARMACY; LAB_TECHNICIAN gắn LAB/IMAGING; giá trị khoa/phòng phải đang hợp lệ theo policy Directory. Tắt phân công cũ có thể thực hiện khi tài khoản không còn active. Mọi truy vấn quản trị vẫn qua kiểm tra phiên/role server.

`GET /api/directory/doctors/admin` trả cả hồ sơ inactive cho ADMIN để tiếp tục cập nhật hồ sơ đã có. Danh mục công khai vẫn giữ contract cũ. Tạo profile mới để Hibernate sinh UUID; không gán UUID vào đối tượng mới rồi gọi merge, tránh StaleObjectStateException. Đây là sửa persistence, không đổi schema Directory.

Directory gọi đúng `GET /api/users/staff/{UUID}` bằng token actor và caller riêng, qua TLS/mTLS. Identity yêu cầu certificate phù hợp, caller key và scope `GET:/api/users/staff/**`, rồi vẫn kiểm tra actor ADMIN. Nhánh này chỉ bỏ yêu cầu tái sử dụng DPoP của trình duyệt cho GET metadata đã ủy quyền; không ủy quyền list/create/patch. Service không chuyển tiếp proof DPoP vốn được ký cho URL Directory. Không có token actor, sai ID/role/status hoặc Identity không đáp ứng thì từ chối; không dùng dữ liệu cache/fallback để chấp nhận phân công.

Vì hai service commit độc lập, Identity có thể đã tạo tài khoản nhưng Directory chưa lưu. UI giữ hai trạng thái “cần phân công” và “chưa có passkey”, cho tiếp tục sau reload. Không rollback bằng cách xóa tài khoản đã tạo ở Identity khi Directory lỗi. Đổi role sau này có thể làm phân công cũ không còn phù hợp; hiệu lực truy cập vẫn phải qua role/phiên hiện hành. R1 không tự di chuyển phân công theo role mới.

## Schema và giới hạn

V18 thêm `identity.staff_account_provisioning` và `identity.staff_account_audit`; [DBML](identity-db.dbml) được đồng bộ. Không seed tài khoản có credential sử dụng được, mã cấp quyền, đồng ý hoặc kết quả định danh giả. Dữ liệu provisioning/audit sinh theo thao tác thật; fixture tổng hợp ở DB test riêng.

Chưa phải hệ thống HR đầy đủ, không tự gửi mật khẩu tạm hoặc tự xác nhận chứng chỉ hành nghề. Không chứng minh hộp thư do nhân viên sở hữu chỉ bằng việc ADMIN nhập email; việc enrollment cần cả email factor và activation factor riêng sau đối chiếu. Scope caller cấu hình private và không mang theo secret vào repo. Production/staging, passkey vật lý và cảm biến iPhone cần nghiệm thu môi trường riêng, xem [biên bản](r1-verification-2026-10-20.md).
