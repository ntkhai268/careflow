# Yêu cầu và đặc tả use case — R0-1.0

Đối chiếu với [báo cáo hiện trạng](../requirement-coverage-audit-2026-10-19.md). Các mã PAT/DOC/CORE/REQ giữ nguyên để truy vết; E01–E18 là phạm vi nghiệm thu trong [kế hoạch tổng](../full-requirements-plan-2026-10-19.md#9-r7--nghiệm-thu-cùng-phiên-bản-và-bàn-giao).

## 1. Phạm vi và tác nhân

Bệnh nhân dùng app native; bác sĩ dùng Hospital Web; STAFF tiếp nhận/cấp phát theo phân công; LAB_TECHNICIAN thực hiện CLS; ADMIN quản trị. ADMIN không mặc nhiên có quyền quyết định lâm sàng/ký toa. Người chưa có tài khoản có thể được STAFF tạo hồ sơ tiếp nhận tối thiểu và cấp số walk-in khi R2 hoàn tất.

Mục tiêu là đủ12service đề bài và hành trình thực. Directory/Eureka hỗ trợ thêm, Common là thư viện. Thanh toán provider/BHYT/PACS/kho thuốc đầy đủ là mở rộng. AI thuộc yêu cầu cuối nhưng thực hiện sau. CORE02 mặc định là định danh local + đối chiếu theo quyết định chủ dự án; eKYC tự động chỉ đạt nếu nhánh KYC-A được kiểm chứng riêng.

## 2. Đặc tả use case

Trong bảng: tiền điều kiện phải được server kiểm tra; hậu điều kiện là dữ liệu bền vững hoặc kết quả đọc được. Các bước là đặc tả nghiệp vụ, không thay hướng dẫn thao tác chuẩn.

| UC / yêu cầu | Actor và tiền điều kiện | Luồng chính | Hậu điều kiện | Nhánh lỗi/thay thế | Service / đợt / nghiệm thu |
|---|---|---|---|---|---|
| UC-A01 / PAT01 | Người đăng ký có email nhận thư | Yêu cầu mã → xác minh → hoàn tất tạo tài khoản | Một tài khoản PATIENT sau verify, không cấp quyền sớm | Mã sai/hết hạn/replay, email trùng, gửi lại/quota, race | Identity / R1 / E01 |
| UC-A02 / CORE01 | Người dùng ACTIVE; Face ID đã đăng ký nếu chọn | Mật khẩu hoặc credential hợp lệ → phiên ràng buộc thiết bị → đăng xuất/khóa app | Phiên/thu hồi có hiệu lực; logout có thể giữ đăng ký Face ID theo policy | Mất khóa, credential bị thu hồi, đổi tài khoản, mạng lỗi, idle; không bypass xác thực | Identity + Gateway + native / R1 / E02 |
| UC-A03 / CORE01 | Người quên mật khẩu có email tài khoản | Gửi mã → verify mã → grant dùng một lần → đặt mật khẩu | Mật khẩu mới; phiên/credential cũ bị thu hồi theo policy | Sai/hết hạn/quota/replay; không tới bước mật khẩu trước verify | Identity / R1 / E02 |
| UC-A04 / CORE01 | ADMIN được xác thực phù hợp; phân công hợp lệ | Tạo nội bộ → liên kết Directory → cấp/enroll passkey → khóa/mở | Identity và phân công nhất quán, thay đổi có audit | Directory lỗi: pending/retry rõ; khóa không phải chỉ đổi state UI; sai quyền403 | Identity + Directory / R1 / E04 |
| UC-I01 / CORE02 | PATIENT đăng nhập và đồng ý; STAFF/ADMIN được phép duyệt | OCR/QR tại máy → bệnh nhân rà ba trường → gửi → đối chiếu bản gốc/người thật tại quầy | CONFIRMED hoặc trạng thái kết thúc rõ; xóa payload theo contract | Sửa tay, từ chối, hủy, hết hạn; không tự mở EMR hoặc gọi provider ngoài | Identity / R1 / E03 |
| UC-P01 / PAT02 | Chủ tài khoản hoặc nhân viên tiếp nhận đúng phạm vi | Tạo/chọn hồ sơ; yêu cầu liên kết hồ sơ cũ/đại diện sau xác minh | Quan hệ hồ sơ được kiểm soát, có thu hồi/audit | Trùng hồ sơ, chưa đủ căn cứ → chờ xử lý, không auto merge từ thông tin tự khai | Patient + Identity / R2–R3 / E14 |
| UC-P02 / PAT04 | PATIENT sở hữu hồ sơ; file hợp lệ | Tạo health record → upload → lưu file/metadata → tải/xem/chia sẻ chủ động | Record/file đúng patient; nội dung tải khớp | Quá dung lượng/MIME, scanner lỗi, mất mạng, hủy, đổi tài khoản/dọn cache | Patient + native / R3 / E07 |
| UC-B01 / PAT03 | Hồ sơ có quyền; Directory và slot hoạt động | Chọn khoa/dịch vụ/ngày/slot → server chọn phân công/capacity hợp lệ → xác nhận | Appointment giữ snapshot giá/phòng/bác sĩ; event cấp phiếu | Slot đầy/đã qua, giá đổi, cấu hình sai, gửi lặp hoặc mất phản hồi | Appointment + Directory + Patient + Queue / R2 / E05,E10 |
| UC-B02 / PAT03 | Chủ lịch hoặc nhân viên có quyền; lịch còn hủy được | Hủy → event → phiếu/queue hội tụ CANCELLED | Không dùng lịch đã hủy để check-in/khám | Đã bắt đầu khám, gửi lặp, event trễ; không nhầm hủy với hoàn tiền | Appointment + Queue + Notification / R2 / E13 |
| UC-Q01 / PAT05,CORE03 | Lịch/phiếu hợp lệ và PATIENT có quyền; hoặc STAFF hỗ trợ theo phòng | Quét QR bằng camera → lấy GPS → kiểm tra token/geofence/phiên → check-in | Entry đã check-in một lần và đủ điều kiện gọi | QR hết hạn/sai, ngoài bán kính, GPS/camera bị từ chối, lịch hủy, lặp | Queue + Appointment + native / R2 / E05 |
| UC-Q02 / CORE04 | STAFF được phân công; bệnh nhân đến không hẹn | Tìm/tạo hồ sơ tối thiểu → cấp số walk-in → gọi → bắt đầu ca | Queue/consultation có liên kết rõ dù appointmentId=null | Trùng intake, sai phân công, hồ sơ không có tài khoản; không tạo appointment giả | Patient + Queue + Consultation / R2 / E08 |
| UC-Q03 / PAT06,CORE03 | PATIENT đọc đúng hồ sơ; DOCTOR đúng phòng | Server tính thứ tự/dự kiến theo ba lane → người có quyền gọi/start | Thứ tự/chuyển trạng thái có khóa/idempotency; mobile thấy cập nhật | Chưa check-in, caller sai, gọi đồng thời; đề xuất không đồng nghĩa tự gọi | Queue + Consultation / R2,R4 / E05,E09 |
| UC-Q04 / CORE05 | Entry CALLED đủ số lần recall; actor đúng phân công | Đánh dấu MISSED → người bệnh quay lại → requeue đúng policy → gọi lại | Entry về cuối/đầu lane theo rule, reset bộ đếm gọi | Gọi chưa đủ số lần, state sai, position thiếu ở manual, cạnh tranh; review chỉ về cuối lane | Queue + web / R2 / E09 |
| UC-Q05 / CORE06 | ADMIN; master/phân công hợp lệ | Lưu cấu hình lịch/slot hoặc queue → đọc lại → dùng cho lượt mới | Cấu hình có version/hiệu lực; lịch đã xác nhận được bảo vệ | Edit stale409, slot đóng còn booking: không âm thầm hủy; trường không tác dụng không cho chỉnh giả | Appointment + Queue + Directory / R2 / E10 |
| UC-D01 / DOC01 | DOCTOR có quyền theo ca/phân công; patient đúng | Tra EMR nội bộ và hồ sơ upload → mở file qua phiên xác thực | Xem được dữ liệu có quyền, không ghi bằng GET | Mất dependency khác rỗng; thiếu file/cách ly; người ngoài ca403 | EMR + Patient + Consultation/Prescription/Lab / R1,R3 / E07,E14 |
| UC-D02 / DOC02 | Queue đã gọi và bác sĩ có quyền | Bắt đầu ca → ghi sinh hiệu/triệu chứng/chẩn đoán → lưu | Một ca đúng queue/visit; nội dung bền vững | Ca khác đang mở, sai người/phòng, mất mạng/gửi lặp, bàn giao | Consultation + Queue + Directory / R3 / E05,E14 |
| UC-D03 / DOC04,PAT09 | Bác sĩ đang phụ trách ca; LAB được phân công | Tạo order catalog → thực hiện/finalize → đủ kết quả → đọc lại ca cũ | Order/result đúng patient; review không tạo consultation mới; native đọc đúng | Order sai điểm, chưa đủ kết quả, sửa sau finalize, event trễ/trùng, provider lỗi | Lab + Queue + Consultation + Notification / R3 / E06 |
| UC-D04 / DOC03,PAT08 | Bác sĩ phụ trách; catalog/dị ứng tải được | Nháp → kiểm tra → ký → hoàn tất → cấp phát | Toa/tác giả/trạng thái được bảo toàn; native nhận toa đúng hồ sơ | Toa kế thừa khác tác giả, đã ký, lỗi lưu hoặc chưa đọc đủ CLS; không báo complete giả | Prescription + Consultation + Queue / R3 / E05,E14 |
| UC-D05 / DOC05,PAT10 | Bác sĩ còn quyền; slot tái khám hợp lệ | Chọn ngày/slot đúng khoa/ngữ cảnh → tạo lịch liên kết ca → thông báo | Một lịch nguồn rõ; lần khám sau là ca mới | Hết chỗ/ngày nghỉ, mất phản hồi, gửi lại, bác sĩ cũ sau bàn giao; retry không trùng | Appointment + Prescription/Consultation + Notification / R3 / E11 |
| UC-N01 / PAT07 | Người dùng đúng owner và installation; OS cho phép push nếu cần | Event nghiệp vụ → inbox → realtime/push → mở app tải lại API | Inbox bền vững, read state, không gửi sai account | Token cũ, quyền từ chối, offline/background/terminated; event trùng; OS hạn chế phải ghi nhận | Notification + native / R4 / E12 |
| UC-S01 / Analytics | ADMIN hoặc vai trò được cấp; dữ liệu projection có nguồn | Chọn khoảng ngày/khoa/phòng → đọc KPI/read model | Số liệu đối soát được; có thời điểm cập nhật | Consumer lag, event lệch thứ tự/trùng, dữ liệu rỗng, rebuild | Analytics + web / R5 / E15 |
| UC-AI01 / DOC06 | Bác sĩ có quyền; bộ nguồn/mô hình đã được chọn | Hỏi theo ca → context tối thiểu → truy xuất nguồn → gợi ý → bác sĩ quyết định | Trả nguồn kiểm tra được và lưu vết có kiểm soát | Thiếu dữ liệu, sai quyền, nguồn độc hại, model lỗi → unavailable; không fallback mẫu y khoa | AI + EMR/Consultation + web / R6 / E16 |

## 3. Quan hệ và ranh giới

- Đặt khám bao gồm kiểm tra hồ sơ, master, slot và giá; cấp phiếu qua event là kết quả bất đồng bộ, không gọi là mất booking nếu consumer chưa xử lý xong.
- Khám có thể mở rộng sang CLS; đủ kết quả tạo lượt RESULT_REVIEW trong cùng consultation. Toa/tái khám là các kết quả được bảo vệ bằng quyền và idempotency riêng.
- Walk-in là một cách vào queue, không phải appointment trực tuyến với thời gian giả. Mọi cuộc khám vẫn phải có ngữ cảnh queue xác thực.
- Chatbot chỉ gợi ý, không tự thực hiện command lâm sàng. Analytics chỉ đọc/tổng hợp, không điều phối queue.
- Định danh tài khoản, chủ sở hữu hồ sơ và phân công lâm sàng là ba kiểm tra khác nhau.

## 4. Ma trận bao phủ yêu cầu

| Yêu cầu | Use case/tài liệu đáp ứng | Kiểm chứng cuối |
|---|---|---|
| REQ01 | Tất cả UC ở mục2; đặc tả từng service tham chiếu bản này | E18 |
| REQ02 | [Kiến trúc](02-service-architecture.md), [contract/state](03-contracts-and-states.md) | E17–E18 |
| PAT01 | UC-A01 | E01 |
| PAT02 | UC-P01 | E14 |
| PAT03 | UC-B01,UC-B02,UC-Q05 | E05,E10,E13 |
| PAT04 | UC-P02 | E07 |
| PAT05 | UC-Q01 | E05 |
| PAT06 | UC-Q03 | E09,E12 |
| PAT07 | UC-N01 | E12 |
| PAT08 | UC-D04 | E05 |
| PAT09 | UC-D03 | E06 |
| PAT10 | UC-D05 | E11 |
| DOC01 | UC-D01 | E07,E14 |
| DOC02 | UC-D02 | E05 |
| DOC03 | UC-D04 | E05 |
| DOC04 | UC-D03 | E06 |
| DOC05 | UC-D05 | E11 |
| DOC06 | UC-AI01 | E16 + rà soát chuyên môn |
| CORE01 | UC-A02,UC-A03,UC-A04 | E01–E04 |
| CORE02 | UC-I01; KYC-A nếu yêu cầu tự động | E03 |
| CORE03 | UC-Q01,UC-Q03 | E05 |
| CORE04 | UC-Q02 | E08 |
| CORE05 | UC-Q04 | E09 |
| CORE06 | UC-Q05 | E10 |
| Analytics Service | UC-S01 | E15 |

Các trạng thái CURRENT/PARTIAL/TARGET và bằng chứng cũ lấy từ báo cáo kiểm tra, không tự chuyển sang đạt nghiệm thu chỉ vì use case đã được đặc tả.
