# CareFlow — Kế hoạch hoàn thiện mobile và điều kiện phát hành

Ngày lập: 19/10/2026. Trạng thái: **đợt 1 đã triển khai trên môi trường test và cài app (11); các đợt tiếp theo vẫn là kế hoạch**.

## 1. Mốc hiện tại và phạm vi

- iPhone 11 Pro Max đã cài/mở CareFlow **1.0.0 (10)**. Bản (10) bỏ nút đăng nhập thay thế dư thừa, giữ icon Face ID/vân tay cạnh ô mật khẩu; 9 kiểm tra giao diện đạt.
- Backend test gồm 12 tiến trình đã cập nhật; 14 nhóm kiểm tra bảo mật qua API thật đạt. Các kết quả này không thay thế nghiệm thu cảm biến, hộp thư và hành trình trên iPhone.
- Mobile dùng API thật, `DEMO_MODE=false`, mock auth mặc định tắt; `FIREBASE_ENABLED=false`. Dữ liệu backend vẫn là dữ liệu mẫu phục vụ test.
- Source đang nằm ở các nhánh/worktree feature, có thay đổi chưa commit. Web chưa được phát hành/nghiệm thu đồng bộ như một release production.
- **AI để sau** theo yêu cầu. Không đưa AI vào tiêu chí hoàn thành các đợt dưới đây.
- **Giữ nguyên key Resend** theo lựa chọn chủ dự án; sender tiếp tục là `CareFlow <careflow@supersignal-resend.id.vn>`. Không tự thay/thu hồi khóa hoặc đưa secret vào tài liệu.

Nguồn: [báo cáo khắc phục](security/bao-cao-khac-phuc-bao-mat-2026-10-19.md), [nghiệm thu API/runtime](security/iphone-security-live-2026-10-19.md), [changelog bản cài](../huong-dan-su-dung/CHANGELOG.md).

Kế hoạch tổng ngày 14/10 là thiết kế lịch sử. Không coi mọi mục trong tài liệu cũ còn chưa làm; đối chiếu với source và bằng chứng mới trước khi mở việc. Kế hoạch này tập trung các khoảng trống đã rà ngày 19/10, không tự bổ sung hệ thống cấp cứu hoặc nghiệp vụ bảo hiểm toàn diện.

## Cập nhật đợt 1

MOB-01–04 đã có source và kiểm chứng API/web/Flutter; bản (11) đã cài/mở trên iPhone. Trình xem/chia sẻ iOS và thao tác trên các màn native mới còn chờ xác nhận. Xem [biên bản đợt 1](frontend/phase1-verification-2026-10-19.md).

**Cập nhật KYC-01 theo lựa chọn người dùng:** đã chọn VNPT gói Free; adapter REST backend, quota bền vững, chống gửi trùng và giao diện ba bước đã có source. Bản iPhone (12) đã cài/mở; chưa có token/entitlement để nghiệm thu VNPT thật, nên **KYC-01 chưa hoàn thành**. Xem [biên bản VNPT](identity-service/vnpt-ekyc-verification-2026-10-19.md).

## 2. Hai mốc bàn giao

**Mốc A — Mobile dùng thực tế:** không có nút giả thành công; bảng giá từ server; quản lý thiết bị; đăng ký xác minh email; Face ID/reset email và hành trình khám được nghiệm thu trên điện thoại; bảo mật và hạ tầng đủ cho phạm vi phát hành đã chọn. Nếu chưa tích hợp thanh toán điện tử/eKYC, giao diện phải thể hiện đúng khả năng và quy trình tại bệnh viện; không đánh dấu hai tích hợp đó đã hoàn thành.

**Mốc B — Hoàn thiện các tích hợp còn thiếu:** push nền trên thiết bị được hỗ trợ, thanh toán/hoàn tiền/đối soát, eKYC thật và các quy trình nghiệp vụ liên quan. Các mục cần nhà cung cấp chỉ hoàn thành khi có bằng chứng môi trường thật phù hợp, không chỉ sandbox hoặc mock.

Không đặt ngày phát hành cứng trước khi chốt nguồn lực, tài khoản dịch vụ và nghiệp vụ. Đơn vị theo dõi là từng hạng mục có tiêu chí đạt; mỗi đợt phải ghi phiên bản app/backend, kết quả và phần còn mở.

## 3. Thứ tự triển khai

| Đợt | Kết quả | Phụ thuộc để kết thúc |
|---|---|---|
| 0 | Chốt baseline, rà cấu hình và bộ dữ liệu nghiệm thu | Mã hiện hành, backup test, phiên bản artifact |
| 1 | Hoàn thiện các thiếu sót nhìn thấy trên mobile | Đợt 0; chốt nguồn bảng giá |
| 2 | Hoàn thiện vòng đời tài khoản và bảo mật thiết bị | Đợt 1; email thật, iPhone; quy trình passkey nhân viên |
| 3 | Hoàn thiện quy trình hồ sơ và nghiệm thu hành trình | Đợt 1–2; phân quyền/nghiệp vụ được xác nhận |
| 4 | Push thật và phát hành đồng bộ lên staging | Tài khoản Apple/Firebase, HTTPS/origin, môi trường staging |
| 5 | Thanh toán và eKYC thật | Chọn nhà cung cấp, tài khoản sandbox/live, nghiệp vụ thu/hoàn tiền và xác minh |
| 6 | Chốt production và vận hành | Các hạng mục thuộc phạm vi release đạt, kiểm tra an toàn/restore/rollback |

Các công việc hạ tầng, quét dependency và chuẩn bị tài khoản dịch vụ có thể chuẩn bị trong lúc hoàn thiện mobile. Việc triển khai code theo từng service vẫn giữ phạm vi riêng và contract rõ ràng.

## 4. Đợt 0 — Chuẩn hóa điểm xuất phát

| ID | Công việc | Tiêu chí đạt |
|---|---|---|
| BASE-01 | Ghi manifest source của mobile/Identity/Common/Gateway/backend/web; phân biệt thay đổi có trước và thay đổi mới. | Build lại được bộ artifact đã chọn; biết chính xác phiên bản nào đang chạy/cài. |
| BASE-02 | Chuẩn bị dữ liệu test riêng cho tài khoản, gia đình, tệp, lịch, CLS và toa; giữ lịch sử ca đã hoàn tất. | Có fixture đúng ngày/quan hệ, lặp lại được mà không sửa trạng thái để vượt quy trình; không dùng dữ liệu người thật. |
| BASE-03 | Kiểm tra và lưu cấu hình runtime an toàn: profile, endpoint, feature flags, TLS, sender, scanner. | Không nhầm test với production; không in secret; có backup và cách khôi phục môi trường test. |
| BASE-04 | Rà độ lệch tài liệu, bao gồm hướng dẫn cũ còn nhắc câu hỏi bí mật. | Hướng dẫn hiện hành khớp UI/API; lịch sử được ghi rõ, không hướng dẫn lại luồng đã ngừng. |

## 5. Đợt 1 — Hoàn thiện mobile trước

### MOB-01 — Tải, xem và chia sẻ tệp hồ sơ

- Thay ActionChip hiện chỉ báo tên file bằng tải bytes qua `ApiService`, kèm access token và DPoP. Không mở URL bảo vệ bằng trình duyệt bên ngoài rồi giả định trình duyệt có phiên app; không đặt token trong URL.
- Hiện tiến trình, hủy/tải lại, lỗi mất mạng/hết phiên/file đã xóa. Chỉ tải tài nguyên đúng patient/file ID được server cho phép.
- Dùng vùng tạm riêng của app để xem tệp; dọn dữ liệu tạm theo vòng đời và khi đổi tài khoản/đăng xuất. Chia sẻ/lưu ra ngoài chỉ khi người dùng chủ động chọn.
- Không tin tên tệp làm đường dẫn; giữ giới hạn kích thước và xử lý MIME nhất quán với backend.
- **Nghiệm thu:** PDF/ảnh mở được trên iPhone; nội dung khớp file gốc; tài khoản khác bị từ chối; mất mạng không tạo file được báo thành công; phiên hết hạn xử lý đúng; đổi tài khoản không thấy file tạm của tài khoản trước.
- Phạm vi: patient-mobile + patient-service nếu contract tải tệp cần bổ sung. Dự kiến không cần schema mới cho thao tác tải.

### MOB-02 — Bảng giá từ API

- Hospital Directory quản lý danh mục dịch vụ và bảng giá có thời điểm hiệu lực; API trả mã, tên, giá, đơn vị tiền, trạng thái và phiên bản giá.
- Mobile lấy dịch vụ/giá từ API; bỏ giá cố định 150.000đ. API lỗi phải thể hiện không lấy được giá, không tự điền giá cũ/fake hoặc 0đ.
- Appointment kiểm tra dịch vụ/giá tại thời điểm xác nhận. Chốt quy tắc giá thay đổi và lưu snapshot báo giá nếu giá gắn với booking, để lịch sử không đổi theo bảng giá mới.
- Có màn quản trị danh mục theo quyền nếu chưa có; chọn master bằng searchable combobox từ API.
- **Nghiệm thu:** sửa giá phía quản trị làm booking mới nhận giá đúng; booking cũ giữ snapshot theo contract; sửa số tiền trên client không ảnh hưởng giá server; dịch vụ ngừng hoạt động không đặt mới được.
- Schema dự kiến: danh mục/giá hiệu lực tại Directory và tham chiếu/snapshot tại Appointment; migration + DBML riêng từng service. Không tạo hóa đơn hay giao dịch PAID chỉ vì đã có báo giá.

### MOB-03 — Màn quản lý thiết bị

- Tận dụng API `/api/auth/devices` và API thu hồi hiện có; hiển thị các đăng ký sinh trắc học, ngày đăng ký/lần sử dụng và thiết bị hiện tại nếu xác định được từ credential ID local.
- API hiện chưa có tên/model thiết bị: không tự dựng tên iPhone cho bản ghi không biết. Nếu bổ sung nhãn, coi là metadata do client cung cấp, không phải bằng chứng tin cậy.
- Cho thu hồi thiết bị với xác nhận rõ. Thu hồi thiết bị hiện tại phải dọn phiên/đăng ký local và đưa về đăng nhập; thu hồi thiết bị khác không làm mất phiên đang dùng.
- API hiện liệt kê đăng ký sinh trắc học, không phải mọi phiên web/mật khẩu. Muốn hiển thị “tất cả phiên đăng nhập” phải thêm contract/session inventory riêng.
- **Nghiệm thu:** hai client cùng tài khoản; thu hồi một client làm access/refresh/biometric của nó mất hiệu lực; client kia hoạt động; ID thuộc tài khoản khác bị từ chối; UI phản ánh khi bản ghi đã bị thu hồi từ nơi khác.

### MOB-04 — Dọn mock và các đường truy cập cũ

- Kiểm tra caller của `DoctorMockController`; chuyển caller hợp lệ sang Directory. Loại bỏ hoặc chặn endpoint cũ theo contract đã chốt, không trả bác sĩ đầu tiên khi ID không tồn tại.
- Kiểm kê `DemoJourneyRepository`, mock auth và `ONLINE_MOCK`; giữ fixture test ở phạm vi rõ ràng, thêm kiểm tra build phát hành không bật các cờ demo.
- Không xóa toàn bộ dữ liệu test đang dùng. Tách bộ seed/test khỏi khởi tạo production; chỉ khởi tạo master đã được duyệt khi vận hành thật.
- **Nghiệm thu:** các danh sách khoa/phòng/bác sĩ/dịch vụ lấy từ API; ID sai trả đúng lỗi; bản phát hành không có luồng thanh toán hoặc đăng nhập giả; nội dung demo không lọt sang DB production.

## 6. Đợt 2 — Vòng đời tài khoản và thiết bị

| ID | Công việc | Tiêu chí nghiệm thu |
|---|---|---|
| AUTH-01 | Đăng ký email bằng challenge có hạn, giới hạn gửi/thử và mật khẩu được hash. Chỉ kích hoạt tài khoản khi xác minh thành công. Tái sử dụng hạ tầng gửi/outbox/template, tách mục đích đăng ký khỏi reset mật khẩu. | Không đăng nhập/nhận quyền bằng tài khoản chưa xác minh; mã sai/hết hạn/dùng lại bị từ chối; xác minh đồng thời không tạo tài khoản trùng; không lộ email đã đăng ký qua thông báo tùy tiện. |
| AUTH-02 | Hoàn thiện nghiệm thu quên mật khẩu trên iPhone; đồng bộ UI khi credential bị thu hồi. | Email thật có template đúng, không ảnh đính kèm lỗi; chỉ vào nhập mật khẩu sau verify; gửi lại vô hiệu mã cũ; reset thu hồi các phiên/Face ID/passkey cũ; mã/token không xuất hiện trong log hoặc ảnh nghiệm thu. |
| AUTH-03 | Chốt cấp passkey và khôi phục cho nhân viên, gồm xác minh người yêu cầu, quyền người cấp, audit và xử lý mất toàn bộ credential. | Có thể cấp lần đầu và khôi phục hợp lệ; email/password không bỏ qua yêu cầu passkey; không bật policy bắt buộc khiến nhân viên không còn đường đăng nhập. |
| AUTH-04 | Chuyển khóa DPoP native sang Secure Enclave/Android Keystore không xuất private key khi phần cứng hỗ trợ. Thiết kế di chuyển từ key phần mềm và liên kết phiên/credential mới. | Chỉ xuất public JWK/chữ ký; cùng thuật toán và proof contract server; không tự mất Face ID sau nâng app; máy không hỗ trợ có chính sách rõ; kiểm tra thay sinh trắc học, restore và mất thiết bị. |
| AUTH-05 | Chốt idle timeout theo vai trò và yêu cầu xác thực lại cho thao tác nhạy cảm; phối hợp khóa app, access/refresh và thay đổi tài khoản. | Hết thời gian nhàn rỗi xử lý đúng; tác vụ nền không vô tình kéo dài phiên; không hỏi Face ID cho mọi API thông thường; thao tác nhạy cảm cần xác nhận mới theo policy. |
| AUTH-06 | Theo dõi kết quả gửi email, bounce và retry; xác thực webhook nếu dùng; cảnh báo lỗi kéo dài. | Email gửi lỗi không bị báo đã giao tới hộp thư; không gửi mã cũ sau khi đã hết hạn/thay thế; thử lại không gửi trùng ngoài contract idempotency. |

AUTH-01 có thể cần schema challenge/verified state riêng, cập nhật `identity-db.dbml`; không dùng chung mã giữa đăng ký và quên mật khẩu. AUTH-04 cần khảo sát plugin/native bridge và thiết bị hỗ trợ trước khi cam kết toàn bộ Android; ghi rõ mức bảo vệ của fallback.

## 7. Đợt 3 — Hồ sơ và hành trình khám

| ID | Công việc | Tiêu chí nghiệm thu |
|---|---|---|
| CLIN-01 | Hoàn thiện quan hệ đại diện, claim và xử lý trùng hồ sơ: người xác minh, bằng chứng tối thiểu, quyền và thu hồi. | Nhập trùng CCCD/số điện thoại không tự nhận bệnh án; A1/A2 đúng hồ sơ; thu hồi quyền có hiệu lực; merge giữ liên kết và lịch sử, không gộp tự động. |
| CLIN-02 | Tách sửa demographics/tiếp nhận của STAFF thành API/DTO giới hạn và kiểm tra phân công. | STAFF được sửa đúng trường hành chính trong phạm vi, không đọc/sửa bệnh sử/dị ứng qua response hay request thừa; bác sĩ/ADMIN đúng ma trận đã duyệt. |
| CLIN-03 | Nghiệm thu iPhone phối hợp web nhân viên/bác sĩ/LAB/quầy thuốc trên cùng ca thật. | Đặt lịch → tiếp nhận/QR-GPS → khám → CLS nếu có → đọc kết quả → toa → nhận thuốc có ID và trạng thái nhất quán. Không tự chuyển trạng thái từ mobile để thay thao tác chuyên môn. |
| CLIN-04 | Hoàn thiện các nhánh hủy, lỡ lượt, hết chỗ, chuyển phòng/bàn giao, mất mạng và thao tác lặp. | Không tạo lịch/lượt/toa trùng; retry cho kết quả nhất quán; ca hoàn tất không bị mở lại bằng sửa dữ liệu; quyền thu hồi giữa ca được áp dụng. |
| CLIN-05 | Tái khám qua UI và nghiệm thu lần khám mới khi đến ngày thật. | Nối đúng ca nguồn và hồ sơ; không kế thừa sai quyền bác sĩ; ngày/slot còn hợp lệ; ghi riêng phần tương lai chưa chạy. |

Các thay đổi quy trình phải chốt với người phụ trách nghiệp vụ trước migration. Không suy ra chính sách lâm sàng chỉ từ dữ liệu seed. Việc chỉnh sửa demographics không được mở lại endpoint hồ sơ đầy đủ cho STAFF.

## 8. Đợt 4 — Push và staging

### NOT-01 — Push iPhone/Android

- Xác minh điều kiện tài khoản Apple, provisioning và push entitlement của bundle hiện tại; chuẩn bị APNs/Firebase private config. Không hứa push production chỉ dựa trên bản ký Personal Team hiện có.
- Đăng ký installation/token theo phiên; xử lý token thay đổi, từ chối quyền, đăng xuất và đổi tài khoản; thu hồi token cũ đúng owner.
- Push chỉ chứa nội dung tối thiểu, tránh chi tiết bệnh án trên màn khóa. Khi mở, app lấy dữ liệu qua API có kiểm tra quyền.
- Giữ inbox là nguồn thông báo bền vững; lỗi push không rollback nghiệp vụ khám.
- **Nghiệm thu:** foreground/background/app đã đóng, quyền từ chối, token hết hạn, đổi tài khoản, gửi trùng; không gửi dữ liệu tài khoản cũ tới người vừa đăng nhập.

### REL-01 — Phát hành web/backend/mobile đồng bộ trên staging

- HTTPS domain ổn định thay phụ thuộc Mac/mạng `.local`; đúng API origin, passkey RP ID và cookie/BFF.
- Đóng gói đúng baseline, migration theo thứ tự, health/readiness có request nghiệp vụ kiểm chứng; health UP riêng lẻ không chứng minh route TLS hoạt động.
- E2E web nhiều tab, refresh đồng thời, logout/đổi tài khoản, CSRF và quyền 403; E2E mobile trên cùng staging.
- **Nghiệm thu:** artifact/version truy vết được; mất mạng/restart không tạo trạng thái giả; không còn yêu cầu Mac của lập trình viên để dùng bản staging; có quy trình rollback đã thử.

## 9. Đợt 5 — Tích hợp cần nhà cung cấp

### PAY-01 — Thanh toán, đối soát và hoàn tiền

1. Chốt phạm vi: phí đặt khám hay cả CLS/thuốc; thời điểm thu; tiền mặt/chuyển khoản; hủy/no-show; hoàn một phần/toàn bộ; hóa đơn cần phát hành loại nào.
2. Chọn provider và tài khoản sandbox/live. Chốt service sở hữu payment/order/receipt trước schema; mobile chỉ gửi yêu cầu và đọc trạng thái, không là nguồn xác nhận thanh toán.
3. Dùng bảng giá/snapshot MOB-02. Tạo giao dịch với idempotency key; xác minh chữ ký callback/webhook, số tiền, đơn vị tiền và liên kết order.
4. Hoàn thiện trạng thái chờ/thành công/thất bại/hết hạn/hoàn tiền, đối soát webhook mất/trễ/trùng và callback tới sai thứ tự.
5. Có thao tác thu tại quầy theo quyền nếu nằm trong phạm vi, audit thu/hoàn tiền và chứng từ đúng nghiệp vụ.

**Nghiệm thu:** giả redirect “thành công” không biến giao dịch thành PAID; webhook giả/sai tiền bị từ chối; trùng webhook không thu/phát quyền hai lần; hủy/mất mạng/hoàn tiền có kết quả nhất quán; đối soát phát hiện giao dịch lệch. Sandbox đạt trước, sau đó kiểm tra live trong phạm vi số tiền/tài khoản được chủ dự án chấp thuận cụ thể.

### KYC-01 — Xác minh danh tính thật

- Chốt mục đích cần eKYC và provider; dữ liệu gửi/lưu, thời hạn lưu và người được xem kết quả. Không gửi dữ liệu người thật cho provider khi chưa có phạm vi được phép.
- Thay response mock bằng trạng thái rõ: chờ/đạt/không đạt/cần xem xét; xử lý provider timeout, retry và kết quả không chắc chắn.
- Tách kết quả xác minh danh tính khỏi quyền vào hồ sơ. eKYC thành công không tự merge hoặc cấp quyền bệnh án nếu chưa qua CLIN-01.
- **Nghiệm thu:** sandbox với dữ liệu test, callback xác thực, thất bại an toàn, quy trình xem xét; bỏ hoàn toàn kết quả danh tính cố định khỏi đường production. Luồng thật chỉ nghiệm thu với tài khoản/provider và dữ liệu được phép.

## 10. Đợt 6 — Điều kiện bảo mật và vận hành

| ID | Công việc | Bằng chứng hoàn thành |
|---|---|---|
| SEC-01 | Rà ba advisory Spring theo artifact mới nhất; chọn nâng framework hoặc bản vá được hỗ trợ. Quét thêm image/JDK/native SDK. | SBOM và kết quả scan của artifact cuối; mọi cảnh báo còn lại có điều kiện, owner và thời hạn xử lý. Không coi gate ngoại lệ là đã vá. |
| SEC-02 | Rate limit theo ingress/IP và account, nhiều instance; ngân sách request trước BCrypt; load test và NAT/proxy. | IP giả từ header không đổi quota; đo tải hợp lệ và tải dò; không khóa hàng loạt người dùng cùng NAT ngoài policy; cache/bộ đếm không tăng vô hạn. |
| SEC-03 | ClamAV với signature cập nhật; giới hạn/quota file; kiểm kê file cũ, quét lại hoặc cách ly theo kết quả. | File thử an toàn/EICAR trong môi trường cách ly, scanner ngừng chạy, quota, file giả và truy cập khác chủ đều xử lý đúng; không chạy malware thật. |
| SEC-04 | Khóa/secret private và quyền tối thiểu; diễn tập key rotation, thu hồi credential; tách quyền DB migration/runtime. | Consumer không có private signing key; khóa cũ/mới có thời gian chuyển hợp lý; dịch vụ không chạy tiếp với cấu hình secret/TLS sai. Resend vẫn giữ theo quyết định hiện tại, không tự xoay. |
| SEC-05 | Lưu audit có kiểm soát sửa/xóa, cảnh báo bảo mật, delivery và DLQ. | Theo dõi được actor/action/resource/correlation; không chứa token/OTP/body bệnh án; cảnh báo được thử, có người tiếp nhận. |
| SEC-06 | TLS DB production, mTLS/service credentials, firewall, volume bền vững, backup/PITR/restore, rollback/HA theo nhu cầu. | Diễn tập phục hồi trên môi trường riêng, đo RPO/RTO được chốt; app đọc lại dữ liệu/file sau thay tiến trình/container; có runbook. |
| SEC-07 | Chốt retention, xóa/xuất dữ liệu và phân quyền vận hành; kiểm soát dữ liệu qua provider. | Policy nghiệp vụ và cấu hình thực tế thống nhất; không giữ file/OTP/log vô thời hạn do mặc định. |
| SEC-08 | Kiểm tra cấu hình phát hành và pipeline thật. | Production không bật demo/test/mock; đăng ký chưa xác minh bị chặn; passkey nhân viên chỉ bật sau AUTH-03; ảnh container/CI/artifact truy vết được, không deploy thẳng main tự động. |

SEC-01/02/03/04 có thể thực hiện sớm, không phải chờ tới cuối mới bắt đầu. Chỉ phát hành khi các mục thuộc phạm vi đã chốt có bằng chứng đạt hoặc quyết định xử lý rủi ro cụ thể; không gắn nhãn “an toàn tuyệt đối”.

## 11. Quy tắc nghiệm thu và bàn giao mỗi đợt

- Mỗi hạng mục ghi một trong các trạng thái: chưa làm / đang làm / source đạt / đã triển khai test / đã nghiệm thu máy thật / đã phát hành. Không gộp chúng thành “đã xong”.
- Kiểm tra phù hợp phạm vi: unit/contract cho logic, integration với DB/broker riêng, E2E HTTP/UI cho liên kết service, thao tác cảm biến/nhận email trên máy thật. Không tạo test chỉ để khẳng định một dòng UI đã xóa.
- Tối thiểu có ca thành công, từ chối quyền, hết phiên, lỗi mạng, gửi lặp và đổi tài khoản cho luồng có liên quan. Không chạy test phá hủy trên DB remote đang dùng chung.
- Source vào nhánh feature theo service; pull main trước code, GitNexus impact trước sửa symbol và báo rủi ro HIGH/CRITICAL. Không tự stage/commit/push khi chưa được yêu cầu.
- Schema có migration + DBML service, kiểm tra dữ liệu cũ và tương thích khi nâng app/backend khác thời điểm. Không seed OTP, secret hoặc dữ liệu người thật.
- Hướng dẫn thao tác duy nhất ở `huong-dan-su-dung/`, cập nhật README/CHANGELOG và trang liên quan khi triển khai. Tài liệu này là kế hoạch thiết kế, không thay hướng dẫn sử dụng.
- Bàn giao gồm artifact/version, kiểm tra đã chạy, bằng chứng, giới hạn còn lại và cách rollback. Mỗi đợt mobile được build/cài trên iPhone đã kết nối khi thiết bị sẵn sàng.

## 12. Các quyết định cần chốt đúng thời điểm

| Quyết định | Đề xuất ban đầu | Khi cần chốt |
|---|---|---|
| Bảng giá | Directory quản lý giá hiệu lực, Appointment lưu snapshot báo giá | Trước schema MOB-02 |
| Thuộc tính thiết bị | Dùng metadata tối thiểu; không hứa nhận diện model nếu API chưa có | Khi thiết kế MOB-03 |
| Phục hồi tài khoản nhân viên | Có người phụ trách xác minh và audit; email không tự bỏ passkey | Trước AUTH-03 và production policy |
| Quyền người đại diện/merge | Cấp quyền sau xác minh; có thu hồi và lịch sử | Trước CLIN-01 |
| Apple/Firebase | Kiểm tra khả năng provisioning và chuẩn bị config private | Trước nghiệm thu NOT-01 |
| Payment provider/quy tắc thu tiền | Chốt phí nào thu qua app và trách nhiệm đối soát trước chọn provider | Trước PAY-01 |
| eKYC provider/phạm vi dữ liệu | Chỉ tích hợp theo mục đích đã xác nhận | Trước KYC-01 |
| Staging/production | Domain ổn định, đơn vị vận hành, ngân sách và mục tiêu phục hồi | Trước REL-01/SEC-06 |

Các quyết định này không cản việc bắt đầu MOB-01, thiết kế MOB-03 và nghiệm thu AUTH-02. Chỉ yêu cầu lựa chọn khi nó ảnh hưởng contract hoặc tích hợp thực tế; không cần dừng toàn bộ kế hoạch để hỏi tất cả ngay từ đầu.

## 13. Đợt nên bắt đầu

Thực hiện **BASE-01–04 → MOB-01 tải tệp → MOB-03 quản lý thiết bị → MOB-02 bảng giá → AUTH-01 xác minh đăng ký**, đồng thời nghiệm thu Face ID và AUTH-02 trên iPhone hiện có. Sau đó hoàn thiện nghiệp vụ hồ sơ, staging/push và các tích hợp provider. Duy trì backlog bảo mật SEC xuyên suốt, AI tiếp tục hoãn.


## Tiến độ đợt 2 — 19/10/2026

| Mục | Đã triển khai/kiểm tra | Chưa nghiệm thu |
|---|---|---|
| AUTH-01 | Challenge đăng ký riêng, mã/email quota, chỉ tạo PATIENT sau verify, race PostgreSQL; UI hai bước | Email/inbox thực tế và đăng ký trên UI iPhone |
| AUTH-02 | Recovery ba API, template không ảnh, reset hủy phiên/Face ID/passkey/grant chờ; kiểm thử DB và client | Người dùng xác nhận nhận thư/luồng reset trên iPhone |
| AUTH-03 | ADMIN fresh passkey + kiểm tra danh tính + hai mã riêng; bootstrap operator; backup và chống gỡ khóa cuối; WebAuthn thật với CTAP2 ảo | Cấp ADMIN/nhân viên thực tế và diễn tập khóa vật lý trước bật bắt buộc trên môi trường dùng chung |
| AUTH-04 | Bridge iOS Secure Enclave/Android Keystore; di chuyển khóa và xử lý mất phản hồi; chữ ký ES256 đạt trên iPhone thật bằng namespace test | Thay bộ sinh trắc học, restore/mất máy, nghiệm thu phần cứng Android |
| AUTH-05 | Patient idle 30 phút/staff 15 phút, app nền 60 giây; activity từ cử chỉ; refresh không làm mới tuổi xác thực; khóa UI khi hết phiên | Nghiệm thu cảm biến Face ID và idle dài trên thiết bị thật |
| AUTH-06 | Provider ID, trạng thái gửi/giao riêng, webhook Svix ký/idempotent, retry và bảng tình trạng ADMIN | Callback HTTPS công khai/Resend events thật; cảnh báo vận hành chủ động ngoài trang quản trị |

Source chưa commit/push; backend test trên Mac đã áp dụng V16, bản iPhone (13) đã cài/mở. Secure profile vẫn yêu cầu staff passkey; ngoại lệ mật khẩu demo cục bộ chưa bị tắt trước khi có ADMIN với khóa thực tế. Báo cáo: `identity-service/phase2-verification-2026-10-19.md`; thao tác chuẩn: `../huong-dan-su-dung/16-bao-mat-dot-2.md`. VNPT và AI tiếp tục hoãn theo yêu cầu.


## Bổ sung 19/10/2026 — Tiếp tục KYC-01, bản (14)

Người dùng đã điền thông tin VNPT ngoài Git và yêu cầu tiếp tục eKYC. Token/gói Free và quyền API đã xác minh bằng tài khoản thật; hai Identity đã bật. Kiểm tra âm tính thật dùng hai request, từ chối đúng và chống gửi trùng đạt; iPhone (14) đã cài/mở. KYC-01 vẫn chờ nghiệm thu thành công với căn cước/selfie thật trên điện thoại, hiệu quả liveness và điều kiện lưu trữ/đồng ý trước phát hành. Không tự cấp quyền bệnh án. Ba email thật đã được Resend báo Delivered; nghiệm thu inbox/UI còn chờ người dùng. AI tiếp tục hoãn. Bằng chứng: [VNPT live](identity-service/vnpt-ekyc-live-verification-2026-10-19.md).


## Quyết định mới nhất 19/10/2026 — Bỏ VNPT, đối chiếu tại quầy

Theo yêu cầu riêng tư của người dùng, mốc VNPT trước đó đã được thay thế: không tiếp tục nhà cung cấp ngoài. Bản15 đọc giấy tờ trên điện thoại và chỉ gửi ba trường đã kiểm tra. V17 lưu mã hóa và quy trình STAFF/ADMIN đối chiếu bản gốc đã triển khai; không tự đánh dấu VERIFIED hoặc cấp quyền bệnh án. API/web được nghiệm thu bằng dữ liệu giả, camera/OCR và quy trình thật còn chờ người dùng xác nhận. KYC tự động hoàn toàn tiếp tục là phần tương lai, không xem là hoàn thành. Xem [báo cáo hiện hành](identity-service/local-document-review-verification-2026-10-19.md) và [hướng dẫn chuẩn](../huong-dan-su-dung/15-doc-giay-to-va-doi-chieu.md).
