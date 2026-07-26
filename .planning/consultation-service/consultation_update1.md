# Consultation Service — Update Phase 1: Appointment Integration

## 1. Overview & Objectives
Tài liệu này tổng hợp thiết kế và triển khai đợt 1 nâng cấp **CareFlow Consultation Service** để tích hợp trực tiếp với **Appointment Service** (nhánh `feature/khai/appointment-service`).

Trước đây, `ConsultationService` hoạt động độc lập và chỉ nhận `appointmentId` tùy chọn mà không kiểm tra tính hợp lệ. Sau đợt cập nhật này, hai dịch vụ hoạt động theo mô hình tích hợp hybrid (Feign HTTP + RabbitMQ Events).

---

## 2. Integrated Architecture & Workflow

```
[Patient / Receptionist] 
       │ 
       ▼
   Appointment (CONFIRMED / CHECKED_IN)
       │
       ├──────────────────────────────────────────────────────┐
       │ (Doctor Bắt Đầu Khám)                                 │
       ▼                                                      ▼
[Consultation Service] ────(Feign Client GET/PUT)───► [Appointment Service]
  - Validate appointment status                        - Update status: IN_PROGRESS
  - Fallback / Rollback if invalid/failed
  - Create Consultation (IN_PROGRESS)
       │
       ▼ (Doctor Hoàn Tất Khám)
[Consultation Service] ───(RabbitMQ Event)───────────► [Appointment Service]
  - Consultation → COMPLETED                            - Event Consumer
  - Publish `consultation.completed`                    - Update status: COMPLETED
```

### Chi tiết luồng xử lý:
1. **Khởi tạo khám bệnh (`POST /api/consultations`)**:
   - Client gửi `CreateConsultationRequest` bắt buộc có `appointmentId` (`@NotNull`).
   - `ConsultationService` dùng Feign Client (`AppointmentClient`) gọi `GET /api/appointments/{id}` để kiểm tra lịch khám.
   - Yêu cầu trạng thái lịch khám phải là `CONFIRMED` hoặc `CHECKED_IN` (hoặc `IN_PROGRESS`).
   - Gọi tiếp Feign Client `PUT /api/appointments/{id}/status` để cập nhật trạng thái lịch khám thành `IN_PROGRESS`.
   - **Fallback / Rollback**: Nếu kết nối thất bại hoặc trạng thái không hợp lệ, hệ thống ném `BusinessException`, hủy bỏ việc tạo `Consultation` trong DB (transaction rollback).

2. **Hoàn thành khám bệnh (`PUT /api/consultations/{id}/complete`)**:
   - `Consultation` đổi trạng thái thành `COMPLETED` và lưu `completedAt`.
   - Publish event `consultation.completed` qua RabbitMQ topic exchange `careflow.consultation`.
   - `ConsultationCompletedConsumer` ở **Appointment Service** nhận event và tự động đổi trạng thái `Appointment` tương ứng sang `COMPLETED`.

---

## 3. Class & Component Map

### Consultation Service (`careflow-consultation-service`)
* [`ConsultationApplication.java`](file:///home/levi/Desktop/careflow/careflow-consultation-service/src/main/java/com/careflow/consultation/ConsultationApplication.java): Thêm `@EnableFeignClients`.
* [`AppointmentClient.java`](file:///home/levi/Desktop/careflow/careflow-consultation-service/src/main/java/com/careflow/consultation/client/AppointmentClient.java): Feign Client kết nối `appointment-service`.
* [`CreateConsultationRequest.java`](file:///home/levi/Desktop/careflow/careflow-consultation-service/src/main/java/com/careflow/consultation/dto/request/CreateConsultationRequest.java): Thêm validation `@NotNull` cho `appointmentId`.
* [`ConsultationService.java`](file:///home/levi/Desktop/careflow/careflow-consultation-service/src/main/java/com/careflow/consultation/service/ConsultationService.java): Tích hợp Feign validation, fallback rollback, và method `getConsultationsByAppointment()`.
* [`ConsultationController.java`](file:///home/levi/Desktop/careflow/careflow-consultation-service/src/main/java/com/careflow/consultation/controller/ConsultationController.java): Bổ sung endpoint `GET /api/consultations/appointment/{appointmentId}`.

### Appointment Service (`careflow-appointment-service`)
* [`RabbitMQConfig.java`](file:///home/levi/Desktop/careflow/careflow-appointment-service/src/main/java/com/careflow/appointment/config/RabbitMQConfig.java): Thêm `queue.appointment.consultation.completed` và binding với exchange `careflow.consultation`.
* [`ConsultationCompletedConsumer.java`](file:///home/levi/Desktop/careflow/careflow-appointment-service/src/main/java/com/careflow/appointment/listener/ConsultationCompletedConsumer.java): Listener tiêu thụ event hoàn tất khám bệnh để cập nhật trạng thái appointment sang `COMPLETED`.

---

## 4. API Endpoints Update

| Method | Endpoint | Description | Status Code |
|--------|----------|-------------|-------------|
| `POST` | `/api/consultations` | Tạo phiên khám (Validate & set appointment → `IN_PROGRESS`) | `201 Created` / `400 Bad Request` |
| `PUT` | `/api/consultations/{id}/complete` | Hoàn thành phiên khám (Publish event → appointment `COMPLETED`) | `200 OK` |
| `GET` | `/api/consultations/appointment/{appointmentId}` | Tra cứu danh sách phiên khám theo `appointmentId` | `200 OK` |

---

## 5. Verification & Testing
* **Compilation**: `mvn test-compile` trên cả 2 service đạt **BUILD SUCCESS**.
* **Dependencies**: Đã bổ sung `spring-cloud-starter-openfeign` vào `pom.xml` của Consultation Service.
