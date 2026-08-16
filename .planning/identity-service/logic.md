# CareFlow Identity Service - Kiến trúc & Luồng Xử lý

## 1. Tổng quan
Dịch vụ **Identity Service** đảm nhiệm vai trò quản lý tài khoản người dùng, xác thực (Authentication), cấp phát JWT token, và xoay vòng Refresh Token (Refresh Token Rotation).

## 2. Mô hình Dữ liệu (Database Schema)
Chi tiết cấu trúc các bảng được mô tả tại file DBML: [.planning/identity-service/identity-db.dbml](file:///home/levi/Desktop/careflow/.planning/identity-service/identity-db.dbml).

## 3. Các Luồng Nghiệp vụ Chính
- **Đăng nhập (`POST /api/auth/login`)**: Xác thực tài khoản trên Remote DB, cấp cặp Access Token (JWT) & Refresh Token.
- **Xoay vòng Token (`POST /api/auth/refresh`)**: Thu hồi token cũ (`revoked_at`), cấp cặp token mới.
- **Đăng xuất (`POST /api/auth/logout`)**: Thu hồi Refresh Token.
