# CareFlow — Eureka & System Infrastructure

Nhánh `feature/dangkhoii/eureka-infrastructure` cung cấp service discovery và môi trường tích hợp cục bộ cho CareFlow. Phạm vi đã được kiểm tra gồm Eureka Server, API Gateway, Identity, Queue, Notification, PostgreSQL và RabbitMQ.

## Trạng thái

- Eureka chạy độc lập ở cổng `8761`, không tự đăng ký hoặc fetch registry.
- Dashboard, registry API và metrics được bảo vệ bằng HTTP Basic.
- Health/liveness/readiness được công khai để Docker và nền tảng điều phối probe.
- Gateway, Identity, Queue và Notification đăng ký Eureka bằng DNS nội bộ Docker và báo trạng thái `UP`.
- Các service chỉ khởi động sau khi dependency tương ứng healthy.
- Image ứng dụng dùng Java 21, multi-stage build, chạy non-root, read-only filesystem và có graceful shutdown.
- PostgreSQL, RabbitMQ, Eureka và Gateway mặc định chỉ bind vào `127.0.0.1`.
- Maven Wrapper, integration test và GitHub Actions đã được bổ sung.

Đây là topology single-node dành cho development/integration. Khi production cần high availability, phải triển khai tối thiểu hai Eureka peer ở các failure domain khác nhau, dùng TLS và secret manager; cấu hình Compose hiện tại không đáp ứng HA.

## Luồng input/output

```text
Eureka clients
  ├─ POST lease + heartbeat ──Basic Auth──▶ Eureka Server
  ├─ GET registry             ──Basic Auth──▶ Eureka Server
  └─ health/status            ◀────────────── Registry instance UP/DOWN

Mobile/Web ──▶ API Gateway ──service name──▶ Eureka registry ──▶ backend instance
```

Input cấu hình bắt buộc của Eureka là username, password, hostname và peer URL. Output của service là dashboard HTML, registry JSON/XML, lease lifecycle, health probe và Prometheus metrics. Eureka không cung cấp API nghiệp vụ nên không có Swagger/OpenAPI UI; registry REST contract bên dưới là giao diện kiểm thử đúng của service này.

### Contract HTTP

| Method và path | Auth | Input chính | Output thành công |
|---|---|---|---|
| `GET /actuator/health` | Public | Không | `200`, `{"status":"UP"}` |
| `GET /actuator/health/liveness` | Public | Không | `200`, probe status |
| `GET /actuator/health/readiness` | Public | Không | `200`, probe status |
| `GET /` | Basic | Eureka credentials | `200`, dashboard HTML |
| `GET /eureka/apps` | Basic | `Accept: application/json` hoặc XML | `200`, danh sách application/instance |
| `POST /eureka/apps/{app}` | Basic | Eureka `InstanceInfo` JSON/XML | `204`, tạo lease |
| `PUT /eureka/apps/{app}/{instanceId}` | Basic | Không | `200`, renew lease |
| `DELETE /eureka/apps/{app}/{instanceId}` | Basic | Không | `200`, hủy lease |
| `GET /actuator/prometheus` | Basic | Không | `200`, Prometheus text format |

Mọi endpoint ngoài health trả `401` nếu thiếu hoặc sai credentials. CSRF chỉ được bỏ qua cho `/eureka/**` để Eureka client có thể quản lý lease; các endpoint còn lại vẫn đi qua Spring Security.

## Yêu cầu môi trường

- Docker Desktop hoặc Docker Engine và Docker Compose v2.
- Khoảng 4 GB RAM trống để build/chạy full stack.
- Các cổng `5432`, `5672`, `15672`, `8080`, `8761` chưa bị chiếm.
- JDK 21+ và Maven 3.9+ nếu build trực tiếp. Maven cục bộ không bắt buộc vì repository có Wrapper 3.9.9.

## Cấu hình

Tạo file local từ template và thay toàn bộ giá trị `replace-with-...`:

```bash
cp .env.example .env
openssl rand -base64 48  # JWT secret
openssl rand -hex 32     # PostgreSQL/RabbitMQ/Eureka password
docker compose config --quiet
```

| Biến | Bắt buộc | Ý nghĩa |
|---|---:|---|
| `POSTGRES_PASSWORD` | Có | Password PostgreSQL |
| `RABBITMQ_PASSWORD` | Có | Password RabbitMQ |
| `EUREKA_USERNAME` | Có | Basic Auth username của dashboard, registry và client |
| `EUREKA_PASSWORD` | Có | Basic Auth password của Eureka |
| `JWT_SECRET` | Có | Secret dùng chung cho các service xác thực JWT, tối thiểu 32 byte |
| `POSTGRES_USER` | Không | Mặc định `careflow` |
| `RABBITMQ_USERNAME` | Không | Mặc định `careflow` |
| `BIND_ADDRESS` | Không | Mặc định an toàn `127.0.0.1`; không đặt `0.0.0.0` nếu chưa có firewall |
| `QUEUE_BUSINESS_ZONE` | Không | Mặc định `Asia/Ho_Chi_Minh` |
| `ALLOWED_ORIGINS` | Không | Danh sách origin cho frontend local |

Không commit `.env` hoặc secret thật. Nên dùng password dạng hex/URL-safe cho Eureka vì credentials được nhúng trong `EUREKA_URL` của client; đồng thời phải tránh ghi URL này ra log. Ở production, đặt Eureka sau TLS/reverse proxy và nạp secret từ secret manager.

### Cấu hình một Eureka client

Các module trong repository đã dùng chung contract sau:

```yaml
eureka:
  client:
    healthcheck:
      enabled: true
    service-url:
      defaultZone: "${EUREKA_URL:http://careflow:careflow@localhost:8761/eureka/}"
```

Khi chạy ngoài Docker, truyền URL bằng environment:

```bash
export EUREKA_URL='http://careflow-discovery:your-password@localhost:8761/eureka/'
```

Trong Compose, URL dùng hostname `eureka-server`; không dùng `localhost` vì mỗi container có network namespace riêng.

## Chạy hệ thống

### Full integration stack

```bash
docker compose up -d --build
docker compose ps
```

| Thành phần | Địa chỉ host | Ghi chú |
|---|---|---|
| API Gateway | `http://localhost:8080` | Public entry point |
| Eureka | `http://localhost:8761` | Dashboard cần Basic Auth |
| PostgreSQL | `localhost:5432` | User/password lấy từ `.env` |
| RabbitMQ AMQP | `localhost:5672` | User/password lấy từ `.env` |
| RabbitMQ Management | `http://localhost:15672` | Dashboard RabbitMQ |

Identity (`8081`), Queue (`8084`) và Notification (`8085`) chỉ mở trong Docker network; request bên ngoài đi qua Gateway. Script `scripts/init-databases.sql` tạo database-per-service ở lần đầu PostgreSQL volume được khởi tạo.

### Chỉ PostgreSQL và RabbitMQ

```bash
docker compose -f docker-compose.infra.yml up -d
docker compose -f docker-compose.infra.yml ps
```

### Chạy Eureka bằng Maven

```bash
EUREKA_USERNAME=careflow-discovery \
EUREKA_PASSWORD=local-development-password \
DEBUG=false \
./mvnw -pl careflow-eureka-server -am spring-boot:run
```

`DEBUG=false` tránh biến môi trường `DEBUG` của shell vô tình bật Spring Boot debug logging.

## Xác minh vận hành

```bash
set -a
. ./.env
set +a

curl http://localhost:8761/actuator/health

curl -u "$EUREKA_USERNAME:$EUREKA_PASSWORD" \
  -H 'Accept: application/json' \
  http://localhost:8761/eureka/apps

curl -u "$EUREKA_USERNAME:$EUREKA_PASSWORD" \
  http://localhost:8761/actuator/prometheus

curl http://localhost:8080/actuator/health
docker compose logs -f eureka-server
```

Registry ổn định khi có đúng bốn application `API-GATEWAY`, `IDENTITY-SERVICE`, `QUEUE-SERVICE`, `NOTIFICATION-SERVICE`, mỗi instance ở trạng thái `UP`. Đăng ký có tính eventual consistency và lease được heartbeat định kỳ; không dùng registry như nguồn dữ liệu giao dịch.

Eureka self-preservation mặc định bật để tránh xóa hàng loạt lease khi mạng chập chờn. Có thể đặt `EUREKA_SELF_PRESERVATION=false` chỉ trong môi trường test chuyên biệt; không nên tắt ở production.

## Build và kiểm thử

```bash
DEBUG=false ./mvnw --batch-mode --no-transfer-progress clean verify

docker compose config --quiet
docker compose -f docker-compose.infra.yml config --quiet

docker build \
  --build-arg MODULE=careflow-eureka-server \
  --tag careflow-eureka-server:local .
```

`EurekaServerIntegrationTest` khởi động server trên random port và kiểm tra:

- health public nhưng dashboard/registry yêu cầu authentication;
- đăng ký instance, đọc registry, heartbeat và hủy lease;
- mã HTTP của từng bước trong lease lifecycle.

Workflow `.github/workflows/eureka-infrastructure-ci.yml` chạy full Maven reactor, validate cả hai Compose contract và build image Eureka với Java 21.

## Dừng và xử lý sự cố

```bash
# Dừng nhưng giữ database/message data
docker compose down

# Xem trạng thái và log
docker compose ps -a
docker compose logs --tail=200 eureka-server api-gateway
```

Các lỗi thường gặp:

- `401 Unauthorized`: credentials trong `EUREKA_URL` không khớp Eureka Server.
- Client gọi `localhost:8761` trong container: chưa truyền đúng `EUREKA_URL` dùng hostname `eureka-server`.
- Gateway chưa start: một dependency vẫn chưa healthy; xem `docker compose ps -a` và log service đó.
- Database không được tạo lại sau khi sửa init script: init script chỉ chạy khi volume PostgreSQL còn trống. Chỉ xóa volume khi chấp nhận mất toàn bộ dữ liệu local.
- Integration test báo không bind được random port: môi trường chạy test đang cấm mở localhost socket; chạy ở môi trường CI/container có quyền bind loopback.

## Giới hạn phạm vi

- Compose hiện chỉ triển khai một Eureka node, không có HA hoặc TLS termination.
- Identity, Queue và Notification trên nhánh hạ tầng là integration skeleton; API nghiệp vụ đầy đủ phải được tích hợp từ nhánh service tương ứng.
- PostgreSQL và RabbitMQ trong Compose phục vụ local development, không phải cấu hình production.
- Eureka cung cấp service discovery, không thay thế API Gateway, load balancer biên, distributed tracing hay secret management.

Tài liệu nghiệp vụ và phân công tổng thể nằm trong [docs/implementation_plan.md](docs/implementation_plan.md).
