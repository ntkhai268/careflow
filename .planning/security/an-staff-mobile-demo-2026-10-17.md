# Khôi phục demo An / STAFF / Mobile — 17/10/2026

Đối chiếu `origin/develop` tại `e5fc9c1` sau fetch; remote không có nhánh `developo`. Runtime và source sửa tại `.worktrees/security-integration`, HEAD `5a8c6e5` kèm thay đổi chưa commit. Không reset checkout, add, commit hay push. Hướng dẫn thao tác và tài khoản được quản lý tại [huong-dan-su-dung](../../huong-dan-su-dung/README.md), không phải tại báo cáo kỹ thuật này.

## Nguyên nhân và phạm vi sửa

- Quyền layout chặn toàn bộ `/dashboard` đối với STAFF/KTV, trong khi menu và trang `/dashboard/general` có sẵn phần Tổng quan riêng cho các vai trò này trên develop. Cho phép đúng trang Tổng quan; các trang bác sĩ, ca khám và ADMIN tiếp tục kiểm tra vai trò.
- An còn Consultation thử rỗng từ 16/10 ở trạng thái IN_PROGRESS. Guard một ca đang khám là hành vi có sẵn trên develop, không bị xóa. Thông báo trước chỉ qua bong bóng trợ lý; thêm thông báo cố định và đường mở lại ca ở Hàng đợi, kích hoạt banner Vào ca khám đã có ở Tổng quan.
- Kiểm tra ca cũ không có chẩn đoán, chỉ định hay toa; bác sĩ phụ trách chuyển Consultation sang CANCELLED qua API thật để giải phóng ca demo. Giữ bản ghi lịch sử, không dùng SQL sửa trạng thái.
- Bệnh nhân có lịch Ngoại chưa sử dụng từ 16/10; hủy lịch bằng giao diện Mobile. Không đổi tài khoản, khoa/phòng hoặc quyền để né lỗi.

Chỉ sửa ba file TSX: layout, trang Tổng quan và Hàng đợi bác sĩ. Các chức năng nhập khám, danh mục, chỉ định, kê toa, ký toa, hoàn tất, tiếp nhận và quầy thuốc được giữ. Không thay đổi schema.

## Kết quả trên API/DB thật

Gateway HTTPS/mTLS, PostgreSQL audit cục bộ và RabbitMQ thật. Không intercept response hoặc thay DB bằng mock. Browser thao tác giao diện thật qua Playwright; mỗi vai trò có hồ sơ Chrome riêng. Bản build Web production/TypeScript đạt, đã restart bản đang phục vụ.

| Phạm vi | Kết quả |
|---|---|
| STAFF Tổng quan | Mở được phần dữ liệu đúng vai trò, không còn 403 |
| An bị chặn bởi ca cũ | Thông báo cố định và Vào ca khám mở đúng ca, không gọi bệnh nhân khác |
| NOI-001 của bệnh nhân demo | Gọi 200 → bắt đầu 201 → lưu toa 201 → ký 200 → hoàn tất 200 → quầy thuốc gọi/đối chiếu/phát 200 |
| Đối chiếu cuối NOI-001 | Appointment/Consultation/queue COMPLETED, prescription DISPENSED |
| Lượt mới NOI-003 | Đặt qua UI Mobile 201, STAFF thấy WAITING trước check-in, duyệt 200; An tự cập nhật và thấy cùng người |
| Mốc chuẩn bị NOI-003 | CHECKED_IN, 17/10/2026 15:00–15:30, ROOM-01; chưa gọi/chưa có ca khám tại mốc này |
| Tải lại | Web An và STAFF vẫn thấy đúng lượt; Mobile khôi phục phiên thật |
| Từ chối đúng quyền | STAFF vào ADMIN, Hàng đợi bác sĩ và ca khám thấy 403; An vào check-in STAFF thấy 403 |

Trong lúc lưu bằng chứng, NOI-003 được gọi/bắt đầu và chuyển IN_PROGRESS, Consultation `72e2303c-a82e-4f7a-b542-064f8a986918` vẫn do An phụ trách. Giữ nguyên thay đổi đang diễn ra; không reset về CHECKED_IN. Trên Web chọn Vào khám để tiếp tục.

Mã tài nguyên, kết quả và SELECT tại [results.json](an-staff-mobile-2026-10-17/results.json), [database.txt](an-staff-mobile-2026-10-17/database.txt), [guards.json](an-staff-mobile-2026-10-17/guards.json), [build.json](an-staff-mobile-2026-10-17/build.json). HTTP có vài 404 current sau khi ca hoàn tất và 404 phiếu ngắn sau đặt lịch trước consumer; không có 401 trong lượt browser đã ghi. Không xóa các response này để tô kết quả.

Ảnh: [An sẵn sàng gọi lượt mới](an-staff-mobile-2026-10-17/an-ready-demo.png), [STAFF thấy lượt trước tiếp nhận](an-staff-mobile-2026-10-17/staff-fresh-waiting-row.png), [Tổng quan STAFF](an-staff-mobile-2026-10-17/staff-overview.png), [đối chiếu toa](an-staff-mobile-2026-10-17/staff-before-dispense.png), [phiếu Mobile](an-staff-mobile-2026-10-17/mobile01-ready-ticket.png).

## Phân tích ảnh hưởng và giới hạn

GitNexus impact trước sửa: handleCallEntry và handleCallPatient LOW, mỗi hàm có một caller trực tiếp/trang và một nhóm process. DashboardLayout, DashboardQueuePage và handleCallNext UNKNOWN do không resolve đủ route/JSX callback; đã đối chiếu trực tiếp cây route, nút gọi và guard. Thay đổi quyền layout được đánh giá thủ công là phạm vi nhạy cảm và đã báo trước, kiểm tra cả truy cập hợp lệ và bị từ chối. Detect-changes toàn worktree báo CRITICAL do gồm nhiều thay đổi bảo mật/E2E có trước; không coi đó là diff riêng của bản sửa này và không commit toàn worktree.

Buổi này nghiệm thu luồng chính không CLS với đúng bộ tài khoản người dùng yêu cầu. Không chạy lại toàn nhánh CLS, bàn giao, AI, thanh toán, native mobile hoặc sinh trắc học vật lý. Các kiểm chứng trước đó được ghi riêng; không dùng kết quả này để khẳng định toàn bộ production đã đạt. Đã sao lưu DB thật sau chuẩn bị lượt mới, không restore/reset dữ liệu.
