# CareFlow 🏥 — Phân Hệ Hệ Thống (Core/Infrastructure)

> Phần việc đảm nhận bởi **Người A (dangkhoii)** trong dự án CareFlow - Hệ thống hỗ trợ khám chữa bệnh tại bệnh viện công theo kiến trúc Microservices.

## 📌 Tổng quan phân hệ
Phân hệ Hệ thống đóng vai trò xương sống của dự án CareFlow, chịu trách nhiệm quản lý luồng điều phối chính (routing, authentication), thuật toán hàng đợi khám bệnh và cơ chế giao tiếp real-time giữa các dịch vụ.

Các thành phần chính do **dangkhoii** thiết kế & triển khai:
- **API Gateway**: Điểm vào duy nhất của hệ thống, xử lý định tuyến và xác thực JWT.
- **Identity & Auth Service**: Đăng ký/đăng nhập, quản lý tài khoản và phân quyền người dùng (PATIENT, DOCTOR, ADMIN).
- **Queue Management Service (Trọng tâm)**: Quản lý số thứ tự, check-in QR, và thuật toán gọi khám xen kẽ N:M động.
- **Notification Service**: Đẩy thông báo real-time đến bệnh nhân và bác sỹ qua WebSocket.
- **Hạ tầng chung**: Service Discovery (Eureka Server), Message Broker (RabbitMQ), Docker Compose.

---

## 🛠️ Kiến trúc Hệ thống & Luồng tích hợp

```
                       [CLIENT LAYER]
  ┌──────────────┐    ┌──────────────┐    ┌──────────────┐
  │ Mobile App   │    │ Web App      │    │ Admin Panel  │
  │ (Bệnh nhân)  │    │  (Bác sỹ)    │    │   (Admin)    │
  └──────┬───────┘    └──────┬───────┘    └──────┬───────┘
         │                   │                   │
         ▼                   ▼                   ▼
┌─────────────────────────────────────────────────────────┐
│            API GATEWAY (Spring Cloud Gateway)           │
│         JWT Filter · Rate Limiting · Route Routing      │
└────────────────────────────┬────────────────────────────┘
                             │
            ┌────────────────┼────────────────┐
            ▼                ▼                ▼
┌─────────────────┐  ┌──────────────┐  ┌──────────────────┐
│  Eureka Server  │  │  RabbitMQ    │  │  Shared Library  │
│  (Discovery)    │  │(Event Broker)│  │ (common utility) │
└─────────────────┘  └──────────────┘  └──────────────────┘
                             │
     ┌────────┬────────┬─────┴─┬────────┐
     ▼        ▼        ▼       ▼        ▼
┌────────┐┌────────┐┌────────┐┌────────┐┌────────┐
│Identity││Patient ││Appoint-││Queue   ││Notifi- │
│Service ││Service ││ment Svc││Mgmt Svc││cation  │ (Các service
│  (A)   ││ (B)    ││  (B)   ││  (A)   ││  (A)   │  khác...)
└───┬────┘└───┬────┘└───┬────┘└───┬────┘└───┬────┘
    ▼         ▼         ▼         ▼         ▼
[Auth DB] [Pat DB]  [Appt DB] [Queue DB] WebSocket
```

---

## 🚀 Thuật toán nổi bật (Queue Management)

### 1. Phân cấp độ ưu tiên (4 mức)
- **P0 - `EMERGENCY`**: Ca cấp cứu, luôn được gọi ngay lập tức mà không làm ảnh hưởng đến chu kỳ N:M.
- **P1 - `PRIORITY`**: Người già, trẻ em, phụ nữ mang thai (Được phục vụ theo chu kỳ N:M).
- **P2 - `APPOINTMENT`**: Đã đặt lịch trước (Xếp vào nhóm Normal).
- **P3 - `WALK_IN`**: Đến trực tiếp (Xếp vào nhóm Normal).

### 2. Thuật toán gọi khám xen kẽ động N:M
- Điều phối thông minh giữa nhóm **Ưu tiên (P1)** và nhóm **Thường (P2, P3)** theo tỉ lệ cấu hình động (Ví dụ: 2 bệnh nhân ưu tiên : 1 bệnh nhân thường).
- Tự động fallback/chuyển phase khi một trong các hàng chờ trống để tránh tắc nghẽn queue.

---

## 🗄️ Cấu trúc thư mục phân hệ (Người A)
```text
medici/ (careflow/)
├── docker-compose.infra.yml    # Docker Compose chạy infra (PostgreSQL, RabbitMQ, Eureka)
├── docker-compose.yml          # Docker Compose khởi chạy toàn bộ hệ thống
├── shared/
│   └── common-lib/             # Thư viện dùng chung (DTOs, JWT Utils, Exceptions)
├── services/
│   ├── eureka-server/          # Service Discovery (Eureka Server)
│   ├── api-gateway/            # API Gateway (Spring Cloud Gateway)
│   ├── identity-service/       # Xác thực & Quản lý tài khoản (Port: 8081)
│   ├── queue-service/          # Quản lý hàng đợi & Số thứ tự (Port: 8084) ⭐
│   └── notification-service/   # Đẩy thông báo real-time qua WebSocket (Port: 8085)
```

---

## 📄 Tài liệu Phân tích & Thiết kế (OOAD)

- 📘 [Tài liệu OOAD chi tiết của Người A](ooad-person-a.md)
- 🗃️ [Database Model DBML của Người A](docs/database/careflow-person-a.dbml)
- 📋 [Kế hoạch triển khai tổng thể](docs/implementation_plan.md)

---

## 🗄️ Thiết kế Cơ sở dữ liệu (Database Schema)

Phân hệ Hệ thống sử dụng 2 Database độc lập trên PostgreSQL (tuân thủ nguyên tắc *Database-per-Service*):

```mermaid
erDiagram
    users {
        uuid id PK
        varchar username
        varchar email
        varchar password_hash
        varchar role
        varchar status
        int failed_login_attempts
        timestamptz locked_until
        timestamptz last_login_at
        bigint version
        timestamptz created_at
        timestamptz updated_at
    }

    queue_configs {
        uuid id PK
        uuid department_id UK
        varchar department_name_snapshot
        varchar queue_prefix
        varchar room_code
        smallint priority_ratio_n
        smallint normal_ratio_m
        smallint avg_consultation_minutes
        smallint near_turn_threshold
        varchar missed_policy
        date scheduler_date
        varchar cycle_phase
        smallint served_in_phase
        varchar normal_cursor
        boolean is_active
        bigint version
        timestamptz created_at
        timestamptz updated_at
    }

    queue_number_sequences {
        uuid id PK
        uuid queue_config_id FK
        date queue_date
        int last_number
        bigint version
        timestamptz updated_at
    }

    queue_entries {
        uuid id PK
        uuid queue_config_id FK
        uuid department_id
        uuid appointment_id
        uuid patient_id
        uuid user_id FK
        date queue_date
        int sequence_number
        varchar queue_number
        varchar priority_level
        varchar status
        timestamptz checked_in_at
        timestamptz eligible_since_at
        timestamptz called_at
        timestamptz started_at
        timestamptz completed_at
        timestamptz missed_at
        timestamptz cancelled_at
        smallint missed_count
        int estimated_wait_minutes
        bigint version
        timestamptz created_at
        timestamptz updated_at
    }

    outbox_events {
        uuid event_id PK
        varchar aggregate_type
        uuid aggregate_id
        varchar event_type
        int event_version
        bigint aggregate_version
        varchar exchange_name
        varchar routing_key
        jsonb payload
        varchar status
        int attempts
        timestamptz occurred_at
        timestamptz next_attempt_at
        timestamptz published_at
        text last_error
        timestamptz created_at
    }

    processed_events {
        uuid event_id PK
        varchar consumer_name PK
        varchar event_type
        timestamptz processed_at
    }

    users ||..o{ queue_entries : "logical_reference"
    queue_configs ||--o{ queue_entries : "owns"
    queue_configs ||--o{ queue_number_sequences : "owns"
```

### 1. Database: `careflow_identity` (Identity Service)
Quản lý tài khoản, thông tin đăng nhập và phân quyền.
- **`identity.users`**: Lưu trữ thông tin tài khoản, email, mật khẩu đã hash, vai trò (`PATIENT`, `DOCTOR`, `ADMIN`), trạng thái tài khoản và thông tin phục vụ cơ chế khóa tài khoản khi đăng nhập sai.

### 2. Database: `careflow_queue` (Queue Service)
Quản lý hàng đợi, số thứ tự khám và đảm bảo truyền nhận message tin cậy.
- **`queue.queue_configs`**: Lưu cấu hình hàng đợi của từng chuyên khoa (chỉ số N:M, thời gian khám trung bình, phòng khám, chính sách lỡ lượt) và trạng thái bộ lập lịch tại thời điểm chạy.
- **`queue.queue_number_sequences`**: Sinh số thứ tự tự động tăng dần theo ngày nghiệp vụ của từng khoa.
- **`queue.queue_entries`**: Lưu trạng thái chi tiết của từng lượt khám bệnh (ngày, số thứ tự, mức độ ưu tiên, trạng thái khám, các mốc thời gian check-in, gọi khám, hoàn thành, hủy).
- **`queue.outbox_events`**: Lưu trữ các sự kiện nghiệp vụ phục vụ mô hình *Transactional Outbox* để gửi tin nhắn tin cậy sang RabbitMQ.
- **`queue.processed_events`**: Lưu lịch sử các sự kiện đã xử lý từ các service khác nhằm đảm bảo tính *Idempotency* (tránh xử lý trùng lặp).

