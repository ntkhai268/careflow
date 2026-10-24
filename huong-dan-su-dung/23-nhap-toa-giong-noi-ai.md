[Quay về mục lục](README.md)

# Nhập toa bằng giọng nói

**Cập nhật nhận diện24/10/2026:** có thể đọc thẳng **tên thuốc, hàm lượng, tần suất**; không cần nói “thuốc”, “kê”, “thêm” hoặc đánh số thứ tự. Các dấu hiệu này gắn cách dùng vào từng tên; thuốc ngoài catalog vẫn giữ bằng tên. Từ dẫn nghe sai như “hốt thứ tư” không còn làm mất dòng. Thiếu/mơ hồ ở liều hoặc trường khác vẫn hiện để sửa, không làm mất tên và không tự điền.

**Sửa trích xuất24/10/2026:** cách đọc “thuốc thứ nhất/hai/ba/tư” giữ đủ dòng, cả tên ngoài catalog; số ngày bằng chữ được giữ đúng. Xóa hết dòng DRAFT rồi nhập/ghi mới thay nội dung trên cùng toa. Xem mục cuối trang. Các job/toa mẫu cũ không tự đổi kết quả; muốn kiểm bản sửa thì chọn **Nhập đoạn mới**, dùng lại transcript (xóa các dòng thử cũ nếu muốn thay), rồi xử lý/lưu.

**Bản hiện tại24/10/2026 — ghi trực tiếp và thời lượng:** đã bỏ nút/cửa sổ “Mở rộng không gian nhập toa”. Ghi âm, nghe lại, transcript và sửa đơn nằm ngay trang khám. Bộ đếm hiện **mm:ss**, có chỉ báo mức âm thanh; dừng vẫn giữ thời lượng ở thẻ bản ghi và trình nghe lại. Sửa metadata WebM trong trình duyệt, không tải audio lên dịch vụ ngoài. Các biên bản cửa sổ mở rộng bên dưới là lịch sử, không mô tả UI hiện tại.

**Mốc24/10/2026 — ghi nhiều lần và một đơn chung:** chỉ có một khu vực **Đơn thuốc** nằm trực tiếp ở trang khám. Một khung **Xác nhận toàn bộ đơn thuốc trước khi ký** ở cuối danh sách đại diện cho tất cả thuốc. DRAFT cho ghi thêm nhiều đoạn vào cùng mã toa, giữ các chỉnh sửa đã có; toa đã ký vẫn khóa. Nghiệm thu runtime cho mốc này được ghi trong biên bản `voice-repeat-2026-10-24.json`; không thay cho nghiệm thu micro vật lý/chuyên môn.

**Mốc24/10/2026 — bản sửa toa có thể chỉnh:** Hospital Web lưu các tên thuốc nhận được vào DRAFT, kể cả khi chưa có mã danh mục, và cho chỉnh trực tiếp từng ô trong đơn giấy; không còn khối “Kiểm tra thuốc nhận được” chặn lưu. Có phần tải/nghe lại audio và transcript trực tiếp trên trang khám; worker bật context tên từ danh mục bệnh viện. AI API chạy Docker, GPU worker giữ trên Mac. Xem [vận hành Docker](26-voice-va-docker.md) và [biên bản đối chiếu prototype](../.planning/ai-service/voice-parity-docker-2026-10-24.md). Các mốc R6/23 bên dưới là lịch sử; nghiệm thu chuyên môn/micro vật lý vẫn mở.

Mốc21/10/2026: R6 đã ghép nút ghi âm/Lưu toa nháp trên Hospital Web và API Prescription. Xem [trang24](24-tro-ly-lam-sang-r6.md) để thử cùng chatbot có nguồn. Feature mặc định tắt; stack Mac thử nghiệm xử lý local, không gửi audio tới dịch vụ AI ngoài. Chưa nghiệm thu chất lượng trên giọng bác sĩ/bệnh án thật.

## 1. Phạm vi có thể kiểm tra

- Worker nhận transcript hoặc audio, đối chiếu catalog được CareFlow truyền vào.
- AI Service tạo job, trả trạng thái/kết quả, sửa transcript bằng version, hủy,
  chống gửi trùng và kiểm quyền người tạo còn phụ trách ca.
- Web gọi Prescription để tạo DRAFT rồi sửa trên cùng toa; ký riêng. Không tự ký, phát hành, cấp phát hoặc tạo thuốc mới. Job thành công chưa phải
  toa đã lưu; không báo bác sĩ “Đã lưu toa nháp” khi chỉ nhận SUCCEEDED.

## 2. Chuẩn bị của kỹ thuật viên

Giữ runtime nghiên cứu/dataset/model ngoài Git. Cần Java 21+, Maven, PostgreSQL
DB riêng AI; worker Mac Apple Silicon, Python 3.11, FFmpeg. Linux image Java
không tự chạy được model MLX. Đọc contract `.planning/ai-service/voice-prescription-contract.md`.

Cần baseline Identity có GET `/api/auth/session` trả phiên active và các service
kiểm quyền hiện hành. Bản gộp develop có source bảo mật R1–R3; cần triển khai đúng bộ service và
private config tương ứng trước khi bật; không dùng header giả hoặc tắt kiểm
phiên để làm demo trông thành công.

Cài worker từ thư mục `careflow-ai-service/inference/voice-prescription/`:

```sh
uv sync --locked --extra mlx --extra test --python python3.11
```

Provision bản model cục bộ đã được đánh giá vào thư mục private **ngoài repo**:

```sh
python tools/provision_model.py --base /private/models/base-snapshot \
  --adapter /private/models/evaluated-adapter \
  --output /private/artifacts/voice-release --release-id reviewed-release-id
```

Đường dẫn trên chỉ là ví dụ. Tool kiểm base/adapter và tạo manifest đủ checksum,
không tự tải model, không lấy dữ liệu train. Xác minh điều kiện license trước
chia sẻ artifact. Token service do người vận hành cấp, không điền vào hướng dẫn
hay commit file chứa token.

Đặt `VOICE_MODEL_MANIFEST` trỏ manifest và `VOICE_WORKER_TOKEN` là secret riêng
tối thiểu 32 ký tự, rồi chạy:

```sh
uv run --locked careflow-voice-worker --host 127.0.0.1 --port 8770
```

Worker readiness chỉ đạt khi model/FFmpeg/checksum nạp được. Không có manifest
thì chỉ chạy contract text; không coi đó là sẵn sàng nhận audio.

## 3. Cấu hình Java và khởi động

Qua environment/secret store, cấu hình `VOICE_DB_URL`, `VOICE_DB_USER`,
`VOICE_DB_PASSWORD`, `VOICE_IDENTITY_URL`, `VOICE_CONSULTATION_URL`,
`VOICE_PRESCRIPTION_URL`, `VOICE_WORKER_URL`, `VOICE_WORKER_TOKEN`, `VOICE_CALLER_KEY`, `AI_CONTENT_KEY`.
Worker secure cần `VOICE_TLS_CERT`, `VOICE_TLS_KEY`, `VOICE_TLS_CA`; kết nối phải dùng HTTPS/mTLS.
DB mặc định hướng remote PostgreSQL theo repo; không tự migrate DB dùng chung
trước khi người phụ trách cấp DB/quyền. Migration chỉ chạy khi feature được bật.

Production cần HTTPS/mTLS/truststore theo baseline. Chỉ kiểm thử cùng máy mới
đặt `VOICE_ALLOW_LOOPBACK_HTTP=true`; secure/prod/production từ chối lựa chọn đó.
Cuối cùng đặt `VOICE_PRESCRIPTION_ENABLED=true` trên môi trường kiểm thử đã cấu
hình đầy đủ. Không dùng cờ demo chatbot để mở voice.

```sh
mvn -pl careflow-ai-service -am test
```

Khởi động AI Service bằng quy trình chạy backend hiện có sau khi kiểm dependency
sẵn sàng. Chưa bật feature thì `/api/ai/voice-prescription/jobs` trả503 có kiểm soát,
không tự kết nối DB mới. Các endpoint cloud cũ vẫn bị chặn; chatbot local chỉ mở khi bật capability riêng.

## 4. Kiểm luồng kỹ thuật

Qua Gateway đã xác thực, tạo POST `/api/ai/voice-prescription/jobs` với JSON
`consultationId/transcript` hoặc multipart `consultationId/audio`, Bearer và
`Idempotency-Key`. Không gửi đồng thời text và audio. Chỉ bác sĩ phụ trách ca ở
trạng thái cho phép; sai người hoặc bàn giao phải bị chặn.

Nhận202/jobId, poll GET job. Khi SUCCEEDED, kiểm transcript, trường trống,
candidates, evidence và blockers. Không tự chọn mã gần nhất hoặc làm tròn số
lượng/ngày. Trường chưa map sang Prescription nằm trong unmappedFields.

Thử lại cùng key/nội dung phải trả cùng job; key cũ/nội dung khác409. Sửa transcript
gửi expectedVersion; version cũ409, kết quả/evidence cũ mất hiệu lực. Hủy job
không được hủy toa đã lưu. Job hết15 phút không trả lại nội dung; crash hết lease
phải thất bại rõ thay vì kẹt RUNNING. Hàng đợi đầy429; thiếu model/Identity503.

Trong R6, bấm Lưu toa nháp tự dừng mic, chờ AI và lưu DRAFT qua Prescription; chỉ báo đã lưu khi có prescriptionId. Chỉnh sửa cập nhật cùng toa, ký là thao tác riêng. Luồng này đã có trên web thử nghiệm.

## 5. Dữ liệu và giới hạn

Audio/transcript không ghi log hoặc tự đưa vào train. Input tạm bị xóa khi job
kết thúc/hủy/hết hạn; result TTL15 phút; audit/idempotency metadata giữ30 ngày.
Worker không dùng Gemini/ElevenLabs/TTS làm fallback. Test ASR thật là audio TTS
không có bệnh án thật; không thay cho nghiệm thu chuyên môn hoặc điểm bác sĩ thật.


## R6: thử trên Hospital Web

### Giao diện hiện tại — 24/10/2026

1. Mở ca đúng bác sĩ tại trang01. Trong **Nhập toa bằng giọng nói**, bấm **Bật ghi âm AI**, cấp quyền micro. Không có cửa sổ mở rộng. Nút chuyển sang **Dừng ghi âm (mm:ss)**; bên dưới hiện bộ đếm và mức âm thanh. Nếu chưa thấy tiếng rõ, kiểm tra micro đang chọn và thử nói gần hơn.
2. Bấm **Dừng ghi âm**: micro dừng, bản ghi hiện thời lượng và nút nghe lại; thẻ vàng **Đã ghi mm:ss · Chưa thêm vào toa nháp** phân biệt với toa đã lưu. Nút nghe lại phải hiện tổng thời gian, không đứng0:00 do thiếu metadata WebM. Có thể **Tải audio**, tối đa3phút/20MiB; bản ghi gốc chỉ ở bộ nhớ tab, không khôi phục sau refresh.
3. **XỬ LÝ & LƯU TOA NHÁP** tại phần bản ghi, hoặc **LƯU TOA NHÁP** ở thanh hành động, dừng mic nếu cần, nhận dạng và lưu các dòng có tên vào DRAFT. Thành công hiện **Đã xử lý đoạn ghi âm · mm:ss**; thời lượng này khác số giây AI xử lý. Thuốc chưa có mã danh mục vẫn lưu bằng tên để bác sĩ sửa; không tự gán mã, đổi sang thuốc gần âm hoặc thêm master data. Thời gian nhận dạng/đọc toa là số đo worker, không phải tổng qua Gateway, kiểm quyền và lưu DB.
4. Đơn hiện STT, tên/hàm lượng, số lượng/đơn vị và cách dùng. Trường chưa nghe được để trống/“—”; không tự bù liều/ngày hoặc chọn thuốc gần âm. Danh mục CareFlow hiện có15 thuốc active, khác catalog tham khảo871 hồ sơ của prototype.
5. Sau lưu nháp, mở **Chỉnh sửa thuốc** ngay dưới từng dòng. Sửa tên, hàm lượng, số lượng, đơn vị, liều mỗi lần, tần suất/điều kiện dùng, thời điểm, số ngày, dạng bào chế và các chi tiết khác → **LƯU TOA NHÁP**. Sửa tên/hàm lượng bỏ liên kết mã cũ. Thêm, xóa hoặc sửa bất kỳ dòng nào sẽ bỏ xác nhận toàn đơn để kiểm tra lại. Mở lại/tải lại giữ cùng mã toa và thay đổi đã lưu.
6. Trong cùng tab, transcript gốc có thể lấy lại sau refresh khi job còn hạn15phút và bác sĩ còn quyền ca. Browser chỉ giữ mã job/toa và số giây của đoạn đã xử lý gần nhất theo bác sĩ/ca, không lưu transcript/audio/token vào storage. Transcript hết hạn: thuốc đã lưu vẫn giữ. Kết quả AI gốc không được áp lại lên thay đổi của bác sĩ.
7. Cuối khu vực **Đơn thuốc** có duy nhất một khung **Xác nhận toàn bộ đơn thuốc trước khi ký**. Kiểm tra tất cả dòng, bổ sung tên/đơn vị còn thiếu trong **Chỉnh sửa thuốc**, rồi tick **Tôi đã kiểm tra tên thuốc, đơn vị và cách dùng của tất cả thuốc trong đơn**. Lưu nháp hoặc ký sau đó. Không xác nhận riêng từng thuốc; toàn trang chỉ có một danh sách thuốc. Nút Ký đưa tới khung xác nhận chung khi chưa kiểm tra. Thêm/xóa/sửa thuốc yêu cầu xác nhận lại toàn đơn.
8. **KÝ & XÁC NHẬN ĐƠN** → xác nhận → **HOÀN TẤT PHIÊN KHÁM** → xác nhận. Chưa lưu bản ghi thì Hoàn tất đưa tới phần nhập toa; thiếu thông tin khi ký thì server từ chối và mở các ô sửa. Bấm Dừng hoặc rời trang/đăng xuất để dừng mic; mở công cụ danh mục cũng dừng mic. Rời trang giải phóng audio tạm. Ký vẫn là thao tác riêng.

Đã thử WAV tổng hợp qua ASR/API Docker và sửa/reload toa thật. Kiểm mic bằng MediaStream tổng hợp chỉ chứng minh vòng đời track. Bản inline được kiểm riêng; shell Hospital Web trên màn hình nhỏ và micro vật lý chưa nghiệm thu, nên thử luồng bác sĩ trên cửa sổ desktop đầy đủ.

### Bộ dữ liệu sẵn để thử ngày23/10/2026

Đăng nhập bác sĩ của bộ voice ở [trang01](01-tai-khoan-va-du-lieu.md), mở [TEST VOICE NOI MOT / NOI-002](http://localhost:5173/consultation/a681e915-fc33-45cf-b0ad-5e792588e535). Ca đang khám, chưa có toa lúc chuẩn bị; có thể thử ngay mà không cần đặt lịch/check-in lại. Trên Chrome ở `http://localhost:5173`, cho phép micro khi bấm **Bật ghi âm AI**.

Đoạn đọc tổng hợp để kiểm phần mềm, không phải hướng dẫn điều trị: “Kê Paracetamol 500mg, mỗi lần một viên, ngày hai lần, buổi sáng, dùng trong ba ngày, số lượng sáu viên.” Danh mục thật có MED001; kiểm transcript và từng trường trước khi lưu/sửa. Đã kiểm job đầu vào text thành công, chưa ghi nhận micro vật lý hoặc đảm bảo ASR luôn chép đúng đoạn này. Bấm **LƯU TOA NHÁP** để dừng mic và xử lý; nếu nhận sai tên thuốc thì sửa transcript theo danh mục và thử lại.

Hồ sơ dự phòng và slot22:00–23:00 ngày23/10 ở ROOM-01 được mô tả tại trang01. Các slot này dùng khi muốn thử thêm luồng đặt lịch; chúng không phải điều kiện để dùng ca không hẹn đang mở. Sau ngày23/10 phải tạo lịch/lượt mới theo ngày thực tế.

### Thao tác

1. Đăng nhập DOCTOR, mở ca đang phụ trách chưa bị khóa, chưa có toa của mình.
2. Bấm **Bật ghi âm AI**, cho phép micro. Giới hạn180giây/20MiB. Chỉ dùng dữ liệu tổng hợp khi chưa nghiệm thu model.
3. Bấm **LƯU TOA NHÁP** khi đang ghi; mic phải dừng. Chờ nhận dạng và lưu, không bấm ký trong lúc đang xử lý.
4. Có tên thuốc thì lưu nháp được dù chưa khớp danh mục. Nếu chưa nhận được tên nào, sửa **Transcript toa thuốc** rồi lưu lại; không tạo toa rỗng. Có thể chọn **Nhập transcript** để thử không cần mic.
5. Khi DRAFT đã lưu, sửa trực tiếp các ô dưới **Chỉnh sửa thuốc**, rồi **LƯU TOA NHÁP**. Trường AI chưa xác định hoặc mâu thuẫn để trống để bác sĩ bổ sung; không tự chọn một giá trị mâu thuẫn. Tải lại phải còn đúng thay đổi trên cùng mã toa. Xóa một dòng không xóa những dòng khác chưa có mã.
6. Chỉ sau khi kiểm tra đầy đủ mới bấm **KÝ & XÁC NHẬN ĐƠN** và xác nhận. Server chặn toa nguồn AI thiếu trường bắt buộc.
7. Rời trang/đổi tài khoản/đổi ca phải dừng mọi track mic. Lỗi model/mất mạng giữ trạng thái lỗi, không báo đã lưu. Gửi lại cùng job/revision không tạo toa thứ hai.

### Ghi âm nhiều lần trên cùng toa

- Trước khi lưu: **Dừng ghi âm** → **Ghi lại** thay đoạn chưa lưu. Muốn giữ đoạn vừa ghi, bấm **LƯU TOA NHÁP** trước.
- Sau khi có DRAFT: **Ghi thêm vào toa nháp** → đọc đoạn tiếp → dừng hoặc bấm **LƯU TOA NHÁP**. Các dòng mới được thêm vào **cùng mã toa**, giữ chỉnh sửa các dòng cũ. Có thể lặp lại, **Tải audio** hoặc **Nhập đoạn mới** nhiều lần; mỗi lượt tối đa3phút/20MiB, tổng đơn tối đa50dòng.
- **Bỏ đoạn mới** bỏ input chưa lưu, không xóa các thuốc đã lưu. Rời trang/đăng xuất dừng mic; stream được cấp muộn sau khi rời trang cũng phải dừng.
- Sau mỗi lần thêm, kiểm tra toàn danh sách và xác nhận lại toàn đơn. Không tự gộp các dòng cùng tên: bác sĩ cần sửa/xóa dòng đọc lặp trước khi ký. Retry cùng job/revision không thêm thuốc lần thứ hai.
- Chưa lưu đoạn mới thì **Ký** mở lại phần nhập, không ký trên nội dung cũ. Lưu và kiểm tra các thuốc mới trước khi ký. Toa đã ký/bị khóa không ghi thêm được.
- Khi bản ghi lỗi, ghi lại hoặc chuyển sang transcript. Nút **Lưu toa nháp** thông thường vẫn dùng được khi AI tắt.

## Các ô bắt buộc được đánh dấu đỏ — 24/10/2026

- Trong **Chỉnh sửa thuốc**, các trường có dấu **\*** cần đủ trước khi ký: tên, đơn vị, liều mỗi lần, tần suất/điều kiện dùng, thời điểm, số ngày và số lượng. Ô còn thiếu có viền/nền đỏ và lời nhắc ngay bên dưới. Số ngày/số lượng cần là số nguyên lớn hơn0.
- Bấm **KÝ & XÁC NHẬN ĐƠN** khi còn thiếu: hệ thống mở phần chỉnh sửa nếu đang gập, cuộn và đặt con trỏ vào ô đầu tiên cần bổ sung; chưa gửi yêu cầu ký. Đúng thuốc đã đủ thông tin không bị đánh đỏ theo thuốc khác.
- Điền hợp lệ thì lỗi của riêng ô đó tự hết. Ghi chú, dạng bào chế/đường dùng và các chi tiết tùy chọn không bị yêu cầu chỉ vì đang trống. Có thể tiếp tục **LƯU TOA NHÁP** khi còn thiếu; chưa được ký. Không tự điền liều hoặc cách dùng từ AI.
- Các ô được đánh dấu ngay trang khám, vẫn chỉ có một khung xác nhận toàn đơn. Bổ sung xong → xác nhận toàn đơn → ký. Server vẫn kiểm điều kiện phát hành độc lập.

## Khi cần sửa hoặc chưa ký được toa — 24/10/2026

- **Loratadin chưa có trong danh mục:** vẫn hiện tên và lưu DRAFT, không còn yêu cầu thay thuốc hoặc sửa transcript chỉ để khớp catalog. Mở ô sửa nếu tên/hàm lượng nhận sai. Việc này không tự tạo thuốc trong danh mục quản trị.
- **Trường còn trống hoặc mâu thuẫn:** điền lại ngay trên dòng thuốc; lời đọc gốc vẫn để đối chiếu. Ký yêu cầu đủ liều, tần suất, thời điểm, số ngày, số lượng; thuốc kê bằng tên cần thêm đơn vị; giao diện yêu cầu xác nhận một lần cho toàn đơn.
- **Thông tin đúng hết:** lưu nháp, kiểm tra rồi ký; không cần cố sửa transcript. Tick xác nhận toàn bộ đơn thuốc trước khi ký. Số nguyên như3.0 được lưu thành3, không làm tròn số lẻ; giá trị chưa xác định vẫn để trống để sửa.
- **Đã lưu nhưng chưa hoàn tất được:** ký và xác nhận đơn trước. Khi ký bị từ chối, trang cuộn tới phần sửa cùng thông báo của server. Sau khi ký thành công mới hoàn tất phiên khám.
- **Job hết hạn hoặc AI không sẵn sàng:** transcript gốc không còn tải lại được sau TTL, nhưng toa đã lưu vẫn chỉnh/sửa được; không cần gọi AI để sửa và lưu thuốc. Không chạy lại kết quả AI để ghi đè thay đổi bác sĩ.

Tải lại web để nhận bản sửa. Trước khi tải lại, sao chép transcript chưa lưu nếu muốn giữ nội dung đang nhập; audio tạm chỉ ở bộ nhớ tab. Chưa coi luồng mẫu là nghiệm thu giọng nói/chuyên môn hoặc toàn bộ R7.

### Thời lượng bản ghi và giới hạn khôi phục

- Khi đang ghi, bộ đếm dựa trên thời gian thực kể từ khi MediaRecorder bắt đầu, không đếm số lần timer chạy. Chỉ báo mức âm thanh giúp phát hiện đang im lặng hoặc chọn nhầm micro; không phải bằng chứng model hiểu đúng lời đọc.
- Dừng xong mới tạo bản nghe lại và sửa thời lượng WebM. Thời gian chỉ xuất hiện khi đã đo được; file tải lên không đọc được metadata không bị gán0:00 giả. Audio MP4 của trình duyệt hỗ trợ được giữ nguyên.
- Sau lưu, thời lượng ưu tiên kết quả decoder máy chủ. Cùng tab có thể khôi phục con số thời lượng sau refresh ngay cả khi transcript đã hết TTL, nhưng **không khôi phục audio gốc**. Không có lịch sử lưu lâu dài/tổng thời gian nhiều đoạn hoặc đồng bộ con số này sang thiết bị khác; đây là metadata của đoạn xử lý gần nhất trong phiên tab. Chưa lưu mà tải lại sẽ mất audio và thời lượng tạm.
- Các bản ghi cũ trước bản sửa có thể không còn thông tin thời lượng nếu job hết hạn; không suy ra thời gian hoặc hiện0:00 như đã ghi rỗng. Ghi mới để kiểm tra bản hiện tại, nghe lại trước khi xử lý.

## Toa nhiều thuốc, câu đọc bị ngắt và xóa hết để đọc lại —24/10/2026

- Cách đọc **Thuốc thứ nhất là… Thuốc thứ hai là…** được tách thành các dòng riêng, kể cả thiếu dấu câu và tên chưa có mã danh mục. Giữ nguyên tên/hàm lượng nghe được; không tự đổi Cetirizin/Loratadine sang thuốc gần âm hoặc thêm master data.
- Số ngày được nói rõ bằng chữ như **năm ngày/ba ngày** chuyển sang5/3 cho cả thuốc kê bằng tên, giữ đoạn lời đọc làm bằng chứng. Không tự đổi tuần/tháng, làm tròn số lẻ hoặc suy số ngày từ số lượng.
- Câu nối sau dấu chấm vẫn giữ số lượng/số ngày/thời điểm của đúng thuốc đang đọc. “Buổi sáng và buổi tối” là một lịch gồm hai thời điểm; nếu đồng thời nói ngày một lần thì vẫn báo mâu thuẫn. Không sửa “Này hai lần” thành “ngày hai lần” hoặc đoán liều từ “mỗi lần bốn. 1 viên”; ô tương ứng để trống/đỏ để bác sĩ sửa theo audio.
- Nếu đã có DRAFT và bấm **Xóa toàn bộ đơn** rồi ghi/nhập đoạn mới → **XỬ LÝ & LƯU TOA NHÁP**, nội dung mới thay các thuốc đã xóa trên **cùng mã toa**. Không cần giữ sẵn một thuốc cũ. Chỉ thay khi đề xuất mới hợp lệ; lỗi xử lý giữ toa đã lưu trên máy chủ, đoạn mới vẫn có thể sửa/thử lại.
- Nếu còn ít nhất một dòng, bản ghi mới được **thêm** vào các dòng đang giữ; các dòng đã xóa không tự xuất hiện lại. Không có bản ghi/thuốc mới mà chỉ xóa hết rồi lưu thì cần thêm thuốc hoặc hủy toa; không lưu toa rỗng và không ký tự động.

Bản sửa được kiểm trên transcript mẫu bốn thuốc ở biên bản `voice-four-drugs-2026-10-24.json`. Đây là kiểm trích xuất/lưu phần mềm, không phải điểm chất lượng ASR trên mọi cách nói hay nghiệm thu y khoa.

### Đọc không đánh số thuốc

Ví dụ kiểm phần mềm: “Cetirizin10mg, ngày một lần, số lượng ba viên; Loratadin10mg, ngày hai lần, số lượng bốn viên.” Mỗi cụm tên/hàm lượng/tần suất phải thành một dòng riêng, giữ lượng/tần suất của đúng thuốc. Có thể dùng dấu phẩy, chữ “và” hoặc nói tiếp, không bắt buộc đọc thứ tự. Tên+hàm lượng ở một câu và tần suất ở câu kế tiếp vẫn được nối khi xác định được đúng thuốc.

Nếu ASR sai từ dẫn nhưng tên/hàm lượng còn rõ, đối chiếu tên trong toa nháp; nếu cách dùng chưa rõ thì sửa ô đỏ theo audio. Câu phủ định, tiền sử/đang dùng hoặc giả định không được tự coi là chỉ định mới. Tên chưa có danh mục không tự gán mã hoặc tạo master data. Các kết quả cũ không tự chạy lại: dùng **Nhập đoạn mới**, xóa các dòng thử cũ nếu muốn thay và xử lý lại; giữ phần đang nhập trước khi tải lại tab.
