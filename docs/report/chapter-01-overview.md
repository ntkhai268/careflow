# CHƯƠNG 1. TỔNG QUAN ĐỀ TÀI

## 1.1. Giới thiệu đề tài

Trong quá trình chuyển đổi số y tế, công nghệ thông tin ngày càng được ứng dụng
để nâng cao chất lượng phục vụ và hỗ trợ quản lý hoạt động khám chữa bệnh. Tuy
nhiên, quy trình khám ngoại trú tại nhiều bệnh viện công vẫn gồm nhiều bước rời
rạc như đăng ký khám, chờ đến lượt, khám lâm sàng, thực hiện xét nghiệm, nhận
kết quả, toa thuốc và lịch tái khám. Điều này khiến bệnh nhân khó chủ động theo
dõi hành trình khám, đồng thời làm tăng khối lượng công việc cho bác sĩ và nhân
viên y tế.

Từ thực tế trên, đề tài **“Xây dựng hệ thống phần mềm trợ giúp khám chữa bệnh tại
bệnh viện công theo kiến trúc Microservices”** được thực hiện nhằm xây dựng hệ
thống CareFlow hỗ trợ số hóa và điều phối quy trình khám ngoại trú.

CareFlow gồm ứng dụng di động dành cho bệnh nhân, ứng dụng web dành cho bác sĩ
và nhân viên y tế, cùng các dịch vụ backend phục vụ xác thực, quản lý hồ sơ bệnh
nhân, lịch khám, hàng đợi, phiên khám, xét nghiệm, toa thuốc và thông báo. Hệ
thống hỗ trợ bệnh nhân đặt lịch, nhận phiếu khám và mã QR, theo dõi lượt chờ,
nhận thông báo, xem kết quả, toa thuốc và lịch tái khám. Bác sĩ có thể tra cứu
hồ sơ, ghi nhận thông tin khám, chẩn đoán, tạo chỉ định và kê toa trên ứng dụng
web.

Hệ thống được thiết kế theo kiến trúc Microservices, trong đó các nhóm nghiệp vụ
được tách thành những dịch vụ có trách nhiệm riêng. Cách tổ chức này giúp hệ
thống dễ phát triển, kiểm thử, triển khai và mở rộng theo nhu cầu.

## 1.2. Lý do chọn đề tài

Quy trình khám ngoại trú có sự tham gia của nhiều bộ phận và phát sinh nhiều
loại dữ liệu khác nhau. Bệnh nhân có thể phải di chuyển giữa khu tiếp nhận,
phòng khám, khu cận lâm sàng và nhà thuốc nhưng chưa có công cụ thống nhất để
theo dõi tiến trình. Việc thiếu liên kết thông tin cũng có thể dẫn đến nhập liệu
lặp lại, cập nhật chậm và khó phối hợp giữa các bộ phận.

Bên cạnh đó, việc quản lý lượt khám theo phương pháp thủ công khiến bệnh nhân
khó biết thời điểm đến lượt, còn nhân viên y tế phải dành nhiều thời gian hướng
dẫn và gọi bệnh nhân. Một hệ thống sử dụng phiếu khám điện tử, mã QR, hàng đợi
theo từng điểm phục vụ và thông báo theo thời gian thực có thể giúp quy trình
minh bạch, thuận tiện hơn.

Kiến trúc Microservices được lựa chọn vì các nghiệp vụ như xác thực, đặt lịch,
quản lý hàng đợi, khám bệnh, xét nghiệm và kê toa có ranh giới tương đối độc
lập. Kiến trúc này phù hợp với mục tiêu phân chia công việc, phát triển từng
thành phần riêng biệt và tích hợp chúng thành một quy trình hoàn chỉnh. Đề tài
đồng thời tạo cơ hội vận dụng các kiến thức về API Gateway, JWT, service
discovery, giao tiếp sự kiện, WebSocket, cơ sở dữ liệu và container hóa vào một
bài toán thực tế.

## 1.3. Mục tiêu nghiên cứu

Mục tiêu tổng quát của đề tài là phân tích, thiết kế và xây dựng hệ thống
CareFlow hỗ trợ số hóa quy trình khám ngoại trú tại bệnh viện công theo kiến
trúc Microservices, qua đó giúp bệnh nhân theo dõi hành trình khám và hỗ trợ
bác sĩ, nhân viên y tế xử lý thông tin thuận tiện hơn.

Các mục tiêu cụ thể gồm:

1. Phân tích quy trình khám ngoại trú, xác định tác nhân, chức năng và các bước
   trao đổi thông tin trong hệ thống.
2. Thiết kế kiến trúc Microservices và xác định trách nhiệm của từng dịch vụ.
3. Xây dựng ứng dụng Mobile hỗ trợ bệnh nhân quản lý hồ sơ, đặt lịch, nhận phiếu
   khám, theo dõi lượt và xem thông tin sau khám.
4. Xây dựng ứng dụng Web hỗ trợ bác sĩ và nhân viên y tế quản lý lượt khám, tra
   cứu hồ sơ, ghi nhận chẩn đoán, chỉ định và toa thuốc.
5. Xây dựng cơ chế quản lý hàng đợi, xác nhận bệnh nhân bằng mã QR và gửi thông
   báo tại các mốc quan trọng.
6. Tích hợp các dịch vụ thông qua REST API và sự kiện bất đồng bộ, đồng thời
   kiểm thử các chức năng và luồng nghiệp vụ chính.
7. Nghiên cứu khả năng ứng dụng phân tích dữ liệu và trợ lý lâm sàng ở vai trò
   hỗ trợ bác sĩ.

## 1.4. Phạm vi áp dụng

Đề tài tập trung vào quy trình khám ngoại trú có hẹn hoặc không hẹn tại bệnh
viện công. Phạm vi nghiệp vụ chính bắt đầu từ đăng ký tài khoản, quản lý hồ sơ,
đặt lịch và nhận phiếu khám; tiếp tục với xác nhận có mặt, xếp hàng, khám lâm
sàng, thực hiện chỉ định cận lâm sàng; và kết thúc bằng việc nhận kết quả, toa
thuốc, lịch tái khám và lưu lịch sử khám.

Các nhóm người sử dụng chính gồm bệnh nhân, bác sĩ, nhân viên tiếp nhận, kỹ
thuật viên cận lâm sàng và quản trị viên. Bệnh nhân sử dụng ứng dụng Mobile;
các nhóm nhân sự bệnh viện sử dụng ứng dụng Web và được phân quyền theo vai
trò.

Hệ thống được xây dựng ở mức thử nghiệm và trình diễn. Đề tài không hướng đến
thay thế toàn bộ hệ thống thông tin bệnh viện và không triển khai đầy đủ các
nghiệp vụ cấp cứu, điều trị nội trú, quyết toán bảo hiểm y tế, quản lý kho dược,
PACS, xử lý ảnh y khoa hoặc thanh toán thực tế. Chức năng eKYC, thanh toán và
trợ lý lâm sàng có thể được mô phỏng hoặc triển khai ở mức hỗ trợ. Mọi gợi ý từ
AI chỉ có tính chất tham khảo, không thay thế quyết định chuyên môn của bác sĩ.
