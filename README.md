# CareFlow — Common Foundation

Nhánh: `feature/dangkhoii/common-foundation`

`careflow-common` là shared foundation cho các Spring Boot microservice của CareFlow.
Module cung cấp contract HTTP, error mapping, correlation ID, event envelope và JPA
base entity. Đây là thư viện, không phải service độc lập: không có port hoặc Swagger
riêng.

## Phạm vi

| Thành phần | Trách nhiệm |
|---|---|
| `ApiResponse<T>` | JSON response envelope thống nhất |
| `ApiError` | Error code, path, correlation ID và field violations |
| `GlobalExceptionHandler` | Chuyển exception thành HTTP response an toàn |
| `CorrelationIdFilter` | Propagate/sinh `X-Correlation-Id` và đưa vào MDC |
| `EventEnvelope` | Metadata bắt buộc cho event RabbitMQ |
| `BaseEntity` | UUID, audit timestamps, optimistic locking và identity semantics |
| `AppConstants` | Role, trusted header, exchange và routing-key constants |

Spring MVC dependencies được đánh dấu optional, vì vậy một consumer chỉ dùng DTO
hoặc event contract không bị kéo theo Tomcat, Hibernate, HikariCP hay JDBC. Servlet
service có `spring-boot-starter-web` sẽ tự nhận common web beans qua Spring Boot
auto-configuration.

## Yêu cầu

- Java 21 trở lên; bytecode luôn compile với `--release 21`.
- Maven 3.9 trở lên, hoặc Maven Wrapper đi kèm repository.
- Docker Engine với Compose v2 nếu cần PostgreSQL/RabbitMQ cục bộ.

Maven Enforcer kiểm tra phiên bản Java/Maven trong mọi module. CI sử dụng Temurin 21.

## Build và kiểm thử

```bash
./mvnw -pl careflow-common -am clean test
./mvnw clean verify
```

Common test suite bao phủ:

- JSON/status contract của `ApiResponse`.
- Entity equality, stable hash code và audit lifecycle.
- Event metadata validation.
- Error detail masking.
- Correlation-ID propagation/sanitization.
- Auto-configuration trong servlet consumer context.

## Sử dụng trong service

```xml
<dependency>
    <groupId>com.careflow</groupId>
    <artifactId>careflow-common</artifactId>
    <version>${project.version}</version>
</dependency>
```

Không cần thêm `@ComponentScan("com.careflow.common")`. File
`AutoConfiguration.imports` tự đăng ký `GlobalExceptionHandler` và
`CorrelationIdFilter` khi consumer là servlet web application. Consumer vẫn có thể
override bằng bean cùng type.

## HTTP response contract

### Success output

Input Java:

```java
ApiResponse.success("Thành công", data);
ApiResponse.created("Đã tạo", data);
ApiResponse.of(202, "Đã tiếp nhận", data);
```

Output JSON:

```json
{
  "status": 200,
  "message": "Thành công",
  "data": {},
  "timestamp": "2026-07-20T17:00:00Z"
}
```

Status ngoài khoảng `100..599` bị từ chối. `ApiResponse.error` chỉ chấp nhận status
`4xx` hoặc `5xx`, tránh body báo lỗi nhưng HTTP status lại thành công.

### Error output

```json
{
  "status": 400,
  "message": "Validation failed",
  "data": {
    "code": "VALIDATION_FAILED",
    "path": "/api/patients",
    "correlationId": "request-123",
    "violations": [
      {"field": "email", "message": "must be a well-formed email address"}
    ]
  },
  "timestamp": "2026-07-20T17:00:00Z"
}
```

Handler mặc định hỗ trợ:

| Exception | HTTP | Code |
|---|---:|---|
| `ResourceNotFoundException` | 404 | `RESOURCE_NOT_FOUND` |
| `BusinessException` | 4xx/5xx do service khai báo | Code nghiệp vụ |
| Bean/constraint validation | 400 | `VALIDATION_FAILED` |
| Body JSON sai | 400 | `MALFORMED_REQUEST` |
| Thiếu/sai parameter, header, multipart part | 400 | `INVALID_REQUEST` |
| Upload quá lớn | 413 | `PAYLOAD_TOO_LARGE` |
| Media type không hỗ trợ | 415 | `UNSUPPORTED_MEDIA_TYPE` |
| Method không hỗ trợ | 405 | `METHOD_NOT_ALLOWED` |
| Exception chưa xử lý | 500 | `INTERNAL_ERROR` |

Lỗi `500` được log server-side cùng correlation ID nhưng response không chứa message,
SQL, credential hay stack trace nội bộ.

Business error có code ổn định:

```java
throw new BusinessException(
    409,
    "USERNAME_EXISTS",
    "Username already exists"
);
```

## Correlation ID

Client có thể gửi:

```http
X-Correlation-Id: mobile-request-123
```

Chỉ giá trị `[A-Za-z0-9._-]`, tối đa 128 ký tự được chấp nhận. Giá trị thiếu hoặc
không an toàn được thay bằng UUID. ID được:

- trả lại trong response header;
- lưu trong request attribute;
- đưa vào MDC với key `correlationId`;
- gắn vào mọi error response.

`X-User-Id` và `X-User-Role` chỉ là tên header thống nhất; common foundation không
tự coi chúng là trusted identity. Việc xác thực và loại header giả mạo thuộc API
Gateway/service security layer.

## Event contract

Tạo event bằng factory:

```java
EventEnvelope event = EventEnvelope.create(
    "AppointmentCreated",
    1,
    appointmentId,
    0,
    "appointment-service",
    correlationId,
    objectMapper.valueToTree(payload)
);
```

Output:

```json
{
  "eventId": "842a1071-c719-4a16-bbe2-bb18961b81b7",
  "eventType": "AppointmentCreated",
  "eventVersion": 1,
  "aggregateId": "b2cd77c5-f7ed-49d4-a5a9-4b686c808492",
  "aggregateVersion": 0,
  "occurredAt": "2026-07-20T17:00:00Z",
  "producer": "appointment-service",
  "correlationId": "mobile-request-123",
  "payload": {}
}
```

Các ID, timestamp, producer, correlation ID và payload không được null; string không
được blank; `eventVersion >= 1` và `aggregateVersion >= 0`. Factory tự sinh
`eventId` và `occurredAt`.

Consumer vẫn phải triển khai idempotency theo `eventId`, retry/DLQ và transactional
outbox ở service sở hữu dữ liệu.

## JPA base entity

```java
@Entity
public class Patient extends BaseEntity {
}
```

`BaseEntity` cung cấp:

- UUID primary key.
- UTC `createdAt` và `updatedAt` bằng JPA lifecycle callbacks.
- `@Version` để chống lost update.
- Equality chỉ đúng khi hai entity cùng type có cùng non-null ID.
- Hash code ổn định trước và sau persist.

Schema của entity kế thừa phải có cột `version BIGINT NOT NULL`. Khi áp dụng common
foundation vào database đã tồn tại, hãy thêm cột bằng Flyway trước khi bật
`ddl-auto=validate`.

## Infrastructure cục bộ

```bash
cp .env.example .env
docker compose -f docker-compose.infra.yml up -d
docker compose -f docker-compose.infra.yml ps
```

Compose khởi chạy PostgreSQL 16 và RabbitMQ Management với healthcheck, persistent
volume và restart policy. Password là biến bắt buộc; không có credential production
hard-code. RabbitMQ dùng user `careflow` thay cho `guest` để service trong container
khác có thể kết nối.

Các service đọc `RABBITMQ_HOST`, `RABBITMQ_PORT`, `RABBITMQ_USER` và
`RABBITMQ_PASSWORD`; mặc định local tương thích với `.env.example`. Hãy thay toàn bộ
password mặc định ở môi trường dùng chung hoặc production.

```bash
docker compose -f docker-compose.infra.yml down
```

Thêm `-v` chỉ khi chủ động muốn xóa toàn bộ dữ liệu local.

## CI và build reproducibility

- Maven Wrapper khóa Maven `3.9.9`.
- GitHub Actions chạy `clean verify` bằng Temurin 21.
- Compose được validate trong CI.
- Maven compiler dùng `release=21`.
- JAR timestamp được cố định để hỗ trợ reproducible build.

## Giới hạn kiến trúc

Common foundation không chứa JWT/security policy, business DTO của từng bounded
context, database repository hoặc RabbitMQ publisher/consumer implementation. Các
thành phần này phải thuộc service sở hữu nghiệp vụ để tránh biến shared library
thành distributed monolith.

Nếu hệ thống bổ sung consumer không dùng Spring hoặc mở rộng mạnh sang WebFlux, nên
tách artifact thành `common-contracts`, `common-web-starter` và `common-jpa` trong
một major-version migration có kiểm soát.

## Tài liệu liên quan

- [Implementation plan](docs/implementation_plan.md)
- [Git workflow](docs/git-workflow.md)
