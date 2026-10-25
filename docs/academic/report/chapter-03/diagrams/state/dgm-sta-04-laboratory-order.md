# DGM-STA-04 — Trạng thái Laboratory Order

```mermaid
stateDiagram-v2
    [*] --> ORDERED
    ORDERED --> QUEUED: Order hợp lệ
    QUEUED --> CALLED
    CALLED --> IN_PROGRESS
    IN_PROGRESS --> RESULT_AVAILABLE: Phát hành kết quả
    RESULT_AVAILABLE --> REVIEWED: Bác sĩ đã đọc
    CALLED --> MISSED
    MISSED --> QUEUED
    ORDERED --> CANCELLED
    REVIEWED --> [*]
    CANCELLED --> [*]
```
