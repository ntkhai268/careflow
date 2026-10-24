[Quay về mục lục](README.md)

# R6 — trợ lý có nguồn và toa giọng nói local

Mốc21/10/2026, nguồn R6 được mang sang `feature/dangkhoii/release-r7`, nền907cba2 kèm thay đổi chưa commit/push. Runtime hiện tại và cách bàn giao xem [trang25](25-nghiem-thu-va-ban-giao-r7.md). AI chạy thử với dữ liệu tổng hợp; chuyên môn/giọng bác sĩ vẫn chưa nghiệm thu. Hospital Web: http://localhost:5173/login. Tài khoản thử chỉ xem tại [trang01](01-tai-khoan-va-du-lieu.md).

## Tra cứu hướng dẫn theo ca khám

1. ADMIN mở **Nguồn hướng dẫn AI** trong menu quản trị (`/admin/ai-sources`). Nhập tên, phiên bản, URL HTTPS kiểm chứng được và đoạn tài liệu có quyền sử dụng. Chọn khoa từ Directory thật. Nguồn giả chỉ dùng thử phải đánh dấu **Nguồn tổng hợp kiểm thử**.
2. Lưu tạo nguồn **DRAFT**. Nguồn này chưa được trợ lý sử dụng. Nội dung một phiên bản bất biến; sửa nội dung bằng cách nhập phiên bản mới.
3. Một DOCTOR khác người nhập mở **Rà soát nguồn AI** (`/dashboard/ai-sources`), đối chiếu nội dung và URL, xác nhận checkbox rồi bấm **Xác nhận đã rà soát chuyên môn**. Chỉ thao tác sau khi đã rà soát thực tế. Bấm nút trên tài liệu tổng hợp chỉ kiểm tra phần mềm, không phải nghiệm thu chuyên môn.
4. Bác sĩ mở ca đang phụ trách rồi mở biểu tượng trợ lý ở góc phải. Hỏi nội dung thuộc khoa có nguồn được duyệt. Server lấy ngữ cảnh từ Consultation/Patient, kiểm tra phân công; không lấy hồ sơ từ màn hình hay patientId do người hỏi tự gửi.
5. Kết quả hiển thị trích đoạn, phiên bản, hash, thời điểm rà soát, link nguồn, thông tin thiếu và nhãn tổng hợp nếu có. Bác sĩ chủ động đối chiếu và quyết định. Lịch sử chỉ thuộc bác sĩ/ca đó và còn trong thời hạn lưu.
6. ADMIN bấm **Ngừng dùng nguồn** để thu hồi. Hỏi lại không được dùng nguồn đã thu hồi. Lịch sử cũ vẫn giữ bản trích dẫn đến hết retention, không biến thành hướng dẫn hiện hành.

Không có nguồn đã duyệt: báo chưa sẵn sàng. Có nguồn nhưng không phù hợp: báo chưa đủ căn cứ. Sai quyền/bàn giao/mất phiên/nguồn lỗi: báo lỗi; không trả phác đồ mẫu. Có thể tiếp tục khám và nhập toa thủ công.

Đây là bộ tra cứu trích đoạn local có lọc ngữ cảnh, không phải mô hình tự suy luận/chẩn đoán. Hiện chưa có bộ tài liệu bệnh viện và bộ ca được chuyên gia duyệt để nghiệm thu chất lượng lâm sàng.

## Toa giọng nói

Xem [trang23](23-nhap-toa-giong-noi-ai.md) cho cấu hình và cách lặp lại. Luồng: bật ghi âm → **LƯU TOA NHÁP** tự dừng mic và xử lý local → sửa trên cùng toa → lưu cập nhật → ký bằng thao tác riêng. Không có màn duyệt AI bắt buộc trước khi lưu nháp.

Bản sửa24/10 cho lưu DRAFT theo tên dù chưa có mã danh mục và sửa trực tiếp từng ô. Không tự lấy thuốc gần âm đầu tiên hoặc thêm danh mục. Giao diện chỉ có một Đơn thuốc và một xác nhận toàn đơn cuối danh sách. Có thể ghi thêm nhiều lần vào cùng DRAFT, giữ chỉnh sửa cũ; lưu và kiểm tra lại toàn đơn trước khi ký. Server vẫn kiểm xác nhận thuốc kê bằng tên; xem luồng hiện tại ở trang23. Trường chưa nghe được để trống; toa nguồn AI chưa đủ liều/tần suất/thời điểm/số ngày/số lượng bị chặn ký. Lưu lại cùng job/revision trả đúng toa đã có và không ghi đè chỉnh sửa về sau.

## Điều kiện kỹ thuật và phạm vi nghiệm thu

AI ở HTTPS nội bộ8092 (stack Mac18093), worker có mTLS và token riêng. Bật riêng `CLINICAL_AI_ENABLED`/`VOICE_PRESCRIPTION_ENABLED`; mặc định tắt. Secure runtime cần `AI_CONTENT_KEY` riêng (base64 của32byte), caller credentials, truststore/keystore và DB riêng. Production DB cần `sslmode=verify-full`; không bật nguồn tổng hợp ở production. Không đưa khóa vào frontend/Git.

Nội dung tương tác được mã hóa, mặc định7ngày (cấu hình1–30); nội dung voice900giây. TTL ngăn đọc ngay khi hết hạn, job cleanup xóa nội dung sau đó. Mất key không thể đọc lại; sao lưu key an toàn cùng kế hoạch khôi phục DB. DB voice cũ có plaintext cần chuyển đổi có kiểm soát trước khi bật mã hóa; không tự nâng cấp rồi coi là đã mã hóa dữ liệu lịch sử.

Bộ thử trình duyệt dùng MediaStream tổng hợp qua MediaRecorder và ASR local; ca thử cần sửa transcript. Micro vật lý/giọng bác sĩ chưa nghiệm thu. Đã kiểm tra kỹ thuật trên dữ liệu tổng hợp; kết quả và giới hạn nằm trong [biên bản R6](../.planning/ai-service/r6-acceptance-2026-10-21.md). Chưa nghiệm thu giọng bác sĩ độc lập, chất lượng phác đồ chuyên môn, tải production hoặc reboot/restore toàn stack. Không dùng kết quả thử làm quyết định điều trị thật.
