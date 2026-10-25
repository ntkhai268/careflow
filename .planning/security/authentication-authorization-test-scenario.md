# Kịch bản kiểm tra xác thực và phân quyền CareFlow

Ngày kiểm tra: 2026-10-14. Baseline: `develop`, commit `8bf77e2`.
Phạm vi: kiểm tra code, unit test hiện có và kế hoạch kiểm tra HTTP/network. Chưa sửa cơ chế bảo mật.

## 1. Câu trả lời theo kiến trúc hiện tại

### Đăng nhập và gọi service

```mermaid
sequenceDiagram
    participant U as Web/Mobile
    participant G as Gateway :8080
    participant I as Identity :8081
    participant E as Eureka :8761
    participant S as Service nghiệp vụ
    U->>G: POST /api/auth/login (username/password)
    G->>I: Chuyển request public theo route
    I->>I: Kiểm tra mật khẩu và trạng thái tài khoản
    I-->>U: Access JWT + refresh token (qua Gateway)
    U->>G: Request nghiệp vụ + Authorization: Bearer JWT
    G->>G: Xóa X-User-* do client gửi; xác minh JWT
    alt JWT không hợp lệ / hết hạn / thiếu
        G-->>U: 401; không chuyển tới service
    else JWT hợp lệ
        G->>E: Resolve instance theo tên service (discovery/cache)
        G->>S: Request + X-User-Id + X-User-Role từ JWT
        S->>S: Kiểm tra vai trò, tài nguyên, phân công, trạng thái
        S-->>U: 2xx / 403 / 409 (qua Gateway)
    end
```

- Identity mới là nơi xác thực mật khẩu và cấp token. Login thông thường cũng đi qua Gateway; không phải login xong mới bắt đầu đi qua Gateway.
- Gateway hiện không lưu một session `ACTIVE` cho từng user. Mỗi request nghiệp vụ phải mang access token để Gateway kiểm tra lại.
- `lb://patient-service` và các route tương tự tìm instance đang chạy qua Eureka. Request không tự khởi động một service đã tắt. Không có instance thường dẫn tới 503; lỗi kết nối hoặc timeout phụ thuộc cấu hình và có thể là 5xx khác.
- Phần lớn service nghiệp vụ tin header `X-User-Id`/`X-User-Role`; chưa có cơ chế chung để xác thực bên gọi là Gateway. Identity tự kiểm tra JWT; Notification kiểm tra JWT trong STOMP CONNECT.
- Service không tự biết user vừa login ở đâu. Nó chỉ biết bằng chứng đi kèm request mà chính nó đã xác minh, hoặc context từ một bên gọi được xác thực và tin cậy.
- Một microservice là ứng dụng/process/container có địa chỉ mạng. Nó có public trên Internet hay chỉ trong mạng nội bộ là quyết định triển khai. Docker Compose hiện publish nhiều port service ra host; firewall/public ingress thực tế chưa kiểm chứng.
- Chạy trên nhiều máy không thay đổi quy tắc phân quyền. Cần bổ sung ranh giới mạng, xác thực service-to-service, discovery, đồng bộ khóa/chính sách, thời gian và cơ chế thu hồi token giữa các instance.

## 2. Quy tắc chấp nhận hoặc từ chối

Đề xuất quy tắc chung cho mọi API được bảo vệ:

1. Xác thực danh tính bên gọi (Gateway/service khác) khi API được thiết kế chỉ dùng nội bộ.
2. Xác thực danh tính user: chữ ký hoặc introspection, thuật toán được cho phép, issuer, audience theo loại token, thời hạn và trạng thái thu hồi.
3. Cho phép đúng vai trò/action; mặc định từ chối nếu chưa có chính sách.
4. Kiểm tra quyền trên đúng tài nguyên: chủ hồ sơ, bác sĩ phụ trách, phòng/quầy được phân công.
5. Kiểm tra trạng thái nghiệp vụ: có quyền nhưng ca đã hoàn tất vẫn không được sửa.
6. Nếu không xác minh được quyền, không thực hiện thao tác và không phát event. Lỗi hạ tầng có thể trả 503; không cần giả thành 403.

| Trường hợp | Kết quả mong đợi |
| --- | --- |
| Thiếu/sai/hết hạn token ở API cần đăng nhập | 401; không thực thi |
| Token hợp lệ nhưng sai vai trò, tài nguyên hoặc phân công | 403 hoặc 404 nếu chính sách che giấu tài nguyên; không thực thi |
| User có quyền nhưng trạng thái nghiệp vụ không cho phép | 409 theo contract; không thực thi |
| Tài khoản bị khóa, quyền bị thu hồi | Từ chối trong thời hạn thu hồi đã quy định |
| Không xác minh được danh tính/quyền do dependency lỗi | Từ chối an toàn; có thể trả 503 |
| Đúng danh tính, quyền, tài nguyên và trạng thái | 2xx; chỉ trả dữ liệu được phép |
| Service chưa chạy/không có instance | Lỗi availability; không phải chứng cứ lỗi phân quyền |

`200` không tự chứng minh an toàn: cần kiểm tra body có dữ liệu của người khác không. `403` cũng chưa đủ: DB, file và outbox phải không thay đổi.

## 3. Các phương án bảo vệ ranh giới service

| Phương án | Cách service tin request | Điều kiện và đánh đổi |
| --- | --- | --- |
| Gateway xác thực + context nội bộ | Service xác thực Gateway bằng mTLS hoặc token nội bộ ký số | Chặn truy cập ngoài bypass Gateway; header thuần không đủ. Service vẫn kiểm tra tài nguyên/action |
| JWT tại Gateway và tại service | Mỗi service xác minh token bằng khóa public của Identity | Tách quyền ký khỏi quyền kiểm tra; kiểm tra audience theo token profile, expiry, issuer và quy tắc tài nguyên |
| Opaque token + introspection | Service hỏi Identity xem token còn active và quyền hiện tại | Thu hồi tập trung; thêm độ trễ và phụ thuộc mạng. Cache phải phù hợp SLA thu hồi |

Định hướng phù hợp với lỗ hổng hiện tại: service phải kiểm tra quyền riêng; đóng port nghiệp vụ ngoài ingress; xác thực service-to-service; truyền danh tính user có thể kiểm chứng. Không chỉ thêm một lần kiểm tra ở Gateway.

Code hiện dùng HMAC/shared secret. Bên có secret xác minh cũng có khả năng ký token. Không copy secret ký user token vào mọi service chỉ để thuận tiện. Cấu hình JWT có fallback secret trong repository; triển khai cần secret riêng và từ chối khởi động nếu thiếu cấu hình bắt buộc. Chưa kiểm tra giá trị secret thực tế của deployment.

TLS/mTLS xác minh đường truyền/bên gọi; JWT xác minh thông tin user; phân quyền tài nguyên xác minh thao tác. Ba kiểm tra phục vụ mục đích khác nhau. Không phương án nào là cam kết an toàn tuyệt đối.

## 4. Dữ liệu và cách ghi nhận kết quả

Tạo dữ liệu riêng cho audit trong DB test:

- `PATIENT_A` sở hữu `PATIENT_A1` và `PATIENT_A2`; `PATIENT_B` sở hữu `PATIENT_B1`.
- `DOCTOR_A` phụ trách `ROOM_A`, appointment và consultation của `PATIENT_A1`; `DOCTOR_B` ở `ROOM_B`.
- `STAFF_A` được phân công phòng/quầy A; `STAFF_B` ở B; `LAB_A` và `LAB_B` có service point khác nhau; một `ADMIN_TEST`.
- Một consultation đang IN_PROGRESS, một đã COMPLETED, health record/file của mỗi bệnh nhân, prescription CONFIRMED và lab order IN_PROGRESS đã được claim bởi LAB_A.
- Tài khoản thử khóa/đổi role là tài khoản audit riêng, không dùng tài khoản seed mà người khác đang dùng.

Mỗi ca ghi: ID, endpoint/method, vai trò, đường vào (Gateway/direct), trạng thái fixture, expected/actual status, body đã loại bỏ thông tin nhạy cảm, DB/event trước-sau và correlation ID. Không ghi password, JWT/refresh token hay toàn bộ hồ sơ y tế vào báo cáo.

Nhãn: `UNIT` = đã có unit test liên quan, không chứng minh end-to-end; `CODE-GAP` = thấy thiếu kiểm tra trong code, chưa tái hiện HTTP; `LIVE` = cần bật hệ thống; `FUTURE` = tính năng chưa triển khai.

## 5. Ma trận kiểm tra

| ID | Thao tác | Mong đợi | Hiện trạng / cách kiểm tra |
| --- | --- | --- | --- |
| AUTH-01 | Login đúng mật khẩu; gọi GET /api/auth/me qua Gateway | 200, đúng user; token có expiry | UNIT login/JWT; LIVE chuỗi HTTP |
| AUTH-02 | Sai password 5 lần với tài khoản audit | Login thất bại; tài khoản bị khóa; password đúng trong thời gian khóa không mở lại | UNIT một phần; LIVE |
| AUTH-03 | Register gửi thêm role ADMIN | Tài khoản tạo ra vẫn PATIENT | UNIT |
| AUTH-04 | Refresh lần 1; dùng lại refresh token cũ | Rotation; token cũ bị từ chối; phiên refresh liên quan bị thu hồi theo policy | UNIT; LIVE |
| AUTH-05 | Logout rồi dùng lại refresh token và access token đã lưu | Refresh từ chối; access token bị từ chối theo SLA thu hồi đã thống nhất | UNIT revoke refresh; CODE-GAP access JWT hiện còn dùng tới expiry |
| AUTH-06 | ADMIN khóa/đổi role tài khoản audit; dùng access token cũ | Quyền cũ hết hiệu lực theo SLA trên mọi instance | CODE-GAP; LIVE |
| JWT-01 | Gọi API được bảo vệ không có token; chỉ gửi X-User-Role: ADMIN | 401; không forward | UNIT Gateway |
| JWT-02 | Sửa payload role/sub nhưng giữ chữ ký cũ; token hết hạn; ký bằng test key sai | 401; không forward | LIVE hoặc bổ sung regression test với khóa test |
| JWT-03 | Token sai issuer, sai audience, thiếu exp; token malformed | Từ chối theo token profile; không 500 | UNIT wrong issuer ở Notification; CODE-GAP chưa có audience/required-exp policy chung |
| JWT-04 | Token PATIENT + header X-User-Role: ADMIN/X-User-Id của người khác qua Gateway | Header downstream lấy từ JWT; không nâng quyền | UNIT Gateway |
| JWT-05 | Mock mobile token gửi tới backend thật | 401; không coi mock session là xác thực thật | LIVE |
| EDGE-01 | CLIENT bên ngoài gọi thẳng port Patient/Directory với header giả mạo | Không tới được API nội bộ hoặc bị từ chối trước nghiệp vụ | CODE-GAP service tin header; LIVE network |
| EDGE-02 | Gọi thẳng Identity /api/users với token PATIENT | 403; thay X-User-* không đổi quyền | Code có SecurityFilterChain; LIVE |
| EDGE-03 | Gọi thẳng EMR không token | Không tới được hoặc 401, không đọc/ghi dữ liệu | CODE-GAP EMR không có guard chung |
| EDGE-04 | Gọi API qua route discovery tự động /{service-id}/... nếu được expose | Chính sách tương đương route /api/...; không tạo đường bypass | LIVE; discovery locator đang enabled |
| ROLE-01 | PATIENT gọi GET /api/users; DOCTOR sửa directory | 403; DB không đổi | Code có guard; UNIT directory; LIVE Gateway |
| ROLE-02 | ADMIN GET /api/directory/staff/assignments qua Gateway | 200 | CODE-GAP Gateway đánh dấu public, xóa role => controller từ chối 403 |
| DATA-01 | PATIENT_A đọc/sửa Patient profile của B | Từ chối; dữ liệu B không đổi | UNIT PatientServiceSecurityTest |
| DATA-02 | PATIENT_A đọc/sửa/xóa health record hoặc tải file của B bằng ID B | Từ chối; file/DB không đổi | CODE-GAP HealthRecordController/Service chỉ đối chiếu ID trong URL |
| DATA-03 | PATIENT_A đọc/sửa EMR của B; GET summary với patientId khác | Từ chối; GET bị từ chối không tự tạo master record | CODE-GAP MedicalRecordController/Service |
| DATA-04 | PATIENT_A xem prescription/lab của hồ sơ A2 | Cho phép dữ liệu hợp lệ vì A2 cùng chủ tài khoản | CODE-GAP các client nhận diện patient lấy hồ sơ đầu tiên |
| DATA-05 | DOCTOR_B gọi PUT /api/consultations/{id}/clinical-data của A | 403 | Code có requireAssignedDoctor; LIVE |
| DATA-06 | PATIENT_B/DOCTOR_B gọi PUT /api/consultations/{id}, /status, /complete của A | Từ chối; không ghi DB/phát event | CODE-GAP các endpoint legacy thiếu guard |
| DATA-07 | DOCTOR_B gọi queue ROOM_A qua route /rooms/... | 403 | UNIT QueueRoomAssignmentTest; LIVE |
| DATA-08 | DOCTOR_B gọi queue A qua /departments/{id}/dashboard và /next | 403; không claim/gọi bệnh nhân của A | CODE-GAP route theo khoa bỏ kiểm tra assignment |
| DATA-09 | LAB_B sửa item/result của order IN_PROGRESS do LAB_A claim | 403; kết quả/performedBy không đổi | CODE-GAP updateResult không kiểm tra claim/assignment |
| DATA-10 | Bác sĩ không phụ trách tạo/duyệt/hủy lab order của ca khác | Từ chối; không tạo event chuyển luồng | CODE-GAP kiểm tra role nhưng thiếu quan hệ với consultation |
| DATA-11 | STAFF khác quầy đọc/dispense prescription | Từ chối; không cập nhật thuốc/queue/event | Code kiểm tra queue service point; LIVE |
| DATA-12 | Không có quyền gọi AI clinical API hoặc lấy interaction của người khác | Từ chối theo policy được duyệt | CODE-GAP chưa có guard quyền/ownership trong AI controller |
| STATE-01 | Bác sĩ đúng phụ trách sửa ca COMPLETED | 409 theo contract hoặc lỗi trạng thái hiện có; không ghi | LIVE; phân biệt lỗi trạng thái với quyền |
| WS-01 | STOMP CONNECT thiếu/sai JWT; subscribe notification người khác | Từ chối; không nhận nội dung | UNIT JWT authenticator; code chỉ allow /user/queue/notifications; LIVE |
| S2S-01 | Service A gọi B không có danh tính service; chỉ gửi user header | Từ chối trước thao tác | CODE-GAP chưa có lớp xác thực caller chung |
| S2S-02 | Directory/ownership dependency bị tắt hoặc timeout | Không cho phép thao tác khi quyền chưa xác minh; có thể 503 | LIVE bằng stub/test môi trường riêng |
| S2S-03 | Request không có context user đi qua Feign Consultation | Không tự biến thành bác sĩ mẫu | CODE-GAP Feign interceptor fallback user/DOCTOR |
| MULTI-01 | Cùng request tới hai instance service trên hai máy | Quyết định giống nhau; không dùng quyền của request trước | LIVE với ít nhất hai instance |
| MULTI-02 | Rotate khóa hoặc thu hồi token; lần lượt gọi hai instance | Khóa cũ/token thu hồi xử lý thống nhất theo policy và SLA | LIVE; không suy ra từ unit test |
| MULTI-03 | Service ngừng chạy | Gateway trả lỗi availability; không coi user chưa đăng nhập; không tự bật service | LIVE bằng tắt instance audit |
| UI-01 | Sửa careflow_user.role thành ADMIN; token vẫn PATIENT | API vẫn từ chối; UI phải đồng bộ lại danh tính thật | CODE-GAP UI tin localStorage; LIVE UI |
| UI-02 | Token hết hạn, user cached vẫn còn; truy cập khu vực khác role | Login lại khi 401; 403 hiển thị AccessDenied; không tải dữ liệu trái quyền | CODE-GAP AuthContext chưa xác minh session lúc khởi tạo; LIVE UI |

## 6. Đăng nhập ngoài password và các ca kiểm tra

Hiện trạng: username/email + password; JWT + refresh token. `/api/auth/ekyc` chạy sau xác thực và trả `mock=true`; không phải đăng nhập khuôn mặt/vân tay. Chưa thấy triển khai TOTP, WebAuthn/passkey hoặc câu hỏi bí mật trong Identity.

Các lựa chọn cần thiết kế riêng:

- Password + TOTP từ ứng dụng authenticator: một yếu tố biết và một yếu tố sở hữu. Cần quy trình đăng ký, khóa thiết bị, recovery và step-up.
- Passkey/WebAuthn: thiết bị giữ private key, backend kiểm tra chữ ký challenge. Vân tay/Face ID có thể mở khóa credential trên thiết bị; không gửi ảnh sinh trắc lên backend để thay chữ ký.
- SSO/OIDC từ Identity Provider: backend kiểm chứng luồng đăng nhập và danh tính; không tin một email client tự gửi.
- Câu hỏi bí mật thuộc cùng yếu tố “biết” như password; không tạo MFA. Không chọn làm lớp bảo mật bổ sung hoặc đường reset thay thế có thể bypass MFA.

| ID | Kịch bản tương lai | Mong đợi |
| --- | --- | --- |
| MFA-01 | Tài khoản yêu cầu MFA, chỉ gửi đúng password | Chưa cấp access token có toàn bộ quyền |
| MFA-02 | TOTP sai/hết hạn/dùng lại; thử nhiều lần | Từ chối; có giới hạn thử; không log mã |
| MFA-03 | Passkey đúng/sai challenge, origin hoặc RP ID; replay response | Chỉ chấp nhận chữ ký đúng với challenge chưa dùng của phiên này |
| MFA-04 | Mất thiết bị, dùng recovery code, đổi phương thức MFA | Xác thực và audit; code một lần; thông báo user; không hạ xuống câu hỏi bí mật |
| MFA-05 | Ảnh CCCD bất kỳ trả VERIFIED từ mock eKYC | Ghi nhận mock; không dùng làm chứng cứ sinh trắc hay tự nâng quyền |
| MFA-06 | SSO token sai issuer/audience; callback sai state/nonce theo flow | Từ chối; không tự tạo phiên bằng thông tin client |

Tất cả MFA/passkey/SSO case là FUTURE; chưa thể báo pass bằng code hiện tại.

## 7. Thực thi kiểm tra code không bật backend

Unit test không cần PostgreSQL, RabbitMQ, Eureka hay frontend. Chỉ chạy test được chỉ định, không chạy DataInitializer.

```bash
mvn -o -pl careflow-api-gateway,careflow-patient-service,careflow-identity-service,careflow-hospital-directory-service,careflow-queue-service,careflow-prescription-service,careflow-lab-service -am -Dtest=JwtAuthenticationFilterTest,PatientServiceSecurityTest,AuthServiceTest,DepartmentControllerAuthTest,StaffAssignmentControllerAuthTest,QueueRoomAssignmentTest,PrescriptionServiceTest,LabOrderServiceTest -Dsurefire.failIfNoSpecifiedTests=false test

mvn -o -pl careflow-identity-service,careflow-api-gateway,careflow-notification-service -am -Dtest=JwtServiceTest,RefreshTokenServiceTest,EkycServiceTest,JwtAuthenticationFilterTest,JwtAuthenticatorTest -Dsurefire.failIfNoSpecifiedTests=false test
```

`-o` yêu cầu dependency đã có trong Maven cache. Nên dùng JDK 21 theo compiler target dự án. Máy kiểm tra hiện dùng JDK 26; Mockito cần chạy ngoài sandbox và các test dùng inline mock cần `-Dnet.bytebuddy.experimental=true`. Đây là tùy chọn kiểm tra môi trường, không thay đổi cấu hình bảo mật ứng dụng.

Kết quả đã có ở lượt audit trước: 46 test của nhóm Gateway/Identity/Patient/Queue/Prescription/Lab/Directory pass. Không suy ra các ca CODE-GAP hoặc network đã pass từ những test này.

Lượt này chạy thêm nhóm JWT/refresh/eKYC/Notification: 15 test pass, gồm 4 test Gateway đã chạy trước và 11 test bổ sung. Tổng cộng 57 test riêng biệt đã pass qua hai lượt audit. Chưa bổ sung regression test mới cho các lỗ hổng và chưa chạy HTTP end-to-end.

## 8. Cần bật gì để kiểm tra trực tiếp

Ngày kiểm tra, `http://127.0.0.1:8080/actuator/health` không kết nối được; không thấy listener CareFlow trong kiểm tra local.

Chạy theo giai đoạn, có DB audit riêng:

1. Luồng login/Gateway: PostgreSQL test, Eureka 8761, Identity 8081, Gateway 8080.
2. Quyền hồ sơ: thêm Patient 8082, Directory 8090, Appointment 8083 và dependency của các service này.
3. Toàn bộ luồng: RabbitMQ test, Queue 8084, Notification 8085, Consultation 8086, Prescription 8087, EMR 8088, Lab 8089; AI 8091 nếu kiểm tra AI.
4. UI: Doctor Web 3000, cấu hình API trỏ Gateway local. Không cần frontend cho test HTTP/backend.
5. Nhiều máy: hai instance service, discovery/network riêng, đồng hồ đồng bộ; client ngoài mạng nội bộ để thử reachability. Chỉ localhost không chứng minh firewall Internet.

Override URL DB cụ thể của từng service về DB test; đừng chỉ dựa vào tên profile `local`. Cấu hình mặc định ưu tiên remote DB; Queue/Appointment có logic reset dữ liệu khi khởi động. Không dùng dữ liệu chung cho ca xóa/sửa/khóa tài khoản.

Không chạy nguyên `start-local.sh` để audit: script hiện khởi động Consultation/Prescription hai lần và cleanup có lệnh kill toàn bộ Java/Node. Bật từng thành phần hoặc môi trường container test riêng.

Kiểm tra health/readiness và đăng ký Eureka trước test. Ví dụ read-only:

```bash
curl --silent --show-error --fail --max-time 10 http://127.0.0.1:8080/actuator/health
```

Trình tự live: ca login/token trước; ca đọc trái quyền sau; ca ghi trên fixture audit cuối; chụp DB/outbox trước-sau và dọn fixture audit. Các ca sai chữ ký tự tạo token bằng khóa test trong môi trường riêng; không dùng secret deployment trong tài liệu/log.

## 9. Bệnh nhân đến trực tiếp bệnh viện (walk-in)

### Cách phân hàng trong code hiện tại

Phân biệt người đã đặt lịch đến trực tiếp quầy để check-in với người chưa đặt lịch:

- Đã đặt lịch: STAFF check-in bằng ticket code và phòng được phân công, kích hoạt lượt có sẵn; không tạo thêm lượt WALK_IN.
- Chưa đặt lịch: API `POST /api/queues/entries` nhận patientId, userId, departmentId, priorityLevel và Idempotency-Key. Tạo lượt CONSULTATION/INITIAL, CHECKED_IN trong ngày, không có appointmentId, đủ điều kiện chờ gọi ngay.
- WALK_IN => NORMAL. APPOINTMENT đã check-in thông thường => NORMAL. Không có lane riêng dành cho walk-in/online; trong lane sắp theo eligibleSinceAt rồi sequenceNumber.
- PRIORITY => PRIORITY. RESULT_REVIEW là lượt quay lại sau kết quả, nằm ở lane riêng.
- Scheduler luân phiên PRIORITY -> NORMAL -> RESULT_REVIEW, tiếp tục từ lastServedLane và bỏ qua lane trống. Đây là lịch gọi mặc định khi dùng call-next, không phải ưu tiên tuyệt đối; gọi chọn đích danh có thể thay đổi thứ tự thực tế.
- Hàng khám theo cấu hình khoa/phòng; hàng xét nghiệm LAB_EXECUTION và hàng thuốc PHARMACY_DISPENSING theo service point riêng. Ba lane trên chỉ áp dụng CONSULTATION.

Ví dụ fixture trong cùng phòng, lastServedLane chưa có:

| Bệnh nhân | Điều kiện | Lane |
| --- | --- | --- |
| P1 | Lượt ưu tiên đã được xác nhận theo quy trình | PRIORITY |
| W1 | Chưa đặt lịch, tiếp nhận lúc 08:00 | NORMAL |
| A1 | Đặt lịch, check-in đủ điều kiện lúc 08:05 | NORMAL |
| W2 | Chưa đặt lịch, tiếp nhận lúc 08:10 | NORMAL |
| R1 | Có kết quả, được đưa lại hàng xem kết quả | RESULT_REVIEW |
| P2 | Lượt ưu tiên tiếp theo | PRIORITY |

Nếu các lượt vẫn đủ điều kiện và không có thao tác chọn đích danh: P1 -> W1 -> R1 -> P2 -> A1 -> W2. Không gọi A1 chỉ vì số vé nhỏ hơn hoặc đã đặt online; thời điểm đủ điều kiện trong lane mới quyết định.

### Các khoảng trống cần kiểm chứng

1. Controller manualIntake chỉ cho ADMIN; STAFF tiếp nhận chưa thể tạo walk-in bằng endpoint này (403). Màn hình staff/checkin hiện có check-in lượt có sẵn, chưa thấy luồng tạo walk-in đầy đủ.
2. manualIntake chấp nhận EMERGENCY nhưng newEntry chỉ gán QueueClass.PRIORITY khi priorityLevel là PRIORITY. EMERGENCY vì thế vào NORMAL. Không dùng enum này để kết luận hệ thống đã có quy trình cấp cứu phù hợp.
3. ManualIntakeRequest bắt buộc userId và patientId. Chưa có đường tiếp nhận hoàn chỉnh cho người chưa có tài khoản/hồ sơ.
4. manualIntake không gọi Patient/Directory để xác minh patientId thực sự thuộc userId đã gửi, và không lưu lý do ưu tiên như luồng check-in. Cần xác thực quan hệ và quy định ai được đánh dấu ưu tiên.
5. Idempotency-Key tránh lặp cùng request/key; không tự ngăn một bệnh nhân tạo nhiều lượt bằng key khác nhau.
6. QueueConfig hiện truy xuất theo departmentId; cần thử nhiều phòng cùng khoa để xác nhận định tuyến không chọn nhầm cấu hình hoặc trộn lượt.
7. Walk-in không có appointmentId; phải kiểm tra xuyên suốt tạo consultation, prescription, lab và quyền xem dữ liệu, vì các luồng khác dùng appointment để xác minh ownership/assignment.

### Kịch bản WALK-IN

Tiền điều kiện: DB audit riêng; các master patient/user/khoa/phòng tồn tại; một phòng A có bác sĩ và staff được phân công; fixture sáu lượt như bảng trên.

| ID | Thao tác | Mong đợi / kiểm tra |
| --- | --- | --- |
| WALK-01 | Người đã đặt lịch đến quầy, STAFF check-in ticket | Kích hoạt đúng lượt có sẵn; không sinh thêm WALK_IN; lưu actor/method |
| WALK-02 | Người chưa đặt lịch đã có hồ sơ, nhân viên tiếp nhận tạo lượt NORMAL | Chỉ actor được phân quyền theo quy trình mới tạo được; đúng phòng/ngày/patient; có số lượt; hiện CODE-GAP STAFF bị 403 |
| WALK-03 | Bệnh nhân chưa có tài khoản/hồ sơ | Luồng đăng ký hồ sơ và quan hệ sở hữu rõ ràng trước cấp lượt; không điền userId bác sĩ/staff để thay bệnh nhân; hiện chưa đầy đủ |
| WALK-04 | Dùng endpoint hiện tại với ADMIN và priorityLevel WALK_IN | Tạo CONSULTATION/INITIAL/CHECKED_IN, lane NORMAL, appointmentId null; vào đúng hàng |
| WALK-05 | Dùng PATIENT/DOCTOR tạo lượt qua Gateway hoặc giả header ADMIN | Từ chối; không có lượt/event mới; direct-service bypass thử riêng EDGE-01 |
| WALK-06 | Gửi patientId của A nhưng userId B; khoa không active/không tồn tại | Từ chối dữ liệu sai; không cấp lượt. Quan hệ patient-user hiện CODE-GAP |
| WALK-07 | Gửi lại cùng Idempotency-Key/payload; key cũ nhưng đổi payload | Cùng payload trả lại lượt; payload khác bị từ chối; không tăng số lượt/event |
| WALK-08 | Tạo hai lần bằng key khác nhau cho cùng bệnh nhân | Áp dụng quy tắc số lượt active được duyệt; ghi nhận hiện code không có chống trùng theo bệnh nhân |
| WALK-09 | Gọi-next liên tục trên fixture P1/W1/A1/W2/R1/P2 | Thứ tự ví dụ đúng; NORMAL theo eligibleSinceAt/sequence; kiểm tra lastServedLane |
| WALK-10 | Một lane trống; một ca đang khám | Bỏ qua lane trống; thao tác call/start tuân thủ trạng thái; không tạo lượt khám chồng trái quy trình |
| WALK-11 | Đánh dấu PRIORITY / EMERGENCY | Quyền và lý do ưu tiên được kiểm chứng theo policy; ghi nhận EMERGENCY hiện rơi NORMAL, chưa pass nghiệp vụ |
| WALK-12 | DOCTOR_B/STAFF_B thao tác lượt phòng A | 403; đúng actor phòng A mới xử lý; thử cả route phòng và route khoa để bắt bypass |
| WALK-13 | Gọi bệnh nhân vắng mặt; recall/miss/requeue | Theo missed policy đã cấu hình; không đổi quyền sở hữu/phòng; thứ tự và audit xác định |
| WALK-14 | Hai bác sĩ/instance gọi-next đồng thời | Không gọi trùng một lượt; không ghi đè calledBy; kiểm tra transaction/idempotency thực tế |
| WALK-15 | Walk-in được khám, có chỉ định xét nghiệm rồi có kết quả | Sang LAB_EXECUTION riêng, sau đó RESULT_REVIEW đúng ca/bác sĩ; không tạo thêm INITIAL |
| WALK-16 | Walk-in có đơn thuốc xác nhận | Sang PHARMACY_DISPENSING đúng quầy; không còn trong hàng khám sau hoàn tất; staff khác quầy bị từ chối |
| WALK-17 | PATIENT chủ hồ sơ xem trạng thái/kết quả của walk-in không có appointment | Truy cập đúng hồ sơ được phép; người khác bị từ chối; đặc biệt kiểm tra logic ownership phụ thuộc appointment |
| WALK-18 | Hai phòng cùng khoa, tạo walk-in và gọi-next | Định tuyến đúng phòng đã được chọn theo quy trình; không lẫn hàng giữa phòng; hiện cấu hình theo khoa cần kiểm chứng |

Các ca này là kế hoạch CODE/LIVE, chưa chạy HTTP. Không coi ví dụ scheduler là quy trình lâm sàng đã được bệnh viện phê duyệt.

## 10. Bằng chứng và tiêu chí hoàn thành

Code liên quan (đường dẫn tính từ root):

- `careflow-api-gateway/src/main/java/com/careflow/gateway/security/JwtAuthenticationFilter.java`: xác minh JWT, thay header, public path.
- `careflow-api-gateway/src/main/resources/application.yml`: route/discovery/fallback JWT config.
- `careflow-identity-service/src/main/java/com/careflow/identity/config/SecurityConfig.java`: stateless; user management ADMIN.
- `careflow-identity-service/src/main/java/com/careflow/identity/service/AuthService.java`, `RefreshTokenService.java`: mật khẩu, khóa tài khoản, rotation/revoke.
- `careflow-consultation-service/src/main/java/com/careflow/consultation/controller/ConsultationController.java`: endpoint legacy và endpoint kiểm tra assignment.
- `careflow-consultation-service/src/main/java/com/careflow/consultation/config/FeignClientInterceptorConfig.java`: context fallback.
- `careflow-patient-service/src/main/java/com/careflow/patient/controller/HealthRecordController.java`: thiếu actor authorization ở health records.
- `careflow-emr-service/src/main/java/com/careflow/emr/controller/MedicalRecordController.java`: thiếu actor authorization ở EMR.
- `careflow-queue-service/src/main/java/com/careflow/queue/controller/QueueController.java`: route khoa và route phòng.
- `careflow-lab-service/src/main/java/com/careflow/lab/service/LabOrderService.java`: role/claim/ownership.
- `docker-compose.yml`: published ports.

Hoàn thành khi: có cả ca đúng và sai quyền cho mỗi action/tài nguyên; denied request không ghi DB/file/event; direct ingress không bypass; token revocation và nhiều instance đáp ứng SLA đã định; report tách rõ UNIT, LIVE và FUTURE. Chưa có đủ bằng chứng này để kết luận hệ thống hiện an toàn.

Tài liệu đối chiếu:

- [OWASP Microservices Security](https://cheatsheetseries.owasp.org/cheatsheets/Microservices_Security_Cheat_Sheet.html): service authorization, identity propagation và service-to-service authentication.
- [OWASP REST Security](https://cheatsheetseries.owasp.org/cheatsheets/REST_Security_Cheat_Sheet.html): token validation và shared MAC key.
- [OWASP Multifactor Authentication](https://cheatsheetseries.owasp.org/cheatsheets/Multifactor_Authentication_Cheat_Sheet.html): các yếu tố độc lập và passkeys.
- [OWASP Passkey Security](https://cheatsheetseries.owasp.org/cheatsheets/Passkey_Security_Cheat_Sheet.html): WebAuthn challenge/credential/recovery.
- [OWASP Security Questions](https://cheatsheetseries.owasp.org/cheatsheets/Choosing_and_Using_Security_Questions_Cheat_Sheet.html): hạn chế của câu hỏi bí mật.
