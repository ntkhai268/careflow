# CareFlow — Deploy & Pre-Merge Verification Checklist

> **Quy chuẩn Kiểm định Mã nguồn Trước khi Merge vào `develop` hoặc `main`**  
> **Áp dụng cho**: Toàn bộ lập trình viên và AI Agents làm việc trên hệ thống CareFlow.  
> **Nguyên tắc cốt lõi**: Bắt buộc chạy đầy đủ các bước kiểm tra bên dưới. Chỉ khi **100% các tiêu chí đạt (PASS)** mới được phép tạo Pull Request hoặc thực hiện merge.

---

## 1. Bảng Kiểm Tra Nhanh Trước Khi Merge (Quick Checklist)

| STT | Hạng mục kiểm tra | Lệnh / Thao tác xác minh | Tiêu chuẩn Đạt (PASS) |
| :---: | :--- | :--- | :--- |
| **1** | **Git Sync & Branch** | `git fetch origin && git log HEAD..origin/develop` | Đã đồng bộ nhánh mới nhất, không xung đột |
| **2** | **Git Working Tree** | `git status` | Không có file tạm, file `.env`, file log rác |
| **3** | **Git Commit Format** | `git log -1` | Tiếng Anh, chuẩn Conventional Commits |
| **4** | **Backend Compilation** | `mvn test-compile` (hoặc `-pl <service>`) | **BUILD SUCCESS** 100%, 0 lỗi compile |
| **5** | **Backend Unit Tests** | `mvn test` (hoặc `-pl <service>`) | **100% tests PASS**, 0 failures, 0 errors |
| **6** | **Security & Auth Key** | Kiểm tra `security.caller.key` | Khóa nội bộ $\ge 32$ ký tự, không lọt secret |
| **7** | **DB & DBML Sync** | Kiểm tra file Flyway & `.dbml` | Schema khớp DBML tại `.planning/<service>/` |
| **8** | **Frontend Doctor Web** | `npm --prefix frontend/doctor-web run build` | **Compiled successfully**, 0 TypeScript errors |
| **9** | **UI/UX Guidelines** | Kiểm tra Component & State | Chuẩn Ocean Blue/Teal, 3-state pattern, không dùng `<select>` HTML |
| **10** | **Patient Mobile** *(nếu sửa)* | `cd frontend/patient-mobile && flutter test` | 0 issues analyze, 100% tests PASS |
| **11** | **GitNexus Impact** | `bunx gitnexus@latest detect-changes --scope all --repo .` | **Risk level: LOW / MEDIUM** (Không có CRITICAL chưa xử lý) |
| **12** | **Docs & Handover** | `docs/SESSION_HANDOVER.md` & `huong-dan-su-dung/` | Đã cập nhật phiên làm việc và hướng dẫn |

---

## 2. Chi Tiết Các Tầng Kiểm Định

### Tầng 1: Git & Branch Hygiene (Vệ sinh Nhánh)
* [ ] **Pull nhánh mới nhất**: Đã fetch và merge/rebase code mới nhất từ `origin/develop` trước khi hoàn tất tính năng.
* [ ] **Quy tắc bảo vệ nhánh `main`**: TUYỆT ĐỐI không push trực tiếp lên `main` hay `develop`. Mọi thay đổi phải đi qua feature branch (`feature/...`) và Pull Request.
* [ ] **Loại bỏ dữ liệu nhạy cảm**:
  * File `.env`, `application-local.yml`, `application-remote.yml` **không được commit lên Git**.
  * Không chứa secret key, token private, mật khẩu thật.
* [ ] **Commit message chuẩn mực**: Viết hoàn toàn bằng tiếng Anh (`feat: ...`, `fix: ...`, `refactor: ...`, `docs: ...`, `test: ...`).

### Tầng 2: Backend Spring Boot Verification (Chất lượng Backend)
* [ ] **Kiểm tra biên dịch**:
  ```bash
  mvn -pl <service-name> test-compile
  ```
  Phải đạt `BUILD SUCCESS`, không cảnh báo lỗi type hay lombok mapping.
* [ ] **Kiểm tra Logic & Regression Tests**:
  ```bash
  mvn -pl <service-name> test
  ```
  Toàn bộ test suites phải PASS 100%. Không được bỏ qua test `@Disabled` khi chưa có lý do chính đáng.
* [ ] **Bảo mật phiên liên dịch vụ (ActiveSession)**:
  * Các service có gọi Identity hoặc nhận request nội bộ bắt buộc phải cấu hình `security.caller.name` và `security.caller.key` (độ dài tối thiểu 32 ký tự).
* [ ] **Đồng bộ Cơ sở Dữ liệu & Flyway**:
  * Mỗi khi tạo/sửa Entity JPA: Phải có migration script Flyway (`V...__.sql`).
  * Bắt buộc cập nhật sơ đồ DBML tương ứng tại `.planning/<service_name>/<service_name>-db.dbml`.
* [ ] **Idempotent Data Seeding**:
  * Dữ liệu danh mục/master data trong `DataInitializer.java` phải kiểm tra `exists` trước khi tạo, không tạo trùng lặp gây rác DB.

### Tầng 3: Frontend Web & Mobile Verification (Chất lượng Giao diện)
* [ ] **Doctor Web Build**:
  ```bash
  npm --prefix frontend/doctor-web run build
  ```
  Phải PASS 100% không có bất kỳ lỗi TypeScript hay cú pháp Turbopack nào.
* [ ] **Quy chuẩn 3-State Pattern**:
  * Mọi trang danh sách/bảng PHẢI tách rõ: `null` (chưa fetch) $\rightarrow$ `isLoading` (Skeleton Loading) $\rightarrow$ `error` (Banner lỗi) $\rightarrow$ `data.length === 0` (Empty state) $\rightarrow$ `data.map(...)`.
  * Có null-guard `?? []` trên mọi truy cập mảng.
* [ ] **UI/UX & Master Data Combobox**:
  * Không dùng thẻ `<select>` mặc định của trình duyệt; dùng custom select/combobox có bo góc nhẹ `rounded-xl` và shadow.
  * Chỉ cho phép chọn giá trị có thực từ Master Data API, tự động đóng khi click ra ngoài (`useRef` + `mousedown`).
  * Đồng bộ đúng bảng màu CareFlow: **Ocean Medical Blue (`#0277A8` / `#00577E`) & Medical Teal (`#087F6A` / `#D9F3EC`)**.
* [ ] **Patient Mobile (nếu có chỉnh sửa)**:
  ```bash
  cd frontend/patient-mobile && flutter analyze && flutter test
  ```
  Phải đạt 0 issue analyze và toàn bộ tests PASS.

### Tầng 4: GitNexus Graph & Blast Radius Analysis (Phân tích Tác động)
* [ ] **Kiểm tra thay đổi đồ thị mã nguồn**:
  ```bash
  bunx gitnexus@latest detect-changes --scope all --repo .
  ```
* [ ] **Đánh giá rủi ro**:
  * Mức rủi ro chấp nhận được: `LOW` hoặc `MEDIUM`.
  * Nếu xuất hiện `HIGH` hoặc `CRITICAL`: Bắt buộc phải rà soát lại upstream callers và các luồng nghiệp vụ bị tác động (`processes_affected`) trước khi merge.

### Tầng 5: Tài liệu, Bàn giao & An toàn Y tế (Documentation & Handover)
* [ ] **Cập nhật Hướng dẫn sử dụng**:
  * Nếu có thay đổi giao diện, màn hình mới, trường dữ liệu hoặc quyền hạn: Cập nhật tài liệu tương ứng tại thư mục `huong-dan-su-dung/` và ghi nhận mốc thay đổi vào `huong-dan-su-dung/CHANGELOG.md`.
* [ ] **Cập nhật Bàn giao Ngữ cảnh liên phiên**:
  * Cập nhật đầy đủ công việc vừa hoàn thành, quyết định kỹ thuật và trạng thái hệ thống vào `docs/SESSION_HANDOVER.md`.
* [ ] **An toàn Y tế (Human-in-the-Loop)**:
  * Không để AI tự ý ký toa hay tự động kết thúc phiên khám; mọi hành động nhạy cảm phải qua nút xác nhận của Bác sĩ.

---

## 3. Lệnh Tắt Kiểm Tra Nhanh Toàn Diện (One-Liner Health Check)

Trước khi chuẩn bị tạo Pull Request, lập trình viên hoặc AI Agent chạy chuỗi lệnh sau tại thư mục gốc:

```bash
# 1. Kiểm tra biên dịch & test Backend (ví dụ phân hệ ai-service)
mvn -pl careflow-ai-service test

# 2. Kiểm tra biên dịch Frontend Doctor Web
npm --prefix frontend/doctor-web run build

# 3. Quét đồ thị rủi ro mã nguồn GitNexus
bunx gitnexus@latest detect-changes --scope all --repo .
```
*(Nếu cả 3 lệnh trên đều trả về kết quả màu xanh / Exit code 0 $\rightarrow$ Nhánh đủ điều kiện nghiệm thu và merge).*
