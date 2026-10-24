# 02. Các Phép Đo Khoảng Cách & Bản Chất Cosine Similarity / Cosine Distance

> **Mục tiêu**: Hiểu cặn kẽ công thức toán học của Cosine Similarity, Cosine Distance, lý do toán tử `<=>` là lựa chọn số 1 cho RAG y tế, và sự khác biệt với Euclidean Distance (L2) & Inner Product.

---

## 1. Ba Phép Đo Khoảng Cách Phổ Biến Trong pgvector

Khi lưu trữ vector trong PostgreSQL với extension `pgvector`, hệ thống hỗ trợ 3 toán tử khoảng cách:

| Phép toán | Ký hiệu trong pgvector | Công thức toán học | Giá trị đầu ra | Ứng dụng thực tế |
|---|:---:|---|:---:|---|
| **Cosine Distance** | `<=>` | $d = 1 - \cos(\theta)$ | $[0, 2]$ | **Tối ưu nhất cho RAG/Văn bản**. Đo góc lệch giữa 2 vector, không bị ảnh hưởng bởi độ dài câu ngắn hay dài. |
| **L2 Euclidean Distance** | `<->` | $d = \sqrt{\sum_{i=1}^N (u_i - v_i)^2}$ | $[0, +\infty)$ | Đo khoảng cách đường thẳng hình học. Thường dùng cho xử lý ảnh, nhận diện khuôn mặt. |
| **Inner Product (Tích vô hướng)** | `<#>` | $d = - (u \cdot v)$ | $(-\infty, +\infty)$ | Tính toán cực nhanh nếu vector đã được chuẩn hóa độ dài về 1 (Normalized). |

---

## 2. Đi Sâu Vào Cosine Similarity & Cosine Distance

### 2.1. Công thức Toán học của Cosine Similarity
Cosine Similarity ($\cos\theta$) đo góc lệch giữa hai vector $\vec{u}$ và $\vec{v}$ trong không gian $N$ chiều:

$$\text{Cosine Similarity}(\vec{u}, \vec{v}) = \cos(\theta) = \frac{\vec{u} \cdot \vec{v}}{\|\vec{u}\| \|\vec{v}\|} = \frac{\sum_{i=1}^{N} u_i v_i}{\sqrt{\sum_{i=1}^{N} u_i^2} \sqrt{\sum_{i=1}^{N} v_i^2}}$$

- **Ý nghĩa giá trị $\cos(\theta)$**:
  - $\cos(\theta) = 1$: Hai vector cùng hướng hoàn toàn $\rightarrow$ **Ý nghĩa giống nhau 100%**.
  - $\cos(\theta) = 0$: Hai vector vuông góc $\rightarrow$ **Hoàn toàn không có liên quan gì về ngữ nghĩa**.
  - $\cos(\theta) = -1$: Hai vector ngược hướng hoàn toàn $\rightarrow$ **Ý nghĩa đối lập triệt để**.

### 2.2. Tại sao CSDL lại dùng Cosine Distance (`<=>`) thay vì Cosine Similarity?
Trong lý thuyết CSDL và các thuật toán tìm kiếm hàng xóm gần nhất (ANN - Approximate Nearest Neighbors):
- Thuật toán tìm kiếm luôn tối ưu theo bài toán **"Tìm điểm có khoảng cách nhỏ nhất"** (`ORDER BY distance ASC`).
- Vì vậy, `pgvector` định nghĩa:

$$\text{Cosine Distance} = 1 - \text{Cosine Similarity} = 1 - \cos(\theta)$$

- **Bảng tương quan giữa Khoảng cách và Độ tương đồng**:

| Quan hệ ngữ nghĩa | Góc $\theta$ | $\cos(\theta)$ (Similarity) | $1 - \cos(\theta)$ (Distance `<=>`) | Ý nghĩa trong truy vấn SQL |
|---|:---:|:---:|:---:|---|
| Hoàn toàn trùng khớp | $0^\circ$ | **1.0** | **0.0** | Khoảng cách bằng 0 $\rightarrow$ Trả về đầu tiên |
| Tương đồng rất cao | $20^\circ$ | **0.94** | **0.06** | Rất gần nhau |
| Không liên quan | $90^\circ$ | **0.0** | **1.0** | Trung tính |
| Đối lập hoàn toàn | $180^\circ$ | **-1.0** | **2.0** | Khoảng cách cực đại |

### 2.3. Quy đổi ngược lại Similarity Score trong Spring Boot
Khi truy vấn SQL, để hiển thị điểm tương đồng dạng phần trăm ($0\% - 100\%$) cho bác sĩ:
```sql
SELECT c.id, c.title,
       (1.0 - (c.embedding <=> cast(:embedding as vector))) AS similarity_score
FROM clinical_guideline_chunks c
ORDER BY c.embedding <=> cast(:embedding as vector) ASC
LIMIT 5;
```

---

## 3. Tại Sao Cosine Là "Vua" Trong RAG & Xử Lý Văn Bản Y Tế?

### Ví dụ Thực Chiến: "Bệnh nhân sốt cao" vs. "Bệnh nhân sốt cao, ho nhiều, khó thở, sốt cao kéo dài"

Giả sử một câu ngắn và một câu dài mang cùng chủ đề:
- **Vector $\vec{u}$ (Câu ngắn)**: Độ dài vector (Magnitude $\|\vec{u}\|$) nhỏ vì ít từ.
- **Vector $\vec{v}$ (Câu dài lặp từ)**: Độ dài vector (Magnitude $\|\vec{v}\|$) lớn vì nhiều từ.

```mermaid
graph TD
    Origin((Gốc tọa độ 0)) -->|u: ngắn| U["Vector Câu Ngắn (39 độ C)"]
    Origin -->|v: dài| V["Vector Đoạn Dài (39 độ C, triệu chứng...)"]
    U -.->|Euclidean Distance L2 RẤT LỚN (bị phạt vì câu dài)| V
    U ===|Góc Theta RẤT NHỎ (Cosine Distance cực gần)| V
```

- **Nếu dùng Euclidean Distance ($L2$ `<->`)**:
  - Vì câu dài có tổng bình phương tọa độ lớn hơn nhiều, khoảng cách đường thẳng giữa $\vec{u}$ và $\vec{v}$ sẽ **rất lớn**.
  - Hệ thống sẽ tưởng nhầm là hai câu này **không liên quan**, dẫn đến bỏ sót tài liệu phác đồ chi tiết.
- **Nếu dùng Cosine Distance (`<=>`)**:
  - Công thức chia cho $\|\vec{u}\| \|\vec{v}\|$ đã **triệt tiêu hoàn toàn ảnh hưởng của độ dài văn bản**.
  - Nó chỉ đo góc định hướng $\theta$. Miễn là hai văn bản cùng hướng về chủ đề *sốt cao*, điểm tương đồng vẫn đạt $>95\%$.

> [!TIP]
> **Quy tắc thiết kế CareFlow**: Mọi bảng tri thức phác đồ, tương tác thuốc DDI và triệu chứng ca khám **BẮT BUỘC dùng toán tử Cosine Distance `<=>`** kết hợp với `vector_cosine_ops` trong chỉ mục HNSW.
