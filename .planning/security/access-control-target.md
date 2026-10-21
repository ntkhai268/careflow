# CareFlow — Ma trận quyền đích đề xuất

Ngày 2026-10-14; theo [kế hoạch tổng](../system-completion-plan.md). Đây là contract cần review trước triển khai, không mô tả mọi quyền đang có trong code.

## Quy tắc chung

- Mỗi user request có principal đã xác minh; mỗi internal call có ServicePrincipal được phép gọi và user delegation nếu thao tác thay mặt user.
- Role không đủ: action còn phụ thuộc capability, patient relationship, assignment có hiệu lực, visit/room/service point và trạng thái tài nguyên.
- ADMIN quản trị cấu hình và tài khoản; không tự có toàn bộ quyền sửa/xem dữ liệu lâm sàng. Nếu nghiệp vụ cần quyền đặc biệt, cấp capability riêng với phạm vi, lý do và audit; không dùng ADMIN bypass mặc định.
- Quyền điều phối là capability của STAFF theo cơ sở/phòng/phiên, chưa cần thêm role COORDINATOR. Nhân viên tiếp nhận, dược và kỹ thuật không có quyền giống nhau chỉ vì cùng role.
- Patient không có User không bị mất dịch vụ. Actor thao tác là nhân viên được phân công; người bệnh vẫn là patientId độc lập. Người nhà chỉ có quyền qua quan hệ đã xác minh.
- Không xác minh được quyền => không thực thi. Mã lỗi phản ánh thiếu xác thực, thiếu quyền, trạng thái hoặc dependency; mọi denied command không side effect.

## Ma trận thao tác

| Thao tác | PATIENT/đại diện verified | DOCTOR | STAFF tiếp nhận | STAFF dược | LAB_TECHNICIAN | STAFF điều phối | ADMIN |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Quản lý account/role/assignment | Tài khoản bản thân theo API được phép | Bản thân | Bản thân | Bản thân | Bản thân | Bản thân | Quản trị, audit |
| Tìm bệnh nhân để tiếp nhận | Hồ sơ có quyền | Phạm vi lâm sàng được phép | Tóm tắt vận hành trong scope | Tối thiểu phục vụ phát thuốc | Tối thiểu phục vụ CLS | Tóm tắt vận hành trong scope | Chỉ capability dữ liệu đã cấp |
| Tạo hồ sơ không tài khoản | Không tự claim người khác | Theo capability riêng nếu có | Trong scope, audit | Không | Không | Theo capability tiếp nhận | Theo capability dữ liệu |
| Sửa thông tin hành chính | Hồ sơ có quyền và field được phép | Theo field/capability | Trong scope và field được phép | Không | Không | Theo capability | Theo capability dữ liệu |
| Đọc/upload hồ sơ cá nhân | Hồ sơ có quyền; đúng quyền read/write của đại diện | Quan hệ lâm sàng hợp lệ | Không đọc toàn bộ hồ sơ | Không | Chỉ dữ liệu cần cho order | Không | Không mặc định |
| Đặt/hủy lịch | Hồ sơ có quyền; theo trạng thái | Tái khám/phạm vi phụ trách | Tiếp nhận hỗ trợ theo scope | Không | Không | Ngoại lệ theo capability/lý do | Cấu hình, không tự sửa ca |
| Tạo walk-in/check-in | Tự check-in lượt có quyền; không tự ưu tiên | Không mặc định intake | Trong phạm vi được giao | Không | Không | Theo capability intake | Theo capability intake |
| Xác nhận ưu tiên phục vụ | Không tự xác nhận | Nếu được giao theo policy | Nếu được giao theo policy | Không | Không | Nếu được giao theo policy | Cấu hình policy; không thay quyết định chuyên môn |
| Gọi/start/complete bước khám | Không | Bác sĩ phụ trách phòng/ca | Hỗ trợ recall theo capability; không khám | Không | Không | Điều phối có lý do; không sửa lâm sàng | Không mặc định |
| Transfer/requeue/override | Yêu cầu hỗ trợ | Trong scope cho phép | Recall/requeue trong policy | Trong quầy cho phép | Trong điểm CLS cho phép | Trong scope, reason/audit | Theo capability điều phối |
| Sửa/hoàn tất consultation | Không | Bác sĩ phụ trách, trạng thái hợp lệ | Không | Không | Không | Không | Không mặc định |
| Tạo CLS/đánh giá kết quả | Xem kết quả được phát hành, có quyền | Ca được giao, state hợp lệ | Không | Không | Không quyết định thay bác sĩ | Không | Không mặc định |
| Nhập/phát hành kết quả CLS | Không | Chỉ capability duyệt được cấp nếu có | Không | Không | Assigned/claimed điểm thực hiện + state | Không | Quyền đặc biệt được cấp, audit |
| Kê/xác nhận/amend toa | Xem đơn được phát hành, có quyền | Bác sĩ hợp lệ của ca, state hợp lệ | Không | Không | Không | Không | Không mặc định |
| Phát thuốc | Nhận hướng dẫn của hồ sơ có quyền | Không mặc định | Không | Assigned quầy + claim + đơn hợp lệ | Không | Không | Quyền đặc biệt được cấp, audit |
| EMR tổng hợp | Dữ liệu hồ sơ được phép xem | Phạm vi lâm sàng hợp lệ | Tóm tắt vận hành riêng | Dữ liệu tối thiểu phục vụ thuốc | Dữ liệu tối thiểu phục vụ CLS | Tóm tắt vận hành riêng | Không mặc định |
| AI clinical/interaction | Không mặc định | Capability + context ca được phép | Không mặc định | Không mặc định | Không mặc định | Không mặc định | Không mặc định |

Xác nhận ưu tiên phục vụ không phải phân loại cấp cứu. Chuyên môn phải được thực hiện bởi nhân viên y tế được bệnh viện giao, không suy ra từ role kỹ thuật hoặc tuổi bệnh nhân.

## Internal call/event

| Bên gọi | Tài nguyên/operation | Quyền và bằng chứng |
| --- | --- | --- |
| Gateway | Endpoint user công khai theo route contract | User token và xác thực caller theo deployment; không cấp quyền rộng hơn user |
| Appointment/Queue/Consultation/Lab/Prescription/EMR | Patient access/summary | Caller scope allowlist + patientId cụ thể + user relationship/clinical context; background call có policy riêng |
| Queue/Appointment | Directory assignment/capability | Caller scope; user/room/session cụ thể; không trả mọi assignment nếu chỉ cần decision |
| Queue | Capacity allocation ở Appointment | Caller scope + visit/intake context + idempotency; không sửa lịch bác sĩ tự do |
| Lab/Prescription | Consultation clinical context | Caller scope + consultationId, visitId, patientId khớp + actor phụ trách |
| EMR | Scoped read ở service nguồn | Caller scope + actor/patient context; không có endpoint bypass chỉ để tổng hợp |
| Producer event | Consumer nghiệp vụ | Broker ACL producer, envelope version, resource references hợp lệ, dedup; event không mang user bearer token |
| Notification | Recipient resolution | Caller scope; relation/contact verification hiện hành; public call DTO không PHI |

## Tiêu chí nghiệm thu ma trận

Với mỗi ô được phép: ít nhất một ca chấp nhận và một ca sai phạm vi bị từ chối. Với mỗi ô không được phép: gọi qua Gateway, direct endpoint và alias legacy nếu tồn tại đều không thực thi. Kiểm tra đổi role/revoke relation/đổi assignment, service dependency lỗi và hai instance theo SLA.

Các capability và field policy cần được bệnh viện duyệt trong G0; đây là lý do cần review nghiệp vụ, không có nghĩa dừng sửa các endpoint đang bỏ qua authorization.
