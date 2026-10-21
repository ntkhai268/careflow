# Đối chiếu yêu cầu đề tài với CareFlow hiện tại

Ngày rà soát: 19/10/2026. Phạm vi: ba nhóm yêu cầu do chủ dự án cung cấp, gồm mobile bệnh nhân, web bác sĩ, hệ thống lõi và danh sách 12 service.

## 1. Kết luận

Hệ thống đã có phần lớn luồng khám cơ bản, nhưng **chưa đáp ứng đầy đủ toàn bộ yêu cầu**. Có 11/12 service được nêu trong đề bài có module source; **Analytics Service chưa có module độc lập**. Con số này chỉ là kiểm kê module, không phải tỷ lệ hoàn thành chức năng.

Các khoảng trống trực tiếp cần xử lý:

1. Analytics và báo cáo vận hành theo thời gian chưa được triển khai thành service.
2. Thông báo đến lượt trên native chưa hoàn thiện: inbox có API; màn queue polling 20 giây; chưa có đường nhận realtime xuyên suốt app và push nền được nghiệm thu.
3. Khách vãng lai có API tiếp nhận, nhưng web chưa có luồng tạo/tìm hồ sơ và cấp số hoàn chỉnh.
4. Lỡ lượt có logic backend, nhưng chưa có thao tác web xếp lại hoàn chỉnh; client requeue chưa khớp contract chọn vị trí.
5. Cấu hình khung giờ/check-in trên web chỉ thay state và hiện toast, chưa lưu server. Cấu hình geofence ở cùng trang có API thật, cần phân biệt hai phần.
6. Bác sĩ xem được lịch sử nội bộ nhưng chưa có giao diện xem/tải các hồ sơ cũ bệnh nhân upload.
7. Tái khám có API và hiển thị mobile, nhưng đường tự tạo từ toa còn cố định khoa/slot, chưa có lựa chọn lịch phù hợp và chưa nghiệm thu toàn chu kỳ trên iPhone.
8. AI có module/chatbot/provider client, nhưng còn fallback dữ liệu mẫu, chưa đủ để nghiệm thu gợi ý phác đồ. Theo quyết định hiện tại, tiếp tục hoãn triển khai AI.
9. Định danh hiện là OCR/QR tại máy + đối chiếu trực tiếp; đúng lựa chọn bỏ VNPT, nhưng không tương đương eKYC tự động hoàn toàn.
10. Chưa có bằng chứng nghiệm thu toàn hành trình trên iPhone bản16. Các luồng web/emulator đã đạt không tự chuyển thành nghiệm thu native.

Ngoài ra có lỗi hoàn thiện sản phẩm đáng chú ý: trang quản lý tài khoản nội bộ tạo/khóa tài khoản bằng state local, và một số lỗi tải lịch sử bị hiển thị thành không có lịch sử. Chi tiết tại mục5.

## 2. Baseline và độ tin cậy của kết luận

- **C — backend/web hiện hành:** worktree `security-integration`, nhánh `feature/dangkhoii/security-integration`, HEAD `5a8c6e5` cộng thay đổi chưa commit.
- **M — mobile native hiện hành:** worktree `patient-mobile-ios-login`, nhánh `feature/dangkhoii/patient-mobile-ios-login`, HEAD `e5fc9c1` cộng thay đổi chưa commit. Dùng `M/frontend/patient-mobile`, không dùng bản Flutter cũ trong C để kết luận về app iPhone.
- Bản native gần nhất có biên bản cài/mở: **1.0.0 (16)**. Biên bản xác nhận thử Keychain/Secure Enclave và OCR ảnh tổng hợp trên thiết bị; chưa chứng minh đăng nhập bằng mặt thật, camera căn cước thật hoặc cả hành trình khám đã đạt.
- Lượt rà này đọc source, module/routes, tài liệu thiết kế và bằng chứng kiểm thử có sẵn; không chạy lại E2E ghi dữ liệu, không gửi email/giấy tờ tới provider, không sửa nghiệp vụ, không commit/push. Kết luận lỗi tích hợp là từ source; không mô tả chúng như ca vừa tái hiện trên điện thoại.
- Đã cập nhật index GitNexus hai worktree, query các nhóm luồng và context QueueManagementService. Index có cảnh báo giới hạn liên kết khác ngôn ngữ và cắt bớt execution flows; kết luận thiếu chức năng dựa thêm vào source/controller/caller/module, không suy từ việc graph không trả kết quả.
- Bằng chứng E2E17/10 dùng Chrome + Flutter Web emulator + API/DB/broker thật. Đây là bằng chứng liên kết backend/web, **không phải E2E mobile native**. Không dùng kết quả câu hỏi bí mật lịch sử làm trạng thái auth hiện tại.

Quy ước: **Có** = có triển khai thực; **Một phần** = thiếu nhánh/tích hợp/giao diện; **Chưa có** = chưa tìm thấy triển khai trong phạm vi source đã rà; **Chờ nghiệm thu** = có code/bằng chứng riêng lẻ, còn thiếu kiểm chứng đúng môi trường.

## 3. Bảng đối chiếu nghiệp vụ

| ID | Yêu cầu | Hiện trạng | Phần còn thiếu hoặc cần nghiệm thu |
|---|---|---|---|
| REQ01 | Phân tích yêu cầu, đặc tả use case | Có tài liệu theo service và bộ Chương3 | Chưa đồng bộ toàn bộ với scope vừa cung cấp: tài liệu Chương5 loại AI/Analytics khỏi MVP trong khi đề bài vẫn yêu cầu. Auth/định danh mới cần cập nhật vào ma trận tổng. [D1] |
| REQ02 | Phân rã service, kiến trúc microservices | Có Gateway/Eureka, các module, DB/migration, REST và RabbitMQ | Sơ đồ tổng chưa thể hiện đầy đủ Directory/AI và phần Analytics còn thiếu; cần đồng bộ sơ đồ với source và trust boundary hiện hành. [D1][S1] |
| PAT01 | Đăng ký thành viên | Có đăng ký qua xác minh email | API/PostgreSQL có bằng chứng; cần nghiệm thu giao diện và nhận mail trên iPhone theo bản hiện hành. [A1] |
| PAT02 | Quản lý hồ sơ bản thân/người nhà | Có nhiều hồ sơ thuộc một tài khoản | Chưa có quy trình đầy đủ nhận hồ sơ bệnh viện cũ, xác minh quyền đại diện, xử lý trùng/merge và thu hồi quyền đại diện. Đây là phần cần cho sản phẩm thực tế, ngoài luồng tự tạo hồ sơ cơ bản. [P1] |
| PAT03 | Đăng ký khám bệnh | Có mobile nhiều bước, API thật, kiểm tra trùng/slot/capacity và snapshot giá | Còn giới hạn một phòng khám active/khoa và bác sĩ mặc định; chưa có lịch làm việc/ngày nghỉ quản trị thực. Nghiệm thu cùng native còn mở. [B1][B2] |
| PAT04 | Upload lịch sử khám/điều trị, xét nghiệm | Có form native chọn file/multipart; backend lưu record/file và kiểm tra quyền | Đã có bằng chứng web/API; nghiệm thu chọn file, mở/chia sẻ, mất mạng và đổi tài khoản trên iPhone còn mở. [P1][M1] |
| PAT05 | Quét QR tại bệnh viện xác nhận đến khám | Có native MobileScanner/GPS, server kiểm tra QR/geofence và quyền | Chưa có bằng chứng camera/GPS vật lý đầy đủ cho bản16; không coi việc dán token/tọa độ trong E2E browser là nghiệm thu quét trên máy thật. [Q1][M2] |
| PAT06 | Theo dõi lượt khám | Có trạng thái/số thứ tự/thời gian dự kiến từ API; native polling20giây khi mở màn queue | Cần đồng bộ khi quay lại app, ở màn khác, đổi hồ sơ, mất mạng và chuyển các chặng khám/CLS/nhà thuốc. [M3] |
| PAT07 | Nhận thông báo đến lượt | Có event QueueNearTurn/PatientCalled, inbox bền vững, code push sender/client | Native inbox chủ yếu fetch/refresh, chưa thấy subscription WebSocket trong M/lib; Firebase mặc định tắt và bản test chưa nghiệm thu push nền/đóng app. Chưa đạt trải nghiệm thông báo đến lượt đầy đủ. [N1][M3][M4] |
| PAT08 | Nhận toa thuốc | Có Prescription API và native hiển thị theo consultation/patient | Cần nghiệm thu native từ toa bác sĩ ký thật tới màn kết quả, đúng hồ sơ và sau đổi tài khoản. PDF toa/in không được nêu rõ trong đề bài, không tự coi là bắt buộc. [C1][M5] |
| PAT09 | Nhận chỉ định và kết quả xét nghiệm | Có Lab API, native màn CLS/kết quả | Có E2E CBC qua web/emulator; còn cần native cùng visit. PACS/imaging không phải điều kiện mặc định của yêu cầu xét nghiệm cơ bản này. [C1][M5] |
| PAT10 | Nhận lịch tái khám | Có followUpDate, FollowUpScheduled, appointment liên kết ca nguồn và native hiển thị | Đường từ toa còn cố định `NOI_TONG_QUAT`/`09:00-09:30`; cần lấy đúng khoa/phòng/bác sĩ/slot thực tế, xử lý hết chỗ/ngày nghỉ. Chưa nghiệm thu vòng khám ngày tái khám trên iPhone. [B3][M5] |
| DOC01 | Tra cứu hồ sơ bệnh án | Có EMR tổng hợp patient/dị ứng/lịch sử consultation/toa/lab; web có tab tóm tắt | Chưa nối hồ sơ upload vào giao diện bác sĩ. Lỗi tải lịch sử có thể thành danh sách rỗng rồi hiển thị như lần đầu khám. [E1][E2] |
| DOC02 | Chẩn bệnh, lưu sinh hiệu/triệu chứng/chẩn đoán | Có Consultation và web khám thực | Đã có E2E ca không CLS/có CLS và lỗi lưu; cần E2E native cùng ca, thêm nghiệm thu nhánh ngoại lệ theo phạm vi release. [C1] |
| DOC03 | Ghi toa thuốc | Có catalog/nháp/ký/sửa đổi/kiểm tra chủ ca và cấp phát | Không thiếu toàn chức năng. Cần nghiệm thu nâng bản và lỗi dependency/đồng thời trên release đã chốt. [C1] |
| DOC04 | Chỉ định xét nghiệm, nhận/đọc kết quả | Có Lab catalog/order/execution/finalize/review; trở về consultation cũ | Không thiếu luồng CBC cơ bản. Còn nghiệm thu nhiều order, lỗi/mất kết quả, các loại CLS ngoài mẫu đã thử nếu chọn đưa vào release. [C1] |
| DOC05 | Hẹn tái khám | Có API theo quyền bác sĩ phụ trách, thông tin ngày từ UI toa | Chưa có UI chọn slot/khoa phù hợp cho nhánh từ toa; hạn chế mô tả tại PAT10. [B3] |
| DOC06 | Chatbot gợi ý phác đồ | Có widget, chat/suggestions endpoint và Gemini/DeepSeek client | Chưa nghiệm thu; fallback chẩn đoán/dữ liệu mẫu, interactionStore chỉ trong RAM; chưa có quy trình chứng minh gợi ý dựa trên dữ liệu/nguồn phác đồ phù hợp. Đang hoãn theo yêu cầu. [AI1] |
| CORE01 | Auth/quản lý định danh | Có email verification/recovery, Face ID device auth, passkey staff, thu hồi phiên/thiết bị | Native Face ID bản16 cần người dùng nghiệm thu sau bật lại đăng ký đã bị thu hồi; passkey nhân viên thật và cấu hình production còn mở. Không đánh đồng có bridge với cảm biến đã đạt. [A1][A2] |
| CORE02 | eKYC | Có đọc OCR/QR local + duyệt đối chiếu tại quầy | Không có eKYC tự động chống giả/liveness/đối chiếu nguồn cấp. Đây là thay đổi scope có chủ đích để bỏ VNPT. Cần ghi rõ khi đối chiếu đề bài; không bật lại provider ngoài. [A3] |
| CORE03 | Tự động cấp số cho người có hẹn | Có Appointment event → ticket/entry; QR/quầy check-in; scheduler đề xuất lượt | Có logic thực, cần nghiệm thu gọi đồng thời, restart và native cùng ngày. Tự động xếp/đề xuất không có nghĩa tự gọi bệnh nhân khi bác sĩ chưa sẵn sàng. [Q1][C1] |
| CORE04 | Cấp số cho người không hẹn | Có PatientReceptionService và ReceptionIntakeService/API | Chưa thấy web caller tạo hồ sơ tiếp nhận hoặc POST queue entries. STAFF UI hiện tập trung phiếu/lịch đã có. Luồng walk-in qua giao diện chưa hoàn chỉnh. [Q2] |
| CORE05 | Cập nhật thứ tự khi lỡ lượt | Có recall/miss/requeue, chính sách FRONT/BACK/manual và khóa DB | Chưa thấy nơi gọi requeueEntry trong UI; hàm client gửi `{reason}` trong khi backend nhận `{position}`. Policy REQUIRE_MANUAL chưa sử dụng được đầy đủ qua client này. [Q3] |
| CORE06 | Cấu hình quản lý hàng đợi/lịch | Có API queue config và geofence; có scheduler ba lane | Web chưa gọi API queue config; trang schedule lưu slot/check-in chỉ toast. Backend vẫn dùng DEFAULT_TIME_SLOTS/capacity cấu hình. Có một phần quản trị, chưa phải cấu hình vận hành đầy đủ. [B2][Q4] |

## 4. Kiểm kê đúng 12 service đề bài

| Service yêu cầu | Module hiện tại | Đánh giá |
|---|---|---|
| API Gateway | careflow-api-gateway | Có routing/security boundary. |
| Identity & eKYC | careflow-identity-service | Có Auth và đối chiếu giấy tờ thủ công; chưa có eKYC tự động hoàn toàn. |
| Patient | careflow-patient-service | Có hồ sơ/health records/file/reception API. |
| EMR | careflow-emr-service | Có master/summary; chưa tổng hợp file upload cho bác sĩ. |
| Appointment | careflow-appointment-service | Có booking/cancel/follow-up; lịch làm việc và UI cấu hình còn thiếu. |
| Queue Management | careflow-queue-service | Có scheduler/check-in/walk-in/requeue; thiếu nối đầy đủ web. |
| **Analytics** | **Chưa có module** | Chưa thấy API/read model/báo cáo lịch sử độc lập. Dashboard đếm danh mục hoặc queue hiện tại không thay service này. |
| Doctor Consultation | careflow-consultation-service | Có khám/chẩn đoán/hoàn tất/bàn giao. |
| Prescription | careflow-prescription-service | Có toa/ký/cấp phát; follow-up từ toa còn hạn chế. |
| Laboratory Order | careflow-lab-service | Có catalog/order/result/review. Tên module ngắn khác đề bài không phải thiếu service. |
| Notification | careflow-notification-service | Có inbox/event/push infrastructure; native realtime/push chưa hoàn chỉnh. |
| AI Clinical Assistant | careflow-ai-service | Có source/prototype tích hợp; chưa nghiệm thu và đang hoãn. |

Ngoài danh sách có Hospital Directory và Eureka. Common là thư viện dùng chung, không tính là service nghiệp vụ. Không dùng số tiến trình đang chạy để kết luận đủ chức năng. Kiểm kê từ parent POM, controller/source và Gateway; không xác nhận tất cả tiến trình đang hoạt động tại thời điểm đọc báo cáo. [S1]

## 5. Mock và lỗi hoàn thiện phát hiện thêm

| Phát hiện | Hệ quả | Hướng xử lý |
|---|---|---|
| Admin accounts lấy doctor profile, tự dựng email, trộn INITIAL_ACCOUNTS; tạo và khóa bằng setAccounts | Nhìn như quản trị tài khoản thật nhưng không tạo/khóa Identity user; không thể dùng nút này làm bằng chứng vô hiệu hóa quyền | Nối Identity API thực, dùng userId/email/status từ Identity, phối hợp cấp passkey và thu hồi phiên; báo lỗi khi server không lưu. [UI1] |
| Admin schedule handleSaveConfig chỉ toast | Đổi slot/cửa sổ check-in không tác động booking/queue và mất khi reload | Contract lưu bền vững, version/hiệu lực, UI đọc lại từ API; xét lịch đã đặt trước khi đổi. Geofence đang có lưu API, không cần viết lại phần đó. [B2] |
| Lỗi tải summary/history được catch thành rỗng | Có thể khiến bác sĩ tưởng không có tiền sử/lần khám đầu tiên | Tách trạng thái không có dữ liệu và không tải được; cho thử lại, không gắn thông báo lâm sàng chắc chắn khi dependency lỗi. [E2] |
| Admin dashboard luôn ghi đã kết nối PostgreSQL; general dashboard có tuổi/giờ fallback cố định ở một số nhánh | Chỉ báo không dựa trên kiểm tra sức khỏe thực và một số dữ liệu hiển thị không đáng tin | Hiển thị không xác định nếu thiếu dữ liệu; health trạng thái phải có nguồn kiểm chứng và phân quyền phù hợp. [UI2] |
| AI fallback MOCK_RULE_BASED và lưu interactions RAM | Nội dung có thể là mẫu không liên quan ca; mất audit khi restart | Khi quay lại AI: bỏ fallback giả vào luồng dùng thật, báo unavailable, kiểm tra quyền/ngữ cảnh ca, thiết kế lưu vết/nguồn tham chiếu và đánh giá chất lượng. Chưa gọi provider trong lượt rà này. [AI1] |
| DemoJourneyRepository/mock auth/ONLINE_MOCK còn trong source test/debug | Sự tồn tại không chứng minh bản iPhone đang dùng mock | Bản phát hành có guard chống DEMO_MODE/USE_MOCK_AUTH; tiếp tục kiểm tra artifact/cấu hình. Không xóa fixture chỉ để làm sạch kết quả tìm từ khóa. [M6] |
| DoctorMockController và tên method mockEkyc còn trong source | Tên cũ dễ bị tính nhầm thành mock đang hoạt động | Danh bạ cũ đã trả410; VNPT/luồng ảnh cũ đã ngừng. Đánh giá hành vi thực và endpoint mới, không chỉ tên lớp. [A3] |

Giá dịch vụ, bệnh nhân, bác sĩ và thuốc seed trong DB test là **dữ liệu thử qua API thật**, khác response mock. Chưa được coi là danh mục đã được bệnh viện duyệt để dùng production.

## 6. Thứ tự hoàn thiện đề xuất

**Ưu tiên1 — khép kín chức năng hiện có và tránh báo thành công giả**

- Nối walk-in, requeue và cấu hình queue/slot vào web; khớp role/assignment/position contract. Thành công phải đọc lại sau reload và có từ chối quyền phù hợp.
- Nối quản trị tài khoản Identity thật; sửa phân biệt lỗi tải lịch sử với không có lịch sử.
- Bổ sung bác sĩ xem file bệnh nhân upload, giữ nguyên quyền theo chủ hồ sơ/phân công và tải qua phiên xác thực.
- Sửa tái khám từ toa dùng đúng ngữ cảnh/slot hợp lệ; xử lý lịch hết chỗ, bác sĩ nghỉ và gửi lặp.

**Ưu tiên2 — nghiệm thu mobile đúng thiết bị**

- Cùng một visit: đặt khám → QR camera/GPS iPhone → queue/call → bác sĩ chẩn đoán → CLS nếu có → đọc kết quả → ký toa → mobile thấy toa/chỉ định/lịch tái khám.
- Nhánh riêng: walk-in, lỡ lượt/xếp lại, hủy lịch, mất mạng/restart, hết phiên, đổi tài khoản/hồ sơ và thu hồi quyền.
- Hoàn thiện nhận event/refresh khi resume và APNs/FCM cho thông báo khi app mở/nền/đã đóng; kiểm tra từ chối quyền, token đổi, logout và không gửi nhầm tài khoản.
- Nghiệm thu Face ID, email/reset và đối chiếu giấy tờ theo đúng giới hạn bản16. Đây là công việc kiểm chứng, không kết luận mọi phần đều thiếu code.

**Ưu tiên3 — phần đề bài còn thiếu**

- Analytics: chốt KPI tối thiểu như lượt theo ngày/khoa, thời gian chờ thực tế, lỡ lượt/hủy, tải phòng và thời gian hoàn tất; service đọc dữ liệu tổng hợp, API phân quyền, lọc thời gian và kiểm chứng với giao dịch nguồn. Không tự thêm doanh thu nếu chưa có sổ thanh toán thật.
- Đồng bộ use case/ma trận truy vết/sơ đồ với danh sách12service và trạng thái thực; không loại AI/Analytics khỏi phạm vi bằng tài liệu MVP khi yêu cầu bàn giao vẫn giữ hai mục đó.
- AI giữ hoãn theo quyết định trước. Đưa vào danh sách chưa hoàn thành của đề bài để làm sau, không tự triển khai hoặc gửi hồ sơ bệnh nhân cho provider.
- Ghi rõ phạm vi định danh: chấp nhận OCR local + đối chiếu trực tiếp cho mốc hiện tại; nếu bắt buộc eKYC tự động thì là một hạng mục tiếp theo, không coi OCR là bằng chứng chống giả.

**Điều kiện sản phẩm thực tế, tách khỏi thiếu sót của đề bài:** môi trường staging ổn định, release đồng bộ, backup/restore DB và file, vận hành cảnh báo, kiểm thử tải/đồng thời, quản lý secret/quyền/retention và quy trình hồ sơ đại diện. Thanh toán điện tử/hoàn tiền, BHYT toàn diện, PACS và kho thuốc đầy đủ không được nêu trực tiếp trong yêu cầu vừa cung cấp; không tự dùng chúng làm lý do kết luận thiếu yêu cầu bắt buộc.

## 7. Bằng chứng tham chiếu

Các đường dẫn source dưới đây thuộc C trừ khi ghi M. Mỗi kết luận tĩnh cần được tái hiện bằng kiểm thử tích hợp khi bắt đầu sửa; lượt rà này không sửa code hoặc dữ liệu nghiệp vụ.

- **[S1]** [Parent POM](../pom.xml), [Gateway routes](../careflow-api-gateway/src/main/resources/application.yml).
- **[D1]** [Danh mục use case](../docs/report/chapter-03/01-use-case-catalog.md), [sơ đồ kiến trúc](../docs/report/chapter-03/diagrams/architecture/dgm-arc-01-logical.puml), [Chương5](../docs/report/chapter-05-implementation.md). Chương5 hiện ghi AI/Analytics là giới hạn, công nghệ/giá mẫu cũng có nội dung cũ; đây là bằng chứng lệch tài liệu, không dùng làm nguồn duy nhất kết luận code.
- **[A1]** [Biên bản đợt2](identity-service/phase2-verification-2026-10-19.md).
- **[A2]** [Biên bản sửa Face ID/OCR bản16](identity-service/iphone-faceid-document-fix-2026-10-19.md).
- **[A3]** [Contract đối chiếu local](identity-service/local-document-review-contract.md), [kiểm chứng runtime](identity-service/local-document-review-verification-2026-10-19.md).
- **[P1]** [Patient access implementation](patient-service/health-record-access-implementation.md), [HealthRecordController](../careflow-patient-service/src/main/java/com/careflow/patient/controller/HealthRecordController.java), [kế hoạch CLIN](product-completion-plan-2026-10-19.md). Kế hoạch có lịch sử các đợt, phải đọc cập nhật mới nhất.
- **[B1]** [AppointmentService](../careflow-appointment-service/src/main/java/com/careflow/appointment/service/AppointmentService.java): DEFAULT_TIME_SLOTS, createAppointment, resolveDirectoryAssignment, getTimeSlotAvailability.
- **[B2]** [Admin schedule](../frontend/doctor-web/src/app/(dashboard)/admin/schedule/page.tsx): DEFAULT_SLOTS dòng30, handleSaveConfig dòng86; [cấu hình capacity](../careflow-appointment-service/src/main/resources/application.yml).
- **[B3]** [Prescription AppointmentClient](../careflow-prescription-service/src/main/java/com/careflow/prescription/config/AppointmentClient.java): payload dòng57–62; [AppointmentService](../careflow-appointment-service/src/main/java/com/careflow/appointment/service/AppointmentService.java): createFollowUpAppointment dòng244, kiểm tra phân công dòng168.
- **[Q1]** [QueueController](../careflow-queue-service/src/main/java/com/careflow/queue/controller/QueueController.java), [QueueManagementService](../careflow-queue-service/src/main/java/com/careflow/queue/service/QueueManagementService.java), [scheduler ba lane](../careflow-queue-service/src/main/java/com/careflow/queue/service/ConsultationQueueScheduler.java).
- **[Q2]** [PatientReceptionService](../careflow-patient-service/src/main/java/com/careflow/patient/service/PatientReceptionService.java), [ReceptionIntakeService](../careflow-queue-service/src/main/java/com/careflow/queue/service/ReceptionIntakeService.java), [STAFF check-in](../frontend/doctor-web/src/app/(dashboard)/staff/checkin/page.tsx), [patient client](../frontend/doctor-web/src/lib/patient-api.ts).
- **[Q3]** [Queue client](../frontend/doctor-web/src/lib/queue-api.ts): requeueEntry dòng137; [RequeueRequest](../careflow-queue-service/src/main/java/com/careflow/queue/dto/RequeueRequest.java); [QueueManagementService](../careflow-queue-service/src/main/java/com/careflow/queue/service/QueueManagementService.java): requeue dòng771. Tìm caller toàn frontend chỉ thấy định nghĩa requeueEntry.
- **[Q4]** [QueueConfig](../careflow-queue-service/src/main/java/com/careflow/queue/domain/QueueConfig.java), [QueueController](../careflow-queue-service/src/main/java/com/careflow/queue/controller/QueueController.java): GET/PUT configs; client web chưa có các lệnh này.
- **[E1]** [EMR summary service](../careflow-emr-service/src/main/java/com/careflow/emr/service/MedicalRecordService.java), [summary DTO](../careflow-emr-service/src/main/java/com/careflow/emr/dto/response/PatientSummaryResponse.java): danh sách consultation/prescription/lab, không có uploaded records; web chưa có caller health-records/file download.
- **[E2]** [Consultation web](../frontend/doctor-web/src/app/(dashboard)/consultation/[id]/page.tsx): loadSummary dòng294–318, thông báo lịch sử rỗng dòng1415 trở đi.
- **[N1]** [NotificationService](../careflow-notification-service/src/main/java/com/careflow/notification/service/NotificationService.java), [FirebasePushSender](../careflow-notification-service/src/main/java/com/careflow/notification/service/FirebasePushSender.java), [PushDeliveryService](../careflow-notification-service/src/main/java/com/careflow/notification/service/PushDeliveryService.java).
- **[AI1]** [AiClinicalAssistantService](../careflow-ai-service/src/main/java/com/careflow/ai/service/AiClinicalAssistantService.java): provider chain, buildRuleBasedSuggestions dòng184, buildRuleBasedChat dòng215, interactionStore dòng38; [AI web widget](../frontend/doctor-web/src/components/AiAssistantWidget.tsx).
- **[UI1]** [Admin accounts](../frontend/doctor-web/src/app/(dashboard)/admin/accounts/page.tsx): INITIAL_ACCOUNTS dòng15, dữ liệu email dựng dòng85, handleConfirmToggleStatus dòng106, handleCreateAccount dòng117.
- **[UI2]** [Admin dashboard](../frontend/doctor-web/src/app/(dashboard)/admin/page.tsx), [General dashboard](../frontend/doctor-web/src/app/(dashboard)/dashboard/general/page.tsx): nhánh LAB/STAFF có tuổi30/40 và giờ08:30 fallback.
- **[M1]** M: [form upload](../../patient-mobile-ios-login/frontend/patient-mobile/lib/screens/health_record/health_record_form_screen.dart), [service upload](../../patient-mobile-ios-login/frontend/patient-mobile/lib/services/health_record_service.dart), [tải file](../../patient-mobile-ios-login/frontend/patient-mobile/lib/services/health_attachment_service.dart); [biên bản đợt1](frontend/phase1-verification-2026-10-19.md).
- **[M2]** M: [QR native](../../patient-mobile-ios-login/frontend/patient-mobile/lib/features/journey/presentation/hospital_qr_check_in_screen.dart).
- **[M3]** M: [màn queue](../../patient-mobile-ios-login/frontend/patient-mobile/lib/features/journey/presentation/clinic_queue_screen.dart): Timer20giây; [notification API](../../patient-mobile-ios-login/frontend/patient-mobile/lib/services/notification_service.dart), [inbox/controller](../../patient-mobile-ios-login/frontend/patient-mobile/lib/features/journey/application/journey_providers.dart), [màn thông báo](../../patient-mobile-ios-login/frontend/patient-mobile/lib/features/journey/presentation/journey_notification_screen.dart).
- **[M4]** M: [push client](../../patient-mobile-ios-login/frontend/patient-mobile/lib/services/push_notification_service.dart): FirebaseBootstrap.enabled mặc định false; đọc cùng phạm vi runtime trong [kế hoạch NOT01](product-completion-plan-2026-10-19.md).
- **[M5]** M: [backend journey](../../patient-mobile-ios-login/frontend/patient-mobile/lib/features/journey/data/backend_journey_repository.dart), [visit outcome](../../patient-mobile-ios-login/frontend/patient-mobile/lib/features/journey/presentation/visit_outcome_screen.dart), [lab client](../../patient-mobile-ios-login/frontend/patient-mobile/lib/services/lab_service.dart), [prescription client](../../patient-mobile-ios-login/frontend/patient-mobile/lib/services/prescription_service.dart).
- **[M6]** M: [release guard](../../patient-mobile-ios-login/frontend/patient-mobile/lib/config/release_configuration.dart), [repository selection](../../patient-mobile-ios-login/frontend/patient-mobile/lib/features/journey/application/journey_providers.dart).
- **[C1]** [E2E17/10 và giới hạn nghiệm thu](security/end-to-end-2026-10-17.md), [kết quả an toàn](security/end-to-end-2026-10-17-results.json). Bằng chứng lịch sử có giá trị theo phiên bản/nhánh đã ghi, không thay nghiệm thu bản native mới.

Hướng dẫn thao tác chuẩn tiếp tục nằm duy nhất trong [huong-dan-su-dung](../huong-dan-su-dung/README.md). Báo cáo này là đối chiếu kỹ thuật, không thay thế hướng dẫn hay biên bản nghiệm thu.
