# CareFlow Clinical RAG & AI Service — Session Handover & Memory Log

> **Tài liệu bàn giao ngữ cảnh liên phiên (Cross-Session Handover & Memory Context)**  
> **Dự án**: CareFlow — Phân hệ AI Lâm Sàng & Clinical RAG  
> **Nhánh Git hiện tại**: `feature/vi/ai-service` (Tuyệt đối không push trực tiếp lên `main`)  
> **Cập nhật lần cuối**: 20/10/2026  
> **Mục đích**: Lưu giữ 100% bối cảnh công việc, các quyết định kiến trúc, kinh nghiệm xử lý lỗi và nhiệm vụ tiếp theo để bất kỳ phiên làm việc AI mới nào cũng có thể tiếp tục ngay lập tức mà không bị mất dấu.

---

## 1. Vị Trí Hiện Tại & Tiến Độ Dự Án (Current State & Progress)

- **Lộ trình tổng thể**: Đang triển khai theo tài liệu kỹ thuật [`docs/ai-service/clinical-ai-architecture-and-rag-implementation-plan.md`](./ai-service/clinical-ai-architecture-and-rag-implementation-plan.md).
- **Trạng thái phân kỳ**:
  - ✅ **Phase 1: Ingestion & Data Preparation**: ĐÃ HOÀN TẤT 100% (Dữ liệu y khoa lâm sàng đã được làm sạch, bóc tách và chuẩn hóa).
  - ⏳ **Phase 2: Database Schema (`pgvector`) & Vector Indexing**: CHUẨN BỊ BẮT ĐẦU (Nhiệm vụ kế tiếp).
  - 📋 **Phase 3: Backend RAG Engine & Hybrid Search**: Chưa bắt đầu.
  - 📋 **Phase 4: Frontend Zustand Store & Tab Trợ lý AI**: Chưa bắt đầu.

---

## 2. Những Công Việc Đã Hoàn Thành (Completed Work & Artifacts)

### A. Dữ liệu y khoa lâm sàng chuẩn hóa ([`docs/rag-docs/`](./rag-docs/)):
1. **Từ điển ICD-10 đầy đủ ([`docs/rag-docs/icd10/icd10_dictionary.csv`](./rag-docs/icd10/icd10_dictionary.csv))**:
   - Quét trọn vẹn toàn bộ **1.271 trang** của văn bản gốc `06-byt-kem.pdf`.
   - Thu thập **15.844 mã bệnh** từ Chương I (A00) đến Chương XXII (U85).
   - Đã kiểm tra có đầy đủ các mã bệnh lâm sàng cốt lõi của dự án:
     - `I10`: Tăng huyết áp vô căn (nguyên phát).
     - `E11`: Đái tháo đường típ 2.
     - `J18`: Viêm phổi, không xác định vi sinh vật.
2. **Danh mục Tương tác thuốc Chống chỉ định DDI ([`docs/rag-docs/ddi/`](./rag-docs/ddi/))**:
   - Bóc tách chuẩn xác từ Quyết định 5948/QĐ-BYT (176 trang) bằng `pdfplumber`, khắc phục hoàn toàn lỗi vỡ bảng của `markitdown`.
   - File CSV: [`ddi_contraindicated.csv`](./rag-docs/ddi/ddi_contraindicated.csv) — **701 cặp/nhóm tương tác chống chỉ định** (633 cặp hoạt chất Bảng 3.1 + 68 nhóm dược lý Bảng 3.2).
   - File Markdown: [`tuong_tac_thuoc_chong_chi_dinh_5948.md`](./rag-docs/ddi/markdown/tuong_tac_thuoc_chong_chi_dinh_5948.md) — Định dạng bảng GFM chuẩn 6 cột: `STT | Hoạt chất 1 | Hoạt chất 2 | Cơ chế | Hậu quả | Xử trí`.
3. **Phác đồ điều trị sạch ([`docs/rag-docs/treatment-protocol/markdown/`](./rag-docs/treatment-protocol/markdown/))**:
   - [`tang_huyet_ap.md`](./rag-docs/treatment-protocol/markdown/tang_huyet_ap.md) (Tăng huyết áp - QĐ BYT).
   - [`dai_thao_duong_tip2.md`](./rag-docs/treatment-protocol/markdown/dai_thao_duong_tip2.md) (Đái tháo đường típ 2 - QĐ BYT).
   - [`viem_phoi_cong_dong.md`](./rag-docs/treatment-protocol/markdown/viem_phoi_cong_dong.md) (Viêm phổi cộng đồng - QĐ 4800/QĐ-BYT): Đã loại bỏ 100% chữ ký số điện tử rác (`Times-Roman`).
4. **Danh mục thuốc Generic ([`docs/rag-docs/drug-formulary/drugs_generic.csv`](./rag-docs/drug-formulary/drugs_generic.csv))**:
   - Chuẩn hóa cấu trúc 9 cột: `stt, category, bid_code, active_ingredient, concentration, route, unit, group, dosage_form` cho 185 thuốc generic.

### B. Bộ công cụ script tự động ([`scripts/`](../scripts/)):
- [`scripts/extract_all_icd10.py`](../scripts/extract_all_icd10.py): Script trích xuất full 1.271 trang ICD-10 (hỗ trợ checkpoint lưu định kỳ mỗi 100 trang).
- [`scripts/extract_ddi_perfect.py`](../scripts/extract_ddi_perfect.py): Script bóc tách bảng tương tác thuốc QĐ 5948, xử lý ngắt trang và sửa lỗi wrap chữ tên thuốc (`Acenocoumar ol` ➔ `Acenocoumarol`...).
- [`scripts/clean_and_rebuild_protocols.py`](../scripts/clean_and_rebuild_protocols.py): Script lọc bỏ watermark `Times-Roman` và chuẩn hóa bảng thuốc.

### C. Cấu hình bảo mật hạ tầng ([`.gitignore`](../.gitignore)):
- Đã đưa vào `.gitignore`:
  - `application-remote.yml` & `application-remote.properties` (Bảo vệ IP Tailscale `100.116.233.60` và credentials RabbitMQ/Postgres).
  - `.venv/`, `venv/`, `__pycache__/`, `*.py[cod]` (Không đẩy binary Python lên repo).
  - `docs/rag-docs/**/*.pdf`, `docs/rag-docs/**/*.xlsx` (Chặn các file PDF nặng 32MB, chỉ giữ lại Markdown và CSV gọn nhẹ 1.6MB).

---

## 3. Quyết Định Kiến Trúc & Cạm Bẫy Đã Khắc Phục (Decisions & Gotchas)

1. **Quy tắc Git Branch**:
   - Chỉ commit và push lên nhánh: **`feature/vi/ai-service`**.
   - **TUYỆT ĐỐI KHÔNG PUSH LÊN NHÁNH `main`**.
2. **Công cụ chuyển đổi PDF sang dữ liệu**:
   - `markitdown` chỉ tốt cho văn bản thuần túy, nhưng làm vỡ nát các bảng phức tạp nhiều dòng và nuốt watermark.
   - Với bảng biểu y tế phức tạp (DDI, ICD-10): **BẮT BUỘC dùng `pdfplumber`** để bóc tách theo tọa độ bảng thực tế.
3. **Lỗi mã hóa font chữ trong PDF Bộ Y Tế**:
   - Ký tự `(cid:1233)` trong font PDF thực chất là chữ **`ọ`** tiếng Việt (`tr(cid:1233)ng` ➔ `trọng`, `h(cid:1233)c` ➔ `học`). Đã được tự động map trong các script xử lý.
4. **Watermark chữ ký số điện tử**:
   - Trong file `Huong_dan_CD_v__DT_VPMPCD_singed_5fa4c.pdf`, watermark chữ ký số chạy dọc lề trang sử dụng font duy nhất là **`Times-Roman`** (có dấu gạch ngang), trong khi nội dung chính là `Times New Roman`. Lọc bỏ `fontname == 'Times-Roman'` sẽ làm sạch 100% watermark.
5. **Chính sách ToS & Customizations của Antigravity**:
   - KHÔNG cài đặt hoặc dùng token Google qua các wrapper/proxy bên thứ ba (như OpenCode, OpenClaw).
   - Antigravity hỗ trợ native MCP và Rules qua `AGENTS.md`. Hệ thống handover này là 100% native markdown, hoàn toàn an toàn và tuân thủ ToS.

---

## 4. Nhiệm Vụ Cụ Thể Tiếp Theo Cho Phiên Tới (Immediate Next Tasks)

Khi mở phiên làm việc mới, agent hãy bắt đầu ngay vào **Phase 2 (Database Schema & Chunking)**:

1. **Tạo Schema CSDL PostgreSQL + `pgvector`**:
   - Kết nối DB hoặc viết file Flyway Migration / DDL SQL:
     - Tạo extension: `CREATE EXTENSION IF NOT EXISTS vector;`
     - Tạo bảng `clinical_guideline_documents` (thông tin phác đồ: QĐ 4800, QĐ 5948, năm ban hành, chuyên khoa).
     - Tạo bảng `clinical_guideline_chunks` (chứa `document_id`, `section_title`, `content TEXT`, `metadata JSONB`, `embedding VECTOR(768)` cho Google text-embedding-004 hoặc `VECTOR(1024)` cho BGE-M3, và `tsv_content TSVECTOR`).
     - Tạo HNSW Index cho cosine vector và GIN Index cho full-text search.
2. **Cập nhật sơ đồ DBML**:
   - Bổ sung cấu trúc 2 bảng trên vào `.planning/ai-service/ai-service-db.dbml` (tuân thủ quy tắc DB Schema của dự án).
3. **Viết script Semantic Chunking (`scripts/ingest_clinical_rag.py`)**:
   - Đọc 3 file phác đồ Markdown (`tang_huyet_ap.md`, `dai_thao_duong_tip2.md`, `viem_phoi_cong_dong.md`) và bảng DDI.
   - Chunking theo cấu trúc phân cấp (cắt theo tiêu đề `##`, `###`, độ dài 400 - 800 tokens, tự động chèn tiền tố *Breadcrumbs Context* vào đầu mỗi chunk).
   - Nhúng vector và nạp vào bảng `clinical_guideline_chunks`.
4. **Tích hợp Backend `careflow-ai-service`**:
   - Bổ sung dependency `pgvector` vào `careflow-ai-service/pom.xml`.
   - Xây dựng Repository thực thi câu truy vấn **Hybrid Search (Vector Cosine + Sparse BM25 với Reciprocal Rank Fusion k=60)**.

---

## 5. Tệp Tham Khảo Quan Trọng (Key File References)

- **Tài liệu kiến trúc AI & RAG đầy đủ**: [`docs/ai-service/clinical-ai-architecture-and-rag-implementation-plan.md`](./ai-service/clinical-ai-architecture-and-rag-implementation-plan.md)
- **Hợp đồng dịch vụ AI**: `docs/service-contracts/12-ai-clinical-assistant.md` (CF-SVC-12)
- **Quy tắc chung của dự án**: [`AGENTS.md`](../AGENTS.md)
- **Mã bệnh ICD-10 đầy đủ**: [`docs/rag-docs/icd10/icd10_dictionary.csv`](./rag-docs/icd10/icd10_dictionary.csv)
- **Tương tác thuốc chống chỉ định**: [`docs/rag-docs/ddi/ddi_contraindicated.csv`](./rag-docs/ddi/ddi_contraindicated.csv)
- **Phác đồ điều trị sạch**: [`docs/rag-docs/treatment-protocol/markdown/`](./rag-docs/treatment-protocol/markdown/)
