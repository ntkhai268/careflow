# Nghiệp vụ EMR & Quy chuẩn Hiển thị Lâm sàng (Clinical UI Standards)

Nghiệp vụ **Quản lý Hồ sơ bệnh án điện tử (EMR)** là nghiệp vụ trung tâm của hệ thống khám chữa bệnh tại bệnh viện, có nhiệm vụ lưu trữ, tổ chức và cho phép tra cứu toàn bộ thông tin lâm sàng của người bệnh trong suốt quá trình khám, điều trị và theo dõi. Dữ liệu trong EMR liên kết với các phân hệ khác như Patient Service, Consultation Service, Prescription Service để cung cấp một bức tranh tổng thể (Patient Summary / Chart Summary 360°) phục vụ **Use Case 6 – Tra cứu Hồ sơ bệnh án điện tử (UC6)**.

---

## 1. Nguyên tắc Phân chia Dữ liệu & Quyền hạn (Source of Truth)

1. **EMR Service — Master Health Record**: Quản lý thông tin hồ sơ chủ cố định (`medical_records`): Mã EMR (`record_number`), Nhóm máu (`blood_type`), Tiền sử bệnh nền bản thân & gia đình (`medical_history`).
2. **Patient Service — Structured Allergies**: Nguồn sự thật cho danh sách dị ứng cấu trúc hóa (`patient_allergies`) chia 3 mức độ (Critical, Warning, Info).
3. **Consultation Service & Prescription Service**: Nguồn sự thật cho lịch sử ca khám và đơn thuốc cũ.
4. **EMR API Aggregation Pattern**: Endpoint `GET /api/emr/patients/{patientId}/summary` đóng vai trò aggregator tổng hợp 360° dữ liệu cho Frontend hiển thị.

---

## 2. Quy tắc Ưu tiên Hiển thị Giao diện Lâm sàng (Clinical UI Priority Rules)

Dựa trên phân tích an toàn y khoa và trải nghiệm bác sĩ (Doctor UX):

### 2.1. Thanh định danh Header (Clinical Header)
- **Muc đích**: Nhận diện nhanh người bệnh và cảnh báo sinh mạng an toàn kê đơn.
- **Thành phần**: Tên, Tuổi, Giới tính, Mã BN, Số điện thoại, Số BHYT.
- **Ưu tiên cao nhất**: **Banner Cảnh báo Dị ứng 3 Mức** (Sticky Red Banner cho dị ứng phản vệ Mức 1; Yellow Banner cho dị ứng Mức 2).
- **Quy tắc**: KHÔNG đẩy Mã EMR hay Nhóm máu lên Header để tránh gây quá tải thông tin thị giác cho bác sĩ.

### 2.2. Tab "Tổng quan & Tiền sử" (`activeTab === "summary"`)
- **Vị trí 1 — EMR Master Record Card**: Hiển thị riêng 1 Card ở đầu tab gồm:
  - **Mã EMR (`recordNumber`)**: e.g., `EMR-2026-000001` (Phục vụ tra cứu hành chính, đối chiếu và kiểm tra BHYT).
  - **Nhóm máu (`bloodType`)**: e.g., `O Rh+` (Hiển thị như thông tin tham khảo lâm sàng).
  - **Tiền sử bệnh nền (`medicalHistory`)**: Tiền sử bệnh bản thân & gia đình.
- **Vị trí 2 — Structured Allergy List**: Danh sách các dị ứng đầy đủ mức độ.
- **Vị trí 3 — Consultation History**: Top 5 ca khám gần nhất.
- **Vị trí 4 — Prescription History**: Top 3 đơn thuốc gần nhất.

---

## 3. Tiền điều kiện
- Bác sĩ đã đăng nhập thành công.
- Có quyền truy cập hồ sơ bệnh nhân theo ca trực / chuyên khoa.

---

## 4. Luồng nghiệp vụ chính
1. Bác sĩ mở trang phòng khám của một bệnh nhân (`/consultation/[id]`).
2. Header tự động hiển thị Banner cảnh báo dị ứng nếu bệnh nhân có tiền sử dị ứng nguy hiểm.
3. Khi bác sĩ chuyển sang Tab **"Tổng quan & Tiền sử"**, Web App gọi API `GET /api/emr/patients/{patientId}/summary`.
4. Web App hiển thị Card EMR Master (Mã EMR, Nhóm máu, Tiền sử), cùng với Lịch sử khám và Đơn thuốc cũ.
5. Bác sĩ có thể bấm xem chi tiết từng ca khám cũ hoặc đơn thuốc cũ.

---

## 5. Giá trị nghiệp vụ
Việc phân chia đúng cấu trúc hiển thị giúp bác sĩ tập trung vào an toàn người bệnh (dị ứng), đồng thời vẫn dễ dàng truy xuất thông tin hành chính (mã EMR) và dữ liệu lâm sàng khi cần thiết.
