# CF-AI-VP v1 — nhập toa bằng giọng nói

Ngày 21/10/2026. Phạm vi triển khai: `careflow-ai-service`, nhánh
`develop` theo yêu cầu người dùng, không commit/push tự động.
Nguồn thiết kế: kế hoạch `security-integration/.planning/ai-service/voice-prescription-integration-plan-2026-10-20.md`.

## Mức bàn giao

Đã có package Python độc lập và API/job Java có persistence, quyền, version,
idempotency, lease, quota, TTL và audit. Có kiểm thử HTTP với dependency fixture,
và kiểm worker cài từ wheel với ASR thật, không mạng ngoài. **Chưa nghiệm thu
Gateway/Identity/Consultation/Prescription/Web thật cùng nhau**, chưa có test
giọng bác sĩ độc lập và chưa mở feature trên runtime bệnh viện.

Checkout gốc chưa tự có toàn bộ R1–R3 chưa commit trong worktree bảo mật. Đặc biệt
Identity phải có `/api/auth/session` theo contract active-session; thiếu endpoint
này thì voice phải trả lỗi, không dùng JWT/header tự khai làm fallback. Không
copy thay đổi liên service từ worktree khác vào checkout này.

## Ownership và luồng

AI chỉ tạo **đề xuất**, không gọi write API của Prescription/Lab/Consultation.
Web tương lai điều phối: bác sĩ bật ghi âm → bấm Lưu toa nháp (tự dừng mic) →
tạo/poll job → Prescription tạo DRAFT → editor của cùng toa → cập nhật → ký riêng.
Không có bước duyệt AI riêng bắt buộc nếu đầu ra lưu được. `SUCCEEDED` không có
nghĩa toa đã lưu, ký hoặc cấp phát. Việc liên kết `sourceAiJobId/sourceAiRevision`
và chống tạo trùng DRAFT thuộc PR Prescription/Web, chưa làm trong service này.

## API public

Prefix `/api/ai/voice-prescription/jobs`. JSON camelCase, UUID, thời gian ISO.
Envelope `ApiResponse`: status/message/data/timestamp. Response không có local path.

| Method | Request | Response |
|---|---|---|
| POST | JSON `{consultationId, transcript}` + `Idempotency-Key` | 202, job |
| POST | multipart `consultationId`, part `audio` + `Idempotency-Key` | 202, job |
| GET `/{id}` | Bearer phiên hợp lệ | 200, job và result nếu có |
| POST `/{id}/revisions` | `{expectedVersion, transcript, append}` | 202; xử lý toàn transcript trên cùng job |
| DELETE `/{id}` | Bearer phiên hợp lệ | 200; CANCELLED, xóa nội dung; không xóa toa |

JSON audio/text là hai kiểu tách biệt; JSON text có field audio/field lạ bị từ
chối. Audio tối đa 20 MiB, 180 giây sau decode. MIME WAV/WebM/Ogg/MP4/MPEG/FLAC;
codec parameter của MediaRecorder được tách khỏi base MIME. Không nhận URL/path.

Job `data` gồm `jobId, consultationId, version, transcriptRevision, state,
schemaVersion, result, errorCode, createdAt, expiresAt`. Không trả actor token,
input base64, hash idempotency hoặc credential. `result` khi thành công:

```json
{
  "schemaVersion": "careflow.voice-prescription.v1",
  "transcript": "Cho Paracetamol, số lượng sáu viên.",
  "transcriptRevision": 1,
  "transcriptHash": "<SHA256 UTF-8 của transcript NFC>",
  "catalogVersion": "<SHA256 catalog server cung cấp>",
  "items": [],
  "warnings": [],
  "modelInfo": {"extractor": "rules-v3", "clinicalValidation": false},
  "prescriptionDraft": {"items": [], "blockers": [], "canSaveWithoutCorrection": false},
  "processingMs": 0
}
```

Ví dụ trên là cấu trúc, không phải expected output của câu. Fixture chạy được
nằm trong `inference/voice-prescription/contracts/` và tests, không có bệnh án thật.

Mỗi item giữ `spokenName`, `normalizedName`, `strength`, `dosageForm`, `quantity`,
`quantityUnit`, `dose`, `frequency`, `route`, `duration`, `timing`, `minInterval`,
`maxDailyDose`, `medicineCode`, `candidates`, `missingFields`, `issues`,
`fieldConflicts`, `evidence`. Trường không nghe được là null; không bù liều.
Không tạo confidence/xác suất giả. Tên gần âm chỉ là candidate, không tự chọn top1.

Evidence: transcript NFC, offset Unicode code point, start inclusive/end exclusive,
`text`, revision/hash và `offsetUnit=unicode_codepoint`. Java kiểm substring bằng
`offsetByCodePoints`; không dùng trực tiếp index UTF-16. Sửa transcript xóa result
cũ và tăng revision; evidence cũ không còn được dùng. Test có ký tự ngoài BMP.

## Quyền và phụ thuộc

Mỗi thao tác lấy user/role từ Identity active-session qua Bearer, không từ
`X-User-Id`, `doctorId` hay transcript. Chỉ DOCTOR đang phụ trách consultation,
trạng thái `IN_PROGRESS` hoặc `READY_TO_COMPLETE`. Kiểm lại khi tạo/đọc/sửa/xóa,
trước và sau inference; sau bàn giao người cũ bị chặn, người mới không đọc job cũ.

AI chuyển tiếp Bearer/correlation tới Identity/Consultation/Prescription nhưng
**không đưa user token vào worker hoặc DB**. Service caller có credential riêng;
production cần HTTPS và cấu hình mTLS/truststore theo baseline bảo mật. HTTP chỉ
cho loopback với opt-in, bị chặn trong secure/prod/production.

Identity: `GET /api/auth/session`, envelope data có active/userId/role/sessionId/
expiresAt. Xác minh DPoP method/path được chuyển khi có. Consultation: `GET
/api/consultations/{id}`, lấy doctor/status từ server. Catalog: `GET
/api/prescriptions/medicines`; endpoint hiện trả danh sách thuốc active. Worker
nhận snapshot code/name/unit/active và hash, không nhận description làm dữ liệu
hoạt chất chuẩn, không DAV ID, patientId hoặc EMR.

## Mapping sang Prescription

Chỉ exact name duy nhất từ snapshot active được đề xuất code tự động. Mơ hồ,
heuristic gần âm, xung đột hoặc không có thuốc tạo blockers, không tạo DRAFT giả.
Tên/code/unit của proposal lấy từ catalog. `dose→dosage`, frequency/timing giữ
nguyên. Chỉ quantity nguyên dương và duration biểu đạt rõ số ngày nguyên được
map Integer; không làm tròn, không đổi tuần/tháng hoặc suy thời gian từ số lượng.

strength/dosageForm/route/minInterval/maxDailyDose được giữ trong `unmappedFields`
cùng evidence. Có blocker để editor xử lý/mapping contract trước khi auto-save;
không lặng lẽ bỏ hoặc nhét vào sai field. Prescription vẫn phải kiểm mã thuốc và
validation riêng khi lưu/cập nhật/ký. Kết quả AI không tự nới validation phát hành.

## Hàng đợi, độ bền và retention

Một inference coordinator/instance, tối đa 8 đợi + 1 chạy, tối đa 2 job đang chờ/
chạy mỗi bác sĩ. SQL capacity-row lock kiểm quota xuyên instance; enqueue có
unique actor/idempotencyKey và request hash. Cùng key/nội dung trả cùng job;
khác nội dung 409. Revision dùng CAS/version, cancellation/expiry chặn late write.

QUEUED → RUNNING → SUCCEEDED/FAILED. CANCELLED/EXPIRED là terminal. Bearer chỉ giữ
trong bộ nhớ task. Lease owner riêng/instance, heartbeat 10 giây, lease 30 giây.
Sau crash, hết lease thì job FAILED/SERVER_RESTARTED và xóa input, không replay
không có phiên. Startup instance khác không được hủy job còn lease sống.

TTL mặc định 900 giây từ lúc tạo. Input text/audio xóa khỏi DB ngay khi terminal;
result xóa khi hủy/hết TTL. Metadata idempotency/audit giữ 30 ngày rồi cleanup;
audit chứa ID, action, version, hash, schema/catalog/model metadata, correlation,
thời điểm, không chứa transcript/audio/Bearer. Không tự dùng audio này cho train.
DB/backups phải có quyền, encryption/retention tương ứng trước nhận dữ liệu thật.

## Worker nội bộ

`POST /internal/v1/voice/infer`, JSON có schemaVersion, transcriptRevision,
catalogVersion, medicines và đúng một trong transcript hoặc audioBase64 + MIME.
Service Bearer token riêng tối thiểu 32 ký tự, so sánh constant-time. Không có UI,
SQLite prototype, trainer, crawler, TTS hay provider cloud fallback. Model load
ở thread riêng duy nhất và giữ thường trú; 1 inference slot, 8 connection threads.

`/health/live` kiểm process; `/health/ready` yêu cầu service auth và chỉ đạt khi
model/adapter/tokenizer/checksum/ffmpeg đã nạp được. Text-only contract test vẫn
chạy khi chưa provision model nhưng readiness audio trả 503, không giả ready.
FFmpeg timeout 30 giây, model deadline 150 giây; model treo quá hạn làm readiness
false tới khi restart. Java HTTP inference timeout 210 giây. Quá tải 429, lỗi
model 503, input sai 422; không trả raw exception/model filesystem path.

MLX chỉ hỗ trợ đã thử trên Mac Apple Silicon. Chưa cung cấp Linux/CUDA backend.
Worker default loopback; remote/production cần TLS/mTLS proxy cấu hình riêng.

## GitNexus Impact Assessment

Repo được bind bằng đường dẫn `/Users/dangkhoii/careflow`, HEAD e5fc9c1. Checkout
hiện tại là `develop` theo yêu cầu người dùng; toàn bộ thay đổi chưa commit được
giữ nguyên khi chuyển nhánh. Trước đó `git pull --ff-only origin main` trả Already
up to date. Không stash/reset/ghi đè thay đổi sẵn có.

Đã re-index và query flow voice/clinical. Trước sửa method legacy
`AiVoiceProcessingService.processVoiceToFields`: impact LOW, 1 caller trực tiếp
`AiVoiceController.processVoiceToFields`, 1 process/module được graph nhận diện.
Thay đổi legacy duy nhất là bỏ log transcript. Các lớp voice mới tách namespace,
không đổi flow ký hoặc service khác. Graph có cảnh báo giới hạn coverage flows;
không coi số zero ở flow khác là bằng chứng tuyệt đối không ảnh hưởng. Test HTTP
và đối chiếu source được dùng bổ sung. Không commit/push.

## Gate phát hành còn thiếu

- Ghép Gateway + session/mTLS thật và recheck assignment trên baseline R1–R3.
- PostgreSQL/migration, TLS, retention/backups và tải đồng thời trên môi trường đích.
- Web mic và thao tác Lưu toa nháp → Prescription tạo/cập nhật đúng DRAFT, nguồn
  AI có idempotency bền vững, ký riêng. Chưa có thay đổi frontend trong lượt này.
- Kiểm extraction/catalog trên tập chốt; giữ baseline cũ 0/313 positive full
  draft match, không gọi smoke audio hoặc fixture test là điểm chất lượng.
- Test bác sĩ thật độc lập, codec/browser và ngưỡng do phụ trách chuyên môn chốt.

Hướng dẫn thao tác duy nhất: `huong-dan-su-dung/23-nhap-toa-giong-noi-ai.md`.
