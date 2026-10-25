# API mobile đã đối chiếu từ careflow

Nguồn: `frontend/patient-mobile/lib/services/*.dart`, `config/api_config.dart`, các controller Java trên snapshot `develop@8bf77e2`. Các endpoint dưới đây qua Gateway; response JSON dùng `{status,message,data,timestamp}`. Client chỉ gửi `Authorization: Bearer <accessToken>`, không gửi `X-User-Id`/`X-User-Role`.

| Phần | Method và endpoint | Giao diện / dữ liệu |
|---|---|---|
| Identity (A) | `POST /api/auth/register` | `username,email,password` |
| Identity (A) | `POST /api/auth/login` | `usernameOrEmail,password`; nhận `accessToken,refreshToken,user` trong `data` |
| Identity (A) | `GET /api/auth/me` | Xác minh phiên và user hiện tại |
| Identity (A) | `POST /api/auth/refresh` | `refreshToken`; rotation theo mobile |
| Identity (A) | `POST /api/auth/logout` | Thu hồi refresh token, xóa phiên cục bộ |
| Identity (A) | `POST /api/auth/ekyc` | Multipart `image`; trang eKYC thêm trong bản giả lập |
| Patient | `GET /api/patients/user/{userId}` | Hồ sơ mặc định |
| Patient | `GET /api/patients/user/{userId}/profiles` | Nhiều hồ sơ thuộc tài khoản |
| Patient | `POST /api/patients`, `GET/PUT/DELETE /api/patients/{id}` | Tạo/xem/sửa/xóa hồ sơ |
| Hồ sơ sức khỏe | `GET/POST /api/patients/{id}/health-records` | Danh sách/tạo mới; multipart `request` JSON và `files` |
| Hồ sơ sức khỏe | `GET/PUT/DELETE /api/patients/{id}/health-records/{recordId}` | Xem/sửa/xóa bản ghi |
| Hồ sơ sức khỏe | `GET /api/patients/{id}/health-records/files/{fileId}` | URL file theo service mobile |
| Đặt lịch | `GET /api/appointments/departments` | Khoa từ API thật |
| Đặt lịch | `GET /api/appointments/time-slots` | Query `department,date` |
| Đặt lịch | `GET /api/appointments/time-slots/availability` | Query `department,date`; sức chứa slot |
| Đặt lịch | `POST /api/appointments` | Payload từ booking flow Flutter gốc |
| Đặt lịch | `GET /api/appointments/patient/{patientId}` | Lịch của hồ sơ đang chọn |
| Đặt lịch | `GET /api/appointments/{id}`, `PUT /api/appointments/{id}/cancel` | Xem/hủy lịch |
| Queue (A) | `GET /api/queues/tickets/appointment/{appointmentId}` | Phiếu khám thật |
| Queue (A) | `GET /api/queues/patients/{patientId}/current` | Hàng đợi của hồ sơ |
| Queue (A) | `POST /api/queues/check-in` | `appointmentId,checkInQrToken,latitude,longitude,accuracyMeters` |
| Notification (A) | `GET /api/notifications?limit=50` | Inbox tài khoản |
| Notification (A) | `POST /api/notifications/{id}/read`, `POST /api/notifications/read-all` | Đánh dấu đã đọc theo API/mobile hiện có |
| Notification (A) | WebSocket `/ws/notifications` | STOMP 1.2; `CONNECT` có Bearer; subscribe `/user/queue/notifications` |
| Consultation | `GET /api/consultations/appointment/{appointmentId}` | Dữ liệu khám trong hành trình API thật |
| Lab | `GET /api/labs/orders/consultation/{consultationId}` | Chỉ định/kết quả theo lượt khám |
| Lab | `GET /api/labs/orders/patient/{patientId}` | Chỉ định theo bệnh nhân |
| Prescription | `GET /api/prescriptions/consultation/{consultationId}` | Danh sách toa thuốc theo lượt khám |
| Prescription | `GET /api/prescriptions/patient/{patientId}` | Toa thuốc theo bệnh nhân |

Các API staff/admin như tạo QR theo phòng, gọi bệnh nhân, cấu hình geofence và khóa tài khoản không được gọi bằng danh tính bệnh nhân. Dùng Hospital Web và API có quyền tương ứng để tạo tình huống kiểm thử cho mobile.

Bản web proxy `/api/*` và upgrade `/ws/notifications` tới Gateway đã chọn. Body, JWT, multipart boundary và mã lỗi backend được giữ nguyên. Origin trình duyệt được giữ để Gateway/Notification vẫn kiểm tra allowed origins. Nhật ký chỉ giữ method/path đã che UUID/status/thời gian; không lưu body, header hay query.

## Định danh máy trong workspace

Bản nhiều máy vẫn giữ nguyên toàn bộ endpoint và payload nghiệp vụ. `X-Emulator-Device` chỉ dùng giữa iframe và proxy để gắn nhãn request; proxy xóa header này trước khi chuyển tới Gateway. Đây không phải danh tính xác thực. Mỗi iframe giữ JWT riêng và backend vẫn kiểm tra quyền theo JWT đó.

`GET /__emulator/config` bổ sung `storageScope`, fingerprint của Gateway, để tách vùng lưu trữ theo môi trường. Trình duyệt không gửi token qua postMessage giữa các máy; các thông báo lên workspace chỉ chứa trạng thái boolean/realtime/ready. Lệnh điều hướng từ workspace được kiểm tra cùng origin và đúng cửa sổ cha.
