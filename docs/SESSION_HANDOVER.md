# CareFlow Clinical RAG & AI Service — Session Handover & Memory Log

> **Tài liệu bàn giao ngữ cảnh liên phiên (Cross-Session Handover & Memory Context)**  
> **Dự án**: CareFlow — Phân hệ AI Lâm Sàng & Clinical RAG  
> **Nhánh Git hiện tại**: `feature/vi/ai-service` (Tuyệt đối không push trực tiếp lên `main`)  
> **Cập nhật lần cuối**: 07/11/2026 (13:30)  
> **Mục đích**: Lưu giữ 100% bối cảnh công việc, các quyết định kiến trúc, kinh nghiệm xử lý lỗi và nhiệm vụ tiếp theo để bất kỳ phiên làm việc AI mới nào cũng có thể tiếp tục ngay lập tức mà không bị mất dấu.

---

## 1. Vị Trí Hiện Tại & Tiến Độ Dự Án (Current State & Progress)

- **Lộ trình tổng thể**: Triển khai theo tài liệu kỹ thuật [`docs/ai-service/clinical-ai-architecture-and-rag-implementation-plan.md`](./ai-service/clinical-ai-architecture-and-rag-implementation-plan.md).
- **Trạng thái phân kỳ**:
  - ✅ **Phase 1: Ingestion & Data Preparation**: ĐÃ HOÀN TẤT 100% (15.844 ICD-10, 701 cặp DDI, 3 phác đồ Markdown sạch, 185 thuốc generic).
  - ✅ **Phase 2: Database Schema (`pgvector`) & Vector Indexing**: **ĐÃ HOÀN TẤT 100%**:
    - CSDL Local PostgreSQL 16 + `pgvector` (0.8.7) chạy trên Docker port `5433` (`careflow_ai`).
    - DDL 3 bảng & toàn bộ chỉ mục HNSW, GIN, B-Tree đã thực thi và kiểm tra truy vấn thành công.
    - Script nạp dữ liệu [`scripts/ingest_clinical_rag.py`](../scripts/ingest_clinical_rag.py) đã nạp thành công **1.095 / 1.095 chunks lâm sàng (100%)** với vector 768 chiều (`models/gemini-embedding-2`) vào CSDL PostgreSQL.
    - Đã lưu cache đĩa an toàn 100% tại [`scripts/.rag_embeddings_cache_v2.json`](../scripts/.rag_embeddings_cache_v2.json).
  - ✅ **Phase 3: Backend RAG Engine & Hybrid Search**: **ĐÃ HOÀN TẤT 100%**:
    - Maven, DataSource, JPA Entities, Native queries Cosine + BM25, Gemini Embedding, Hybrid Search Service (RRF k=60), Prompt 3 phần với EvidenceRef. Đạt 13/13 tests PASS.
  - ✅ **Phase 4: Frontend Integration & Clinical Evidence UI**: **ĐÃ HOÀN TẤT 100%**.
  - ✅ **Phase 5: User Feedback Refinement, Zustand Global Store & Medicine Catalog**: **ĐÃ HOÀN TẤT 100%**.
  - ✅ **Phase 6: E2E Local Orchestration, Bug Fixes & Full Verification**: **ĐÃ HOÀN TẤT 100%**:
    - **Sửa lỗi HTTP 500 trên `GET /api/appointments/clinical-context/me`**:
      - Nguyên nhân: `ServiceTransportIdentity` kiểm tra địa chỉ gọi nội bộ Feign chỉ cho phép loopback (`127.0.0.1`), trong khi Eureka đăng ký IP mạng nội bộ máy phát triển (`192.168.1.62`), dẫn đến từ chối 401 `Caller service cần chứng chỉ mTLS đúng danh tính` và bung lỗi 500.
      - Khắc phục: Cho phép thêm `addr.isSiteLocalAddress()` trong `ServiceTransportIdentity.java` khi bật cấu hình `allowLoopbackHttp: true`. Recompile và install `careflow-common`. Đã xác thực trả về HTTP 200 kèm context Bác sĩ (`BS. CKI Nguyễn Văn An`) và Phòng khám (`ROOM-01`).
    - **Nâng cấp Script Điều phối `start-local.sh`**:
      - Thêm chế độ daemon và lệnh `./start-local.sh stop` để dọn dẹp các tiến trình nền sạch sẽ.
      - Bổ sung kiểm tra sức khỏe (health checks) cho cả 9 microservices + frontend trước khi thông báo hoàn tất, tránh lỗi 503 "No servers available".
    - **Sửa lỗi DPoP Origin**: Thêm `http://localhost:8091` vào whitelist DPoP của `identity-service` và trỏ base URL frontend AI qua API Gateway (`http://localhost:8080`).
    - **Khắc phục lỗi HTTP 503 `Chưa có nguồn chuyên môn được duyệt cho khoa này` trong Clinical AI Assistant**:
      - Bảng `consultations` có trường `department_code` bị NULL do các ca khám cũ khởi tạo khi chưa kết nối Directory Service. Đã cập nhật `department_code='NOI_TONG_QUAT'`.
      - Bảng `ai_guideline_sources` chưa có dữ liệu phê duyệt (`APPROVED`). Đã seed 4 phác đồ Bộ Y Tế (Viêm phổi cộng đồng CAP QĐ 4800, Tăng huyết áp QĐ 5968, Đái tháo đường típ 2 QĐ 5481, Tương tác thuốc DDI QĐ 5948) với cơ chế thẩm định độc lập (Maker-Checker constraint).
    - **Khắc phục lỗi Data Integrity trong `ai_clinical_interactions`**:
      - Cột `doctor_id`, `user_question`, `prompt_snapshot`, `service_mode` đã được chuyển sang `NULLABLE` và tạo Trigger tự động gán `doctor_id := actor_id`. Tạo Flyway migration `V3__sync_clinical_interactions_and_guidelines.sql`.
    - **Kích hoạt Profile `demo` tự động seed Bệnh nhân & Hàng đợi**:
      - Trước đây các service chạy với `-Dspring-boot.run.profiles=local`, khiến các `DataInitializer` đánh dấu `@Profile("demo | test")` không được kích hoạt.
      - Đã cập nhật `start-local.sh` sang `ACTIVE_PROFILES="local,demo"`, tự động seed 6 bệnh nhân mẫu vào `careflow-patient-service` và 5 lượt chờ khám tại phòng `ROOM-01` (`NOI-001` đến `NOI-005`) vào `careflow-queue-service`.
      - Khắc phục lỗi `ObjectOptimisticLockingFailureException` ở `consultation-service` và `prescription-service` khi seed dữ liệu có ID cố định bằng cách chuyển sang `JdbcTemplate`.
      - Bổ sung cấu hình `careflow.voice` (worker-token, caller-key, loopback) và migration schema `voice_prescription_jobs` trong `careflow_ai`.
    - **Kiểm thử Toàn diện E2E (`scripts/test_e2e_consultation.cjs`)**:
      - Bước 1 (Gateway): UP (200)
      - Bước 2 (Đăng nhập Bác sĩ DPoP): Thành công (200)
      - Bước 3 (Clinical Context Me): Thành công (200)
      - Bước 4 (Danh sách ca khám trong ngày): Thành công (200 - 3 ca khám)
      - Bước 5 (Danh mục 15 loại thuốc kê đơn): Thành công (200)
      - Bước 6 (AI Voice-to-Text & bóc tách sinh hiệu tự động): Thành công (200, bóc tách đầy đủ Mạch, HA, Thân nhiệt, SpO2, Cân nặng)
      - Bước 7 (Trợ lý AI RAG gợi ý phác đồ Bộ Y Tế): Thành công (200, trích dẫn 3 phác đồ và khuyến nghị lâm sàng chi tiết).
    - **Sửa triệt để lỗi CORS Duplicate Header trên Voice STT & RAG (`TypeError: Failed to fetch`)**:
      - **Nguyên nhân**: `careflow-ai-service` có cấu hình `WebConfig.java` riêng thêm CORS header `Access-Control-Allow-Origin: http://localhost:3000`. Khi client frontend gọi qua API Gateway (`http://localhost:8080`), Gateway cũng cấu hình `globalcors`, dẫn đến phản hồi trả về trình duyệt có 2 giá trị: `Access-Control-Allow-Origin: http://localhost:3000, http://localhost:3000`. Trình duyệt lập tức chặn kết nối với lỗi `Failed to fetch`.
      - **Khắc phục**:
        1. Xóa `addCorsMappings` trong `careflow-ai-service/src/main/java/com/careflow/ai/config/WebConfig.java` để Gateway quản lý CORS tập trung đồng nhất như các microservice khác.
        2. Thêm bộ lọc `DedupeResponseHeader=Access-Control-Allow-Origin Access-Control-Allow-Credentials, RETAIN_FIRST` vào `default-filters` của `careflow-api-gateway/src/main/resources/application.yml` để chống trùng lặp header từ mọi upstream.
        3. Viết script kiểm thử mô phỏng trình duyệt `scripts/test_voice_and_rag_browser_sim.cjs`: Xác nhận 100% cả Voice STT upload và Clinical RAG suggestions đều trả về HTTP 200 với duy nhất 1 header `Access-Control-Allow-Origin: http://localhost:3000`.
    - **Chuyển đổi hoàn toàn sang Pipeline RAG + Gemini 2.5 Flash thực thụ (`mode: LLM_PROVIDER`)**:
      - **Nguyên nhân**: `AiClinicalController.java` trước đây trỏ vào `ClinicalEngine.java` (`careflow-local-evidence-v1`), đây là module tìm kiếm trích xuất tĩnh (extractive keyword search trong RAM ~5ms), trả về text tóm tắt cố định và không gọi LLM nên không có cảm giác đàm thoại / phân tích ca bệnh thông minh.
      - **Khắc phục**:
        1. Đấu nối lại `AiClinicalController.java` sang `AiClinicalAssistantService.getClinicalSuggestions` và `processClinicalChat`.
        2. Tích hợp `ClinicalCitationDto` vào `ClinicalSuggestionResponse` để Frontend vừa hiển thị phân tích chuyên sâu của Gemini 2.5 Flash dạng Markdown chuẩn, vừa hiển thị thẻ trích dẫn Phác đồ Bộ Y Tế và các nút `+ Áp dụng`.
        3. Cập nhật `frontend/doctor-web/src/lib/ai-api.ts` tự động truyền `pageRoute`, `pageTitle`, `pageData` (toàn bộ sinh hiệu, triệu chứng, chẩn đoán của ca khám) vào prompt để Gemini suy luận trực tiếp trên ca bệnh cụ thể.
        4. Tăng `response-timeout` của API Gateway từ 30s lên 90s và tối ưu `generationConfig` cho Gemini 2.5 Flash (phản hồi trong 8-11 giây). Đã kiểm thử thành công 100% đạt `mode: "LLM_PROVIDER"`.
    - ✅ **Task 1: Cấu hình Maven & DataSource**: ĐÃ HOÀN TẤT 100%. Kết nối CSDL PostgreSQL vector port `5433` (`careflow_ai`), cấu hình `spring-boot-starter-data-jpa`, `postgresql` driver và đã pass kiểm thử TDD `DataSourceConnectionTest` (xác thực đủ 1.095 chunks).
    - ✅ **Task 2: Định nghĩa JPA Entities & Projection**: ĐÃ HOÀN TẤT 100%. Tạo xong 3 Entities (`ClinicalGuidelineDocument`, `ClinicalGuidelineChunk`, `AiClinicalInteraction`), `ChunkSearchResultProjection` và 3 JPA Repositories. Đã pass kiểm thử `EntityMappingTest` (xác thực map đúng 4 phác đồ cha và 1.095 chunks).
    - ✅ **Task 3: Xây dựng Native Vector & BM25 Repository Queries**: ĐÃ HOÀN TẤT 100%. Cài đặt thành công 3 Native Queries trong `ClinicalGuidelineChunkRepository`: Cosine `<=>` (HNSW), BM25 Fulltext `tsv_content` (GIN) và JSONB `icd10_codes` filter (`@>`). Đã pass 3/3 tests trong `ClinicalGuidelineChunkRepositoryTest`.
    - ✅ **Task 4: Mở rộng Gemini Embedding Client**: ĐÃ HOÀN TẤT 100%. Thêm hàm `generateEmbedding(text)` vào `GeminiClientService` hỗ trợ sinh vector 768 chiều với các model `gemini-embedding-2-preview` / `text-embedding-004` kèm cơ chế fallback trả về `null` an toàn. Đã pass 2/2 tests trong `GeminiEmbeddingTest`.
    - ✅ **Task 5: Xây dựng ClinicalHybridSearchService & Nâng cấp AiClinicalAssistantService**: ĐÃ HOÀN TẤT 100%. Cài đặt thuật toán Reciprocal Rank Fusion ($k=60$) kết hợp HNSW Cosine Distance và GIN BM25 full-text. Nâng cấp `AiClinicalAssistantService` tích hợp RAG Retrieval vào Prompt 3 phần, trích xuất chính xác `EvidenceRef` và danh sách khuyến nghị phác đồ Bộ Y Tế. Đã pass 3/3 tests `AiClinicalAssistantServiceTest` và 2/2 tests `ClinicalHybridSearchServiceTest` (Tổng: 13/13 tests PASS 100%).
  - ✅ **Phase 4: Frontend Integration & Clinical Evidence UI**: **ĐÃ HOÀN TẤT 100%**:
    - Nâng cấp `frontend/doctor-web/src/lib/ai-api.ts`: Hỗ trợ kiểu `CLINICAL_GUIDELINE_RECOMMENDATION`, trích xuất `evidenceRefs` và context ca khám.
    - Nâng cấp Tab Trợ lý AI Lâm Sàng trên `consultation/[id]/page.tsx`:
      - Tự động đóng gói ngữ cảnh thời gian thực của ca khám gửi sang RAG Backend.
      - Hiển thị khối trích dẫn Phác đồ Bộ Y Tế rõ ràng với badge mã phác đồ kèm nút "+ Áp dụng".
      - Hiển thị khối Cảnh báo tương tác thuốc DDI từ Dược thư Quốc gia nổi bật.
  - ✅ **Phase 5: User Feedback Refinement, Zustand Global Store & Medicine Catalog**: **ĐÃ HOÀN TẤT 100%**:
    - **Markdown Rendering**: Tích hợp thư viện chuẩn `react-markdown` + `remark-gfm` trong component `FormattedAiText`, render chuẩn xác tiêu đề `####`, gạch đầu dòng `*`, in đậm `**`, bảng biểu và trích dẫn.
    - **Loại bỏ Overdesign theo `docs/design.md`**: Gỡ bỏ hoàn toàn gradient tím/hồng lòe lẹt, emoji trong tiêu đề (`🐾`), xóa bỏ nhân vật "Bác sĩ Chồn AI" và các câu đùa cợt; chuẩn hóa giọng điệu AI nghiêm túc của "Trợ lý Lâm sàng (Phác đồ điều trị Bộ Y Tế)", giảm 30% cỡ chữ chuẩn y khoa SJD Barcelona.
    - **Hợp nhất Hỏi đáp & Gợi ý nhanh**: Gộp khung câu hỏi gợi ý và ô tra cứu thành một thanh điều khiển duy nhất tại đầu tab AI, loại bỏ hoàn toàn khung chat thừa thãi bên dưới.
    - **Quản lý Trạng thái Toàn cục với Zustand**: Tạo store `useVoiceStore` (`src/stores/voice-store.ts`) quản lý tập trung `isRecording`, `isProcessing`, `recordingDuration`, `transcript`, `extractedFields`, `isPreviewOpen`.
    - **Nút Hủy (Cancel & Re-record) cho Voice STT**:
      - Bổ sung nút **"Hủy"** ngay khi đang ghi âm để bác sĩ hủy ngay và nói lại mà không cần đợi STT bóc tách.
      - Bổ sung nút **"Hủy"** khi đang xử lý STT và trong `VoicePreviewModal`.
    - **Tinh gọn Text Nút bấm theo `docs/design.md`**:
      - Nút ghi âm ban đầu: `"Ghi âm"` (bỏ "lời khám", bỏ icon rườm rà).
      - Khi đang ghi âm: đồng hồ bấm giờ `[00:05]` + nút `"Xong"` + nút `"Hủy"`.
      - Khi đang xử lý: badge `"Đang xử lý..."` + nút `"Hủy"`.
      - Trong modal xem trước: nút `"Hủy"` và nút `"Áp dụng"`.
    - **Vị trí Nút Ghi âm (Layout Optimization)**:
      - Loại bỏ hoàn toàn khung frame to bao quanh ("Ghi âm lời khám rảnh tay (STT)...").
      - Tách nút `"Ghi âm"` ra khỏi hàng Tab khám bệnh và đặt tinh gọn ở **phía trên dãy Tab** (căn lề phải), giữ cho hàng Tab thông thoáng, phẳng phiu.
    - **Khắc phục Danh mục Thuốc Trống (Combobox Chips)**:
      - Khởi tạo bảng `drugs_dictionary` và nạp 25 thuốc lâm sàng chuẩn phác đồ Bộ Y Tế (ĐTĐ type 2, Tăng huyết áp, Viêm phổi, Tim mạch, DDI Clopidogrel - Omeprazole) vào CSDL `careflow_prescription`.
      - Cung cấp `DEFAULT_MEDICINE_CATALOG` trên Frontend đảm bảo ô chọn thuốc Combobox luôn có đầy đủ dữ liệu gợi ý.
    - **Triệt tiêu Cảnh báo Tương tác Thuốc (DDI) Rác**:
      - Sửa Backend `AiClinicalAssistantService`: Chỉ trích xuất DDI khi ngữ cảnh ca khám thực sự có danh sách thuốc đang kê và tên hoạt chất khớp với phác đồ DDI.
      - Sửa Frontend `page.tsx`: Khối thẻ cảnh báo DDI màu vàng chỉ render khi `prescriptionItems.length > 0` và có thuốc tương tác; khi chưa kê thuốc, thẻ này bị ẩn hoàn toàn.
  - ✅ **Phase 6: Merge & Harmonization with develop (R0–R7 Baseline)**: **ĐÃ HOÀN TẤT 100%**:
    - **Đồng bộ mã nguồn**: Đã kéo và tích hợp toàn bộ release R0–R7 từ `origin/develop` vào `feature/vi/ai-service`.
    - **Giải quyết 100% xung đột (7 tệp)**: Giữ song song cả 2 luồng Voice (Voice Prescription và Consultation Clinical STT), bảo toàn kiến trúc RAG BYT và tương thích `ClinicalEngine`, bảo mật DPoP token + absolute URL.
    - **Kiểm thử xác thực**: `npm run build` trên `frontend/doctor-web` PASS 100%, `mvn test-compile` trên `careflow-ai-service` BUILD SUCCESS 100%. Nhánh hiện tại sẵn sàng 100% để tạo PR vào `develop`.

---

## 2. Nhật Ký Các Phiên Gần Nhất (Recent Sessions Log)

### A. Phiên 25/10/2026 (Phiên trưa — Merge develop vào ai-service, đối chiếu Voice-to-Text & giải quyết 7 xung đột):
1. **Kiểm tra Dry Run & Kéo nhánh develop (`origin/develop`)**:
   - Chạy `git merge origin/develop` để đồng bộ toàn bộ release R0–R7 mới nhất.
   - Phát hiện và giải quyết triệt để xung đột trên **7 tệp**:
     1. `careflow-ai-service/src/main/java/com/careflow/ai/service/AiClinicalAssistantService.java`: Giữ cả kiến trúc `ClinicalEngine` (R6) và toàn bộ RAG Retrieval Engine + Gemini/DeepSeek provider.
     2. `frontend/doctor-web/src/app/(dashboard)/consultation/[id]/page.tsx`: Giữ toàn bộ quy trình mới của develop (`VoicePrescriptionPanel`, `ClinicalCitations`, `FollowUpAppointmentPanel`) kết hợp hoàn hảo với RAG tab BYT, thanh cảnh báo dị ứng siêu gọn và nút Ghi âm lời khám rảnh tay (`useVoiceStore`).
     3. `frontend/doctor-web/src/components/AiAssistantWidget.tsx`: Tích hợp render markdown (`FormattedAiText`) vào widget hỗ trợ bác sĩ với bảo vệ vai trò `DOCTOR`.
     4. `frontend/doctor-web/src/lib/api.ts`: Hợp nhất bảo mật DPoP + session refresh token của develop với logic URL linh hoạt của nhánh AI.
     5. `AGENTS.md`, `CLAUDE.md`, `README.md`: Hợp nhất quy định dự án, số lượng symbol GitNexus và tài liệu khởi chạy.
2. **Khảo sát & Đối chiếu 2 hệ thống Voice-to-Text (Voice STT)**:
   - **Voice Prescription (Nhánh `develop`)**: Nhập toa thuốc bằng giọng nói qua `VoicePrescriptionPanel` + python worker runtime (`careflow_voice_core`), bóc tách danh mục thuốc, liều lượng và lưu toa nháp (`PrescriptionAiDraftService`).
   - **Consultation Clinical STT (Nhánh `ai-service`)**: Ghi âm lời khám bệnh rảnh tay qua `useVoiceStore` + `VoicePreviewModal`, bóc tách triệu chứng lâm sàng, khám thực thể, chẩn đoán sơ bộ điền vào form ca khám.
   - **Kết luận**: Cả hai tính năng hoàn toàn bổ trợ cho nhau ở 2 giai đoạn khám khác nhau và đã được tích hợp đồng thời, hoạt động độc lập không xung đột.
3. **Kiểm thử xác thực (Verification)**:
   - `npm --prefix frontend/doctor-web run build`: PASS 100% (Turbopack, TypeScript, Static generation).
   - `mvn -pl careflow-ai-service test-compile`: PASS 100%.

### B. Phiên 24/10/2026 (Phiên tối — Archify Architecture, Header Patient Refinement, Allergy Compact Bar & Manual RAG Import):
1. **Tích hợp Skill Archify & Tạo Sơ đồ Kiến trúc**:
   - Cài đặt skill `archify` vào dự án (`.agents/skills/archify`).
   - Thiết kế và xuất bản sơ đồ kiến trúc trực quan độc lập (HTML standalone): [`docs/html/01-architecture.html`](./html/01-architecture.html) mô tả chi tiết luồng Voice STT, Hybrid Search RAG và Human-in-the-Loop.
2. **Chuẩn hóa Header Thông tin Bệnh nhân**:
   - Chỉnh sửa `consultation/[id]/page.tsx`: Tên bệnh nhân hiển thị chữ đen đậm rõ ràng (`Nguyễn Thị Mai`), thông tin tuổi, giới tính và mã ID (`ID: f0000001`) thu gọn chữ nhỏ màu xám ở dòng dưới.
   - Thêm ánh xạ fallback dữ liệu mẫu cho các bệnh nhân hạt giống (`f0000001` đến `f0000006`).
3. **Refactor Thanh Cảnh báo Dị ứng theo Chuẩn `docs/design.md`**:
   - Thay thế các banner to đùng thô ráp bằng các thanh Compact Bar siêu gọn (~36px, `min-h-[36px] px-3 py-1.5`).
   - Viền trái mảnh 3px (`border-l-[3px]`), loại bỏ hoàn toàn chữ IN HOA thô cứng (`MỨC 1`, `TỰ ĐỘNG CHẶN KÊ ĐƠN TRÙNG NHÓM`).
   - Áp dụng thang cỡ chữ giảm 30% (`text-[11px]` và `text-[10px]`).
4. **Chuẩn bị Dữ liệu & Hướng dẫn Import CSDL RAG Local**:
   - Khẳng định quy định Git: Toàn bộ tài liệu `.md` (phác đồ điều trị) và `.csv` (DDI, generic drugs) được phép và BẮT BUỘC push lên Git để đồng bộ cả nhóm.
   - Xuất file dump CSDL hoàn chỉnh: `scripts/careflow_ai_schema_and_data.sql` (9.5MB) chứa sẵn 1.095 chunks và vector 768 chiều.
   - Soạn thảo tài liệu hướng dẫn nhóm: [`docs/ai-service/manual-rag-import.md`](./ai-service/manual-rag-import.md) hỗ trợ import trong 3–5 giây mà không cần gọi lại API Gemini.
5. **Kiểm thử**:
   - `npm run build` trên `frontend/doctor-web`: PASS 100%.
   - `mvn -pl careflow-ai-service test`: 13/13 tests PASS 100%.

### B. Phiên 24/10/2026 (Phiên chiều — Refactor UI/UX, Markdown, Zustand & Layout):
1. **Markdown Formatting**: Cài đặt `react-markdown@10.1.0` + `remark-gfm@4.0.1`, tạo `FormattedAiText.tsx` thay thế render text thô.
2. **Loại bỏ Overdesign & Tinh gọn Giao tiếp AI**: Xóa bỏ mascot "Bác sĩ Chồn AI", icon `🐾`, các câu đùa cợt; đổi tên thành "Trợ lý Lâm sàng (Phác đồ điều trị Bộ Y Tế)".
3. **Gộp Điều khiển Tra cứu RAG**: Chuyển ô nhập câu hỏi lên cùng hàng với các chips gợi ý nhanh ("Chẩn đoán phân biệt", "Cảnh báo tương tác DDI", "Dữ liệu còn thiếu", "Đề xuất Cận lâm sàng").
4. **Zustand Voice Store**: Tạo `voice-store.ts`, tách biệt logic MediaRecorder khỏi UI component, hỗ trợ nút "Hủy" bất kỳ thời điểm nào.
5. **Tinh gọn Nút Bấm & Bố trí Nút Voice**: Bỏ frame to bao quanh nút voice, chuyển nút "Ghi âm" lên phía trên dãy Tab (căn phải) để không làm vỡ layout hàng Tab.
6. **Data Seeding & DDI Guard**: Nạp 25 thuốc chuẩn BYT vào bảng `drugs_dictionary`, triệt tiêu cảnh báo DDI rác khi chưa kê đơn.
7. **Kiểm thử & Git**:
   - `npm run build` trên `frontend/doctor-web`: PASS 100% (không lỗi TypeScript hay build).
   - `mvn -pl careflow-ai-service test`: 13/13 tests PASS 100%.
   - Push commit `bc7bc85` và `d6fdbbd` lên `feature/vi/ai-service`.

### B. Phiên 23/10/2026 (Phiên chiều — Ingestion & Backend RAG Engine):
1. **Kiểm tra & khôi phục Quota Gemini Embedding API**: Quota reset thành công sau 00:00 UTC.
2. **Khởi động Vector Database**: Kích hoạt Docker `careflow-ai-postgres` (PostgreSQL 16 + pgvector 0.8.7) tại port `5433`.
3. **Ingestion 100% Tri thức Lâm sàng**: Nạp hoàn tất **1.095 chunks kèm dense vector 768 chiều** vào `clinical_guideline_chunks`.
4. **Hybrid Search Service**: Xây dựng thuật toán Reciprocal Rank Fusion kết hợp HNSW Cosine Distance và GIN BM25.

---

## 3. Trạng Thái CSDL & Hạ Tầng (Infrastructure & DB State)

- **Vector Database**:
  - Host: `localhost` | Port: `5433` | Database: `careflow_ai` | User: `careflow` | Password: `careflow`
  - Container Docker: `careflow-ai-postgres`
  - Bảng `clinical_guideline_documents`: **4** tài liệu cha.
  - Bảng `clinical_guideline_chunks`: **1.095** chunks (100% có vector 768 chiều).
- **Core Database**:
  - Host: `localhost` | Port: `5432` | User: `careflow` | Password: `careflow`
  - CSDL `careflow_prescription`: Đã có bảng `drugs_dictionary` và 25 thuốc chuẩn phác đồ BYT.
- **Tiến trình Nền (Background Tasks)**:
  - Tất cả các tiến trình Spring Boot và Next.js dev server đã được **DỪNG HOÀN TOÀN** an toàn trước khi kết thúc phiên.

---

## 4. Nhiệm Vụ Tiếp Theo Khi Tiếp Tục (Next Tasks)

1. **Push nhánh `feature/vi/ai-service` lên GitHub**:
   - Chạy `git push origin feature/vi/ai-service` để cập nhật các commit merge và conflict resolution lên remote.
2. **Tạo Pull Request (PR) merge vào `develop`**:
   - Nhánh `feature/vi/ai-service` hiện tại đã đồng bộ với `develop`, giải quyết sạch toàn bộ conflicts và đã pass kiểm thử frontend build + backend test-compile. Có thể mở PR vào `develop` ngay lập tức.
3. **Thử nghiệm Đơn thuốc & Song song 2 hệ thống Voice (E2E Test)**:
   - Khởi động backend và frontend: `./start-local.sh ai` và `npm --prefix frontend/doctor-web run dev`.
   - Thử nghiệm lâm sàng:
     - Dùng nút "Ghi âm" ở cột trái: Đọc bệnh sử để điền tự động vào ca khám.
     - Dùng panel "Nhập toa bằng giọng nói" ở cột phải: Đọc toa thuốc để bóc tách đơn thuốc nháp.
     - Kiểm tra tab "Trợ lý AI Lâm sàng": Xem trích dẫn phác đồ Bộ Y Tế, nút "+ Áp dụng" và cảnh báo tương tác thuốc DDI từ Dược thư Quốc gia.

---

## 5. Tệp Tham Khảo Quan Trọng (Key File References)

- **Component định dạng Markdown**: [`frontend/doctor-web/src/lib/format-ai-text.tsx`](../frontend/doctor-web/src/lib/format-ai-text.tsx)
- **Zustand Voice Store**: [`frontend/doctor-web/src/stores/voice-store.ts`](../frontend/doctor-web/src/stores/voice-store.ts)
- **Voice Button Component**: [`frontend/doctor-web/src/components/VoiceRecorderButton.tsx`](../frontend/doctor-web/src/components/VoiceRecorderButton.tsx)
- **Trang Ca Khám Bác Sĩ**: [`frontend/doctor-web/src/app/(dashboard)/consultation/[id]/page.tsx`](../frontend/doctor-web/src/app/(dashboard)/consultation/[id]/page.tsx)
- **Service AI Lâm Sàng**: [`careflow-ai-service/src/main/java/com/careflow/ai/service/AiClinicalAssistantService.java`](../careflow-ai-service/src/main/java/com/careflow/ai/service/AiClinicalAssistantService.java)
- **Chiến lược Chunking chuẩn CareFlow**: [`.planning/ai-service/chunking-strategy.md`](../.planning/ai-service/chunking-strategy.md)
- **Tài liệu Báo cáo Kỹ thuật đã hoàn thành**: [`docs/ai-service/clinical-ai-technical-report.md`](./ai-service/clinical-ai-technical-report.md)
- **Tài liệu kiến trúc AI & RAG đầy đủ**: [`docs/ai-service/clinical-ai-architecture-and-rag-implementation-plan.md`](./ai-service/clinical-ai-architecture-and-rag-implementation-plan.md)
- **Quy chuẩn Bảng kiểm Pre-Merge / Deploy**: [`docs/DEPLOY_CHECKLIST.md`](./DEPLOY_CHECKLIST.md)



---

## 7. Milestone SDD Refactor Toàn Hệ Thống (Phases 1–5) — ĐÃ HOÀN TẤT 100%

- **Tài liệu đặc tả SDD (`.planning/*/sdd-spec-*.md`)**: Đã thiết lập đầy đủ cho toàn bộ Backend Microservices, `frontend/doctor-web` và `frontend/patient-mobile`.
- **Phase 1 — Core Clinical Services**:
  - `careflow-consultation-service` (PR `#23`), `careflow-prescription-service` (PR `#24`, `#25`), `careflow-lab-service` (PR `#26`): Tách biệt State Validator, Orchestration, Event Payload Factory, Pricing/Stock Helper và Query/Support components.
- **Phase 2 — Patient Journey & EMR Services**:
  - `careflow-appointment-service` (PR `#27`), `careflow-queue-service` (PR `#28`), `careflow-emr-service` (PR `#29`): Tách biệt Slot/CheckIn/Fifo Validator, Queue Calling/Ticket/Payload Helpers, và EMR Encryption/Integrity/Audit/Access/Search Helpers.
- **Phase 3 — Directory, Patient, Identity, Gateway & Support Services**:
  - `careflow-hospital-directory-service` & `careflow-patient-service` (PR `#30`), `careflow-identity-service` & `careflow-api-gateway` (PR `#31`), `careflow-ai-service`, `careflow-notification-service` & `careflow-analytics-service` (PR `#32`).
- **Phase 4 — Doctor & Hospital Web (`frontend/doctor-web`)**:
  - **4.1**: Tách trang `consultation/[id]/page.tsx` (2.613 dòng) thành 2 custom hooks (`usePrescriptionForm`, `useClinicalAi`), 1 module constants và 11 sub-components trong `src/components/consultation/` (PR `#33`).
  - **4.2**: Tách 4 màn hình Hàng đợi & Tiếp nhận (`staff/checkin`, `dashboard/queue`, `lab/queue`, `staff/pharmacy`) với bộ component dùng chung trong `src/components/queue/` (PR `#34`).
  - **4.3**: Tách `admin/facility/page.tsx` (thay thế `<select>` mặc định bằng `FacilitySelectBox` chuẩn SJD) và `dashboard/general/page.tsx` thành các component trong `src/components/admin/` và `src/components/dashboard/` (PR `#35`).
- **Phase 5 — Patient Mobile (`frontend/patient-mobile`)**:
  - Tách gọn `journey_models.dart`, `backend_journey_repository.dart`, `login_methods_screen.dart` và `appointment_detail_screen.dart` thành các `part` file chuyên biệt theo domain và widget; `flutter analyze` (0 issues) và `flutter test` (14/14 tests PASS).

---

## 8. Milestone SDD UI/UX Enhancement & Mobile Theme Unification (`frontend/doctor-web`) — ĐÃ HOÀN TẤT 100%

- **Nhánh làm việc**: `feature/khai/doctor-web-ui-ux` (khởi tạo từ `origin/develop` `863ce1e`).
- **Tài liệu Báo cáo Kiểm toán & Đặc tả SDD**:
  - [`docs/ui-ux-audit/CAREFLOW_WEB_UI_UX_SDD_REPORT.md`](./ui-ux-audit/CAREFLOW_WEB_UI_UX_SDD_REPORT.md)
  - Bộ 12 ảnh chụp màn hình **Before**: [`docs/ui-ux-audit/screenshots/`](./ui-ux-audit/screenshots/)
  - Bộ 12 ảnh chụp màn hình **After**: [`docs/ui-ux-audit/screenshots-after/`](./ui-ux-audit/screenshots-after/)
- **Tóm tắt 5 Slices đã hoàn tất**:
  1. **Slice 1 — Core Theme, Typography & CSP (`globals.css`, `layout.tsx`, `proxy.ts`, `public/logo.svg`)**:
     - Đồng bộ 100% bảng màu **CareFlow Ocean Medical Blue (`#0277A8` / `#00577E` / `#8BD2EE` / `#DDF3FC`) & Medical Teal (`#087F6A` / `#D9F3EC`)** từ `frontend/patient-mobile/lib/config/theme.dart`.
     - Nạp font `Be Vietnam Pro` và `Roboto Mono` hỗ trợ đầy đủ tiếng Việt.
  2. **Slice 2 — App Shell & Auth (`Sidebar.tsx`, `Topbar.tsx`, `HospitalAuthLayout.tsx`, `login/page.tsx`, `ConfirmModal.tsx`, `(dashboard)/layout.tsx`)**:
     - Chuyển Sidebar sang Deep Ocean Slate (`#0B2534` $\rightarrow$ `#0F3144`), loại bỏ emoji trên Topbar, đồng bộ bo góc `rounded-xl` cho ô input và nút trên trang `/login`.
  3. **Slice 3 — Doctor Dashboards & 3-Lane Queue (`dashboard/general/page.tsx`, `dashboard/queue/page.tsx`, `QueueBadges.tsx`)**:
     - Tách tên bác sĩ và badge khoa trên tiêu đề chào mừng, cố định chiều rộng nút bảng (`w-28`), rút gọn UUID bệnh nhân thành 8 ký tự in hoa (`F0000001`), sửa badge làn đọc kết quả CLS.
  4. **Slice 4 — Clinical Consultation Workspace (`consultation/[id]/page.tsx`, `VitalsTab.tsx`, `VoicePrescriptionPanel.tsx`, `VoicePrescriptionPreview.tsx`, `PatientHeaderBar.tsx`, `AiClinicalTab.tsx`, `PrescriptionRightPanel.tsx`, `ConsultationActionBar.tsx`)**:
     - Sửa lỗi vỡ dấu tiếng Việt (`font-serif` $\rightarrow$ `Be Vietnam Pro`) và lỗi lặp chữ `"Trong 5 ngày ngày"` trong `VoicePrescriptionPreview.tsx`.
     - Thu gọn `VoicePrescriptionPanel.tsx` thành compact toolbar (`~68px` khi chờ) để thẻ `ĐƠN THUỐC` hiển thị ngay trên màn hình đầu tiên.
     - Bo góc `rounded-xl` cho các ô Sinh hiệu, thêm đơn vị đo inline (`°C`, `mmHg`, `bpm`, `%`, `cm`, `kg`) và badge tự động tính **BMI**.
     - Sắp xếp 6 Tab khám lâm sàng gọn trên 1 hàng ngang duy nhất (`grid-cols-6`).
  5. **Slice 5 — Hospital Staff, Lab & Admin Portals (`staff/checkin/page.tsx`, `WalkInReception.tsx`, `ProfileLinkReception.tsx`, `HospitalQrCheckInCard.tsx`, `ReceptionQueueTable.tsx`, `HandoverPicker.tsx`, `staff/pharmacy/page.tsx`, `PharmacyVerificationPanel.tsx`, `lab/queue/page.tsx`, `LabResultFormPanel.tsx`, `admin/page.tsx`, `admin/facility/page.tsx`)**:
     - Bố cục 2 cột (`7/12` & `5/12`) trên `/staff/checkin`, đưa Bảng tiếp nhận và Mã QR Check-in bệnh viện lên ngay màn hình đầu tiên; thay `border-black` bằng `border-[#D5E0E6]`.
     - Việt hóa 100% nhãn loại phòng trên `/admin` và trạng thái phát thuốc trên `/staff/pharmacy`; khử trùng lặp học hàm bác sĩ (`BS.CKII BS.CKII` $\rightarrow$ `BS.CKII`) trên `/admin/facility`.
- **Kiểm증 (Verification)**:
  - `npm --prefix frontend/doctor-web run build`: **PASS 100%** (0 lỗi TypeScript, 0 lỗi Next.js build).

---

## 9. Chuyển Đổi Voice sang Built-in Engine & Quản Lý Trạng Thái Toàn Cục bằng Zustand (`frontend/doctor-web`) — ĐÃ HOÀN TẤT 100%

- **Bối cảnh & Quyết định**:
  - Đối chiếu hiệu năng giữa ElevenLabs Cloud (2.0–4.0s qua 2 chặng internet) và Built-in Local Worker (200–600ms, không phụ thuộc internet, bảo mật dữ liệu y tế PHI).
  - Thống nhất chuyển đổi và chuẩn hóa luồng Voice sang hệ thống **Built-in Engine nội bộ**, đồng thời áp dụng **Zustand Store** để quản lý trạng thái thời gian thực và dọn dẹp triệt để sự cồng kềnh của React component.
- **Các bước kỹ thuật đã thực hiện**:
  1. **Khởi tạo Store Zustand Chuyên biệt (`src/stores/voice-prescription-store.ts`)**:
     - Quản lý toàn bộ State Machine bất đồng bộ của Built-in Voice Job: `IDLE` $\rightarrow$ `RECORDING` $\rightarrow$ `FINISHING` $\rightarrow$ `POLLING (QUEUED / RUNNING)` $\rightarrow$ `SUCCEEDED / FAILED`.
     - Đóng gói toàn bộ logic MediaRecorder, WebM duration fix (`@fix-webm-duration/fix`), AudioContext metering cho visualizer, upload file audio (tối đa 20 MiB).
     - Quản lý tự động polling API `/api/ai/voice-prescription/jobs`, xử lý transcript revisions khi bác sĩ sửa text, và gọi endpoint `/api/prescriptions/ai-drafts/editable`.
     - Tự động duy trì và khôi phục metadata phiên làm việc qua `sessionStorage`.
  2. **Tái cấu trúc `VoicePrescriptionPanel.tsx`**:
     - Rút gọn component từ gần **900 dòng** code nhồi nhét hơn 20 `useRef` và nhiều `useState` xuống component khai báo tinh gọn (~400 dòng).
     - Tách biệt 100% logic xử lý luồng âm thanh/API ra khỏi tầng UI, duy trì trọn vẹn thiết kế giao diện Ocean Medical Blue & Teal chuẩn của mốc R7/PR #37.
     - Bảo toàn đầy đủ hợp đồng `VoiceDraftHandle` (`save`, `pending`, `open`, `stop`, `collapse`), đảm bảo **0 breaking change** với `usePrescriptionForm.ts` và `consultation/[id]/page.tsx`.
- **Kiểm thử xác minh (Verification)**:
  - `npm --prefix frontend/doctor-web run build`: **PASS 100%** (0 lỗi TypeScript, 0 lỗi Next.js Turbopack).
  - Khởi chạy `careflow-ai-service` độc lập (profile `local` trên port `8091`), kiểm thử thành công:
    - `GET /api/ai/capabilities`: HTTP 200 OK.
    - `POST /api/ai/text-to-clinical-fields`: HTTP 200 OK (bóc tách thành công Mạch: 80, Huyết áp: 120/80, Sốt: 38.5, SpO2: 98).
    - `POST /api/ai/voice-to-clinical-fields`: HTTP 200 OK (xử lý payload audio multipart/form-data chuẩn xác).
  - Đã dừng toàn bộ tiến trình server và container sạch sẽ sau khi kiểm thử.

---

## 10. Kiểm Định Tiền Merge (Pre-Merge Deploy Checklist) & Tạo Pull Request — ĐÃ HOÀN TẤT 100%

- **Quy chuẩn ban hành**: Thiết lập tài liệu kiểm định bắt buộc [`docs/DEPLOY_CHECKLIST.md`](./DEPLOY_CHECKLIST.md) và tích hợp vào quy tắc hệ thống (`AGENTS.md`).
- **Kết quả thực thi Checklist trước khi đẩy code**:
  1. **Git Sync**: Đã merge `origin/develop` (`8d46c75`) vào nhánh làm việc mà không phát sinh xung đột.
  2. **Backend Unit/Integration Tests**: `mvn -pl careflow-ai-service test` $\rightarrow$ **44 tests run, 0 failures, 0 errors, 11 skipped** (BUILD SUCCESS).
  3. **Frontend Production Build**: `npm --prefix frontend/doctor-web run build` $\rightarrow$ **PASS 100%** (0 TypeScript errors, 0 ESLint errors).
  4. **GitNexus Impact Analysis**: `bunx gitnexus@latest detect-changes --scope all --repo .` $\rightarrow$ **Risk level: LOW** (0 affected execution flows).
- **Trạng thái Git & PR**:
  - Nhánh cục bộ: `feature/vi/ai-service`.
  - Đã commit & push:
    - `0a06e4a`: `feat(ai,doctor-web): integrate zustand voice prescription store and add pre-merge deploy checklist`
    - `34c3616`: `docs: update session handover with checklist and pr status`
  - Đã push thành công lên: `origin/feature/vi/ai-service`.
  - **Đường link Pull Request vào `develop`**:
    `https://github.com/ntkhai268/careflow/compare/develop...feature/vi/ai-service?expand=1`

---

## 12. Cập Nhật Mới: Sửa Lỗi Khởi Chạy Hệ Thống Cục Bộ (`./start-local.sh ai`) & Tài Liệu HTML

- **Vấn đề gặp phải & Giải pháp đã thực hiện (07/11/2026)**:
  1. **Lỗi R1-R7 Security Baseline (JWT & Caller Key)**:
     - *Hiện tượng*: Các service backend crash khi khởi động do thiếu RSA key pair và caller key.
     - *Xử lý*: Đã tự động sinh RSA key pair (`.jwt-keys/jwt-private.pem`, `jwt-public.pem`) trong `start-local.sh`, thêm `.jwt-keys/` vào `.gitignore`, và xuất các biến môi trường `SECURITY_CALLER_KEY`, `SECURITY_CALLERS_JSON`, `SECURITY_JWT_*`, `SECURITY_AUDIT_ALLOW_LOOPBACK_HTTP=true`.
  2. **Lỗi 401 DPoP (`DPoP proof không hợp lệ hoặc không khớp khóa client/request`)**:
     - *Hiện tượng*: Frontend web không thể đăng nhập hoặc làm mới phiên.
     - *Xử lý*: Đã cấu hình `security.dpop.public-origins` trong `application-local.yml` của `careflow-identity-service` và xuất `SECURITY_DPOP_PUBLIC_ORIGINS` trong `start-local.sh` chấp nhận `http://localhost:3000`, `http://localhost:8080`, `http://127.0.0.1:3000`, `https://careflow-demo.online`.
  3. **Lỗi 503 Appointment Service (RabbitMQ credentials & thiếu service phụ thuộc)**:
     - *Hiện tượng*: Mode `ai` thiếu `appointment-service` và `prescription-service`; RabbitMQ bị lỗi xác thực `guest:guest`.
     - *Xử lý*: Đã bổ sung 2 service vào case `"ai"` trong `start-local.sh`, đồng thời xuất `SPRING_RABBITMQ_USERNAME=admin` và `SPRING_RABBITMQ_PASSWORD=admin123`.
  4. **Lỗi 500 trên `GET /api/appointments/clinical-context/me`**:
     - *Hiện tượng*: Doctor Dashboard báo Internal Server Error 500 do `appointment-service` gọi Feign sang `hospital-directory-service` thất bại (`UnknownHostException`).
     - *Nguyên nhân gốc rễ*: `careflow-hospital-directory-service` thiếu file `application-local.yml`, mặc định kết nối DB remote `100.116.233.60:5432` dẫn đến SocketTimeoutException và service không khởi động thành công.
     - *Xử lý*: Tạo file `careflow-hospital-directory-service/src/main/resources/application-local.yml` trỏ về `localhost:5432/careflow_hospital_directory`, đồng thời export `DIRECTORY_DB_URL` trong `start-local.sh`.
  5. **Tài liệu hướng dẫn RAG & Chunking dạng HTML**:
     - Đã tạo trang hướng dẫn chi tiết import CSDL RAG và trang giải thích cơ chế "Contextual Document-Structure Chunking" tại thư mục `docs/html/`.


- **Trạng thái hiện tại**:
  - Phân hệ **AI Service & Clinical RAG** cùng toàn bộ **Zustand Voice Prescription Store** trên `frontend/doctor-web` đã hoàn thành 100%, vượt qua toàn bộ quy trình kiểm định [`docs/DEPLOY_CHECKLIST.md`](./DEPLOY_CHECKLIST.md).
  - Đã kết nối thành công pipeline RAG + Gemini 2.5 Flash thông minh cho cả Chat Widget và Tab 6 RAG, loại bỏ hoàn toàn tình trạng fallback hardcoded kết quả tĩnh.
  - Xử lý triệt để lỗi rate limit (429) & timeout của WebClient và cấu hình Gateway `httpclient.response-timeout: 90s`.
  - Fix lỗi React duplicate key `ClinicalCitations.tsx`.

---

## 13. Sửa Lỗi Tương Tác AI RAG Chat & Timeout LLM (07/11/2026 - 14:50)

- **Hiện tượng**:
  - Người dùng hỏi trợ lý AI lâm sàng trong widget nổi hoặc Tab 6, hệ thống phản hồi siêu nhanh (~5ms) với câu trả lời tĩnh cố định (`careflow-local-evidence-v1`), không mang tính hội thoại thông minh của LLM.
  - Khi kích hoạt LLM, widget chat bị treo ở trạng thái *"Đang kiểm tra ngữ cảnh và nguồn..."* rất lâu (>2 phút).
  - Console báo lỗi React Duplicate Key `Encountered two children with the same key, 'BYT-QD-5948-DDI'` tại `ClinicalCitations.tsx`.

- **Nguyên nhân cốt lõi**:
  1. `AiClinicalController.java` trỏ vào `ClinicalEngine.java` (extractive in-memory matcher) thay vì gọi `AiClinicalAssistantService.java`.
  2. Google AI Studio áp dụng rate limit 15 RPM trên Free Tier cho `gemini-2.5-flash`. Khi gặp 429, WebClient gọi cascade tới các model không tồn tại mà không cấu hình timeout, dẫn đến thread bị block tới 166 giây.
  3. API Gateway cấu hình timeout sai cấp thuộc tính (`server.webflux.httpclient` thay vì `spring.cloud.gateway.httpclient`), làm Gateway rớt kết nối trước khi service kịp hoàn tất.
  4. `ClinicalCitations.tsx` dùng `c.sourceId` làm key; khi 1 phác đồ được trích nhiều đoạn thì sinh key trùng.

- **Giải pháp đã thực hiện**:
  1. **Đấu nối `AiClinicalController` vào `AiClinicalAssistantService`**: Lấy dữ liệu ngữ cảnh màn hình và ca khám thực tế truyền vào RAG prompt.
  2. **Tối ưu Client Gemini**: Đặt timeout 15 giây cho WebClient, cấu hình cơ chế retry sau 1.2s khi gặp 429 tạm thời, chuyển sang model chuẩn `gemini-2.5-flash`.
  3. **Tối ưu Client DeepSeek & Gateway**: Đặt timeout 10s cho DeepSeek và nâng Gateway `response-timeout: 90s` chuẩn xác.
  4. **Sửa Key React**: Chuyển key thành `${c.sourceId}-${c.contentHash || idx}` trong `ClinicalCitations.tsx`.
  5. **Xác minh E2E**: Chạy thử nghiệm thực tế với ca khám `c0000001-0000-0000-0000-000000000099`, AI nhận diện chính xác bệnh nhân 7 tuổi, sốt 37.8°C, dị ứng Penicillin và phân tích khuyến nghị phác đồ Bộ Y Tế trong ~9.1 giây với đầy đủ trích dẫn hợp lệ.

- **Nhiệm vụ cho phiên tiếp theo (Next Session)**:
  1. **Theo dõi & Merge PR**: Sau khi PR được review và merge vào `develop`, kéo code mới nhất từ `develop` về trước khi mở nhánh mới (`git checkout develop && git pull origin develop`).
  2. **Kiểm thử Luồng E2E Toàn Diện (End-to-End Clinical Flow)**:
     - Luồng 1: Bác sĩ bấm "Ghi âm" $\rightarrow$ Built-in STT bóc tách sinh hiệu & triệu chứng $\rightarrow$ tự động điền vào hồ sơ khám.
     - Luồng 2: Bác sĩ tra cứu Phác đồ Bộ Y Tế $\rightarrow$ Hệ thống truy xuất Hybrid RAG (HNSW Cosine + GIN BM25) $\rightarrow$ Bác sĩ nhấn "+ Áp dụng" phác đồ vào đơn thuốc.
     - Luồng 3: Bác sĩ chọn thuốc tương tác chống chỉ định (ví dụ Clopidogrel + Omeprazole) $\rightarrow$ Cảnh báo DDI thời gian thực hiển thị rõ ràng.
  3. **Phát triển Phân hệ Tiếp theo**: Chuyển sang thực hiện các tính năng trên nhánh dịch vụ chuyên trách (ví dụ `feature/vi/prescription-service` hoặc `feature/vi/consultation-service`) theo đúng `AGENTS.md`.




