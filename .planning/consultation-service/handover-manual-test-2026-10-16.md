# Kiểm tra thủ công bàn giao ca khám — 16/10/2026

## Kết luận

Đã thao tác từng bước trên **Chrome có giao diện**, điều khiển bằng Playwright và quan sát ảnh màn hình/API thực tế, từ 15:46 ngày 16/10/2026. Không chỉ chạy lại script kiểm thử tự động của lần triển khai trước. Backend, Gateway và DB audit thật; không intercept/mock phản hồi. **28 kiểm tra UI đạt**, phát hiện **1 lỗi giao diện đã sửa và kiểm tra lại**. Production Web build và kiểm tra 14 đầu kết nối đều đạt; không có JavaScript exception chưa xử lý.

## Môi trường và dữ liệu

- Web: `http://localhost:5173`; Mobile Emulator: `http://localhost:3000`; Gateway: HTTPS `18080`.
- Bác sĩ A/B/C của bộ demo `9bbb8a`, Da liễu ROOM-08; các phiên Chrome độc lập. ADMIN chỉ quản lý quyền điều phối.
- Bệnh nhân bàn giao 01, ca `c0e4d579-a1df-4cbb-803e-ae6d5384a508`, phiếu **DAL-001**. Bệnh nhân bàn giao 02 là ca đã hoàn tất để kiểm tra không thể chọn bàn giao.
- Dùng dữ liệu có sẵn, thêm các yêu cầu/quyết định bàn giao, một ghi chú lâm sàng và một toa nháp của C **qua giao diện và API thật**. Không cần ghi trực tiếp SQL. Đọc SQL cục bộ chỉ để đối chiếu kết quả cuối.
- Toa mới ghi rõ dữ liệu kiểm thử, không sử dụng điều trị. Không ký/xác nhận phát hành toa mới hoặc hoàn tất ca demo đang dùng để trình diễn.

[Tài khoản, mật khẩu và cách trình diễn](../../careflow-mobile-emulator-service/docs/CLINICAL-HANDOVER-GUIDE.md).

## Những thao tác đã thực hiện

| Luồng | Kết quả thực tế |
|---|---|
| Bỏ trống lý do/SBAR | 5 trường bắt buộc chặn gửi; không phát POST |
| B gửi C | Có yêu cầu PENDING đúng bệnh nhân/SBAR trên phiên C |
| C mở ca trước khi nhận | GET 403; không có dữ liệu ca đầy đủ |
| C từ chối có lý do | REJECTED; B vẫn mở/tiếp tục ca được |
| B hủy yêu cầu chờ | CANCELLED; C không còn nút tiếp nhận |
| C điều phối hủy yêu cầu B→A | CANCELLED, người quyết định thực tế là C; B vẫn phụ trách |
| B→C và C tự xác nhận | ACCEPTED; C mở đúng ca, tên phụ trách C và người khởi tạo A |
| B mở lại sau khi đã giao | GET 403; màn lỗi cố định, không có thao tác khám |
| C xem toa nháp của A | Chỉ đọc chi tiết, không ký/sửa thay tác giả A |
| C lưu ghi chú và toa mới | PUT ca 200, POST toa 201; toa DRAFT mới đứng tên C |
| Tải lại ca của C | Ghi chú/toa mới vẫn còn từ DB thật |
| Mobile đăng nhập bệnh nhân 01 | Hành trình Đang khám tiếp tục, không đặt lịch/check-in lại |
| C bàn giao về B | B xác nhận; ghi chú C giữ nguyên, cả toa A và C chỉ đọc đối với B |
| ADMIN thu hồi quyền C | C không thấy ca của B để khởi tạo thay |
| ADMIN cấp lại quyền C | Chọn đúng C ROOM-08 từ danh mục thật, có lý do |
| C điều phối gửi ngoại lệ B→A | PENDING ghi nguồn B, người khởi tạo C, đích A; không tự chuyển trách nhiệm |
| A xác nhận nhận ngoại lệ | Sau xác nhận của A mới chuyển ca sang A |
| A bàn giao về B | B tự nhận lại, phục hồi trạng thái demo bằng luồng nghiệp vụ |
| ADMIN vào trang bàn giao doctor | Trang 403 theo vai trò |
| DOCTOR vào trang cấp quyền admin | Trang 403 theo vai trò |
| Ca demo 02 đã hoàn tất | Không có trong bộ chọn ca được bàn giao |
| Mobile sau khi phục hồi | Màn Đang khám hiện BS Bàn giao B, Phòng 08 - Da liễu |
| ID ca không tồn tại | 404 với màn lỗi cố định, không dựng form/trạng thái khám giả |
| Thử lại / Quay lại ở màn lỗi | Gọi lại API thật và trở lại danh sách bàn giao đúng |

Bảng gộp các bước liên quan để dễ đọc; 28 mục kiểm tra chi tiết nằm trong [manual-results.json](manual-handover-evidence/manual-results.json). Các HTTP 403/404 trong nhật ký là những nhánh từ chối được chủ động kiểm tra, không phải lỗi ngoài dự kiến.

## Lỗi phát hiện và sửa

**HANDOVER-MANUAL-01 — trạng thái giả khi tải ca thất bại.** Khi bác sĩ chưa có quyền mở thẳng URL ca, API trả 403 đúng nhưng `ConsultationPage` chỉ hiện toast 3 giây rồi tiếp tục dựng biểu mẫu mặc định: “Bệnh nhân”, 30 tuổi và “Hoàn tất”. Không làm lộ bệnh án hoặc vượt quyền server, nhưng gây hiểu nhầm là ca đã kết thúc và hiển thị các thao tác không phù hợp.

Sửa gọn trong `frontend/doctor-web/src/app/(dashboard)/consultation/[id]/page.tsx`: giữ lỗi tải bằng state riêng, xóa đối tượng ca cũ khi bắt đầu tải và chặn toàn bộ biểu mẫu khi tải lỗi/không có ca. Màn hình có thông báo cố định, gợi ý phải tiếp nhận trước khi mở, nút Thử lại và Quay lại bàn giao. Không thay API, quyền server hoặc state machine lâm sàng.

GitNexus impact trước sửa: **HIGH**, 26 liên kết trực tiếp, 70 symbol trong phạm vi tác động, 0 execution flow được graph ghi nhận. Đã cảnh báo và thực hiện trong phạm vi ủy quyền sửa toàn hệ thống. Sau sửa: Next production build thành công; mở ca bị 403 và chờ hơn 3,5 giây vẫn có thông báo, không còn form/nút hoàn tất; 404, Thử lại/Quay lại và ca hợp lệ đều được kiểm tra lại.

| Trước sửa | Sau sửa |
|---|---|
| [Ảnh biểu mẫu trống sau 403](manual-handover-evidence/02-pending-access-denied.png) | [Ảnh màn lỗi cố định](manual-handover-evidence/04-fixed-access-error.png) |

## Đối chiếu API/DB sau thao tác

- Ca demo vẫn IN_PROGRESS, người phụ trách **B**, tác giả ca gốc **A**, giữ nguyên patientId/appointmentId.
- Giữ nguyên ticketId và mã **DAL-001**; không phát sinh phiếu/lịch mới cho lần bàn giao.
- 7 yêu cầu `MANUAL-*`: **4 ACCEPTED, 2 CANCELLED, 1 REJECTED**, không còn PENDING. Bốn lần nhận mới đều có snapshot khớp bác sĩ trước khi giao và tác giả gốc A.
- Toa ban đầu giữ tác giả A. Toa nháp mới `96f20bcc-4cf0-4821-91e7-32a59db669b9` thuộc C. Chỉ định Lab gốc vẫn thuộc A.
- Ca gốc **NOI-010** vẫn IN_PROGRESS, phụ trách bác sĩ An. Không dùng ca này để gửi/nhận hoặc sửa bệnh án.
- C đã được cấp lại quyền điều phối. Các phiên bác sĩ/admin/bệnh nhân do phiên kiểm tra tạo đã đăng xuất và Chrome của phiên test đã đóng.

[SQL chỉ đọc xác minh invariants](manual-handover-evidence/database-invariants.txt) · [đối chiếu provider](manual-handover-evidence/provider-invariants.json).

## Ảnh minh chứng

- [Yêu cầu chờ](manual-handover-evidence/01-pending.png)
- [Đã tiếp nhận](manual-handover-evidence/05-accepted.png)
- [Bác sĩ nhận tiếp tục ca](manual-handover-evidence/06-receiver-continues.png)
- [Lưu toa nháp riêng](manual-handover-evidence/08-receiver-saves-own-draft.png)
- [Hành trình trên điện thoại](manual-handover-evidence/09-mobile-same-journey.png)
- [Yêu cầu ngoại lệ cần người nhận xác nhận](manual-handover-evidence/12-exceptional-pending.png)
- [Trạng thái cuối trên Web](manual-handover-evidence/14-final-b-responsible.png)
- [Trạng thái cuối trên Mobile](manual-handover-evidence/15-mobile-final-b.png)

Backup PostgreSQL sau thao tác đã lưu ngoài Git: `~/Library/Application Support/CareFlow/mac-demo/backups/postgres-20261016-155413.sql.gz`. Web đang chạy bản sửa mới. Không stage/commit/push, không thay worktree `develop`.

Kiểm tra này xác nhận bàn giao cùng khoa/phòng, quyền trước/sau, tiếp tục ghi bệnh án/toa nháp và hiển thị mobile. Không đại diện nghiệm thu toàn bộ xét nghiệm, thanh toán, cấp phát thuốc, chuyển khoa/phòng hoặc durable outbox hoàn tất liên dịch vụ.
