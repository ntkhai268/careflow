[Quay về mục lục](README.md)

# Nhập toa bằng giọng nói — bản backend thử nghiệm

Mốc 21/10/2026: code nằm trực tiếp trong `careflow-ai-service`, feature mặc định
tắt. Đã thử package Python cài sạch, worker ASR thật và API/job qua fixture
Identity/Consultation/Prescription. **Chưa ghép nút ghi âm/Lưu toa nháp lên web
bác sĩ, chưa nghiệm thu end-to-end với bệnh án thật.** Không có bước bắt duyệt
AI riêng trong luồng sản phẩm dự kiến; việc lưu/ký do Prescription đảm nhiệm.

## 1. Phạm vi có thể kiểm tra

- Worker nhận transcript hoặc audio, đối chiếu catalog được CareFlow truyền vào.
- AI Service tạo job, trả trạng thái/kết quả, sửa transcript bằng version, hủy,
  chống gửi trùng và kiểm quyền người tạo còn phụ trách ca.
- Không tự ký, phát hành, cấp phát hoặc tạo thuốc mới. Job thành công chưa phải
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
`VOICE_PRESCRIPTION_URL`, `VOICE_WORKER_URL`, `VOICE_WORKER_TOKEN`, `VOICE_CALLER_KEY`.
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
không tự kết nối DB mới. Chatbot/voice cloud cũ vẫn bị chặn trong secure/prod.

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

Trong phiên bản web sau này: bấm Lưu toa nháp tự dừng mic, chờ AI và lưu DRAFT
qua Prescription; chỉ báo đã lưu khi có prescriptionId. Chỉnh sửa cập nhật cùng
toa, ký là thao tác riêng. Luồng web này hiện **chưa triển khai trong bản backend**.

## 5. Dữ liệu và giới hạn

Audio/transcript không ghi log hoặc tự đưa vào train. Input tạm bị xóa khi job
kết thúc/hủy/hết hạn; result TTL15 phút; audit/idempotency metadata giữ30 ngày.
Worker không dùng Gemini/ElevenLabs/TTS làm fallback. Test ASR thật là audio TTS
không có bệnh án thật; không thay cho nghiệm thu chuyên môn hoặc điểm bác sĩ thật.
