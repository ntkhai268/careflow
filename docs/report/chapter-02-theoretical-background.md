# CHƯƠNG 2. CƠ SỞ LÝ THUYẾT

Chương này trình bày những cơ sở lý thuyết và công nghệ chính được sử dụng để
xây dựng hệ thống CareFlow. Nội dung tập trung vào kiến trúc Microservices, cơ
chế giao tiếp và bảo mật, mô hình hàng đợi, cùng các nền tảng kỹ thuật trực tiếp
liên quan đến hệ thống. Các nội dung phân tích nghiệp vụ, thiết kế chi tiết và
cách triển khai CareFlow sẽ được trình bày ở các chương sau.

## 2.1. Tổng quan kiến trúc Microservices

### 2.1.1. Khái niệm và đặc điểm

Microservices là một phong cách kiến trúc trong đó hệ thống được tổ chức thành
nhiều dịch vụ nhỏ, mỗi dịch vụ tập trung xử lý một nhóm nghiệp vụ cụ thể. Các
dịch vụ có thể được phát triển và triển khai tương đối độc lập, đồng thời giao
tiếp với nhau thông qua những hợp đồng đã xác định như REST API hoặc sự kiện.

Khác với việc chia ứng dụng đơn thuần theo các lớp giao diện, xử lý và dữ liệu,
Microservices thường được phân chia theo miền nghiệp vụ. Mỗi dịch vụ chịu trách
nhiệm đối với dữ liệu và quy tắc nghiệp vụ thuộc phạm vi của mình. Ví dụ, dịch
vụ quản lý lịch hẹn chịu trách nhiệm về ngày khám, khung giờ và trạng thái lịch;
dịch vụ quản lý hàng đợi chịu trách nhiệm về số thứ tự và trạng thái chờ. Một
dịch vụ không nên truy cập trực tiếp cơ sở dữ liệu của dịch vụ khác mà cần trao
đổi qua hợp đồng tích hợp.

Kiến trúc Microservices có một số đặc điểm chính:

- Hệ thống được chia theo chức năng hoặc miền nghiệp vụ.
- Mỗi dịch vụ có phạm vi trách nhiệm và dữ liệu sở hữu rõ ràng.
- Các dịch vụ giao tiếp thông qua giao diện được công bố.
- Một dịch vụ có thể được xây dựng, kiểm thử và triển khai riêng.
- Lỗi tại một thành phần cần được cô lập để hạn chế ảnh hưởng dây chuyền.
- Hệ thống cần cơ chế quan sát, quản lý cấu hình và phát hiện dịch vụ.

Microservices không chỉ là việc chia nhỏ mã nguồn. Nếu các service vẫn dùng
chung cơ sở dữ liệu, phụ thuộc chặt vào cấu trúc nội bộ hoặc buộc phải triển
khai đồng thời, hệ thống vẫn mang nhiều đặc điểm của một ứng dụng nguyên khối
phân tán. Vì vậy, ranh giới nghiệp vụ và hợp đồng giữa các dịch vụ là yếu tố
quan trọng hơn số lượng service.

### 2.1.2. Ưu điểm và hạn chế

Ưu điểm nổi bật của Microservices là khả năng phát triển và mở rộng từng thành
phần. Nhóm phát triển có thể thay đổi một dịch vụ mà không phải đóng gói lại
toàn bộ hệ thống nếu hợp đồng tích hợp được giữ ổn định. Những dịch vụ có tần
suất truy cập lớn cũng có thể được tăng số lượng phiên bản chạy độc lập với các
dịch vụ còn lại.

Việc phân chia theo miền nghiệp vụ còn giúp mã nguồn dễ tổ chức hơn. Quy tắc về
lịch hẹn, hàng đợi hay toa thuốc được đặt trong đúng dịch vụ chịu trách nhiệm,
từ đó giảm nguy cơ một thay đổi lan sang nhiều phần không liên quan. Khả năng cô
lập lỗi cũng được cải thiện: một chức năng tạm thời không khả dụng không nhất
thiết làm toàn bộ ứng dụng dừng hoạt động.

Tuy nhiên, Microservices làm tăng độ phức tạp của hệ thống phân tán. Một thao
tác nghiệp vụ có thể đi qua nhiều dịch vụ và mạng có thể xảy ra lỗi ở bất kỳ
bước nào. Hệ thống phải xử lý timeout, gửi trùng thông điệp, dữ liệu cập nhật
không đồng thời và theo dõi yêu cầu xuyên dịch vụ. Hoạt động triển khai cũng cần
quản lý nhiều ứng dụng, cơ sở dữ liệu, log và cấu hình hơn.

Do đó, Microservices phù hợp khi bài toán có nhiều miền nghiệp vụ tương đối rõ,
có nhu cầu phát triển độc lập hoặc mở rộng lâu dài. Việc lựa chọn kiến trúc này
cần đi kèm quy ước API, quản lý phiên bản sự kiện, kiểm thử tích hợp và cơ chế
giám sát phù hợp.

### 2.1.3. So sánh Microservices và Monolithic

Monolithic là kiến trúc trong đó các chức năng chính được đóng gói và triển
khai như một ứng dụng thống nhất. Kiến trúc này thường đơn giản hơn trong giai
đoạn đầu: việc chạy, kiểm thử và theo dõi giao dịch trong cùng một tiến trình
tương đối thuận tiện. Tuy nhiên, khi ứng dụng phát triển lớn, các module dễ phụ
thuộc lẫn nhau và một thay đổi nhỏ có thể yêu cầu triển khai lại toàn hệ thống.

Sự khác biệt cơ bản giữa hai kiến trúc được tóm tắt như sau:

| Tiêu chí | Monolithic | Microservices |
|---|---|---|
| Đơn vị triển khai | Toàn bộ ứng dụng | Từng dịch vụ |
| Phân chia chức năng | Module trong cùng ứng dụng | Dịch vụ theo miền nghiệp vụ |
| Dữ liệu | Thường dùng chung cơ sở dữ liệu | Dữ liệu thuộc sở hữu từng dịch vụ |
| Giao tiếp | Lời gọi trong tiến trình | API hoặc thông điệp qua mạng |
| Mở rộng | Thường mở rộng toàn ứng dụng | Có thể mở rộng riêng từng dịch vụ |
| Xử lý lỗi | Dễ theo dõi hơn | Cần xử lý lỗi phân tán |
| Vận hành | Đơn giản ở quy mô nhỏ | Yêu cầu nhiều công cụ và quy ước hơn |

Không thể khẳng định Microservices luôn tốt hơn Monolithic. Một ứng dụng nhỏ có
thể phù hợp với Monolithic để giảm chi phí vận hành. Đối với CareFlow, các nhóm
nghiệp vụ như tài khoản, bệnh nhân, lịch khám, hàng đợi và phiên khám có vòng
đời riêng, nên Microservices phù hợp với mục tiêu phân chia trách nhiệm và phát
triển hệ thống theo từng phần.

## 2.2. Các thành phần cơ bản của kiến trúc Microservices

### 2.2.1. API Gateway và Service Discovery

Trong một hệ thống có nhiều dịch vụ, việc để ứng dụng khách kết nối trực tiếp
đến từng service làm tăng độ phức tạp và khiến cấu trúc nội bộ bị lộ ra ngoài.
API Gateway giải quyết vấn đề này bằng cách cung cấp một điểm vào chung. Gateway
tiếp nhận yêu cầu, xác định tuyến phù hợp và chuyển tiếp yêu cầu đến dịch vụ
đích. Ngoài định tuyến, Gateway có thể thực hiện xác thực token, xử lý CORS,
ghi nhận mã tương quan và áp dụng một số chính sách dùng chung.

Theo cơ chế của Spring Cloud Gateway, yêu cầu phù hợp với một route sẽ đi qua
chuỗi bộ lọc trước khi được chuyển đến dịch vụ phía sau; các bộ lọc cũng có thể
xử lý phản hồi sau khi lời gọi hoàn tất. Mô hình này phù hợp để đặt các thao tác
dùng chung tại biên của hệ thống nhưng không nên đưa nghiệp vụ riêng của từng
service vào Gateway.

Khi các dịch vụ được triển khai động, địa chỉ của chúng có thể thay đổi. Service
Discovery cung cấp sổ đăng ký để các service công bố tên, địa chỉ và trạng thái.
Thành phần cần gọi dịch vụ khác có thể tra cứu theo tên thay vì gắn cứng địa
chỉ. Eureka sử dụng cơ chế đăng ký và heartbeat; nếu một instance không tiếp
tục gửi tín hiệu trong khoảng thời gian quy định, nó có thể bị loại khỏi danh
sách khả dụng.

API Gateway và Service Discovery giải quyết hai vấn đề khác nhau nhưng thường
được kết hợp: Gateway là điểm truy cập từ bên ngoài, còn Service Discovery giúp
Gateway và các service tìm thấy instance đang hoạt động ở bên trong hệ thống.

### 2.2.2. Database per Service

Database per Service là nguyên tắc mỗi dịch vụ sở hữu dữ liệu thuộc miền nghiệp
vụ của mình. Service khác không được đọc hoặc sửa trực tiếp bảng dữ liệu đó.
Mọi tương tác cần thực hiện qua API hoặc sự kiện do service sở hữu dữ liệu cung
cấp. Cách tổ chức này bảo vệ ranh giới nghiệp vụ và cho phép một dịch vụ thay
đổi cấu trúc lưu trữ mà không ảnh hưởng trực tiếp đến thành phần khác.

Mỗi service có thể dùng một máy chủ cơ sở dữ liệu riêng hoặc dùng chung một hệ
quản trị nhưng tách database/schema và tài khoản truy cập. Điều quan trọng là
quyền sở hữu dữ liệu phải rõ ràng. Ví dụ, lịch hẹn thuộc Appointment Service,
trong khi trạng thái lượt chờ thuộc Queue Service; Queue Service không nên cập
nhật trực tiếp bảng lịch hẹn.

Đổi lại, giao dịch ACID xuyên nhiều service không còn thuận tiện như trong một
cơ sở dữ liệu chung. Hệ thống thường chấp nhận tính nhất quán cuối cùng: service
nguồn hoàn tất giao dịch cục bộ, sau đó phát sự kiện để các service liên quan
cập nhật dữ liệu của mình. Vì thông điệp có thể bị gửi hoặc xử lý lại, consumer
cần có tính idempotent, nghĩa là xử lý cùng một sự kiện nhiều lần không tạo ra
kết quả sai hoặc dữ liệu trùng.

### 2.2.3. Giao tiếp đồng bộ và bất đồng bộ

Giao tiếp đồng bộ được sử dụng khi bên gọi cần biết kết quả ngay để tiếp tục xử
lý. REST API qua HTTP là hình thức phổ biến. Ví dụ, thao tác đăng nhập cần trả
token ngay hoặc đặt lịch cần thông báo thành công, hết chỗ hay trùng lịch. Ưu
điểm của giao tiếp đồng bộ là luồng xử lý rõ ràng; hạn chế là bên gọi phụ thuộc
vào khả năng phản hồi của dịch vụ đích.

Giao tiếp bất đồng bộ được sử dụng để thông báo một sự việc đã xảy ra mà không
yêu cầu tất cả service liên quan phản hồi trong cùng thời điểm. Service nguồn
gửi thông điệp đến Message Broker, sau đó tiếp tục công việc; các consumer nhận
và xử lý thông điệp theo khả năng của mình. Ví dụ, sau khi bệnh nhân được xác
nhận vào hàng đợi, hệ thống có thể phát sự kiện để Notification Service tạo
thông báo.

Hai hình thức không loại trừ nhau. Một hệ thống thường dùng API cho command cần
kết quả trực tiếp và dùng event để lan truyền sự thật nghiệp vụ. Việc lựa chọn
cần dựa trên yêu cầu nhất quán, thời gian phản hồi và mức phụ thuộc chấp nhận
được giữa các service.

## 2.3. Giao tiếp và bảo mật hệ thống

### 2.3.1. RESTful API và kiến trúc hướng sự kiện

REST là phong cách thiết kế dịch vụ dựa trên tài nguyên và các phương thức của
HTTP. Tài nguyên được nhận diện bằng URI; các thao tác phổ biến sử dụng GET để
truy vấn, POST để tạo, PUT hoặc PATCH để cập nhật và DELETE để xóa. HTTP status
code thể hiện kết quả ở mức giao thức, trong khi nội dung JSON cung cấp dữ liệu
hoặc mô tả lỗi.

Một API rõ ràng cần quy định cấu trúc request, response, xác thực, mã lỗi và
phiên bản. API cũng cần kiểm tra dữ liệu đầu vào và không tiết lộ chi tiết nội
bộ trong thông báo lỗi. Trong Microservices, hợp đồng API ổn định giúp consumer
và provider phát triển độc lập hơn.

Kiến trúc hướng sự kiện tổ chức tích hợp xoay quanh các sự kiện nghiệp vụ. Sự
kiện thể hiện một sự việc đã xảy ra, chẳng hạn lịch khám đã được xác nhận hoặc
bệnh nhân đã check-in. Tên sự kiện vì thế thường được biểu diễn ở dạng quá khứ.
Một sự kiện nên chứa mã định danh, loại, phiên bản, thời điểm, aggregate liên
quan và payload cần thiết để consumer xử lý.

RabbitMQ là Message Broker trung gian giữa producer và consumer. Producer gửi
message đến exchange; exchange định tuyến message vào queue theo binding; sau
đó consumer đọc và xác nhận xử lý. Queue giúp tách thời điểm gửi và nhận, đồng
thời hỗ trợ lưu tạm thông điệp khi consumer chưa sẵn sàng. Cơ chế retry và dead
letter queue có thể được dùng cho những message xử lý thất bại, nhưng cần giới
hạn số lần thử và lưu nguyên nhân lỗi để tránh vòng lặp vô hạn.

### 2.3.2. Xác thực, phân quyền và JWT

Xác thực là quá trình xác định người dùng là ai; phân quyền là quá trình kiểm
tra người dùng đó được phép thực hiện hành động nào. Một hệ thống y tế cần kiểm
soát cả vai trò và quyền sở hữu dữ liệu. Việc một người dùng đã đăng nhập không
có nghĩa họ được phép xem hồ sơ của mọi bệnh nhân.

JSON Web Token (JWT) là định dạng gọn, an toàn với URL để biểu diễn các claim
được truyền giữa các bên. JWT thường gồm header, payload và chữ ký. Payload có
thể chứa mã người dùng, vai trò, đơn vị phát hành và thời điểm hết hạn; chữ ký
giúp bên nhận kiểm tra token không bị thay đổi. JWT có thể được gửi trong
`Authorization` header của yêu cầu HTTP.

Cần lưu ý JWT được ký không đồng nghĩa dữ liệu payload đã được mã hóa. Vì vậy,
không nên đưa thông tin bệnh án nhạy cảm vào token. Dịch vụ nhận token phải kiểm
tra chữ ký, thuật toán, đơn vị phát hành và thời hạn. Access token nên có thời
gian sống giới hạn; refresh token được quản lý riêng để phát hành access token
mới và có thể thu hồi phiên đăng nhập.

Trong kiến trúc có Gateway, Gateway có thể xác thực token trước khi chuyển tiếp.
Tuy nhiên, dịch vụ phía sau vẫn phải kiểm tra quyền phù hợp với nghiệp vụ. Các
header định danh do client tự gửi không được xem là nguồn tin cậy. Quyền truy
cập dữ liệu y tế cần dựa trên danh tính đã xác thực, vai trò và mối quan hệ giữa
người dùng với tài nguyên được yêu cầu.

### 2.3.3. WebSocket và cập nhật thời gian thực

HTTP truyền thống hoạt động theo mô hình client gửi yêu cầu rồi server trả phản
hồi. Nếu client cần biết trạng thái mới, ứng dụng có thể truy vấn lặp lại, nhưng
cách này tạo nhiều yêu cầu không cần thiết và có độ trễ. WebSocket cung cấp kênh
giao tiếp hai chiều tồn tại lâu dài giữa client và server, cho phép server chủ
động gửi dữ liệu sau khi kết nối được thiết lập.

Trong hệ thống khám bệnh, WebSocket phù hợp với thông báo thay đổi lượt chờ,
gọi bệnh nhân hoặc cập nhật kết quả. Tuy nhiên, thông báo thời gian thực không
nên là nguồn dữ liệu duy nhất. Người dùng có thể mất mạng hoặc đóng ứng dụng,
nên dữ liệu quan trọng vẫn cần được lưu và có API để tải lại. WebSocket đóng vai
trò giúp giao diện cập nhật nhanh, còn trạng thái nghiệp vụ chính vẫn thuộc về
service và cơ sở dữ liệu tương ứng.

## 2.4. Cơ sở lý thuyết về quản lý hàng đợi

### 2.4.1. Khái niệm hàng đợi và nguyên tắc FIFO

Hàng đợi là mô hình tổ chức các đối tượng chờ được phục vụ. Nguyên tắc FIFO
(First In, First Out) quy định đối tượng vào trước được xử lý trước. Trong phần
mềm, một phần tử hàng đợi thường có số thứ tự, thời điểm tham gia, điểm phục vụ
và trạng thái hiện tại.

Hàng đợi khám bệnh không chỉ là một danh sách số. Hệ thống cần phân biệt người
đã được cấp phiếu với người thực sự có mặt, người đang được gọi, đang được phục
vụ, đã hoàn tất hoặc lỡ lượt. Một vòng đời đơn giản có thể gồm:

```text
Đã cấp phiếu → Đã xác nhận có mặt → Được gọi → Đang phục vụ → Hoàn tất
```

Nếu người bệnh không có mặt khi được gọi, lượt có thể chuyển sang trạng thái lỡ
lượt và được đưa lại cuối hàng theo chính sách. Việc mô hình hóa trạng thái giúp
hệ thống tránh gọi cùng một lượt nhiều lần và cung cấp thông tin chính xác cho
người bệnh cũng như nhân viên y tế.

### 2.4.2. Ứng dụng hàng đợi trong khám ngoại trú

Trong bệnh viện, không nên coi toàn bộ người bệnh là một hàng đợi duy nhất. Mỗi
phòng khám, khu lấy mẫu hoặc điểm chẩn đoán hình ảnh có năng lực phục vụ và danh
sách chờ riêng. Vì vậy, hàng đợi cần gắn với điểm phục vụ và phiên làm việc cụ
thể.

Đối với bệnh nhân đặt lịch, số thứ tự có thể được cấp trước nhưng chỉ nên tham
gia hàng đợi hoạt động sau khi bệnh nhân đến và xác nhận có mặt. Quy tắc này
giúp tránh tình trạng danh sách chờ chứa nhiều người chưa đến bệnh viện. Khi có
chỉ định cận lâm sàng, hệ thống có thể tạo lượt tại điểm phục vụ tương ứng dựa
trên order đã được xác nhận, thay vì yêu cầu bệnh nhân đăng ký lại.

FIFO tạo ra nguyên tắc phục vụ dễ hiểu và minh bạch. Tuy nhiên, hệ thống vẫn cần
quy định rõ cách xử lý trường hợp đến muộn, lỡ lượt hoặc quay lại bác sĩ đọc kết
quả. Những chính sách này thuộc thiết kế nghiệp vụ của từng bệnh viện và phải
được biểu diễn thành trạng thái, điều kiện chuyển trạng thái cụ thể.

### 2.4.3. Mã QR trong xác nhận lượt khám

Mã QR là phương thức biểu diễn dữ liệu để thiết bị có camera có thể đọc nhanh.
Trong quy trình khám bệnh, QR có thể được gắn với phiếu khám điện tử và dùng để
xác nhận bệnh nhân đã đến đúng điểm tiếp nhận. Sau khi quét, hệ thống kiểm tra
hiệu lực của phiếu rồi mới đưa bệnh nhân vào hàng đợi hoạt động.

QR không nên chứa trực tiếp họ tên, chẩn đoán hoặc thông tin y tế nhạy cảm. Một
phương án an toàn hơn là mã chỉ chứa token ngẫu nhiên hoặc tham chiếu có thời
hạn; server sử dụng giá trị này để tra cứu dữ liệu sau khi kiểm tra chữ ký,
trạng thái và quyền truy cập. Mã cũng cần có cơ chế hết hạn hoặc vô hiệu hóa để
hạn chế việc sử dụng lại ảnh chụp cũ.

## 2.5. Các công nghệ sử dụng

### 2.5.1. Java, Spring Boot và Spring Cloud

Java là ngôn ngữ lập trình hướng đối tượng được sử dụng rộng rãi cho các hệ
thống backend. Hệ sinh thái thư viện phong phú, khả năng chạy đa nền tảng qua
JVM và công cụ kiểm thử ổn định giúp Java phù hợp với ứng dụng nghiệp vụ.
CareFlow sử dụng Java 21 cho các dịch vụ backend.

Spring Boot hỗ trợ xây dựng ứng dụng Spring độc lập với cơ chế cấu hình tự động,
dependency starter và web server nhúng. Lập trình viên có thể tổ chức ứng dụng
theo controller, service và repository, đồng thời tích hợp Spring Data JPA,
Spring Security, validation và actuator. Mã nguồn CareFlow sử dụng Spring Boot
3.3.5 làm nền tảng thống nhất cho các module backend.

Spring Cloud cung cấp các thành phần phục vụ hệ thống phân tán. CareFlow sử dụng
Spring Cloud Gateway làm điểm vào chung và Spring Cloud Netflix Eureka cho
Service Discovery. Sự kết hợp này giúp client truy cập qua một địa chỉ thống
nhất, trong khi các dịch vụ phía sau đăng ký và được tìm kiếm theo tên.

### 2.5.2. PostgreSQL, RabbitMQ và WebSocket

PostgreSQL là hệ quản trị cơ sở dữ liệu quan hệ mã nguồn mở, hỗ trợ giao dịch,
ràng buộc dữ liệu, chỉ mục và nhiều kiểu dữ liệu. Mô hình quan hệ phù hợp với
những dữ liệu có cấu trúc và yêu cầu toàn vẹn như tài khoản, lịch khám, lượt chờ
và toa thuốc. Trong CareFlow, các service nghiệp vụ sử dụng database riêng trên
nền PostgreSQL để duy trì ranh giới sở hữu dữ liệu.

Flyway được dùng để quản lý thay đổi lược đồ cơ sở dữ liệu theo phiên bản. Các
tập lệnh migration được lưu cùng mã nguồn và thực thi theo thứ tự, giúp môi
trường phát triển và triển khai có cấu trúc database nhất quán. Cách làm này
an toàn và dễ kiểm tra hơn việc sửa bảng thủ công.

RabbitMQ hiện thực hóa giao tiếp bất đồng bộ. Các service có thể phát sự kiện
nghiệp vụ mà không cần biết trực tiếp tất cả consumer. Broker tiếp nhận, định
tuyến và lưu tạm message, giúp giảm phụ thuộc theo thời điểm giữa các thành
phần. WebSocket được sử dụng ở lớp giao tiếp với ứng dụng người dùng để chuyển
các cập nhật cần phản hồi nhanh, chẳng hạn thay đổi trạng thái lượt khám.

### 2.5.3. Next.js, React, TypeScript, Flutter và Dart

React là thư viện xây dựng giao diện theo mô hình component. Giao diện được chia
thành các thành phần có thể tái sử dụng và cập nhật theo trạng thái. Next.js là
framework dựa trên React, cung cấp cơ chế định tuyến, tổ chức ứng dụng và quy
trình build thống nhất. TypeScript mở rộng JavaScript bằng hệ thống kiểu tĩnh,
giúp phát hiện sớm lỗi về cấu trúc dữ liệu và cải thiện khả năng bảo trì. Doctor
Web của CareFlow được xây dựng bằng Next.js, React và TypeScript.

Flutter là bộ công cụ xây dựng giao diện đa nền tảng. Ứng dụng được viết bằng
Dart và biên dịch cho các nền tảng mục tiêu. Flutter sử dụng mô hình widget để
mô tả giao diện; khi trạng thái thay đổi, framework cập nhật phần hiển thị cần
thiết. CareFlow sử dụng Flutter để xây dựng Patient Mobile, nhờ đó có thể duy
trì một mã nguồn chính cho ứng dụng di động.

Việc tách Doctor Web và Patient Mobile phản ánh bối cảnh sử dụng khác nhau. Bác
sĩ cần giao diện nhiều thông tin trên màn hình lớn, trong khi bệnh nhân cần ứng
dụng thuận tiện trên điện thoại để đặt lịch, hiển thị QR và theo dõi hành trình.

### 2.5.4. Docker, Docker Compose và Maven

Container đóng gói ứng dụng cùng các thư viện và cấu hình cần thiết thành một
đơn vị có thể chạy nhất quán ở nhiều môi trường. Khác với máy ảo đầy đủ,
container chia sẻ kernel của hệ điều hành nhưng cách ly tiến trình và hệ thống
tệp. Docker cung cấp công cụ để xây dựng image, chạy container, quản lý network
và volume.

Docker Compose mô tả một hệ thống nhiều container trong các tệp cấu hình. Mỗi
service có thể khai báo image hoặc Dockerfile, cổng, biến môi trường, network,
health check và quan hệ phụ thuộc. Với một hệ thống Microservices, Compose giúp
khởi động các thành phần theo một cấu hình có thể tái sử dụng thay vì chạy thủ
công từng service.

Maven là công cụ quản lý vòng đời build và dependency cho Java. Dự án CareFlow
được tổ chức theo mô hình Maven nhiều module với một parent POM quản lý phiên
bản chung. Maven hỗ trợ biên dịch, chạy kiểm thử và đóng gói từng module hoặc
toàn bộ hệ thống. Cách tổ chức này giúp các service dùng chung quy ước build
nhưng vẫn có artifact độc lập.

Nhìn chung, nhóm công nghệ trên đáp ứng các nhu cầu chính của CareFlow: Spring
Boot và Spring Cloud xây dựng backend phân tán; PostgreSQL lưu trữ dữ liệu;
RabbitMQ và WebSocket hỗ trợ tích hợp và cập nhật trạng thái; Next.js và Flutter
xây dựng hai loại ứng dụng người dùng; Docker hỗ trợ đóng gói và Maven quản lý
quá trình build. Cách các công nghệ này được áp dụng cụ thể sẽ được trình bày
trong chương xây dựng và phát triển hệ thống.

## Tài liệu tham khảo chính của chương

1. Martin Fowler, James Lewis, [“Microservices”](https://martinfowler.com/articles/microservices.html), 2014.
2. IETF, [“RFC 7519: JSON Web Token (JWT)”](https://www.rfc-editor.org/rfc/rfc7519), 2015.
3. IETF, [“RFC 6455: The WebSocket Protocol”](https://www.rfc-editor.org/rfc/rfc6455), 2011.
4. Spring, [“Spring Cloud Gateway Reference Documentation”](https://docs.spring.io/spring-cloud-gateway/reference/).
5. Spring, [“Spring Cloud Netflix Reference Documentation”](https://docs.spring.io/spring-cloud-netflix/reference/).
6. RabbitMQ, [“RabbitMQ Tutorials”](https://www.rabbitmq.com/tutorials).
7. PostgreSQL Global Development Group, [“PostgreSQL Documentation”](https://www.postgresql.org/docs/).
8. Docker, [“Docker Documentation”](https://docs.docker.com/).
9. Flutter, [“Flutter Architectural Overview”](https://docs.flutter.dev/resources/architectural-overview).
