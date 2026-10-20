# CareFlow Documentation Index

Tài liệu dự án **CareFlow** được tổ chức theo các nhóm chức năng rõ ràng để thuận tiện cho việc tra cứu, phát triển và báo cáo.

---

## 📌 Tài liệu Cốt lõi & Thiết kế Tổng thể (Core & Design)

- [Hành trình bệnh nhân & System Workflow](patient-journey-and-system-workflow.md) — Đặc tả toàn bộ hành trình khám bệnh ngoại trú, luồng đồng bộ/bất đồng bộ và phân nhiệm giữa các services.
- [Hệ thống Thiết kế UI/UX (Design System)](design.md) — Nguyên tắc thiết kế giao diện Web bác sĩ, bảng màu Sant Joan de Déu (SJD Purple) và typography.
- [Kế hoạch Xây dựng Hệ thống (Implementation Plan)](implementation_plan.md) — Phân tích tổng thể, lộ trình 12 services và chiến lược build/mock.
- [Hướng dẫn Vận hành & Cài đặt Local (Manual)](manual.md) — Khởi chạy cơ sở hạ tầng (Postgres, RabbitMQ) và cấu hình tối ưu tài nguyên máy dev.

---

## 🛠️ Hướng dẫn Kỹ thuật & Môi trường Dev (Guides)

- [Danh sách Tài khoản Test](guides/accounts.md) — Danh sách tài khoản Doctor, Lab, Staff, Admin xác minh từ database.
- [Quy trình Git (Git Workflow)](guides/git-workflow.md) — Chiến lược phân nhánh Git và quy tắc làm việc nhóm.
- [Demo Hybrid Patient Mobile Local](guides/local-patient-mobile-demo.md) — Thiết lập môi trường chạy thử nghiệm phân hệ Mobile cục bộ.
- [Kế hoạch triển khai Hospital Web](guides/web-implementation-phases.md) — Trạng thái chi tiết các màn hình trên Hospital Web.

---

## 💡 Đặc tả Tính năng & Kỹ thuật Chuyên sâu (Features & Slices)

- [Speech-to-Text & Clinical Extraction](features/speech-to-text.md) — Giải pháp nhận diện giọng nói và trích xuất chỉ số lâm sàng bằng LLM.
- [Service Contracts (12 Microservices)](service-contracts/README.md) — Quy ước HTTP, Event và API chi tiết cho từng service.
- [Vertical Slices](vertical-slices/01-check-in-queue.md) — Chi tiết lát cắt tính năng liên dịch vụ (Check-in, Queue).
- [Architecture Decisions (ADRs)](decisions/) — Các quyết định kiến trúc quan trọng (Thanh toán hai giai đoạn, Mobile booking, v.v.).
- [Superpowers Specs & Plans](superpowers/) — Kế hoạch và thông số kỹ thuật các đợt phát triển mở rộng.

---

## 🎓 Học thuật & Báo cáo Tốt nghiệp (Academic)

- [Báo cáo Tổng hợp (Report Draft)](academic/report.md) — Bản thảo báo cáo tốt nghiệp đề tài CareFlow.
- [Các chương Báo cáo Chi tiết](academic/report/) — Cấu trúc từng chương (Chương 1 đến Chương 6) và quy chuẩn viết.
- [Phân tích & Thiết kế OOAD - Phân hệ Bệnh nhân](academic/ooad-person-b.md) — Tài liệu OOAD phân hệ Người B.
- [Phân tích Chi tiết OOAD](academic/ooad-analysis-person-b.md) — Bảng Use Case, Actor và phân tích chi tiết.
- [Đề xuất Ứng dụng AI vào Hệ thống](academic/De_Xuat_Ung_Dung_AI_Vao_He_Thong_CareFlow.docx) — Văn bản đề xuất ứng dụng AI.

---

## 📚 Tài liệu Tham khảo & Nghiên cứu (Reference & Research)

- [System Design Roadmap Checklist](reference/system-design-roadmap.md) — Danh mục kiến thức System Design tham khảo.
- [Nghiên cứu Kiến trúc & Messaging Patterns](research/) — Tổng hợp kiến trúc hệ thống và giao tiếp Microservices.
