# Clinical RAG DB Configuration, Entities & Repositories Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Cấu hình kết nối Spring Boot `careflow-ai-service` tới CSDL PostgreSQL `pgvector` (port 5433, db `careflow_ai`), xây dựng các JPA Entities / Projections và Native Query Repository hỗ trợ tìm kiếm HNSW Vector (`<=>`) và Sparse BM25 (`tsv_content`).

**Architecture:** Sử dụng Spring Data JPA kết hợp Native SQL Queries để tận dụng tối đa sức mạnh của extension `pgvector` và GIN tsvector mà không làm phức tạp hóa Hibernate dialect. Embedding query vector được truyền dưới dạng chuỗi float array và cast sang kiểu `vector` trực tiếp trong PostgreSQL.

**Tech Stack:** Java 21, Spring Boot 3.3.x, Spring Data JPA, PostgreSQL Driver 42.x, pgvector 0.8.7, PostgreSQL 16.

**Spec:** [`docs/service-contracts/12-ai-clinical-assistant.md`](../../service-contracts/12-ai-clinical-assistant.md) và [`docs/ai-service/clinical-ai-architecture-and-rag-implementation-plan.md`](../../ai-service/clinical-ai-architecture-and-rag-implementation-plan.md).

## Global Constraints
- CSDL mục tiêu: PostgreSQL 16 + pgvector chạy tại `localhost:5433` (DB: `careflow_ai`, User: `careflow`, Pass: `careflow`).
- `spring.jpa.hibernate.ddl-auto: none` (Tuyệt đối không để Hibernate tự động sinh hoặc sửa đổi DDL của các bảng và chỉ mục HNSW/GIN đã tối ưu hóa).
- Giữ nguyên cấu trúc bảng đã được nạp 1.095 chunks tại Phase 2: `clinical_guideline_documents`, `clinical_guideline_chunks`, `ai_clinical_interactions`.
- Các câu truy vấn vector phải sử dụng toán tử `<=>` (Cosine Distance) kết hợp chỉ mục HNSW `idx_clinical_chunks_hnsw`.
- Tất cả commit message phải viết bằng tiếng Anh (`feat: ...`, `fix: ...`). Không tự ý commit khi chưa có yêu cầu.

## GitNexus Impact Assessment
- **Index freshness**: Đã cập nhật chỉ mục mới nhất (`npx gitnexus analyze`, 9.540 nodes, 20.176 edges).
- **Target Symbols**: `AiClinicalAssistantService`, `AiClinicalController`.
- **Upstream Callers**: Chỉ có `AiClinicalController` gọi trực tiếp `AiClinicalAssistantService`.
- **Processes Affected**: Không có process liên service nào bị gián đoạn (0 affected external flows).
- **Risk Level**: **LOW** (Các thay đổi nằm trọn vẹn trong module `careflow-ai-service`).

## Review Focus
1. **PostgreSQL Vector Casting**: Kiểm tra câu lệnh native query `cast(:embedding as vector)` hoặc `:embedding::vector` tương thích tốt với JDBC parameter binding mà không gặp lỗi cú pháp Hibernate.
2. **JSONB & Array Types**: Xử lý cột `metadata` (JSONB) và danh sách mã ICD-10/thuốc thông qua String/Jackson hoặc Projection interface nhẹ nhàng, tránh lỗi mapping `No Dialect mapping for JDBC type: 1111`.
3. **Connection Resilience**: `careflow-ai-service` phải khởi động mượt mà ngay cả khi container CSDL vector khởi động chậm hoặc đang kết nối lại.
4. **Cosine Distance vs Similarity Score**: Chuyển đổi chính xác từ Cosine Distance $d \in [0, 2]$ sang Cosine Similarity $S = 1 - d$ để điểm tương đồng nằm trong thang $[0, 1]$.
5. **Full-Text Tokenizer**: Sử dụng đúng từ điển `simple` trong `plainto_tsquery('simple', :query)` để khớp chính xác với chỉ mục `idx_clinical_chunks_tsv`.

---

## Task Structure

### Task 1: Cấu hình Maven Dependencies & DataSource
**Files:**
- Modify: `careflow-ai-service/pom.xml`
- Modify: `careflow-ai-service/src/main/resources/application.yml`
- Test: `careflow-ai-service/src/test/java/com/careflow/ai/config/DataSourceConnectionTest.java`

- [ ] **Step 1.1: Viết test kiểm tra kết nối DataSource thất bại (TDD Red)**
  Tạo `DataSourceConnectionTest.java` kiểm tra `DataSource` bean tồn tại và có thể lấy connection tới database `careflow_ai`.
- [ ] **Step 1.2: Chạy test để xác nhận test FAIL**
  Chạy `mvn test -Dtest=DataSourceConnectionTest` trong `careflow-ai-service` -> Fail vì thiếu JPA và PostgreSQL driver.
- [ ] **Step 1.3: Cập nhật `pom.xml` và `application.yml` (TDD Green)**
  - Bỏ exclusion `spring-boot-starter-data-jpa` hoặc thêm dependency `spring-boot-starter-data-jpa`.
  - Thêm `org.postgresql:postgresql`.
  - Cấu hình `spring.datasource` và `spring.jpa` trong `application.yml`:
    ```yaml
    spring:
      datasource:
        url: ${AI_DB_URL:jdbc:postgresql://localhost:5433/careflow_ai}
        username: ${AI_DB_USER:careflow}
        password: ${AI_DB_PASS:careflow}
        driver-class-name: org.postgresql.Driver
      jpa:
        hibernate:
          ddl-auto: none
        properties:
          hibernate:
            format_sql: false
            jdbc:
              time_zone: Asia/Ho_Chi_Minh
    ```
- [ ] **Step 1.4: Chạy test để xác nhận PASS**
  Chạy `mvn test -Dtest=DataSourceConnectionTest` -> Pass kết nối thành công.

---

### Task 2: Định nghĩa JPA Entities & Projection Interfaces
**Files:**
- Create: `careflow-ai-service/src/main/java/com/careflow/ai/entity/ClinicalGuidelineDocument.java`
- Create: `careflow-ai-service/src/main/java/com/careflow/ai/entity/ClinicalGuidelineChunk.java`
- Create: `careflow-ai-service/src/main/java/com/careflow/ai/entity/AiClinicalInteraction.java`
- Create: `careflow-ai-service/src/main/java/com/careflow/ai/repository/projection/ChunkSearchResultProjection.java`
- Test: `careflow-ai-service/src/test/java/com/careflow/ai/entity/EntityMappingTest.java`

- [ ] **Step 2.1: Viết test kiểm tra mapping Entity thất bại (TDD Red)**
  Viết test kiểm tra có thể map và truy vấn metadata của `ClinicalGuidelineDocument`.
- [ ] **Step 2.2: Chạy test để xác nhận FAIL**
  Chạy `mvn test -Dtest=EntityMappingTest` -> Compile error / Class not found.
- [ ] **Step 2.3: Tạo các JPA Entities và Projection (TDD Green)**
  - `ClinicalGuidelineDocument`: `@Entity`, `@Table(name = "clinical_guideline_documents")`, các trường `id`, `code`, `title`, `issuer`, `publishedYear`, `specialty`, `sourceUrl`, `isActive`.
  - `ClinicalGuidelineChunk`: `@Entity`, `@Table(name = "clinical_guideline_chunks")`, các trường `id`, `documentId`, `sectionTitle`, `content`, `metadata` (dạng String/JSON), `createdAt`. (Trường `embedding` và `tsv_content` được quản lý qua native query để tránh xung đột Hibernate type).
  - `AiClinicalInteraction`: `@Entity`, `@Table(name = "ai_clinical_interactions")`, lưu lịch sử hỏi đáp và audit trail theo CF-SVC-12.
  - `ChunkSearchResultProjection`: Interface Spring Data Projection định nghĩa các getter: `getId()`, `getDocumentId()`, `getDocumentCode()`, `getDocumentTitle()`, `getSectionTitle()`, `getContent()`, `getMetadata()`, `getSimilarityScore()`.
- [ ] **Step 2.4: Chạy test để xác nhận PASS**
  Chạy `mvn test -Dtest=EntityMappingTest` -> Pass.

---

### Task 3: Xây dựng Repository hỗ trợ Native Vector & BM25 Search
**Files:**
- Create: `careflow-ai-service/src/main/java/com/careflow/ai/repository/ClinicalGuidelineDocumentRepository.java`
- Create: `careflow-ai-service/src/main/java/com/careflow/ai/repository/ClinicalGuidelineChunkRepository.java`
- Create: `careflow-ai-service/src/main/java/com/careflow/ai/repository/AiClinicalInteractionRepository.java`
- Test: `careflow-ai-service/src/test/java/com/careflow/ai/repository/ClinicalGuidelineChunkRepositoryTest.java`

- [ ] **Step 3.1: Viết test truy vấn Vector & BM25 thất bại (TDD Red)**
  Tạo `ClinicalGuidelineChunkRepositoryTest.java` kiểm tra:
  - `searchByVectorSimilarity`: Truy vấn top K chunks với vector 768 chiều mẫu (768 số 0.0 hoặc vector thực tế).
  - `searchByBm25`: Truy vấn từ khóa `"viêm phổi"` hoặc `"metformin"`.
- [ ] **Step 3.2: Chạy test để xác nhận FAIL**
  Chạy `mvn test -Dtest=ClinicalGuidelineChunkRepositoryTest` -> Fail.
- [ ] **Step 3.3: Cài đặt Native Queries trong `ClinicalGuidelineChunkRepository` (TDD Green)**
  - Câu truy vấn Vector Cosine Search:
    ```java
    @Query(value = """
        SELECT c.id AS id,
               c.document_id AS documentId,
               d.code AS documentCode,
               d.title AS documentTitle,
               c.section_title AS sectionTitle,
               c.content AS content,
               c.metadata\\:\\:text AS metadata,
               (1.0 - (c.embedding <=> cast(:embedding as vector))) AS similarityScore
        FROM clinical_guideline_chunks c
        JOIN clinical_guideline_documents d ON c.document_id = d.id
        WHERE (:specialty IS NULL OR d.specialty = :specialty)
        ORDER BY c.embedding <=> cast(:embedding as vector) ASC
        LIMIT :limit
        """, nativeQuery = true)
    List<ChunkSearchResultProjection> searchByVector(
            @Param("embedding") String embeddingString,
            @Param("specialty") String specialty,
            @Param("limit") int limit);
    ```
  - Câu truy vấn BM25 Full-text:
    ```java
    @Query(value = """
        SELECT c.id AS id,
               c.document_id AS documentId,
               d.code AS documentCode,
               d.title AS documentTitle,
               c.section_title AS sectionTitle,
               c.content AS content,
               c.metadata\\:\\:text AS metadata,
               ts_rank_cd(c.tsv_content, plainto_tsquery('simple', :query)) AS similarityScore
        FROM clinical_guideline_chunks c
        JOIN clinical_guideline_documents d ON c.document_id = d.id
        WHERE c.tsv_content @@ plainto_tsquery('simple', :query)
          AND (:specialty IS NULL OR d.specialty = :specialty)
        ORDER BY similarityScore DESC
        LIMIT :limit
        """, nativeQuery = true)
    List<ChunkSearchResultProjection> searchByBm25(
            @Param("query") String query,
            @Param("specialty") String specialty,
            @Param("limit") int limit);
    ```
- [ ] **Step 3.4: Chạy test để xác nhận PASS**
  Chạy `mvn test -Dtest=ClinicalGuidelineChunkRepositoryTest` -> Cả hai truy vấn vector và BM25 đều trả về kết quả chính xác từ 1.095 chunks thực tế trong DB.

---

### Task 4: Bổ sung Gemini Embedding Client trong `GeminiClientService`
**Files:**
- Modify: `careflow-ai-service/src/main/java/com/careflow/ai/service/GeminiClientService.java`
- Test: `careflow-ai-service/src/test/java/com/careflow/ai/service/GeminiEmbeddingTest.java`

- [ ] **Step 4.1: Viết test sinh embedding thất bại (TDD Red)**
  Test gọi `geminiClientService.generateEmbedding("bệnh nhân sốt cao ho có đờm")` trả về float array kích thước 768 hoặc null khi thiếu key.
- [ ] **Step 4.2: Cài đặt phương thức `generateEmbedding` trong `GeminiClientService` (TDD Green)**
  - Endpoint: `https://generativelanguage.googleapis.com/v1beta/models/gemini-embedding-2:embedContent?key={apiKey}`
  - Request body:
    ```json
    {
      "model": "models/gemini-embedding-2",
      "content": {"parts": [{"text": "..."}]},
      "outputDimensionality": 768
    }
    ```
  - Phân tích JSON response trích xuất `embedding.values` thành `float[]` và chuyển đổi thành định dạng `"[0.0123,-0.0456,...]"` để truyền vào native SQL query.
- [ ] **Step 4.3: Chạy test để xác nhận PASS**
  Chạy `mvn test -Dtest=GeminiEmbeddingTest` -> Pass.
