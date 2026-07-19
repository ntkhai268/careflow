# CareFlow 🏥

> Hệ thống phần mềm trợ giúp khám chữa bệnh tại bệnh viện công theo kiến trúc Microservices

## Branch hiện tại: Notification WebSocket Service

Branch: `feature/dangkhoii/notification-websocket` · Port: `8085`

### Chức năng

- Khai báo durable RabbitMQ queue và DLQ cho các sự kiện `queue.#`.
- Consume sự kiện cấp số, check-in, gần lượt, gọi lượt, missed và completed.
- Cung cấp STOMP WebSocket endpoint `/ws`.
- Xác thực JWT trong STOMP `CONNECT` và dùng `sub` làm WebSocket principal.
- Gửi thông báo riêng tới `/user/queue/notifications`.
- Gửi cập nhật dashboard tới `/topic/queues/departments/{departmentId}`.
- Chỉ cho `DOCTOR`/`ADMIN` subscribe topic khoa; bệnh nhân chỉ nhận user destination.

### Contract realtime

| Thành phần | Giá trị |
|---|---|
| Handshake | `/ws` |
| Patient destination | `/user/queue/notifications` |
| Doctor/Admin topic | `/topic/queues/departments/{departmentId}` |
| RabbitMQ input | `queue.exchange` với routing key `queue.#` |
| REST source of truth | Queue Management API |

### Chạy kiểm thử

```bash
export JWT_SECRET="replace-with-at-least-32-random-bytes"
mvn -pl careflow-notification-service -am test
```

WebSocket là kênh best-effort; sau reconnect client phải gọi lại Queue REST API để đồng bộ.

## Tổng quan

CareFlow là hệ thống quản lý quy trình khám bệnh tại bệnh viện công, xây dựng theo kiến trúc Microservices. Hệ thống hỗ trợ:

- **Bệnh nhân** (Mobile App): Đăng ký khám, theo dõi hàng đợi, nhận thông báo, xem toa thuốc
- **Bác sỹ** (Web App): Tra cứu hồ sơ, chẩn bệnh, kê toa, ChatBot AI gợi ý phác đồ
- **Hệ thống** (Core): Quản lý hàng đợi đa độ ưu tiên, xác thực, thông báo real-time

## Kiến trúc

```
Mobile App (Flutter)  ──┐
                        ├──▶ API Gateway ──▶ Microservices ──▶ PostgreSQL
Web App (React)       ──┘         │                │
                            Eureka Server     RabbitMQ
```

## Tech Stack

| Layer | Công nghệ |
|-------|-----------|
| Backend | Spring Boot 3, Java 21 |
| Mobile | Flutter |
| Web | React + Vite |
| Database | PostgreSQL |
| Message Broker | RabbitMQ |
| Service Discovery | Spring Cloud Eureka |
| API Gateway | Spring Cloud Gateway |
| Container | Docker + Docker Compose |

## Services

| Service | Trạng thái |
|---------|------------|
| API Gateway | 🔧 Planned |
| Identity & Auth | 🔧 Planned |
| Patient Service | 🔧 Planned |
| Appointment Service | 🔧 Planned |
| Queue Management ⭐ | 🔧 Planned |
| Notification Service | ✅ Implemented on this branch |
| Doctor Consultation | 🔧 Planned |
| Prescription Service | 🔧 Planned |
| EMR Service | 🔧 Planned |
| Laboratory Order | 🔧 Mock |
| Analytics Service | 🔧 Mock |
| AI Clinical Assistant | 🔧 Mock |

## Tài liệu

- [Implementation Plan](docs/implementation_plan.md)

## Team

Đề tài thực tập tốt nghiệp — PTIT
