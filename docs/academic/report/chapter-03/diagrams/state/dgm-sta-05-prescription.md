# DGM-STA-05 — Trạng thái Prescription

```mermaid
stateDiagram-v2
    [*] --> DRAFT
    DRAFT --> CONFIRMED: Bác sĩ xác nhận
    DRAFT --> CANCELLED: Hủy draft
    CONFIRMED --> DISPENSED: Phát thuốc sau khi lượt đã bắt đầu
    CONFIRMED --> CANCELLED_BY_AMENDMENT: Thay thế toa
    DISPENSED --> [*]
    CANCELLED --> [*]
    CANCELLED_BY_AMENDMENT --> [*]

    note right of CONFIRMED
        Patient chỉ xem toa CONFIRMED
        hoặc DISPENSED.
        PrescriptionIssued tạo queue
        PHARMACY_DISPENSING.
    end note
```
