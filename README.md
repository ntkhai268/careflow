# CareFlow — Identity & eKYC Service

Nhánh triển khai: `feature/dangkhoii/identity-service`

Identity Service chịu trách nhiệm đăng ký, đăng nhập, quản lý phiên JWT, khóa tài
khoản, phân quyền quản trị và eKYC giả lập cho CareFlow. API Gateway và Identity
Service đều xác thực JWT, vì vậy việc gọi thẳng service không thể bỏ qua lớp bảo mật.

## Trạng thái hiện tại

| Thành phần | Trạng thái |
|---|---|
| Identity Service | Hoạt động, port `8081` |
| API Gateway | Hoạt động, port `8080` |
| Eureka Server | Hoạt động, port `8761` |
| PostgreSQL | Flyway schema version `2` |
| Swagger/OpenAPI | `/swagger-ui/index.html`, `/v3/api-docs` |
| eKYC | Mock có kiểm tra ảnh thật, luôn trả `mock=true` |

Các chức năng đã có:

- Đăng ký tài khoản `PATIENT`; chuẩn hóa username/email về chữ thường.
- BCrypt password và policy mật khẩu mạnh.
- Đăng nhập bằng username hoặc email.
- Access token JWT và opaque refresh token được lưu dưới dạng SHA-256 hash.
- Refresh-token rotation, phát hiện reuse và thu hồi toàn bộ phiên còn hoạt động.
- Logout thu hồi refresh token hiện tại.
- Khóa tài khoản 15 phút sau 5 lần đăng nhập sai.
- Phân quyền `ADMIN` cho API cập nhật trạng thái tài khoản.
- Mock eKYC chỉ nhận JPEG/PNG hợp lệ, tối đa 5 MB.
- Health probe, graceful shutdown, Docker non-root và Compose healthcheck.

## Kiến trúc và trust boundary

```text
Flutter / Web
      │  Authorization: Bearer <access-token>
      ▼
API Gateway :8080 ── Eureka ── Identity Service :8081 ── PostgreSQL
      │                            │
      ├─ kiểm tra chữ ký/issuer    └─ kiểm tra lại JWT và phân quyền
      └─ xóa header giả mạo
```

Client không được tự gửi `X-User-Id` hoặc `X-User-Role`. Gateway luôn xóa các
header này trước khi tạo lại từ JWT cho downstream. Identity Service không dùng
header làm danh tính mà lấy principal trực tiếp từ Bearer token.

`JWT_SECRET` và issuer phải đồng nhất giữa Gateway và Identity Service. Secret phải
có ít nhất 32 byte và không được commit vào repository.

## API contract

Tất cả JSON response dùng envelope:

```json
{
  "status": 200,
  "message": "Mô tả kết quả",
  "data": {},
  "timestamp": "2026-07-20T16:00:00Z"
}
```

| Method | Endpoint | Input | Output chính | Auth |
|---|---|---|---|---|
| `POST` | `/api/auth/register` | `username`, `email`, `password` | `201`, `UserResponse` | Public |
| `POST` | `/api/auth/login` | `usernameOrEmail`, `password` | `200`, cặp token và user | Public |
| `POST` | `/api/auth/refresh` | `refreshToken` | `200`, cặp token mới | Public |
| `POST` | `/api/auth/logout` | `refreshToken` | `200` | Public |
| `GET` | `/api/auth/me` | Bearer access token | `200`, `UserResponse` | Authenticated |
| `POST` | `/api/auth/ekyc` | multipart field `image` | `200`, `EkycResponse` | Authenticated |
| `PATCH` | `/api/users/{id}/status` | `ACTIVE`, `LOCKED` hoặc `DISABLED` | `200`, `UserResponse` | ADMIN |

### Quy tắc input

- `username`: 3–50 ký tự; chỉ chữ, số, `.`, `_`, `-`.
- `email`: email hợp lệ, tối đa 255 ký tự.
- `password`: 8–72 ký tự, có chữ hoa, chữ thường, số và ký tự đặc biệt.
- `image`: JPEG hoặc PNG khớp cả MIME type và magic bytes, tối đa 5 MB.

### Login output

Payload trong `data`:

```json
{
  "accessToken": "<jwt>",
  "refreshToken": "<opaque-token>",
  "tokenType": "Bearer",
  "expiresInSeconds": 86400,
  "refreshTokenExpiresInSeconds": 2592000,
  "user": {
    "id": "<uuid>",
    "username": "patient01",
    "email": "patient01@example.com",
    "role": "PATIENT",
    "status": "ACTIVE"
  }
}
```

## Cơ chế refresh token

Refresh token đã được triển khai theo cơ chế rotation:

1. `/login` cấp một access token và một refresh token ngẫu nhiên 256-bit.
2. Database chỉ lưu SHA-256 hash, không lưu raw refresh token.
3. `/refresh` kiểm tra token tồn tại, chưa hết hạn, chưa bị thu hồi và user còn
   `ACTIVE`.
4. Token cũ bị revoke và được liên kết với token thay thế; response trả một access
   token và refresh token hoàn toàn mới.
5. Dùng lại refresh token cũ trả `401` và thu hồi các refresh token còn hoạt động
   của user để giới hạn token theft/replay.
6. `/logout` thu hồi refresh token được gửi lên. Access token đã cấp vẫn hợp lệ đến
   khi hết hạn; client phải xóa cả hai token sau logout.

Mobile app tự thử refresh một lần khi API trả `401`, lưu cặp token mới rồi retry
request ban đầu. Nếu refresh thất bại, app xóa phiên thay vì fallback sang mock.
Mock auth chỉ bật chủ động bằng `--dart-define=USE_MOCK_AUTH=true`.

## Khởi chạy bằng Docker Compose

Yêu cầu: Docker Engine có Compose v2.

```bash
git switch feature/dangkhoii/identity-service
cp .env.example .env
```

Đổi `JWT_SECRET` trong `.env` thành secret ngẫu nhiên tối thiểu 32 byte, sau đó:

```bash
docker compose up --build
```

Compose khởi chạy PostgreSQL → Eureka → Identity → Gateway theo healthcheck. Kiểm
tra trạng thái:

```bash
curl http://localhost:8081/actuator/health
curl http://localhost:8080/actuator/health
```

Dừng stack mà vẫn giữ volume database:

```bash
docker compose down
```

## Khởi chạy Identity Service trực tiếp

Yêu cầu: JDK 21, Maven 3.9+, PostgreSQL 16.

```bash
docker compose -f docker-compose.infra.yml up -d postgres

export JWT_SECRET="$(openssl rand -base64 48)"
mvn -pl careflow-identity-service -am clean package
java -jar careflow-identity-service/target/careflow-identity-service-1.0.0-SNAPSHOT.jar
```

Flyway tự tạo/validate schema `identity` khi service khởi động. Identity Service
không phụ thuộc RabbitMQ; broker trong file infra phục vụ các service event-driven
khác của CareFlow.

### Biến môi trường

| Biến | Mặc định | Ý nghĩa |
|---|---|---|
| `JWT_SECRET` | Bắt buộc | Secret ký và kiểm tra JWT |
| `JWT_EXPIRATION_MS` | `86400000` | Thời hạn access token |
| `JWT_REFRESH_EXPIRATION_MS` | `2592000000` | Thời hạn refresh token |
| `IDENTITY_DB_URL` | `jdbc:postgresql://localhost:5432/careflow_identity` | JDBC URL |
| `IDENTITY_DB_USERNAME` | `careflow` | Database user |
| `IDENTITY_DB_PASSWORD` | `careflow` | Database password |
| `EUREKA_URL` | `http://localhost:8761/eureka/` | Eureka endpoint |
| `JPA_SHOW_SQL` | `false` | In câu SQL để debug |

## Swagger và ví dụ sử dụng

Mở `http://localhost:8081/swagger-ui/index.html`. Với API bảo vệ, chọn
**Authorize** và nhập access token vào Bearer scheme.

Đăng ký và đăng nhập:

```bash
curl -X POST http://localhost:8081/api/auth/register \
  -H 'Content-Type: application/json' \
  -d '{
    "username":"patient01",
    "email":"patient01@example.com",
    "password":"Patient@123"
  }'

curl -X POST http://localhost:8081/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"usernameOrEmail":"patient01","password":"Patient@123"}'
```

Gọi qua Gateway:

```bash
curl http://localhost:8080/api/auth/me \
  -H 'Authorization: Bearer <ACCESS_TOKEN>'

curl -X POST http://localhost:8080/api/auth/ekyc \
  -H 'Authorization: Bearer <ACCESS_TOKEN>' \
  -F 'image=@/absolute/path/to/cccd.png;type=image/png'
```

Refresh và logout:

```bash
curl -X POST http://localhost:8081/api/auth/refresh \
  -H 'Content-Type: application/json' \
  -d '{"refreshToken":"<CURRENT_REFRESH_TOKEN>"}'

curl -X POST http://localhost:8081/api/auth/logout \
  -H 'Content-Type: application/json' \
  -d '{"refreshToken":"<CURRENT_REFRESH_TOKEN>"}'
```

## Kiểm thử

Backend:

```bash
mvn -pl careflow-api-gateway,careflow-identity-service -am clean test package
```

Mobile:

```bash
cd frontend/patient-mobile
flutter analyze
flutter test --no-test-assets
```

Docker Compose syntax:

```bash
JWT_SECRET=replace-with-at-least-32-random-bytes docker compose config --quiet
```

Bộ regression hiện kiểm tra JWT round-trip, refresh rotation/reuse, logout,
account lock, Gateway header spoofing, phân quyền admin, magic bytes của ảnh và
contract login trên Flutter.

## HTTP status chính

| Status | Trường hợp |
|---|---|
| `200` | Login, refresh, logout, `/me`, eKYC hoặc update status thành công |
| `201` | Đăng ký thành công |
| `400` | Body/field/multipart không hợp lệ |
| `401` | Sai đăng nhập, thiếu/sai JWT, refresh token hết hạn/reuse |
| `403` | Sai quyền hoặc tài khoản bị khóa/vô hiệu hóa |
| `409` | Username/email đã tồn tại |
| `413` | File vượt giới hạn upload |
| `415` | File không phải JPEG/PNG hợp lệ |
| `500` | Lỗi nội bộ đã được che thông tin triển khai |

## Giới hạn trước production

- eKYC là deterministic mock phục vụ tích hợp; chưa có OCR, face matching hoặc
  liveness provider thật và không persist hồ sơ xác minh.
- Logout thu hồi refresh token, không blacklist access token đã phát hành.
- Chưa có distributed rate limiting, audit/security event pipeline và SIEM.
- Production cần TLS, Secret Manager/KMS, backup database và monitoring tập trung.

## Tài liệu liên quan

- [Implementation plan](docs/implementation_plan.md)
- [Tài liệu trong thư mục docs](docs/)
