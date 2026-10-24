# CareFlow Clinical RAG & AI Service — Session Handover & Memory Log

> **Tài liệu bàn giao ngữ cảnh liên phiên (Cross-Session Handover & Memory Context)**  
> **Dự án**: CareFlow — Phân hệ AI Lâm Sàng & Clinical RAG  
> **Nhánh Git hiện tại**: `feature/vi/ai-service` (Tuyệt đối không push trực tiếp lên `main`)  
> **Cập nhật lần cuối**: 23/10/2026 (17:00)  
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
    - **Loại bỏ Overdesign theo `docs/design.md`**: Gỡ bỏ hoàn toàn gradient lòe lẹt, emoji trong tiêu đề (`🐾`), áp dụng bảng màu tối giản thực dụng SJD Barcelona, giảm 30% cỡ chữ chuẩn y khoa.
    - **Hợp nhất Hỏi đáp & Gợi ý nhanh**: Gộp khung câu hỏi gợi ý và ô tra cứu thành một thanh điều khiển duy nhất tại đầu trang, loại bỏ hoàn toàn khung chat thừa thãi bên dưới.
    - **Nút Hủy (Cancel & Re-record) cho Voice STT**:
      - Bổ sung nút **"Hủy & Nói lại"** ngay khi đang ghi âm để bác sĩ hủy ngay mà không cần đợi.
      - Bổ sung nút **"Hủy"** khi đang xử lý STT và trong `VoicePreviewModal`.
    - **Quản lý Trạng thái Toàn cục với Zustand**: Tạo store `useVoiceStore` (`src/stores/voice-store.ts`) quản lý tập trung `isRecording`, `isProcessing`, `recordingDuration`, `transcript`, `extractedFields`, `isPreviewOpen`.
    - **Khắc phục Danh mục Thuốc Trống (Combobox Chips)**:
      - Khởi tạo bảng `drugs_dictionary` và nạp 25 thuốc lâm sàng chuẩn phác đồ Bộ Y Tế (ĐTĐ type 2, Tăng huyết áp, Viêm phổi, Tim mạch, DDI Clopidogrel - Omeprazole) vào CSDL `careflow_prescription`.
      - Cung cấp `DEFAULT_MEDICINE_CATALOG` trên Frontend đảm bảo ô chọn thuốc Combobox luôn có đầy đủ dữ liệu gợi ý.

---

## 2. Những Công Việc Đã Hoàn Thành

### A. Phiên 23/10/2026 (Phiên chiều):
1. **Kiểm Tra & Khôi Phục Hạn Mức Google Embedding API**:
   - Xác nhận quota Google AI Studio đã tự động reset thành công sau 00:00 UTC (14:00 VN).
   - Gọi kiểm thử trực tiếp sinh vector 768 chiều từ `models/gemini-embedding-2` trả về HTTP 200 OK.
2. **Khởi Động Lại Hạ Tầng Vector Database**:
   - Kích hoạt lại container Docker `careflow-ai-postgres` (PostgreSQL 16 + pgvector 0.8.7) tại port `5433`.
   - Kết nối thành công với user `careflow`, db `careflow_ai`.
3. **Hoàn Tất Ingestion 100% Kho Tri Thức Lâm Sàng**:
   - Chạy pipeline nạp tri thức [`scripts/ingest_clinical_rag.py`](../scripts/ingest_clinical_rag.py).
   - Nạp 976 chunks cũ trong 0.2s nhờ cơ chế Disk Cache Hit.
   - Xử lý hoàn tất 119 chunks DDI cuối cùng qua 12 batches an toàn với Google API.
   - Ghi thành công toàn bộ **1.095 chunks kèm dense vector 768 chiều** vào bảng `clinical_guideline_chunks`.
4. **Kiểm Thử Trực Tiếp Truy Vấn CSDL (Verification Tests)**:
   - **BM25 / Full-text search**: Cấu hình `simple` dictionary trên cột `tsv_content` hoạt động chính xác với độ tương đồng từ khóa cao.
   - **Vector Cosine Distance**: Truy vấn toán tử `<=>` với chỉ mục HNSW hoạt động mượt mà, trả kết quả tương đồng ngữ nghĩa chính xác.

### B. Phiên 22/10/2026 (Phiên tối):
1. **Tài liệu Tiêu chuẩn Chiến lược Phân đoạn Tri thức ([`.planning/ai-service/chunking-strategy.md`](../.planning/ai-service/chunking-strategy.md))**:
   - Chuẩn mực Best Practice: Contextual Document-Structure Chunking, Breadcrumb Injection, Window Guard 400 - 800 tokens.
   - Nguyên tắc DDI: 1 Hàng CSV = 1 Synthetic Narrative chunk độc lập.
2. **Sơ đồ DBML & Sổ tay Lý thuyết Vector HNSW**:
   - Hoàn thiện tài liệu DBML tại [`.planning/ai-service/ai-service-db.dbml`](../.planning/ai-service/ai-service-db.dbml) và giải trình [`.planning/ai-service/ai-service-db.md`](../.planning/ai-service/ai-service-db.md).
   - Chuẩn hóa sơ đồ HNSW 3 tầng tại [`.planning/ai-service/theory.md`](../.planning/ai-service/theory.md).

---

## 3. Trạng Thái CSDL & Hạn Mức Vector

- **Thông số kết nối Vector DB**:
  - Host: `localhost` | Port: `5433` | Database: `careflow_ai` | User: `careflow` | Password: `careflow`
  - Container Docker: `careflow-ai-postgres`
- **Số lượng bản ghi thực tế**:
  - `clinical_guideline_documents`: **4** tài liệu cha (`BYT-QD-4800-HOHAP`, `BYT-QD-5968-TIMMACH`, `BYT-QD-5481-NOITIET`, `BYT-QD-5948-DDI`).
  - `clinical_guideline_chunks`: **1.095** chunks (100% có dense vector 768 chiều không null).
- **Bộ nhớ đệm Offline Cache**:
  - [`scripts/.rag_embeddings_cache_v2.json`](../scripts/.rag_embeddings_cache_v2.json) chứa toàn bộ 1.095 vector embeddings, đảm bảo nạp lại DB trong < 1s nếu cần tái tạo.

---

## 4. Nhiệm Vụ Cụ Thể Tiếp Theo (Next Immediate Tasks — Phase 5 End-to-End Verification)

1. **Khởi chạy Hệ thống & Thử nghiệm Thực tế (Live End-to-End Test)**:
   - Khởi động `careflow-ai-service` (port `8088` hoặc profile dev).
   - Mở `frontend/doctor-web` trên trình duyệt (`http://localhost:3000`).
   - Vào một ca khám mẫu (ví dụ: ca ho sốt hoặc tăng huyết áp), chuyển qua Tab "Trợ lý AI Lâm sàng" và bấm "Gợi ý Chẩn đoán Phân biệt".
   - Xác nhận kết quả phân tích hiển thị chính xác các đoạn trích Phác đồ Bộ Y Tế và cảnh báo tương tác thuốc DDI.

2. **Commit Code & Báo Cáo Milestone**:
   - Khi người dùng yêu cầu, tạo focused commit theo quy chuẩn conventional commits tiếng Anh.

---

## 5. Tệp Tham Khảo Quan Trọng (Key File References)

- **Chiến lược Chunking chuẩn CareFlow**: [`.planning/ai-service/chunking-strategy.md`](../.planning/ai-service/chunking-strategy.md)
- **Script Ingestion hoàn chỉnh**: [`scripts/ingest_clinical_rag.py`](../scripts/ingest_clinical_rag.py)
- **Cache Vector 1.095 chunks**: [`scripts/.rag_embeddings_cache_v2.json`](../scripts/.rag_embeddings_cache_v2.json)
- **Sơ đồ DBML CSDL AI**: [`.planning/ai-service/ai-service-db.dbml`](../.planning/ai-service/ai-service-db.dbml)
- **Tài liệu giải trình CSDL**: [`.planning/ai-service/ai-service-db.md`](../.planning/ai-service/ai-service-db.md)
- **Sổ tay lý thuyết Vector & HNSW**: [`.planning/ai-service/theory.md`](../.planning/ai-service/theory.md)
- **Tài liệu kiến trúc AI & RAG đầy đủ**: [`docs/ai-service/clinical-ai-architecture-and-rag-implementation-plan.md`](./ai-service/clinical-ai-architecture-and-rag-implementation-plan.md)
- **Hợp đồng dịch vụ AI**: `docs/service-contracts/12-ai-clinical-assistant.md` (CF-SVC-12)
