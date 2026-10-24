# Voice — đối chiếu prototype và đóng gói Docker

Mốc23–24/10/2026, worktree `release-r7`, nhánh `feature/dangkhoii/release-r7`, nền907cba2 kèm R6/R7 chưa commit. Prototype chỉ đọc từ thư mục chung `careflow-voice-prescription`, không sửa hoặc đưa dataset/trainer/SQLite vào CareFlow. Hướng dẫn chuẩn ở [trang23](../../huong-dan-su-dung/23-nhap-toa-giong-noi-ai.md) và [trang26](../../huong-dan-su-dung/26-voice-va-docker.md).

## Nguyên nhân khác trải nghiệm

| Thành phần | Prototype | Bản tích hợp trước cập nhật | Hiện tại |
|---|---|---|---|
| Model | Active adapter đã được promote | Adapter serving cùng checksum | Giữ nguyên weights; đã đối chiếu checksum |
| ASR context | Mặc định bật vocabulary catalog | Chuỗi context rỗng | Bật vocabulary từ catalog active CareFlow,50 tên/4000 ký tự |
| Decode | Mặc định SDK, không timestamps | Ép768 token | Đồng bộ cách gọi prototype, giữ timeout150giây |
| Danh mục | Snapshot tham khảo871 hồ sơ, nhiều mục chưa xác minh |15 thuốc active từ Prescription | Giữ15 thuốc được phép kê; không coi DAV ID là mã thuốc bệnh viện |
| Bản nháp | SQLite cục bộ, người dùng xác nhận catalog | AI job→Prescription có kiểm quyền/mã thuốc | Giữ luồng lâm sàng; lỗi thuốc vẫn cần đối chiếu |
| UI | Audio/transcript và đơn giấy, editor từng dòng | Khung ghi âm nhỏ, lỗi chung | Cửa sổ hai cột, audio nghe lại, transcript, đơn giấy, bằng chứng và mở editor hiện có |
| Thời gian | ASR và extraction có số đo riêng | `processingMs` chỉ parser | Thêm timing worker, giữ tổng API/lưu toa trong phép thử riêng |

Đây là các khác biệt cấu hình/contract đã xác nhận; không có bằng chứng cho kết luận adapter khác hoặc Docker làm model nhanh hơn. Loratadin vẫn ngoài danh mục CareFlow nên không được tự chọn/lưu như sản phẩm được phép kê.

## Thực hiện

- Worker giữ model/thread thường trú. Chuẩn hóa WAV như prototype, truyền vocabulary chỉ từ catalog được service cấp, không lấy đáp án mẫu, ngữ cảnh bệnh nhân hoặc metadata train làm prompt. Kiểm catalog trước khi chiếm thời gian ASR. Có timing decode/ASR/extraction/total thực đo.
- Cửa sổ voice mở khi ghi hoặc tải audio, có nghe lại và phần transcript; paper preview ghi STT/tên/số lượng/đơn vị/cách dùng. Trường thiếu để trống, mã không rõ vẫn bị chặn trước lưu và ở server. Đã lưu nháp thì nút sửa mở đúng editor dùng chung; không tạo toa thứ hai hoặc tự ký.
- Browser chỉ lưu cặp jobId/prescriptionId theo actor/consultation trong session metadata. Refresh chỉ đọc lại job đúng ca/toa, còn hạn và còn quyền qua API. Không lưu raw audio/transcript/token vào storage; không áp lại kết quả gốc lên thay đổi của bác sĩ. Hết TTL thì giữ toa, không phục hồi transcript vô hạn.
- Dockerfile runtime pin base digest, đóng gói đúng JAR đã kiểm, ignore context chỉ nhận JAR. Container non-root/read-only/cap-drop,2CPU/768MiB/heap384MiB, tmpfs và health mTLS. Config/TLS mount read-only ngoài Git; không CA private key hoặc khóa service khác trong bundle. GPU worker vẫn native Mac; Linux container này không chạy MLX Metal. [Docker GPU passthrough](https://docs.docker.com/desktop/features/gpu/) và [kết nối host](https://docs.docker.com/desktop/features/networking/networking-how-tos/) là cơ sở lựa chọn kiến trúc.
- Preflight trên18193 trước chuyển traffic. JAR trong image khớp hash native. Sau chuyển container phục vụ18093, Gateway URI giữ nguyên; native fallback artifact được giữ. Không đổi schema/secret/model. Launcher chỉ khởi động container có owner/image ID/cổng đúng descriptor; snapshot lưu descriptor, bundle private lưu bền vững ngoài `/tmp`.

## Kiểm chứng và số đo

[Bằng chứng máy đọc](voice-parity-docker-2026-10-24.json) ghi hash source/artifact và kết quả, không kèm raw transcript/audio/credential.

- Worker **32/32** đạt, gồm29 hồi quy trước và3 ca context chỉ active catalog, từ chối catalog sai trước model, decode FFmpeg thật/truyền tham số SDK. Model của test đơn vị được thay bằng stub ở ca codec; không gọi đây là đánh giá ASR.
- **4 kiểm tra** helper config/ownership đạt: giữ nguồn config/key/origin, map DNS/canonical `/private/tmp`, từ chối DB shared/HTTP, không khởi động image/owner/public binding khác. Compose config/build và health mTLS thật đạt.
- TypeScript, ESLint file liên quan và production webpack build đạt. Browser thật tải WAV→MLX local qua Docker API→một DRAFT; sửa qua editor, lưu cùng mã, refresh lấy lại transcript hợp lệ, sửa sau refresh không bị AI ghi đè. Đã kiểm layout cửa sổ desktop/390px. MediaStream tổng hợp kiểm Escape/rời trang/đăng xuất dừng track; chưa dùng mic vật lý.
- So trực tiếp cùng model resident trên hai clip tổng hợp thuộc corpus train, hai lượt warm: không context768/prototype-context/hospital-context. CER trung bình trên hai clip tương đương; context có overhead, không chứng minh tăng tốc hoặc chất lượng người thật. Đây là smoke cấu hình, không tập đánh giá độc lập hoặc thí nghiệm chất lượng đơn thuốc.
- Đo native API và Docker API với cùng worker/audio8giây, hai lượt/mỗi loại và polling800ms. Phân biệt model dưới1giây với tổng API khoảng1–3giây; số cụ thể ở JSON. Có cold connection/ảnh hưởng tải máy, chưa benchmark p95/load hoặc chứng minh Docker nhanh hơn. Luồng browser còn có cập nhật ca và lưu toa.
- Đã kiểm launcher start/status nhận AI Docker trong bộ14 backend; worker readiness riêng đạt. Ca xác minh của bác sĩ C có toa thử hủy và ca/phiếu hoàn tất qua API, không SQL ép trạng thái. Ca chính dành người dùng được đọc lại và giữ riêng.

## Sự cố/giới hạn

Preflight đầu phát hiện canonical đường dẫn macOS `/private/tmp` bị thay thiếu prefix trong config TLS; sửa helper và thêm kiểm tra path rồi mới chuyển traffic. Không tắt TLS verification. Lần browser đầu hoàn tất audio/sửa/reload/layout nhưng logout ở390px bị action bar của shell Hospital Web có sẵn che; khôi phục viewport desktop và kiểm lại. Chỉ nghiệm thu responsive cho cửa sổ voice, chưa cho toàn shell bệnh viện trên mobile.

Chưa benchmark giọng bác sĩ độc lập/đơn thuốc lâm sàng, GPU/load đa người, bộ Docker trên máy khác, restart giữa inference hoặc DR production. Container health không tự restart GPU worker. Danh mục mới/model mới phải được kiểm riêng, không nhập tự động hồ sơ DAV chưa xác minh. Java JAR không đổi nên không chạy lại reactor1.000 test; đó là chứng cứ nền trước bản cập nhật.

## GitNexus và phát hành

Bind rõ worktree `release-r7`, force rebuild graph khi tra tên ngắn trả symbol không đúng file, dùng UID/file chính xác trước sửa. Runtime `_transcribe/transcribe`, Engine.run và React panel/page: UNKNOWN ở đường gọi qua executor/HTTP/JSX; xác minh source chain HTTP→Engine→runtime và page→panel→saveDraft/editor, cộng browser/API thật. Không coi zero là unused. `infer/main/start/status`: LOW; `snapshot`: LOW,4symbol upstream/2caller; VoiceResult ảnh hưởng panel/preview/page. Kết quả toàn checkout gồm cả R6/R7 mang theo có trong JSON; aggregate CRITICAL không được hạ vì test đạt. File mới/untracked được rà riêng.

Chưa stage/commit/push. Full acceptance R7 vẫn OPEN; không ghi đè bằng chứng candidate21/10 hoặc bản sửa23/10 thành kết quả của source mới. Bản này phục vụ thử nghiệm trên Mac, chưa phải chứng nhận sẵn sàng vận hành bệnh viện.
