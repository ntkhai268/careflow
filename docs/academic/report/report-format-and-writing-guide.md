# KHUNG VÀ QUY ƯỚC VIẾT BÁO CÁO CAREFLOW

> **Nguồn chuẩn bắt buộc:** tài liệu này ghi lại cả đề mục, nhận thức và cách
> trình bày đã được chốt từ hướng dẫn của giảng viên. Agent phải đọc file này
> trước khi tạo hoặc sửa nội dung trong `docs/report`. Khi tài liệu cũ có cấu
> trúc khác, ưu tiên file này và các quyết định nghiệp vụ mới nhất.

## 1. Mạch nhận thức chung

```text
Nhu cầu thực tế và mục tiêu
  → cơ sở khoa học đã nghiên cứu
  → hiện trạng khi chưa có CareFlow
  → mô hình vận hành mới có CareFlow
  → tình huống sử dụng, tương tác và yêu cầu
  → thiết kế kiến trúc và thiết kế theo từng Use Case
  → phiên bản phần mềm đã hiện thực
  → kiểm thử, truy vết và đánh giá phiên bản
```

Các mức mô tả không được trộn lẫn:

- **Phân tích:** nhìn từ nghiệp vụ và bên ngoài hệ thống; xác định actor, tình
  huống, dữ liệu trao đổi, trạng thái và yêu cầu.
- **Thiết kế:** mô tả cấu trúc bên trong; Form/Boundary, Process/Control,
  Entity/Data, API, service, repository, database và event.
- **Hiện thực:** mô tả phiên bản đã xây dựng, màn hình thực tế, API, dữ liệu,
  cấu hình và cách vận hành.
- **Kiểm thử:** dùng Use Case, yêu cầu, Sequence, State và ERD làm nguồn sinh
  Test Case; phải có kết quả và truy vết.

## 2. Khung nội dung chính thức

## CHƯƠNG 1. GIỚI THIỆU ĐỀ TÀI

### 1.1. Mục đích

**Nhận thức:** trình bày nhu cầu thực tế và giá trị ứng dụng mang lại, chưa đi
sâu vào chức năng kỹ thuật. Phần “Lý do chọn đề tài” được gộp vào đây.

**Cách trình bày:** trả lời ba câu hỏi:

- CareFlow được sử dụng trong ngữ cảnh nào?
- Những đối tượng nào được hưởng lợi?
- Ứng dụng giúp bệnh viện và bệnh nhân cải thiện điều gì?

### 1.2. Mục tiêu

**Nhận thức:** mô tả các kết quả phần mềm phải đạt được để giải quyết những vấn
đề chính trong mục đích.

**Cách trình bày:** mục tiêu cần bao phủ quản lý hành trình khám ngoại trú,
lịch và phiếu khám, điều phối hàng đợi, kết nối khám–cận lâm sàng–đọc kết
quả–phát thuốc, Patient Mobile, Hospital Web, bảo mật và truy vết.

### 1.3. Phương pháp tiến hành

#### 1.3.1. Khảo sát hiện trạng

#### 1.3.2. Tìm hiểu nghiệp vụ và quy định

#### 1.3.3. Nghiên cứu mô hình, phương pháp, giải thuật và công nghệ

#### 1.3.4. Phân tích, thiết kế, hiện thực, kiểm thử và đánh giá

### 1.4. Phạm vi đề tài

Mục này không được tài liệu của giảng viên quy định trực tiếp nhưng được giữ để
phân biệt rõ:

- phạm vi thực hiện;
- phần mô phỏng;
- phần ngoài phạm vi.

## CHƯƠNG 2. CƠ SỞ KHOA HỌC CỦA ĐỀ TÀI

**Nhận thức:** đây không phải chương giới thiệu công nghệ theo kiểu từ điển.
Chương này trình bày kiến thức đã tìm hiểu để xây dựng giải pháp CareFlow.

### 2.1. Cơ sở nghiệp vụ khám ngoại trú

Trình bày quy trình đặt khám, tiếp nhận và xác nhận có mặt, khám ban đầu, thực
hiện cận lâm sàng, quay lại đọc kết quả, kê toa và phát thuốc.

### 2.2. Các quy tắc nghiệp vụ liên quan

Trình bày đối tượng ưu tiên, FIFO trong từng hàng, điều phối nhiều làn, xử lý
lỡ lượt và quy tắc bảo mật dữ liệu y tế.

### 2.3. Phương pháp phân tích và thiết kế

Trình bày phân tích–thiết kế hướng đối tượng, Use Case, Sequence Diagram,
Activity Diagram, Class Diagram và State Diagram; phân biệt rõ biểu đồ phân
tích hộp đen với biểu đồ thiết kế hộp trắng.

### 2.4. Cơ sở về hàng đợi

Trình bày FIFO, Round Robin, Round Robin 1:1:1 tại phòng khám, queue theo điểm
phục vụ và xử lý `MISSED`/requeue.

### 2.5. Kiến trúc và công nghệ được lựa chọn

Với mỗi kiến trúc hoặc công nghệ phải trả lời đủ:

1. CareFlow sử dụng nó để làm gì?
2. CareFlow sử dụng nó như thế nào?
3. Có những lựa chọn tương tự nào?
4. Vì sao chọn giải pháp này?

#### 2.5.1. Kiến trúc Microservices

#### 2.5.2. REST API và RabbitMQ

#### 2.5.3. Database per Service và PostgreSQL

#### 2.5.4. Spring Boot và Spring Cloud

#### 2.5.5. Next.js, React và Flutter

#### 2.5.6. Docker và Docker Compose

## CHƯƠNG 3. PHÂN TÍCH HỆ THỐNG

### 3.1. Hiện trạng phát sinh nhu cầu sử dụng phần mềm

**Nhận thức:** khảo sát tổ chức khi chưa có CareFlow, xác định các tình huống
và khó khăn thực tế. Đây chưa phải phần định nghĩa yêu cầu phần mềm.

#### 3.1.1. Các đối tượng trong quy trình hiện tại

Bệnh nhân, bác sĩ, nhân viên tiếp nhận, thu ngân/bộ phận thanh toán, kỹ thuật
viên cận lâm sàng, nhân viên cấp phát thuốc và bộ phận quản lý bệnh viện.

#### 3.1.2. Quy trình khám ngoại trú hiện tại

Phải thể hiện mô hình thanh toán tự chi trả đã chốt: trả trước phí khám khi đặt
lịch và quyết toán toàn bộ lượt khám ở cuối, trước khi nhận thuốc. Chi phí cận
lâm sàng được cộng vào quyết toán, không tạo bước thanh toán riêng. Phiên bản
báo cáo không xét BHYT.

#### 3.1.3. Các tình huống nghiệp vụ phát sinh

#### 3.1.4. Những khó khăn và hạn chế

**Cách trình bày:** dùng mô tả bằng lời, bảng tình huống–đối tượng–khó khăn và
Workflow/Activity/Sequence/Collaboration Diagram của hiện trạng. Không đưa
Microservice, API Gateway, RabbitMQ hoặc database vào sơ đồ hiện trạng.

### 3.2. Đề xuất giải pháp của đề tài

**Nhận thức:** đề xuất mô hình vận hành mới, trong đó CareFlow là công cụ tham
gia cùng con người và các hệ thống hiện có.

#### 3.2.1. Mô hình vận hành mới

#### 3.2.2. Vai trò của CareFlow

#### 3.2.3. Những thay đổi so với hiện trạng

#### 3.2.4. Lợi ích của giải pháp

**Cách trình bày:** có Workflow hoặc Sequence Diagram của quy trình mới;
CareFlow xuất hiện như một thành phần trong tổ chức; có bảng “Vấn đề hiện tại –
Sự hỗ trợ của CareFlow – Lợi ích”. Mô hình mới phải giữ hai giai đoạn thanh toán,
công thức `amountDue/refundDue` và các trạng thái `PAYMENT_DUE`, `SETTLED`,
`REFUND_PENDING`, `REFUNDED`. Cổng thanh toán và hoàn tiền production nằm ngoài
phạm vi; trạng thái trong báo cáo là adapter/demo.

### 3.3. Định nghĩa các tình huống CareFlow tham gia giải quyết

**Nhận thức:** mỗi Use Case là một tình huống thực tế mà actor cần CareFlow trợ
giúp, không phải API, service hoặc một màn hình.

#### 3.3.1. Xác định các actor nghiệp vụ

Actor phải mang vai trò thực tế như Bệnh nhân, Bác sĩ, Nhân viên tiếp nhận.
Trong Use Case phân tích không dùng actor chung chung `User`, actor kỹ thuật
`Admin`, Use Case Đăng nhập/Phân quyền hoặc các thành phần RabbitMQ, API Gateway,
database. Các nội dung này thuộc yêu cầu vận hành và thiết kế bảo mật.

Thu ngân/hệ thống thanh toán ngoài là actor hỗ trợ. Dùng `UC-PAY-01` làm Use Case
hỗ trợ và mô hình hóa nó như luồng `extend` tại `UC-APT-02` và `UC-PHA-01`;
không liên kết payment với `UC-LAB-01` và không tính nó là Use Case cốt lõi thứ
chín. Ranh giới
demo/production phải được ghi rõ và tám Use Case cốt lõi vẫn được trình bày nhất
quán.

#### 3.3.2. Biểu đồ Use Case tổng quát

#### 3.3.3. Quan hệ giữa các Use Case

Giải thích đúng mục đích của generalization, `include` và `extend`; không thêm
quan hệ chỉ để làm biểu đồ phức tạp.

#### 3.3.4. Danh mục Use Case

#### 3.3.5. Đặc tả sơ bộ từng Use Case

Mỗi đặc tả có: mục đích, actor chính, actor liên quan, tiền điều kiện, hậu điều
kiện thành công, hậu điều kiện thất bại, luồng chính và luồng ngoại lệ.

### 3.4. Định nghĩa các tương tác của CareFlow

**Nhận thức:** tên Use Case chỉ là ý niệm khái quát; một Use Case có thể gồm
nhiều request–response. Mọi thông điệp gửi đến hệ thống phải mang dữ liệu cụ
thể và các sơ đồ phải nhất quán tên actor, đối tượng, trạng thái, thông điệp.

#### 3.4.1. Sequence Diagram mức phân tích

Xem CareFlow như hộp đen; thể hiện actor gửi yêu cầu và dữ liệu gì, hệ thống trả
kết quả gì. Không đưa Controller, Repository hoặc database vào sơ đồ này.

Mỗi Use Case cốt lõi phải có một Sequence Diagram mức phân tích riêng. Với bộ
Use Case hiện tại, mục này phải có đủ tám sơ đồ cho `UC-APT-02`, `UC-QUE-02`,
`UC-QUE-04`, `UC-CON-01`, `UC-LAB-01`, `UC-LAB-03`, `UC-PRE-01` và
`UC-PHA-01`.

#### 3.4.2. Activity Diagram

Mô tả luồng chính, nhánh điều kiện và ngoại lệ.

Mỗi Use Case cốt lõi phải có Activity Diagram riêng được suy ra từ đặc tả của
Use Case đó; không dùng một Activity Diagram toàn hành trình để thay thế tám
sơ đồ chi tiết.

#### 3.4.3. Class Diagram phân tích

Mô tả các đối tượng nghiệp vụ xuất hiện trong Use Case.

#### 3.4.4. State Diagram

Mô tả vòng đời của Appointment, Queue Entry, Consultation, Laboratory Order,
Prescription và `VisitSettlement` (`DEMO_MOCK`).

### 3.5. Định nghĩa yêu cầu cho các thành phần CareFlow

**Nhận thức:** lúc này xem CareFlow là hệ thống gồm nhiều thành phần phối hợp
xử lý Use Case.

#### 3.5.1. Xác định thành phần tham gia từng Use Case

Ví dụ: Patient Mobile, Hospital Web, Appointment Service, Queue Service,
Consultation Service, Laboratory Service, Prescription Service và Notification
Service.

#### 3.5.2. Xác định các đối tượng hỗ trợ bên ngoài

Ví dụ hệ thống thanh toán/hoàn tiền hoặc dịch vụ gửi thông báo.

#### 3.5.3. Xác định cách phối hợp giữa các thành phần

Dùng System Sequence Diagram để phân rã thông điệp từ actor xuống các thành
phần.

#### 3.5.4. Yêu cầu thuộc tính và hành vi của từng thành phần

Mô tả tên lớp/thành phần, trách nhiệm, thuộc tính, phương thức, dữ liệu vào và
dữ liệu ra ở mức phân tích.

### 3.6. Yêu cầu chất lượng

#### 3.6.1. Yêu cầu từ môi trường nghiệp vụ

Ví dụ thời gian gọi lượt, giao diện tiếng Việt và khả năng sử dụng.

#### 3.6.2. Yêu cầu từ môi trường vận hành

Xác thực, phân quyền, bảo mật, tính sẵn sàng, sao lưu và truy vết.

#### 3.6.3. Yêu cầu từ môi trường phát triển

Microservices, coding convention, khả năng mở rộng, triển khai độc lập và thư
viện dùng chung. Mỗi yêu cầu phải có nguồn gốc và tiêu chí đo được.

### 3.7. Ma trận truy vết yêu cầu

Truy vết theo chuỗi: Vấn đề → Use Case → Yêu cầu → Thành phần → Test Case.

## CHƯƠNG 4. THIẾT KẾ PHẦN MỀM

### 4.1. Thiết kế kiến trúc

#### 4.1.1. Layering – Phân tầng

Boundary/Interface, Process/Control và Entity/Data.

#### 4.1.2. Segmentation – Phân vùng

Phân chia theo miền Appointment, Queue, Consultation, Laboratory,
Prescription, Notification và các miền liên quan.

#### 4.1.3. Factoring – Thành phần dùng chung

Response envelope, event envelope, security context, audit information và các
thành phần dùng chung khác.

#### 4.1.4. Kiến trúc Microservices tổng thể

### 4.2. Thiết kế chi tiết theo từng Use Case

**Bắt buộc thiết kế theo Use Case, không chỉ mô tả từng Microservice độc lập.**
Với mỗi Use Case quan trọng phải trình bày đủ ba lớp:

1. **Lớp biên – Form:** tên form, hình thiết kế, actor, control, input, output,
   API được gọi và mục đích của từng control.
2. **Lớp xử lý – API/Service:** tên API, nhiệm vụ, input, output, luồng xử lý,
   repository/service, event và API bên ngoài.
3. **Lớp thực thể – Entity/Data:** entity, thuộc tính, quan hệ, trạng thái và
   ràng buộc.

Sequence Diagram ở chương này là sơ đồ hộp trắng, thể hiện Form, API, Service,
Repository và database; khác Sequence Diagram hộp đen ở Chương 3.

### 4.3. Thiết kế cơ sở dữ liệu

#### 4.3.1. Database per Service

#### 4.3.2. ERD hoặc Class-to-Table

#### 4.3.3. Từ điển dữ liệu

Trình bày từng bảng theo mẫu `ID | Attribute | Type | Constraint | Note`; phân
biệt rõ khóa ngoại cùng database với tham chiếu logic xuyên service và ghi trạng
thái đã có migration hay mới là mô hình logic đích.

#### 4.3.4. Chuẩn hóa và mô tả bảng

#### 4.3.5. Repository và truy vấn dữ liệu

#### 4.3.6. Stored Procedure nếu thực tế có sử dụng

#### 4.3.7. Trigger, constraint và migration

Không tự tạo Stored Procedure hoặc Trigger chỉ để đủ đề mục. Nếu CareFlow dùng
JPA Repository và Flyway thì trình bày đúng giải pháp đó.

### 4.4. Thiết kế đáp ứng yêu cầu chất lượng

JWT và phân quyền, ownership và assignment, idempotency, outbox, concurrency
control, audit log, retry/xử lý lỗi và bảo vệ dữ liệu y tế.

## CHƯƠNG 5. HIỆN THỰC PHẦN MỀM

### 5.1. Phiên bản phần mềm đã xây dựng

Nêu version, phạm vi chạy thật, phần mock và phần chưa thực hiện. Theo quyết
định của báo cáo CareFlow, các chức năng trong phạm vi MVP được trình bày như
phiên bản mục tiêu đã hoàn thành; những capability cố ý dùng adapter demo vẫn
phải ghi đúng là `DEMO_MODE`.

### 5.2. Môi trường và công cụ phát triển

### 5.3. Cấu trúc mã nguồn

### 5.4. Hiện thực theo từng Use Case

Với mỗi Use Case trình bày quy trình sử dụng form, ảnh giao diện thực tế, API,
service, dữ liệu/event và ngoại lệ đã xử lý. Đây là phần hướng dẫn phiên bản từ
góc nhìn người sử dụng, khác thiết kế Form/API ở Chương 4.

### 5.5. Triển khai và vận hành hệ thống

Docker Compose, cấu hình, migration, cách khởi chạy và dữ liệu demo.

## CHƯƠNG 6. KIỂM THỬ VÀ ĐÁNH GIÁ

Chương này được bổ sung để hoàn chỉnh chu trình. Mọi Test Case phải được sinh
ra từ sản phẩm phân tích và thiết kế trước đó.

### 6.1. Mục tiêu và phạm vi kiểm thử

### 6.2. Môi trường và dữ liệu kiểm thử

### 6.3. Kiểm thử đơn vị

Nguồn: phương thức lớp, business rule, thuật toán Queue và state transition.

### 6.4. Kiểm thử chức năng theo Use Case

Mỗi luồng chính và luồng ngoại lệ trở thành Test Case. Bảng kiểm thử cần có:
mã, nguồn yêu cầu/Use Case, tiền điều kiện, dữ liệu vào, kết quả mong đợi, kết
quả thực tế và trạng thái.

### 6.5. Kiểm thử API và tích hợp

Nguồn: thông điệp trong Sequence Diagram, REST API, RabbitMQ event, consumer
idempotency, contract giữa service, retry và lỗi tích hợp.

### 6.6. Kiểm thử trạng thái và dữ liệu

Nguồn: State Diagram, ERD, constraint, transaction và concurrent request.

### 6.7. Kiểm thử yêu cầu chất lượng

Đánh giá ba nhóm NFR: nghiệp vụ, vận hành và phát triển.

### 6.8. Ma trận truy vết kiểm thử

Use Case → Yêu cầu → Thiết kế → Test Case → Kết quả.

### 6.9. Tổng hợp kết quả

Nêu số Test Case đạt/không đạt, lỗi phát hiện, lỗi đã khắc phục và phần chưa
kiểm thử.

### 6.10. Đánh giá phiên bản

Đây là phần ưu/khuyết điểm của phiên bản so với yêu cầu: yêu cầu đã đáp ứng,
đáp ứng một phần, ưu điểm, hạn chế và hướng phát triển.

## TÀI LIỆU THAM KHẢO

Tài liệu phải là dẫn chứng thực sự cho quy trình nghiệp vụ, quy định, phương
pháp, giải thuật, kiến trúc và công nghệ được lựa chọn. Không liệt kê nguồn
không được dùng trong nội dung.

## 3. Quy tắc bắt buộc cho agent

1. Đọc file này, nguồn sự thật Chương 3, hành trình nghiệp vụ và service
   contract trước khi viết.
2. Không đưa API, service, database hoặc broker vào hiện trạng.
3. Không dùng Đăng nhập, Phân quyền, `User`, actor kỹ thuật `Admin`, Gateway,
   RabbitMQ hoặc database làm Use Case/actor nghiệp vụ phân tích.
4. Chương 2 phải giải thích CareFlow dùng kiến thức/công nghệ như thế nào và vì
   sao chọn, không viết kiểu định nghĩa chung.
5. Chương 4 phải thiết kế từng Use Case theo Form–Process–Entity và dùng
   sequence hộp trắng.
6. Mục 3.4 phải có Sequence hộp đen và Activity riêng cho đủ tám Use Case cốt
   lõi; Class/State có thể dùng chung nhưng phải có bảng truy vết về Use Case.
7. Chương 5 chỉ mô tả phiên bản được chốt; phần adapter demo phải được ghi đúng
   biên, không gọi là backend production.
8. Chương 6 phải truy vết Test Case về yêu cầu, Use Case và thiết kế.
9. Tên actor, trạng thái, event, entity và mã Use Case phải nhất quán giữa các
   chương và biểu đồ.
10. Không tự thêm Stored Procedure, Trigger, AI, Payment microservice hoặc hệ
   thống ngoài phạm vi chỉ để đủ đề mục.
