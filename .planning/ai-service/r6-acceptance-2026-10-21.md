# R6 — biên bản triển khai và kiểm chứng kỹ thuật

Ngày21/10/2026. Base907cba2, nhánh `feature/dangkhoii/ai-r6`; không commit/push R6. Root `develop` giữ nguyên và sạch. Đã pull origin/main trước coding. Chỉ triển khai runtime audit local; không migrate DB remote dùng chung. Không thay key Resend, cấu hình OCR local hoặc app iPhone.

## Kết quả theo yêu cầu

| Mục | Thực hiện | Phạm vi còn mở |
|---|---|---|
| R6-01 | Chọn xử lý self-host: local evidence retrieval và ASR MLX; kho nguồn có khoa/phiên bản/hash/người rà soát | Chưa có bộ tài liệu bệnh viện, chuyên khoa ưu tiên và bộ ca được chuyên gia xác nhận |
| R6-02 | Session DOCTOR thật; kiểm doctor/consultation/patient từ owning APIs trước/sau hỏi; whitelist facts; bỏ pageData và DOM scraping | Cần đánh giá thêm chính sách dữ liệu cho triển khai bệnh viện |
| R6-03 | Web quản lý nguồn và chatbot trích nguồn APPROVED thuộc khoa; link/version/hash, thông tin thiếu; không tự ghi toa/chỉ định | Bộ tra cứu trích đoạn chưa phải mô hình suy luận; chưa nghiệm thu hiệu quả lâm sàng |
| R6-04 | Bỏ fallback y khoa; timeout/unavailable, history owner/ca, AES-GCM, TTL/cleanup; voice quota/lease/idempotency; mTLS | Load, vận hành production và restore key/DB toàn stack chưa nghiệm thu |
| R6-05 | Test tổng hợp quyền, sai liên kết, injection inert, outage, mã hóa/TTL, review concurrent, handover, retry và web | Chưa có chuyên gia rà đáp án/ca thật, chưa có test giọng bác sĩ độc lập |
| Voice | Web ghi âm → Lưu toa nháp tự dừng mic → local ASR/extraction → Prescription DRAFT → sửa cùng toa → ký riêng | Ca âm thanh tổng hợp cần sửa transcript; chưa xác nhận micro vật lý/giọng thật |

R6 đã có phần tích hợp kỹ thuật chạy được; **chưa đóng nghiệm thu toàn bộ R6 hoặc sẵn sàng điều trị thật**.

## Bằng chứng kiểm tra

Dữ liệu chi tiết đã bỏ credential/PHI trong [r6-verification-2026-10-21.json](r6-verification-2026-10-21.json).

- Java199/199: Common49, Prescription119, AI31; không lỗi, không bỏ qua. Bao gồm PostgreSQL thật cho review/TTL/history và tạo/lưu trùng/sửa/ký toa; bộ hồi quy phát hành Prescription R3 cũng được bật.
- Python12/12; wheel mới build và cài vào môi trường riêng, kiểm exact name + explicit strength, không tự bù liều.
- Web production build (webpack), Gateway build, secure Compose config, git diff whitespace: đạt.
- Gateway/mTLS thật: capability theo role; nguồn chưa duyệt unavailable; nguồn duyệt được trích nguyên văn; pageData không tạo quyền; sai bác sĩ/bệnh nhân từ chối; history phân scope; nguồn retired không trả lời mới.
- Worker local thật tạo đề xuất; Prescription tạo đúng một DRAFT khi retry, giữ dữ liệu thiếu là null và chặn ký422. Chỉnh sửa có named details không bị retry AI ghi đè.
- Browser: sửa toa/details, tải lại còn nguyên; nguồn ADMIN nhập từ Directory combobox → DOCTOR rà soát → widget trích dẫn/lưu lịch sử → ADMIN thu hồi.
- Browser voice: đầu vào là **MediaStream tổng hợp từ TTS** đi qua MediaRecorder thật và ASR local thật. Save dừng mọi track; cần sửa transcript rồi mới lưu được một DRAFT, tải lại/lưu lại không tạo trùng. Chrome headless không cấp được mic vật lý/fake-device nên không ghi nhận nghiệm thu micro thật.
- Logout thu hồi quyền đọc AI bằng token đã cấp; direct AI thiếu client certificate bị chặn; worker có mTLS nhưng sai token vẫn401. Kiểm DB live: result voice mã hóa và history không chứa marker plaintext.
- Stack Mac có16 đầu health OK. Không chạy lại/reset hàng đợi hay seed production để tạo kết quả giả.

Các request setup chỉ dùng tài khoản demo chuẩn và hồ sơ có nhãn SYNTHETIC. Không gọi email, VNPT hoặc provider AI ngoài; source URL là metadata, không fetch tùy ý. Nguồn tổng hợp thử đã retired sau kiểm tra.

## Phân tích phạm vi và giới hạn

GitNexus index20795nodes/48812edges/852flows. `detect-changes` báo150symbols,39processes, aggregate **CRITICAL**; cảnh báo đã được thông báo. Phạm vi rộng tập trung AI, DTO/mapping/ký Prescription, UI khám và đăng ký Gateway/TLS. Đây không phải chứng nhận an toàn. Symbol UNKNOWN/cross-language được kiểm tra thủ công tại caller/route/bean. File mới chưa tracked không được coi là đã phủ chỉ nhờ detect-changes; có rà soát file riêng.

Không sửa implementation Consultation/Lab/EMR/Identity/Patient hoặc native. Runtime chỉ bổ sung caller/scopes/AI discovery cần thiết, giữ secret hiện có. DBML AI và Prescription khớp migration; migration mới chỉ chạy trên DB audit đã sao lưu. V2AI thêm nguồn/history; V3Prescription thêm details/source link. Không sửa V1 đã phát hành. Content key riêng phải được sao lưu an toàn; DB voice cũ plaintext cần chuyển đổi kiểm soát trước bật encryption.

Baseline prototype lịch sử `positive_full_draft_exact=0/313` chưa được đánh giá lại; integration pass không xóa hạn chế đó. Nguồn tổng hợp không dùng quyết định chuyên môn, `allow-synthetic` bị cấm ở production. Nhận dạng gần âm/thuốc mơ hồ không tự chọn; bác sĩ sửa transcript hoặc nhập toa thủ công.

Còn chờ: tài liệu/chuyên khoa và người rà soát chuyên môn; bộ ca có đáp án do chuyên gia duyệt; test giọng độc lập trên mic thật; tải/rate limit clinical đa instance, monitoring/audit vận hành, khôi phục DB/key và reboot. Worker có hướng dẫn khởi động riêng, chưa có tự phục hồi toàn stack sau reboot. R4 push iPhone và các giới hạn R1–R5 trước đây không được đóng bởi R6.

Hướng dẫn thao tác duy nhất: [trang24](../../huong-dan-su-dung/24-tro-ly-lam-sang-r6.md) và [trang23](../../huong-dan-su-dung/23-nhap-toa-giong-noi-ai.md).
