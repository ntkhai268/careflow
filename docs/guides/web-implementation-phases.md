# CareFlow — Implementation Plan theo Giai đoạn (Website Only)

> [!IMPORTANT]
> **Phạm vi**: Chỉ code **Hospital Web App** (Doctor / Staff / Lab Tech / Admin). **Patient Mobile App** không thuộc phạm vi nhóm này.

## Trạng thái Hiện tại

| Phân hệ                      | Trang hiện có                                      | Trạng thái |
| ---------------------------- | -------------------------------------------------- | ---------- |
| Doctor Web — Login           | `login/page.tsx`                                   | ✅ Done    |
| Doctor Web — Dashboard       | `dashboard/page.tsx`, `dashboard/general/page.tsx` | ✅ Done    |
| Doctor Web — Queue (3 lanes) | `dashboard/queue/page.tsx`                         | ✅ Done    |
| Doctor Web — Consultation    | `consultation/[id]/page.tsx`                       | ✅ Done    |
| Doctor Web — Prescriptions   | `dashboard/prescriptions/page.tsx`                 | ✅ Done    |
| Staff Web                    | —                                                  | ❌ Chưa có |
| Lab Tech Web                 | —                                                  | ❌ Chưa có |
| Admin Web                    | —                                                  | ❌ Chưa có |

## 3 Queue Types (Tóm tắt)

| Queue Type            | Phục vụ ai           | Tạo từ                 | Trạng thái    |
| --------------------- | -------------------- | ---------------------- | ------------- |
| `CONSULTATION`        | Bác sĩ phòng khám    | `AppointmentConfirmed` | ✅ Done       |
| `LAB_EXECUTION`       | Kỹ thuật viên CLS    | `ClinicalOrderCreated` | ❌ Chưa có UI |
| `PHARMACY_DISPENSING` | Nhân viên phát thuốc | `PrescriptionIssued`   | ❌ Chưa có    |

---

## Phase 1: Staff Web — Nhân viên Tiếp nhận

> **Mục tiêu**: Nhân viên đăng nhập, quét QR check-in, xem lịch hôm nay, xử lý MISSED/recall.
> **Ưu tiên**: CAO — đây là bước đưa bệnh nhân vào CONSULTATION queue.

### Frontend (trong `frontend/doctor-web`)

#### [NEW] `src/app/(dashboard)/staff/page.tsx`

- Dashboard Staff: tổng quan ngày (số đã check-in, đang chờ, vắng mặt)
- Sidebar riêng cho role `STAFF`

#### [NEW] `src/app/(dashboard)/staff/checkin/page.tsx`

- Giao diện quét QR (camera hoặc nhập mã thủ công)
- Gọi `POST /api/queues/check-in`
- Hiển thị kết quả: tên BN, số thứ tự, phòng → `CHECKED_IN`
- Xử lý lỗi: QR hết hạn, đã check-in (idempotent)

#### [NEW] `src/app/(dashboard)/staff/schedule/page.tsx`

- Danh sách tất cả lịch hôm nay (`CONFIRMED` / `CHECKED_IN` / `NO_SHOW`)
- Filter theo khoa / phòng

#### [NEW] `src/app/(dashboard)/staff/missed/page.tsx`

- Danh sách bệnh nhân `MISSED`
- Recall → `MISSED → CHECKED_IN` (xếp lại cuối hàng)
- Confirm `NO_SHOW`

### Routing & Auth Guard

- Route `/staff/*` chỉ cho role `STAFF`
- Điều hướng từ login theo role

**Checklist items covered**: §29.1 → §29.4

---

## Phase 2: Lab Tech Web — Kỹ thuật viên Cận lâm sàng

> **Mục tiêu**: KTV xem `LAB_EXECUTION` queue, gọi số, bắt đầu thực hiện, nhập kết quả, phát hành.

### Frontend (trong `frontend/doctor-web`)

#### [NEW] `src/app/(dashboard)/lab/page.tsx`

- Dashboard: tổng số chờ, đang thực hiện, đã xong hôm nay

#### [NEW] `src/app/(dashboard)/lab/queue/page.tsx`

- Danh sách `LAB_EXECUTION` theo `servicePointId` (FIFO)
- Số thứ tự (`XN-105`), danh sách xét nghiệm trong order, trạng thái
- Nút **Gọi tiếp theo** → `QUEUED → CALLED`

#### [NEW] `src/app/(dashboard)/lab/orders/[orderId]/page.tsx`

- Chi tiết Lab Order: danh sách hạng mục
- Nút **Bắt đầu thực hiện** → `CALLED → IN_PROGRESS`
- Form nhập kết quả: giá trị, đơn vị, khoảng tham chiếu, flag bất thường
- Nút **Phát hành kết quả** → `IN_PROGRESS → RESULT_AVAILABLE`
- Xử lý vắng mặt: `CALLED → MISSED → QUEUED` lại

### API Integration

```
GET  /api/queues/service-points/{spId}/active
POST /api/queues/service-points/{spId}/call-next
POST /api/labs/orders/{orderId}/start
PUT  /api/labs/orders/{orderId}/items/{itemId}/result
POST /api/labs/orders/{orderId}/finalize
```

**Checklist items covered**: §31.1 → §31.6

---

## Phase 3: Pharmacy Dispensing Queue

> **Mục tiêu**: Khi toa xác nhận (`PrescriptionIssued`), hệ thống tự tạo lượt phát thuốc. Staff gọi FIFO, đối chiếu toa, xác nhận cấp phát.

> [!IMPORTANT]
> Phase này cần thay đổi **cả backend** (Queue Service consumer mới) **lẫn frontend**.

### Backend — `careflow-queue-service`

#### [NEW/MODIFY] `PrescriptionIssuedConsumer.java`

- Consume event `PrescriptionIssued` từ RabbitMQ
- Tạo Queue Entry `type = PHARMACY_DISPENSING` tại `servicePointId` mặc định
- State: `QUEUED`, ghi `queuedAt`
- Idempotency theo `prescriptionId`

#### API cần có

```
GET  /api/queues/service-points/{spId}/active?type=PHARMACY_DISPENSING
POST /api/queues/service-points/{spId}/call-next
POST /api/queues/entries/{entryId}/start
POST /api/queues/entries/{entryId}/complete
```

### Frontend (trong `frontend/doctor-web`)

#### [NEW] `src/app/(dashboard)/staff/pharmacy/page.tsx`

- Danh sách `PHARMACY_DISPENSING` queue (FIFO theo `queuedAt`)
- Nút **Gọi số tiếp theo** → `QUEUED → CALLED`
- Chi tiết đơn thuốc khi đang phát (thuốc, liều, số lượng)
- Nút **Xác nhận cấp phát** → `Prescription: CONFIRMED → DISPENSED`
- Xử lý vắng mặt: `CALLED → MISSED → QUEUED` lại

**Checklist items covered**: §30.1 → §30.3

---

## Phase 4: Doctor Consultation — Hoàn thiện Luồng Bác sĩ

> **Mục tiêu**: Hoàn thiện toàn bộ luồng bác sĩ từ gọi số đến hoàn tất khám.

> [!NOTE]
> Items này liên quan `feature/vi/prescription-service` & `feature/vi/emr-service`. Cân nhắc branch strategy.

### Doctor Web — Các tính năng còn thiếu

| Feature                                                             | Checklist | Trạng thái |
| ------------------------------------------------------------------- | --------- | ---------- |
| Bác sĩ bấm "Bắt đầu khám" → `CALLED → IN_PROGRESS`                  | 11.1–11.3 | ❌         |
| Nhập sinh hiệu đầy đủ (nhiệt độ, HA, nhịp tim, SpO2...)             | 11.5      | ❌         |
| Nhập triệu chứng, chẩn đoán, mã ICD-10                              | 11.6      | ❌         |
| Kê toa: DRAFT → CONFIRMED → phát `PrescriptionIssued`               | 16.1      | ❌         |
| Hoàn tất: Consultation/QueueEntry/Appointment → COMPLETED/FULFILLED | 16.3      | ❌         |
| Đồng bộ EMR                                                         | 16.4      | ❌         |

**Checklist items covered**: §11.1–11.7, §12.1–12.6, §16.1–16.4

---

## Phase 5: Admin Web — Quản trị Hệ thống

> **Mục tiêu**: Admin quản lý tài khoản, cấu hình cơ sở y tế, xem báo cáo vận hành.

### Frontend (trong `frontend/doctor-web`)

#### [NEW] `src/app/(dashboard)/admin/page.tsx`

- Dashboard: lượt khám hôm nay, thời gian chờ TB, phòng đang hoạt động

#### [NEW] `src/app/(dashboard)/admin/users/page.tsx`

- Danh sách bác sĩ, KTV, nhân viên
- Tạo / sửa / khóa tài khoản, phân quyền role

#### [NEW] `src/app/(dashboard)/admin/departments/page.tsx`

- Quản lý khoa, phòng khám, service points (khu XN, phòng SA)
- Danh mục dịch vụ CLS

#### [NEW] `src/app/(dashboard)/admin/schedules/page.tsx`

- Lịch làm việc bác sĩ, khung giờ, capacity
- `checkInWindowMinutes`

#### [NEW] `src/app/(dashboard)/admin/reports/page.tsx`

- Thống kê lượt khám theo ngày/khoa/bác sĩ
- Tỉ lệ NO_SHOW / MISSED

**Checklist items covered**: §32.1 → §32.5

---

## Thứ tự Ưu tiên

```
Phase 1 (Staff Check-in)       ← Unblocks consultation queue hoạt động thực tế
Phase 2 (Lab Tech Web)         ← Unblocks CLS flow và RESULT_REVIEW trigger
Phase 3 (Pharmacy Dispensing)  ← Hoàn thiện prescription end-to-end
Phase 4 (Doctor Flow finish)   ← Polish Doctor Web
Phase 5 (Admin Web)            ← Cấu hình & vận hành
```

---

## Open Questions

> [!IMPORTANT]
> **Q1**: Tất cả role (Staff, Lab, Admin) dùng chung 1 frontend `doctor-web` với route riêng, hay tách thành app khác?
>
> **Q2**: Backend Queue Service đã có API `GET /api/queues/service-points/{spId}/active` chưa? Hay chỉ có `/rooms/{roomId}/active`?
>
> **Q3**: Phase nào muốn bắt đầu ngay?

## Verification Plan

### Manual E2E

- **Scenario A**: Staff quét QR → BN vào Doctor Queue → Bác sĩ gọi → Khám → Kê toa → Hoàn tất
- **Scenario B**: Bác sĩ tạo Lab Order → KTV thấy trong Lab Queue → KTV nhập kết quả → BN vào RESULT_REVIEW lane
- **Scenario C**: Bác sĩ confirm Prescription → Staff thấy trong Pharmacy Queue → Staff xác nhận DISPENSED
