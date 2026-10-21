# Rà soát bàn giao giữa bác sĩ — 16/10/2026

> Đây là báo cáo **trước triển khai**. Chức năng đã được thực hiện và kiểm tra sau đó; xem [kết quả triển khai](handover-implementation-results.md) và [hướng dẫn trình diễn](../../careflow-mobile-emulator-service/docs/CLINICAL-HANDOVER-GUIDE.md).

## Kết luận

Runtime CareFlow hiện chưa có chức năng bàn giao ca khám giữa hai bác sĩ hoàn chỉnh. Kiểm tra mã nguồn, contract, mô hình dữ liệu và sáu trang Hospital Web đang chạy không tìm thấy API/màn hình yêu cầu bàn giao, tiếp nhận, từ chối hoặc lịch sử bàn giao. Đây là kết luận trên source/runtime tích hợp đã kiểm tra, không phải khẳng định về mọi nhánh chưa tích hợp.

Về nghiệp vụ: cần phân biệt điều phối nhân sự với bàn giao trách nhiệm chuyên môn. Đề xuất cho CareFlow là bác sĩ đang phụ trách gửi bàn giao, bác sĩ đủ điều kiện được phân công tiếp nhận và xác nhận; người quản lý chuyên môn điều phối/phê duyệt theo quy định của cơ sở. Vai trò ADMIN kỹ thuật không mặc nhiên có thẩm quyền chuyên môn hoặc được ký nhận thay bác sĩ.

## Bằng chứng hiện trạng

- `ConsultationController`: có create/read/update/clinical-data/wait-for-results/resume/status/complete và các query; không có handover request/accept/reject hoặc reassign command.
- `ConsultationService.requireAssignedDoctor` (dòng 227): chỉ DOCTOR có userId trùng doctorId của ca được thao tác. `requireReadAccess` cũng giữ ownership này.
- `UpdateConsultationRequest`: chỉ có dữ liệu sinh hiệu/lâm sàng/chẩn đoán, không có bác sĩ nhận.
- Entity Consultation có một doctorId và snapshot bác sĩ; chưa có bản ghi bác sĩ giao/nhận, thời điểm yêu cầu/tiếp nhận, lý do hay trạng thái bàn giao.
- Enum `TRANSFERRED` có mô tả chuyển viện/chuyển khoa. Nó không đại diện bàn giao giữa bác sĩ; endpoint updateStatus được bảo vệ hiện cũng không cho dùng enum này như lệnh bàn giao.
- Admin có thể sửa hồ sơ bác sĩ/phân công phòng qua Directory; method `updateDoctorProfile` chỉ cập nhật DoctorProfile, không chuyển quyền sở hữu một Consultation đang chạy.
- Lab và Prescription còn kiểm tra bác sĩ của consultation/bác sĩ chỉ định hoặc bác sĩ trên toa. Thay một doctorId đơn lẻ không đủ để bác sĩ nhận làm việc đúng quyền trên toàn bộ lượt khám.

GitNexus index xác nhận current; query/context được chạy trước rà soát. Query bàn giao không có process phù hợp; đây không phải bằng chứng độc lập về việc thiếu tính năng do giới hạn search/graph, nên đã kiểm tra source/controller/model và giao diện thật bổ sung. Không sửa function/class/API/schema và không tạo implementation plan trong lần rà soát này.

## Kiểm tra Web/API thật

Đăng nhập bằng hai browser context riêng cho DOCTOR và ADMIN. Chỉ mở trang/đọc API, không gọi lệnh sửa ca, thay bác sĩ hoặc phân công phòng.

| Vai trò | Trang đã mở | Thao tác bàn giao tìm thấy |
|---|---|---|
| DOCTOR — BS. An | `/dashboard/queue` | Không |
| DOCTOR — BS. An | `/dashboard/general` | Không |
| DOCTOR — BS. An | Ca khám NOI-010 tại `/consultation/{id}` | Không |
| ADMIN | `/admin` | Không |
| ADMIN | `/admin/facility` | Không |
| ADMIN | `/admin/schedule` | Không |

Các trang đúng URL, API chính tương ứng trả 200. Chi tiết đường dẫn/trạng thái, không có token/body bệnh án: `/tmp/careflow-handover-browser-review.json` trên máy kiểm tra; file này là evidence cục bộ và không được coi là artifact có sẵn khi clone Git.

Đọc cùng một Consultation thật bằng ba phiên thử nghiệm:

| Actor | HTTP | Ý nghĩa |
|---|---|---|
| BS. An, đang phụ trách | 200 | Đọc ca hiện tại |
| Tài khoản DOCTOR khác | 403 | Không tự nhận ca chỉ vì có vai trò bác sĩ |
| ADMIN | 403 | Không được đọc ca lâm sàng theo quyền admin đơn thuần |

Đã đăng xuất các phiên tạo cho lần kiểm tra. Không thay đổi dữ liệu lâm sàng, không reset DB, không thực hiện commit/push.

## Đối chiếu nguồn nghiệp vụ

1. [AHRQ TeamSTEPPS — Handoff](https://www.ahrq.gov/teamstepps-program/curriculum/communication/tools/handoff.html): bàn giao chuyển thông tin cùng trách nhiệm/quyền hạn chăm sóc; người nhận cần xác nhận đã hiểu và chấp nhận. Chưa có xác nhận thì bên giao chưa được coi là đã hoàn tất trách nhiệm bàn giao. Nguồn cũng nhấn mạnh cơ hội hỏi lại và trao đổi trực tiếp.
2. [WHO — Communication During Patient Hand-Overs, 2007](https://cdn.who.int/media/docs/default-source/patient-safety/patient-safety-solutions/ps-solution3-communication-during-patient-handovers.pdf): khuyến nghị quy trình chuẩn hóa như SBAR, truyền đạt tình trạng, thuốc, kế hoạch và thay đổi quan trọng; có thời gian hỏi đáp/nhắc lại để xác nhận.
3. [Báo điện tử Chính phủ — Quy định về trực khám, chữa bệnh, 03/01/2024](https://xaydungchinhsach.chinhphu.vn/quy-dinh-moi-ve-truc-kham-chua-benh-tu-1-1-2024-119240103065651053.htm): bài giới thiệu Chương VIII Thông tư 32/2023/TT-BYT nêu nhận/bàn giao giữa các phiên trực; lịch trực do lãnh đạo ký duyệt và đổi trực ngang cấp cần được lãnh đạo phê duyệt. Phạm vi phần quy định này là các cơ sở có giường nội trú/giường lưu/cấp cứu ngoại viện nêu trong bài; không suy diễn thành quy định mọi lần đổi bác sĩ ngoại trú đều phải do ADMIN phần mềm phê duyệt.

Các nguồn trên hỗ trợ nguyên tắc bàn giao và phân công. Ánh xạ thành role/quy trình phần mềm dưới đây là đề xuất cho đồ án; cần quy chế của bệnh viện để chốt thẩm quyền cụ thể.

## Phân biệt tình huống cho CareFlow ngoại trú

| Tình huống | Cách tổ chức đề xuất |
|---|---|
| Chưa bắt đầu khám, đổi bác sĩ/phòng do lịch làm việc | Người được ủy quyền điều phối cập nhật phân công, kiểm tra năng lực/lịch/capacity và thông báo bệnh nhân; chưa phải bàn giao một ca đang điều trị |
| Ca đang khám hoặc chờ CLS/đọc kết quả | Bác sĩ A gửi nội dung và lý do; bác sĩ B được phân công xem thông tin cần thiết, hỏi lại và xác nhận nhận; quyền phụ trách chuyển tại thời điểm tiếp nhận đã xác nhận |
| Đổi trực, ngoài phạm vi phân công hoặc có tranh chấp | Trưởng khoa/người phụ trách chuyên môn điều phối hoặc phê duyệt theo quy chế; hai bên vẫn cần bàn giao và tiếp nhận nội dung chuyên môn |
| Bác sĩ A không thể thao tác do tình huống đột xuất | Cần nhánh tiếp quản ngoại lệ do người có thẩm quyền chuyên môn quyết định, có lý do và audit, bác sĩ nhận xác nhận; không giả chữ ký của A |
| Chuyển khoa/chuyển tuyến | Quy trình chuyển người bệnh riêng; không dùng thay quy trình đổi bác sĩ cùng ca |

Thông tin cần được chuyển rõ: đúng bệnh nhân/lượt khám, tình trạng và chẩn đoán, dị ứng, việc đã làm, thuốc/chỉ định, kết quả còn chờ, việc phải theo dõi và kế hoạch tiếp theo. Có thể trình bày theo SBAR. Cần giữ tác giả/lịch sử chẩn đoán, toa và chỉ định trước đó; không ghi đè bác sĩ phát hành thành bác sĩ nhận.

Trước khi triển khai phải chốt contract và quyền liên service (Consultation, Queue, Appointment, Lab, Prescription, EMR, Notification), quyền đọc tối thiểu để B xem yêu cầu, cơ chế tiếp nhận đồng thời và lịch sử trách nhiệm. Đây là phần còn thiếu của hệ thống, chưa được thực hiện bởi việc sửa phòng trong trang admin.
