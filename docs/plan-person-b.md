# Kế hoạch chi tiết — Người B (Phân hệ Bệnh nhân)

## Tổng quan

| Mục | Chi tiết |
|-----|----------|
| **Phân hệ** | 🏥 Bệnh nhân |
| **Backend** | Patient Service (:8082), Appointment Service (:8083), EMR Service (:8088) |
| **Frontend** | Mobile App (Flutter) |
| **Phụ** | Mock Analytics Service (nếu dư thời gian) |

---

## Bạn phụ thuộc vào ai?

```
Người A cung cấp cho bạn:
├── Identity Service API    → để gọi Login/Register
├── Queue Service API       → để lấy số thứ tự, vị trí hàng đợi
├── Notification Service    → WebSocket để nhận thông báo đến lượt
├── QR Check-in API         → để quét QR xác nhận
└── Shared Library          → ApiResponse, BaseEntity, Constants

Người C gửi event cho bạn:
└── PrescriptionCreated     → qua RabbitMQ → hiển thị toa thuốc trên Mobile
```

> ⚠️ **Quan trọng**: Tuần 1-2, khi API của A chưa xong, bạn **mock dữ liệu giả** trên Mobile để không bị block. Khi A xong thì thay bằng API thật.

---

## Màn hình Mobile cần làm

| # | Màn hình | Độ ưu tiên | Tuần |
|---|----------|-----------|------|
| 1 | Splash + Onboarding | 🔴 P0 | 1 |
| 2 | Đăng nhập / Đăng ký | 🔴 P0 | 1 |
| 3 | Trang chủ (Home) | 🔴 P0 | 1 |
| 4 | Hồ sơ bệnh nhân (Profile) | 🟡 P1 | 1-2 |
| 5 | Đăng ký khám bệnh (chọn khoa, ngày, giờ) | 🔴 P0 | 2 |
| 6 | Xác nhận đặt lịch + nhận số thứ tự | 🔴 P0 | 2 |
| 7 | Theo dõi hàng đợi (real-time) | 🔴 P0 | 2-3 |
| 8 | Quét QR Code check-in | 🔴 P0 | 3 |
| 9 | Thông báo (danh sách + real-time popup) | 🔴 P0 | 3 |
| 10 | Xem toa thuốc + lịch tái khám | 🟡 P1 | 3 |
| 11 | Hồ sơ bệnh án (lịch sử + upload) | 🟡 P1 | 3-4 |
| 12 | Lịch sử khám bệnh | 🟢 P2 | 4 |

---

## Kế hoạch 5 tuần — Chi tiết theo ngày

### 📅 Tuần 1: Foundation

#### Ngày 1 (Thứ 2)
- [ ] Clone repo, chạy `docker-compose -f docker-compose.infra.yml up -d`
- [ ] Setup Flutter project trong `frontend/patient-mobile/`
- [ ] Chọn state management: **Provider** (đơn giản) hoặc **Riverpod** (tốt hơn)
- [ ] Cài dependencies cơ bản: `http`, `shared_preferences`, `flutter_secure_storage`
- [ ] Tạo cấu trúc thư mục Flutter:
```
lib/
├── main.dart
├── config/
│   ├── api_config.dart          # Base URL, endpoints
│   └── theme.dart               # Colors, fonts, text styles
├── models/                      # Data models
├── services/                    # API services
├── providers/                   # State management
├── screens/                     # Màn hình
│   ├── auth/
│   ├── home/
│   ├── appointment/
│   ├── queue/
│   ├── profile/
│   └── notification/
├── widgets/                     # Reusable widgets
└── utils/                       # Helpers, formatters
```

#### Ngày 2 (Thứ 3)
- [ ] Thiết kế **Design System**: color palette, typography, button styles, card styles
- [ ] Build **Splash Screen** + **Onboarding** (2-3 slide giới thiệu app)
- [ ] Build **Login Screen** (email + password)
- [ ] Build **Register Screen** (tên, email, SĐT, password)
- [ ] Tạo `AuthService` để gọi API Login/Register
  - Nếu API Auth của A chưa xong → mock response giả

#### Ngày 3 (Thứ 4)
- [ ] Tích hợp Login/Register với API Auth của A (nếu đã xong)
- [ ] Lưu JWT token vào `flutter_secure_storage`
- [ ] Tạo `AuthProvider` quản lý trạng thái đăng nhập
- [ ] Build **Home Screen** (layout chính: greeting, quick actions, upcoming appointments)
- [ ] Tạo **Bottom Navigation Bar** (Trang chủ, Đặt khám, Thông báo, Hồ sơ)

#### Ngày 4 (Thứ 5)
- [ ] Setup **Patient Service** backend (Spring Boot)
- [ ] Tạo `Patient` entity + DB schema
- [ ] API CRUD: `POST /api/patients`, `GET /api/patients/{id}`, `PUT /api/patients/{id}`
- [ ] Test API bằng Postman

#### Ngày 5 (Thứ 6)
- [ ] Build **Profile Screen** trên Mobile (hiển thị + chỉnh sửa thông tin bệnh nhân)
- [ ] Kết nối Profile với Patient Service API
- [ ] Review code tuần 1, fix bugs
- [ ] **Sync với nhóm**: demo Login + Home + Profile

#### ✅ Checklist cuối tuần 1
- [ ] Flutter app chạy được, có Splash → Login → Home → Profile
- [ ] Patient Service API hoạt động (CRUD)
- [ ] Login tích hợp với Auth API của A (hoặc mock)
- [ ] Design system nhất quán, UI đẹp

---

### 📅 Tuần 2: Đặt lịch khám + Hàng đợi

#### Ngày 6 (Thứ 2)
- [ ] Setup **Appointment Service** backend
- [ ] Tạo `Appointment` entity:
```java
@Entity
public class Appointment extends BaseEntity {
    private UUID patientId;
    private String department;       // Chuyên khoa
    private UUID doctorId;           // nullable
    private LocalDate appointmentDate;
    private String timeSlot;         // "08:00-08:30"
    
    @Enumerated(EnumType.STRING)
    private AppointmentStatus status; // PENDING, CONFIRMED, CHECKED_IN, 
                                      // IN_PROGRESS, COMPLETED, CANCELLED
    private String notes;
}
```
- [ ] API: `POST /api/appointments`, `GET /api/appointments/patient/{patientId}`

#### Ngày 7 (Thứ 3)
- [ ] Tích hợp **RabbitMQ**: publish event `AppointmentCreated` khi tạo appointment
```java
// Appointment Service → publish event
rabbitTemplate.convertAndSend(
    AppConstants.EXCHANGE_APPOINTMENT,
    AppConstants.RK_APPOINTMENT_CREATED,
    appointmentCreatedEvent
);
```
- [ ] Test: tạo appointment → A nhận được event → tạo queue number
- [ ] API: `GET /api/appointments/{id}`, `PUT /api/appointments/{id}/cancel`

#### Ngày 8 (Thứ 4)
- [ ] Mobile: Build **Đăng ký khám bệnh** screen
  - Bước 1: Chọn chuyên khoa (danh sách card)
  - Bước 2: Chọn ngày (calendar picker)
  - Bước 3: Chọn ca khám (time slots)
  - Bước 4: Xác nhận thông tin → Submit
- [ ] Kết nối với Appointment API

#### Ngày 9 (Thứ 5)
- [ ] Mobile: Build **Xác nhận đặt lịch** screen (hiển thị số thứ tự từ Queue API của A)
- [ ] Mobile: Build **Theo dõi hàng đợi** screen
  - Hiển thị: số thứ tự, vị trí hiện tại, thời gian chờ ước tính
  - Gọi Queue API của A: `GET /api/queues/patient/{patientId}/status`
  - (Tuần sau mới làm real-time WebSocket, giờ dùng polling tạm)

#### Ngày 10 (Thứ 6)
- [ ] Polish UI đặt lịch + hàng đợi
- [ ] Test luồng: đặt lịch → nhận số → xem hàng đợi
- [ ] **Sync với nhóm**: demo luồng đăng ký khám

#### ✅ Checklist cuối tuần 2
- [ ] Appointment Service hoạt động, publish event qua RabbitMQ
- [ ] Mobile: đặt lịch khám → nhận số thứ tự → xem hàng đợi
- [ ] Event `AppointmentCreated` → A nhận và tạo queue entry

---

### 📅 Tuần 3: Check-in + Thông báo real-time + Toa thuốc

#### Ngày 11 (Thứ 2)
- [ ] Mobile: Tích hợp **WebSocket** để nhận thông báo real-time từ Notification Service của A
```dart
// Flutter WebSocket (dùng package stomp_dart_client)
final client = StompClient(
  config: StompConfig.sockJS(
    url: 'http://localhost:8080/ws',
    onConnect: (frame) {
      client.subscribe(
        destination: '/topic/queue/{patientId}',
        callback: (frame) {
          // Hiển thị thông báo: "Sắp đến lượt", "Đến lượt bạn"
        },
      );
    },
  ),
);
```
- [ ] Cập nhật **Theo dõi hàng đợi** từ polling → real-time WebSocket

#### Ngày 12 (Thứ 3)
- [ ] Mobile: Build **Quét QR Code** screen
  - Dùng package: `mobile_scanner` hoặc `qr_code_scanner`
  - Quét QR → gọi API Check-in của A: `POST /api/queues/check-in`
  - Hiển thị kết quả check-in (thành công / thất bại)
- [ ] Mobile: Build **Notification Screen** (danh sách thông báo)

#### Ngày 13 (Thứ 4)
- [ ] Mobile: Build **Xem toa thuốc** screen
  - Nhận event `PrescriptionCreated` từ C qua RabbitMQ/WebSocket
  - Hiển thị: tên thuốc, liều lượng, tần suất, thời gian dùng
- [ ] Mobile: Build **Lịch tái khám** (hiển thị follow-up date từ prescription)
- [ ] Lắng nghe event `PrescriptionCreated` từ RabbitMQ:
```java
// Có thể tạo 1 endpoint đơn giản trên Patient Service
// hoặc nhận qua WebSocket từ Notification Service
```

#### Ngày 14 (Thứ 5)
- [ ] Setup **EMR Service** backend
- [ ] Tạo `MedicalRecord` entity:
```java
@Entity
public class MedicalRecord extends BaseEntity {
    private UUID patientId;
    private String recordType;      // "EXAMINATION", "LAB_RESULT", "IMAGING"
    private String title;
    private String description;
    private String fileUrl;         // URL file upload
    private LocalDate recordDate;
}
```
- [ ] API: `POST /api/emr/records`, `GET /api/emr/records/patient/{patientId}`
- [ ] API upload file: `POST /api/emr/records/{id}/upload` (dùng MultipartFile)

#### Ngày 15 (Thứ 6)
- [ ] Mobile: Build **Hồ sơ bệnh án** screen (danh sách + upload)
- [ ] Test toàn bộ luồng end-to-end
- [ ] **Sync với nhóm**: demo luồng check-in → thông báo → khám → toa thuốc

#### ✅ Checklist cuối tuần 3
- [ ] QR check-in hoạt động
- [ ] Nhận thông báo real-time qua WebSocket
- [ ] Xem toa thuốc + lịch tái khám
- [ ] EMR Service hoạt động (CRUD + upload file)

---

### 📅 Tuần 4: Polish + Edge Cases + Test Data

#### Ngày 16 (Thứ 2)
- [ ] Xử lý edge cases Mobile:
  - Mất kết nối WebSocket → auto reconnect
  - API timeout → hiển thị thông báo lỗi
  - Token hết hạn → tự động refresh hoặc redirect login

#### Ngày 17 (Thứ 3)
- [ ] Polish UI/UX:
  - Loading skeletons (shimmer effect)
  - Pull-to-refresh
  - Empty states (chưa có lịch hẹn, chưa có toa thuốc)
  - Animations cho chuyển trang

#### Ngày 18 (Thứ 4)
- [ ] Mobile: Build **Lịch sử khám bệnh** screen (nếu còn thời gian)
- [ ] Mock **Analytics Service** (nếu còn thời gian):
```java
@GetMapping("/dashboard")
public DashboardData getDashboard() {
    return DashboardData.builder()
        .totalPatientsToday(45)
        .avgWaitTime(23)
        .build();
}
```

#### Ngày 19 (Thứ 5)
- [ ] Tạo **test data** thực tế cho demo:
  - 5-10 bệnh nhân mẫu (tên, SĐT, CMND thực tế)
  - 10-15 lịch hẹn khám ở nhiều chuyên khoa
  - 5-7 hồ sơ bệnh án mẫu
  - 3-5 toa thuốc mẫu
- [ ] Viết script seed data (SQL hoặc API calls)

#### Ngày 20 (Thứ 6)
- [ ] Fix bugs tổng hợp
- [ ] Test toàn bộ luồng end-to-end trên máy khác
- [ ] **Sync với nhóm**: demo tổng cuối tuần 4

#### ✅ Checklist cuối tuần 4
- [ ] Mobile app mượt, đẹp, không crash
- [ ] Edge cases được xử lý
- [ ] Test data sẵn sàng cho demo

---

### 📅 Tuần 5: Demo + Báo cáo

#### Ngày 21-22
- [ ] Viết phần báo cáo **Phân hệ Bệnh nhân**:
  - Usecase diagram
  - DB schema (Patient, Appointment, EMR)
  - API endpoints list
  - Screenshots tất cả màn hình Mobile
- [ ] Quay **video demo** phần Mobile

#### Ngày 23-24
- [ ] Rehearse demo: chạy thử kịch bản 3 luồng chính
- [ ] Fix bugs phát hiện khi rehearse
- [ ] Chuẩn bị máy demo (cài emulator hoặc dùng điện thoại thật)

#### Ngày 25
- [ ] **DEMO** 🎉

---

## API Endpoints tổng hợp

### Patient Service (:8082)

| Method | Endpoint | Mô tả |
|--------|----------|-------|
| POST | `/api/patients` | Tạo hồ sơ bệnh nhân |
| GET | `/api/patients/{id}` | Xem hồ sơ |
| PUT | `/api/patients/{id}` | Cập nhật hồ sơ |
| GET | `/api/patients/user/{userId}` | Tìm patient theo user ID |

### Appointment Service (:8083)

| Method | Endpoint | Mô tả |
|--------|----------|-------|
| POST | `/api/appointments` | Đặt lịch khám |
| GET | `/api/appointments/{id}` | Xem chi tiết lịch hẹn |
| GET | `/api/appointments/patient/{patientId}` | Danh sách lịch hẹn của bệnh nhân |
| PUT | `/api/appointments/{id}/cancel` | Hủy lịch hẹn |
| GET | `/api/appointments/departments` | Danh sách chuyên khoa |

### EMR Service (:8088)

| Method | Endpoint | Mô tả |
|--------|----------|-------|
| POST | `/api/emr/records` | Tạo hồ sơ bệnh án |
| GET | `/api/emr/records/patient/{patientId}` | Danh sách hồ sơ bệnh án |
| GET | `/api/emr/records/{id}` | Xem chi tiết |
| POST | `/api/emr/records/{id}/upload` | Upload file đính kèm |

### API của người khác mà bạn cần gọi

| Service (của ai) | Endpoint | Bạn gọi khi nào |
|---|---|---|
| Identity (A) | `POST /api/auth/login` | Đăng nhập |
| Identity (A) | `POST /api/auth/register` | Đăng ký |
| Queue (A) | `GET /api/queues/patient/{patientId}/status` | Xem vị trí hàng đợi |
| Queue (A) | `POST /api/queues/check-in` | Quét QR check-in |
| Notification (A) | `WS /ws/topic/queue/{patientId}` | Nhận thông báo real-time |
| Prescription (C) | `GET /api/prescriptions/patient/{patientId}` | Xem toa thuốc |

---

## Database Schema

### Patient Service DB (`careflow_patient`)

```sql
CREATE TABLE patients (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL UNIQUE,          -- FK tới Identity Service
    full_name VARCHAR(100) NOT NULL,
    date_of_birth DATE,
    gender VARCHAR(10),                     -- MALE, FEMALE, OTHER
    phone VARCHAR(15),
    id_card_number VARCHAR(20),            -- CMND/CCCD
    insurance_number VARCHAR(20),          -- Số BHYT
    address TEXT,
    avatar_url VARCHAR(255),
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);
```

### Appointment Service DB (`careflow_appointment`)

```sql
CREATE TABLE appointments (
    id UUID PRIMARY KEY,
    patient_id UUID NOT NULL,
    department VARCHAR(50) NOT NULL,        -- Chuyên khoa
    doctor_id UUID,                         -- nullable (nếu không chọn BS)
    appointment_date DATE NOT NULL,
    time_slot VARCHAR(20),                  -- "08:00-08:30"
    status VARCHAR(20) DEFAULT 'PENDING',   -- PENDING, CONFIRMED, CHECKED_IN,
                                            -- IN_PROGRESS, COMPLETED, CANCELLED
    notes TEXT,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

CREATE TABLE departments (
    id UUID PRIMARY KEY,
    name VARCHAR(50) NOT NULL,              -- "Nội khoa", "Ngoại khoa"...
    description TEXT,
    location VARCHAR(100),                  -- "Tầng 2, khu A"
    is_active BOOLEAN DEFAULT TRUE
);
```

### EMR Service DB (`careflow_emr`)

```sql
CREATE TABLE medical_records (
    id UUID PRIMARY KEY,
    patient_id UUID NOT NULL,
    record_type VARCHAR(30),                -- EXAMINATION, LAB_RESULT, IMAGING
    title VARCHAR(200) NOT NULL,
    description TEXT,
    file_url VARCHAR(500),
    record_date DATE,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);
```

---

## RabbitMQ Events

### Event bạn PUBLISH (gửi đi)

| Event | Exchange | Routing Key | Khi nào | Ai nhận |
|-------|----------|-------------|---------|---------|
| `AppointmentCreated` | `appointment.exchange` | `appointment.created` | Bệnh nhân đặt lịch khám | Người A (Queue Service) |

```java
// Payload mẫu
{
    "appointmentId": "uuid",
    "patientId": "uuid",
    "department": "Nội khoa",
    "appointmentDate": "2026-07-20",
    "timeSlot": "08:00-08:30",
    "priority": "APPOINTMENT"    // APPOINTMENT hoặc WALK_IN
}
```

### Event bạn SUBSCRIBE (nhận về)

| Event | Routing Key | Từ ai | Bạn làm gì |
|-------|-------------|-------|-------------|
| `QueueNumberAssigned` | `queue.number.assigned` | A (Queue Service) | Hiển thị số thứ tự trên Mobile |
| `QueueCalled` | `queue.called` | A (Notification Service) | Popup "Đến lượt bạn!" |
| `PrescriptionCreated` | `prescription.created` | C (Prescription Service) | Hiển thị toa thuốc mới |

---

## Tips Flutter

### Packages nên dùng

```yaml
# pubspec.yaml
dependencies:
  # HTTP + API
  http: ^1.2.0
  dio: ^5.4.0                    # HTTP client mạnh hơn http

  # State Management (chọn 1)
  provider: ^6.1.0               # Đơn giản
  # flutter_riverpod: ^2.5.0     # Mạnh hơn

  # Storage
  shared_preferences: ^2.2.0     # Key-value đơn giản
  flutter_secure_storage: ^9.0.0 # Lưu JWT token

  # UI
  google_fonts: ^6.1.0           # Typography
  shimmer: ^3.0.0                # Loading skeleton
  cached_network_image: ^3.3.0   # Cache ảnh

  # QR Code
  mobile_scanner: ^4.0.0         # Quét QR

  # WebSocket
  stomp_dart_client: ^2.0.0      # STOMP over WebSocket

  # Utils
  intl: ^0.19.0                  # Format ngày/giờ
  flutter_svg: ^2.0.0            # SVG icons
```

### Cấu trúc API Service

```dart
// lib/config/api_config.dart
class ApiConfig {
  static const String baseUrl = 'http://localhost:8080/api'; // qua Gateway
  static const String wsUrl = 'http://localhost:8080/ws';
}

// lib/services/api_service.dart
class ApiService {
  final String baseUrl = ApiConfig.baseUrl;
  String? _token;

  void setToken(String token) => _token = token;

  Future<Map<String, dynamic>> get(String endpoint) async {
    final response = await http.get(
      Uri.parse('$baseUrl$endpoint'),
      headers: {'Authorization': 'Bearer $_token'},
    );
    return jsonDecode(response.body);
  }
}
```

### Mock data khi API chưa sẵn sàng

```dart
// lib/services/mock/mock_queue_service.dart
class MockQueueService {
  static Map<String, dynamic> getQueueStatus() {
    return {
      'queueNumber': 'A-042',
      'position': 5,
      'estimatedWaitMinutes': 23,
      'status': 'WAITING',
      'department': 'Nội khoa',
    };
  }
}
```
