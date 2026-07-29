Dưới đây là **bản hoàn thiện** theo văn phong nghiệp vụ/SRS, đã chỉnh cho sát hơn với bệnh viện công và dễ đưa vào báo cáo đồ án.

## Nghiệp vụ EMR

**Nghiệp vụ Quản lý Hồ sơ bệnh án điện tử (EMR)** là nghiệp vụ trung tâm của hệ thống khám chữa bệnh tại bệnh viện, có nhiệm vụ lưu trữ, tổ chức và cho phép tra cứu toàn bộ thông tin lâm sàng của người bệnh trong suốt quá trình khám, điều trị và theo dõi. Dữ liệu trong EMR liên kết với các phân hệ khác như HIS, LIS và RIS/PACS để cung cấp một bức tranh tổng thể về người bệnh, bao gồm thông tin định danh, tiền sử bệnh, dị ứng, chẩn đoán, chỉ định, kết quả cận lâm sàng và đơn thuốc.

Nghiệp vụ này chủ yếu phục vụ **Use Case 6 – Tra cứu Hồ sơ bệnh án điện tử (UC6)**, với tác nhân chính là **Bác sĩ** thao tác trên **Web App**. Ngoài ra, sau khi hoàn tất khám bệnh, một phần dữ liệu được liên thông sang **Mobile App của bệnh nhân** để phục vụ tra cứu kết quả khám và đơn thuốc điện tử.

## Tiền điều kiện

Trước khi tra cứu hồ sơ bệnh án, bác sĩ phải:

- Đăng nhập thành công vào hệ thống.
- Được phân quyền truy cập hồ sơ bệnh án của bệnh nhân theo chuyên môn, ca trực hoặc quy định của bệnh viện.
- Bệnh nhân phải có hồ sơ tồn tại trên hệ thống EMR/HIS, hoặc có dữ liệu được tiếp nhận từ các lần khám trước đó.

## Luồng nghiệp vụ chính

1. Bác sĩ chọn một bệnh nhân từ danh sách đang chờ khám, đang điều trị, hoặc tra cứu chủ động theo mã bệnh nhân, CCCD hoặc số hồ sơ.
2. Hệ thống gửi yêu cầu đến EMR Service/HIS và các phân hệ liên quan để lấy dữ liệu bệnh án trong phạm vi quyền truy cập của bác sĩ.
3. Web App hiển thị màn hình tóm tắt hồ sơ bệnh án, bao gồm:
   - Thông tin định danh người bệnh.
   - Tiền sử bệnh và tiền sử dị ứng.
   - Chẩn đoán gần nhất.
   - Danh sách thuốc đang dùng.
   - Kết quả xét nghiệm và cận lâm sàng.
   - Các tài liệu/hình ảnh y tế đã được lưu, nếu có.
4. Bác sĩ có thể chọn một lần khám cũ để xem chi tiết, bao gồm:
   - Sinh hiệu.
   - Triệu chứng lâm sàng.
   - Chẩn đoán.
   - Chỉ định cận lâm sàng.
   - Kết quả xét nghiệm.
   - Đơn thuốc và hướng dẫn điều trị của lần khám đó.
5. Hệ thống tự động ghi nhận **audit log** cho mọi hành động tra cứu, bảo đảm phục vụ kiểm soát truy cập, an toàn thông tin và kiểm tra sau này.

## Luồng thay thế

- **Bác sĩ không đủ quyền xem hồ sơ**: hệ thống từ chối hiển thị dữ liệu, chỉ trả về thông báo không có quyền truy cập và ghi nhận log bảo mật.
- **Dữ liệu cận lâm sàng chưa đồng bộ**: hệ thống vẫn hiển thị dữ liệu hiện có và đánh dấu rõ các phần chưa cập nhật.
- **Tài liệu đính kèm không hợp lệ**: hệ thống không cho phép hiển thị nội dung file; tùy chính sách bệnh viện, có thể chỉ hiển thị metadata.
- **Không tìm thấy hồ sơ**: hệ thống thông báo bệnh nhân chưa có hồ sơ điện tử phù hợp trong hệ thống.

## Tích hợp AI hỗ trợ

Để hỗ trợ bác sĩ tra cứu nhanh, hệ thống có thể tích hợp một luồng xử lý nền cho AI nhằm tạo **báo cáo tóm tắt hồ sơ** từ các dữ liệu quá khứ như sinh hiệu, tiền sử bệnh, toa thuốc cũ và kết quả nổi bật. Báo cáo này chỉ mang tính chất hỗ trợ tham khảo, được tách khỏi luồng CRUD chính để không ảnh hưởng đến tốc độ tra cứu và cập nhật bệnh án.

Tính năng AI không thay thế quyết định chuyên môn của bác sĩ mà chỉ đóng vai trò hỗ trợ tổng hợp thông tin, giúp rút ngắn thời gian đọc hồ sơ và nhận diện nhanh các điểm cần chú ý như dị ứng thuốc, bệnh nền hoặc tiền sử điều trị.

## Liên thông dữ liệu cho bệnh nhân

Sau khi bác sĩ hoàn tất ca khám, các thông tin được phép công bố sẽ được đồng bộ sang Mobile App của bệnh nhân để phục vụ:

- Tra cứu kết quả khám.
- Xem chẩn đoán lâm sàng.
- Xem đơn thuốc điện tử.
- Xem hoặc tải file PDF của đơn thuốc, nếu bệnh viện cho phép.

Việc đồng bộ dữ liệu này phải tuân theo chính sách phân quyền và bảo mật của bệnh viện, bảo đảm bệnh nhân chỉ xem được các thông tin được phép hiển thị.

## Giá trị nghiệp vụ

Việc số hóa EMR giúp bệnh viện giảm phụ thuộc vào hồ sơ giấy, tăng tốc độ tra cứu thông tin, hỗ trợ bác sĩ phát hiện dị ứng thuốc và bệnh nền quan trọng, đồng thời nâng cao chất lượng quyết định điều trị. Trong bối cảnh chuyển đổi số tại bệnh viện công, EMR là nền tảng quan trọng để tiến tới quản lý khám chữa bệnh đồng bộ, an toàn và hiệu quả hơn. [tapchiyhcd](https://tapchiyhcd.vn/index.php/yhcd/article/view/2905)

Nếu bạn muốn, mình có thể làm tiếp cho bạn 1 trong 3 bản sau:

1. **Bản ngắn hơn để đưa vào báo cáo**
2. **Bản chuẩn SRS có mục: actor, precondition, main flow, alternate flow, postcondition**
3. **Bản đặc tả Use Case UC6 hoàn chỉnh theo form bảng**
