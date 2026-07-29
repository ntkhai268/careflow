# Project Rules - CareFlow

## Git & Work Branch Strategy
- **Prescription Feat Scope:** Only code and edit features related to Prescription on the `feature/vi/prescription-service` branch. 
- **Isolation of Features:** Keep consultation-service and prescription-service cleanly implemented. Do not implement Lab Orders or EMR logic directly on this branch. Mock external dependencies when necessary, and document them clearly.
- **Commit discipline:** Ensure focused commits so that changes can be easily cherry-picked or reverted without affecting other services.

## UI/UX & Design System Constraints
- **Absolute Rounding Rule:** All components must use `border-radius: 0px` (flat/square design system). No exceptions for buttons, cards, inputs, badges, or avatars.
- **No Decorative Icons:** Strictly follow the design system in `docs/design.md`. Do not add icons to stat cards or decorative elements.
- **No Emojis:** Do not use emojis in UI titles (e.g. no waving hands, warning icons unless strictly warning badge style).
- **Background Grid:** Apply decorative grid backgrounds (`bg-[linear-gradient(...)]`) to the main workspace and dark sidebar using SJD colors and correct grid size (`24px x 24px`).
- **Sidebar Hover State:** Use a darker purple background `#1E1322` for sidebar menu item hovers to maintain high readability of text and icons.

## GitNexus Integration
- Run `gitnexus analyze` whenever a new feature is completed or significant structural changes are made to keep repository index updated.
- Use `gitnexus context` and `gitnexus impact` to analyze blast radius and affected execution flows before writing new features.

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

