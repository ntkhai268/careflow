# CF-AI-VP v1 — nhập toa bằng giọng nói

**Cập nhật ghi nhiều đoạn24/10/2026:** `/api/prescriptions/ai-drafts/editable` nhận thêm `prescriptionId` tùy chọn. Không có target giữ hành vi tạo DRAFT; có target chỉ thêm vào DRAFT cùng patient/consultation do bác sĩ còn phụ trách. Mỗi job/revision có advisory lock và một source link; retry đúng target trả toa hiện tại, khác target trả409. Append khóa hàng Prescription, giữ các dòng/chỉnh sửa cũ, giới hạn tổng50dòng. V4 bỏ unique trên `prescription_ai_sources.prescription_id`, thêm index thường; PK(job_id,transcript_revision) và FK không đổi. DBML đã đồng bộ, migration V3→V4 có kiểm dữ liệu cũ.

Web chỉ có một editor Đơn thuốc trực tiếp trên trang; dialog mở rộng đã bỏ. Một checkbox cuối đơn đánh dấu doctor review cho tất cả dòng, không để AI tự xác nhận. Thêm/xóa/sửa dòng hoặc append bỏ xác nhận toàn đơn trên UI; bản ghi mới chưa lưu chặn ký. Các validator server về tên/mã/cách dùng và xác nhận thuốc không có mã vẫn bắt buộc. Lưu trước các sửa đổi local rồi append giữ chúng trên cùng toa; latch đồng bộ chống nhấp lưu hai lần. Không cam kết xử lý stale ordinary PUT từ nhiều tab. Micro có latch/epoch, stream cấp muộn sau đóng không được bắt đầu; mỗi bản ghi là job riêng, không nối các Blob container. Ký là bước riêng.

**Cập nhật luồng sửa24/10/2026:** Hospital Web dùng POST `/api/prescriptions/ai-drafts/editable` với cùng `jobId/transcriptRevision`. Prescription tạo DRAFT từ mọi dòng đã nhận được tên, kể cả `medicineCode=null`; không chọn candidate gần âm, không thêm Drug/master data. Các trường mâu thuẫn để trống cho bác sĩ sửa trong toa; metadata nguồn/hash và idempotency vẫn giữ. Endpoint `/ai-drafts` cũ giữ chế độ đối chiếu catalog nghiêm ngặt để tương thích client cũ. Hai endpoint dùng chung liên kết job/revision→toa nên không tạo trùng hoặc ghi đè chỉnh sửa khi retry.

Sửa tên/hàm lượng trên UI bỏ liên kết code cũ và xác nhận cũ. Dòng kê bằng tên phải có `details.manualReview="confirmed"` do bác sĩ chọn, đơn vị và đầy đủ cách dùng/số ngày/số lượng trước khi ký. AI không được tự đặt xác nhận này. Validation ký áp dụng ở server kể cả khi gọi API trực tiếp; chỉ bác sĩ phụ trách ca còn hợp lệ được lưu/cập nhật/ký. `details` dùng JSONB hiện có và `medicine_code` đã nullable; không thay schema. Số lượng JSON3.0 được chuyển sang3 chỉ khi giá trị nguyên dương chính xác; không làm tròn giá trị phân số. Toa nháp vẫn sửa được sau khi job hết TTL; transcript gốc tuân theo retention cũ.

**Mốc24/10/2026:** source hiện tại ở worktree `release-r7`; AI API chạy Docker, worker MLX giữ GPU trên Mac. Context dùng tối đa50 tên/4000 ký tự từ snapshot catalog active của Prescription, không dùng transcript chuẩn làm gợi ý. Decode tương ứng prototype, bỏ ép768 token; giữ deadline model150giây và kiểm evidence/schema trước lưu. `result.timings` bổ sung `extractionMs/totalMs` và với audio có `decodeMs/asrMs/audioDurationSeconds`; đây là số đo worker, không gồm Gateway/queue/kiểm quyền/lưu Prescription. `processingMs` cũ vẫn là parser. `modelInfo.contextUsed` cho biết có context hay không. Xem [biên bản mới](voice-parity-docker-2026-10-24.md); hướng dẫn duy nhất tại [trang23](../../huong-dan-su-dung/23-nhap-toa-giong-noi-ai.md)/[trang26](../../huong-dan-su-dung/26-voice-va-docker.md).

Ngày21/10/2026. Phạm vi R6: AI Service, adapter Prescription và Hospital Web trên nhánh `feature/dangkhoii/ai-r6`; chưa commit/push.
Nguồn thiết kế: kế hoạch `security-integration/.planning/ai-service/voice-prescription-integration-plan-2026-10-20.md`.

## Mức bàn giao R6 —21/10/2026

Đã tích hợp API/job Java, local worker, Gateway và luồng web ghi âm → lưu DRAFT → chỉnh cùng toa → ký riêng. Xem [contract R6](r6-contract-2026-10-21.md), [biên bản](r6-acceptance-2026-10-21.md). Feature mặc định tắt; phép thử Mac chỉ dùng dữ liệu tổng hợp. Giới hạn chuyên môn/giọng bác sĩ vẫn mở.

AI chỉ tạo đề xuất. Prescription sở hữu tạo/cập nhật/ký toa, bảng `prescription_ai_sources` bảo đảm cùng job/revision trả cùng toa mà không ghi đè chỉnh sửa sau đó. Endpoint: POST `/api/prescriptions/ai-drafts` với jobId/transcriptRevision. Ký là thao tác riêng, chặn nếu toa nguồn AI thiếu trường bắt buộc. Không cần màn duyệt AI riêng trước DRAFT.

Secure AI gọi worker và source services qua mTLS, dùng key nội dung riêng để mã hóa input/result/history. Identity kiểm session/role/caller, Consultation kiểm phân công hiện hành. Không đưa user token vào worker hoặc DB.

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

Chỉ tên chính xác duy nhất từ snapshot active (hoặc tên + hàm lượng được nói rõ, bỏ khác biệt khoảng trắng) được đề xuất code tự động. Mơ hồ,
heuristic gần âm, xung đột hoặc không có thuốc tạo blockers trong proposal tự động; không tạo mã danh mục giả. Blockers này không ngăn endpoint editable lưu dòng kê bằng tên vào DRAFT để bác sĩ sửa.
Tên/code/unit của proposal lấy từ catalog. `dose→dosage`, frequency/timing giữ
nguyên. Chỉ quantity nguyên dương và duration biểu đạt rõ số ngày nguyên được
map Integer; không làm tròn, không đổi tuần/tháng hoặc suy thời gian từ số lượng.

strength/dosageForm/route/minInterval/maxDailyDose được giữ trong `unmappedFields`
cùng evidence. Không lặng lẽ bỏ dòng hoặc nhét vào sai field. Endpoint cũ yêu cầu proposal khớp đầy đủ; endpoint editable giữ các dòng có tên, trường chưa xác định để trống. Prescription kiểm mã được cung cấp, còn thuốc không mã phải được bác sĩ kiểm tra/xác nhận rõ trước khi ký. Cách dùng và các trường bắt buộc vẫn được kiểm ở bước phát hành.

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


## Sửa thời lượng và UI inline —24/10/2026

MediaRecorder WebM có thể thiếu Duration header (upstream: https://github.com/yusitnikov/fix-webm-duration). Web pin `@fix-webm-duration/fix@1.0.1` MIT, xử lý Blob ngay browser sau stop, đo elapsed bằng performance.now(); không gửi audio sang bên thứ ba. MP4 không sửa. Có cờ finalizing để không ghi mới/ghi đè Blob trong lúc hoàn tất, stop() đợi Blob sửa xong trước submit. Đồng hồ live dùng thời gian đã trôi thay số lần timer. AudioContext/Analyser chỉ đọc RMS để phản hồi micro có tiếng, không lưu mẫu mới; dừng/unmount đóng context và tracks.

Trạng thái hiển thị theo đoạn đang ghi/chưa thêm/đã xử lý, không dựa việc đã có prescriptionId để khẳng định đoạn mới đã lưu. API/retention không thay đổi. Khi lưu thành công metadata sessionStorage dưới actor/consultation thêm audioDurationSeconds hữu hạn>0≤181 (ưu tiên decoder server); không raw audio/transcript. Metadata duration có thể giữ trong cùng tab khi job TTL hết; không là lịch sử bền vững hoặc tổng nhiều đoạn. Không tự suy duration cho job cũ thiếu metadata, không tự tải lại raw audio.

Phát hiện thêm lỗi CSP: không có media-src, nên default-src/self không cho phát blob URL trên trình duyệt thử. Proxy trang thêm duy nhất `media-src self blob:`; không thêm blob vào connect-src, không mở script-src hoặc nguồn media bên ngoài. Regression kiểm header thực và phát/tua HTMLAudioElement, không chỉ kiểm audio Blob hoặc thẻ tồn tại.


## Toa đánh số và thay nội dung DRAFT đã xóa —24/10/2026

RulesExtractor tách ranh giới thuốc thứ nhất/hai/ba/tư… có “là”, giữ ngữ cảnh phủ định/tiền sử/giả định trước thuốc. Bổ sung nhận tên ngoài catalog trong vị trí chỉ định đánh số, giữ bằng chứng offset. Chuẩn hóa explicit integer days sang chuỗi “N ngày” ở field duration cho mọi thuốc để adapter editable nhận cả chữ số/chữ Việt; evidence vẫn nguyên văn, tuần/tháng/số lẻ không quy đổi. TIMING giữ lịch nối “và”; rule một lần/ngày kèm hai buổi vẫn xung đột. Câu nối “Này hai lần” chỉ cho nhận các trường rõ còn lại, không tự sửa frequency/dose.

EditableRequest thêm boolean replaceItems mặc địnhfalse. Chỉ dùng true với prescriptionId khi UI đã xóa hết dòng trong DRAFT và đang xử lý nguồn mới; thiếu target400. saveSource lấy khóa nguồn và khóa toa hiện hành như append, kiểm actor/phân công/cùng visit/patient/DRAFT, xây mới items từ nguồn trước khi update; không persist toa rỗng hoặc tạo toa khác. Bản ghi chưa có tên/nguồn lỗi rollback không xóa nội dung cũ. Retry job/revision trả toa hiện tại, không áp lại thay đổi hoặc tạo nguồn trùng. False giữ hành vi append; pre-save local edits chỉ khi còn items. Không đổi schema/retention/strict endpoint hoặc tự ký.


## Nhận diện bằng tên/hàm lượng/tần suất —24/10/2026

Tên thuốc không bị ràng buộc với từ dẫn hoặc thứ tự. medicine_mentions bổ sung tên chưa có catalog dựa trên vị trí tên trước strength và frequency/PRN sau đó; bare medicine line có thể nối frequency ở câu tiếp theo. Các field từ sau strength tới tên kế tiếp chỉ gắn với đúng thuốc. Grammar words/numerals không được lấy làm tên, dose-only/strength-only không sinh thuốc. Catalog matching giữ code policy/evidence/retention cũ, không tự chọn thuốc gần giống.

Ordinal cue chấp nhận “thứ … là” dù từ dẫn trước đó sai/mất; không hardcode thay hốt→thuốc hoặc tự chữa transcript. Không tự sửa liều/tần suất chưa rõ. History/negation/hypothetical vẫn được kiểm trước khi thêm items. Không đổi schema/API, chỉ parser của worker native local; dùng cùng model và khóa.
