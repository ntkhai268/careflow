# 25 — Nghiệm thu và bàn giao R7

**Cập nhật24/10:** AI API đã chuyển Docker cùng port18093, worker MLX trên Mac18370; không gọi bộ hiện tại là14 JVM host. Web/worker có cập nhật context/timing/không gian nhập toa; xem [trang26](26-voice-va-docker.md) và [biên bản](../.planning/ai-service/voice-parity-docker-2026-10-24.md). Descriptor Docker thêm vào snapshot; bundle config/TLS private cần sao lưu riêng cùng model. Chứng cứ candidate21/10/23 không tự trở thành bằng chứng source24/10; full acceptance giữ OPEN.

[Quay về mục lục](README.md)

Mốc 21/10/2026: worktree `release-r7`, nhánh `feature/dangkhoii/release-r7`, nền `907cba2` kèm thay đổi chưa commit. Bản này mang theo phần tích hợp R6; worktree R6 và nhánh develop được giữ nguyên. Đây là bản dùng thử kỹ thuật trên Mac, **chưa phải nghiệm thu đầy đủ để vận hành bệnh viện**. Xem [biên bản và bằng chứng](../.planning/deployment/r7-acceptance-2026-10-21.md).

Cập nhật23/10: web/worker đã có [bản sửa đối chiếu toa giọng nói](23-nhap-toa-giong-noi-ai.md#khi-chưa-lưu-được-toa--bản-sửa23102026), xem [biên bản bản sửa](../.planning/ai-service/voice-fix-2026-10-23.md). Candidate21/10 không còn trùng toàn bộ source hiện tại; gate xác minh source phải giữ OPEN, không tái sử dụng trạng thái PASS cũ làm nghiệm thu bản sửa.

## Phiên bản và nơi sử dụng

- Web bác sĩ/nhân viên/quản trị: `http://localhost:5173/login`. Chọn tài khoản theo [trang01](01-tai-khoan-va-du-lieu.md); không nhập thông tin người thật vào bộ thử tổng hợp.
- iPhone: CareFlow **1.0.0 (21)** đã ký, cài đè và mở, giữ bundle/dữ liệu. Dùng iOS 15 trở lên; cấu hình test vẫn `DEMO_MODE=false`, `FIREBASE_ENABLED=false`. Thao tác Face ID, camera, GPS và toàn bộ hành trình trên iPhone phải được xác nhận riêng.
- Android: APK release bản21 đã build và kiểm chữ ký bằng khóa thử riêng ngoài Git. Chưa thử máy Android thật, chưa phải bản ký để phát hành Google Play.
- Gateway test HTTPS18080; các service nội bộ18081–18093 yêu cầu client TLS. Cầu nối iPhone HTTPS18443 giữ xác minh TLS tới Gateway. Không mở trực tiếp cổng DB/service ra Internet.
- AI dùng kho hướng dẫn local và ASR local; giọng nói tạo **toa nháp có thể sửa**, ký toa là thao tác riêng của bác sĩ. Xem [trang23](23-nhap-toa-giong-noi-ai.md) và [trang24](24-tro-ly-lam-sang-r6.md). Chưa nghiệm thu hiệu quả chuyên môn hay giọng bác sĩ độc lập.

## Mở lại trên Mac đã được provision

Giữ Docker Desktop sẵn sàng. Từ worktree R7:

```sh
/usr/bin/python3 scripts/mac-demo/demo.py start
/usr/bin/python3 scripts/mac-demo/demo.py status
```

Launcher chỉ dùng các container/DB audit đã có, kiểm tra tiến trình theo JAR/cổng, không xóa volume hoặc reset hàng đợi. Nó phục hồi bản runtime riêng đã lưu khi `/tmp` bị dọn. Đây không phải bộ cài cho một máy mới; máy khác cần provision DB, TLS và secret riêng trước. R7 đã cấp lại hai chứng chỉ leaf nội bộ Analytics/AI với extension đầy đủ, giữ CA/khóa hiện có; kiểm tra chain/hostname bằng Python3.14 strict cũng đạt. Đây vẫn là bộ chứng chỉ của môi trường test.

Cầu nối iPhone và worker AI là tiến trình riêng; không suy ra chúng đã chạy chỉ vì Java health xanh:

```sh
node ~/Library/Developer/CareFlow/iphone11pm-20261018/bridge.mjs
```

Khởi động worker bằng cấu hình private theo trang23, dùng source R7 trong `PYTHONPATH`, manifest mô hình local đã kiểm hash, mTLS và token riêng. Worker sống ở18370; readiness phải báo sẵn sàng mới thử ghi âm. Không đưa token/khóa vào lệnh được chia sẻ, Git hoặc ảnh chụp. Khi AI lỗi vẫn có thể khám và nhập toa thủ công.

Mac và iPhone cùng Wi-Fi, cho phép **Mạng cục bộ** với CareFlow; xem [trang11](11-cai-app-iphone.md). Sau khi mở lại server, đóng/mở app và thử đăng nhập, tải hồ sơ/lịch hẹn. Nếu mất phiên thì đăng nhập lại; không xóa app hoặc reset DB để xử lý lỗi kết nối.

## Chạy lại các kiểm tra kỹ thuật

Chạy trên DB audit loopback của Mac hoặc CI cách ly, **không trỏ tới DB remote dùng chung**. Đặt thư mục đầu ra mới ngoài checkout, quyền truy cập riêng. Tránh chạy đồng thời hai full stack Java, build native và full Maven trên máy16GB.

```sh
python3 -m unittest discover -s scripts/release/tests -v
python3 scripts/release/verify_backend.py \
  --audit-state /tmp/careflow-dangkhoii-audit/state.json \
  --output-dir "$HOME/Library/Developer/CareFlow/r7-check-new"
```

Kết quả mong đợi: Maven `clean verify` thành công, các bộ PostgreSQL được bật, tổng test/failure/error/skipped được ghi trong JSON. Không dùng lần chạy DB unavailable hoặc test skipped để thay cho kết quả đạt. CI thủ công `R7 candidate verification` thực hiện các kiểm tra tương tự; workflow chưa chạy trên GitHub khi bản này chưa được push.

Web: `npm ci`, kiểm tra dependency, test quản lý phiên và `npm run build -- --webpack` trong `frontend/doctor-web`. Mobile: `flutter analyze` và `flutter test` trong `frontend/patient-mobile`. Bản R7 ghi riêng15 test Flutter bị bỏ qua; xem bằng chứng, không tính chúng là đã đạt.

Build iOS từ nguồn R7, cấu hình riêng và signing trong Xcode theo trang11. Với Android release, đặt `CAREFLOW_ANDROID_KEY_PROPERTIES` tới file properties riêng có `storeFile`, `storePassword`, `keyAlias`, `keyPassword`; không commit file hoặc keystore. Build thiếu cấu hình ký phải dừng, không âm thầm ký bằng debug key. Khóa dùng thử R7 chưa thay thế quy trình ký/custody của bản phát hành chính thức.

## Sao lưu, phục hồi thử và quay lại artifact trước

Sao lưu runtime hiện có bằng lệnh ở [trang00](00-chuan-bi.md). Bản sao lưu chứa khóa và dữ liệu: giữ ngoài Git, giới hạn người đọc. Sau khi thay artifact đã kiểm chứng, lưu lại snapshot runtime để lần reboot không phục hồi nhầm bản cũ.

Phép thử phục hồi có guard chỉ chấp nhận DB audit127.0.0.1:15432, thư mục mới ngoài checkout:

```sh
python3 scripts/release/restore_rehearsal.py \
  --state-file /tmp/careflow-dangkhoii-audit/state.json \
  --backup-dir "$HOME/Library/Developer/CareFlow/r7-restore-new" \
  --report "$HOME/Library/Developer/CareFlow/r7-restore-report.json"
```

Nó dùng snapshot PostgreSQL nhất quán, khôi phục vào **DB scratch mới**, so sánh số dòng/hash nội dung từng bảng rồi xóa các clone vừa tạo. Nó không khôi phục đè DB đang sử dụng. Cùng backup có JAR, TLS, các file khóa tham chiếu và tệp upload. Báo cáo không đủ để tuyên bố đã diễn tập DR production: còn cần nơi lưu độc lập, mã hóa backup, RPO/RTO, người chịu trách nhiệm và thử khôi phục trên máy khác.

Rollback R7 đã thử ở mức thay JAR Prescription trước/sau trên bản DB sao chép cùng schema; dữ liệu vẫn giữ nguyên. Queue V14 cần giữ code mới để chặn xác nhận đến sau hủy; quay lại Queue cũ chưa được nghiệm thu về invariant này. Khi triển khai thật phải kiểm tương thích schema trước, bảo toàn DB/khóa/tệp, chạy smoke quyền và đọc dữ liệu sau thay đổi.

## Hoàn tất E01–E18

Danh sách và kết quả hiện tại nằm ở [ma trận nghiệm thu R7](../.planning/deployment/evidence/r7-20261021/cases.json). Chạy các luồng đúng vai trò theo trang02–24, cùng một bệnh nhân/phiếu/ca khám; ghi expected/actual và phiên bản thật. Bằng chứng không được chứa token, OTP hoặc dữ liệu người thật.

Các bước còn cần người thực hiện:

1. Trên iPhone21: đăng ký/xác minh email, quên mật khẩu hai bước, Face ID sau logout và hủy xác thực, đổi tài khoản/khóa thiết bị; passkey nhân viên trên authenticator thật.
2. Dùng giấy tờ **tổng hợp** để thử camera/OCR/QR, kiểm tra đối chiếu trực tiếp; không gửi CCCD tới VNPT. Thử picker upload, GPS/QR check-in, nhận kết quả/toa/tái khám và quyền giữa hai tài khoản trên app thật.
3. Provision Firebase/APNs và entitlement `aps-environment` bằng team phù hợp. Thử thông báo khi app mở/nền/bị đóng, từ chối quyền, logout/đổi token. Bản21 hiện thiếu các điều kiện này nên E12 bị chặn.
4. Chuyên gia cung cấp/duyệt nguồn chuyên môn và bộ ca có đáp án; thử micro/giọng bác sĩ độc lập, đo lỗi thuốc/liều và xác nhận quy trình sửa toa. Nguồn SYNTHETIC, TTS và kiểm thử API không thay thế nghiệm thu lâm sàng.
5. Đối chiếu các ca còn PARTIAL/BLOCKED trong ma trận cuối; phần API E08–E10/E13–E15 đã có bằng chứng mới. Bổ sung các nhánh native còn thiếu và lưu bằng chứng bền vững gắn đúng candidate.

Cổng nghiệm thu kiểm đủ18ca, candidate nguồn, hash bằng chứng, xác nhận phần cứng và chuyên môn:

```sh
python3 scripts/release/acceptance_gate.py \
  .planning/deployment/evidence/r7-20261021/cases.json \
  --verify-source --output "$HOME/Library/Developer/CareFlow/r7-gate.json"
```

Hiện kết quả mong đợi là **OPEN, exit2**. Workflow deploy backend/frontend dừng ở cổng này cho tới khi toàn bộ ca đạt. Không sửa trạng thái PASS chỉ để bỏ chặn deploy; nếu source thay đổi phải tạo lại candidate và nghiệm thu phần liên quan.

## Kiểm chứng bổ sung trong lần tiếp tục R7

Backend sau bản vá xung đột ghi và thứ tự sự kiện đạt **1.000/1.000**, không bỏ qua. Hai phiên cùng xếp lại một lượt lỡ: một phiên thành công, phiên còn lại nhận409 và phải tải lại; cơ chế bảo vệ version của DB vẫn hoạt động.

Đã kiểm lại bằng Gateway/API thật trên sáu DB sao chép riêng: tiếp nhận có/không tài khoản tới bác sĩ hoàn tất; bốn nhánh xếp lại đầu/cuối/thủ công và cạnh tranh hai phiên; lưu/reload lịch phòng, ngày nghỉ, slot đầy, bảo vệ lịch đã đặt và tạm ngừng hàng đợi. Analytics được đối chiếu lại với chín chỉ số chính, tỷ lệ, thời gian/percentile và chỉ số chất lượng từ nguồn; sự kiện trùng/đến muộn, rebuild và JVM restart giữ nguyên kết quả. Chi tiết nằm trong ma trận E08/E09/E10/E15 và bằng chứng R7. Đây là kiểm chứng API; các ca bắt buộc native vẫn cần thao tác trên iPhone.

Kiểm thêm E13/E14: mất phần phản hồi201 rồi thử lại chỉ có một lịch; sự kiện hủy đến trước xác nhận không tạo phiếu chờ. V14 thêm dấu hủy bền vững, không được xóa riêng bảng này để “làm mới” hàng đợi. Đã thử nâng từ bản sao V13 với22 lịch hủy cũ, giữ nguyên nội dung các lượt khám. Bác sĩ cũ mất quyền ca/hồ sơ/Lab/PDF sau bàn giao dù vẫn đăng nhập; người nhận không được ký toa nháp của tác giả khác. Liên kết hồ sơ cần đối chiếu và chủ tài khoản chấp thuận; thu hồi chặn cả hồ sơ và lịch hẹn đã có. Bước xác nhận danh tính trong bộ thử dùng dữ liệu/attestation tổng hợp để kiểm phần mềm, chưa thay thế đối chiếu vật lý.
