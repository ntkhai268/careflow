# CareFlow - Quy tắc Thiết kế Database, Git Flow & Quản lý Thư mục .planning

Tài liệu này quy định các quy tắc bắt buộc áp dụng cho toàn bộ đội ngũ phát triển và AI Assistant khi làm việc với codebase **CareFlow**.

---

## 📌 Quy tắc Git & Work Branch Strategy (Git Flow Rules)

1. **Phạm vi Nhánh Cá nhân (Service Isolation)**:
   - Mỗi lập trình viên / dịch vụ chỉ làm việc và **push code lên nhánh feature riêng của service đó** (ví dụ: `feature/vi/prescription-service`, `feature/vi/consultation-service`).
   - **TUYỆT ĐỐI KHÔNG PUSH TRỰC TIẾP LÊN NHÁNH `main`**. Mọi thay đổi vào `main` bắt buộc phải qua Pull Request (PR) và Code Review.

2. **Cập nhật Code mới từ Main trước khi code (Pull before Coding)**:
   - Trước khi bắt đầu phát triển bất kỳ tính năng hoặc sửa lỗi mới nào, **BẮT BUỘC** phải thực hiện `git pull origin main` (hoặc merge/rebase mới nhất từ `main`) về nhánh làm việc hiện tại.
   - Việc này đảm bảo mã nguồn nhánh feature luôn đồng bộ với các thay đổi mới nhất của hệ thống, tránh xung đột (conflict) và tình trạng code bị lỗi thời (outdated).

3. **Ngôn ngữ Git Commit Messages (English Commits)**:
   - Tất cả các Git commit messages **BẮT BUỘC phải viết bằng Tiếng Anh** theo chuẩn Conventional Commits (ví dụ: `feat: connect prescription-service directly to remote postgres database`, `fix: remove mock jwt logic in doctor web auth context`).

---

## 📌 Quy tắc Thiết kế & Cập nhật Database Schema (DB Rules)

1. **Đồng bộ hóa DBML**: 
   Khi thực hiện bất kỳ thay đổi nào liên quan đến Database (thêm/sửa/xóa bảng, thêm/sửa/xóa cột, đổi kiểu dữ liệu, index, khóa ngoại) trong mã nguồn (JPA Entity / Flyway SQL Migration):
   - **BẮT BUỘC** phải cập nhật ngay lập tức file `.dbml` tương ứng trong thư mục `.planning/<service_name>/`.
   - Đường dẫn file DBML quy chuẩn:
     - EMR Service: `.planning/emr-service/emr-db.dbml`
     - Consultation Service: `.planning/consultation-service/consultation-db.dbml`
     - Prescription Service: `.planning/prescription-service/prescription-db.dbml`
     - Identity Service: `.planning/identity-service/identity-db.dbml`

2. **Cấu hình Remote Database**:
   - Tất cả kết nối Database làm việc trực tiếp với **Remote Server** (`100.116.233.60:5432`).
   - Mọi cấu hình kết nối Remote DB phải để trong file `application-remote.yml` của dịch vụ và file `.env` ở thư mục gốc.
   - **KHÔNG COMMIT** thông tin bảo mật DB remote lên Git.

3. **Cấu trúc thư mục `.planning/`**:
   - Thư mục `.planning/` ở thư mục gốc dự án chứa toàn bộ kế hoạch, sơ đồ cơ sở dữ liệu (`.dbml`) và tài liệu nghiệp vụ (`logic.md`, `progress.md`) phân theo từng Microservice riêng biệt.
