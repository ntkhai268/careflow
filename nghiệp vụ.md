# **BÁO CÁO TIẾN ĐỘ**

# **ĐỀ TÀI: XÂY DỰNG HỆ THỐNG PHẦN MỀM TRỢ GIÚP KHÁM CHỮA BỆNH TẠI BỆNH VIỆN CÔNG THEO KIẾN TRÚC MICROSERVICES**

**CHƯƠNG 1: BỐI CẢNH THỰC TẾ VÀ ĐẶT VẤN ĐỀ:**

\- **Quá tải hệ thống hàng chờ:** Tình trạng bệnh nhân phải xếp hàng từ sáng sớm để lấy số, chờ đợi nhiều giờ liền trước phòng khám vô cùng phổ biến. Quy trình thủ công hoặc hệ thống định tuyến đơn giản gây lãng phí thời gian và mệt mỏi cho người bệnh.

\- **Bất cập trong điều phối (Khám mới vs. Tái khám):** Việc đan xen giữa bệnh nhân mới vào khám, bệnh nhân đã có lịch hẹn trước, và bệnh nhân quay lại phòng khám sau khi có kết quả cận lâm sàng (xét nghiệm, siêu âm) chưa có thuật toán tối ưu, dẫn đến xung đột tại cửa phòng khám.

\- **Phụ thuộc vào hồ sơ giấy:** Việc lưu trữ, tra cứu lịch sử bệnh án (EMR) qua sổ khám bệnh giấy dễ thất lạc, khiến bác sĩ gặp khó khăn trong việc bao quát toàn bộ tiến trình bệnh lý của bệnh nhân.

\- **Áp lực lên đội ngũ y tế:** Bác sĩ phải tiếp đón số lượng bệnh nhân quá lớn trong một ca trực, dễ dẫn đến sai sót hoặc thiếu thông tin cập nhật về các phác đồ điều trị mới.

**CHƯƠNG 2: CÁI NHÌN BAO QUÁT HỆ THỐNG (SYSTEM OVERVIEW)**

**2.1. Yêu cầu Đầu vào (Inputs)**

**\- Từ phía Bệnh nhân:** Thông tin định danh cá nhân (hình ảnh CCCD/khuôn mặt để eKYC), thông tin đặt lịch hẹn, hồ sơ bệnh án cũ (file PDF/ảnh chụp kết quả xét nghiệm), dữ liệu Check-in tại chỗ qua QR Code.

\- **Từ phía Bác sĩ:** Y lệnh điều trị, chẩn đoán bệnh, đơn thuốc điện tử, chỉ định cận lâm sàng, câu lệnh tương tác với trợ lý AI.

\- **Từ phía Hệ thống:** Dữ liệu thời gian thực (Real-time) từ các phòng khám, dữ liệu lịch sử thời gian khám của từng bác sĩ/chuyên khoa để làm cơ sở tính toán.

**2.2. Luồng Trải nghiệm Người dùng (User Experience \- UX Journey)**

1. **Trải nghiệm của Bệnh nhân (Mobile App):** Ở nhà Đặt lịch & Upload hồ sơ → Đến viện Quét QR Code để Check-in → Nhận số thứ tự và theo dõi thời gian chờ ước tính (ETA) → Nhận thông báo mời vào phòng khám → Khám xong, nhận kết quả, đơn thuốc và lịch hẹn tái khám ngay trên điện thoại.

2. **Trải nghiệm của Bác sĩ (Web App):** Đăng nhập hệ thống → Tiếp nhận danh sách hàng đợi của phòng khám → Click chọn bệnh nhân, hệ thống tự động hiển thị EMR (bao gồm cả hồ sơ cũ bệnh nhân tự upload) → Chat với AI Clinical Assistant để tham khảo phác đồ → Kê đơn / Chỉ định cận lâm sàng bằng vài click chuột.

**2.3. Yêu cầu Đầu ra (Outputs)**

**\-** Mã số thứ tự khám tối ưu hóa theo độ ưu tiên (Multi-priority Queue).

\- Thời gian chờ dự kiến (ETA) chính xác hiển thị trên màn hình phòng khám và Mobile App.

\- Hồ sơ bệnh án điện tử (EMR) được số hóa và chuẩn hóa dưới định dạng có cấu trúc (ví dụ: HL7 FHIR tiêu chuẩn y tế).

\- Đơn thuốc điện tử hợp lệ, mã QR đơn thuốc, lịch hẹn tái khám đồng bộ lên lịch cá nhân.

**CHƯƠNG 3: CHI TIẾT CÁC USE CASE VÀ PHÂN TÁCH SERVICES**

**3.1. Phân hệ Bệnh nhân (Actor: Patient \- Mobile App)**

**UC1: Đăng ký tài khoản và Định danh (eKYC):**

**Mô tả:** Bệnh nhân tải ứng dụng, chụp ảnh CCCD 2 mặt và quét khuôn mặt để tự động trích xuất thông tin và xác thực danh tính thực.

**Luồng dữ liệu:** Mobile App → API Gateway → *Identity & eKYC Service* (xử lý nhận diện qua OCR & Face Matching) → Lưu thông tin tài khoản hợp lệ.

**UC2: Đặt lịch khám và Upload hồ sơ**

**Mô tả:** Chọn chuyên khoa, bác sĩ, khung giờ khám và đính kèm (upload) file kết quả xét nghiệm, đơn thuốc cũ từ bệnh viện khác.

**Luồng dữ liệu:** Mobile App → *Appointment Service* (kiểm tra lịch trống của bác sĩ) \+ *Patient Service* (lưu trữ link file hồ sơ cũ lưu trên Cloud Storage như S3).

**UC3: Check-in tại bệnh viện bằng QR Code**

**Mô tả:** Khi đến viện, bệnh nhân quét mã QR đặt tại sảnh hoặc trước phòng khám để xác nhận sự có mặt (Xóa bỏ bước xếp hàng lấy số giấy).

**Luồng dữ liệu:** Mobile App → API Gateway → *Queue Management Service* (chuyển trạng thái từ "Đã hẹn" sang "Đã có mặt \- Chờ khám").

**UC4: Theo dõi và nhận thông báo hàng chờ**

**Mô tả:** Xem thời gian thực (Real-time) số thứ tự đang khám, số của mình, và nhận thông báo đẩy (Push Notification) khi còn 2 lượt là đến phiên.

**Luồng dữ liệu:** *Queue Management Service* phát sự kiện → Kafka → *Notification Service* (Firebase Cloud Messaging) → Mobile App.

**UC5: Tra cứu kết quả khám**

**Mô tả:** Sau khi hoàn thành ca khám, bệnh nhân xem đơn thuốc điện tử, các chỉ định cận lâm sàng tiếp theo ngay trên app mà không cần đợi in giấy.

**Luồng dữ liệu:** Mobile App → *Prescription / Lab Order Service* → Lấy dữ liệu hiển thị.

**3.2. Phân hệ Bác sĩ (Actor: Doctor \- Web App)**

**UC6: Tra cứu Hồ sơ bệnh án điện tử (EMR)**

**Mô tả:** Bác sĩ mở giao diện phòng khám, hệ thống tự động tải toàn bộ lịch sử bệnh án nội bộ và các tệp tin do bệnh nhân tự upload từ trước.

**Luồng dữ liệu:** Web App → *Electronic Medical Record (EMR) Service* → Đọc dữ liệu tổng hợp từ Database.

**UC7: Khám và Chẩn bệnh**

**Mô tả:** Bác sĩ nhập thông tin triệu chứng lâm sàng, chẩn đoán sơ bộ, áp mã bệnh ICD-10 vào hệ thống.

**Luồng dữ liệu:** Web App → *Doctor Consultation Service* → Ghi nhận phiên khám hiện tại.

**UC8: Chỉ định Cận lâm sàng & Kê đơn thuốc**

**Mô tả:** Bác sĩ chọn các xét nghiệm cần làm hoặc lên đơn thuốc từ danh mục thuốc có sẵn của bệnh viện.

**Luồng dữ liệu:** Web App → *Lab Order Service* & *Prescription Service*. Các service này sẽ phát sự kiện ra Message Broker để cập nhật trạng thái viện phí hoặc đẩy thông báo sang phòng thuốc/phòng xét nghiệm.

**UC9: Tương tác với AI Chatbot (AI Clinical Assistant)**

**Mô tả:** Bác sĩ tham khảo ý kiến trợ lý AI bằng cách gửi thông tin bệnh lý, AI sẽ đối chiếu với phác đồ chuẩn của Bộ Y tế để đưa ra gợi ý.

**Luồng dữ liệu:** Web App → *AI Clinical Assistant Service* (Python FastAPI kết nối Local LLM/RAG) → Trả kết quả dạng text/markdown gợi ý cho bác sĩ.

**3.3. Phân hệ Hệ thống (Actor: System \- Core / Background Jobs)**

**UC10: Quản lý hàng đợi đa ưu tiên (Multi-priority Queueing)**

**Mô tả:** Thuật toán tự động sắp xếp hàng đợi dựa trên trọng số điểm ưu tiên (Ví dụ: Bệnh nhân cấp cứu/trẻ em/người già: Độ ưu tiên 1; Bệnh nhân đặt lịch trước: Độ ưu tiên 2; Bệnh nhân vãng lai không đặt lịch: Độ ưu tiên 3).

**Logic xử lý:** Thực hiện nội tại bằng Redis Sorted Set, sử dụng score được tính bằng công thức kết hợp giữa thời gian Check-in và Trọng số đối tượng:

**UC11: Xử lý hàng đợi xen kẽ động (N:M) và lỡ lượt**

**Mô tả:** \* **Xen kẽ N:M:** Điều phối tự động giữa N bệnh nhân khám mới và M bệnh nhân cầm kết quả xét nghiệm quay lại (Ví dụ quy định tỷ lệ 2 bệnh nhân mới : 1 bệnh nhân quay lại).  
 	**Lỡ lượt (Missed Turn):** Nếu gọi tên 3 lần bệnh nhân không có mặt, hệ thống tự động chuyển số đó vào trạng thái "Lỡ lượt". Khi bệnh nhân quay lại xác nhận, thuật toán sẽ tự động xen kẽ chèn họ vào sau 2 hoặc 3 lượt kế tiếp thay vì bắt họ xếp hàng lại từ đầu.

**Xử lý kỹ thuật:** Sử dụng kiến trúc hướng sự kiện (Event-driven) qua Kafka để đồng bộ trạng thái hàng đợi giữa các phòng chức năng (Phòng khám ↔ Phòng xét nghiệm) theo thời gian thực.

**UC12: Ước tính thời gian khám (ETA Calculation)**

**Mô tả:** Hệ thống chạy background job phân tích thời gian xử lý trung bình của từng bác sĩ đối với từng mặt bệnh cụ thể trong quá khứ để đưa ra dự báo: *"Còn khoảng 15 phút nữa sẽ đến lượt của bạn"*.

**Luồng dữ liệu:** *Analytics Service* tổng hợp dữ liệu lịch sử từ *Doctor Consultation Service* → Train/Update mô hình ước tính đơn giản → Cập nhật thông số ETA sang *Queue Management Service*.

