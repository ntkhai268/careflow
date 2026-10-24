# CareFlow Clinical AI & RAG — Sổ Tay Lý Thuyết Chuyên Đề (Theory Knowledge Base)

> **Thư mục lưu trữ**: `.planning/ai-service/theory/`  
> **Dự án**: CareFlow — Phân hệ AI Lâm Sàng & Clinical RAG (`careflow-ai-service`)  
> **Đi kèm**: [`.planning/ai-service/ai-service-db.dbml`](../ai-service-db.dbml) & [`.planning/ai-service/ai-service-db.md`](../ai-service-db.md)  
> **Mục đích**: Phân tách các khái niệm lý thuyết cốt lõi thành từng chuyên đề độc lập, sâu sắc và dễ tra cứu dành cho Kỹ sư AI & Backend Developer.

---

## Danh Mục Các Chuyên Đề Lý Thuyết

| STT | Tên Chuyên Đề | Tệp Chi Tiết | Tóm Tắt Nội Dung Cốt Lõi |
|:---:|---|---|---|
| **01** | **Vector Embeddings & Không Gian Ngữ Nghĩa** | [`01-vector-embeddings-and-semantic-space.md`](./01-vector-embeddings-and-semantic-space.md) | Bản chất vector, Semantic Space, Transformer Self-Attention, Mean Pooling, tại sao số chiều luôn cố định (768 chiều). |
| **02** | **Các Phép Đo Khoảng Cách & Cosine Distance** | [`02-distance-metrics-and-cosine.md`](./02-distance-metrics-and-cosine.md) | Công thức Cosine Similarity & Distance (`<=>`), so sánh với L2 Euclidean và Dot Product, lý do Cosine là "vua" trong RAG văn bản. |
| **03** | **Thuật Toán HNSW Graph Indexing** | [`03-hnsw-graph-indexing.md`](./03-hnsw-graph-indexing.md) | Tại sao B-Tree bất lực với Vector, mô hình giao thông 3 tầng HNSW, Skip-List, các tham số $m=16, ef\_construction=64$, so sánh B-Tree vs GIN vs HNSW. |
| **04** | **Tokens vs. Words & Quy Tắc Đo Đoạn Y Tế** | [`04-tokens-vs-words.md`](./04-tokens-vs-words.md) | Phân biệt Token và Từ vựng (BPE), tỷ lệ token tiếng Việt & Dược học, "tỷ lệ vàng" chunk 400 – 800 tokens. |
| **05** | **Tìm Kiếm Lai Hybrid Search & RRF Algorithm** | [`05-hybrid-search-and-rrf.md`](./05-hybrid-search-and-rrf.md) | Điểm mù của Dense Vector trong y tế, kết hợp Dense + Sparse BM25, công thức Reciprocal Rank Fusion ($k=60$) và ví dụ tính điểm từng bước. |
| **06** | **Kiến Trúc Hoàn Chỉnh Clinical RAG Pipeline** | [`06-clinical-rag-architecture.md`](./06-clinical-rag-architecture.md) | Luồng End-to-End từ Bác sĩ $\rightarrow$ Gateway $\rightarrow$ Vector DB $\rightarrow$ Gemini 2.5 Flash, 3 thành phần Prompt RAG, cấu trúc `evidenceRefs`. |
| **07** | **Kiến Trúc Ingestion Pipeline** | [`07-ingestion-pipeline-architecture.md`](./07-ingestion-pipeline-architecture.md) | Offline Python Pipeline vs Online Spring Boot Serving, cơ chế Disk Caching, Idempotency và kiểm soát rate-limit. |
| **08** | **Thuật Toán RRF & Mã Giả Java** | [`08-rrf-algorithm-origin-and-java-implementation.md`](./08-rrf-algorithm-origin-and-java-implementation.md) | Nguồn gốc bài báo SIGIR 2009 (Cormack et al.), sự áp dụng của Elasticsearch/Azure Search, lý do tự viết Java 21 và toàn bộ mã giả chi tiết. |
