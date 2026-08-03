# API Gateway Service Contract

> Contract ID: `CF-SVC-01` | Version: `1.0` | Module: `careflow-api-gateway`

## 1. Trách nhiệm

- Là điểm vào duy nhất của Patient Mobile, Doctor Web và Staff/Lab Web.
- Xác thực JWT, loại bỏ identity header giả và gắn trusted headers.
- Tạo hoặc chuẩn hóa correlation ID.
- Route request đến đúng service qua Eureka.
- Áp dụng CORS, giới hạn kích thước request, timeout và response bảo mật thống nhất.

Không chứa nghiệp vụ đặt lịch, queue, hồ sơ, toa thuốc hoặc dữ liệu domain. Không gọi database domain.

## 2. Route bắt buộc

| Public path | Upstream service |
|---|---|
| `/api/auth/**`, `/api/users/**` | `identity-service` |
| `/api/patients/**` | `patient-service` |
| `/api/appointments/**` | `appointment-service` |
| `/api/queues/**` | `queue-service` |
| `/api/consultations/**` | `consultation-service` |
| `/api/prescriptions/**` | `prescription-service` |
| `/api/emr/**` | `emr-service` |
| `/api/labs/**` | `lab-service` |
| `/api/notifications/**`, `/ws/**` | `notification-service` |
| `/api/analytics/**` | `analytics-service` |
| `/api/ai/**` | `ai-service` |

Endpoint public không cần JWT:

```text
POST /api/auth/register
POST /api/auth/login
POST /api/auth/refresh
POST /api/auth/logout
OPTIONS /**
GET /actuator/health/**
GET /v3/api-docs/**
GET /swagger-ui/**
WebSocket handshake /ws/**
```

Mọi endpoint khác phải có Bearer token hợp lệ.

## 3. Security contract

Gateway phải:

1. Xóa `X-User-Id`, `X-User-Role`, `X-Correlation-Id` do client gửi.
2. Verify signature, issuer, expiry và role của JWT.
3. Gắn `X-User-Id` từ `sub`, `X-User-Role` từ claim `role`.
4. Giữ correlation ID hợp lệ dạng UUID hoặc sinh UUID mới.
5. Trả `401` theo `ApiResponse` nếu token thiếu/sai/hết hạn.

Downstream vẫn chịu trách nhiệm authorization và ownership. Gateway không thay thế kiểm tra nghiệp vụ.

## 4. Failure behavior

| Tình huống | Kết quả |
|---|---|
| Thiếu/sai JWT | `401` |
| Request lớn hơn 20 MB | `413` |
| Không có route | `404` |
| Service không khả dụng | `503` với correlation ID |
| Connect quá 3 giây | Ngắt và trả `504`/`503` nhất quán |
| Response quá 5 giây | Timeout; không retry command ghi dữ liệu |

Gateway không tự retry `POST`, `PUT`, `PATCH`, `DELETE`.

## 5. Mock và contract test

Frontend có thể mock Gateway bằng base URL duy nhất và các trusted behavior sau:

```http
Authorization: Bearer <jwt>
X-Correlation-Id: 916b5544-3084-4ae8-8249-fcbdc0f0dc9f
```

Test bắt buộc:

- public endpoint đi qua không cần JWT;
- protected endpoint thiếu JWT trả `401`;
- client giả `X-User-Id` bị xóa và thay bằng subject thật;
- role ngoài allow-list bị từ chối;
- route của đủ 12 service được resolve;
- correlation ID được truyền xuyên suốt;
- request quá giới hạn và upstream timeout trả lỗi đúng.

## 6. Definition of Done

### `CONTRACT_READY`

- Route table, public endpoint, trusted header và failure behavior đã chốt.
- Frontend chỉ cần một base URL và có mock `401`, `403`, `503`.

### `FUNCTIONAL_READY`

- Route đủ 12 service.
- JWT filter và CORS/request size/timeout có automated test.
- Không có domain logic hoặc domain database.

### `INTEGRATION_READY`

- Token thật từ Identity truy cập được ít nhất một endpoint của từng service đã chạy.
- Trusted headers đến downstream đúng và header giả bị loại.
- Eureka unregister hoặc service down tạo failure response dự kiến.

### `DEMO_READY`

- Patient Mobile và Doctor Web chỉ gọi qua port `8080`.
- Log truy vết được một hành trình bằng correlation ID.
- Có smoke script kiểm tra login, một protected API, `401` và route health.
