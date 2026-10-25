# Hướng Dẫn Thiết Lập & Nạp CSDL Clinical RAG Cục Bộ (Manual RAG Import Guide)

> **Tài liệu Kỹ thuật Dành Cho Thành Viên Dự Án CareFlow**  
> **Phân hệ**: AI Lâm Sàng & Clinical RAG (`careflow-ai-service`)  
> **Cập nhật**: 24/10/2026  
> **Mục đích**: Hướng dẫn chi tiết từng bước cho các thành viên trong nhóm thiết lập môi trường CSDL Vector (`pgvector`), nạp đầy đủ 1.095 chunks tri thức Phác đồ Bộ Y Tế & Dược thư Quốc gia trên máy local để chạy và kiểm thử tính năng RAG.

---

## 1. Quy Định Về Git: Tài Liệu `.md` và `.csv` Có Được Push Không?

> [!IMPORTANT]
> **CÂU TRẢ LỜI LÀ: HOÀN TOÀN CÓ THỂ VÀ BẮT BUỘC NÊN PUSH!**

* **Các file `.md` và `.csv` được phép push lên Git:**
  * Toàn bộ phác đồ điều trị định dạng Markdown sạch: `docs/rag-docs/treatment-protocol/markdown/*.md` (Đái tháo đường típ 2, Tăng huyết áp, Viêm phổi cộng đồng).
  * Toàn bộ danh mục tương tác thuốc DDI: `docs/rag-docs/ddi/*.csv` (701 cặp tương tác chống chỉ định từ QĐ 5948/QĐ-BYT).
  * Danh mục thuốc generic & ICD-10: `docs/rag-docs/drug-formulary/*.csv` và `docs/rag-docs/icd10/*.csv`.
  * *Lý do:* Đây là dữ liệu tri thức y khoa nguồn mở, dung lượng nhẹ, định dạng văn bản chuẩn (dễ diff và review trên GitHub), là "Single Source of Truth" để bất kỳ thành viên nào kéo code về cũng có thể nạp RAG.
* **Các file ĐÃ ĐƯỢC CHẶN tự động qua `.gitignore` (Tuyệt đối KHÔNG commit):**
  * File văn bản thô nhị phân kích thước lớn: `docs/rag-docs/**/*.pdf`, `docs/rag-docs/**/*.xlsx`.
  * File cache vector trung gian: `scripts/.rag_embeddings_cache*.json`.
  * File cấu hình bí mật: `.env`, `careflow-ai-service/.env` (chứa `GEMINI_API_KEY`).

---

## 2. Yêu Cầu Môi Trường (Prerequisites)

1. **Docker Desktop / Docker Engine** (đang chạy trên máy).
2. **Java 21 & Maven 3.9+** (để biên dịch và chạy `careflow-ai-service`).
3. **PostgreSQL Client (`psql`)** hoặc công cụ DBeaver (tùy chọn để kiểm tra dữ liệu).
4. **Python 3.10+** (chỉ cần nếu bạn muốn tự chạy script Ingestion từ file nguồn Markdown/CSV).

---

## 3. Bước 1: Khởi Tạo CSDL Vector `pgvector` (Docker)

CareFlow AI Service sử dụng CSDL **PostgreSQL 16 tích hợp sẵn extension `pgvector`**, hoạt động trên cổng riêng biệt **`5433`** (để không xung đột với PostgreSQL Core của hệ thống trên cổng `5432`).

Chạy lệnh Docker sau trong terminal:

```bash
docker run -d \
  --name careflow-ai-postgres \
  -p 5433:5432 \
  -e POSTGRES_USER=careflow \
  -e POSTGRES_PASSWORD=careflow \
  -e POSTGRES_DB=careflow_ai \
  --restart unless-stopped \
  pgvector/pgvector:pg16
```

Kiểm tra container đang chạy:
```bash
docker ps --filter "name=careflow-ai-postgres"
```
*(Nếu đã tạo container trước đó nhưng đang tắt, bạn chỉ cần chạy: `docker start careflow-ai-postgres`)*.

---

## 4. Bước 2: Nạp Dữ Liệu Vector (Chọn 1 trong 2 cách)

### Cách A (Khuyên dùng — Siêu tốc < 5 giây): Restore trực tiếp từ file SQL Dump

Dự án đã xuất sẵn bản snapshot đầy đủ toàn bộ Schema, 4 tài liệu cha, **1.095 chunks lâm sàng (đã nhúng sẵn vector 768 chiều)** và toàn bộ chỉ mục HNSW/GIN tại tệp `scripts/careflow_ai_schema_and_data.sql`.

Chỉ cần chạy 1 dòng lệnh sau từ thư mục gốc dự án:

```bash
docker exec -i careflow-ai-postgres psql -U careflow -d careflow_ai < scripts/careflow_ai_schema_and_data.sql
```

> [!TIP]
> **Ưu điểm của Cách A:** 
> - Hoàn tất trong 3–5 giây.
> - **Không cần gọi API Google Gemini**, không lo hết quota (Rate Limit) hay tốn chi phí embedding.
> - CSDL lập tức sẵn sàng 100% để test RAG ngay.

---

### Cách B: Nạp lại từ đầu qua Script Ingestion Python (Dành cho người chỉnh sửa phác đồ)

Nếu bạn bổ sung thêm phác đồ Markdown mới hoặc cập nhật file CSV tương tác thuốc, hãy chạy quy trình Ingestion tự động:

1. **Cài đặt thư viện Python:**
   ```bash
   pip install requests psycopg2-binary pgvector google-genai
   ```
2. **Cấu hình API Key trong file `careflow-ai-service/.env`:**
   ```env
   GEMINI_API_KEY=AIzaSy... (API Key của bạn)
   ```
3. **Chạy script Ingestion:**
   ```bash
   python3 scripts/ingest_clinical_rag.py
   ```
   Script sẽ tự động:
   - Kết nối vào PostgreSQL port `5433`.
   - Phân đoạn ngữ nghĩa tài liệu Markdown theo Breadcrumb Header (`#`, `##`, `###`).
   - Bóc tách 701 cặp tương tác thuốc DDI.
   - Gọi mô hình `models/gemini-embedding-2-preview` (hoặc `text-embedding-004`) để sinh vector 768 chiều.
   - Lưu cache an toàn và nạp toàn bộ vào bảng `clinical_guideline_chunks`.

---

## 5. Bước 3: Xác Thực Dữ Liệu Đã Nạp Thành Công

Chạy câu lệnh kiểm tra số lượng bản ghi trong database `careflow_ai`:

```bash
docker exec -it careflow-ai-postgres psql -U careflow -d careflow_ai -c "
SELECT 
    (SELECT COUNT(*) FROM clinical_guideline_documents) AS total_documents,
    (SELECT COUNT(*) FROM clinical_guideline_chunks) AS total_chunks,
    (SELECT COUNT(*) FROM clinical_guideline_chunks WHERE embedding IS NOT NULL) AS chunks_with_vector;
"
```

**Kết quả kỳ vọng chuẩn:**
```text
 total_documents | total_chunks | chunks_with_vector 
-----------------+--------------+--------------------
               4 |         1095 |               1095
```

Thử nghiệm một câu truy vấn tìm kiếm vector cosine (`<=>`) trực tiếp trên DB:
```bash
docker exec -it careflow-ai-postgres psql -U careflow -d careflow_ai -c "
SELECT document_id, section_title 
FROM clinical_guideline_chunks 
WHERE section_title ILIKE '%đái tháo đường%' 
LIMIT 3;
"
```

---

## 6. Bước 4: Khởi Chạy & Kiểm Thử Trên `careflow-ai-service`

### 1. Kiểm tra cấu hình kết nối:
Tệp `careflow-ai-service/src/main/resources/application-local.yml` đã được cấu hình mặc định sẵn sàng kết nối tới Docker port `5433`:
```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5433/careflow_ai
    username: careflow
    password: careflow
```

### 2. Chạy bộ kiểm thử tự động (Unit & Integration Tests):
```bash
mvn -pl careflow-ai-service test
```
*Kết quả:* **13/13 tests PASS 100%** (bao gồm `ClinicalHybridSearchServiceTest`, `ClinicalGuidelineChunkRepositoryTest`, `AiClinicalAssistantServiceTest`...).

### 3. Khởi động hệ thống để test thực tế:
Chạy script khởi động cụm phân hệ AI:
```bash
./start-local.sh ai
```
Sau khi khởi động xong:
- **Doctor Web Frontend:** [http://localhost:3000](http://localhost:3000) (Đăng nhập: `doctor@careflow.com` / `Password123@`).
- Vào màn hình ca khám bất kỳ ➔ Chuyển sang Tab **"Trợ lý Lâm sàng (Phác đồ BYT)"** để test tra cứu gợi ý phác đồ và cảnh báo tương tác thuốc DDI.

---

## 7. Khắc Phục Sự Cố Thường Gặp (Troubleshooting)

| Lỗi gặp phải | Nguyên nhân | Cách khắc phục |
| :--- | :--- | :--- |
| `Bind for 0.0.0.0:5433 failed: port is already allocated` | Cổng 5433 đã bị tiến trình khác chiếm dụng. | Kiểm tra tiến trình chiếm cổng: `ss -tuln \| grep 5433` hoặc dừng container cũ: `docker stop <container_id>`. |
| `psql: error: connection to server ... failed` | Container `careflow-ai-postgres` đang tắt. | Chạy `docker start careflow-ai-postgres`. |
| `Extension "vector" does not exist` | Dùng sai image PostgreSQL thường thay vì `pgvector`. | Dùng đúng image `pgvector/pgvector:pg16` như lệnh ở Bước 1. |
| `No Resource Found / 404 on /api/ai/...` | Gọi nhầm prefix URL. | Prefix đúng của AI Service là `/api/ai/...` (ví dụ: `/api/ai/clinical-suggestions`, `/api/ai/text-to-clinical-fields`). |
