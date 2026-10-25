# Lịch sử hướng dẫn sử dụng

## 24/10/2026 — Bàn giao và xuất bản source lên develop

- Tổng hợp các thay đổi R6/R7, voice local, mobile/runtime và bằng chứng đã kiểm; xuất bản theo yêu cầu chủ dự án lên develop, giữ main nguyên trạng.
- Thêm [file handover](../.planning/deployment/handover-2026-10-24.md) với phạm vi source/runtime, migration, nguồn hướng dẫn, kiểm thử và phần chưa nghiệm thu. Bằng chứng21/10 giữ nguyên candidate lịch sử, không đổi gate OPEN thành PASS do push Git.
- Khóa/config riêng/model/dataset/audio không thuộc bản Git; tài khoản demo vẫn chỉ quản lý tại trang01. Biên bản kiểm tra xuất bản được ghi riêng theo source hiện hành.

## 24/10/2026 — Nhận thuốc không phụ thuộc từ dẫn hoặc số thứ tự

- Nhận tên/hàm lượng/tần suất khi đọc thẳng, cả tên ngoài catalog, và tách nhiều thuốc trong cùng câu. Không yêu cầu từ “thuốc/kê/thêm” hoặc “thứ nhất/hai”; cụm “thứ tư là” vẫn nhận khi từ trước bị ASR nghe sai thành hốt/hột.
- Giữ tên và trường rõ, không tự chữa “mỗi lần cố một viên” thành liều chắc chắn; duy trì phủ định/tiền sử/giả định, không nhận số liều đơn lẻ thành thuốc. Hướng dẫn23/contract cập nhật. Worker59/59 đạt; browser/API thật kiểm transcript có hốt, bản bỏ mọi thứ tự, reload cùng toa và hai tên ngoài catalog trong một câu có tần suất riêng. Fixture đã hủy/hoàn tất, không sửa ca người dùng. Phạm vi runtime ghi ở [biên bản](../.planning/ai-service/voice-name-cues-2026-10-24.json), chưa phải nghiệm thu ASR/y khoa hoặc full R7; không đổi Web/API/DB/model/keys, chưa commit/push.

## 24/10/2026 — Giữ đủ thuốc trong cách đọc đánh số và cho ghi lại toa đã xóa hết

- Tái hiện đúng transcript có4tên nhưng chỉ ra2dòng. Sửa tách “thuốc thứ…”, giữ tên ngoài catalog, không cắt mất trường ở câu nối, chuẩn hóa số ngày bằng chữ và giữ lịch sáng/tối. Giữ trống liều/tần suất ASR chưa rõ, không tự chữa thành thông tin kê đơn.
- Xóa hết dòng trong DRAFT rồi đọc mới dùng chế độ thay thuốc nguyên tử trên cùng toa; không còn yêu cầu phải giữ thuốc cũ và không làm sống lại dòng đã xóa. Mặc định vẫn thêm nếu còn dòng. Kiểm phiên/phân công/trạng thái/nguồn và chống thêm trùng giữ nguyên, không đổi schema.
- Hướng dẫn23 và contract đồng bộ. Worker40/40 và Common53 + Prescription131 kiểm thử đạt, không skip; TypeScript/lint/build đạt. Browser/API thật giữ đủ4tên, lượng5–3–6–3 và ngày5–3–3–3, chỉ2ô mơ hồ đỏ; lưu/reload không chạy lại AI, append giữ chỉnh sửa và ghi âm sau xóa hết thay đúng cùng toa. Fixture đã hủy/hoàn tất. Kết quả runtime ghi trong [biên bản](../.planning/ai-service/voice-four-drugs-2026-10-24.json); chưa coi là nghiệm thu chuyên môn/ASR tổng quát hoặc full R7, chưa commit/push.

## 24/10/2026 — Bỏ cửa sổ voice, sửa thời lượng0:00

- Ghi âm/nghe lại/transcript/sửa toa trực tiếp trên trang khám, bỏ nút và dialog mở rộng; giữ một danh sách và một xác nhận toàn đơn.
- Chính sách trang cho phép `media-src 'self' blob:` để audio tạm phát được; giữ giới hạn script/connect/object hiện có. Bộ đếm dựa thời gian thực, sửa duration metadata WebM bằng thư viện MIT chạy local trong browser, giữ MP4. Hiện thời lượng khi dừng và sau xử lý, tách thời gian audio với thời gian nhận dạng; có chỉ báo mức âm thanh và báo chưa phát hiện tiếng rõ. Trạng thái mới phân biệt ghi tạm với thêm thuốc vào DRAFT, bỏ hai đoạn hướng dẫn lặp.
- Metadata tab thêm số giây của đoạn đã xử lý gần nhất; không lưu audio/transcript vào storage hoặc gửi dịch vụ ngoài. Chưa phải lịch sử/đồng bộ thời lượng nhiều thiết bị. TypeScript/lint/build đạt. Browser thực kiểm bản ghi62.35giây có tiếng, native player nghe/tua/thời lượng; FFmpeg xác minh âm thanh giải mã không đổi sau sửa metadata; ASR thật lưu hai đoạn vào cùng DRAFT, reload giữ số giây, im lặng/quyền từ chối/logout đúng trạng thái. Fixture đã hủy toa/hoàn tất hàng chờ, ca người dùng chỉ đối chiếu đọc. Phạm vi nghiệm thu runtime ghi trong [biên bản](../.planning/ai-service/voice-inline-duration-2026-10-24.json); micro vật lý/chuyên môn/full R7 vẫn mở, chưa commit/push.

## 24/10/2026 — Đánh dấu trực tiếp ô thuốc còn thiếu

- Thêm dấu bắt buộc, viền/nền đỏ và hướng dẫn dưới ô tên/đơn vị/liều/tần suất/thời điểm/số ngày/số lượng còn thiếu hoặc số không hợp lệ; tự hết lỗi khi sửa đúng, không đánh đỏ các ô tùy chọn.
- Ký khi chưa đủ mở phần chỉnh sửa và tập trung ô đầu tiên cần bổ sung, không gửi yêu cầu ký; lưu nháp vẫn được. Dùng cùng trạng thái trong danh sách/cửa sổ mở rộng, giữ một xác nhận toàn đơn. Không đổi backend/schema/model.
- TypeScript/lint/build và5 kiểm tra browser qua API thật đạt: đúng ô đỏ ở hai layout, focus phần đang gập, lưu/reload toa thiếu, số/chuỗi không hợp lệ, sửa/lưu/ký/hoàn tất. Fixture đã cấp phát/rời hàng chờ, đối chiếu ca người dùng chỉ đọc. Phạm vi và kết quả runtime được ghi trong [biên bản](../.planning/ai-service/voice-required-fields-2026-10-24.json); không coi là nghiệm thu chuyên môn hoặc full R7. Hướng dẫn23 cập nhật cùng bản web, chưa commit/push.

## 24/10/2026 — Ghi âm nhiều lần, gộp đơn và xác nhận toàn đơn

- Gộp Đơn thuốc/Danh sách Đơn thuốc thành một editor, dùng cùng khu vực khi mở rộng. Chỉ một khung xác nhận cuối danh sách, đại diện toàn đơn; thêm/xóa/sửa thuốc hoặc ghi thêm yêu cầu xác nhận lại.
- Cho ghi/dừng/ghi lại và thêm nhiều audio/transcript vào cùng DRAFT, giữ các chỉnh sửa; bỏ đoạn mới không xóa toa. Chặn ký khi còn bản ghi chưa lưu và khóa input sau ký. Latch chống nhấp mic/lưu liên tiếp; đóng khi xin quyền không để micro tự bật muộn.
- Prescription hỗ trợ target DRAFT tùy chọn, khóa append và liên kết source chống trùng; V4/DBML cho nhiều nguồn trên cùng toa, giữ PK/FK. Sao lưu DB trước cập nhật. Common53 + Prescription129 kiểm thử đạt, không skip; TypeScript/lint đạt. Production build và12 kiểm tra browser/API đạt: ghi nhiều lần bằng MediaRecorder/MediaStream tổng hợp, ASR thật, một editor/checkbox, lưu/reload/giữ chỉnh sửa, master-data drawer, ký/khóa/hoàn tất; retry ba nguồn không thêm trùng và STAFF403. Ca thử đã cấp phát/rời hàng chờ, ca người dùng được đối chiếu chỉ đọc. Kết quả runtime ghi ở [biên bản](../.planning/ai-service/voice-repeat-2026-10-24.json); micro vật lý/chuyên môn/full R7 vẫn mở, chưa commit/push.

## 24/10/2026 — Hiển thị rõ xác nhận thuốc kê bằng tên

- Đưa khung Xác nhận trước khi ký ngay dưới từng thuốc ở danh sách toa chính, có ô tên/đơn vị và checkbox; bỏ giới hạn chiều cao cuộn riêng làm khuất phần này. Trong cửa sổ voice, xác nhận nằm ngoài phần chỉnh sửa có thể gập.
- Ký khi thiếu xác nhận/đơn vị đưa con trỏ tới đúng ô còn thiếu trước khi mở hộp ký. Các vị trí dùng chung trạng thái và lưu vào cùng toa; không đổi API, schema hoặc điều kiện kiểm tra phía server.
- Hướng dẫn23 cập nhật vị trí mới. TypeScript/lint/production build và7 bước browser/API thật đạt: thấy ô trên danh sách chính, đưa focus đúng chỗ, đồng bộ hai vị trí, giữ sau reload, ký/hoàn tất. Ca mẫu đã cấp phát và kết thúc hàng chờ; đối chiếu chỉ đọc trên ca người dùng, không xác nhận/ký thay.

## 24/10/2026 — Toa giọng nói sửa trực tiếp và hoàn tất ca

- Bỏ khối “Kiểm tra thuốc nhận được” chặn lưu theo catalog; endpoint editable lưu mọi dòng có tên vào DRAFT, giữ nguồn/idempotency, không tự tạo mã hoặc master data. Số lượng JSON nguyên dạng3.0 được nhận chính xác, không làm tròn phân số.
- Thêm ô sửa tên/hàm lượng/số lượng/đơn vị/liều/tần suất/thời điểm/số ngày/chi tiết ngay trên đơn giấy. Lưu lại cùng toa, reload giữ chỉnh sửa; sửa tên bỏ mã cũ. Thuốc kê bằng tên cần bác sĩ xác nhận đã kiểm tra và đủ cách dùng trước khi ký; server kiểm lại. Thao tác sửa/xóa phân biệt đúng dòng kể cả nhiều dòng không có mã.
- Chưa lưu bản ghi thì Hoàn tất mở ô nhập toa; ký bị từ chối mở ngay ô sửa và thông báo server. Đã kiểm hai luồng trên ca tổng hợp qua AI/API/web thật: thuốc chưa có mã và thông tin đúng đủ → ký → hoàn tất; ca thử đã phát thuốc và rời hàng chờ qua sự kiện đúng quy trình. Không sửa các ca người dùng đang dùng.
- Common53 và Prescription123 kiểm thử đạt, không skip; TypeScript/lint/production build đạt,12 bước browser qua API lâm sàng thật (có giả lập riêng lỗi capability để kiểm sửa toa khi AI không sẵn sàng) và kiểm tra quyền mới. Cập nhật trang23/24, contract và mô tả JSONB; không đổi schema/khóa/model, không commit/push. Chuyên môn/micro vật lý/full R7 vẫn mở.
## 24/10/2026 — Sửa gói CA khiến iPhone không đăng nhập được

- Xác định bản22 thiếu asset CA dù cấu hình yêu cầu nạp CA; kiểm đăng nhập từ Mac trước đó không phát hiện lỗi bundle này. Bổ sung khai báo file CA trong pubspec của staging riêng, giữ CA và cấu hình ký ngoài Git, không bỏ xác minh HTTPS.
- Chạy bản chẩn đoán24 trực tiếp trên iPhone: HTTPS200, Secure Enclave/ký yêu cầu, API đăng nhập200, AuthService đăng nhập/đọc danh tính đều đạt. Cài đè và mở bản thường1.0.0(25), kiểm CA trùng file nguồn/chữ ký và loại entrypoint/tham số tài khoản chẩn đoán khỏi bản bàn giao. Không đổi mật khẩu, không gỡ app hoặc sửa backend.
- Bổ sung bước kiểm asset/hash/chữ ký trước cài tại trang11 để tránh lặp lại; Face ID/camera/GPS/push và nghiệm thu R7 đầy đủ vẫn mở.

## 24/10/2026 — Cài lại mobile R7 trên iPhone

- Đối chiếu nguồn mobile R7 với staging, build Release1.0.0(22), kiểm chữ ký, cài đè cùng bundle ID và mở thành công trên iPhone 11 Pro Max; không gỡ app hay sửa logic mobile/backend.
- Kiểm backend đang hoạt động và cầu nối HTTPS bằng CA của app từ Mac. Chưa nghiệm thu lại Face ID/camera/GPS/đăng nhập bằng thao tác trên điện thoại; provisioning bản22 vẫn hết hạn25/10 lúc15:04 giờ Việt Nam. Cập nhật trang11 với phiên bản và lệnh cài hiện tại.

## 24/10/2026 — Không gian nhập toa và Docker AI API

- Xác nhận adapter trùng checksum prototype; bật context catalog bệnh viện, đồng bộ cấu hình decode, thêm timing thực đo. Giữ15 thuốc active, không nhập catalog tham khảo thành thuốc được phép kê.
- Thêm cửa sổ nhập/audio/transcript và đơn giấy; tải/nghe lại, xem bằng chứng, mở editor thuốc hiện có. Sửa/reload giữ cùng toa; lấy lại transcript còn hạn qua API bằng metadata mã job/toa theo bác sĩ/ca, không lưu raw transcript/audio/token hoặc áp lại AI lên thay đổi đã sửa.
- Docker runtime image giới hạn context, non-root/read-only/cap-drop/health mTLS/tài nguyên. API trên18093, GPU worker giữ Mac18370; launcher kiểm owner/image/cổng, snapshot thêm descriptor. Không đổi schema/secret.
- Worker32/32,4 kiểm tra config/ownership Docker, TypeScript/lint/build đạt; audio thật qua container→DRAFT→sửa/reload, mic lifetime bằng MediaStream tổng hợp. Phép đo nhỏ chưa chứng minh chất lượng lâm sàng/tải production. Cập nhật trang01/23/25/26 và biên bản; chưa commit/push, full R7 OPEN.

## 23/10/2026 — Sửa lỗi đối chiếu toa giọng nói

- Sửa nhận nhầm mâu thuẫn PRN khi “khi cần” được nhắc lại bằng cùng điều kiện có triệu chứng; giữ nguyên điều kiện khác nhau là xung đột. Giữ câu sửa số lượng trên đúng thuốc đang đọc và nhận giới hạn liều tối đa ở câu tiếp theo.
- Giữ dòng thuốc được kê rõ nhưng chưa có trong catalog để báo cần đối chiếu, tránh bỏ sót khi còn thuốc khác đã khớp. Web hiển thị thuốc/hàm lượng gợi ý từ catalog và trường mâu thuẫn; sửa transcript xong xử lý lại, chưa tự ký hoặc tự chọn thuốc.
- Worker29/29 đạt; TypeScript/lint/build web đạt. Browser/API thật kiểm thiếu hàm lượng, thuốc ngoài catalog, xung đột thật, sửa revision→DRAFT→reload và server422 khi bỏ qua kiểm tra giao diện. Ca xác minh riêng đã hoàn tất; ca bác sĩ An vẫn đang khám, chưa có toa.
- Cập nhật trang01/23/25 và mốc nguồn. Runtime web/worker đã cập nhật, giữ khóa/model/DB schema; chưa commit/push. R7 đầy đủ vẫn OPEN; không chuyển chứng cứ candidate21/10 sang source mới.

## 23/10/2026 — Chuẩn bị dữ liệu voice và khung khám tối

- Qua API thật, thêm ngoại lệ22:00–23:00 ngày23/10 cho ROOM-01, hai slot30 phút/mỗi slot5 chỗ; giữ nguyên cửa sổ sáng/chiều và các ngày khác. Availability xác nhận cả hai slot còn chỗ lúc chuẩn bị.
- Tạo hai hồ sơ tổng hợp thuộc bệnh nhân demo; một lượt không hẹn đang khám bởi bác sĩ An, chưa có toa; hồ sơ còn lại để đặt lịch dự phòng. Hai lượt tiếp nhận sơ bộ ROOM-11 giữ chờ, không sửa ca cũ của bác sĩ A/B. Tài khoản và mã ca chỉ quản lý tại trang01.
- Cập nhật trang23 với cách mở ca, cho phép micro và đoạn đọc tổng hợp. Kiểm phiên web thật/nút ghi âm, worker readiness và job transcript nhận thuốc trong catalog; chưa nghiệm thu micro vật lý hoặc độ chính xác giọng nói. Không sửa code/schema, không lưu sẵn toa và không thay secret.

## 21/10/2026 — R7: bàn giao kỹ thuật và cổng nghiệm thu

- Thêm trang25, ma trận E01–E18 và bằng chứng gắn candidate; giữ OPEN với phần cứng/push/chuyên môn hoặc nhánh chưa kiểm đủ. Workflow deploy dừng khi thiếu nghiệm thu.
- Cập nhật runtime R7, iOS1.0.0(21), iOS15/Pods, Android release ký riêng; launcher không bắt buộc build giả lập để dùng iPhone.
- Bổ sung hướng dẫn503 upstream, kiểm backend/restore scratch, snapshot sau rollout và phục hồi sau reboot; không restore đè DB hoặc đổi key hiện có.
- Backend cuối đạt1.000/1.000 không skip; Flutter có15 skip ghi riêng; chưa commit/push R7.
- Kiểm chứng bổ sung E08/E09/E10/E15 bằng service thật và DB scratch: tiếp nhận/hoàn tất, bốn nhánh requeue cạnh tranh, lịch/slot/ngày nghỉ, toàn bộ KPI Analytics và broker replay/rebuild/JVM restart. Xung đột optimistic locking trả409 an toàn thay vì500; cập nhật trang09/25.
- Sửa sự kiện hủy đến trước xác nhận làm xuất hiện lại phiếu chờ: Queue V14 lưu dấu hủy bền vững, khóa xử lý theo mã lịch; DBML đồng bộ. Kiểm nâng V13→V14 với22 lịch hủy cũ, restart/concurrency và phát lại sự kiện thật. Kiểm E13/E14 gồm mất phản hồi, bàn giao/thu hồi điều phối, PDF, liên kết có consent và thu hồi quyền trên lịch đã có. Cập nhật trang03/09/25 và bằng chứng R7.
- Chuẩn hóa extension chứng chỉ leaf nội bộ AI/Analytics, giữ CA và khóa; kiểm Python3.14 strict đạt, lưu lại runtime/backup. Ghi đúng hạn provisioning bản21 tại trang11; không thay key Resend.

## 21/10/2026 — R6: trợ lý có nguồn và toa giọng nói local

- Thêm kho nguồn theo phiên bản, ADMIN nhập/thu hồi, DOCTOR rà soát; chatbot chỉ trích nguồn đã duyệt thuộc khoa, không còn fallback phác đồ mẫu.
- Server kiểm ngữ cảnh/phân công và lịch sử theo bác sĩ/ca; nội dung mã hóa và hết hạn.
- Web ghi âm → Lưu toa nháp → chỉnh cùng toa → ký riêng; Prescription chống trùng job/revision, giữ trường chi tiết và chặn ký khi còn thiếu.
- Cập nhật trang23/24, DBML và biên bản kỹ thuật. Chưa có tài liệu/giọng bác sĩ được chuyên gia nghiệm thu; chưa đóng toàn bộ R6. Chưa commit/push các thay đổi R6.

## 21/10/2026 — Gộp source để phát hành lên develop

- Hợp nhất backend/web R0–R5 và source app native từ các worktree; giữ nguyên các giới hạn nghiệm thu đã ghi.
- Giữ phần backend/worker toa giọng nói đã có ở checkout gốc, mặc định tắt; hướng dẫn chuyển sang trang23 để không trùng trang17 quản trị R1. Chưa triển khai luồng web ghi âm/lưu toa nháp hoặc đóng R6.
- Tài liệu và code được xuất bản cùng nhau; cấu hình private, khóa ký, model/dataset riêng và runtime vẫn ở ngoài Git.

## 21/10/2026 — R5: Analytics Service và dashboard quản trị

- Thêm trang22: KPI theo ngày/khoa/phòng, phân biệt phiếu tương lai với lượt đến, missed/requeue/review và chất lượng thời gian.
- Dashboard lấy dữ liệu vận hành qua Analytics, có bộ lọc Directory thật, biểu đồ, rỗng/lỗi/403; ADMIN đối soát và dựng lại có audit.
- Consumer chống trùng, đọc snapshot nguồn tối thiểu qua mTLS, backfill có cursor; giữ dữ liệu khi lỗi và hiển thị độ mới/coverage. Seed riêng có nhãn tổng hợp, không trộn REAL.
- Đồng bộ mục lục/chuẩn bị và launcher Mac có Analytics,15 đầu kiểm tra; không thay key Resend hay app iPhone. R0–R4 giữ chưa commit, R4 push Firebase/APNs còn chờ nghiệm thu.
- PostgreSQL/API/RabbitMQ/browser và production build web đã kiểm tra; tải lớn, triển khai production và reboot/restore toàn máy chưa nghiệm thu.

## 20/10/2026 — R3: hồ sơ và hành trình lâm sàng

- Thêm trang20, đồng bộ trang04–08/14: bác sĩ tải hồ sơ có quyền, native upload ngày chuẩn/tiến trình/hủy và lịch sử khám không hẹn.
- Đọc kết quả là thao tác xác nhận chủ động; không tự duyệt khi hoàn tất. Lượt đọc gắn phiếu gốc, hỗ trợ bàn giao ca không hẹn.
- Chọn tái khám bằng khoa/phân công và slot thật; báo riêng toa đã ký với lịch chưa tạo, retry không nhân đôi lịch/sự kiện.
- Hoàn tất giữ trạng thái lâm sàng đã lưu, đối soát Queue/Appointment khi lỗi thay vì báo thành công toàn bộ.
- iOS1.0.0(18) build/ký đạt; chưa xác nhận cài do iPhone chưa sẵn sàng. Kiểm thử picker/xem/chia sẻ vật lý, Android và push R4 còn ghi rõ chưa nghiệm thu.

## 20/10/2026 — R2: lịch phòng, tiếp nhận và hàng đợi

- Đối chiếu tên giữ dấu, chuẩn hóa chữ hoa/thường và khoảng trắng sau xác minh; số giấy tờ/ngày sinh và chấp thuận chính chủ vẫn bắt buộc.
- Thêm trang19: lịch phục vụ có phiên bản/ngày hiệu lực/ngoại lệ, capacity nhiều phòng và chính sách hàng đợi áp dụng thật.
- STAFF tìm/tạo hồ sơ tối thiểu và cấp lượt không hẹn; bác sĩ bắt đầu/hoàn tất theo phiếu, không cần lịch hẹn giả.
- Bổ sung luồng liên kết sau đối chiếu giấy tờ và chấp thuận của chính tài khoản bệnh nhân, xử lý trùng bằng trạng thái kiểm tra và thu hồi quyền.
- Thêm danh sách lỡ lượt, FRONT/BACK theo policy; tạm ngừng tiếp nhận vẫn cho xem/hoàn tất ca đang phục vụ.
- Mobile 1.0.0 (17): mục liên kết hồ sơ, hàng đợi theo đúng lịch đang mở, tải lại khi quay từ nền và nhãn hủy/chưa check-in. Đã cài/mở trên iPhone 11 Pro Max; không coi API/unit là nghiệm thu camera/GPS hoặc thao tác người dùng.
- R1 được người dùng chấp nhận chuyển đợt sau bằng chứng passkey ADMIN và phiên sinh trắc học thật; các kiểm tra chưa xác nhận riêng giữ ở trang18/R7.

## 20/10/2026 — Kết nối iPhone để nghiệm thu R1

- Nhận thiết bị vật lý và xác nhận app1.0.0(16); bridge HTTPS và danh mục khoa/phòng truy cập được.
- Thêm trang18 và mẫu chữ OCR tổng hợp để thử camera, sửa tay và consent mà không cần dùng giấy tờ thật.
- Ghi rõ runtime chưa có passkey ADMIN hoạt động: bước bootstrap/cấp khóa thật còn cần chủ thiết bị xác nhận; không thay bằng credential ảo hoặc session SQL.
- Operator cấu hình contact ADMIN demo theo hộp thư đã cung cấp, giữ backup private và audit; chuẩn bị grant bootstrap và một email được Resend chấp nhận. Cửa sổ enrollment thật chờ người nhận nhập mã và xác nhận hệ điều hành, không gắn passkey hoặc session giả.
- Các kết quả Face ID/camera/UI email/passkey thật vẫn chờ người thao tác xác nhận, chưa chuyển trạng thái R1 thành hoàn tất.

## 20/10/2026 — R1 tài khoản và quản trị

- Thêm hướng dẫn17: Identity quản lý danh sách/tạo/khóa/mở tài khoản nội bộ; phân công thực từ Directory, tiếp tục phần thiết lập còn dở, cấp passkey theo hai kênh.
- ADMIN cần passkey xác thực gần đây trước tạo/khóa/đổi quyền; không tự khóa/đổi quyền mình. Tài khoản mới không đăng nhập bằng mật khẩu demo.
- Sửa lỗi lưu mới hồ sơ bác sĩ; phân biệt Identity user ID và profile ID.
- Bệnh sử/hồ sơ lỗi có cảnh báo và thử lại; bỏ tuổi/giờ/mã EMR giả, chỉ báo DB luôn xanh. Cập nhật trang04 và16.
- Backend/web được kiểm thử với DB riêng; native giữ bản16. Cảm biến Face ID, camera/giấy tờ thật và passkey vật lý còn chờ nghiệm thu, chi tiết ở biên bản R1.

## 20/10/2026 — Hoàn tất chuẩn hóa R0, giữ app (16)

- Thống nhất nguồn yêu cầu/use case/kiến trúc và CURRENT/TARGET tại `.planning/architecture/`; cập nhật tham chiếu ở báo cáo/contract cũ.
- Chụp baseline backend/web/native và artifact private, ghi hash nguồn/migration/toolchain; build lại ở thư mục riêng, không đổi runtime hoặc cài app mới.
- Hướng dẫn13 bổ sung cách đối chiếu/khôi phục baseline và giới hạn chưa diễn tập DB restore; ghi điều kiện APNs/Firebase còn thiếu, không báo push đã nghiệm thu.
- R1–R7 còn theo kế hoạch; AI giữ đợt sau, định danh local + đối chiếu trực tiếp, không dùng lại VNPT.

## 19/10/2026 — Sửa Face ID/Keychain cũ và cải thiện đọc giấy tờ, bản (16)

- Sửa thao tác xóa từng khóa trên iOS không bị giới hạn bởi accessibility của bản mới; tái hiện lỗi cũ và kiểm tra bản sửa trực tiếp trên iPhone bằng dữ liệu thử riêng.
- Màn thiết lập Face ID đồng bộ danh sách thiết bị còn hiệu lực, bỏ metadata đã thu hồi nhưng giữ phiên mật khẩu; đang hoạt động hoặc lỗi mạng không bị xóa. Đăng ký đã thu hồi phải bật lại qua xác thực, không tự phục hồi.
- Đọc giấy tờ hỗ trợ nhãn thẻ mới/Fullname, số có khoảng trắng, ngày sinh dấu chấm/gạch ngang; thông báo rõ đã đọc trên máy và các trường cần bổ sung. Cập nhật mô tả quyền camera phù hợp luồng không selfie.
- Bản16 đã build/ký/cài/mở; Face ID khả dụng, khóa thử Secure Enclave ổn định qua hai lần mở và native OCR điền đủ ba trường từ ảnh tổng hợp. Chưa thay cho nghiệm thu quét mặt/camera/CCCD thật của người dùng.
- 233 Flutter full-suite và14 HTTP fixture chạy riêng đạt (247 lượt thực thi đạt); Android debug build đạt. Không gửi ảnh thật, không gọi VNPT và không đổi backend/DB/Resend trong lượt này.

## 19/10/2026 — Ngừng VNPT, đọc giấy tờ trên máy và đối chiếu trực tiếp, bản (15)

- Bỏ adapter outbound và thông tin kết nối VNPT khỏi runtime; API cũ trả410, bộ đếm VNPT không tăng trong kiểm thử mới.
- Đọc ảnh trên iPhone bằng Vision và Android bằng model OCR đóng gói; chỉ gửi số căn cước/họ tên/ngày sinh sau người dùng kiểm tra, không ảnh/selfie/địa chỉ/QR gốc. Có nhập/sửa tay.
- V17/DBML lưu thông tin chờ bằng mã hóa khóa riêng; STAFF/ADMIN đối chiếu ba điều kiện tại quầy, ghi nhật ký và xóa payload sau quyết định. Bệnh nhân hủy được yêu cầu; hết hạn7 ngày; không tự gộp hồ sơ.
- Thêm trang web staff/admin identity và menu. API/web thật với dữ liệu kiểm thử đạt; bản iPhone15 đã ký/cài/mở. Camera/OCR và giấy tờ gốc trên iPhone vẫn chờ người dùng xác nhận; không coi bài kiểm tra giả là xác minh danh tính thật.
- Hướng dẫn15 chuyển sang `15-doc-giay-to-va-doi-chieu.md`; đường dẫn VNPT cũ chỉ trỏ về trang hiện hành. Lượt gửi VNPT trước đây không tự bị xóa khi ngừng kết nối.

## 19/10/2026 — Kết nối VNPT thật, iPhone bản (14)

- Xác nhận token khớp dự án Free đang hoạt động, có đủ quyền kiểm tra giấy tờ/OCR/liveness/so khớp; bật hai Identity. Không mua/nâng gói hoặc thay key Resend.
- Chuẩn hóa tiền tố Bearer và kiểm tra cấu hình; sửa gửi lại multipart sau refresh, giữ khóa chống trùng; hiển thị lý do từ chối rõ hơn.
- Kiểm tra âm tính qua VNPT thật dùng hai request; ảnh không phải căn cước bị từ chối, gửi lại không tốn thêm. Chưa nghiệm thu ảnh căn cước/selfie thật.
- 247 test backend và 240 Flutter đạt; Android debug build đạt; iOS (14) build/ký/cài/mở trên iPhone thật. Một test demo cũ bỏ qua; các fixture HTTP có điều kiện đã chạy riêng.
- Ba email thật đăng ký/reset/thông báo: Resend báo Delivered; không ảnh/tệp đính kèm, OTP và grant một lần, thu hồi phiên cũ. Dùng DB/tài khoản thử tách riêng; hiển thị Gmail và UI iPhone vẫn chờ xác nhận.

## 19/10/2026 — Đợt 2, bản iPhone (13)

- Đăng ký hai bước bằng email; chỉ tạo tài khoản sau xác minh. V16 tách mã đăng ký/reset/cấp passkey; cập nhật DBML.
- Trang cấp và khôi phục passkey nhân viên qua ADMIN vừa xác thực bằng passkey + kiểm tra danh tính + hai mã riêng; có bootstrap operator, khóa dự phòng và chặn gỡ khóa cuối.
- Secure Enclave iOS/Android Keystore, chuyển liên kết khóa cũ giữ đăng ký Face ID và xử lý mất phản hồi; xác minh chữ ký ES256 trên iPhone thật bằng namespace test riêng.
- Khóa app sau 60 giây nền, idle bệnh nhân 30 phút/nhân viên 15 phút; thao tác người dùng mới gia hạn, refresh giữ tuổi xác thực gốc. Reset/thay quyền hủy cả quyền cấp passkey đang chờ.
- Theo dõi provider ID, webhook ký và trạng thái giao mail; không dùng logo ảnh đính kèm. Webhook công khai và inbox thật vẫn chờ cấu hình/nghiệm thu.
- Đã build/ký/cài/mở bản (13); kiểm thử WebAuthn dùng thiết bị ảo Chrome với Identity/DB thật tách riêng. Face ID và luồng reset hoàn chỉnh trên UI iPhone chưa được người dùng xác nhận; xem trang 16 và báo cáo kỹ thuật.

## 19/10/2026 — Tích hợp VNPT eKYC, iPhone bản (12), chờ tài khoản VNPT

- Thêm trang 15: đăng ký web VNPT, gói Free, ba thông tin kết nối giữ ngoài Git, cách xác minh trên mobile và xử lý hạn mức.
- Phân biệt 100 requests với 100 lượt xác minh; trần cục bộ không đại diện số dư VNPT. Một luồng đầy đủ dùng tối đa tám request outbound.
- Source có giao diện ba bước, đồng ý chuyển ảnh, backend kiểm tra thật theo contract VNPT, quota bền vững và chống gửi trùng. Endpoint một ảnh cũ ngừng trả mock (410).
- Release iOS (12) đã build/ký/cài/mở trên iPhone; Identity đã triển khai V15 trên DB loopback. 185 test backend, 211 Flutter đạt (13 bỏ qua), 4 nhóm API thật đạt; chưa phát sinh request VNPT thật. Chưa nghiệm thu tenant VNPT thật, SDK native/NFC và camera eKYC trên iPhone.

## 19/10/2026 — Đợt 1, bản iPhone (11)

- Tải/mở/chia sẻ tệp hồ sơ bằng phiên có quyền, tiến trình/hủy và dọn tệp tạm khi đổi phiên/khởi động mới.
- Thêm Thiết bị sinh trắc học và thu hồi đăng ký; giữ đúng phạm vi khác với danh sách mọi phiên.
- Bảng giá từ Directory API; ADMIN quản trị tại `/admin/services`; Appointment lưu báo giá/phiên bản khi đặt, từ chối giá cũ/dịch vụ ngừng hoạt động.
- Ngừng danh bạ bác sĩ hardcode cũ; chặn cờ mock/demo trong Release. Giữ dữ liệu test trong môi trường riêng.
- Đã cài/mở 1.0.0 (11), kiểm tra API thật và web; thao tác trình xem/chia sẻ iOS và UI native mới còn chờ người dùng xác nhận. Xem hướng dẫn 14. AI để sau.

## 19/10/2026 — Tinh gọn màn đăng nhập iPhone, bản (10)

- Bỏ nút “Đăng nhập bằng cách khác”; dùng biểu tượng Face ID/vân tay cạnh ô mật khẩu. Giữ đăng nhập bằng mật khẩu, quên mật khẩu và đổi tài khoản.
- Đã build/cài đè/mở CareFlow 1.0.0 (10) trên iPhone 11 Pro Max; 9 kiểm tra giao diện đăng nhập hiện có đạt. Thao tác Face ID trên cảm biến thật vẫn cần người dùng xác nhận.

## 19/10/2026 — Cài bản bảo mật (9) và cập nhật backend test

- Đã cài đè/mở CareFlow 1.0.0 (9) trên iPhone 11 Pro Max; giữ bundle và dữ liệu app, không gỡ ứng dụng.
- Cập nhật 12 tiến trình backend test trên Mac; V13/V14 áp dụng trên DB loopback đã sao lưu, không truy cập DB remote. Outbox dùng khóa private riêng; giữ nguyên Resend key theo yêu cầu.
- Sửa namespace Gateway Spring Cloud `server.webflux`, xác minh lại TLS/mTLS và endpoint iPhone; 14 nhóm API bảo mật thật đạt.
- Chưa nghiệm thu Face ID trên cảm biến/nhận OTP email/reset hoàn chỉnh/backup-restore; web và hạ tầng production chưa được phát hành đồng bộ. AI để sau.

[Quay về mục lục](README.md)

## 19/10/2026 — Bản vá bảo mật sau đánh giá sản phẩm

- Ngừng câu hỏi bí mật; reset email thu hồi cả passkey/thiết bị/phiên. Chặn STAFF/ADMIN dùng API hồ sơ đầy đủ ngoài quyền và giảm quyền quản trị trên toa.
- Web chuyển refresh token sang cookie HttpOnly và access token trong memory, thêm CSP nonce và CSRF; cập nhật dependency và workflow secure.
- Thêm outbox email mã hóa, quota/rate limit, thời hạn phiên tuyệt đối, key rotation, kiểm tra/scanner/quota file và chặn AI/eKYC giả lập/đăng ký chưa xác minh trên production.
- Bổ sung hướng dẫn 13 về điều kiện chuyển đổi; source chưa commit/push/deploy và chưa cài lại iPhone. Phần cứng, key Resend mới, onboarding nhân viên và vận hành hạ tầng thật còn cần nghiệm thu; không coi là toàn bộ bảo mật production đã hoàn tất.


## 18/10/2026 — Thu gọn đăng nhập Face ID trên điện thoại

- Release 1.0.0 (8) tự điền tài khoản đã nhớ, thu hẹp ô mật khẩu và đặt icon khuôn mặt/vân tay bên cạnh; vẫn nhập mật khẩu được.
- Thêm **Dùng tài khoản khác**; sửa tài khoản sẽ xóa mật khẩu đang nhập, mở rộng ô mật khẩu và ẩn icon của tài khoản cũ. Tải chậm/hủy Face ID không ghi đè tên vừa nhập.
- 52 kiểm thử hồi quy mobile đạt, gồm 9 trường hợp màn đăng nhập; chữ lớn/bàn phím trên màn hình nhỏ không tràn ngang. Đã ký/cài đè/mở trên iPhone, giữ đăng ký Face ID từ Release 7; chờ người dùng nghiệm thu giao diện và Face ID thật. Source chưa commit/push.

## 18/10/2026 — Giữ đăng ký Face ID sau đăng xuất

- Tách đăng ký thiết bị V12 khỏi phiên: đăng xuất thu hồi phiên; Face ID/vân tay mở khóa thông tin riêng để server cấp phiên mới. Không lưu mật khẩu hoặc giữ refresh token đã đăng xuất. Đăng ký 90 ngày, thu hồi khi tắt, reset mật khẩu hoặc đăng xuất toàn bộ.
- Cùng tài khoản đăng nhập mật khẩu/câu hỏi vẫn giữ đăng ký; đổi tài khoản xóa thông tin cục bộ cũ. Thêm nút Face ID trực tiếp trên màn đăng nhập và giữ các form xác thực khi quay lại từ nền.
- Bản Release 1.0.0 (7) đã ký/cài/mở trên iPhone. Cần bật lại Face ID một lần sau nâng cấp. Ba vòng logout→login và hai Identity đã kiểm tra bằng API thật; cảm biến/UI iPhone vẫn cần người dùng nghiệm thu. Source chưa commit/push.

## 18/10/2026 — Xác thực mã trước khi tạo mật khẩu, bỏ ảnh đính kèm

- Tách màn nhập/xác thực mã và màn tạo mật khẩu mới. API verify cấp quyền ngẫu nhiên tồn tại 5 phút; V11 chỉ lưu hash. Xác thực mã không đổi mật khẩu/phiên; API reset từ chối khi chưa verify, sai thiết bị, hết hạn hoặc dùng lại quyền.
- Thay logo CID bằng thương hiệu HTML trong cả thư mã và thư thông báo, không gửi ảnh hoặc tệp đính kèm. Email cũ không tự thay đổi; cần kiểm tra thư mới.
- Cập nhật backend, ký/cài/mở iPhone Release 1.0.0 (6). Kiểm thử tự động và API thật qua hai Identity/Resend test sink đạt; nhận thư cũ đã có bằng chứng Gmail, thư mới và UI chia bước cần người dùng nghiệm thu lại. Không commit/push.

## 18/10/2026 — Quên mật khẩu bằng mã email trên app native

- Bổ sung luồng email → mã 6 số/mật khẩu mới → đăng nhập lại; chỉ dùng Resend với sender CareFlow <careflow@supersignal-resend.id.vn>. Câu hỏi/passkey không còn đặt lại mật khẩu; các API cũ trả 410.
- Thêm bảng mã băm V10, hiệu lực 5 phút/một lần dùng, 5 lần sai, gửi lại sau 60 giây và tối đa 5 yêu cầu mỗi email/giờ; thu hồi toàn bộ phiên sau reset. Không seed OTP, không đưa API key vào source/app.
- Cập nhật Identity/Gateway runtime, ký/cài/mở iPhone Release 1.0.0 (5); giữ màn nhập mã khi chuyển sang Mail và vẫn khóa phiên sinh trắc học cũ. Source/runtime chưa commit/push.
- Đã kiểm tra backend tự động, giao dịch/race trên PostgreSQL riêng và luồng thật qua hai Identity với Resend test sink; nhận thư cá nhân và reset qua UI iPhone còn chờ nghiệm thu. Web/Mobile Emulator chưa chuyển UI khôi phục sang email; đã nêu phạm vi ở trang 02 và 12.

## 18/10/2026 — Đưa phương thức đăng nhập lên app native

- Chuyển giao diện chọn phương thức/câu hỏi/bảo mật từ giả lập vào Flutter mobile; thay browser passkey bằng mở khóa phiên qua sinh trắc học native, tự nhận Face ID/Touch ID/sinh trắc học khả dụng.
- Giữ refresh trong kho yêu cầu sinh trắc học; phiên hoạt động ở bộ nhớ, khóa khi trở lại từ nền, xóa thiết lập khi đăng xuất; không lưu mật khẩu hoặc câu trả lời.
- Nối API thật cho lưu câu hỏi, đăng nhập và khôi phục mật khẩu. Backend runtime security-integration vẫn cần thiết; không sửa giả lập/schema.
- Đã cài/mở Release 1.0.0 (4) lên iPhone, build APK Android kiểm tra thành công, 36 kiểm tra tự động đạt và API câu hỏi/khôi phục thật từ Mac đạt. Bổ sung trang 12, nêu rõ Face ID/câu hỏi trên UI điện thoại và cảm biến Android còn cần nghiệm thu trực tiếp. Source mobile chưa commit/push.

## 18/10/2026 — Khôi phục kết nối iPhone khi IP Mac đổi

- Phát hiện IP Wi-Fi chuyển từ `192.168.1.9` sang `192.168.1.10` trong khi bản app cũ vẫn gọi địa chỉ cũ.
- Cập nhật cấu hình bản Release 1.0.0 (3) sang `MacBook-Pro-cua-DangKhoii.local`, cầu nối nghe IPv4/IPv6; giữ CA và xác minh TLS.
- Đã ký/cài/mở bản (3) trên iPhone; kiểm tra sáu bước API qua tên máy từ Mac đều HTTP 200. Đăng nhập trên iPhone vẫn cần xác nhận trực tiếp. Cập nhật điều kiện cùng Wi-Fi/mDNS và cách build lại ở trang 11.

## 18/10/2026 — Sửa xác thực và HTTPS của bản iPhone

- Bổ sung DPoP ES256 cho API/refresh, giữ khóa riêng trong secure storage theo Gateway; đóng gói CA công khai trong bản app test và giữ xác minh TLS.
- Kiểm tra 8 ca về chữ ký/contract/thông báo lỗi; đăng nhập, đọc hồ sơ, làm mới phiên và đăng xuất qua Gateway thật từ Mac đều HTTP 200 với tài khoản demo hiện hành.
- Đã ký/cài/mở Release 1.0.0 (2) trên iPhone 11 Pro Max iOS 18.1.1. Native runner còn gặp lỗi kết nối tới Mac; quyền Mạng cục bộ/cùng Wi-Fi và đăng nhập thành công trên iPhone chưa được xác nhận.
- Cập nhật trang 11: CA nằm trong app, quyền Mạng cục bộ và cách cài bundle đúng đường dẫn Xcode 27. Source ở nhánh mobile riêng, chưa commit/push; không đổi mật khẩu, reset DB hoặc chỉnh backend.

## 18/10/2026 — Cài bản iPhone độc lập

- Bổ sung trang 11 cho bản Flutter iOS gốc tại `develop@e5fc9c1`: kết nối iPhone, Personal Team, cài Release và mở từ biểu tượng.
- Ghi rõ bản build nằm ngoài Git, cấu hình API thật qua cầu nối HTTPS trên Mac và cách cài/gỡ CA thử nghiệm. Không đưa khóa TLS, Apple Account hoặc token vào repo.
- Đã ký và cài CareFlow 1.0.0 (1), bản Release ARM64, lên iPhone 11 Pro Max iOS 18.1.1. Chữ ký/entitlement khớp provisioning profile, hạn 25/10/2026; iOS đang yêu cầu người dùng tin cậy nhà phát triển trước khi mở app.
- Kiểm tra HTTPS health `200 UP`, API chưa đăng nhập `401` và WebSocket upgrade `101` trên Mac. Chưa nghiệm thu mở app hoặc kết nối backend trên iPhone thật; CA HTTPS còn cần cài/tin cậy, và các thay đổi riêng của giả lập trình duyệt chưa được đồng bộ vào bản native.

## 17/10/2026 — Hiện đầy đủ nút thêm thuốc, lưu/ký và hoàn tất

- Sửa lớp hiển thị của khung thêm thuốc, thanh thao tác và các hộp thoại khám để không bị vùng cuộn, thanh đầu trang hoặc trợ lý nổi che khuất.
- Giữ tiêu đề và nút Hủy bỏ/Xác nhận thêm ở hai đầu khung; chỉ phần nhập liệu cuộn. Thanh lưu/ký/hoàn tất tự xuống dòng ở cửa sổ hẹp.
- Kiểm tra vị trí và vùng bấm trên năm kích thước từ 800×500 đến 1440×800, gồm kích thước tương đương phóng to trình duyệt. Đã thêm thuốc và lưu toa nháp qua UI/API thật; hộp xác nhận ký hiện đủ nút.
- Bổ sung bước Xác nhận thêm trước Lưu nháp ở trang 06; làm rõ ca hoàn tất/toa đã ký sẽ khóa hoặc ẩn nút theo nghiệp vụ. Cập nhật lượt NOI-006; không mở lại ca NOI-003 đã hoàn tất.

## 17/10/2026 — Khôi phục luồng demo chính An / STAFF / Mobile

- Đối chiếu Web với `origin/develop` (`e5fc9c1`), giữ chức năng tiếp nhận, gọi/khám, kê toa và phát thuốc hiện có.
- Sửa STAFF/KTV bị 403 khi mở trang Tổng quan vốn có phần riêng theo vai trò; các trang bác sĩ/ADMIN vẫn được bảo vệ.
- Hiện thông báo cố định và nút Vào ca khám khi bác sĩ còn ca IN_PROGRESS, giữ quy tắc một ca đang khám.
- Hủy Consultation thử rỗng từ 16/10 bằng API của bác sĩ phụ trách; hủy lịch Ngoại cũ qua Mobile. Không reset DB/hàng đợi.
- Kiểm tra NOI-001 đến khám/ký toa/hoàn tất/phát thuốc trên API/DB thật. Chuẩn bị NOI-003 qua mobile và STAFF duyệt; lượt đã được gọi/bắt đầu trong lúc tiếp tục demo nên giữ IN_PROGRESS và hướng dẫn Vào khám, không reset trạng thái.
- Bộ demo chính đặt ở trang 01; bộ ROOM-11 giữ để đối chiếu các ca E2E trước đó. Mỗi vai trò dùng hồ sơ trình duyệt riêng.

## 17/10/2026 — Sửa phiếu hồ sơ người thân và danh sách tiếp nhận

- Sửa mobile báo không có quyền sau khi đặt cho hồ sơ thứ hai cùng tài khoản, chuyển đúng hồ sơ đã chọn và liệt kê lịch của các hồ sơ thuộc tài khoản.
- Khôi phục phiên khi tải lại trực tiếp màn chính/tab Lịch khám và trang chi tiết, tránh báo sai quyền hoặc danh sách rỗng trước khi xác thực xong.
- Tách API danh sách tiếp nhận, gồm phiếu chờ check-in, khỏi hàng đợi gọi khám chỉ gồm lượt đủ điều kiện.
- STAFF tự chọn phòng được phân công, hiện lỗi tải rõ ràng và tự cập nhật danh sách. Trang bác sĩ tự cập nhật sau tiếp nhận.

## 17/10/2026 — Làm rõ phạm vi tái khám khi cập nhật slide

- Đối chiếu script kiểm thử: lịch tái khám được tạo qua API xác thực và xem trên giao diện mobile. Chưa kiểm chứng thao tác tạo lịch qua Web.
- Trang 08 chỉ hướng dẫn xem mẫu lịch tương lai đã có; không mô tả thao tác tạo qua UI chưa được nghiệm thu.

## 17/10/2026 — Đồng bộ khung đăng nhập web

- Dùng chung banner bệnh viện tím và logo gốc giữa đăng nhập, đổi hình thức đăng nhập, quên mật khẩu và thiết lập bảo mật.
- Đồng bộ nền, độ rộng form, ô nhập và nút; banner trái giữ vị trí khi cuộn các form dài trên desktop.
- Giữ các bước chọn phương thức, quay lại và xác thực qua API thật.

## 17/10/2026 — Giữ giao diện đăng nhập web ban đầu

- Theo điều chỉnh của người dùng, khôi phục banner, logo, bố cục hai cột, form và nút đăng nhập Hospital Web ban đầu.
- Hai liên kết đổi hình thức đăng nhập/quên mật khẩu được bổ sung nhẹ dưới nút đăng nhập: chữ nhỏ, biểu tượng nét mảnh, hover tím nhạt và hỗ trợ focus bàn phím.
- Giao diện Mobile Emulator và các màn phương thức/khôi phục vẫn theo bản cập nhật trước.

## 17/10/2026 — Thiết kế lại UI/UX đăng nhập

- Tham khảo Google Passkey UX và FIDO: thẻ phương thức có biểu tượng/mô tả ngắn, quản lý khóa có ngày thêm, thao tác hủy và đổi phương thức rõ ràng.
- Đồng bộ các màn đăng nhập, chọn phương thức, khôi phục và thiết lập bảo mật; mobile theo tông xanh CareFlow, Hospital Web theo tông tím.
- Tách bước lựa chọn khỏi form, làm gọn bộ chọn câu hỏi, bổ sung thông báo lỗi/thành công và màn hoàn tất khôi phục. Hai hàng liên kết nằm dưới nút đăng nhập.
- Cập nhật thao tác tại trang 02; kiểm tra API thật và layout màn hình hẹp/phóng chữ. Không thay đổi API, schema hoặc quy tắc xác thực backend.

## 17/10/2026 — Bỏ MFA, đăng nhập thay thế và kiểm thử E2E thật

- Gỡ MFA/TOTP; thêm hai dòng đổi hình thức đăng nhập/quên mật khẩu trên Web và Mobile Emulator, câu hỏi bí mật và WebAuthn passkey thật. Ghi rõ sinh trắc học vật lý chưa nghiệm thu.
- Tạo bộ 12 tài khoản, hồ sơ/phân công riêng trong DB thật; chạy ba ca khám đến phát thuốc, hủy lịch, các nhánh bàn giao, hồ sơ gia đình/upload/download, thông báo và 403.
- Sửa danh mục CLS dùng API/DB, resume cùng ca từ lượt đọc kết quả, chặng queue ban đầu/đọc kết quả và điều kiện kết quả đã đọc trước hoàn tất.
- Sửa hoàn tất không ghi lại toa đã ký và dừng khi lưu thất bại; thử bằng cách dừng/khởi động lại service thật trên ca riêng.
- Sửa tên bệnh nhân trên form upload và tải tệp thực tế; cập nhật kết quả kiểm thử, phạm vi thanh toán/GPS/native push.
- Đối chiếu tái khám sau bàn giao: quyền đọc liên dịch vụ và bác sĩ phụ trách phải còn hoạt động đúng khoa/phòng.
- Source/runtime ở worktree tích hợp, thay đổi chưa commit/push; hướng dẫn ở gốc repo cũng được đồng bộ.

## 16/10/2026 — Gom hướng dẫn về một chỗ

- Tạo thư mục gốc `huong-dan-su-dung/` và mục lục theo thứ tự thao tác.
- Chuyển hướng dẫn E2E cũ của Mobile Emulator và hướng dẫn bàn giao về đây; đường dẫn cũ giữ liên kết chuyển tiếp.
- Bổ sung chuẩn bị Mac, tài khoản, MFA hai bước, đặt lịch/check-in, khám, CLS, toa/quầy thuốc, bàn giao, sau khám, xử lý lỗi và cách nghiệm thu.
- Đối chiếu bàn giao với 28 kiểm tra UI, cập nhật màn lỗi 403/404, toa nháp riêng của C và trạng thái cuối B phụ trách.
- Ghi rõ runtime/nhánh áp dụng và các nhánh nghiệp vụ chưa được nghiệm thu đầy đủ.
- Bổ sung quy tắc bắt buộc cập nhật hướng dẫn cùng commit/PR khi sửa hành vi người dùng; không duy trì hướng dẫn song song.

## 20/10/2026 — R4: thông báo và đồng bộ native

Thêm hướng dẫn 21: inbox toàn tài khoản, lịch sử phân trang, kiểm tra quyền khi mở tài nguyên, tải bù reconnect/resume, vòng đời token/session và các ca nghiệm thu push. Push APNs/FCM thật vẫn cần cấu hình/provisioning; xem biên bản R4 trước khi đánh dấu đạt. Cập nhật mốc cài bản19 trong trang11/18/20 và dữ liệu fixture tại trang01; không thay đổi credential demo.

## 21/10/2026 — R4: khóa phiên và thao tác thông báo chờ

Cài/mở iOS1.0.0(20); giữ thao tác mở thông báo qua chuyển nền/khóa để kiểm tra lại sau xác thực, bỏ binding khác tài khoản. Khóa UI ngay trước cleanup credential. Cập nhật trang01/11/18/20/21; thêm biên bản21/10. Mobile272 test đạt,15 bỏ qua; push thật vẫn chưa nghiệm thu do thiếu cấu hình Firebase/APNs.
