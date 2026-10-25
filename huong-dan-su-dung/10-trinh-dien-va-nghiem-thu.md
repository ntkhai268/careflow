# 10 — Kịch bản trình diễn và nghiệm thu

[Quay về mục lục](README.md) · [Tài khoản](01-tai-khoan-va-du-lieu.md)

## Tiếp tục lượt demo chính đã chuẩn bị

Tài khoản/mật khẩu ở mục **Bộ demo chính — Nội tổng quát, ROOM-01** tại [trang 01](01-tai-khoan-va-du-lieu.md). Chức năng Web được đối chiếu với `origin/develop` tại `e5fc9c1`; runtime vẫn ở worktree tích hợp, không reset checkout hoặc dữ liệu.

1. Mở riêng ba hồ sơ Chrome cho bệnh nhân, bác sĩ và STAFF. Bệnh nhân dùng Điện thoại 01 trên `http://localhost:3000`; Web ở `http://localhost:5173`.
2. Lượt kiểm tra mới nhất **NOI-006, 17/10/2026, 15:30–16:00**, ROOM-01, đã được dùng kiểm tra giao diện rồi chuyển COMPLETED; toa tại mốc đối chiếu CONFIRMED. NOI-003 trước đó cũng đã COMPLETED. Hai ca này chỉ dùng xem lịch sử; STAFF tiếp tục quầy thuốc nếu toa chưa cấp phát. Muốn demo bước khám/kê toa từ đầu cần lượt mới hợp lệ.
3. Bác sĩ vào `/dashboard/queue`, đối chiếu **Mẫu Mobile 01 / NOI-006**: CHECKED_IN thì **Gọi số**, CALLED thì **Bắt đầu khám**, IN_PROGRESS thì **Vào khám** để giữ cùng ca. Nếu đã hoàn tất, chỉ xem lịch sử và tạo lượt mới hợp lệ khi muốn trình diễn lại.
4. Thực hiện trang 04: nhập dữ liệu demo, lưu nháp, ký toa nếu có thuốc, hoàn tất. STAFF thực hiện trang 06 tại `/staff/pharmacy`; mobile mở kết quả của cùng lượt khám.
5. Muốn diễn từ bước đặt lịch/check-in, hoàn tất lượt này trước rồi chọn **ca giờ khác còn hợp lệ** theo trang 03. Không đặt lại các ca 15:00, 15:30 hoặc 16:00 đã dùng, không sửa trạng thái lịch sử. Nếu ngày/giờ đã qua thì tạo lượt đúng ngày thực tế mới.

NOI-001 trước đó của cùng bệnh nhân đã hoàn tất và phát thuốc; chỉ dùng đọc lịch sử. Tổng quan STAFF mở được đúng dữ liệu vai trò; STAFF vẫn thấy 403 khi vào hàng đợi bác sĩ, ca khám hoặc ADMIN.

## Chạy một luồng từ đầu

1. Mở stack theo trang 00, xác nhận Web/mobile/API hoạt động.
2. Chọn bệnh nhân mới phù hợp và slot đúng ngày. Ghi bệnh nhân, ngày/ca, phòng; dùng danh mục thật.
3. Làm trang 03 đến check-in và bác sĩ thấy đúng người.
4. Bác sĩ gọi/bắt đầu, nhập và lưu dữ liệu theo trang 04. Ghi mã Consultation, đối chiếu với appointment/phiếu.
5. Chọn một nhánh: hoàn tất không CLS; hoặc làm đủ trang 05 rồi đọc kết quả trên ca cũ.
6. Nếu cần trình diễn bàn giao, thực hiện trang 07 trong lúc ca mở; tiếp tục các bước dưới quyền bác sĩ nhận.
7. Phát hành toa nếu cần và thực hiện trang 06 khi đủ điều kiện. Ghi riêng thanh toán/quyết toán thật chưa tích hợp/nghiệm thu.
8. Mobile xem kết quả/hồ sơ/thông báo theo trang 08.
9. Ghi từng chặng đạt/thất bại/chưa chạy. Chỉ gọi là E2E đạt khi cùng ca đi qua đủ những chặng đã chọn bằng API/DB thật.

## Mốc kiểm tra 17/10/2026

Source `.worktrees/security-integration`, HEAD `5a8c6e5` và thay đổi chưa commit; runtime local HTTPS/mTLS, PostgreSQL/RabbitMQ thật. Dùng Mobile Emulator thay mobile native. Browser Chrome có giao diện, thao tác bằng Playwright hỗ trợ; không chặn request để trả response giả, không sửa SQL trạng thái để qua bước.

| Luồng | Kết quả thực tế |
|---|---|
| Đăng nhập mật khẩu, câu hỏi và quên mật khẩu | Đã thao tác mobile/Web; API kiểm tra revoke, DPoP, lockout; MFA cũ 410 |
| CXK-001 không CLS | Đặt lịch → check-in → khám/ký toa → hoàn tất → phát thuốc đạt |
| CXK-004 có CBC | QR → chỉ định → LAB thực hiện/phát hành → đọc kết quả cùng ca → ký toa/hoàn tất/phát thuốc đạt |
| CXK-002 bàn giao + CBC | Gửi/nhận/từ chối/hủy/điều phối, giữ tác giả A, B hoàn tất/phát toa riêng đạt |
| CXK-005 lưu lỗi sau ký toa | Dừng thật service: lưu lỗi thì không hoàn tất; khởi động lại, hoàn tất/phát thuốc đạt, không PUT toa đã ký |
| CXK-003 hủy lịch | Lịch và phiếu CANCELLED |
| Hồ sơ gia đình, tệp | Hai hồ sơ thật; upload tồn tại sau reload; tải xuống khớp bytes; khác chủ 403 |
| Thông báo | Inbox từ sự kiện thật; đánh dấu đọc 200; chưa nghiệm thu native push |
| Tái khám | Xem phạm vi và kết quả cập nhật tại trang 08; không đánh dấu cả chu kỳ ngày tương lai đã đạt |
| Touch ID/Face ID thật, camera/GPS vật lý | Chưa nghiệm thu; không dùng authenticator/vị trí giả để báo đạt phần vật lý |
| Thu/hoàn tiền thật, eKYC production | Chưa nghiệm thu; không dùng ONLINE_MOCK để thay thế |

Các ca CXK đã đóng, dùng để đối chiếu lịch sử. Demo mới cần ca/lịch đúng ngày mới, không tái sử dụng QR cũ hoặc ép trạng thái. Muốn thử bàn giao ca mở cũ DAL, xem trang 07 và đọc lại trạng thái thực tế trước thao tác.

Báo cáo kỹ thuật và mã tài nguyên: [báo cáo E2E](../.planning/security/end-to-end-2026-10-17.md) ở checkout gốc; trong worktree tích hợp xem `.planning/security/end-to-end-2026-10-17.md`.

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
