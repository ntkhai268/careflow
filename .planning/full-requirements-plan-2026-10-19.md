# Kế hoạch đáp ứng đầy đủ yêu cầu CareFlow

Ngày lập:19/10/2026; cập nhật21/10/2026. **R0 chuẩn hóa; R1–R5 đã triển khai kỹ thuật, các giới hạn thiết bị/push giữ nguyên; R6 đã tích hợp AI local, chưa nghiệm thu chuyên môn/giọng bác sĩ; R7 đã kiểm thử và bàn giao kỹ thuật, nghiệm thu đầy đủ OPEN**. Xem [biên bản R7 và18ca](deployment/r7-acceptance-2026-10-21.md), [R6](ai-service/r6-acceptance-2026-10-21.md), [R5](analytics-service/r5-acceptance-2026-10-21.md), [baseline R0](architecture/05-r0-baseline-results.md). Các tiêu chí từng đợt bên dưới giữ vai trò chuẩn nghiệm thu, không được tự đánh dấu đạt từ số test.

Nguồn đối chiếu: [báo cáo kiểm tra yêu cầu](requirement-coverage-audit-2026-10-19.md). Kế hoạch này điều phối việc khép kín toàn bộ yêu cầu đề tài; [kế hoạch mobile trước đây](product-completion-plan-2026-10-19.md) và các biên bản đợt1/2 giữ vai trò lịch sử. Dùng mã R0–R7 để tránh nhầm với các đợt đã triển khai.

## 1. Mục tiêu bàn giao và quyết định kế thừa

Kết quả cuối gồm ba phần đồng bộ:

1. Đặc tả nghiệp vụ/use case, kiến trúc và hợp đồng liên service khớp phần mềm thực tế.
2. Mobile native bệnh nhân và web bác sĩ/nhân viên thực hiện được các luồng đã nêu, dùng API/dữ liệu bền vững và phân quyền thật.
3. Đủ12service theo đề bài, bao gồm Analytics và AI Clinical Assistant có chức năng được nghiệm thu.

Tiếp tục dùng iPhone làm thiết bị nghiệm thu mobile chính. Kết quả Flutter unit/widget hoặc browser emulator chỉ bổ trợ. Android phải build đạt và kiểm thử các nhánh chung; nếu đưa Android vào phạm vi phát hành thì bổ sung thiết bị Android vật lý.

AI vẫn làm ở đợt sau theo quyết định trước. Không loại AI khỏi danh sách yêu cầu cuối và không gọi bản chưa có AI là hoàn thành toàn bộ đề bài.

Định danh giữ hướng **OCR/QR trên máy + đối chiếu trực tiếp**, không dùng lại VNPT hoặc gửi ảnh CCCD tới nguồn ngoài. Đây là tiêu chí mặc định theo yêu cầu mới nhất của chủ dự án; tài liệu phải gọi đúng phương thức xác minh. Nếu tiêu chí nghiệm thu đề tài bắt buộc eKYC tự động hoàn toàn, phải hoàn tất nhánh KYC-A ở mục8 trước khi tuyên bố đạt tiêu chí đó. OCR và Face ID đăng nhập không tự chứng minh giấy tờ là thật.

Các tính năng đã có được kiểm chứng và bổ sung phần thiếu, không viết lại toàn bộ. Thanh toán điện tử/hoàn tiền, BHYT toàn diện, PACS và quản lý kho thuốc đầy đủ là phạm vi mở rộng, không tự thêm vào điều kiện hoàn thành yêu cầu vừa cung cấp.

## 2. Thứ tự và phụ thuộc

| Đợt | Kết quả cần đạt | Phụ thuộc |
|---|---|---|
| **R0 — đã chuẩn hóa** | Baseline thống nhất, ma trận yêu cầu và thiết kế cập nhật; xem biên bản R0 | Đã kiểm chứng build và ghi phụ thuộc còn thiếu |
| **R1 — triển khai test** | Quản trị tài khoản thật và thông báo lỗi đã đạt kiểm thử; nghiệm thu cảm biến/UI iPhone và passkey vật lý còn mở | R0 |
| **R2** | Lịch khám, tiếp nhận không hẹn và xử lý lỡ lượt hoàn chỉnh | R0; quyền từ R1 |
| **R3** | Hồ sơ upload tới bác sĩ, khám/CLS/toa/tái khám liền mạch | R1–R2 |
| **R4** | Đồng bộ trạng thái và thông báo native foreground/background | Event/định danh visit ổn định từ R2–R3; hạ tầng push |
| **R5** | Analytics Service và dashboard báo cáo thật | Event contract từ R0, dữ liệu nghiệp vụ R2–R3 |
| **R6** | AI Clinical Assistant đạt phạm vi gợi ý đã chốt | R3; bộ tài liệu chuyên môn và môi trường mô hình |
| **R7** | Bộ12service + web + mobile được nghiệm thu cùng phiên bản | Các đợt trước; nhánh KYC-A nếu được yêu cầu |

Chuẩn bị push/provisioning, môi trường staging và bộ tài liệu AI từ R0 để giảm thời gian chờ. Analytics có thể bắt đầu sau khi event contract ổn định; không phải chờ AI. Mỗi đợt sửa mobile đều build/cài bản mới trên iPhone và kiểm tra luồng liên quan; R7 là nghiệm thu tổng hợp, không phải lần đầu kiểm tra thiết bị.

Hai mốc theo dõi:

- **Mốc dùng thử nghiệp vụ:** R0–R4 đạt, luồng khám trên iPhone được kiểm chứng, danh tính theo đối chiếu trực tiếp. Analytics/AI còn mở phải được ghi rõ.
- **Mốc đáp ứng đầy đủ đề bài:** R0–R7 đạt và phương thức định danh đáp ứng tiêu chí nghiệm thu đã ghi trong đặc tả. Không dùng số module hoặc số test để thay kết luận này.

Không ấn định ngày hoàn tất trước khi R0 xác định baseline có thể build lại, điều kiện push và nguồn lực kiểm chứng AI. Theo dõi tiến độ bằng đầu ra/tiêu chí đạt dưới đây.

## 3. R0 — Chốt baseline và đặc tả có thể nghiệm thu

| ID | Việc làm | Đầu ra/tiêu chí đạt |
|---|---|---|
| R0-01 | Ghi manifest backend/web từ security-integration và native từ patient-mobile-ios-login; kiểm kê thay đổi chưa commit, artifact và migration | Build lại được đúng baseline; không lấy mobile cũ trong worktree backend làm bản hiện hành; có cách trở lại artifact trước đó |
| R0-02 | Đồng bộ yêu cầu, use case và trạng thái trong báo cáo cũ | Mỗi use case có actor, tiền/hậu điều kiện, luồng chính, lỗi/quyền, dữ liệu và service; bỏ nhận định AI/Analytics ngoài phạm vi bàn giao cuối |
| R0-03 | Cập nhật sơ đồ kiến trúc, deployment, sequence và state | Có12service yêu cầu + Directory/Eureka; nêu rõ ownership dữ liệu, REST/event, xác thực liên service, điểm retry và nhất quán cuối cùng |
| R0-04 | Chốt contract lịch/slot, walk-in, requeue, tái khám và event phục vụ thông báo/Analytics | Có định danh patient/visit/consultation liên kết rõ; idempotency, eventId/version, thời gian nghiệp vụ và quyền từng thao tác; không tạo appointment giả chỉ để ép luồng walk-in chạy |
| R0-05 | Chuẩn bị test fixture/môi trường và các điều kiện ngoài code | DB test riêng, dữ liệu tổng hợp, tài khoản theo vai trò; kiểm tra khả năng ký push trên iPhone, HTTPS staging/origin passkey; ghi các phụ thuộc đang thiếu |

Thiết kế mới và bản chuẩn của tài liệu nghiệp vụ đặt tại `.planning/<service>/`; sơ đồ/đặc tả tổng ở `.planning/architecture/`. Tài liệu báo cáo học thuật hiện có tham chiếu hoặc cập nhật theo bản chuẩn, tránh hai bộ quy tắc mâu thuẫn. Hướng dẫn thao tác chỉ ở `huong-dan-su-dung/`.

## 4. R1 — Quản trị và tài khoản hoạt động thật

**Cập nhật20/10/2026:** R1-01–03 đã triển khai và kiểm thử trên máy chủ test; R1-04–05 đạt regression tự động nhưng còn thao tác iPhone/giấy tờ thật, R1-02 còn passkey vật lý. Xem [biên bản R1](identity-service/r1-verification-2026-10-20.md). Chưa đánh dấu hoàn thành đầy đủ R1.

**Service/phân hệ:** Identity, Directory, Gateway/Common khi cần, Patient/EMR/Consultation để xử lý lỗi hiển thị; doctor-web và mobile native.

| ID | Việc làm | Nghiệm thu |
|---|---|---|
| R1-01 | Nối danh sách/tạo/khóa/mở tài khoản nội bộ với Identity; email/userId/role/status lấy từ server; liên kết hồ sơ bác sĩ/nhân viên Directory | Reload vẫn đúng dữ liệu; ID không lẫn doctorProfileId với identityUserId; khóa tài khoản thật chặn phiên đang dùng theo policy; người sai quyền403; lỗi server không báo thành công |
| R1-02 | Hoàn thiện cấp passkey và phân công nhân viên mới | ADMIN được xác thực phù hợp cấp quyền, nhân viên hoàn tất enrollment; tài khoản chỉ truy cập đúng vai trò/khoa/phòng; tạo dở ở một service hiển thị trạng thái chờ và có thể tiếp tục an toàn |
| R1-03 | Sửa giả thành công/dữ liệu fallback gây hiểu nhầm | Lỗi tải lịch sử hiện lỗi + thử lại; dữ liệu thiếu không biến thành tuổi/giờ/EMR giả; chỉ báo kết nối không luôn xanh; dữ liệu mẫu không dùng làm fallback khi API lỗi |
| R1-04 | Nghiệm thu đăng ký email, reset mật khẩu, Face ID và khóa thiết bị trên iPhone | Đăng ký chỉ hoàn tất sau verify; reset qua verify mã rồi mới đặt mật khẩu; code/grant hết hạn hoặc replay bị chặn; phiên/credential cũ bị thu hồi; đăng xuất→Face ID và đổi tài khoản đúng policy; không phục hồi credential đã bị server thu hồi |
| R1-05 | Nghiệm thu định danh local + nhân viên đối chiếu | Ảnh xử lý tại máy; sửa tay khi OCR sai; JSON tối thiểu sau đồng ý; người đúng quyền đối chiếu, approve/reject/withdraw/expire hoạt động; không tự liên kết hồ sơ bệnh án; Android vật lý nếu nằm trong release |

**Điểm kết thúc:** bỏ các thao tác tài khoản local giả; cấp được tài khoản nội bộ có quyền thực; auth và định danh có bằng chứng UI/API/thiết bị tương ứng. Phần cần người dùng xác thực khuôn mặt hoặc nhân viên xem giấy tờ thật phải ghi chờ nghiệm thu cho đến khi được thực hiện.

## 5. R2 — Lịch khám và hàng đợi đầy đủ

**Phân chia trách nhiệm:** Directory quản lý khoa/phòng/bác sĩ/phân công; Appointment sở hữu lịch phục vụ, slot/capacity và booking; Queue sở hữu số thứ tự, trạng thái, điều phối và chính sách lỡ lượt; Patient sở hữu hồ sơ tiếp nhận. Không lưu hai nguồn lịch làm việc có hiệu lực mâu thuẫn.

| ID | Việc làm | Nghiệm thu |
|---|---|---|
| R2-01 | Lưu cấu hình ngày làm việc, nghỉ/ngoại lệ, khung giờ, capacity; nối trang admin schedule với API | Lưu→reload→mobile tải slot thể hiện cấu hình mới; server chặn slot đóng/hết chỗ; thay cấu hình không âm thầm hủy/đổi lịch đã xác nhận; có hiệu lực và lịch sử thay đổi |
| R2-02 | Nối cấu hình Queue gồm thời gian khám dự kiến, ngưỡng gần lượt, check-in và missed policy; rà trường cũ có được scheduler sử dụng thật | UI chỉ cho chỉnh chính sách có tác dụng; lưu bền vững và kiểm tra giá trị; bảo toàn scheduler ba lane hiện hành trừ khi đặc tả đổi; không hứa tỷ lệ điều phối mà thuật toán không sử dụng |
| R2-03 | Chọn phòng/bác sĩ hợp lệ theo lịch/capacity khi một khoa có nhiều phòng | Không lỗi chỉ vì khoa có hai phòng active; phân công xác định, lịch đã đặt giữ đúng người/phòng; booking đồng thời không vượt capacity; chỉ chọn giá trị Directory đang hợp lệ |
| R2-04 | Web tiếp nhận khách không hẹn: tìm hồ sơ tối thiểu hoặc tạo mới, chọn khoa/dịch vụ/phòng, cấp số | STAFF đúng phân công cấp được lượt không có appointment; chống cấp trùng khi gửi lại; bác sĩ bắt đầu/hoàn tất consultation từ lượt này; không phải nhập UUID thủ công; không cấp STAFF quyền đọc toàn bộ EMR |
| R2-05 | Nối liên kết hồ sơ tiếp nhận với tài khoản khi có yêu cầu hợp lệ; xử lý hồ sơ trùng | Xác minh trước khi nhận quyền hồ sơ; không gộp dựa riêng tên/CCCD tự khai; có audit, trạng thái xử lý và cơ chế thu hồi. Quyền người đại diện chỉ mở khi chính sách đã rõ; chưa làm thì không hiển thị như khả năng đã hỗ trợ |
| R2-06 | Bổ sung danh sách lỡ lượt và lệnh xếp lại; sửa client gửi position theo RequeueRequest | Chuỗi gọi→gọi lại đủ số lần→MISSED→requeue→gọi lại chạy qua UI; FRONT/BACK/manual đúng policy, RESULT_REVIEW đúng quy tắc; lý do/audit nếu cần phải có contract; nhiều nhân viên không xếp/gọi trùng |
| R2-07 | Kiểm tra ngoại lệ queue/check-in | QR hết hạn/sai phòng, ngoài geofence, thiếu quyền GPS, lịch hủy, check-in lặp, bác sĩ không được phân công đều xử lý rõ; bệnh nhân thấy số/trạng thái đúng sau refresh/restart |

**Schema dự kiến:** lịch/slot/ngoại lệ và version ở Appointment; bổ sung tối thiểu policy/audit Queue hoặc liên kết hồ sơ Patient nếu contract hiện có chưa đủ. Chỉ tạo migration sau khi chốt ownership R0-04; cập nhật DBML từng service và fixture test tương ứng.

**Điểm kết thúc:** cả người có hẹn và walk-in đi từ tiếp nhận tới gọi khám qua UI; lỡ lượt quay lại hàng đợi được; quản trị thay cấu hình có tác động thật và không làm sai lịch đã tồn tại.

## 6. R3 — Hồ sơ và hành trình lâm sàng liên tục

**Cập nhật20/10/2026:** R3-01–06 đã có triển khai và kiểm chứng backend/web thật, regression PostgreSQL và native. iOS1.0.0(18) build/ký đạt ở R3; R4 đã cài/mở bản1.0.0(19) chứa source R3/R4. R3-01–02 và nhánh mobile của R3-04 còn nghiệm thu picker/xem/chia sẻ/kết quả trên máy thật; push nền thuộc R4. Chưa đánh dấu hoàn tất trọn vẹn R3. Xem [biên bản R3](consultation-service/r3-acceptance-2026-10-20.md) và [hướng dẫn20](../huong-dan-su-dung/20-ho-so-va-hanh-trinh-lam-sang-r3.md).

**Service/phân hệ:** Patient, EMR, Consultation, Prescription, Lab, Appointment, Queue, Notification; web và native.

| ID | Việc làm | Nghiệm thu |
|---|---|---|
| R3-01 | Bác sĩ xem danh sách/chi tiết/tệp hồ sơ bệnh nhân upload trong ca có quyền | Bệnh nhân upload trên iPhone→bác sĩ phụ trách xem đúng file; bytes tải đúng; bác sĩ khác/STAFF sai quyền bị từ chối; lỗi/quét file chưa xong hiển thị trạng thái phù hợp; không đưa token vào URL |
| R3-02 | Hoàn thiện native upload/mở/chia sẻ và lịch sử kết quả | iPhone chọn PDF/ảnh, tải lại sau mở app, xem/chia sẻ theo thao tác chủ động; hủy/mất mạng không báo thành công; đổi tài khoản dọn cache, không lộ hồ sơ trước |
| R3-03 | Kiểm chứng khám không CLS và có CLS trên cùng visit | Chẩn đoán/sinh hiệu lưu bền vững; chỉ định từ catalog thật; nhận đủ kết quả rồi đọc trong ca cũ; không tạo consultation thứ hai khi quay lại; ký toa/hoàn tất có kiểm tra trạng thái và quyền |
| R3-04 | Hoàn thiện tái khám từ UI bác sĩ và đường tạo từ toa | Lấy khoa/bác sĩ từ ngữ cảnh hợp lệ, chọn ngày/slot còn chỗ; bỏ giá trị cố định; giữ liên kết ca nguồn; hết slot báo lỗi có thể xử lý; gửi lại không tạo hai lịch; native thấy lịch và thông báo |
| R3-05 | Xử lý lỗi liên service khi ký toa/tạo tái khám/hoàn tất | Chốt kết quả từng bước và retry theo idempotency; không báo đã tạo lịch khi chưa tạo; không làm toa đã ký trở thành nháp chỉ vì service tái khám lỗi; lịch/ca/toa/queue hội tụ đúng sau phục hồi |
| R3-06 | Kiểm chứng bàn giao/thu hồi quyền trong ca và vòng tái khám mới | Tác giả toa/chỉ định giữ nguyên; bác sĩ tiếp quản có đúng quyền; người cũ bị chặn theo policy; lần tái khám tạo ca mới đúng lịch và quyền, không sửa trạng thái DB để ép test đạt |

**Điểm kết thúc:** từ giấy tờ bệnh nhân tải lên đến quyết định của bác sĩ và kết quả nhận trên mobile có một luồng thật liên tục; nội dung chưa tải được không bị trình bày thành không có bệnh sử.

## 7. R4–R5 — Thông báo native và Analytics

### R4 — Thông báo đến lượt hoạt động trên điện thoại

Tiếp tục21/10/2026: iOS1.0.0(20) đã cài/mở,272 mobile test đạt; hoàn thiện tap qua khóa phiên. R4 vẫn mở cho cấu hình và nghiệm thu push thật. [Biên bản mới nhất](notification-service/r4-acceptance-2026-10-21.md).

Cập nhật 20/10/2026: đã bổ sung kết nối native toàn app, tải bù, inbox và binding token/session. Chưa đóng R4-01 hoặc nghiệm thu push nền khi thiếu Firebase/APNs. Xem [thiết kế](notification-service/r4-implementation-2026-10-20.md), [biên bản](notification-service/r4-acceptance-2026-10-20.md) và [hướng dẫn21](../huong-dan-su-dung/21-thong-bao-va-dong-bo-r4.md).

| ID | Việc làm | Nghiệm thu |
|---|---|---|
| R4-01 | Chuẩn bị môi trường push iOS/Android, provisioning/entitlement và endpoint ổn định | Xác minh cấu hình với bundle thật và thiết bị, không coi bật FIREBASE_ENABLED là đã có push; config private không lên Git |
| R4-02 | Kết nối event cập nhật native, bổ sung tải bù khi reconnect/resume | Ở màn khác vẫn nhận tín hiệu cập nhật; mở lại app thấy trạng thái API mới nhất; kiểm tra event trùng/trễ/đảo thứ tự; mất kết nối có retry hợp lý và thông báo trạng thái |
| R4-03 | Nối token installation với phiên/tài khoản, vòng đời đổi token/logout/thu hồi | Đổi tài khoản không nhận thông báo tài khoản cũ; token lỗi được dọn; lỗi push không rollback lịch/queue; inbox là lịch sử bền vững |
| R4-04 | Thông báo gần lượt/đến lượt, chỉ định/kết quả/toa/tái khám và điều hướng | Thử foreground, background, app không chạy, quyền bị từ chối và từng trường hợp OS hạn chế; bấm thông báo vào đúng tài nguyên qua kiểm tra quyền; màn khóa không có bệnh án nhạy cảm; không hứa OS luôn giao tức thì |

Khi chưa đủ điều kiện push, ghi rõ blocker và tiếp tục những phần không phụ thuộc. Polling/inbox chỉ là phương án tạm, không đủ để đóng PAT07 theo trải nghiệm mobile đã đặt ra. Việc nhắc hẹn trước ngày tái khám là mở rộng nếu bổ sung; không đánh đồng event tạo lịch với reminder đến hạn.

### R5 — Analytics Service độc lập

**Cập nhật21/10/2026:** R5-01–04 đã triển khai và kiểm chứng kỹ thuật trên PostgreSQL/RabbitMQ/Gateway/browser thật ở stack Mac. Có service/schema riêng, API ADMIN, dashboard, consumer chống trùng, snapshot API nguồn tối thiểu, cursor đối soát và journal rebuild. Phạm vi/giới hạn tại [biên bản R5](analytics-service/r5-acceptance-2026-10-21.md), thao tác tại [hướng dẫn22](../huong-dan-su-dung/22-thong-ke-va-doi-soat-r5.md). Chưa triển khai production/tải lớn; không đóng các nghiệm thu iPhone/push R3–R4 còn mở.

Tạo `careflow-analytics-service`, module/build/deployment/Gateway route và dữ liệu tổng hợp do service sở hữu. Không dùng đếm danh mục Directory làm báo cáo lượt khám.

| ID | Việc làm | Nghiệm thu |
|---|---|---|
| R5-01 | Chốt KPI và cách tính | Lượt đặt/đến/hoàn tất/hủy/lỡ lượt theo ngày/khoa/phòng; thời gian chờ từ check-in hoặc requeue phù hợp đến bắt đầu phục vụ; thời gian ca khám. Quy định rõ mẫu số, ngoại lệ walk-in/lỡ nhiều lần và múi giờ |
| R5-02 | Consumer event + projection bền vững | Chống trùng theo eventId, xử lý event đến muộn/version, không tính lại lượt do retry; có backfill/reconciliation qua API/event export được phép thay vì đọc xuyên DB dịch vụ nguồn |
| R5-03 | API báo cáo và dashboard web | Lọc ngày/khoa/phòng, biểu đồ/bảng dữ liệu thật; ADMIN hoặc vai trò được cấp mới truy cập; dữ liệu rỗng/lỗi/loading khác nhau; không trả dữ liệu bệnh án cá nhân không cần thiết |
| R5-04 | Đối soát số liệu và vận hành | Dataset tổng hợp có kết quả tính trước; báo cáo khớp nguồn sau hủy, missed/requeue, event trùng, restart và rebuild; thể hiện thời điểm cập nhật/độ trễ |

**Schema dự kiến:** processed event/consumer checkpoint, projection lượt khám và bảng tổng hợp cần thiết trong DB/schema Analytics; DBML ở `.planning/analytics-service/`. Seed chỉ ở chế độ test/demo đã chọn, không trộn lượt giả vào thống kê môi trường dùng thật. Doanh thu không thuộc KPI đợt này khi chưa có nguồn giao dịch thanh toán đáng tin cậy.

## 8. R6 — AI ở đợt sau và ranh giới eKYC

### R6 — Hoàn thiện yêu cầu Chatbot gợi ý phác đồ

**Cập nhật21/10/2026:** đã triển khai tích hợp kỹ thuật local cho tra cứu nguồn có duyệt, context/quyền/lưu vết mã hóa và luồng web ghi âm → lưu DRAFT → chỉnh → ký riêng. Xem [biên bản R6](ai-service/r6-acceptance-2026-10-21.md). R6-01/R6-05 còn chờ tài liệu và nghiệm thu chuyên môn/giọng bác sĩ độc lập; không đánh dấu hoàn tất toàn bộ R6.

**Năng lực bổ sung theo yêu cầu20/10/2026:** nhập đơn thuốc bằng giọng nói từ prototype local, xem [kế hoạch bàn giao Voice Prescription](ai-service/voice-prescription-integration-plan-2026-10-20.md). Luồng VP đã được tích hợp kỹ thuật ở R6; chưa mở để sử dụng trên bệnh án thật hoặc xác nhận chất lượng chuyên môn. Luồng đã chốt: bác sĩ bật ghi âm AI trên web → bấm Lưu toa nháp (dừng ghi âm, xử lý AI và lưu DRAFT) → chỉnh sửa/thêm/xóa các thành phần trong toa nháp → lưu cập nhật → ký riêng sau kiểm tra. Không bắt duyệt AI riêng trước khi lưu nháp; Prescription tiếp tục sở hữu lưu/cập nhật/ký toa. Nhánh này không thay thế tiêu chí chatbot/phác đồ R6 bên dưới và không chứng minh model đã đạt bằng benchmark TTS.

| ID | Việc làm | Nghiệm thu |
|---|---|---|
| R6-01 | Chốt phạm vi hỗ trợ, tài liệu chuyên môn và môi trường mô hình | Xác định nhóm tình huống được hỗ trợ, tài liệu có nguồn/phiên bản/người rà soát; chọn khả năng triển khai local/self-host trước khi quyết định dùng provider; không ngầm gửi hồ sơ thật ra ngoài |
| R6-02 | Lấy ngữ cảnh ca khám theo quyền và tối thiểu dữ liệu | Server kiểm tra bác sĩ được xem ca, dữ liệu thuộc đúng bệnh nhân; không tin consultationId/pageData do client gửi như bằng chứng quyền; từ chối khi không đủ quyền/dữ liệu |
| R6-03 | Chatbot trả gợi ý có căn cứ | Truy xuất nội dung hướng dẫn đã quản lý, dẫn nguồn có thể mở/kiểm tra, nêu thông tin thiếu; gợi ý hiện trong web bác sĩ; bác sĩ chủ động quyết định, không tự ghi toa/chỉ định |
| R6-04 | Bỏ fallback y khoa mẫu, lưu vết có kiểm soát | Provider/model lỗi thì báo unavailable; không trả chẩn đoán mẫu như kết quả thật; lưu interaction theo người/ca/model/nguồn/thời điểm và retention, không chỉ RAM; không log token/dữ liệu thừa |
| R6-05 | Đánh giá chất lượng và rủi ro | Bộ ca tổng hợp có đáp án/tiêu chí do người có chuyên môn rà soát; thử thiếu dữ liệu, mâu thuẫn, prompt injection từ tài liệu, sai quyền và provider outage; kiểm tra nguồn trích dẫn tồn tại và hỗ trợ câu trả lời |

Không chốt AI chỉ bằng việc endpoint trả200 hoặc chatbot trả văn bản. Điều kiện kết thúc gồm kiểm chứng chức năng, quyền truy cập, nguồn tham chiếu và rà soát chuyên môn trong phạm vi đã chọn. Nếu chưa có người rà soát, ghi rõ mới đạt kỹ thuật, chưa đạt nghiệm thu chuyên môn.

### Nhánh KYC-A — Chỉ áp dụng nếu phải đạt eKYC tự động hoàn toàn

Nhánh này không bật lại VNPT và không thuộc thay đổi định danh mặc định hiện tại. Không ghi trạng thái tự động VERIFIED chỉ vì OCR đọc được thông tin hoặc Face ID xác thực chủ máy.

1. Đặc tả mức tin cậy cần đạt, loại giấy tờ hỗ trợ và dữ liệu được phép xử lý; đánh giá khả thi trên thiết bị/máy chủ do dự án kiểm soát.
2. Nghiên cứu độc lập các thành phần kiểm tra tính xác thực giấy tờ, chống trình diễn/liveness và đối chiếu khuôn mặt; khả năng NFC/nguồn chứng thực nếu có quyền và hạ tầng phù hợp. Phải chứng minh từng khả năng, không suy từ thư viện OCR.
3. Đánh giá bằng bộ dữ liệu được phép sử dụng, gồm chấp nhận sai/từ chối sai, ảnh in/màn hình/giấy tờ sửa và chất lượng ảnh; chốt ngưỡng theo mức rủi ro trước thử nghiệm.
4. Có trạng thái không chắc chắn và chuyển đối chiếu trực tiếp; giữ quyền vào bệnh án tách khỏi kết quả danh tính.
5. Chỉ tuyên bố đạt tự động trong phạm vi đã chứng minh. Nếu không đủ dữ liệu/nguồn chứng thực/chất lượng, giữ mốc thủ công và ghi rõ phần yêu cầu tự động còn mở.

Nhánh KYC-A có rủi ro nghiên cứu và phụ thuộc ngoài code, không thể cam kết hoàn tất như một đợt UI thông thường. Với lựa chọn hiện tại của chủ dự án, R1-05 là mốc định danh triển khai trước.

## 9. R7 — Nghiệm thu cùng phiên bản và bàn giao

Mỗi case lưu phiên bản mobile/web/backend, cấu hình liên quan đã che secret, ID dữ liệu tổng hợp, bước thực hiện, kết quả mong đợi/thực tế và bằng chứng. Hướng dẫn thực hiện nằm trong `huong-dan-su-dung/`; bảng dưới là phạm vi nghiệm thu, không thay hướng dẫn.

| Case | Hành trình/điều kiện | Yêu cầu bao phủ |
|---|---|---|
| E01 | Đăng ký email→đăng nhập→hồ sơ; mã sai/hết hạn/dùng lại | PAT01, CORE01 |
| E02 | Reset email ba bước, thu hồi phiên; logout→Face ID, đổi tài khoản, mất mạng/khóa app | CORE01 |
| E03 | OCR/QR local→đối chiếu tại quầy→kết quả; từ chối/hủy/hết hạn | CORE02 |
| E04 | Admin tạo nhân viên→cấp passkey/phân công→đăng nhập→khóa tài khoản | CORE01, quản trị thật |
| E05 | Đặt khám native→QR camera/GPS→bác sĩ khám không CLS→toa→hoàn tất→mobile xem | PAT03, PAT05–08, DOC02–03, CORE03 |
| E06 | Nhánh có CLS→kỹ thuật viên trả kết quả→bác sĩ đọc đúng ca→toa→mobile nhận kết quả | PAT09, DOC04 |
| E07 | Upload file native→bác sĩ có quyền đọc→người khác bị từ chối→đổi tài khoản | PAT04, DOC01 |
| E08 | Walk-in không tài khoản và đã có tài khoản→cấp số→bác sĩ khám/hoàn tất | CORE04 |
| E09 | Gọi lại→MISSED→requeue theo từng policy→gọi tiếp; cạnh tranh hai client | CORE05 |
| E10 | Lưu lịch/slot/queue policy→reload→booking và queue phản ánh; slot đầy/ngày nghỉ | CORE06, PAT03 |
| E11 | Tạo tái khám từ toa đúng khoa/slot→mobile thấy→lần khám mới đúng ngày | PAT10, DOC05 |
| E12 | Push gần/đến lượt khi mở/nền/không chạy; từ chối quyền, logout/token đổi | PAT06–07 |
| E13 | Hủy, gửi lặp, mất mạng/restart/dependency lỗi và sự kiện trễ | Các luồng nghiệp vụ có thay đổi trạng thái |
| E14 | Bàn giao ca, thu hồi quyền, truy cập sai hồ sơ và liên kết hồ sơ đã xác minh | PAT02, DOC01–05 |
| E15 | Analytics khớp dữ liệu nguồn theo từng KPI sau retry/backfill | Analytics Service |
| E16 | Chatbot theo ca/nguồn hướng dẫn, sai quyền, thiếu ngữ cảnh và model lỗi | DOC06, AI Service |
| E17 | Build/deploy đồng bộ, migration, health theo request nghiệp vụ, backup/restore/rollback trên test | REQ02 và khả năng bàn giao |
| E18 | Rà đặc tả/use case/sequence/state/API/DBML/hướng dẫn với artifact cuối | REQ01–02 |

Các test cạnh tranh, quota và lỗi dependency chạy trên môi trường riêng. Không reset hàng đợi chung hoặc sửa trực tiếp DB để tạo kết quả đạt. E11 có thể tự động kiểm tra thời gian bằng clock fixture ở môi trường test, nhưng vẫn phải ghi riêng nghiệm thu UI tái khám thực tế chưa thực hiện.

**Điều kiện đóng R7:** tất cả yêu cầu trong phạm vi có bằng chứng đạt; mọi lỗi chặn hành trình/quyền riêng tư/toàn vẹn dữ liệu đã xử lý; không còn nút giả thành công trong luồng sử dụng; module12service đều có chức năng được kiểm chứng; phần chưa thử trên thiết bị/ngoài code được ghi rõ và không đánh dấu hoàn thành. Có thể bàn giao mốc trung gian khi thiếu phụ thuộc, nhưng mốc đầy đủ vẫn để mở.

## 10. Ma trận yêu cầu → đợt chịu trách nhiệm

| Yêu cầu trong báo cáo rà soát | Đợt chính | Bằng chứng cuối |
|---|---|---|
| REQ01, REQ02: phân tích/use case/kiến trúc | R0, R7 | E17–E18 + tài liệu đồng bộ |
| PAT01: đăng ký thành viên | R1 | E01 |
| PAT02: hồ sơ/đại diện/liên kết an toàn | R2, R3 | E07, E14; giới hạn phạm vi được công bố |
| PAT03: đăng ký khám | R2 | E05, E10 |
| PAT04: upload hồ sơ | R3 | E07 |
| PAT05: QR check-in | R2, R7 | E05, E10 |
| PAT06, PAT07: queue/thông báo | R2, R4 | E09, E12 |
| PAT08, PAT09: toa/chỉ định/kết quả | R3 | E05–E06 |
| PAT10: nhận lịch tái khám | R3 | E11 |
| DOC01: tra cứu bệnh án | R1, R3 | E07, E14 |
| DOC02, DOC03, DOC04: khám/toa/CLS | R3 | E05–E06, E13–E14 |
| DOC05: hẹn tái khám | R3 | E11 |
| DOC06: chatbot phác đồ | R6 | E16 và kết quả rà soát chuyên môn |
| CORE01: Auth | R1 | E01–E04 |
| CORE02: định danh | R1; KYC-A nếu yêu cầu tự động | E03; thêm bộ đánh giá KYC-A nếu áp dụng |
| CORE03, CORE04: có/không có hẹn | R2 | E05, E08 |
| CORE05: lỡ lượt | R2 | E09 |
| CORE06: cấu hình queue/lịch | R2 | E10 |
| Analytics Service còn thiếu | R5 | E15 |
| Danh sách đủ12service và tương tác | R0–R7 | Manifest + E05–E18; không chỉ đếm module |

## 11. Quy tắc thực hiện và hồ sơ bàn giao từng đợt

- Trước code mới, kiểm tra nhánh/worktree theo service, bảo toàn thay đổi đang có và cập nhật main theo quy tắc dự án. Không tự commit/stage/push; không push main.
- GitNexus query/context khi tìm hiểu, upstream impact trước sửa symbol và báo HIGH/CRITICAL; detect_changes trước commit nếu được yêu cầu commit. Tạo report/plan không sửa symbol thì không cần giả lập impact.
- Chốt contract trước migration. Mỗi thay đổi schema cập nhật `.planning/<service>/<service>-db.dbml`; migration tương thích dữ liệu hiện có; test fixture/seed đúng chế độ, không seed giả OTP/chấp thuận/định danh.
- Không đưa secret, token, ảnh CCCD, thông tin người thật vào Git/evidence. Giữ cấu hình private ngoài Git, không tự thay key Resend. Nhánh AI/KYC mới phải tuân thủ quyết định dữ liệu đã chốt.
- Khi đổi UI/API/quyền/trạng thái, cập nhật trang liên quan và CHANGELOG trong `huong-dan-su-dung/`; tài khoản demo chỉ ở trang01 chuẩn. Tài liệu không ghi tính năng chưa phát hành như đã có.
- Mỗi đợt bàn giao: danh sách yêu cầu xử lý, artifact/version, migration/DBML nếu có, test phù hợp, bằng chứng runtime/thiết bị, giới hạn còn mở và cách rollback. Unit có mock không thay thế API thật; API thật không thay cảm biến/UX thiết bị.
- Trạng thái công việc: **chưa làm → đang triển khai → đạt kiểm thử code → triển khai test → đạt nghiệm thu đúng môi trường → bàn giao**. R0 đã đạt đầu ra chuẩn hóa; R1 được người dùng chấp nhận chuyển sang R2 ngày 20/10; các nhánh phần cứng chưa xác nhận riêng vẫn để mở ở R7. R2 đã triển khai/kiểm thử code, API và web, cài native17; còn nghiệm thu phần cứng iPhone. R3–R7 chưa triển khai theo kế hoạch. Tính năng có sẵn giữ kết quả trong các biên bản cũ, không tự chuyển thành chưa làm.

**Trạng thái hiện tại (20/10/2026):** R1 được chấp nhận chuyển đợt sau passkey ADMIN và phiên sinh trắc học thật. R2 đã có cấu hình lịch/queue bền vững, nhiều phòng, tiếp nhận và khám không hẹn, liên kết có xác minh/chấp thuận/thu hồi và UI lỡ lượt. Backend/API/web có bằng chứng kiểm thử; native 1.0.0 (17) đã cài/mở trên iPhone. Còn xác nhận camera/GPS và thao tác trên thiết bị theo trang19; chưa tự đóng toàn bộ E2E hoặc bắt đầu R3. Xem `.planning/appointment-service/r2-acceptance-2026-10-20.md`.
