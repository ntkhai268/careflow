# CHƯƠNG 2. CƠ SỞ KHOA HỌC CỦA ĐỀ TÀI

Chương này trình bày những kiến thức được nghiên cứu để xây dựng CareFlow,
không nhằm giới thiệu công nghệ theo kiểu liệt kê định nghĩa. Mỗi cơ sở nghiệp
vụ, phương pháp, giải thuật hoặc công nghệ đều được gắn với một vấn đề cụ thể
của hành trình khám ngoại trú. Kết quả của chương là nền tảng để phân tích hệ
thống ở Chương 3 và thiết kế phần mềm ở Chương 4.

## 2.1. Cơ sở nghiệp vụ khám ngoại trú

Khám ngoại trú là quá trình người bệnh được tiếp nhận, khám, thực hiện các chỉ
định cần thiết và rời bệnh viện trong ngày. Cấu trúc tiếp nhận–khám lâm
sàng–cận lâm sàng–quay lại bác sĩ–nhận toa được tham chiếu từ [Quyết định
1313/QĐ-BYT về quy trình khám bệnh tại Khoa Khám bệnh](https://soyte.laichau.gov.vn/upload/2000987/20221006/1313_qd-byt_184515-2_c5eb210b64.pdf).
Một hành trình có thể đi qua nhiều điểm phục vụ nhưng phải giữ được liên kết
giữa bệnh nhân, lịch khám, phiên khám, kết quả và toa thuốc.

### 2.1.1. Đặt khám và tiếp nhận

Bệnh nhân đăng ký hồ sơ, lựa chọn khoa, ngày và ca khám. Lịch hợp lệ cần gắn với
khoa, capacity và phòng phục vụ. CareFlow mô hình hóa `Department 1:N
ClinicRoom`; dữ liệu MVP chỉ có một phòng active cho mỗi khoa nhưng thiết kế
không giới hạn quan hệ ở 1:1. Sau khi lịch được xác nhận, hệ thống cấp Visit
Ticket và số thứ tự. Bệnh viện hiển thị một QR check-in gắn với phòng và phiên
khám.

Khi đến nơi, bệnh nhân dùng Patient Mobile quét QR, gửi vị trí thiết bị và được
xác nhận có mặt nếu mã QR hợp lệ, đúng lịch/phòng/phiên và nằm trong bán kính
cho phép của bệnh viện. Việc cấp phiếu trước không đồng nghĩa với việc bệnh
nhân đã tham gia active queue; lượt khám ban đầu chỉ active sau `CHECKED_IN`.
Người không sử dụng điện thoại vẫn được nhân viên tiếp nhận hỗ trợ tại quầy
theo cùng quy tắc kiểm tra phiếu.

### 2.1.2. Khám ban đầu và cận lâm sàng

Bác sĩ gọi lượt, xác nhận người bệnh, ghi nhận triệu chứng, sinh hiệu, chẩn
đoán sơ bộ và quyết định có cần cận lâm sàng hay không. Nếu không có chỉ định,
bác sĩ có thể kết luận, kê toa hoặc hẹn tái khám. Nếu có chỉ định, Laboratory
Order ghi các hạng mục cần thực hiện và điều kiện liên quan.

Mỗi khu cận lâm sàng là một điểm phục vụ có hàng đợi riêng. Khi order đủ điều
kiện, bệnh nhân được đưa vào queue thực hiện mà không phải tạo lịch khám mới.
Kỹ thuật viên thực hiện, nhập dữ liệu và phát hành kết quả theo quyền được phân
công.

### 2.1.3. Đọc kết quả, kê toa và phát thuốc

Khi tất cả kết quả bắt buộc đã sẵn sàng, bệnh nhân quay lại gặp bác sĩ. CareFlow
biểu diễn bước này bằng phase/lane `RESULT_REVIEW` trong consultation hiện tại,
không tạo Appointment giả. Bác sĩ đọc kết quả, hoàn thiện chẩn đoán, kê toa và
hẹn tái khám nếu cần.

Toa đã `CONFIRMED` tạo lượt `PHARMACY_DISPENSING` tại quầy thuốc. Nhân viên cấp
phát gọi FIFO, đối chiếu danh tính và toa, sau đó xác nhận đã phát. Như vậy toàn
bộ hành trình vẫn truy vết được từ lịch ban đầu đến kết quả và thuốc.

## 2.2. Các quy tắc nghiệp vụ liên quan

### 2.2.1. Đối tượng ưu tiên

Theo khoản 2 Điều 3 Luật Khám bệnh, chữa bệnh số 15/2023/QH15, cơ sở khám bệnh,
chữa bệnh phải ưu tiên khám bệnh, chữa bệnh đối với các trường hợp sau:

- người bệnh trong tình trạng cấp cứu;
- trẻ em dưới 06 tuổi;
- phụ nữ có thai;
- người khuyết tật đặc biệt nặng;
- người khuyết tật nặng;
- người từ đủ 75 tuổi trở lên;
- người có công với cách mạng, phù hợp với đặc thù của cơ sở khám bệnh, chữa bệnh.

Danh sách trên là căn cứ để cơ sở khám bệnh, chữa bệnh xác định đối tượng được
xem xét ưu tiên; luật không quy định các nhóm này phải có phòng khám hoặc bác sĩ
riêng. Đối với CareFlow, trường hợp cấp cứu được chuyển sang quy trình cấp cứu
và không đưa vào hàng đợi khám ngoại trú của MVP. Các trường hợp còn lại có thể
được phục vụ cùng phòng với lượt thường và được đưa vào nhóm ưu tiên khi có đủ
căn cứ xác nhận.

Quyền ưu tiên phải được nhân viên có thẩm quyền xác nhận cùng lý do; bệnh nhân
không tự khai ưu tiên trên Mobile. Ưu tiên không đồng nghĩa với phòng khám riêng
hoặc bác sĩ riêng. Lượt ưu tiên và lượt thường có thể cùng được phục vụ tại một
phòng, còn CareFlow chỉ hỗ trợ sắp xếp thứ tự đề xuất.

### 2.2.2. Thứ tự trong từng hàng và nhiều làn

Mỗi hàng giữ nguyên tắc FIFO theo `queuedAt`. Phòng khám có ba lane logic:
`PRIORITY`, `NORMAL` và `RESULT_REVIEW`. Queue cận lâm sàng và quầy thuốc là
các queue theo điểm phục vụ, không gộp vào ba lane phòng khám.

Bác sĩ là người bấm gọi bệnh nhân. Hệ thống chỉ hiển thị danh sách và
`recommendedNext`; vì vậy thuật toán điều phối là cơ chế hỗ trợ quyết định,
không tự động thay bác sĩ gọi người bệnh.

### 2.2.3. Xử lý lỡ lượt

Khi người bệnh không có mặt sau khi được gọi, entry chuyển `MISSED` và lưu lịch
sử gọi. Nếu được phép quay lại, thao tác requeue đặt lại `queuedAt` theo chính
sách của điểm phục vụ. Hệ thống không âm thầm đưa một lượt đã lỡ về đầu hàng vì
điều đó làm sai FIFO và gây khó giải thích cho những người đang chờ.

### 2.2.4. Quy tắc dữ liệu và bảo mật

Việc bảo vệ hồ sơ và thông tin sức khỏe được đặt trong bối cảnh quyền được giữ
bí mật thông tin của người bệnh theo [Luật Khám bệnh, chữa bệnh số
15/2023/QH15](https://vanban.chinhphu.vn/?classid=1&docid=207396&pageid=27160)
và yêu cầu bảo vệ dữ liệu cá nhân theo [Nghị định
13/2023/NĐ-CP](https://vanban.chinhphu.vn/default.aspx?docid=207759&pageid=27160).
Trong phạm vi thiết kế CareFlow, các nguyên tắc này được cụ thể hóa như sau:

- Bệnh nhân chỉ xem hồ sơ thuộc quyền sở hữu của mình.
- Bác sĩ, kỹ thuật viên và nhân viên chỉ truy cập dữ liệu trong phạm vi được
  phân công.
- QR check-in chỉ chứa token ký số theo phòng/phiên, không chứa bệnh án hoặc
  thông tin nhạy cảm ở dạng đọc trực tiếp. Tọa độ được kiểm tra ở phía máy chủ
  với tâm và bán kính địa điểm bệnh viện.
- Mọi thay đổi quan trọng phải có actor, thời điểm và correlation ID.
- Message được giao lại không được tạo Appointment, Queue Entry, kết quả, toa
  hoặc Notification trùng.

## 2.3. Phương pháp phân tích và thiết kế

### 2.3.1. Phân tích và thiết kế hướng đối tượng

Phương pháp hướng đối tượng xem hệ thống như tập hợp các đối tượng có trạng
thái, trách nhiệm và quan hệ cộng tác. Trong CareFlow, các đối tượng như
Appointment, Queue Entry, Consultation, Laboratory Order và Prescription có
vòng đời riêng nhưng liên kết thành một hành trình.

Phân tích trả lời phần mềm phải hỗ trợ tình huống nào từ góc nhìn nghiệp vụ.
Thiết kế trả lời bên trong phần mềm phải tổ chức Form, Process và Entity như thế
nào để thực hiện tình huống đó. Phân biệt hai mức này giúp Chương 3 không bị
trộn API, database hoặc framework vào mô tả hiện trạng.

### 2.3.2. Use Case

Use Case biểu diễn một tình huống mà actor nghiệp vụ cần hệ thống trợ giúp. Tên
Use Case phải phản ánh mục tiêu như “Đặt lịch khám”, “Check-in bằng QR” hoặc
“Gọi lượt và phát thuốc”, không phải tên endpoint hay tên service. Đăng nhập và
phân quyền được xem là điều kiện nền tảng, không phải Use Case nghiệp vụ cốt lõi.

Đặc tả Use Case gồm mục đích, actor chính và liên quan, tiền điều kiện, hậu điều
kiện thành công/thất bại, luồng chính và luồng ngoại lệ. Các quan hệ `include`
và `extend` chỉ được dùng khi có ý nghĩa tái sử dụng hoặc mở rộng luồng.

### 2.3.3. Các biểu đồ UML được sử dụng

| Biểu đồ | Vai trò trong CareFlow |
|---|---|
| Use Case Diagram | xác định actor, tình huống và ranh giới hệ thống |
| Sequence Diagram phân tích | xem CareFlow như hộp đen, thể hiện dữ liệu actor gửi và kết quả nhận |
| Activity Diagram | mô tả luồng chính, nhánh điều kiện và ngoại lệ |
| Class Diagram phân tích | xác định đối tượng nghiệp vụ, thuộc tính và quan hệ khái niệm |
| State Diagram | mô tả vòng đời và điều kiện chuyển trạng thái |
| Sequence Diagram thiết kế | mô tả hộp trắng gồm Form, API, Service, Repository, database và event |

Các sơ đồ của cùng một Use Case phải dùng thống nhất tên actor, message, entity
và trạng thái. Nếu State Diagram không cho phép một transition thì Sequence và
Test Case không được giả định transition đó hợp lệ.

## 2.4. Cơ sở về hàng đợi

### 2.4.1. FIFO và queue theo điểm phục vụ

FIFO phục vụ phần tử có thời điểm vào hàng sớm nhất. Cách này dễ giải thích,
phù hợp cho một điểm phục vụ đồng nhất và được CareFlow dùng bên trong từng
lane. Mỗi phòng khám, khu cận lâm sàng hoặc quầy thuốc có queue riêng vì khác
nhân lực, vị trí và loại công việc.

### 2.4.2. Round Robin 1:1:1

Ưu tiên tuyệt đối lane `PRIORITY` có thể làm lane `NORMAL` hoặc `RESULT_REVIEW`
chờ vô hạn. CareFlow sử dụng Round Robin trọng số bằng nhau để luân phiên đề
xuất đầu ba lane theo tỷ lệ 1:1:1. Nếu lane kế tiếp rỗng, thuật toán bỏ qua và
chọn lane có dữ liệu tiếp theo; FIFO bên trong lane vẫn được giữ.

Thuật toán duy trì `lastServedLane`. Sau mỗi lần gọi thành công, con trỏ chuyển
sang lane kế tiếp. Việc cập nhật entry và con trỏ phải nguyên tử để hai bác sĩ
không cùng gọi một lượt hoặc làm mất thứ tự.

### 2.4.3. `MISSED` và requeue

Entry được gọi nhưng bệnh nhân không có mặt chuyển `MISSED`. Requeue là thao tác
có chủ đích của người có quyền; hệ thống lưu lịch sử cũ và cấp thời điểm xếp
hàng mới. Cách này bảo toàn khả năng truy vết và tránh việc lượt lỡ chen vào đầu
hàng không có lý do.

## 2.5. Kiến trúc và công nghệ được lựa chọn

### 2.5.1. Kiến trúc Microservices

CareFlow dùng Microservices để tách các năng lực Appointment, Queue,
Consultation, Laboratory, Prescription và Notification. Mỗi service sở hữu dữ
liệu và quy tắc trạng thái của mình, công khai REST API hoặc event contract.
Phân rã này cho phép đội phát triển thay đổi hoặc triển khai một miền tương đối
độc lập và phù hợp với hành trình có nhiều điểm phục vụ.

So với Monolithic, Microservices tăng chi phí giao tiếp, quan sát và nhất quán
phân tán. Đề tài vẫn chọn phương án này vì mục tiêu nghiên cứu kiến trúc phân
tán và ranh giới nghiệp vụ là nội dung cốt lõi; các rủi ro được kiểm soát bằng
contract, outbox, idempotency, retry và tracing.

### 2.5.2. REST API và RabbitMQ

REST được dùng cho lệnh/truy vấn cần phản hồi trực tiếp, như đặt lịch, đọc active
queue hoặc bấm gọi lượt. API dùng JSON, mã trạng thái HTTP, JWT và response
contract thống nhất. So với RPC gắn chặt, REST dễ kiểm thử và phù hợp cho
Patient Mobile, Hospital Web cùng các client khác nhau.

RabbitMQ truyền các sự kiện như `AppointmentConfirmed`, `PatientCalled`,
`ResultPublished` và `PrescriptionIssued`. Event giúp producer không phải gọi
tuần tự mọi consumer trong request. So với chỉ dùng REST đồng bộ, RabbitMQ hỗ
trợ retry và tách vòng đời service; đổi lại consumer phải idempotent. Producer
ghi outbox cùng transaction với aggregate, consumer lưu `eventId` đã xử lý.

WebSocket/STOMP được dùng tại Notification để đẩy cập nhật nhanh. REST vẫn là
nguồn tải lại khi client mất kết nối, nên WebSocket không trở thành nguồn sự
thật duy nhất.

### 2.5.3. Database per Service và PostgreSQL

Mỗi service dùng schema/database riêng; service khác chỉ nhận định danh và dữ
liệu contract cần thiết. Cách này bảo vệ quyền sở hữu aggregate, giảm coupling
schema và cho phép migration độc lập. Giao dịch xuyên service được thay bằng
eventual consistency có kiểm soát, không dùng transaction phân tán.

PostgreSQL được chọn vì hỗ trợ transaction ACID, constraint, chỉ mục, JSON khi
cần và hệ sinh thái JPA/Flyway ổn định. So với database NoSQL thuần túy, mô hình
quan hệ phù hợp hơn với dữ liệu lịch, phòng, trạng thái và các ràng buộc cần
nhất quán của CareFlow. Flyway quản lý migration có phiên bản.

### 2.5.4. Spring Boot và Spring Cloud

Backend dùng Java 21 và Spring Boot để tổ chức Controller, Application Service,
Domain và Repository; Spring Data JPA truy cập PostgreSQL, Spring Security xử
lý JWT và validation kiểm tra DTO. Spring Cloud Gateway cung cấp điểm vào chung,
filter, CORS, correlation ID; Eureka cung cấp service discovery.

So với tự xây framework hoặc dùng nhiều thư viện rời, hệ sinh thái Spring giảm
chi phí tích hợp và có công cụ kiểm thử trưởng thành. Hạn chế là cấu hình và tài
nguyên runtime lớn hơn, nhưng phù hợp với năng lực nhóm và mục tiêu đồ án.

### 2.5.5. Next.js, React và Flutter

Hospital Web dùng Next.js, React và TypeScript để xây giao diện bác sĩ, nhân
viên tiếp nhận, kỹ thuật viên và quầy thuốc. Component và type giúp dùng chung
control, kiểm tra contract sớm và cập nhật bảng queue theo trạng thái. So với
JavaScript không kiểu, TypeScript giảm lỗi payload và enum trạng thái.

Patient Mobile dùng Flutter/Dart với repository boundary để một codebase phục
vụ nhiều nền tảng. Riverpod quản lý state của hành trình, QR, queue, kết quả,
toa và Notification. Flutter được chọn thay cho hai ứng dụng native riêng để
giảm khối lượng triển khai trong đồ án; đổi lại cần kiểm thử kỹ lưu trữ local,
camera và vòng đời ứng dụng.

### 2.5.6. Docker và Docker Compose

Docker đóng gói service cùng runtime. Docker Compose mô tả container, network,
volume, health check, biến môi trường, PostgreSQL và RabbitMQ để môi trường phát
triển có thể khởi động nhất quán. Maven chịu trách nhiệm dependency, build và
test backend.

So với cài thủ công từng dịch vụ trên máy, container giảm khác biệt môi trường
và giúp kiểm thử startup/migration. Compose phù hợp cho MVP một node; triển khai
production nhiều node có thể dùng nền tảng điều phối container chuyên dụng.

## Tài liệu tham khảo chính của chương

1. Bộ Y tế, [Quyết định 1313/QĐ-BYT về hướng dẫn quy trình khám bệnh tại Khoa Khám bệnh](https://soyte.laichau.gov.vn/upload/2000987/20221006/1313_qd-byt_184515-2_c5eb210b64.pdf), 2013.
2. Quốc hội, [Luật Khám bệnh, chữa bệnh số 15/2023/QH15](https://vanban.chinhphu.vn/?classid=1&docid=207396&pageid=27160), 2023.
3. Chính phủ, [Nghị định 13/2023/NĐ-CP về bảo vệ dữ liệu cá nhân](https://vanban.chinhphu.vn/default.aspx?docid=207759&pageid=27160), 2023.
4. Martin Fowler, James Lewis, [“Microservices”](https://martinfowler.com/articles/microservices.html), 2014.
5. IETF, [“RFC 7519: JSON Web Token (JWT)”](https://www.rfc-editor.org/rfc/rfc7519), 2015.
6. IETF, [“RFC 6455: The WebSocket Protocol”](https://www.rfc-editor.org/rfc/rfc6455), 2011.
7. Spring, [“Spring Cloud Gateway Reference Documentation”](https://docs.spring.io/spring-cloud-gateway/reference/).
8. RabbitMQ, [“RabbitMQ Tutorials”](https://www.rabbitmq.com/tutorials).
9. PostgreSQL Global Development Group, [“PostgreSQL Documentation”](https://www.postgresql.org/docs/).
10. Docker, [“Docker Documentation”](https://docs.docker.com/).
11. Flutter, [“Flutter Architectural Overview”](https://docs.flutter.dev/resources/architectural-overview).
