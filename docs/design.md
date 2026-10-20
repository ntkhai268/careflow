# Design System — Hệ thống Web dành cho Bác sĩ (SJD Purple Theme)

Tài liệu định hướng thị giác cho hệ thống quản lý khám chữa bệnh CareFlow. Thiết kế được lấy cảm hứng từ phong cách của bệnh viện quốc tế **Sant Joan de Déu (SJD) Barcelona**, sử dụng tông màu tím đậm (Eggplant/Purple) làm chủ đạo kết hợp các điểm nhấn ấm áp, tạo cảm giác chuyên nghiệp, đáng tin cậy nhưng vẫn gần gũi và nhân văn.

---

### Màu sắc Thực dụng & Chuyên nghiệp (Minimal Practical Palette)

- **Nguyên tắc thiết kế tối giản:** Không màu mè, không đánh màu nổi cầu kỳ. Giao diện tập trung tối đa vào tính thực dụng, dễ đọc, nghiêm túc.
- **Màu chủ đạo:** Tím nhạt (`#F3E8F5` / `#7B4B94`) làm điểm nhấn nhẹ nhàng cho mục được chọn / header nhẹ.
- **Chữ hiển thị:** Chữ đen đậm / đen xám (`#1A1A1A` / `#2B1D30`) trên nền trắng/xám nhạt để tối ưu độ tương phản và không gây mỏi mắt cho bác sĩ.
- **Bảng viền:** Viền đơn giản (`#E2E8F0`), không dùng gradient hay shadow lòe lẹt.

---

## 2. Typography

### Font chữ

- **Font chính:** `Inter` hoặc `Be Vietnam Pro` (sans-serif) — độ rõ cao ở kích thước nhỏ.
- **Font số liệu:** `Roboto Mono` cho các chỉ số sinh hiệu (huyết áp, SpO2...) để dễ scan, căn hàng dọc chuẩn xác.

### Thang kích thước Tối giản (30% Reduced Type Scale)

> **Quy tắc giảm 30% cỡ chữ:** Để tăng mật độ hiển thị thông tin lâm sàng trên một màn hình và tránh giao diện quá to/thô, toàn bộ kích thước chữ đã được giảm 30% so với thông lệ tiêu chuẩn.

| Cấp         | Size Cũ | Size Mới (-30%) | Weight | Dùng cho                      |
| ----------- | ------- | --------------- | ------ | ----------------------------- |
| H1          | 28px    | **20px**        | 700    | Tiêu đề trang chính           |
| H2          | 22px    | **15px**        | 600    | Tiêu đề section               |
| H3          | 18px    | **13px**        | 600    | Tiêu đề card/bảng             |
| Body        | 15px    | **11px**        | 400    | Nội dung chính                |
| Body Medium | 15px    | **11px**        | 500    | Nhấn nhẹ trong đoạn văn       |
| Small       | 13px    | **10px**        | 400    | Ghi chú, timestamp, label     |
| Caption     | 12px    | **9px**         | 400    | Chú thích nhỏ nhất, đơn vị đo |

### Quy tắc Hiển thị Thông báo Lỗi & Trợ lý AI (Clean Inline Error & AI Assistant Notifications Guidelines)

- **Không dùng ô vuông / banner lớn bọc chữ:** Tuyệt đối không bọc văn bản thông báo lỗi hay cảnh báo hệ thống trong các khối khung vuông màu hồng/đỏ có viền xung quanh gây cảm giác thô kịch và kém chuyên nghiệp.
- **Dòng chữ đỏ đơn giản:** Thông báo lỗi form/input chỉ cần hiển thị dạng **một dòng text màu đỏ (`#D9381E`) đơn giản, tinh tế** nằm ngay dưới tiêu đề hoặc vị trí phát sinh lỗi.
- **Thông báo qua Trợ lý AI Floating (Góc dưới bên phải):** Mọi thông báo hệ thống, nhắc nhở điều hướng, cảnh báo lâm sàng hoặc thông tin sự kiện trong ứng dụng **bắt buộc phải do Trợ lý AI (Widget Floating cố định ở góc dưới bên phải màn hình)** phát ngôn và hiển thị bóng thoại/toast. Không sử dụng các hộp thoại popup hay banner nổi giữa màn hình gây đứt gãy trải nghiệm làm việc của Bác sĩ.

---

## 3. Spacing & Layout

- Đơn vị cơ sở: `4px` (bội số 4/8/12/16/24/32/48)
- Card padding: `20px`
- Chiều cao dòng tối thiểu trong bảng dữ liệu: `44px` (dễ scan và thao tác trên máy tính bảng)
- Bố cục chính: **sidebar cố định trái (240px)** màu tím đậm thương hiệu, khu vực làm việc chính ở bên phải có màu nền xám ấm.

---

## 4. Bo góc & Đổ bóng

| Thành phần | Bo góc | Shadow                             |
| ---------- | ------ | ---------------------------------- |
| Card       | `8px` (`rounded-lg`) | `0 1px 3px rgba(110,37,130,0.06)`  |
| Button     | `6px` (`rounded-md`) | none (dùng màu để phân biệt state) |
| Badge/Tag  | `4px` (`rounded`)    | none                               |
| Modal      | `12px` (`rounded-xl`)| `0 8px 24px rgba(110,37,130,0.12)` |

> **Nguyên tắc Bo góc Nhẹ (Subtle Rounded Corners):** Sử dụng bo góc nhẹ nhàng (`rounded-md` / `rounded-lg`) cho các component (Button, Input, Card, Modal, Dropdown) để tạo cảm giác hiện đại, mềm mại và tinh tế, giúp tăng tính thẩm mỹ nhưng vẫn giữ được sự chuyên nghiệp của môi trường y tế.

### Quy tắc Cố định Kích thước Modal (Strict Fixed Modal Height & Internal Scroll Rule)

- **Cố định chiều cao Modal:** Toàn bộ Modal trong hệ thống **bắt buộc giới hạn chiều cao tối đa (`max-h-[85vh]`)**. Tuyệt đối không để nội dung bên trong làm Modal bị phồng dài tràn màn hình.
- **Thanh cuộn nội bộ:** Phần nội dung bên trong Modal khi dài quá mức cho phép phải tự động bật con lăn cuộn dọc (`overflow-y-auto`), giữ nguyên khung Modal cố định ở trung tâm màn hình.

### Quy tắc Tinh giản Danh sách Xem trước trong Modal (Minimalist Inline List Rule)

- **Không tạo card/ô vuông lồng nhau:** Tuyệt đối không tạo các ô vuông/card trắng bọc viền từng mục dữ liệu trong danh sách xem trước của Modal gây rối mắt và tốn diện tích.
- **Dạng dòng văn bản tinh giản (Inline Text Line):** Danh sách mục (như thuốc) phải hiển thị dạng các dòng text mờ xám ngắn gọn trên 1 dòng đơn. Cấu trúc chuẩn: `1. [Tên thuốc] x[Số lượng] ([Liều dùng/ngày]) - [Mã thuốc]`.

### Quy tắc Nút bấm & Nút Xác nhận Modal (Button Styling & Color Rules)

- **Nút Xác nhận chính trong Modal:** Mọi nút xác nhận chính trong Modal **bắt buộc sử dụng màu Tím thương hiệu SJD Purple (`bg-primary-600 hover:bg-primary-700` / `#6E2582`)**, chữ màu trắng (`text-white`). Tuyệt đối không dùng màu cam hay xanh dương cho các nút xác nhận chính.
- **Quy tắc Nút bấm Tinh gọn (Button Conciseness & No Over-Informing):**
  - **Nhãn nút bấm (Button text)** bắt buộc ngắn gọn, súc tích, đi thẳng vào hành động chính (tối đa 2–4 từ).
  - **TUYỆT ĐỐI KHÔNG over-inform / viết rườm rà dài dòng:** Không chèn thuật ngữ giải thích thuật toán, ghi chú phụ hay mô tả lê thê vào nút bấm.
  - *Ví dụ ĐÚNG:* `"Gọi số"`, `"Gọi số tiếp theo"`, `"Xác nhận check-in"`, `"Bắt đầu"`, `"Xác nhận phát thuốc"`.
  - *Ví dụ SAI:* `"Gọi số phát thuốc tiếp theo (FIFO)"`, `"Gọi bệnh nhân tiếp theo (Round-Robin 1:1:1)"`, `"Bắt đầu thực hiện lấy mẫu"`, `"Xác nhận Đã phát thuốc (DISPENSED)"`.
- **Quy tắc Kích thước & Màu sắc Nút thao tác trong Bảng (Table Action Button Uniformity Rule):**
  - **Đồng nhất kích thước trong cùng 1 cột:** Các nút bấm thao tác trong cùng 1 cột của bảng bắt buộc phải có **cùng một kích thước chiều rộng (Fixed Width, ví dụ `w-20` hoặc `w-24`)**, giúp bố cục hàng cột thẳng đứng hoàn hảo.
  - **Phong cách Nút màu trắng tối giản (White Neutral Buttons):** Các nút thao tác từng dòng trong bảng sử dụng **nền màu trắng tinh giản (`bg-white border border-card-border text-[#2B1D30] hover:bg-gray-50`)**, chữ màu tối rõ ràng. Tuyệt đối không dùng các màu nền đặc hoặc màu sặc sỡ (đỏ, xanh, hồng) làm rối mắt người dùng.

### Quy tắc Ngôn ngữ & Không Chú thích Ngoặc Đơn (Language & Parenthetical Annotation Rule)

- **100% Tiếng Việt tự nhiên & Chuẩn mực:** Giao diện người dùng trên toàn bộ hệ thống (Website) bắt buộc sử dụng 100% tiếng Việt rõ ràng, chuyên nghiệp (trừ các thuật ngữ y khoa, mã tiêu chuẩn y tế hoặc tên danh mục y tế chuyên ngành).
- **TUYỆT ĐỐI KHÔNG chú thích mở ngoặc kiểu `A (chú thích A là gì)`:** 
  - Không chèn vai trò, tên tiếng Anh hay ghi chú phụ trong dấu ngoặc đơn `(...)` ở menu sidebar, tiêu đề, nút bấm hay trạng thái.
  - *Ví dụ SAI:* `"Hàng đợi khám (Doctor)"`, `"Phát thuốc (Staff)"`, `"Hàng đợi CLS (Lab Tech)"`, `"Quét QR Check-in (Staff)"`, `"Gọi lại (Recall)"`.
  - *Ví dụ ĐÚNG:* `"Hàng đợi khám"`, `"Hàng đợi phát thuốc"`, `"Hàng đợi Cận lâm sàng"`, `"Quét QR Check-in"`, `"Gọi lại"`.

### Quy tắc Modal Xác nhận Thao tác Thay đổi Trạng thái (Status Change Confirmation Modal Rule)

- **Bắt buộc Confirmation Modal**: Mọi thao tác thay đổi trạng thái của bản ghi (ví dụ: Khóa/Mở khóa tài khoản, Hủy lượt khám, Tạm dừng/Bắt đầu dịch vụ, Đổi trạng thái phòng khám, Khóa/Xóa dữ liệu...) **BẮT BUỘC phải hiển thị Modal xác nhận thao tác** trước khi gửi yêu cầu thực thi API.
- **Cấu trúc Modal Xác nhận chuẩn**:
  - **Tiêu đề**: Ngắn gọn, đi thẳng vào hành động (ví dụ: *"Xác nhận khóa tài khoản"*, *"Xác nhận hủy lượt khám"*).
  - **Nội dung cảnh báo**: Nêu rõ đối tượng bị ảnh hưởng (tên bản ghi/mã bản ghi) và tác động của thao tác.
  - **2 Nút bấm chuẩn**:
    - Nút **Hủy**: Kiểu outline/ghost xám nhẹ (`border border-card-border text-[#6A5C70] hover:bg-gray-100`).
    - Nút **Xác nhận**: Nút hành động chính màu tím SJD (`bg-[#6E2582] text-white hover:bg-[#561A66]`) hoặc màu đỏ nổi bật (`bg-rose-600 text-white hover:bg-rose-700` đối với thao tác nguy hiểm/khóa tài khoản).

### Quy tắc Ô chọn Danh mục Master (Master Data Searchable Combobox & Real Data Selection Rule)

- **Bắt buộc Combobox / Combobox Chips kết nối dữ liệu thực**: Bất kỳ ô nhập chọn dữ liệu danh mục nào do hệ thống quản lý (như Khoa chuyên môn, Phòng khám, Bác sĩ, Dịch vụ, Danh mục thuốc...) **BẮT BUỘC phải là Combobox có Chips chọn / Searchable Select**, dữ liệu gợi ý phải được fetch trực tiếp từ Real Backend API (`directoryApi`, `patientApi`...).
- **Chỉ chọn các giá trị đã tồn tại trong hệ thống**:
  - Người dùng **chỉ được phép chọn các giá trị hợp lệ đã có sẵn trong hệ thống**.
  - Việc gõ phím vào ô tìm kiếm **CHỈ có tác dụng lọc/tìm nhanh** các tùy chọn sẵn có, **TUYỆT ĐỐI KHÔNG tự do nhập chuỗi tùy ý mới**.
- **Quy trình bổ sung giá trị mới**: Nếu muốn chọn một Khoa / Phòng / Danh mục mới chưa có trong hệ thống, người dùng **bắt buộc phải qua trang Quản lý danh mục tương ứng (ví dụ `/admin/facility` để tạo Khoa mới)**, sau đó mới quay lại ô chọn để lựa chọn.
- **Cơ chế Đóng Dropdown khi Click ra ngoài (Outside Click Dismiss Rule)**:
  - Mọi menu gợi ý Dropdown của Combobox **bắt buộc phải cài đặt `useRef` + `useEffect` lắng nghe sự kiện `mousedown` để tự động đóng menu (`setShowDropdown(false)`) khi người dùng click ra ngoài khu vực ô chọn**.
  - Ngay sau khi chọn xong 1 mục hoặc bấm phím `Esc`, menu gợi ý phải đóng lại tức thì, tuyệt đối không được hiển thị treo vĩnh viễn trên giao diện.
- **Quy tắc Thiết kế Giao diện Khung chọn Custom (Custom Styled Select & Combobox Rule)**:
  - **TUYỆT ĐỐI KHÔNG dùng `<select>` mặc định của trình duyệt**: Không được để lộ dropdown góc nhọn, highlight màu cam/xanh mặc định xấu xí của trình duyệt OS.
  - **Khung chọn Custom bo góc tinh tế chuẩn Hệ thống**: Mọi ô chọn (Select / Combobox) **BẮT BUỘC phải dùng Custom UI với bo góc nhẹ (`rounded-lg` / `rounded-xl`)**, viền mảnh xám nhạt (`border border-card-border`), đổ bóng nổi mượt (`shadow-lg`), màu hover mượt tông tím SJD (`hover:bg-purple-50`), có icon mũi tên dropdown tùy chỉnh.

### Quy tắc Kiểm tra Phân quyền Trang Nghiêm ngặt (Strict Role-Based Route Guard & 403 Access Denied Rule)

- **Phân quyền tuyệt đối theo Router (Strict RBAC)**: Mọi phân vùng trang trên Frontend bắt buộc phải được kiểm tra thẩm quyền nghiêm ngặt theo đúng duy nhất vai trò của người dùng (`user.role`), không cho phép quyền Admin chạy chéo vào chuyên môn của vai trò khác:
  - Phân vùng `/admin/**`: Chỉ duy nhất vai trò `ADMIN` mới được truy cập.
  - Phân vùng `/lab/**`: Chỉ duy nhất vai trò `LAB_TECHNICIAN` mới được truy cập.
  - Phân vùng `/staff/**`: Chỉ duy nhất vai trò `STAFF` mới được truy cập.
  - Phân vùng `/dashboard/**` & `/consultation/**`: Chỉ duy nhất vai trò `DOCTOR` mới được truy cập.
- **Màn hình Cảnh báo Không có quyền truy cập (Access Denied 403 Page)**: Khi người dùng nhập trực tiếp URL hoặc click chuyển sang trang không thuộc thẩm quyền của vai trò hiện tại, hệ thống **TUYỆT ĐỐI KHÔNG cho phép xem dữ liệu**. Ngay lập tức hiển thị giao diện **"403 - Bạn không có quyền truy cập"** chuẩn SJD với:
  - Icon Ổ khóa bảo mật nổi bật trên nền hồng nhạt (`bg-rose-50 text-rose-600`).
  - Thông điệp ghi rõ vai trò yêu cầu của trang và vai trò hiện tại của tài khoản.
  - Nút bấm **"Quay về trang chính của bạn →"** đưa người dùng về đúng Bảng điều khiển mặc định theo vai trò của mình.

### Quy tắc Thiết kế Thông báo Lâm sàng Tinh tế (Clinical Informational Notice Rules)

1. **Phân cấp màu sắc theo mức độ nghiêm trọng:**
   - **Thông tin / Nhắc nhở thông thường (Info/Notice):** Nền sáng nhạt (`bg-[#EEF2FF]` hoặc `bg-[#F8FAFC]`), viền trái mảnh 3px màu tím Indigo (`border-l-3 border-[#6366F1]`), chữ xám tối (`text-slate-700`). Tuyệt đối không dùng nền đen hay màu đỏ/cam báo động cho thông tin nhắc nhở bình thường.
   - **Cảnh báo (Warning):** Nền vàng/cam nhạt (`bg-[#FEF3C7]`, `border-l-3 border-[#F59E0B]`).
   - **Báo động sinh tồn / Nguy hiểm (Critical):** Nền đỏ nhạt hoặc đỏ đậm. Nền đen/tối chỉ dành riêng cho báo động cấp độ cao nhất để không gây hiện tượng "trơ cảm giác báo động" ở Bác sĩ.
2. **Nút hành động (CTA) tinh tế:** Không dùng chữ IN HOA toàn bộ cho CTA. Dùng chữ thường nhẹ nhàng (ví dụ: _"Đến ca khám hiện tại →"_), sử dụng kiểu ghost/link button (`text-[#6366F1] hover:underline font-semibold`).
3. **Kích thước thanh thông báo siêu gọn (Compact Bar):** Chiều cao thanh thông báo thu gọn ~36–40px, icon outline nét mảnh 16px thay cho chấm màu đặc.

### Quy tắc Trợ lý AI CareFlow (Clinical AI Assistant Widget Rules)

1. **Trạng thái nghỉ (Idle state) — Tối giản, không xao nhãng lâm sàng:**
   - Neo cố định duy nhất tại góc dưới bên phải màn hình (`fixed bottom-5 right-6 z-50`). Tuyệt đối không tự di chuyển hay lang thang trên màn hình gây phân tâm khi Bác sĩ đọc hồ sơ bệnh án.
   - Nhịp thở nhẹ nhàng (`scale 1 → 1.03 → 1` trong chu kỳ 3.5s `ease-in-out`), không dùng hiệu ứng nảy (bounce) hay lắc 3D gây cảm giác thiếu nghiêm túc trong môi trường y tế.
   - Vòng hào quang mờ (pulse ring) mờ dịu (`opacity 0.15 - 0.25`, chu kỳ 4s) thể hiện trạng thái "sẵn sàng hỗ trợ".
2. **Bong bóng thoại Preview (Speech Bubble Preview):**
   - Dạng thẻ bo góc mềm (`rounded-2xl` / `14px`), nền tối mờ lịch sự, có đuôi chỉ mượt về phía linh vật AI.
   - Xuất hiện mượt từ phải qua trái (`slide-in + fade-in` 200-250ms), tự động mờ dần ẩn đi sau 6 giây. Giới hạn câu ngắn gọn 1 dòng (~40-50 ký tự).
3. **Chuyển tiếp mở Khung Chat (Pop Transition):**
   - Khung chat mở ra mượt mà từ gốc điểm neo góc dưới bên phải (`transform-origin: bottom right`, 200ms `ease-out`), không che khuất vùng làm việc trung tâm.
4. **Tránh che khuất Nút bấm Lâm sàng (Dynamic Clearance Rule):**
   - Nút Chồn AI phải tự động điều chỉnh độ cao linh hoạt theo màn hình (`transition-all duration-300`). Khi ở trang Khám bệnh (`/consultation/[id]`), tự động dịch chuyển vị trí lên cao (`bottom-20` / 80px) để **tuyệt đối không che khuất thanh công cụ tối màu và nút HOÀN TẤT PHIÊN KHÁM dưới đáy**. Khi quay về Dashboard, tự động hạ vị trí mượt mà về `bottom-5`.

### Quy tắc màu sắc hành động phá hủy (Destructive Action Color Rule)

1. **Phân cấp theo hậu quả (Impact Severity Hierarchy):**
   - **Xóa 1 mục nhỏ / thao tác tần suất cao (dòng thuốc, dòng bảng):** Mặc định KHÔNG dùng màu đỏ tô đậm. Dùng màu xám trung tính (`text-slate-500`), không bold, không gạch chân — chỉ chuyển màu đỏ nhạt khi hover (`hover:text-rose-600`). "Sửa" và "Xóa" cùng tông màu xám, phân biệt bằng icon mảnh (bút chì / thùng rác) thay vì dùng màu sắc cảnh báo gây visual fatigue cho Bác sĩ.
   - **Xóa toàn bộ / hành động phạm vi danh sách (xóa toàn bộ đơn thuốc):** Dùng dạng ghost link chữ thường nhỏ gọn (`text-[11px] text-slate-400 hover:text-rose-600`), không in hoa, không đặt ngang hàng kích thước/độ nổi bật với nút Primary CTA (`+ Thêm thuốc`). **Bắt buộc có Confirm Modal xác nhận trước khi xóa chỉ trừ những trường hợp được chỉ định**.
   - **Hành động hủy/xóa cấp hệ thống thực sự nghiêm trọng (hủy ca khám, xóa hồ sơ):** Đây là trường hợp duy nhất áp dụng màu đỏ đậm/báo động và Confirm Modal double-check.
2. **Quy tắc Typography nút hành động:** Text buttons ưu tiên chữ thường (sentence case), không in hoa (ALL CAPS), trừ các nút CTA chính trên thanh action bar cố định dưới cùng.
3. **Quy tắc Cố định Kích thước Nút thao tác trong Bảng (Fixed Table Action Button Width Rule):** Các nút hành động trong cùng một cột của bảng dữ liệu (ví dụ nút "Gọi số", "Vào khám", "Đang gọi...") **bắt buộc phải có kích thước/chiều rộng cố định đồng nhất (fixed width, ví dụ `w-20` / `80px`)**, tuyệt đối không co giãn linh hoạt theo độ dài của nhãn chữ (text length) để giữ cho giao diện bảng cân đối, ổn định và chuyên nghiệp.
4. **Quy tắc Hiển thị Trạng thái Dạng Văn bản Đơn thuần (Plain Text Status Display Rule):** Các chỉ báo trạng thái (Status indicator / Priority badge) trong danh sách và bảng dữ liệu **bắt buộc hiển thị dạng văn bản chữ màu đơn thuần (Text with color dot/bullet), không dùng khung/viền bọc màu xung quanh (No background container or border)**. Sử dụng chấm màu nhỏ (`w-1.5 h-1.5 rounded-full`) kết hợp với chữ có màu semantic tương ứng (`text-rose-600`, `text-blue-600`, `text-amber-600`) trên nền trong suốt để tránh làm rối mắt thị giác của Bác sĩ.

---

## 5. Icon (Hạn chế tối đa)

- **Nguyên tắc hạn chế:** Hạn chế tối đa việc sử dụng icon cho mục đích trang trí. Thiết kế ưu tiên sử dụng chữ viết (text label) rõ ràng để bác sĩ đọc nhanh trực tiếp thay vì phải suy đoán nghĩa của icon.
- **Trường hợp được dùng:** Chỉ dùng icon khi thực sự cần thiết để chỉ thị trạng thái cực kỳ quan trọng (như biểu tượng cảnh báo dị ứng, chỉ số sinh tồn bất thường) hoặc các hành động cốt lõi điều hướng hệ thống.
- **Định dạng:** Nếu bắt buộc dùng, sử dụng dạng **outline/line-style**, nét mảnh (stroke width 1.5–2px), kích thước nhỏ gọn (16px - 20px). Tránh dùng icon dạng tô đặc (filled) gây rối mắt trong bảng số liệu dày đặc.
- Icon trạng thái y tế bắt buộc phải tuân thủ nghiêm ngặt hệ màu semantic tương ứng.

---

## 6. Hiệu ứng Hover & Nền lưới (Interactive Details)

### Hiệu ứng Hover trên thanh Sidebar

- **Nguyên tắc đồng tone màu:** Khi rê chuột (hover) qua các mục điều hướng trong thanh Sidebar tối (`#2B1D30`), không dùng nền sáng trắng/xám để tránh làm mờ text. Thay vào đó, sử dụng một tông màu tím tối hơn (`#1E1322`) để tạo độ sâu, giúp các nhãn chữ và biểu tượng màu xám sáng (`#6A5C70`) hiển thị nổi bật và dễ đọc.

### Hiệu ứng Nền lưới trang trí (Decorative Grid Background)

- **Cấu trúc:** Để phá vỡ sự đơn điệu của các mảng màu phẳng lớn mà không gây rối mắt, các khu vực làm việc chính (Dashboard, Banner Login) sử dụng một lớp lưới vector mảnh phủ bên dưới.
- **Kích thước & Màu sắc:**
  - Trên nền tối (Banner Đăng nhập): Sử dụng lưới trắng siêu mảnh (`rgba(255,255,255,0.02)`) kích thước ô `24px x 24px`.
  - Trên nền sáng (Dashboard/Workspace): Sử dụng lưới tím xám siêu nhạt (`rgba(43,29,48,0.015)`) kích thước ô `24px x 24px`.
- Lớp lưới này luôn được thiết lập `pointer-events-none` và nằm ở lớp z-index thấp nhất để không cản trở thao tác của người dùng.

---

## 8. Nguyên tắc Mục đích Thông tin (Purpose-Driven Information Principle)

- **Lý do tồn tại của thông tin:** Tất cả các chỉ số (Stats), thẻ thông tin (Cards), danh sách và nút bấm hiển thị trên màn hình **BẮT BUỘC phải có lý do nghiệp vụ rõ ràng**, đáp ứng trực tiếp nhu cầu theo dõi và ra quyết định của bác sĩ trong ca làm việc.
- **Không dữ liệu giả / thừa:** Tuyệt đối không hiển thị các thẻ thông tin mang tính chất trang trí, dữ liệu giả lập (hardcoded mock stats như _Thời gian khám trung bình_ khi chưa có nguồn tính toán thực tế). Nếu một dữ liệu không đến từ backend thực tế hoặc không giúp ích cho quy trình khám bệnh, thông tin đó bắt buộc phải bị loại bỏ khỏi giao diện.

---

## 9. Ngôn ngữ hiển thị (Display Language)

- **Ngôn ngữ chủ đạo:** Hệ thống chỉ sử dụng tiếng Việt làm ngôn ngữ hiển thị chính trên giao diện để bác sĩ và nhân viên y tế dễ dàng tiếp cận và thao tác chính xác.
- **Quy tắc Danh xưng Giao tiếp (User Addressing Rule):** Mọi câu hỏi, thông báo, popup xác nhận và thông báo hệ thống hướng tới người dùng là Bác sĩ **bắt buộc xưng xưng là "Bác sĩ"**, tuyệt đối không dùng đại từ "bạn" (ví dụ: _"Bác sĩ có chắc chắn muốn KÝ và XÁC NHẬN đơn thuốc này?"_, _"Tài khoản của Bác sĩ đã bị khóa"_).
- **Ngoại lệ thuật ngữ chuyên ngành:** Giữ nguyên tiếng Anh đối với các từ viết tắt chuyên ngành y khoa (ví dụ: _SpO2_, _EMR_, _BMI_, _PRN_...) hoặc các thuật ngữ công nghệ/kỹ thuật không có từ dịch nghĩa tương đương chuẩn xác (ví dụ: _Vite_, _Docker_, _API Gateway_, _UI/UX_...). Tất cả các nhãn (labels), chỉ dẫn, thông báo và nút bấm thông thường khác bắt buộc phải dịch sang tiếng Việt.

---

## 8. Tiêu chí đánh giá thiết kế (Design Evaluation Criteria)

Bộ tiêu chí dùng để đánh giá và cải thiện chất lượng thiết kế của CareFlow. Mỗi tiêu chí được chấm theo thang **1–5 điểm** (1 = Rất kém / Không có, 5 = Xuất sắc, benchmark ngành).

---

### 8.1 Hiệu năng kỹ thuật (Performance)

> Nền móng của toàn bộ trải nghiệm — đặc biệt quan trọng với máy tính bệnh viện công thường có cấu hình trung bình và mạng nội bộ không ổn định.

| Mã  | Tiêu chí                            | Mức kỳ vọng            | Ghi chú                                                  |
| --- | ----------------------------------- | ---------------------- | -------------------------------------------------------- |
| P1  | **LCP** (Largest Contentful Paint)  | < 2.5s                 | Dashboard bác sĩ phải load nhanh                         |
| P2  | **CLS** (Cumulative Layout Shift)   | < 0.1                  | Bảng dữ liệu bệnh nhân không được nhảy layout khi load   |
| P3  | **INP** (Interaction to Next Paint) | < 200ms                | Click nút kê đơn/submit phải có cảm giác ngay lập tức    |
| P4  | **Bundle Size**                     | Giảm ≥ 30% từ baseline | Lazy-load các module nặng (charts, PDF viewer, calendar) |
| P5  | **PageSpeed Mobile**                | > 80                   | Bác sĩ có thể dùng tablet tại phòng khám                 |
| P6  | **PageSpeed Desktop**               | > 90                   | Màn hình chính của workstation bệnh viện                 |

**Hướng dẫn kỹ thuật:**

- Chỉ animate `transform` và `opacity` — 2 property được GPU tăng tốc, không trigger layout recalculate.
- Tránh animate `width`, `height`, `top`, `left` — gây layout thrashing, giật trên máy yếu.
- Lazy-load các component nặng (Swiper, PDF viewer, rich-text editor) chỉ load trên trang cần.
- Dùng skeleton screen thay spinner toàn trang để tránh CLS và cải thiện perceived performance.

---

### 8.2 Phân cấp thị giác (Visual Hierarchy)

> Bác sĩ cần đọc thông tin trong vài giây — phân cấp thị giác rõ ràng là yêu cầu phi thương lượng.

| Mã  | Tiêu chí                   | Đánh giá Kém                            | Đánh giá Tốt                                                                             |
| --- | -------------------------- | --------------------------------------- | ---------------------------------------------------------------------------------------- |
| V1  | **Mật độ thông tin**       | Nhồi nhét dữ liệu, không có vùng thở    | Card-based layout, vùng đọc rõ ràng                                                      |
| V2  | **Độ tương phản**          | Text trên background thiếu contrast     | Tỷ lệ tương phản ≥ 4.5:1 (WCAG AA)                                                       |
| V3  | **Typography hierarchy**   | Tất cả chữ cùng cỡ, không phân biệt cấp | H1 > H2 > Body rõ ràng — tuân thủ thang chữ ở mục 2                                      |
| V4  | **Color coding nhất quán** | Màu tùy tiện, không có hệ thống         | Đỏ = khẩn cấp, Xanh lá = ổn định, Vàng = cần chú ý — nhất quán toàn hệ thống (xem mục 1) |
| V5  | **Whitespace**             | Không có khoảng trống                   | Spacing hệ thống bội số 4px giúp mắt nghỉ ngơi                                           |
| V6  | **Icon legibility**        | Icon nhỏ, nghĩa mơ hồ                   | Dùng text label là chủ đạo — icon chỉ là hỗ trợ (xem mục 5)                              |

---

### 8.3 Trải nghiệm sử dụng (UX / Usability)

> Bác sĩ không có thời gian học UI phức tạp — workflow phải tự giải thích được.

| Mã  | Tiêu chí                 | Câu hỏi kiểm tra                                                         |
| --- | ------------------------ | ------------------------------------------------------------------------ |
| U1  | **Task completion time** | Bác sĩ mất bao nhiêu click để kê đơn 1 thuốc? (< 5 click là tốt)         |
| U2  | **Error prevention**     | Form có validate inline không? Có confirmation trước khi delete không?   |
| U3  | **Findability**          | Người dùng mới có tìm thấy tính năng trong < 30 giây không?              |
| U4  | **Navigation clarity**   | Có breadcrumb không? Biết mình đang ở đâu trong hệ thống không?          |
| U5  | **Search quality**       | Tìm kiếm bệnh nhân theo tên/ID có instant results không?                 |
| U6  | **Feedback loop**        | Sau khi submit form, có loading state + success/error message không?     |
| U7  | **Empty states**         | Khi không có dữ liệu, có message hướng dẫn thay vì màn hình trắng không? |

---

### 8.4 Khả năng tiếp cận (Accessibility)

> Môi trường bệnh viện công có đa dạng người dùng — bao gồm cả người cao tuổi và người có khiếm khuyết thị giác nhẹ.

| Mã  | Tiêu chí                | Chuẩn tham chiếu                                                                   |
| --- | ----------------------- | ---------------------------------------------------------------------------------- |
| A1  | **Keyboard navigation** | Toàn bộ chức năng thao tác được bằng bàn phím (Tab, Enter, Esc)                    |
| A2  | **Focus indicator**     | Phần tử đang focus phải có viền nhìn thấy rõ ràng                                  |
| A3  | **ARIA labels**         | Screen reader đọc được các button/input — quan trọng cho form nhập liệu phức tạp   |
| A4  | **Color independence**  | Không dùng màu sắc là _nguồn thông tin duy nhất_ — phải có icon hoặc text kèm theo |
| A5  | **Font size**           | Base font ≥ 15px (xem mục 2), không dùng `px` fixed cứng                           |
| A6  | **Touch targets**       | Button/link ≥ 44×44px (WCAG 2.5.5) — quan trọng cho tablet tại giường bệnh         |

---

### 8.5 Tính nhất quán (Consistency)

> Hệ thống bệnh viện thường được xây dựng nhiều module — đây là điểm yếu hay gặp nhất khi nhiều người cùng phát triển.

| Mã  | Tiêu chí                   | Câu hỏi kiểm tra                                                        |
| --- | -------------------------- | ----------------------------------------------------------------------- |
| C1  | **Design token nhất quán** | Cùng màu primary button ở tất cả các trang — dùng đúng palette ở mục 1? |
| C2  | **Component reuse**        | Table ở trang A và trang B có cùng style, cùng hành vi không?           |
| C3  | **Naming convention**      | "Bệnh nhân" vs "Người bệnh" vs "Patient" — thống nhất xuyên suốt?       |
| C4  | **Date/time format**       | DD/MM/YYYY và HH:mm nhất quán khắp hệ thống?                            |
| C5  | **Error message tone**     | Tất cả thông báo lỗi có cùng giọng văn, cùng vị trí hiển thị không?     |

---

### 8.6 Thẩm mỹ hiện đại (Aesthetics)

> Giao diện "chán" phần lớn là do thiếu nhóm tiêu chí này — không cần redesign lớn, chỉ cần bổ sung từng điểm.

| Mã  | Tiêu chí                       | Bệnh viện công thường mắc              | Hướng cải thiện cho CareFlow                                                               |
| --- | ------------------------------ | -------------------------------------- | ------------------------------------------------------------------------------------------ |
| AE1 | **Color palette có tính toán** | Màu mặc định `#0000FF`, xám trung tính | Dùng HSL palette như đã định nghĩa ở mục 1                                                 |
| AE2 | **Micro-animation**            | Không có transition nào                | Hover effect 150ms, loading skeleton thay spinner                                          |
| AE3 | **Depth & Shadow**             | Flat hoàn toàn, không có layer         | Subtle `box-shadow` theo hệ thống ở mục 4                                                  |
| AE4 | **Dark mode**                  | Không có                               | Hữu ích cho ca trực đêm của bác sĩ                                                         |
| AE5 | **Data visualization**         | Bảng số thuần, không trực quan         | Sparkline, badge màu cho vital signs, trend indicator                                      |
| AE6 | **Loading experience**         | Trang trắng + spinner thô              | Dùng component `LoadingSpinner` hình tròn mượt mà (`#6E2582`) đồng bộ cho toàn bộ hệ thống |

---

### 8.7 Đặc thù hệ thống y tế (Clinical-Specific)

> Những tiêu chí này không có trong web thương mại thông thường nhưng là critical cho CareFlow.

| Mã  | Tiêu chí                        | Lý do quan trọng                                                                           |
| --- | ------------------------------- | ------------------------------------------------------------------------------------------ |
| CL1 | **Critical data prominence**    | Dị ứng, chống chỉ định, cảnh báo phải nổi bật hơn mọi thông tin khác trên trang            |
| CL2 | **Audit trail visibility**      | Ai sửa gì, lúc mấy giờ — bác sĩ và quản lý phải xem được lịch sử thao tác                  |
| CL3 | **Alert fatigue**               | Không spam notification — chỉ alert thứ gì thực sự cần hành động ngay                      |
| CL4 | **Print layout**                | Kết quả xét nghiệm, đơn thuốc phải có CSS print layout riêng, in ra đọc được               |
| CL5 | **Offline / network tolerance** | Mạng bệnh viện hay yếu — các tính năng critical phải có cache, không mất dữ liệu đang nhập |
| CL6 | **Session timeout UX**          | Báo trước 2 phút khi sắp hết phiên, không được mất form đang điền dở                       |

---

### 8.8 Animation & Motion Design

> Mục tiêu của CareFlow: điểm **3–4/5** — đủ để cảm giác "alive" và professional, nhưng không gây distraction hay làm chậm tốc độ làm việc của bác sĩ.

#### Nguyên tắc cốt lõi

| Mã  | Tiêu chí                    | Kém                                           | Tốt                                                               |
| --- | --------------------------- | --------------------------------------------- | ----------------------------------------------------------------- |
| AN1 | **Có lý do tồn tại**        | Animation chỉ để "đẹp", không giúp người dùng | Mỗi animation truyền đạt thông tin: đang load, thành công, có lỗi |
| AN2 | **Không gây confusion**     | Element bay lung tung, ngược chiều thao tác   | Hướng chuyển động logic — menu mở xuống thì đóng lên              |
| AN3 | **Không che khuất content** | Overlay animation block thao tác              | Animation xảy ra ở vùng phụ, không block nội dung chính           |

#### Timing & Easing chuẩn

| Loại animation                               | Thời lượng    | Easing                         |
| -------------------------------------------- | ------------- | ------------------------------ |
| Micro-interaction (hover, focus, click)      | **100–200ms** | `ease-out`                     |
| Transition vào trang / modal                 | **200–300ms** | `ease-out`                     |
| Animation phức tạp (onboarding, empty state) | **400–600ms** | `cubic-bezier(0.4, 0, 0.2, 1)` |
| Element biến mất (fade-out, close)           | **150–200ms** | `ease-in`                      |

> **Tuyệt đối không** dùng `linear` cho animation giao diện — cảm giác máy móc và cứng nhắc.

#### Loại animation phù hợp theo tình huống

| Tình huống                | Animation nên dùng                         | ❌ Tránh                                 |
| ------------------------- | ------------------------------------------ | ---------------------------------------- |
| Đang load dữ liệu         | Skeleton screen (pulse nhẹ 1.5s loop)      | Spinner xoay cả trang                    |
| Submit form thành công    | Checkmark animate + toast slide in từ góc  | Confetti (không phù hợp môi trường y tế) |
| Có lỗi / cảnh báo         | Shake nhẹ input + border đỏ highlight      | Màn hình nhấp nháy, bell rung to         |
| Mở modal / drawer         | Fade in + slide nhẹ từ dưới lên            | Bounce, zoom phóng to nhanh              |
| Hover button              | Background shift màu + subtle shadow       | Scale to quá mức, rotation               |
| Tab switching             | Underline slide theo chiều ngang           | Page flip 3D, rotate                     |
| Dữ liệu realtime thay đổi | Row highlight màu nhạt 1 giây              | Row nhảy vị trí gây mất track thị giác   |
| Empty state               | Illustration/text fade in nhẹ              | Không có gì (màn hình trắng)             |
| Notification toast        | Slide in từ góc trên phải, auto-dismiss 4s | Pop đột ngột giữa màn hình               |

#### Tiêu chí kỹ thuật

| Mã  | Tiêu chí                                           | Lý do                                                               |
| --- | -------------------------------------------------- | ------------------------------------------------------------------- |
| AN4 | **Chỉ animate `transform` và `opacity`**           | GPU-accelerated, không trigger layout recalculate                   |
| AN5 | **Tránh animate `width`, `height`, `top`, `left`** | Gây layout thrashing, giật trên máy cũ của bệnh viện                |
| AN6 | **60fps target**                                   | Kiểm tra bằng DevTools Performance tab, không được dropped frames   |
| AN7 | **Không loop vô tận cho decoration**               | Spinner loading thì OK; animation trang trí lặp mãi = mất tập trung |
| AN8 | **Alert animation phải dừng được**                 | Bác sĩ đã thấy cảnh báo → click để tắt ngay                         |
| AN9 | **Không dùng parallax / scroll animation**         | Không phù hợp với tool chuyên nghiệp, lag trên máy cũ               |

#### Accessibility của Animation

```css
/* BẮT BUỘC implement trong global CSS */
@media (prefers-reduced-motion: reduce) {
  *,
  *::before,
  *::after {
    animation-duration: 0.01ms !important;
    transition-duration: 0.01ms !important;
  }
}
```

| Mã   | Tiêu chí                                     | Lý do                                                                                            |
| ---- | -------------------------------------------- | ------------------------------------------------------------------------------------------------ |
| AN10 | **Respect `prefers-reduced-motion`**         | Người dùng bật "reduce motion" (rối loạn tiền đình, động kinh nhạy sáng) → tắt toàn bộ animation |
| AN11 | **Không flash > 3 lần/giây**                 | WCAG 2.3.1 — có thể gây co giật động kinh                                                        |
| AN12 | **Animation không mang thông tin độc quyền** | Nếu "đỏ nhấp nháy = nguy hiểm", phải có text/icon kèm theo                                       |

---

### 8.9 Bảng điểm tổng hợp (Scoring Rubric)

Dùng để đánh giá định kỳ hoặc trước/sau khi release tính năng lớn.

| Nhóm                    | Số tiêu chí     | Điểm tối đa  |
| ----------------------- | --------------- | ------------ |
| P — Hiệu năng kỹ thuật  | 6               | 30           |
| V — Phân cấp thị giác   | 6               | 30           |
| U — UX / Usability      | 7               | 35           |
| A — Accessibility       | 6               | 30           |
| C — Nhất quán           | 5               | 25           |
| AE — Thẩm mỹ hiện đại   | 6               | 30           |
| CL — Đặc thù y tế       | 6               | 30           |
| AN — Animation & Motion | 12              | 60           |
| **TỔNG**                | **54 tiêu chí** | **270 điểm** |

**Thang đánh giá tổng:**

- **< 135 điểm (< 50%):** Cần cải thiện khẩn cấp
- **135–189 điểm (50–70%):** Chấp nhận được, cần roadmap cải thiện
- **190–216 điểm (70–80%):** Tốt, đáp ứng nhu cầu vận hành
- **> 216 điểm (> 80%):** Xuất sắc, chuẩn mực hệ thống y tế hiện đại

---

### 8.10 Thứ tự ưu tiên cải thiện

Nếu cần nâng cấp hệ thống, ưu tiên theo ROI từ cao đến thấp:

1. **Ngay lập tức (Quick wins):** V2 Tương phản, V3 Typography, U6 Feedback loop, AN10 `prefers-reduced-motion`
2. **Ngắn hạn (1–2 sprint):** AE2 Micro-animation, AE3 Card depth, AE6 Skeleton loading, CL1 Critical data prominence
3. **Trung hạn:** P1–P6 Performance metrics, A1–A6 Accessibility đầy đủ, AE4 Dark mode
4. **Dài hạn:** CL5 Offline capability, full design token system, AN đồng bộ toàn bộ motion language

---

## 10. Nguyên tắc Thiết kế Màn hình Lâm sàng cho Bác sĩ (Clinical Dashboard Best Practices)

Giao diện **dashboard bệnh viện cho bác sĩ** được thiết kế theo các nguyên tắc chặt chẽ về độ ưu tiên thông tin, cỡ chữ, tương phản và bố cục để bác sĩ đọc nhanh, ít bị quá tải. Ưu tiên hàng đầu là “thấy ngay cái quan trọng”, sau đó mới tới chi tiết phụ.

### 10.1 C cỡ chữ (Typography & Scale)

- **Body Text:** Giữ body text tối thiểu khoảng **16px**, và dùng **18px** cho nội dung lâm sàng quan trọng để tăng khả năng đọc.
- **Tabular Numerals:** Dùng font số canh thẳng hàng (như `Roboto Mono`) cho sinh hiệu (mạch, huyết áp, SpO2, xét nghiệm) để quét mắt nhanh.
- **Phân cấp typography:** Thường dùng 3–5 cấp chữ:
  1. Tiêu đề bệnh nhân / khu vực chính (Lớn nhất)
  2. Cảnh báo / giá trị bất thường (Nổi bật hơn body)
  3. Ghi chú, mô tả, lịch sử (Nhỏ hơn nhưng rõ ràng)

### 10.2 Bố cục màn hình (Layout & Progressive Disclosure)

- **Single-screen view:** Bố cục ưu tiên **một màn hình nhìn được thông tin chính**, giảm phải chuyển trang liên tục.
- **Thứ tự ưu tiên trên màn hình:**
  1. Bệnh nhân nào (ID, Họ tên, Tuổi, Giới tính)
  2. Cảnh báo khẩn cấp / Dị ứng
  3. Sinh hiệu hiện tại (Vitals)
  4. Xử trí / Đơn thuốc đã kê
  5. Ghi chú & Lịch sử mở rộng (Progressive Disclosure)
- **Cuộn dọc hơn chia trang:** Nếu nội dung vượt quá màn hình, ưu tiên cuộn dọc đơn giản thay vì chia nhiều tab điều hướng phức tạp.

### 10.3 Màu sắc & Tương phản (Contrast & Color Independence)

- **Nền sáng, chữ đậm:** Nền xám ấm/trắng, chữ đậm (`#1A1A1A`), tương phản WCAG ≥ 4.5:1.
- **Dùng màu có chủ đích:** Màu sắc chỉ dùng cho mức độ nguy cấp (Đỏ/Vàng/Xanh).
- **Không dùng màu duy nhất:** Cảnh báo phải đi kèm chữ hoặc icon/nhãn rõ ràng.

### 10.4 Khoảng cách & Nhịp nhìn (Spacing & Line Height)

- **Line height thoáng:** Dùng `line-height` từ **1.4 – 1.5+** cho nội dung văn bản lâm sàng dài.
- **Padding & Grid:** Padding nhất quán, căn lưới cột thẳng hàng cho sinh hiệu và xét nghiệm để dễ so sánh.

### 10.5 Bộ Quy tắc Gợi ý Nhanh (Quick Checklist)

- Body: 16–18px.
- Alert text: Nổi bật hơn body và có icon/nhãn kèm theo.
- 3–5 cấp typography chuẩn.
- Tương phản đạt chuẩn WCAG.
- Layout theo panel rõ ràng, nhóm gọn menu điều hướng.
- Ưu tiên dữ liệu quan trọng ở trên cùng.
- Giảm số bước click, ưu tiên scroll dọc hơn chuyển trang.

---

## 11. Màu chủ đạo — Indigo-Navy-Blue (Active Primary Color)

> **Quyết định hiện tại:** Hệ thống CareFlow áp dụng bộ ba màu **Xanh dương tím (Indigo) + Đen (Dark Navy) + Xanh dương (Blue)** làm palette chủ đạo. Đây là theme Indigo-Navy Dark với sidebar đen navy đậm và accent indigo/blue hiện đại.

### 11.1 Bảng Màu Indigo-Navy-Blue

| Token                    | Hex                     | Công dụng                                         |
| :----------------------- | :---------------------- | :------------------------------------------------ |
| **Indigo (blue-purple)** | `#6366F1`               | Accent chính: active state, highlight, tab active |
| **Indigo light**         | `#EEF2FF`               | Background active item, hover row, table header   |
| **Blue (CTA)**           | `#3B82F6`               | Nút hành động chính (Gọi bệnh nhân, Vào khám)     |
| **Blue hover**           | `#2563EB`               | Hover state nút blue                              |
| **Dark Navy**            | `#0D0F1E`               | Sidebar background (gần đen, có tông xanh)        |
| **Navy border**          | `#1E2340`               | Viền và divider trong sidebar                     |
| **Sidebar active**       | `rgba(99,102,241,0.15)` | Nền item active trong sidebar                     |
| **Sidebar active text**  | `#818CF8`               | Chữ/icon item active trong sidebar                |
| **Sidebar hover**        | `#161930`               | Hover nền menu item trong sidebar                 |
| **Main background**      | `#F1F5F9`               | Nền workspace (slate blue-tinted light)           |
| **Card background**      | `#FFFFFF`               | Nền card, bảng dữ liệu                            |
| **Card border**          | `#E2E8F0`               | Viền card, dòng bảng                              |

### 11.2 Gradient Rules

- **Logo, avatar, nút CTA chính:** `linear-gradient(135deg, #6366F1, #3B82F6)` — indigo → blue
- **Shortcut card:** `linear-gradient(135deg, #6366F1 0%, #3B82F6 100%)`
- **Sidebar:** `linear-gradient(180deg, #0D0F1E 0%, #111427 100%)`
- **Grid tint trên workspace:** `rgba(99,102,241,0.03)` kích thước `24px x 24px`

### 11.3 Lịch sử thay đổi palette

| Phiên bản         | Palette                                                         | Trạng thái |
| :---------------- | :-------------------------------------------------------------- | :--------- |
| v1                | SJD Purple (`#6E2582`, sidebar `#2B1D30`)                       | Deprecated |
| v2                | Cyan Blue (`#00A3FF`, sidebar trắng) — design1.md               | Rejected   |
| **v3 (hiện tại)** | **Indigo-Navy-Blue (`#6366F1` / `#3B82F6`, sidebar `#0D0F1E`)** | **Active** |

### 11.4 Token code (globals.css)

```css
/* Primary — Indigo / Blue-Purple */
--primary-500: #6366f1; /* indigo — active, accent */
--primary-50: #eef2ff; /* light indigo bg */

/* CTA — Blue */
--blue-500: #3b82f6;
--blue-600: #2563eb;

/* Dark Navy Sidebar */
--sidebar-bg: #0d0f1e;
--sidebar-active-bg: rgba(99, 102, 241, 0.15);
--sidebar-active: #818cf8;
--sidebar-hover: #161930;

/* Layout */
--background: #f1f5f9;
```
