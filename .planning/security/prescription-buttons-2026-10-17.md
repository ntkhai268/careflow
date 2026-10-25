# Nút kê toa bị che — kiểm tra 17/10/2026

Runtime `.worktrees/security-integration`, HEAD `5a8c6e5` kèm thay đổi chưa commit. Chỉ sửa bố cục tại trang Consultation; không đổi handler nghiệp vụ, API hoặc schema, không commit/push. Hướng dẫn thao tác duy nhất tại [trang kê toa](../../huong-dan-su-dung/06-toa-va-cap-phat-thuoc.md).

## Nguyên nhân và sửa

Các phần tử fixed nằm trong vùng main có scrolling/stacking context: khung thuốc bị thanh đầu trang che tiêu đề và trợ lý nổi có thể đè lên vùng nút. `space-y-6` còn tạo margin cho lớp fixed. Thanh thao tác có chiều cao cố định, nhãn nút có thể xuống dòng khi cửa sổ hẹp.

Render nhóm khung thuốc/thanh thao tác/hộp thoại bằng React portal ngoài vùng main. Giữ thứ tự lớp trong cùng nhóm để hộp xác nhận và cửa sổ xem lịch sử vẫn nằm trên thanh thao tác. Khung thuốc chiếm chiều cao viewport, tiêu đề và hai nút không co; phần nhập liệu có min-height 0 và cuộn riêng. Thanh lưu/ký/hoàn tất tự xuống hàng với chiều cao linh hoạt, các nhãn nút không bị cắt.

GitNexus context/impact đã chạy trước sửa ConsultationPage. Kết quả UNKNOWN, không resolve caller của Next route; đã xác nhận route `/consultation/[id]`, các nút và layout cha bằng source/DOM. Detect-changes toàn worktree có CRITICAL gồm các thay đổi từ trước, không dùng làm phạm vi staging. [Diff riêng](prescription-buttons-2026-10-17/focused.patch).

## Kiểm chứng

- Production build, TypeScript và diff whitespace đạt; đã restart Web đang phục vụ.
- Browser dùng API/DB thật, không intercept hoặc mock. Tạo/check-in lượt thử mới qua API xác thực; An gọi và bắt đầu qua UI.
- Kiểm tra 800×500, 960×533, 1024×600, 1280×650, 1440×800. Đo bounding box hoàn toàn trong viewport và `elementFromPoint` tại tâm nút thuộc đúng nút: Hủy bỏ, Xác nhận thêm, Lưu nháp, Ký xác nhận và Hoàn tất. Tiêu đề khung thuốc không còn bị thanh đầu trang che.
- Chọn thuốc từ danh mục thật, bấm Xác nhận thêm, lưu toa nháp qua UI trả 201/DRAFT. Ba nút chính đều nhìn thấy và nhận click; mở hộp xác nhận ký, kiểm tra nút Xác nhận rồi hủy hộp thoại. Phần kiểm tra bố cục không bấm ký/hoàn tất.
- Trong lúc tiếp tục demo, dữ liệu lượt thử được chuyển CONFIRMED/COMPLETED. Giữ nguyên, không mở lại ca; trạng thái đọc lại được lưu riêng. Ca đã hoàn tất ẩn nút kê toa theo nghiệp vụ là đúng.
- Không có uncaught JavaScript error trong lượt browser kiểm tra.

[Kết quả kích thước](prescription-buttons-2026-10-17/checks.json), [mã ca](prescription-buttons-2026-10-17/case.json), [SELECT cuối](prescription-buttons-2026-10-17/database.json), [build đang chạy](prescription-buttons-2026-10-17/build.json).

Ảnh: [trước sửa](prescription-buttons-2026-10-17/repro-small.png), [khung thêm thuốc 800×500](prescription-buttons-2026-10-17/fixed-800.png), [ba nút chính 800×500](prescription-buttons-2026-10-17/three-buttons-800.png), [hộp xác nhận ký](prescription-buttons-2026-10-17/sign-dialog-800.png). Dữ liệu thuốc/khám chỉ phục vụ kiểm thử phần mềm, không phải chỉ định điều trị.
