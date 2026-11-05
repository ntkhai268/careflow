# Báo Cáo Kỹ Thuật Hiện Thực Phân Hệ AI Lâm Sàng & Clinical RAG
## (CareFlow Clinical AI & RAG Implementation Technical Report)

> **Dự án**: CareFlow — Phân hệ Trợ lý AI Lâm sàng & Clinical RAG  
> **Service trọng tâm**: `careflow-ai-service`, `frontend/doctor-web`, `careflow_ai` (Vector Database)  
> **Nhánh phát triển**: `feature/vi/ai-service` (Đã đồng bộ và giải quyết xung đột với `origin/develop` R0–R7)  
> **Trạng thái**: Hoàn tất 100% các Phase 1–6 (Ingestion, pgvector, Hybrid Search RRF, Dual Voice STT, Frontend SJD & Merge baseline)

---

## 1. Tổng Quan Kiến Trúc Kỹ Thuật (Architecture Overview)

Hệ thống AI Lâm sàng trong CareFlow được thiết kế nhằm hỗ trợ Bác sĩ tại phòng khám ngoại trú ra quyết định y khoa dựa trên **Bằng chứng Thực chứng (Evidence-Based Medicine)**, giải quyết triệt để 3 vấn đề cố hữu của Large Language Model (LLM) trong y tế: **Ảo giác y khoa (Hallucination)**, **Thiếu bối cảnh phác đồ Việt Nam** và **Trách nhiệm pháp lý lâm sàng**.

### 1.1. Luồng Xử Lý Toàn Diện (End-to-End Execution Pipeline)

```
[ Bác Sĩ Phòng Khám ]
       │
       ├─── (1) Ghi âm lời khám ───────► [ useVoiceStore (Zustand) ] ──► [ Whisper STT / AI Service ]
       │                                                                         │
       │                                                                   (Điền tự động)
       │                                                                         ▼
       │                                                            [ Form Ca Khám Lâm Sàng ]
       │                                                                         │
       └─── (2) Hỏi Trợ Lý / Click gợi ý ──┐                                    │
                                           │                                    │
                                           ▼                                    │
                               [ API Gateway (Port 8080) ]                      │
                                           │ (Forward JWT + Ngữ cảnh ca khám)   │
                                           ▼                                    │
                             [ careflow-ai-service (Port 8091) ] ◄──────────────┘
                                           │
                ┌──────────────────────────┴──────────────────────────┐
                │ GIAI ĐOẠN 1: HYBRID RETRIEVAL                       │
                │                                                     │
                │ 1. Text Embedding (768d) qua Gemini API             │
                │ 2. Song song Dense HNSW (<=>) & Sparse BM25 (GIN)   │
                │ 3. Reciprocal Rank Fusion (RRF, k=60) Top 5 Chunks  │
                └──────────────────────────┬──────────────────────────┘
                                           │
                ┌──────────────────────────┴──────────────────────────┐
                │ GIAI ĐOẠN 2: AUGMENTATION & GUARDRAILS              │
                │                                                     │
                │ 1. Breadcrumbs: [Tài liệu > Chuyên khoa > Đề mục]   │
                │ 2. DDI Matrix: Kiểm tra tương tác thuốc chống CĐ    │
                │ 3. Prompt 3 tầng có ràng buộc y khoa nghiêm ngặt    │
                └──────────────────────────┬──────────────────────────┘
                                           │
                ┌──────────────────────────┴──────────────────────────┐
                │ GIAI ĐOẠN 3: GENERATION & VERIFICATION              │
                │                                                     │
                │ 1. Gemini 2.5 Flash sinh khuyến nghị điều trị       │
                │ 2. Trích xuất bằng chứng: evidenceRefs              │
                │ 3. Disclaimer: Bác sĩ chịu trách nhiệm chuyên môn   │
                └──────────────────────────┬──────────────────────────┘
                                           │
                                           ▼
                              [ Frontend Doctor Web ]
                                           │
               ┌───────────────────────────┴───────────────────────────┐
               │ - Render Markdown chuẩn y khoa (FormattedAiText)      │
               │ - Nút "+ Áp dụng" phác đồ vào đơn thuốc / chẩn đoán   │
               │ - Thẻ cảnh báo tương tác thuốc DDI & Compact Allergy  │
               └───────────────────────────────────────────────────────┘
```

---

## 2. Các Thành Phần Kỹ Thuật Đã Hoàn Thành (Detailed Implementations)

### 2.1. Phân Kỳ 1: Tiền Xử Lý Dữ Liệu & Data Ingestion Pipeline

Toàn bộ dữ liệu lâm sàng được chuẩn hóa thành tài liệu Markdown sạch và file dữ liệu có cấu trúc tại thư mục [`docs/rag-docs/`](file:///home/levi/Desktop/Projects/careflow/docs/rag-docs):

1. **Từ điển Danh mục Bệnh ICD-10 (Bộ Y Tế)**:
   - File: [`docs/rag-docs/icd10/icd10_dictionary.csv`](file:///home/levi/Desktop/Projects/careflow/docs/rag-docs/icd10/icd10_dictionary.csv).
   - Quy mô: **15.844 bản ghi ICD-10** chuẩn hóa tiếng Việt không lỗi font.
2. **Ma Trận Tương Tác Thuốc Chống Chỉ Định (DDI Matrix)**:
   - File: [`docs/rag-docs/ddi/ddi_contraindicated.csv`](file:///home/levi/Desktop/Projects/careflow/docs/rag-docs/ddi/ddi_contraindicated.csv).
   - Căn cứ pháp lý: **Quyết định 5948/QĐ-BYT** của Bộ Y Tế.
   - Quy mô: **701 cặp tương tác thuốc chống chỉ định nghiêm trọng** (ví dụ: Clopidogrel – Omeprazole, Simvastatin – Clarithromycin...).
   - Cơ chế: **Synthetic Narrative Serialization** — chuyển hóa từng hàng bảng DDI thành 1 đoạn văn bản ngữ cảnh độc lập để embedding.
3. **Phác Đồ Điều Trị Bộ Y Tế (Clinical Practice Guidelines)**:
   - Nguồn văn bản: Đái tháo đường Type 2 (QĐ 5481/QĐ-BYT), Tăng huyết áp (QĐ 3192/QĐ-BYT), Viêm phổi mắc phải cộng đồng (QĐ 4815/QĐ-BYT).
   - Chuẩn hóa Markdown: Giữ nguyên cấu trúc phân cấp `#`, `##`, `###`, bảng biểu thuốc (Table Integrity).
4. **Script Tự Động Phân Đoạn & Vector Hóa**:
   - File script: [`scripts/ingest_clinical_rag.py`](file:///home/levi/Desktop/Projects/careflow/scripts/ingest_clinical_rag.py).
   - **Kỹ thuật Contextual Chunking**:
     - Bơm Breadcrumbs phân cấp vào từng chunk: `[Văn bản BYT > Chuyên khoa > Đề mục]`.
     - Kích thước cửa sổ token: 400 – 800 tokens. Tự động chia đệ quy khi > 800 tokens, gộp đoạn khi < 150 tokens.
     - Khâu nối đa đoạn (Overlap): 50 – 80 tokens kèm liên kết con trỏ `prev_chunk_id` và `next_chunk_id`.
     - Trích xuất metadata JSONB: Danh sách mã ICD-10 liên quan, danh sách tên thuốc generic đề cập, chuyên khoa.
   - Đã nạp thành công **1.095 / 1.095 chunks (100%)** với vector 768 chiều (`models/gemini-embedding-2` / `text-embedding-004`).
   - Lưu trữ bản dump SQL tái tạo nhanh: [`scripts/careflow_ai_schema_and_data.sql`](file:///home/levi/Desktop/Projects/careflow/scripts/careflow_ai_schema_and_data.sql) (9.5MB).

---

### 2.2. Phân Kỳ 2: Cơ Sở Dữ Liệu Vector (PostgreSQL 16 + `pgvector`)

Hạ tầng lưu trữ vector được triển khai trên PostgreSQL 16 tích hợp extension `pgvector` 0.8.7 chạy độc lập trên Docker (Port `5433`, Database `careflow_ai`).

#### Cấu Trúc Bảng DDL & Chỉ Mục Tối Ưu:
- **`clinical_guideline_documents`**: Lưu 4 tài liệu phác đồ gốc của Bộ Y Tế (Mã quyết định, tiêu đề, chuyên khoa, trạng thái active).
- **`clinical_guideline_chunks`**: Lưu 1.095 đoạn tri thức được phân rã ngữ nghĩa:
  - Cột vector: `embedding vector(768)`
  - Cột full-text: `tsv_content tsvector` sinh tự động từ văn bản chunk
  - Cột liên kết & phân cấp: `document_id`, `chunk_index`, `section_title`, `breadcrumbs`
  - Cột metadata: `metadata jsonb` (chứa `icd10_codes`, `mentioned_drugs`)
- **`ai_clinical_interactions`**: Nhật ký kiểm toán (Audit Trail) lưu trữ toàn bộ truy vấn của bác sĩ, câu trả lời của AI, danh sách chunk trích dẫn và phản hồi bác sĩ (áp dụng hay từ chối).

#### Chỉ Mục Kép Hiệu Năng Cao:
- **Chỉ mục HNSW Dense Vector**:
  ```sql
  CREATE INDEX idx_clinical_chunks_hnsw 
  ON clinical_guideline_chunks 
  USING hnsw (embedding vector_cosine_ops) 
  WITH (m = 16, ef_construction = 64);
  ```
- **Chỉ mục GIN Sparse Full-Text**:
  ```sql
  CREATE INDEX idx_clinical_chunks_tsv 
  ON clinical_guideline_chunks 
  USING gin (tsv_content);
  ```
- **Chỉ mục GIN JSONB**:
  ```sql
  CREATE INDEX idx_clinical_chunks_metadata_gin 
  ON clinical_guideline_chunks 
  USING gin (metadata jsonb_path_ops);
  ```

---

### 2.3. Phân Kỳ 3: Backend Hybrid Search Engine & Clinical RAG

Trong module [`careflow-ai-service`](file:///home/levi/Desktop/Projects/careflow/careflow-ai-service), hệ thống RAG được cài đặt chuẩn TDD và clean architecture:

1. **JPA Native Query Repositories**:
   - File: [`ClinicalGuidelineChunkRepository.java`](file:///home/levi/Desktop/Projects/careflow/careflow-ai-service/src/main/java/com/careflow/ai/repository/ClinicalGuidelineChunkRepository.java).
   - Truy vấn Dense: Tìm kiếm theo Cosine Distance sử dụng toán tử `<=>` và ép kiểu native `cast(:embedding as vector)`.
   - Truy vấn Sparse: Tìm kiếm full-text bằng hàm `plainto_tsquery('simple', :query)` trên `tsv_content`.
2. **Thuật Toán Hybrid Search Kết Hợp RRF (Reciprocal Rank Fusion)**:
   - File: [`ClinicalHybridSearchService.java`](file:///home/levi/Desktop/Projects/careflow/careflow-ai-service/src/main/java/com/careflow/ai/service/ClinicalHybridSearchService.java).
   - Chuẩn học thuật: Cormack et al. (SIGIR 2009).
   - Tham số: Hằng số $k = 60$, lấy Top 20 ứng viên từ Vector Search và Top 20 ứng viên từ BM25 Full-text Search.
   - Công thức tính điểm:
     $$RRF\_Score(d) = \sum_{m \in \{Vector, BM25\}} \frac{1}{k + r_m(d)}$$
   - Kết quả: Hợp nhất và chọn ra **Top 5 Chunks** có điểm số cao nhất chuyển tiếp sang tầng Augmentation.
3. **Dịch Vụ AI Lâm Sàng & Guardrails**:
   - File: [`AiClinicalAssistantService.java`](file:///home/levi/Desktop/Projects/careflow/careflow-ai-service/src/main/java/com/careflow/ai/service/AiClinicalAssistantService.java).
   - Tích hợp Gemini 2.5 Flash / DeepSeek fallback.
   - Bơm bối cảnh lâm sàng thời gian thực: Tuổi, giới tính, tiền sử bệnh, dị ứng, triệu chứng, khám thực thể, thuốc đang kê.
   - Bóc tách cấu trúc dữ liệu trả về gồm: `summary`, `differentialDiagnoses`, `treatmentSuggestions`, `contraindications`, `ddiWarnings`, và mảng `evidenceRefs` (Mã QĐ BYT, tên mục phác đồ).
   - **DDI Guardrails**: Chỉ kích hoạt quét và cảnh báo tương tác thuốc khi bác sĩ thực sự đã thêm thuốc vào đơn khám, loại bỏ cảnh báo rác.

---

### 2.4. Phân Kỳ 4: Kiến Trúc Voice-to-Text Kép (Dual Voice STT Architecture)

CareFlow hiện tích hợp hoàn chỉnh và song song **hai luồng xử lý giọng nói độc lập**, phục vụ hai giai đoạn khác nhau trong phiên khám:

| Tiêu Chí | Luồng 1: Consultation Clinical STT | Luồng 2: Voice Prescription Panel |
|---|---|---|
| **Vị trí trên UI** | Căn lề phải phía trên hàng Tab khám bệnh (Cột trái) | Panel "Nhập toa bằng giọng nói" (Cột phải) |
| **Mục đích** | Bác sĩ đọc diễn biến bệnh, triệu chứng lâm sàng | Bác sĩ đọc danh mục thuốc kê đơn, liều dùng |
| **Quản lý trạng thái** | Zustand Store (`useVoiceStore.ts`) | Component state nội bộ (`VoicePrescriptionPanel.tsx`) |
| **Xử lý Backend** | `careflow-ai-service` bóc tách thực thể lâm sàng | `PrescriptionAiDraftService` + Python voice core |
| **Điền tự động vào** | Lý do khám, Bệnh sử, Triệu chứng cơ năng & thực thể | Danh sách đơn thuốc nháp (Prescription Draft Items) |
| **Tính năng an toàn** | Nút **"Hủy"** tức thì khi ghi âm hoặc preview | Xem trước danh sách thuốc trước khi lưu toa nháp |

---

### 2.5. Phân Kỳ 5: Giao Diện Người Dùng Bác Sĩ (Doctor Web UI/UX)

Theo tiêu chuẩn thiết kế Bệnh viện Nhi đồng SJD Barcelona tại [`docs/design.md`](file:///home/levi/Desktop/Projects/careflow/docs/design.md), giao diện được tối ưu hóa toàn diện tại [`consultation/[id]/page.tsx`](file:///home/levi/Desktop/Projects/careflow/frontend/doctor-web/src/app/(dashboard)/consultation/[id]/page.tsx):

1. **Chuẩn Hóa Typography & Loại Bỏ Overdesign**:
   - Giảm 30% thang cỡ chữ chuẩn y khoa (`text-[11px]`, `text-[12px]`, `text-[13px]`).
   - Xóa bỏ hoàn toàn mascot "Bác sĩ Chồn AI", emoji đùa cợt (`🐾`), gradient màu tím/hồng lòe lẹt. Chuẩn hóa giọng điệu: *"Trợ lý Lâm sàng (Phác đồ điều trị Bộ Y Tế)"*.
2. **Thanh Compact Bar Cảnh Báo Dị Ứng**:
   - Chiều cao thu gọn còn ~36px (`min-h-[36px] px-3 py-1.5`).
   - Viền nhấn mảnh bên trái 3px (`border-l-[3px] border-l-rose-500`), loại bỏ chữ in hoa thô cứng, hiển thị trực quan các dị ứng thuốc nghiêm trọng.
3. **Thanh Điều Khiển Tra Cứu Lâm Sàng Thống Nhất**:
   - Gộp ô tra cứu văn bản và các chips gợi ý nhanh thành 1 thanh công cụ duy nhất trên đỉnh tab AI: *"Chẩn đoán phân biệt"*, *"Cảnh báo tương tác DDI"*, *"Dữ liệu còn thiếu"*, *"Đề xuất Cận lâm sàng"*.
4. **Render Markdown Chuẩn Y Khoa**:
   - Component: [`FormattedAiText.tsx`](file:///home/levi/Desktop/Projects/careflow/frontend/doctor-web/src/lib/format-ai-text.tsx).
   - Tích hợp `react-markdown` và `remark-gfm` render chuẩn xác tiêu đề, gạch đầu dòng, bảng liều lượng thuốc và khối trích dẫn phác đồ.
5. **Combobox Danh Mục Thuốc Có Dữ Liệu Thực**:
   - Nạp 25 thuốc mẫu chuẩn phác đồ Bộ Y Tế vào bảng `drugs_dictionary` (`careflow_prescription`).
   - Bổ sung `DEFAULT_MEDICINE_CATALOG` trên Frontend đảm bảo ô chọn thuốc luôn có gợi ý chính xác khi bác sĩ gõ tìm kiếm.
6. **Khối Bằng Chứng Phác Đồ Bộ Y Tế**:
   - Render danh sách `evidenceRefs` kèm badge mã quyết định (VD: `5481/QĐ-BYT`).
   - Nút **"+ Áp dụng"**: Tự động đưa chẩn đoán xác định và danh mục thuốc khuyến nghị vào đơn khám của bệnh nhân chỉ bằng 1 click.

---

### 2.6. Phân Kỳ 6: Đồng Bộ Mã Nguồn & Hòa Nhập Nhánh `develop` (R0–R7)

1. **Giải Quyết Xung Đột Mã Nguồn (7 tệp)**:
   - Đã kéo toàn bộ các release R0–R7 từ `origin/develop` vào `feature/vi/ai-service`.
   - Bảo toàn cấu trúc `ClinicalEngine` của develop song song với toàn bộ `ClinicalHybridSearchService` RAG của nhánh AI.
   - Hợp nhất cơ chế bảo mật xác thực DPoP tokens và Next.js API client routing.
2. **Kết Quả Kiểm Thử Xác Thực**:
   - **Frontend**: Chạy `npm run build` trên `frontend/doctor-web` thành công 100% (Turbopack, TypeCheck, Static Optimization).
   - **Backend**: Chạy `mvn test` trên `careflow-ai-service` đạt **13/13 unit & integration tests PASS 100%**.

---

## 3. Bản Đồ File Mã Nguồn Trọng Tâm (Key Source Code Mapping)

| Thành phần | Đường dẫn tệp | Mô tả chức năng |
|---|---|---|
| **RRF Hybrid Search** | [`ClinicalHybridSearchService.java`](file:///home/levi/Desktop/Projects/careflow/careflow-ai-service/src/main/java/com/careflow/ai/service/ClinicalHybridSearchService.java) | Thuật toán Reciprocal Rank Fusion kết hợp HNSW Cosine và GIN BM25 |
| **Vector Repository** | [`ClinicalGuidelineChunkRepository.java`](file:///home/levi/Desktop/Projects/careflow/careflow-ai-service/src/main/java/com/careflow/ai/repository/ClinicalGuidelineChunkRepository.java) | Native queries `<=>`, `ts_rank_cd`, JSONB filter `@>` |
| **AI Clinical Assistant** | [`AiClinicalAssistantService.java`](file:///home/levi/Desktop/Projects/careflow/careflow-ai-service/src/main/java/com/careflow/ai/service/AiClinicalAssistantService.java) | Bơm context ca khám, gọi Gemini, kiểm soát DDI & bóc tách evidence |
| **Gemini Client** | [`GeminiClientService.java`](file:///home/levi/Desktop/Projects/careflow/careflow-ai-service/src/main/java/com/careflow/ai/service/GeminiClientService.java) | Sinh dense vector 768 chiều và gọi model LLM |
| **JPA Entities** | [`ClinicalGuidelineChunk.java`](file:///home/levi/Desktop/Projects/careflow/careflow-ai-service/src/main/java/com/careflow/ai/entity/ClinicalGuidelineChunk.java) | Entity ánh xạ bảng `clinical_guideline_chunks` |
| **Ingestion Pipeline** | [`scripts/ingest_clinical_rag.py`](file:///home/levi/Desktop/Projects/careflow/scripts/ingest_clinical_rag.py) | Script băm nhỏ văn bản BYT, trích xuất metadata và sinh vector |
| **SQL Data Dump** | [`scripts/careflow_ai_schema_and_data.sql`](file:///home/levi/Desktop/Projects/careflow/scripts/careflow_ai_schema_and_data.sql) | DDL schema và 1.095 chunks có sẵn vector nạp tức thì trong 3 giây |
| **Zustand Voice Store**| [`voice-store.ts`](file:///home/levi/Desktop/Projects/careflow/frontend/doctor-web/src/stores/voice-store.ts) | Store quản lý trạng thái ghi âm, đếm giờ, hủy bản ghi |
| **Voice Button** | [`VoiceRecorderButton.tsx`](file:///home/levi/Desktop/Projects/careflow/frontend/doctor-web/src/components/VoiceRecorderButton.tsx) | Nút ghi âm lời khám rảnh tay đặt tinh gọn phía trên Tab |
| **Giao Diện Ca Khám**| [`page.tsx`](file:///home/levi/Desktop/Projects/careflow/frontend/doctor-web/src/app/(dashboard)/consultation/[id]/page.tsx) | Trang khám bệnh chính tích hợp Tab RAG, DDI alert, Evidence cards |
| **Markdown Formatter**| [`format-ai-text.tsx`](file:///home/levi/Desktop/Projects/careflow/frontend/doctor-web/src/lib/format-ai-text.tsx) | Component định dạng Markdown y khoa |

---

## 4. Hướng Dẫn Vận Hành & Khởi Chạy Local (Quick Run Guide)

### Bước 1: Khởi động CSDL Vector và Core Database
```bash
# Khởi động container PostgreSQL Vector (Port 5433)
docker start careflow-ai-postgres

# (Nếu khởi tạo lần đầu) Import schema và 1.095 chunks
docker exec -i careflow-ai-postgres psql -U careflow -d careflow_ai < scripts/careflow_ai_schema_and_data.sql
```

### Bước 2: Khởi động Backend AI Service
```bash
cd careflow-ai-service
mvn spring-boot:run
# Hoặc chạy toàn bộ hệ thống qua script: ./start-local.sh ai
```

### Bước 3: Khởi động Frontend Doctor Web
```bash
cd frontend/doctor-web
npm run dev
```

### Bước 4: Kiểm Thử E2E Trên Trình Duyệt
1. Truy cập `http://localhost:3000/consultation/f0000001-0000-0000-0000-000000000001` bằng tài khoản Bác sĩ (`bacsitung` / `password`).
2. Nhấn nút **"Ghi âm"** ở góc phải trên hàng Tab để test Voice STT triệu chứng lâm sàng.
3. Chuyển sang Tab **"Trợ lý AI Lâm sàng"**, chọn chip gợi ý nhanh (VD: *"Chẩn đoán phân biệt"* hoặc *"Cảnh báo tương tác DDI"*).
4. Quan sát câu trả lời có định dạng Markdown đẹp, khối trích dẫn phác đồ Bộ Y Tế kèm badge mã văn bản và nút "+ Áp dụng".
