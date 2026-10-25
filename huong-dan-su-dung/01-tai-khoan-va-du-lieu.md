# 01 — Tài khoản và dữ liệu demo

[Quay về mục lục](README.md) · [Tiếp: hình thức đăng nhập](02-dang-nhap-mfa.md)

Các tài khoản dưới đây thuộc **DB audit cục bộ**, cùng mật khẩu demo **`Password123@`**, chỉ còn đúng nếu chưa đổi mật khẩu. MFA đã được gỡ ngày 17/10/2026. Không phải thông tin production. Secret TOTP, OTP, token và mã khôi phục không lưu trong hướng dẫn.

## Bộ demo chính — Nội tổng quát, ROOM-01

Dùng bộ này khi trình diễn luồng chính. Mỗi vai trò dùng **hồ sơ trình duyệt riêng**, không đăng nhập bác sĩ và STAFF trong hai tab chung một hồ sơ Chrome. Mật khẩu chung ở đầu trang.

| Mục đích | Tài khoản | Phân công/dữ liệu hiện tại |
|---|---|---|
| Bệnh nhân trên Điện thoại 01 | `mobile_case01` | PATIENT, hồ sơ Mẫu Mobile 01; lượt kiểm tra giao diện **NOI-006**, 17/10/2026, **15:30–16:00**, ROOM-01 đã COMPLETED; tại mốc đối chiếu toa CONFIRMED để STAFF cấp phát |
| Bác sĩ khám | `an@careflow.vn` | DOCTOR, BS. CKI Nguyễn Văn An, Nội tổng quát, ROOM-01; không còn ca thử rỗng IN_PROGRESS chặn gọi |
| Tiếp nhận và phát thuốc | `staff` | STAFF, ROOM-01 và PHARMACY-MAIN-01; vào được Tổng quan, check-in và quầy thuốc |

Phiếu NOI-001 cùng bệnh nhân đã kiểm tra xong: khám/lưu/ký toa/hoàn tất/phát thuốc, toa DISPENSED. NOI-003 cũng đã COMPLETED nên không dùng làm ca đang kê toa. Lượt NOI-006 có Consultation `714f1ec6-2b9f-4a67-a9d7-5a4e48785c23`: đã thêm thuốc và lưu nháp thật để kiểm tra nút ở cửa sổ thấp/hẹp; sau đó toa chuyển CONFIRMED và ca COMPLETED trong lúc tiếp tục demo. STAFF có thể tiếp tục cấp phát nếu toa chưa DISPENSED. Không mở lại ca đã hoàn tất hoặc ký lại toa đã CONFIRMED. Đọc trạng thái thật trước khi thao tác; tạo lượt hợp lệ mới khi muốn demo lại từ bước khám.

Lịch Ngoại ngày 16/10 của bệnh nhân đã hủy qua Mobile. Ca thử rỗng cũ của bác sĩ An đã đổi Consultation sang CANCELLED qua API của chính bác sĩ, giữ bản ghi lịch sử. Không reset DB/hàng đợi hay đổi quyền tài khoản để bỏ qua kiểm tra nghiệp vụ.

## Bộ E2E đối chiếu ngày 17/10/2026 — ROOM-11

Các tài khoản sau đã được tạo qua API thật; phân công và hồ sơ đã lưu DB. Mật khẩu chung ở đoạn đầu trang này. Tên và bệnh án là dữ liệu tổng hợp để thử phần mềm.

| Luồng | Tài khoản | Vai trò/phân công và trạng thái sau kiểm thử |
|---|---|---|
| Không CLS | `e2e_1710_no_cls` | PATIENT, CXK-001, khám hoàn tất/toa đã phát |
| Có xét nghiệm | `e2e_1710_cls` | PATIENT, CXK-004, CBC đã đọc/khám hoàn tất/toa đã phát |
| Bàn giao + xét nghiệm | `e2e_1710_handover` | PATIENT, CXK-002, A → B → C → B, khám hoàn tất/toa B đã phát |
| Hoàn tất sau ký toa | `e2e_1710_finalize` | PATIENT, CXK-005, thử lưu thất bại thật rồi hoàn tất/phát thuốc thành công |
| Hủy lịch | `e2e_1710_cancel` | PATIENT, CXK-003 đã hủy cả lịch và phiếu |
| Gia đình/tệp hồ sơ | `e2e_1710_family` | PATIENT, hai hồ sơ thuộc cùng tài khoản; có tệp đã upload/download |
| Bác sĩ gốc | `e2e_1710_doctor_a` | DOCTOR, ROOM-11 Cơ xương khớp, bác sĩ mặc định nhận lịch mới |
| Bác sĩ nhận | `e2e_1710_doctor_b` | DOCTOR, ROOM-11, phụ trách cuối ca bàn giao, tác giả toa riêng |
| Bác sĩ điều phối | `e2e_1710_doctor_c` | DOCTOR, ROOM-11, có quyền điều phối cùng phạm vi |
| Tiếp nhận/phát thuốc | `e2e_1710_staff` | STAFF, ROOM-11 và PHARMACY-MAIN-01 |
| Xét nghiệm | `e2e_1710_lab` | LAB_TECHNICIAN, LAB-HEMATOLOGY-01 |
| Quản trị | `e2e_1710_admin` | ADMIN; mật khẩu chung đã được khôi phục sau thử Quên mật khẩu |
| Đăng nhập thay thế mobile | `e2e_auth_20261017` | PATIENT riêng, mật khẩu hiện tại `NewPassword1710@`; chưa có hồ sơ khám |

Câu hỏi bí mật đã ngừng hỗ trợ, không dùng để đăng nhập hoặc khôi phục. Bệnh nhân dùng mật khẩu hoặc Face ID/vân tay đã thiết lập; quên mật khẩu chỉ qua email. Nghiệm thu passkey nhân viên và cảm biến thực tế theo hướng dẫn 12/13.

Các ca CXK đã hoàn tất dùng để xem lịch sử. Muốn chạy từ đầu, tạo lịch/hồ sơ hợp lệ mới theo ngày thực tế; không mở lại ca đã đóng bằng SQL. Ca gốc cũ bên dưới được giữ nguyên.

## Dữ liệu thử voice — 23/10/2026

Dùng bác sĩ `an@careflow.vn`, mật khẩu demo chung ở đầu trang; phân công Nội tổng quát, ROOM-01. Bệnh nhân thuộc tài khoản `mobile_case03`, cùng mật khẩu demo. Đây là dữ liệu tổng hợp, không phải bệnh án hoặc đơn thuốc điều trị thật.

- **TEST VOICE NOI MOT**, phiếu **NOI-002**: lượt không hẹn đã gọi/bắt đầu khám, Consultation `a681e915-fc33-45cf-b0ad-5e792588e535` đang IN_PROGRESS. Lúc chuẩn bị chưa có toa; mở [ca thử voice](http://localhost:5173/consultation/a681e915-fc33-45cf-b0ad-5e792588e535) để ghi âm và lưu nháp.
- **TEST VOICE NOI HAI**: hồ sơ dự phòng, chưa đặt lịch/chưa cấp phiếu; chọn hồ sơ này trên app khi muốn tạo lịch mới. ROOM-01 có thêm hai slot **22:00–22:30**, **22:30–23:00 ngày23/10**, mỗi slot5 chỗ lúc kiểm tra. Ngoại lệ chỉ áp dụng ngày này; giữ lịch sáng/chiều và các ngày khác. Slot còn chỗ không đồng nghĩa đã đủ điều kiện check-in.
- Hai hồ sơ tiếp nhận sơ bộ **TEST VOICE CA MOT/CA HAI** ở ROOM-11: CA MOT đã được bác sĩ C dùng kiểm bản sửa voice, toa thử đã hủy và ca/phiếu đã hoàn tất qua API; CA HAI còn lượt chờ. Không dùng làm ca chính của buổi thử này. Bác sĩ A/B còn ca cũ chưa hoàn tất từ21/10; không hủy/đóng các ca cũ để ép gọi mới.

Đã kiểm qua phiên web thật: bác sĩ An đọc được đúng ca, nút ghi âm/transcript bật; worker local readiness đạt và job transcript nhận đúng MED001. Không lưu sẵn toa để giữ ca trống cho người thử; micro vật lý/độ chính xác giọng nói vẫn cần xác nhận. Sau khi đã lưu toa, sửa toa hiện có; muốn ghi âm lại từ đầu thì hoàn tất ca đúng quy trình và tạo lượt hợp lệ mới. Xem [trang23](23-nhap-toa-giong-noi-ai.md).

Cập nhật24/10: CA HAI ở ROOM-11 và **TEST VOICE PARITY RELOAD** được dùng kiểm audio/Docker/sửa toa/refresh trên ca riêng của bác sĩ C; toa thử hủy, ca/phiếu hoàn tất qua API. Không dùng các ca đó làm lượt mới. Ca chính của bác sĩ An giữ riêng; mở link trực tiếp ở trên nếu ca23/10 không còn trong bộ lọc hôm nay. Slot22:00–23:00 của23/10 đã hết hiệu lực; đặt mới24/10 phải chọn lịch còn chỗ đúng ngày thực tế.

Cập nhật sửa toa24/10: các hồ sơ **TEST VOICE EDITABLE DRAFT**, **TEST VOICE COMPLETE INPUT**, **TEST VOICE EDITABLE FINAL**, **TEST VOICE COMPLETE FINAL** là fixture riêng của bác sĩ C để kiểm sửa thuốc kê bằng tên và trường hợp AI nhận đúng đầy đủ. Các ca đã hoàn tất, toa thử đã cấp phát và hàng chờ phát thuốc kết thúc theo sự kiện; chỉ dùng xem lịch sử, không dùng làm ca đang khám. Không sửa ca chính của bác sĩ An hoặc ca cũ của bác sĩ A/B để chạy kiểm thử.

Hồ sơ **TEST VOICE REVIEW VISIBLE** là fixture riêng kiểm vị trí ô xác nhận thuốc ngày24/10; ca đã hoàn tất, toa đã cấp phát và hàng chờ kết thúc. Không dùng làm ca mới.

## Dữ liệu cũ để đối chiếu

| Mục đích | Tài khoản | Vai trò/phân công |
|---|---|---|
| Bệnh nhân Nội thử cũ | `mobile_noi_c1d9dc` | Consultation NOI-010 đã CANCELLED; lịch/phiếu ngày 16/10 giữ để đối chiếu, không dùng làm ca demo đang khám |
| Các điện thoại mẫu khác | `mobile_case02`, `mobile_case03` | PATIENT; đọc trạng thái mới nhất từ API |
| Bác sĩ seed khác | `doctor` | DOCTOR, seed gán ROOM-06; không dùng thay An ở ROOM-01 |
| Kỹ thuật viên | `lab` | LAB_TECHNICIAN; chọn đúng điểm phục vụ được phân công |
| Quản trị | `admin` | ADMIN; quản lý danh mục/phân công và quyền điều phối |
| Bác sĩ bàn giao A | `handover_a_9bbb8a` | DOCTOR, Da liễu ROOM-08; tác giả gốc ca/toa/chỉ định; bác sĩ chính khi đặt lịch |
| Bác sĩ bàn giao B | `handover_b_9bbb8a` | DOCTOR, Da liễu ROOM-08; đang phụ trách ca demo 01 sau kiểm thử |
| Bác sĩ bàn giao C | `handover_c_9bbb8a` | DOCTOR, Da liễu ROOM-08; đã cấp quyền điều phối; có toa nháp riêng |
| Bệnh nhân bàn giao 01 | `handover_patient01_9bbb8a` | Ca đang IN_PROGRESS, phiếu DAL-001 |
| Bệnh nhân bàn giao 02 | `handover_patient02_9bbb8a` | Ca đã hoàn tất sau bàn giao, dùng đối chiếu lịch sử |

## Đọc đúng trạng thái trước buổi demo

1. Đăng nhập bệnh nhân, mở Lịch khám và phiếu; ghi bệnh nhân, khoa/phòng, ngày/ca, mã phiếu.
2. Đăng nhập bác sĩ đúng phòng, đối chiếu hàng đợi và ca đang phục vụ.
3. Chỉ dùng ca đủ điều kiện cho bước muốn trình diễn. Mô tả seed ban đầu “chưa đặt lịch/đã hủy” đã thay đổi sau thao tác thật; không dùng nhãn cũ thay trạng thái API.
4. Lịch demo ngày 16/10/2026 và QR cũ không dùng để chứng minh check-in thành công ngày khác. Khi cần, tạo tài khoản/hồ sơ và lịch mới đúng ngày bằng giao diện/API thật.
5. Nếu phòng có nhiều bác sĩ hoạt động, ADMIN cấu hình đúng một **bác sĩ chính khi đặt lịch**. Không đổi bác sĩ trên ca cũ bằng cách sửa phân công danh mục.

## Ca cũ được giữ lại (đọc lại trạng thái trước khi dùng)

- **DAL-001 / Bệnh nhân bàn giao 01**: Consultation `c0e4d579-a1df-4cbb-803e-ae6d5384a508`, B phụ trách, A tác giả gốc. Ca có chỉ định Lab của A, toa nháp của A và C, ghi chú kiểm thử do C lưu. Theo [luồng bàn giao](07-ban-giao-ca-kham.md).
- **NOI-010 / Demo Nội c1d9dc**: Consultation `5f0a2c7b-ec39-4e39-9083-bcf694df27cd` đã CANCELLED ngày 17/10. Đây là ca thử rỗng từ 16/10, không có chẩn đoán/toa/chỉ định; lịch và phiếu cũ giữ để đối chiếu, không phải lượt chờ trong ngày hiện tại.
- Một lịch Nội thứ hai của bệnh nhân gốc chưa có phiếu do xung đột quy tắc lượt hoạt động. Không dùng lịch đó để check-in và không gán phiếu NOI-010 vào lịch khác.

Dữ liệu thay đổi sau mỗi lần chạy. Tạo mẫu mới phải là dữ liệu có quan hệ hợp lệ giữa tài khoản, hồ sơ, lịch, phiếu và ca khám. Không reset hàng đợi hoặc sửa SQL trạng thái của ca đang dùng chỉ để qua bước.

## Mốc kiểm thử R2 — 20/10/2026

Bộ tài khoản E2E có sẵn ở trên được dùng lại để nghiệm thu R2; không đổi mật khẩu, vai trò hoặc thiết lập đăng nhập. Các hồ sơ **TEST PERSON CAREFLOW** và **TEST PERSON UI CAREFLOW** là dữ liệu tổng hợp: ca không hẹn đã hoàn tất, các lịch có hẹn dùng thử QR/chính sách tạm ngừng đã hủy, liên kết cấp thử đã thu hồi. Không dùng các ca này làm lượt chờ mới. Các hồ sơ tiếp nhận chưa liên kết còn lại từ lần thử không có quyền tài khoản và không tự gộp với hồ sơ khác.

Tạo lượt mới qua [trang19](19-tiep-nhan-lich-va-hang-doi-r2.md) khi lặp lại. Các cấu hình phòng dùng thử được khôi phục qua API quản trị; không sửa SQL trạng thái ca khám để ép kết quả đạt.

## Mốc kiểm thử R3 — 20/10/2026

Dùng bộ tài khoản E2E hiện có; không đổi mật khẩu/phương thức đăng nhập. Bệnh nhân mobile_case03 có hai hồ sơ **SYNTHETIC R3 NO CLS** và **SYNTHETIC R3 CLS**. Đây là dữ liệu tổng hợp, PDF ghi rõ không chứa giấy tờ/bệnh án người thật. Các ca không hẹn và vòng tái khám cùng ngày đã hoàn tất; toa phát hành đã cấp phát. Ca CLS được A bàn giao cho B cùng ROOM-11; tác giả chỉ định/toa nháp cũ vẫn A, toa nháp cũ không phát hành và không dùng làm toa cấp phát.

Một lịch tái khám riêng ngày21/10 tạo bằng web từ ca không CLS đã được bệnh nhân hủy qua API để dọn fixture; lịch sử nguồn giữ nguyên. Các ca này dùng xem lịch sử, không dùng làm lượt chờ mới. Muốn lặp lại thì theo [trang20](20-ho-so-va-hanh-trinh-lam-sang-r3.md) với ca/tệp test mới, không reset hàng đợi hoặc sửa SQL.

## Dữ liệu kiểm thử R4 — 20/10/2026

Dùng lại tài khoản và hồ sơ SYNTHETIC của R3. Các lịch tổng hợp tạo ngày22/10 để thử inbox/realtime đã được hủy qua API; lịch sử thông báo còn để xem trạng thái hủy. Các installation có appVersion R4_API_TEST chỉ dùng kiểm tra API khi Firebase tắt, đã disable và giữ audit; không phải thiết bị push hoạt động. Lịch thật 07:30 ngày21/10 không bị thay đổi bởi R4. Bản iPhone hiện tại1.0.0(19); cấu hình/giới hạn nghiệm thu xem [trang21](21-thong-bao-va-dong-bo-r4.md).

Cập nhật21/10/2026: bản iPhone1.0.0(20) đã cài/mở. Kiểm tra tiếp R4 chỉ đọc dữ liệu và mở/đóng phiên thử, không thêm lịch/phiếu hay thay tài khoản demo. Xem [biên bản21/10](../.planning/notification-service/r4-acceptance-2026-10-21.md).

Hồ sơ **TEST VOICE REPEAT ORDER** là fixture riêng ngày24/10 kiểm ghi/dừng nhiều lần, thêm ba nguồn vào cùng toa, chỉnh thuốc và xác nhận toàn đơn. Bác sĩ đã xóa dòng đọc lặp trong phép thử; ca hoàn tất, toa cấp phát và hàng chờ kết thúc. Chỉ dùng xem lịch sử, không dùng làm lượt mới.

Hồ sơ **TEST VOICE REQUIRED FIELDS** là fixture riêng ngày24/10 kiểm một thuốc đủ và một thuốc thiếu5trường, đánh dấu đỏ/điều hướng, lưu nháp thiếu, bổ sung rồi ký/hoàn tất. Ca đã hoàn tất, toa đã cấp phát và hàng chờ kết thúc; chỉ dùng xem lịch sử. Không sửa thông tin ca của người dùng để kiểm chức năng.

Hồ sơ **TEST VOICE INLINE DURATION** là fixture riêng ngày24/10 kiểm ghi62giây, thời lượng/nghe/tua, micro im lặng/quyền bị từ chối và hai đoạn qua ASR thật vào cùng toa. Toa nháp đã hủy, ca và hàng chờ đã hoàn tất; chỉ dùng xem lịch sử. Không dùng làm lượt đang khám.

Hồ sơ **TEST VOICE FOUR DRUGS** là fixture riêng ngày24/10 kiểm transcript bốn thuốc, hai ô ASR mơ hồ, giữ sửa/xóa khi thêm đoạn và xóa hết rồi ghi âm thay toa. Toa nháp đã hủy, ca/hàng chờ hoàn tất; chỉ dùng xem lịch sử. Không sửa toa người dùng để tạo kết quả kiểm thử.

Hồ sơ **TEST VOICE PREFIX** là fixture riêng ngày24/10 kiểm từ dẫn bị nghe sai và đọc tên/hàm lượng/tần suất không đánh số. Các phép thử giữ đủ4thuốc và tách đúng2thuốc ngoài catalog cùng câu; toa nháp đã hủy, ca/hàng chờ hoàn tất. Chỉ dùng xem lịch sử.
