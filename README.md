# CareFlow

CareFlow gồm Patient Mobile native, Hospital Web và các microservice phục vụ khám ngoại trú. Phạm vi bàn giao đầy đủ và trạng thái CURRENT/TARGET được chuẩn hóa tại [đặc tả R0](.planning/architecture/README.md).

## Tài liệu chuẩn

- [Hướng dẫn sử dụng và trình diễn](huong-dan-su-dung/README.md): nguồn duy nhất cho thao tác người dùng; tài khoản demo ở trang01.
- [Yêu cầu và use case](.planning/architecture/01-requirements-use-cases.md).
- [Kiến trúc và ownership](.planning/architecture/02-service-architecture.md).
- [Contract/state hiện hành và mục tiêu](.planning/architecture/03-contracts-and-states.md).
- [Baseline, build và điều kiện nghiệm thu](.planning/architecture/05-r0-baseline-results.md).
- [Kế hoạch hoàn thiện R0–R7](.planning/full-requirements-plan-2026-10-19.md).

## Phạm vi hiện tại

Có module Gateway, Identity, Patient, EMR, Appointment, Queue, Consultation, Prescription, Lab, Notification và AI; Analytics hiện có đặc tả, chưa có module. Directory và Eureka là thành phần bổ sung; Common là thư viện. Danh sách module không đại diện tỷ lệ hoàn thành.

Định danh dùng đọc giấy tờ trên máy và đối chiếu trực tiếp, không dùng VNPT. AI còn prototype và được hoàn thiện ở đợt sau. Luồng khám backend/web có bằng chứng riêng; Face ID/camera/push và hành trình native phải được nghiệm thu đúng thiết bị. Bản iPhone16 vẫn là bản đang dùng; build kiểm chứng R0 không tự được cài hoặc phát hành.

Source backend/web R0–R5 và mobile được hợp nhất trong đợt xuất bản develop ngày 21/10/2026; [biên bản gộp](.planning/deployment/publish-r0-r5-2026-10-21.md) ghi phạm vi và kiểm tra. Runtime Mac/iPhone vẫn đối chiếu theo biên bản từng đợt và artifact, không suy ra đã triển khai chỉ từ commit. Cấu hình private, token và dữ liệu người thật không thuộc bản xuất bản.

## Khởi chạy & Vận hành

Yêu cầu: JDK 21, Maven 3.9+, PostgreSQL 16, Docker Engine có Compose v2, Node.js 18+.

### 1. Khởi chạy Hạ tầng (Postgres & RabbitMQ)

```bash
docker compose -f docker-compose.infra.yml up -d
```

### 2. Khởi chạy Hệ thống Microservices & Frontend (Tối ưu hóa RAM/CPU)

Sử dụng script `./start-local.sh` đã được cấu hình tối ưu tài nguyên (giới hạn JVM heap, chống nghẽn CPU và hỗ trợ chạy theo cụm phân hệ):

```bash
# Menu tương tác (tự động chọn chế độ ALL sau 5 giây)
./start-local.sh

# Hoặc khởi chạy theo cụm phân hệ mong muốn để tiết kiệm tài nguyên:
./start-local.sh prescription   # Cụm Kê đơn thuốc (Consultation, Prescription, Patient, Gateway, Frontend...)
./start-local.sh appointment    # Cụm Đặt khám & Hàng đợi (Appointment, Queue, AI, Gateway, Frontend...)
./start-local.sh emr-lab        # Cụm Bệnh án & Xét nghiệm (EMR, Lab, Consultation, Gateway, Frontend...)
./start-local.sh core           # Cụm Dịch vụ Cốt lõi (Eureka, Gateway, Identity, Patient, Frontend)
./start-local.sh all            # Toàn bộ 13 services + Frontend
```

> 📖 Xem hướng dẫn vận hành và danh sách port chi tiết tại **[docs/manual.md](docs/manual.md)**.

## Tài liệu liên quan

- [Hướng dẫn sử dụng và trình diễn](huong-dan-su-dung/README.md)
- [Hướng dẫn vận hành & Khởi chạy (Manual)](docs/manual.md)
- [Hành trình bệnh nhân & System workflow](docs/patient-journey-and-system-workflow.md)
- [Tài liệu AI Service & Clinical RAG](docs/SESSION_HANDOVER.md)
