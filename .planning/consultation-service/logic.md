# CareFlow Consultation Service - Nghiệp vụ Khám bệnh Chi tiết

## 1. Mục đích
Nghiệp vụ khám bệnh dùng để bác sĩ thực hiện đánh giá lâm sàng cho bệnh nhân trong một lượt khám cụ thể, ghi nhận sinh hiệu, triệu chứng, nhận định, chẩn đoán và các chỉ định tiếp theo. Kết quả của nghiệp vụ này là một phiên khám hợp lệ, được lưu vào EMR và làm đầu vào cho kê đơn thuốc (`careflow-prescription-service`).

## 2. Mô hình Database (DBML)
Sơ đồ thiết kế CSDL chi tiết được quản lý tại: [.planning/consultation-service/consultation-db.dbml](file:///home/levi/Desktop/careflow/.planning/consultation-service/consultation-db.dbml).

## 3. Các bước xử lý trong ca khám
1. **Mở phiên khám**: Bác sĩ chọn ca khám từ danh sách chờ. Hệ thống chuyển trạng thái `status` thành `IN_PROGRESS` và ghi nhận `started_at`.
2. **Ghi nhận Sinh hiệu (Vital Signs)**: Nhập nhiệt độ, huyết áp, nhịp tim, SpO2, chiều cao, cân nặng.
3. **Khám lâm sàng & Chẩn đoán**: Nhập triệu chứng, diễn tiến bệnh, mã bệnh quốc tế ICD-10 (`icd10_code`, `icd10_name`) và chẩn đoán chi tiết.
4. **Kê đơn / Chỉ định**: Tạo đơn thuốc liên thông với `careflow-prescription-service`.
5. **Hoàn tất ca khám**: Cập nhật `status = COMPLETED` và `completed_at = NOW()`. Đồng bộ thông tin ca khám sang EMR.

## 4. Quy tắc đồng bộ DB
Khi bổ sung trường mới vào JPA Entity `Consultation.java`, lập tức cập nhật file `consultation-db.dbml` tương ứng.
