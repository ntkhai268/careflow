# CareFlow — Thư Viện Tài Liệu Trực Quan HTML (Visual HTML Hub)

Thư mục này tập trung toàn bộ các tài liệu kiến trúc, cơ chế kỹ thuật và hướng dẫn trực quan dạng **HTML độc lập (Standalone HTML)** của hệ thống CareFlow. Các tệp này có thể mở trực tiếp bằng bất kỳ trình duyệt nào mà không cần web server.

---

### Danh Mục Tài Liệu Trực Quan (Theo Thứ Tự Kiến Trúc)

| STT | Tên Tệp HTML | Tiêu Đề & Nội Dung Chi Tiết |
| :---: | :--- | :--- |
| **01** | [`01-architecture.html`](./01-architecture.html) | **Sơ Đồ Kiến Trúc Toàn Cảnh (Architecture Diagram)**<br>Toàn cảnh hệ thống: Next.js Frontend, Zustand Store, Spring Boot AI Service, Voice STT, pgvector Hybrid Search & Gemini Cloud. |
| **02** | [`02-rag-mechanism.html`](./02-rag-mechanism.html) | **Cơ Chế Hoạt Động Của Clinical RAG (RAG Mechanism)**<br>Chu trình 3 bước khép kín: Ingestion dữ liệu BYT $\rightarrow$ Hybrid Retrieval $\rightarrow$ Generation chống Hallucination. |
| **03** | [`03-chunking.html`](./03-chunking.html) | **Chiến Thuật Phân Đoạn Tri Thức (Chunking Strategy)**<br>Contextual Document-Structure Chunking, Window Guard (400-800 tokens), bảo toàn bảng liều lượng & tiêm Breadcrumb. |
| **04** | [`04-hybrid-search.html`](./04-hybrid-search.html) | **Hiện Thực Hóa Dense & Sparse Search (Hybrid Search)**<br>Chi tiết kỹ thuật kết hợp HNSW Cosine Vector Search (`<=>`), BM25 GIN Fulltext và JSONB filtering trong PostgreSQL. |
| **05** | [`05-rrf-guide.html`](./05-rrf-guide.html) | **Chuyên Đề Thuật Toán RRF (Reciprocal Rank Fusion)**<br>Công thức toán học $RRF\_Score(d) = \sum \frac{1}{k + r_m(d)}$ với $k=60$ và bảng ví dụ từng bước hợp nhất thứ hạng. |
| **06** | [`06-db-import.html`](./06-db-import.html) | **Hướng Dẫn Nạp CSDL RAG Cục Bộ (DB Import)**<br>Hướng dẫn import trực tiếp 1.095 chunks từ file dump SQL vào Docker `careflow_ai` trong 3-5 giây không tốn API key. |
| **07** | [`07-implementation-plan.html`](./07-implementation-plan.html) | **Kế Hoạch Triển Khai Chi Tiết (Implementation Plan)**<br>Lộ trình kỹ thuật 5 Phase của phân hệ AI Lâm Sàng & Clinical RAG. |
| **08** | [`08-technical-report.html`](./08-technical-report.html) | **Báo Cáo Kỹ Thuật Chuyên Sâu (Technical Report - Section 2)**<br>Báo cáo thông số kỹ thuật, đánh giá hiệu năng tìm kiếm, latency và độ phủ tri thức y khoa. |
| **09** | [`09-retrieval-benchmark-deck.html`](./09-retrieval-benchmark-deck.html) | **Slide Thuyết Trình Phản Biện Vector Embedding (PPT Slide Deck)**<br>Slide deck tương tác: Phân tích 3 điểm mù của Gemini, dẫn chứng dữ liệu thực tế từ CSDL `careflow_ai`, bảng số liệu Benchmark A/B Test và kịch bản thuyết trình. |

---

### Cách Sử Dụng
- Nhấp đúp chuột vào bất kỳ tệp `.html` nào ở trên để mở bằng trình duyệt (Chrome, Safari, Edge, Firefox).
- Hoặc mở nhanh bằng terminal: `xdg-open docs/html/01-architecture.html` (Linux) hoặc `open docs/html/01-architecture.html` (macOS).
