# Cơ Chế Hoạt Động Của Hệ Thống Clinical RAG

Tài liệu mô tả quy trình kỹ thuật từng bước của hệ thống Retrieval-Augmented Generation (RAG) đang hoạt động trong `careflow-ai-service`.

---

## 1. Dữ Liệu Nguồn & Tiền Xử Lý (Data Ingestion)

1. **Nguồn văn bản**: Các phác đồ điều trị, danh mục bệnh ICD-10 và bảng tương tác thuốc chống chỉ định (DDI) của Bộ Y Tế.
2. **Phân đoạn văn bản (Chunking)**:
   - Kích thước mỗi đoạn: 400 – 800 tokens, có chồng lấn 50 – 80 tokens giữa các đoạn liền kề.
   - Gắn kèm thông tin ngữ cảnh phân cấp: `[Tên tài liệu > Chuyên khoa > Đề mục]`.
3. **Vector hóa & Lưu trữ**:
   - Chuyển đổi nội dung thành vector nhúng 768 chiều (Google Gemini Embedding).
   - Lưu trữ vào PostgreSQL 16 (extension `pgvector`) gồm: cột vector `embedding` (đánh chỉ mục HNSW) và cột toàn văn `tsv_content` (đánh chỉ mục GIN).

---

## 2. Quy Trình Xử Lý Truy Vấn (Query & Retrieval Pipeline)

Khi nhận yêu cầu từ phiên khám bệnh, hệ thống thực hiện tuần tự qua 4 bước:

```
[Bối cảnh ca khám / Câu hỏi]
             │
             ├──► 1. Chuyển thành vector 768 chiều
             │
             ├──► 2. Truy xuất song song:
             │       ├─ Dense Search (HNSW Cosine): Tìm 20 đoạn gần nghĩa nhất
             │       └─ Sparse Search (BM25 Full-text): Tìm 20 đoạn khớp từ khóa
             │
             ├──► 3. Xếp hạng lại (Reciprocal Rank Fusion - RRF, k=60):
             │       └─ Lấy Top 5 đoạn có điểm số cao nhất
             │
             └──► 4. Sinh phản hồi (LLM Gemini 2.5 Flash):
                     ├─ Đưa Top 5 đoạn vào làm căn cứ
                     ├─ Bổ sung tiền sử, dị ứng và thuốc đang kê
                     └─ Trả về khuyến nghị kèm mã văn bản trích dẫn
```

### Chi tiết các bước:

- **Bước 1 — Embedding câu hỏi**: Văn bản truy vấn được gửi đến mô hình embedding để tạo vector 768 chiều.
- **Bước 2 — Tìm kiếm kết hợp (Hybrid Search)**:
  - *Vector Search*: Sử dụng khoảng cách Cosine (`<=>`) trên chỉ mục HNSW để tìm các đoạn có ngữ nghĩa tương đồng.
  - *Full-text Search*: Dùng truy vấn `plainto_tsquery` trên chỉ mục GIN để tìm chính xác tên bệnh, triệu chứng hoặc tên thuốc.
- **Bước 3 — Hợp nhất và xếp hạng (RRF)**:
  - Áp dụng công thức:
    $$RRF\_Score(d) = \sum_{m \in \{Vector, BM25\}} \frac{1}{60 + r_m(d)}$$
  - Chọn 5 đoạn tri thức có tổng điểm cao nhất làm ngữ cảnh tham chiếu.
- **Bước 4 — Sinh nội dung có đối chiếu (Augmented Generation)**:
  - Ghép 5 đoạn tài liệu cùng dữ liệu ca khám (tuổi, triệu chứng, tiền sử, dị ứng, thuốc dự kiến) vào cấu trúc prompt cố định.
  - Mô hình ngôn ngữ tổng hợp khuyến nghị điều trị, cảnh báo chống chỉ định và đính kèm số quyết định của phác đồ làm bằng chứng.
