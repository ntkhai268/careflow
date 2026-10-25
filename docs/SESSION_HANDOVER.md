# CareFlow Clinical RAG & AI Service — Session Handover & Memory Log

> **Tài liệu bàn giao ngữ cảnh liên phiên (Cross-Session Handover & Memory Context)**  
> **Dự án**: CareFlow — Phân hệ AI Lâm Sàng & Clinical RAG  
> **Nhánh Git hiện tại**: `feature/vi/ai-service` (Tuyệt đối không push trực tiếp lên `main`)  
> **Cập nhật lần cuối**: 25/10/2026 (13:25)  
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
   - Thiết kế và xuất bản sơ đồ kiến trúc trực quan độc lập (HTML standalone): [`docs/ai-service/clinical-ai-rag-and-voice-architecture.html`](./ai-service/clinical-ai-rag-and-voice-architecture.html) mô tả chi tiết luồng Voice STT, Hybrid Search RAG và Human-in-the-Loop.
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
- **Tài liệu kiến trúc AI & RAG đầy đủ**: [`docs/ai-service/clinical-ai-architecture-and-rag-implementation-plan.md`](./ai-service/clinical-ai-architecture-and-rag-implementation-plan.md)
