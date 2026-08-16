# CHƯƠNG 1. GIỚI THIỆU ĐỀ TÀI

## 1.1. Mục đích

Khám bệnh ngoại trú tại bệnh viện công là một quy trình có nhiều bước nối tiếp
nhau. Một người bệnh có thể bắt đầu từ việc đăng ký hoặc đặt lịch, sau đó làm
thủ tục tiếp nhận, chờ khám, thực hiện cận lâm sàng, quay lại gặp bác sĩ, nhận
toa thuốc và đến nhà thuốc. Mỗi bước thường do một bộ phận phụ trách và diễn ra
ở một khu vực khác nhau.

Trong thực tế, người bệnh thường khó nắm được mình đang ở bước nào, cần đến đâu
và phải chờ bao lâu. Khi số lượng người đến khám tăng, việc gọi lượt và cập nhật
tình trạng phục vụ cũng trở nên khó theo dõi. Nhân viên phải trao đổi qua nhiều
kênh, còn bác sĩ phải dành thời gian xử lý các công việc điều phối bên cạnh hoạt
động chuyên môn. Thông tin của một lượt khám vì vậy có thể bị phân tán giữa các
bộ phận.

Từ thực tế đó, đề tài đề xuất CareFlow — một hệ thống hỗ trợ theo dõi và điều
phối hành trình khám ngoại trú. Hệ thống cung cấp cho bệnh nhân một nơi để xem
lịch khám, phiếu khám, trạng thái chờ và các kết quả liên quan. Ở phía bệnh viện,
bác sĩ và nhân viên có thể theo dõi các lượt đang chờ, thực hiện công việc của
mình và chuyển người bệnh sang bước tiếp theo.

Giá trị chính của CareFlow là tạo ra sự liên tục trong hành trình khám. Bệnh
nhân được hướng dẫn rõ ràng hơn, bệnh viện giảm bớt việc phối hợp thủ công và
các bộ phận có thể nhìn thấy thông tin cần thiết của cùng một lượt khám. Đề tài
không nhằm thay thế toàn bộ hệ thống thông tin bệnh viện mà tập trung minh họa
cách số hóa và liên kết các công đoạn chính của quy trình ngoại trú.

## 1.2. Mục tiêu

Mục tiêu tổng quát của đề tài là phân tích, thiết kế và xây dựng một phiên bản
CareFlow hỗ trợ bệnh nhân và bệnh viện quản lý hành trình khám ngoại trú một
cách rõ ràng, thống nhất và thuận tiện hơn.

Các mục tiêu cụ thể gồm:

1. Khảo sát quy trình khám ngoại trú, xác định các bên tham gia và nhận diện
   những khó khăn thường gặp trong quá trình phục vụ.
2. Mô tả hành trình đề xuất từ lúc đăng ký hoặc đặt lịch đến khi người bệnh hoàn
   tất khám, nhận thuốc và được lưu lại thông tin cần thiết.
3. Hỗ trợ bệnh nhân quản lý thông tin cá nhân, đặt lịch, nhận phiếu khám, theo
   dõi lượt chờ và xem các thông tin sau khám trên Patient Mobile.
4. Hỗ trợ bác sĩ và nhân viên tiếp nhận, cận lâm sàng, phát thuốc thực hiện công
   việc tương ứng trên Hospital Web.
5. Điều phối các nhóm bệnh nhân có nhu cầu phục vụ khác nhau, bảo đảm việc gọi
   lượt được thực hiện tuần tự và có sự cân bằng giữa các nhóm.
6. Liên kết các bước khám lâm sàng, cận lâm sàng, đọc kết quả, kê toa và phát
   thuốc để hạn chế việc thông tin bị đứt quãng giữa các bộ phận.
7. Bảo đảm người dùng chỉ được thực hiện những công việc phù hợp với vai trò,
   đồng thời lưu lại các thay đổi quan trọng trong quá trình phục vụ.
8. Kiểm thử và đánh giá phiên bản MVP, từ đó xác định mức độ đáp ứng mục tiêu
   cũng như những giới hạn còn lại của đề tài.

## 1.3. Phương pháp tiến hành

### 1.3.1. Khảo sát hiện trạng

Đề tài bắt đầu bằng việc tìm hiểu hành trình khám ngoại trú tại bệnh viện công,
tập trung vào các bước người bệnh phải thực hiện và cách các bộ phận phối hợp
với nhau. Những vấn đề như thời gian chờ, cách gọi lượt, việc chuyển người bệnh
giữa các khu vực và khả năng theo dõi kết quả được ghi nhận làm cơ sở cho việc
xác định nhu cầu của hệ thống.

### 1.3.2. Tìm hiểu nghiệp vụ và quy định

Các vai trò tham gia, điều kiện phục vụ và quy tắc xử lý trong hành trình khám
được tổng hợp thành các yêu cầu nghiệp vụ. Nội dung này bao gồm việc đặt và hủy
lịch, tiếp nhận, cấp phiếu, gọi lượt, thực hiện cận lâm sàng, quay lại đọc kết
quả, kê toa, phát thuốc và xử lý các trường hợp người bệnh vắng mặt hoặc hủy
lượt.

### 1.3.3. Nghiên cứu mô hình, phương pháp, giải thuật và công nghệ

Đề tài sử dụng cách tiếp cận hướng đối tượng và các biểu đồ UML để mô tả yêu
cầu, hành vi và cấu trúc của hệ thống. Mô hình phát triển được lựa chọn nhằm
phân chia các nhóm nghiệp vụ, giúp các bộ phận có thể phối hợp nhưng vẫn giữ
được ranh giới xử lý riêng. Các công nghệ được sử dụng và lý do lựa chọn sẽ
được trình bày cụ thể ở Chương 2.

### 1.3.4. Phân tích, thiết kế, hiện thực, kiểm thử và đánh giá

Sau khi xác định nhu cầu và phạm vi, đề tài lập danh sách yêu cầu, mô tả các
Use Case và xây dựng các biểu đồ phân tích. Từ kết quả phân tích, hệ thống được
thiết kế, hiện thực trên hai hướng sử dụng là Patient Mobile và Hospital Web.
Các chức năng được kiểm thử theo những tình huống đã xác định, sau đó đánh giá
dựa trên mức độ đúng đắn, tính thuận tiện và khả năng đáp ứng quy trình.

## 1.4. Phạm vi đề tài

### 1.4.1. Phạm vi thực hiện

Đề tài tập trung vào hành trình khám ngoại trú, gồm các nhóm chức năng chính:

- đăng ký, quản lý hồ sơ và đặt lịch khám;
- tiếp nhận người bệnh, cấp phiếu và xác nhận có mặt;
- theo dõi và điều phối lượt chờ tại phòng khám, khu cận lâm sàng và nhà thuốc;
- thực hiện khám, tạo chỉ định và phát hành kết quả cận lâm sàng;
- đưa người bệnh quay lại gặp bác sĩ để đọc kết quả, kê toa và hẹn tái khám;
- đối chiếu toa, phát thuốc và hoàn tất lượt khám.

Bệnh nhân sử dụng Patient Mobile để theo dõi hành trình và nhận thông tin cần
thiết. Bác sĩ, nhân viên tiếp nhận, kỹ thuật viên cận lâm sàng và nhân viên phát
thuốc sử dụng Hospital Web để thực hiện công việc theo vai trò. Phiên bản MVP
mô hình hóa khả năng mở rộng cho nhiều phòng phục vụ, nhưng chỉ cần cấu hình một
phòng hoạt động cho mỗi khoa trong quá trình minh họa.

### 1.4.2. Phần mô phỏng

Đề tài chỉ xét người bệnh tự chi trả. Các khoản phí và việc thanh toán được mô
phỏng để minh họa cách hệ thống ghi nhận khoản đã trả, tổng hợp chi phí và xác
định tình trạng thanh toán ở các thời điểm phù hợp trong hành trình. Đây không
phải là kết nối với cổng thanh toán hoặc quy trình quyết toán thực tế của bệnh
viện.

### 1.4.3. Phần ngoài phạm vi

Đề tài chưa triển khai cấp cứu, điều trị nội trú, quản lý giường bệnh, quản lý
kho và tồn dược, tích hợp thiết bị y tế hoặc chuẩn PACS/DICOM. Các dịch vụ thanh
toán và hoàn tiền production, danh mục giá thực tế, cũng như việc gửi SMS và
email tự động cũng nằm ngoài phạm vi của phiên bản này.
