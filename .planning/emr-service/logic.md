# CareFlow EMR Service - Kế hoạch & Kiến trúc Nghiệp vụ

## 1. Tổng quan
Dịch vụ **EMR Service (Electronic Medical Record)** đảm nhiệm vai trò lưu trữ hồ sơ bệnh án điện tử tập trung của bệnh nhân, bao gồm tiền sử bệnh, nhóm máu, tiền sử dị ứng và các đợt khám bệnh liên thông giữa các dịch vụ trong hệ thống CareFlow.

## 2. Cấu trúc Database (Schema DBML)
Chi tiết thiết kế cơ sở dữ liệu được quản lý tại: [.planning/emr-service/emr-db.dbml](file:///home/levi/Desktop/careflow/.planning/emr-service/emr-db.dbml).

## 3. Quy tắc bắt buộc
Mọi thay đổi liên quan đến JPA Entity hoặc DB Schema của EMR Service phải được cập nhật trực tiếp vào file `emr-db.dbml`.
