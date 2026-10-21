# 21 — Thông báo và đồng bộ R4

Phạm vi: backend R4 local và app native iOS 1.0.0(20). Trạng thái cài/nghiệm thu cụ thể xem [biên bản R4](../.planning/notification-service/r4-acceptance-2026-10-21.md). Bản cũ trên điện thoại chưa có các thay đổi này. Tài khoản demo chỉ xem [trang 01](01-tai-khoan-va-du-lieu.md).

## Sử dụng và kiểm tra lại

1. Đăng nhập app bằng tài khoản bệnh nhân, vào **Thông báo**. Inbox thuộc cả tài khoản, gồm thông báo hồ sơ người thân được quản lý; không phụ thuộc lịch đang chọn ở Trang chủ. Bộ đếm dùng số chưa đọc từ server. **Tải thông báo cũ hơn** đọc tiếp lịch sử; kéo xuống hoặc nút tải lại đọc lại dữ liệu.
2. Khi app đang mở, có thể ở màn hình khác. Nhân viên/bác sĩ thao tác trên lượt khám hợp lệ theo [R2](19-tiep-nhan-lich-va-hang-doi-r2.md) và [R3](20-ho-so-va-hanh-trinh-lam-sang-r3.md): check-in, gọi lượt, chỉ định, phát hành kết quả, ký toa hoặc tạo lịch tái khám. App tải lại sau tín hiệu, thường gom thay đổi trong khoảng 400 ms; dữ liệu lấy từ API hiện tại. Các thông báo gần lượt phụ thuộc số người chờ và cấu hình queue, không tự sinh chỉ bằng mở màn hình.
3. Tắt mạng rồi bật lại hoặc đưa app xuống nền rồi mở lại: app kết nối lại và tải bù. Khi foreground có kiểm tra bù mỗi 30 giây. Mạng/server lỗi có thể cần bấm tải lại; dữ liệu cũ không phải xác nhận trạng thái mới. **Đang kết nối lại** là trạng thái kết nối realtime, không bảo đảm push đã bật.
4. Chạm thông báo mở đúng tài nguyên sau kiểm tra quyền server, kể cả lượt không hẹn và hồ sơ người thân. Thông báo cũ vẫn có thể còn trong inbox sau chuyển quyền; tài nguyên đã mất quyền phải bị từ chối, không được hiển thị bệnh án. Đánh dấu đã đọc không cấp thêm quyền.
Khi bấm thông báo lúc app đang khóa/chuyển nền, yêu cầu mở được giữ để kiểm tra lại sau khi đăng nhập và đăng ký thiết bị thành công. Dùng cùng tài khoản để tiếp tục; đổi sang tài khoản khác không được mở thông báo cũ. Đây là hành vi đã kiểm thử tự động, còn cần nghiệm thu trên thiết bị có cấu hình push thật.

5. Chọn **Bật push** để yêu cầu quyền thông báo của hệ điều hành. Nếu hiển thị “Push chưa được cấu hình”, vẫn có inbox và cập nhật khi mở app; chưa có thông báo nền. Nếu từ chối quyền, vào Settings của iOS/Android để cấp lại rồi trở lại app. Chỉ báo “Đã đăng ký” xác nhận đăng ký thiết bị với server, chưa phải bằng chứng APNs/FCM đã giao thông báo.
6. Đăng xuất, đổi tài khoản hoặc thu hồi thiết bị tại mục bảo mật: phiên cũ không được nhận push mới. Thông báo màn khóa chỉ có lời nhắc CareFlow chung. Chạm thông báo tài khoản cũ không được mở dữ liệu trong tài khoản mới. Lặp lại bằng hai tài khoản demo khác nhau và một sự kiện nghiệp vụ mới cho từng tài khoản.

## Chuẩn bị push thật còn thiếu

Các bước này dành cho người quản trị, không nhập khóa vào app hoặc commit lên Git.

1. Tạo/chọn Firebase project; đăng ký iOS app theo **bundle identifier của bản đang ký** và Android theo applicationId thực tế. Tải GoogleService-Info.plist/google-services.json vào môi trường build private đúng platform. Không dùng file của một bundle khác.
2. Với Apple Developer hỗ trợ Push Notifications, bật capability cho App ID, tạo lại provisioning profile có `aps-environment`; tạo APNs authentication key và tải vào phần Cloud Messaging của Firebase với Key ID/Team ID. File `.p8` chỉ lưu private. Profile ký hiện tại của bản local chưa có entitlement này; cắm USB hoặc bật quyền thông báo không tự bổ sung entitlement.
3. Cấp credential Firebase Admin bằng ADC/service account cho Notification runtime private, cấu hình projectId tương ứng, bật `FIREBASE_ENABLED=true` phía server. Scope caller nội bộ Notification→Identity phải có **POST:/internal/notifications/session-status**, vẫn giữ mTLS và caller key hiện có.
4. Build app với cấu hình Firebase tương ứng và `FIREBASE_ENABLED=true`, API_BASE_URL/WS_BASE_URL cùng origin hợp lệ; cài đè bản đã ký, không cần gỡ app/xóa dữ liệu. iOS phải có APNs token trước khi đăng ký FCM. Android cần quyền thông báo trên phiên bản yêu cầu quyền runtime.
5. Sau đăng nhập chọn Bật push, tạo sự kiện thật bằng API/UI nghiệp vụ. Ghi nhận foreground, background và chạm thông báo khi app không chạy; xác nhận mở đúng tài nguyên, logout/đổi tài khoản không lộ dữ liệu. Kiểm tra cả quyền bị từ chối, thu hồi thiết bị, mất mạng và token đổi.

Theo [Firebase setup cho Flutter](https://firebase.google.com/docs/cloud-messaging/flutter/get-started) và [nhận thông báo](https://firebase.google.com/docs/cloud-messaging/flutter/receive-messages), hành vi nền phụ thuộc hệ điều hành; iOS vuốt tắt app có thể phải mở lại trước khi nhận các tác vụ nền. Không dùng kết quả WebSocket hoặc mock push để đánh dấu các ca APNs/FCM là đạt.

## Giới hạn và cách lặp lại

- Không phải nhắc hẹn trước một ngày; R4 gửi khi lịch/sự kiện nghiệp vụ được tạo/phát sinh. Đặt lịch 07:30 ngày mai không cho check-in hôm nay; xem điều kiện R2.
- Các ca vận hành phía trên cần dữ liệu đúng ngày, đúng phòng/role, đúng trạng thái. Không sửa SQL để ép một lượt thật qua trạng thái kiểm thử.
- Nghiệm thu lại sau mỗi đổi bundle, provisioning, Firebase project hoặc endpoint. Kết quả kiểm thử tự động trong biên bản chỉ chứng minh phạm vi đã ghi; còn cần kiểm tra hiển thị và thao tác trên máy thật.
