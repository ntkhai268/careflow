# 10 — Kịch bản trình diễn và nghiệm thu

[Quay về mục lục](README.md) · [Tài khoản](01-tai-khoan-va-du-lieu.md)

## Chạy một luồng từ đầu

1. Mở stack theo trang 00, xác nhận Web/mobile/API hoạt động.
2. Chọn bệnh nhân mới phù hợp và slot đúng ngày. Ghi bệnh nhân, ngày/ca, phòng; dùng danh mục thật.
3. Làm trang 03 đến check-in và bác sĩ thấy đúng người.
4. Bác sĩ gọi/bắt đầu, nhập và lưu dữ liệu theo trang 04. Ghi mã Consultation, đối chiếu với appointment/phiếu.
5. Chọn một nhánh: hoàn tất không CLS; hoặc làm đủ trang 05 rồi đọc kết quả trên ca cũ.
6. Nếu cần trình diễn bàn giao, thực hiện trang 07 trong lúc ca mở; tiếp tục các bước dưới quyền bác sĩ nhận.
7. Phát hành toa nếu cần và thực hiện trang 06 khi đủ điều kiện. Không bỏ qua điều kiện quyết toán chưa xác minh.
8. Mobile xem kết quả/hồ sơ/thông báo theo trang 08.
9. Ghi từng chặng đạt/thất bại/chưa chạy. Chỉ gọi là E2E đạt khi cùng ca đi qua đủ những chặng đã chọn bằng API/DB thật.

## Kịch bản ngắn trên dữ liệu đang có

1. Mở mobile và đăng nhập bệnh nhân bàn giao 01.
2. Mở Web bằng hai hồ sơ Chrome cho B và C.
3. B gửi SBAR đến C, C xem yêu cầu chờ rồi tự tiếp nhận.
4. C mở ca, đối chiếu tác giả gốc A và các toa cũ chỉ đọc; B không còn mở/sửa được ca.
5. Mobile vẫn cùng hành trình/phiếu. C bàn giao lại B và B xác nhận để giữ bộ demo sẵn dùng.
6. Nếu trình diễn ADMIN, dùng hồ sơ Chrome thứ ba để cấp/thu hồi quyền điều phối; ADMIN không nhận thay bác sĩ.

## Bằng chứng tại mốc 16/10/2026

| Luồng | Mức đã kiểm tra |
|---|---|
| Đăng nhập hai bước, refresh/thu hồi, kiểm tra quyền | Đã có API và browser thật trên runtime Mac |
| Đặt lịch, phiếu, check-in nhân viên/QR, gọi/bắt đầu | Đã kiểm tra các chặng trên dữ liệu thật; phải ghi lại nếu chạy một vòng mới |
| Bàn giao cùng khoa/phòng | Đã kiểm tra API, cạnh tranh nhận ca và 28 bước UI trên Chrome; sửa lỗi form trống khi 403/404 |
| Bác sĩ nhận ghi bệnh án/toa nháp | Đã lưu và tải lại qua Web; giữ tác giả A/C |
| Ca nhận bàn giao không CLS đến hoàn tất | Đã kiểm tra API Consultation/Queue/Appointment và toa phát hành; bệnh nhân thấy lịch hoàn tất |
| CLS → đọc kết quả → cấp phát đầy đủ | Có implementation/contract, chưa nghiệm thu liền mạch toàn bộ nhánh trên bộ hiện tại |
| Tái khám toàn chu kỳ, thanh toán/quyết toán thật | Chưa được coi là hoàn tất qua một lượt E2E |
| eKYC thật, passkey/sinh trắc, FCM/native push | Ngoài phần đã nghiệm thu; không thay bằng mô phỏng để báo đạt |

## Phiếu ghi cho mỗi lần chạy

Sao chép mẫu dưới vào bản ghi buổi demo riêng; kết quả không thay thế nội dung hướng dẫn gốc.

```text
Ngày/giờ, phiên bản source/runtime:
Vai trò/phòng và tài khoản demo:
Bệnh nhân → appointment → phiếu → consultation:
Order/toa liên quan (nếu có):
Chặng đã thao tác và trạng thái trước/sau:
HTTP/bằng chứng đối chiếu ở các vai trò:
Kết quả: Đạt / Thất bại / Chưa chạy
Lỗi và bước tái hiện:
Trạng thái dữ liệu sau kiểm tra, cách lặp lại:
```

Không đưa token, secret, recovery code, QR còn hiệu lực hoặc bệnh án người thật vào bản ghi công khai. Các báo cáo chi tiết của runtime tích hợp nằm tại `.planning/` của worktree đó; bộ hướng dẫn này không tự xuất bản toàn bộ evidence/code chưa commit.
