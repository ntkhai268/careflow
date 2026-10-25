# Project Rules - CareFlow

## Language & Communication Rule
- **Always Answer in Vietnamese**: **LUÔN LUÔN trả lời người dùng bằng TIẾNG VIỆT** trong mọi phản hồi, giải thích và trao đổi (trừ commit message bằng tiếng Anh).

## Session Handover & Memory Continuity Rule
- **BẮT BUỘC ĐỌC ĐẦU MỖI PHIÊN (Mandatory First Read)**: Ở đầu mỗi phiên chat mới hoặc khi bắt đầu tiếp tục công việc, agent BẮT BUỘC phải đọc tệp `docs/SESSION_HANDOVER.md` trước tiên để nắm toàn bộ tiến độ, các quyết định kiến trúc, kinh nghiệm xử lý lỗi và danh sách nhiệm vụ tiếp theo.
- **CẬP NHẬT TRƯỚC KHI KẾT THÚC (Session End Update)**: Trước khi kết thúc phiên hoặc khi hoàn thành một milestone lớn, agent BẮT BUỘC phải tóm tắt và cập nhật trạng thái mới nhất vào `docs/SESSION_HANDOVER.md`.

## Git & Work Branch Strategy
- **Prescription Feat Scope:** Only code and edit features related to Prescription on the `feature/vi/prescription-service` branch. 
- **Isolation of Features:** Keep consultation-service and prescription-service cleanly implemented. Do not implement Lab Orders or EMR logic directly on this branch. Mock external dependencies when necessary, and document them clearly.
- **Commit discipline:** Ensure focused commits so that changes can be easily cherry-picked or reverted without affecting other services.

## UI/UX & Design System Constraints
- **Subtle Rounding Rule:** Components use modern subtle rounded corners (`rounded-md` / `rounded-lg` / `rounded-xl` for cards, buttons, inputs, badges, and modals). Maintain visual harmony across all pages.
- **No Decorative Icons:** Strictly follow the design system in `docs/design.md`. Do not add icons to stat cards or decorative elements.
- **No Emojis:** Do not use emojis in UI titles (e.g. no waving hands, warning icons unless strictly warning badge style).
- **Background Grid:** Apply decorative grid backgrounds (`bg-[linear-gradient(...)]`) to the main workspace and dark sidebar using SJD colors and correct grid size (`24px x 24px`).
- **Sidebar Hover State:** Use a darker purple background `#1E1322` for sidebar menu item hovers to maintain high readability of text and icons.

## GitNexus Integration
- Run `gitnexus analyze` whenever a new feature is completed or significant structural changes are made to keep repository index updated.
- Use `gitnexus context` and `gitnexus impact` to analyze blast radius and affected execution flows before writing new features.

## Business Domain — Mandatory Reading Before Any Design

**TRƯỚC KHI thiết kế hoặc tái thiết kế bất kỳ function, class, API endpoint, state machine, hoặc event nào**, bắt buộc phải:

1. **Đọc `docs/patient-journey-and-system-workflow.md`** — Tài liệu nghiệp vụ nguồn đã được thống nhất. Hiểu rõ:
   - Giai đoạn nào trong hành trình bệnh nhân mà feature liên quan?
   - Trạng thái (state) nào hợp lệ và transition nào được phép?
   - Service nào chịu trách nhiệm gì (xem mục 19)?
   - Luồng đồng bộ vs. bất đồng bộ (xem mục 21)?
   - Quy tắc Queue FIFO, Lab và Result Review (xem mục 22)?
2. **Đọc `docs/service-contracts/README.md`** — Để nắm các quy ước HTTP, response envelope, idempotency và event chung.
3. **Đọc contract cụ thể của service** (ví dụ `docs/service-contracts/08-doctor-consultation.md`) nếu liên quan.

Nếu code và tài liệu mâu thuẫn: tạo issue, không tự ý chọn theo code cũ.

---

## GitNexus — Mandatory Pre-Planning Step

**TRƯỚC KHI tạo bất kỳ Implementation Plan nào**, bắt buộc phải thực hiện các bước sau theo thứ tự:

1. **Kiểm tra index**: Đọc `gitnexus://repo/careflow/context` để xác nhận index không stale. Nếu stale, chạy `npx gitnexus analyze` trước.
2. **Query context**: Chạy `gitnexus_query({query: "<tên tính năng hoặc khái niệm liên quan>"})` để tìm các execution flows và symbols liên quan.
3. **Impact Analysis**: Với mỗi symbol/function dự kiến sẽ thay đổi, chạy `gitnexus_impact({target: "<symbolName>", direction: "upstream"})` để xác định blast radius.
4. **Báo cáo trong Plan**: Trong Implementation Plan, phải có section **"GitNexus Impact Assessment"** tóm tắt:
   - Các symbols sẽ bị ảnh hưởng
   - Risk level (LOW / MEDIUM / HIGH / CRITICAL)
   - Upstream callers và execution flows bị tác động
5. **Chặn nếu rủi ro cao**: Nếu bất kỳ symbol nào trả về risk = HIGH hoặc CRITICAL, **phải cảnh báo rõ người dùng** và chờ xác nhận trước khi tiếp tục lên plan.

---

## Frontend — Data Fetching & Loading State Rules

**Áp dụng cho mọi trang/component có `useEffect` fetch data trong `frontend/doctor-web`.**

### 3-State Pattern (BẮT BUỘC)

Mọi trang có danh sách (list/table) PHẢI tách rõ 3 trạng thái, KHÔNG được dùng `useState<T[]>([])` làm giá trị khởi tạo:

```tsx
// ✅ ĐÚNG
const [data, setData] = useState<T[] | null>(null);   // null = chưa fetch
const [isLoading, setIsLoading] = useState(true);
const [error, setError] = useState<string | null>(null);

// ❌ SAI — dễ gây flash "Chưa có bản ghi" trước khi fetch xong
const [data, setData] = useState<T[]>([]);
```

### Thứ tự Render (BẮT BUỘC)

```tsx
{isLoading ? (
  <SkeletonRows />          // Skeleton loading — giữ layout ổn định
) : error ? (
  <ErrorBanner />           // Banner lỗi rõ ràng
) : (data ?? []).length === 0 ? (
  <EmptyState />            // "Chưa có bản ghi" — CHỈ sau khi fetch xong
) : (
  (data ?? []).map(...)     // Dữ liệu thực
)}
```

### Skeleton Loading (ƯU TIÊN)

- Dùng **Skeleton rows/cards** (`animate-pulse`) thay vì full-page spinner, trừ khi layout quá phức tạp.
- Số skeleton rows nên bằng số rows dự kiến (3-5 rows là hợp lý).
- Chiều rộng mỗi skeleton column nên khớp với nội dung thực tế (`w-10`, `w-36`, v.v.).

### useEffect Template

```tsx
useEffect(() => {
  async function fetchData() {
    setIsLoading(true);
    setError(null);
    try {
      const res = await someApi.getList();
      setData(res.data ?? []);
    } catch {
      setError("Không thể tải dữ liệu. Vui lòng thử lại.");
      setData([]);
    } finally {
      setIsLoading(false);
    }
  }
  fetchData();
}, [deps]);
```

### Null Guard trên mọi .length access

Mọi truy cập `.length`, `.map()`, `.filter()` trên state có thể `null` phải dùng `?? []`:

```tsx
// ✅ ĐÚNG
(data ?? []).length
(data ?? []).map(...)

// ❌ SAI — crash khi state = null
data.length
data.map(...)
```

### Nút Action phụ thuộc vào data

Nút action như "Gọi bệnh nhân tiếp theo" phải disabled khi đang loading:

```tsx
disabled={isCalling || isLoading || (data ?? []).length === 0}
```

## Hướng dẫn sử dụng — nguồn duy nhất

- Mọi hướng dẫn thao tác người dùng và các luồng end-to-end phải đặt trong `huong-dan-su-dung/` ở gốc repo; mục lục là `huong-dan-su-dung/README.md`.
- Khi sửa chức năng/UI/API/quyền/trạng thái/dữ liệu demo, bắt buộc rà soát và cập nhật các bước, điều kiện, kết quả mong đợi và cách lặp lại liên quan trong cùng commit/PR; ghi mốc thay đổi ở `huong-dan-su-dung/CHANGELOG.md`. Không để code và hướng dẫn lệch nhau.
- Tài khoản/mật khẩu demo công khai chỉ quản lý tại `huong-dan-su-dung/01-tai-khoan-va-du-lieu.md`; không đưa token, secret MFA, recovery code, cấu hình private hoặc thông tin người thật lên Git.
- Không tạo/copy thêm hướng dẫn sử dụng trong service, `docs/` hoặc `.planning/`; README/đường dẫn cũ chỉ dẫn tới trang chuẩn. Contract thiết kế và báo cáo kiểm thử vẫn giữ đúng thư mục chuyên môn, không coi chúng là bản hướng dẫn thay thế.
- Khi chưa kiểm tra đầy đủ một nhánh E2E hoặc code runtime chưa được phát hành cùng tài liệu, phải ghi rõ phạm vi, phiên bản và phần còn chưa nghiệm thu.
