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

Source backend/web R0–R5 và mobile được hợp nhất trong đợt xuất bản develop ngày21/10/2026; [biên bản gộp](.planning/deployment/publish-r0-r5-2026-10-21.md) ghi phạm vi và kiểm tra. Runtime Mac/iPhone vẫn đối chiếu theo biên bản từng đợt và artifact, không suy ra đã triển khai chỉ từ commit. Cấu hình private, token và dữ liệu người thật không thuộc bản xuất bản.
