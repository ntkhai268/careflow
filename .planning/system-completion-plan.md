# CareFlow — Kế hoạch hoàn thiện hệ thống theo dịch vụ

> Kế hoạch tiếp nối theo bản iPhone (10) và đợt vá bảo mật ngày 19/10/2026: [Hoàn thiện mobile và điều kiện phát hành](product-completion-plan-2026-10-19.md). Tài liệu dưới đây giữ baseline thiết kế ngày 14/10; không dùng các khoảng trống lịch sử để suy ra trạng thái hiện tại.

Ngày lập: 2026-10-14. Baseline kiểm tra: develop / 8bf77e2.
Trạng thái: kế hoạch đề xuất, chưa triển khai và chưa xác nhận quy trình bệnh viện.
Phạm vi: các vấn đề phân quyền, bảo mật kiến trúc, tiếp nhận trực tiếp, hỗ trợ người cao tuổi và điều phối ngoại trú đã thảo luận. Không bổ sung nghiệp vụ bảo hiểm, thanh toán thật hay cấp cứu toàn diện.

## 1. Kết quả phải đạt

- Người không có smartphone, tài khoản, GPS hoặc người nhà vẫn được tiếp nhận bằng nhân viên và phiếu giấy.
- Người đặt lịch và người đến trực tiếp dùng cùng hồ sơ và hành trình nghiệp vụ; không phải giả userId hay tạo tài khoản bệnh nhân để hệ thống chạy được.
- Mỗi request được xác thực; mỗi thao tác được phân quyền theo tài nguyên/phân công; gọi trực tiếp service không bypass.
- Mỗi bước khám, xét nghiệm, xem kết quả, phát thuốc có đúng lượt và liên kết; xử lý lặp hoặc nhiều instance không phục vụ trùng.
- Ngoại lệ có người chịu trách nhiệm, lý do và audit; bệnh nhân nhận được hướng dẫn qua kênh phù hợp.
- Có bằng chứng kiểm tra cả luồng chấp nhận, từ chối và sự cố. Không dùng nhãn checklist cũ hoặc unit test pass để thay nghiệm thu.

## 2. Nguyên tắc và quyết định nghiệp vụ

### Định hướng dùng để thiết kế

1. Patient là hồ sơ người bệnh, Identity User là tài khoản truy cập. Patient có thể chưa liên kết User. Người nhà có quan hệ đại diện được xác minh, không tự nhận quyền bằng cách nhập số điện thoại/CCCD.
2. Một lần đến viện có visitId. Đề xuất Queue sở hữu bản ghi tiếp nhận Visit vì đang sở hữu phiếu/lượt, tránh thêm service mới; Appointment sở hữu lịch hẹn/capacity; Consultation sở hữu nội dung và trạng thái ca khám. Chốt ranh giới này trước migration.
3. Visit liên kết patientId, source (APPOINTMENT/WALK_IN), appointmentId có thể null, actor tiếp nhận, ngày cơ sở, nhu cầu hỗ trợ và trạng thái hành trình. Không có tài khoản vẫn có visitId/ticket.
4. Queue entry thuộc visitId và điểm phục vụ cụ thể. Consultation gắn visitId; lab/prescription gắn consultationId và patientId, tham chiếu visitId để đối soát. Không cho client tự kết hợp các ID không cùng hành trình.
5. Hồ sơ bệnh nhân cần hỗ trợ, quyền ưu tiên phục vụ và phân luồng khẩn là ba khái niệm riêng. Nhân viên y tế quyết định tình trạng chuyên môn. Luồng cần chuyển cấp cứu ra khỏi scheduler ngoại trú, ghi nhận bàn giao; không xây hệ thống cấp cứu trong đợt này.
6. App là kênh tùy chọn. Staff được tiếp nhận/check-in trong phạm vi được phân công, không cần quyền ADMIN. Phiếu giấy/màn hình/âm thanh có cùng nguồn trạng thái với mobile.
7. Hàng khám theo phòng/phiên; lab và pharmacy theo service point. Người đặt online không mặc nhiên đứng trước người đến trực tiếp.
8. Phân nhóm PRIORITY/NORMAL/RESULT_REVIEW, FIFO trong nhóm theo thời điểm đủ điều kiện; chính sách xen lượt và bảo vệ lịch hẹn phải được nghiệp vụ duyệt. Không coi vòng 1:1:1 hiện tại là chính sách đã chốt.
9. Giữ nguyên lịch sử khi chuyển phòng/lỡ lượt; không tự xóa lượt để sửa trạng thái. Người chờ lâu phải hiện trên màn hình điều phối; giới hạn chờ là tham số nghiệp vụ được duyệt.
10. Không chỉ kiểm tra HTTP status: denied request không ghi DB/file/outbox, không lộ dữ liệu hoặc phát thông báo.

Ma trận quyền chi tiết: [access-control-target.md](security/access-control-target.md). Đề xuất ADMIN không có clinical bypass mặc định; role/capability và phạm vi khác nhau phải được phản ánh trong mọi service plan.

### Cần chốt với đại diện bệnh viện trước giai đoạn 2

| Quyết định | Đề xuất để review | Người duyệt nghiệp vụ |
| --- | --- | --- |
| Chủ sở hữu Visit | Queue; dùng chung reference, không database dùng chung | Kiến trúc + tiếp nhận + nhóm service |
| Nhóm ưu tiên, chứng cứ và người xác nhận | Danh mục do bệnh viện duyệt; hỗ trợ công nghệ không tự nâng ưu tiên | Đại diện chuyên môn/tiếp nhận |
| Lịch hẹn và walk-in | Capacity theo phòng/phiên; dành công suất walk-in; giải phóng công suất không dùng theo quy tắc | Điều phối |
| Thứ tự xen hàng | Một chính sách có trọng số đã duyệt, ngưỡng chờ và lý do override; tránh nhiều thuật toán tùy ý | Điều phối + bác sĩ |
| Sớm/muộn/vắng/quay lại | Khoảng chấp nhận, số lần gọi, thời gian tìm người và cách giữ thời gian chờ | Tiếp nhận/điều phối |
| Phòng thay thế và bàn giao | Cùng khả năng phục vụ, bác sĩ/phòng active, còn công suất; ghi lý do | Chuyên môn/điều phối |
| Đại diện và liên kết hồ sơ | Xác minh quan hệ/đồng ý theo quy trình, quyền cụ thể và khả năng thu hồi | Quản lý dữ liệu bệnh viện |
| Thu hồi phiên/quyền | Đề xuất SLA không quá 60 giây sau disable/đổi role/logout phiên; giá trị phải chốt | Quản trị/bảo mật |
| MFA và phục hồi | MFA bắt buộc cho ADMIN; mở rộng nhân viên theo đánh giá; không dùng câu hỏi bí mật | Quản trị/bảo mật |
| Dự phòng khi mất hệ thống | Phiếu/sổ tiếp nhận đánh số riêng, đối soát sau phục hồi; không cung cấp kết quả lâm sàng từ cache cũ | Vận hành |

Đây là điểm chốt thiết kế, không ngăn sửa các lỗi phân quyền đã xác định ở giai đoạn 1.

## 3. Kiến trúc bảo mật đích đề xuất

- Identity giữ private key, ký access JWT bất đối xứng; Gateway và service xác minh bằng public key/JWKS.
- JWT có issuer, audience theo token profile, exp bắt buộc, subject user, role/capability cần thiết, sessionId/tokenVersion; chốt thuật toán và key rotation. Các instance kiểm tra như nhau.
- Access token ngắn hạn kết hợp refresh rotation và kiểm tra trạng thái/sessionVersion theo SLA. Không nhét dữ liệu y tế vào JWT.
- Service bảo vệ endpoint bằng cơ chế xác thực chung; bỏ tin header X-User-* trực tiếp. Có thể giữ header để tương thích tạm thời nhưng principal xác thực là nguồn quyền.
- Service-to-service dùng credential riêng, scope/audience rõ ràng hoặc mTLS; service caller và user thay mặt là hai danh tính riêng. Không tự đặt role DOCTOR khi thiếu context.
- User token được truyền/xử lý chỉ ở API phù hợp; thao tác nền dùng danh tính service được phép và quan hệ tài nguyên, không giả bệnh nhân/bác sĩ.
- Chỉ ingress/Gateway công khai; business service và Eureka ở private network; DB/broker không public. Local debug dùng loopback/mạng test riêng.
- Một service có thể tự xác minh token hợp lệ nhưng endpoint internal còn phải xác minh caller được phép. Public service endpoint nếu có phải được khai báo rõ, không phát sinh do publish port.
- Không dùng CORS, địa chỉ IP nội bộ hoặc chữ ký JWT riêng lẻ làm toàn bộ quyết định quyền. Service kiểm tra role/action + ownership/assignment + business state.
- Quyền có dependency không xác minh được => không thực thi; lỗi availability có thể 503, không trả thành công với dữ liệu rỗng như thể không có bệnh án.
- Library chung chỉ chuẩn hóa token/principal/error/audit; từng service giữ chính sách nghiệp vụ và dữ liệu của mình.

Việc đổi token phải có giai đoạn tương thích kiểm soát; sau cutover loại bỏ khóa HMAC và header fallback. Không để chế độ tương thích mở vô thời hạn.

## 4. Hợp đồng liên dịch vụ cần chốt

| Dữ liệu/API | Nguồn sở hữu | Bên dùng | Quy tắc |
| --- | --- | --- | --- |
| User/session/MFA/keys | Identity | Gateway, tất cả service | Không tự cấp danh tính trong service nghiệp vụ |
| Patient + quyền đại diện | Patient | Queue, Appointment, Consultation, EMR, Lab, Prescription | authorize patientId cụ thể, không lấy hồ sơ đầu tiên của user |
| Phòng/khả năng phục vụ/phân công | Directory | Appointment, Queue, các service thực thi | Phân công có hiệu lực theo ngày/phiên; chặn master inactive |
| Schedule/capacity | Appointment | Tiếp nhận, Queue | Một nguồn capacity; khóa cạnh tranh; không giả online appointment cho walk-in |
| Visit/ticket/queue step | Queue | Tất cả luồng và UI | Cùng visitId; appointmentId tùy chọn; không tạo bước trùng |
| Consultation context/assignment | Consultation | Lab, Prescription, EMR, Queue | patientId/visitId/bác sĩ khớp; legacy API kiểm tra như API mới |
| Lab order/result | Lab | Queue, Consultation, EMR | Đủ required results theo ca; có revision/review; không phát lại lượt review trùng |
| Prescription/dispensing | Prescription | Queue, EMR, UI | Chỉ đơn xác nhận mới phát; cấp thuốc và hoàn tất queue đối soát được |
| Notification delivery | Notification | UI/thiết bị hỗ trợ | Không là nguồn trạng thái nghiệp vụ; không gửi dữ liệu cho quan hệ bị thu hồi |

Envelope chuẩn: eventId, eventType, schemaVersion, occurredAt, producer, correlationId, aggregateId/version và payload tối thiểu patientId/visitId/consultationId khi áp dụng. Outbox/inbox idempotency, retry/DLQ và thứ tự phiên bản; chấp nhận duplicate delivery nhưng không duplicate nghiệp vụ. Không đưa access/refresh token vào event.

## 5. Thứ tự triển khai và điểm nghiệm thu

| Giai đoạn | Kết quả | Công việc chính | Điều kiện chuyển giai đoạn |
| --- | --- | --- | --- |
| 0 — baseline | Contract và môi trường kiểm tra thống nhất | Chốt Visit/ma trận quyền; kiểm kê endpoint, schema, seed; reconcile docs FIFO/ba lane; DB test riêng | Kịch bản/fixture/contract version và gap list có owner |
| 1 — đóng lỗ hổng | Không vượt quyền qua endpoint cũ hay port service | Guard Health Records/EMR/Consultation/Queue/Lab/AI; Gateway allowlist; principal/caller; token revocation và network boundary | Test trực tiếp + Gateway chấp nhận/từ chối; không side effect trái quyền |
| 2 — tiếp nhận bao trùm | Người không có tài khoản đi được tới hàng khám | Patient nullable link/đại diện; Directory room/session; Visit; STAFF walk-in; capacity; phiếu giấy | Người già đi một mình và người nhà hỗ trợ đều pass; không dùng ID giả |
| 3 — hành trình hoàn chỉnh | Khám -> CLS -> xem kết quả -> thuốc đúng liên kết | Queue fairness/missed/transfer; Consultation/Lab/Prescription/EMR; event idempotency | Walk-in và online hoàn tất, hai phòng cùng khoa, retry/đồng thời không trùng |
| 4 — vận hành an toàn | Đăng nhập và triển khai nhiều instance đáp ứng policy | MFA ADMIN/passkey theo scope; keys/session revocation; multi-host/network; backup, monitor, recovery | Hai instance quyết định quyền thống nhất; mất dependency từ chối an toàn; phục hồi được |
| 5 — nghiệm thu | Bộ bằng chứng và hướng dẫn bàn giao | BA UAT, E2E, accessibility, runbook, đối soát schema/DBML | Tất cả must-have pass; known limitation có owner; không gắn nhãn an toàn tuyệt đối |

Các bước trong cùng giai đoạn có thể được các nhóm service triển khai độc lập sau khi contract chốt. Không triển khai trên một nhánh feature chung chứa mọi service.

## 6. Kế hoạch theo thành phần

| Thành phần | File kế hoạch | Trọng tâm |
| --- | --- | --- |
| Common | [completion-plan.md](common/completion-plan.md) | Principal, lỗi, event envelope và thư viện dùng chung |
| API Gateway | [completion-plan.md](api-gateway/completion-plan.md) | JWT, allowlist, routing, giới hạn ingress |
| Identity | [completion-plan.md](identity-service/completion-plan.md) | Session/revocation, khóa ký, MFA/recovery |
| Patient | [completion-plan.md](patient-service/completion-plan.md) | Hồ sơ không tài khoản, đại diện, Health Records authorization |
| Hospital Directory | [completion-plan.md](hospital-directory-service/completion-plan.md) | Phòng/phiên/assignment/capability |
| Appointment | [completion-plan.md](appointment-service/completion-plan.md) | Capacity online/walk-in và assignment |
| Queue | [completion-plan.md](queue-service/completion-plan.md) | Visit, walk-in, fairness, hỗ trợ, ngoại lệ |
| Consultation | [completion-plan.md](consultation-service/completion-plan.md) | Guard legacy, ca không appointment, điều phối |
| Lab | [completion-plan.md](lab-service/completion-plan.md) | Phụ trách/claim, kết quả và review |
| Prescription | [completion-plan.md](prescription-service/completion-plan.md) | Xác nhận/phát thuốc và nhiều hồ sơ |
| EMR | [completion-plan.md](emr-service/completion-plan.md) | Quyền hồ sơ và tổng hợp đúng context |
| Notification | [completion-plan.md](notification-service/completion-plan.md) | Người nhận, WebSocket và kênh không dùng app |
| AI | [completion-plan.md](ai-service/completion-plan.md) | Quyền interaction/context và dữ liệu tối thiểu |
| Frontend | [completion-plan.md](frontend/completion-plan.md) | Web tiếp nhận, màn hình gọi lượt, mobile tùy chọn |
| Deployment/Eureka | [completion-plan.md](deployment/completion-plan.md) | Private network, multi-instance, môi trường test |

## 7. Kịch bản nghiệm thu xuyên hệ thống

| ID | Hành trình | Bằng chứng bắt buộc |
| --- | --- | --- |
| UAT-01 | Người cao tuổi, không phone/account, đến một mình | Hồ sơ -> visit -> phiếu giấy -> khám -> kết quả/thuốc; không buộc login/GPS |
| UAT-02 | Người nhà đưa đến hoặc đăng ký giúp | Đại diện đã xác minh; actor != patient; quyền có thể thu hồi |
| UAT-03 | Đã đặt lịch nhưng quên mã/phone hết pin | Tìm lịch có xác minh, check-in lượt cũ, không tạo WALK_IN trùng |
| UAT-04 | Đến sớm/muộn, không nghe gọi, đi vệ sinh, quay lại | Quy tắc đã duyệt; recall/requeue có lý do; không mất lịch sử |
| UAT-05 | Hai phòng cùng khoa; phòng/bác sĩ ngừng phục vụ | Định tuyến đúng, capacity không vượt, transfer có audit/bàn giao |
| UAT-06 | Ưu tiên, thường và xem kết quả cùng đông | Chính sách xen lượt đúng; người chờ lâu được cảnh báo; override truy vết |
| UAT-07 | Nhu cầu xử lý khẩn hoặc tình trạng đổi trong lúc chờ | Nhân viên y tế đánh giá; ghi bàn giao luồng phù hợp, không dùng NORMAL thay cấp cứu |
| UAT-08 | Walk-in không appointment -> nhiều CLS -> review -> prescription | IDs khớp, chỉ review khi đủ required results, không event/lượt lặp |
| UAT-09 | Một tài khoản nhiều hồ sơ; tài khoản khác đoán IDs | Chỉ hồ sơ có quyền được đọc/ghi/file download; denied không side effect |
| UAT-10 | Token giả/hết hạn; direct service; legacy route; quyền đổi/khóa | Từ chối ở đúng lớp; SLA revocation trên mọi instance |
| UAT-11 | Hai staff/instance cùng tiếp nhận/gọi/phát thuốc | Không duplicate nghiệp vụ; audit đúng actor; replay kết quả nhất quán |
| UAT-12 | Mất mạng/broker/Directory, máy in lỗi, notification lỗi | Không nới quyền; hướng dẫn dự phòng; retry/đối soát không trùng |
| UAT-13 | Hết phiên, bệnh nhân còn chờ, khám nhiều khoa | Kết thúc/chuyển tiếp có người quyết định; không silently delete |
| UAT-14 | MFA ADMIN, mất thiết bị, recovery, passkey nếu triển khai | Không bypass bằng password/câu hỏi; quyền và recovery được audit |

Dùng [kịch bản bảo mật và walk-in](security/authentication-authorization-test-scenario.md) làm ca chi tiết; bổ sung expected result theo policy đã duyệt. 57 unit test riêng biệt đã pass qua các lượt audit, chưa chứng minh HTTP/network/UAT. Local Gateway chưa chạy tại thời điểm audit.

## 8. Kỷ luật triển khai

- Phân việc theo service/capability ID trong từng plan; mỗi việc có owner, trạng thái, dependency và bằng chứng nghiệm thu. Chưa gán tên người/ước lượng lịch vì chưa có nguồn lực.
- Branch feature riêng từng service; cập nhật main theo quy tắc dự án và tích hợp baseline develop mới nhất trước triển khai; không mất thay đổi local; không push main.
- GitNexus impact/context trước sửa symbol; cảnh báo risk HIGH/CRITICAL; refresh index nếu stale. detect_changes trước commit. Phiên lập kế hoạch chưa có MCP GitNexus và không sửa symbol.
- Migration/schema thực hiện trên branch service, đi kèm DBML tương ứng và đối soát dữ liệu cũ. Chưa sửa DBML hiện tại vì đây là proposal, chưa thay schema.
- Seed master idempotent; fixture online/walk-in/no-account/multi-profile/assignment cho ngày hiện tại. Reset dữ liệu queue chỉ môi trường test/dev được chỉ định; production không reset dữ liệu mỗi startup.
- Unit/contract tests dùng mock dependency; HTTP/UAT dùng DB/broker audit riêng. Không khởi động reset initializer trên remote DB dùng chung để thử.
- Các tài liệu checklist cũ giữ lịch sử, cần reconcile vào contract mới ở giai đoạn 0; không tự đánh dấu đã hoàn tất.
- Không chạy start-local.sh hiện tại để nghiệm thu khi chưa sửa khởi động trùng service và cleanup kill toàn bộ Java/Node.
- Không tự commit/push. Hồ sơ bàn giao gồm API/event contract, migration/DBML, seed, ma trận quyền, test report, known limitations và runbook.

## 9. Nguồn thiết kế

Các đề xuất dựa trên audit code baseline nêu trên và trao đổi BA; policy lâm sàng/ưu tiên chưa được bệnh viện duyệt. Tham khảo:
- [OWASP Microservices Security](https://cheatsheetseries.owasp.org/cheatsheets/Microservices_Security_Cheat_Sheet.html): xác thực caller, user context và phân quyền tại service.
- [OWASP REST Security](https://cheatsheetseries.owasp.org/cheatsheets/REST_Security_Cheat_Sheet.html): JWT claims, signatures, bảo vệ endpoint.
- [OWASP MFA](https://cheatsheetseries.owasp.org/cheatsheets/Multifactor_Authentication_Cheat_Sheet.html) và [Passkeys](https://cheatsheetseries.owasp.org/cheatsheets/Passkey_Security_Cheat_Sheet.html).
- [WHO Triage](https://www.who.int/tools/triage): phân loại theo mức độ cần xử lý chuyên môn.
- [NHS Inclusive Digital Healthcare](https://www.england.nhs.uk/publication/inclusive-digital-healthcare-a-framework-for-nhs-action-on-digital-inclusion/): bổ sung kênh phi số và hỗ trợ.

