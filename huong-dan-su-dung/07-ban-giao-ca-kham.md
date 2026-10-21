# 07 — Bàn giao ca khám giữa bác sĩ

[Quay về mục lục](README.md) · [Tài khoản A/B/C và bệnh nhân](01-tai-khoan-va-du-lieu.md)

Đã kiểm tra API thật và **28 bước UI trên Chrome ngày 16/10/2026**. Bàn giao đổi người chịu trách nhiệm trên **cùng ca**, giữ bệnh nhân, lịch hẹn, phiếu, số thứ tự và tác giả toa/chỉ định. Phạm vi hiện tại là **cùng khoa/phòng**, không phải chuyển khoa hoặc bàn giao hàng loạt ca trực.

## Bộ kiểm tra mới ngày 17/10/2026

Bộ ROOM-11 tại trang 01 đã kiểm tra gửi/từ chối/hủy, C cấp/thu hồi quyền điều phối, C khởi tạo ngoại lệ nhưng B tự tiếp nhận, B → C → B. Trước nhận, người nhận chỉ xem tóm tắt; sau nhận, A bị chặn 403. A vẫn là tác giả chỉ định/toa cũ; B không ký được toa A (403), B lập và ký toa riêng. Sau đó LAB phát hành CBC, B đọc kết quả trên cùng ca và hoàn tất, STAFF phát thuốc thật.

Ca CXK-002 hiện COMPLETED nên không dùng lại để gửi bàn giao. Muốn chạy mới, chuẩn bị ca đang mở tại ROOM-11 và dùng A/B/C mới ở trang 01. Kịch bản DAL dưới đây là dữ liệu lịch sử riêng ngày 16/10, không trộn hai bộ.

## Chuẩn bị

1. Mở đúng runtime tích hợp theo trang 00; dùng các tài khoản bộ bàn giao tại trang 01.
2. Tách ba hồ sơ Chrome cho B, C và ADMIN khi cần; mobile đăng nhập bệnh nhân bàn giao 01.
3. Đọc trạng thái mới nhất. Mốc sau kiểm tra: B phụ trách ca demo 01, C có quyền điều phối; cùng Da liễu ROOM-08. Có bộ test cũ cùng tên hiển thị ở phòng khác, chọn đúng tài khoản/phòng.
4. Ca `c0e4d579-a1df-4cbb-803e-ae6d5384a508`, phiếu DAL-001, đang IN_PROGRESS. Ca có toa nháp của A và C, chỉ định của A và ghi chú kiểm thử C. Không dùng ca gốc NOI-010 để thử bàn giao.
5. Người nhận phải hoạt động, cùng khoa/phòng và đủ điều kiện tải công việc. Khi nhận ca IN_PROGRESS, người nhận không được có một ca IN_PROGRESS khác.

## B gửi, C tiếp nhận

1. B vào **Bàn giao ca khám** tại `/dashboard/handovers`.
2. Chọn **Bệnh nhân bàn giao 01** trong **Ca khám cần bàn giao**; đối chiếu người đang phụ trách B và mã ca.
3. Chọn **BS Bàn giao C** ở **Bác sĩ nhận bàn giao**. Bộ chọn tìm trên danh mục thật, không nhập bác sĩ tự do.
4. Điền lý do và đủ bốn phần SBAR. Nội dung mẫu cho bệnh nhân demo:

   | Trường | Ví dụ |
   |---|---|
   | Lý do | Bàn giao phần khám demo cho C tiếp tục |
   | S — Tình trạng | Đang khám ngoại trú, tình trạng ổn định |
   | B — Bối cảnh | Đã có ghi chú, chỉ định và các toa nháp cần đối chiếu |
   | A — Đánh giá | Cần theo dõi các chỉ định còn chờ |
   | R — Kế hoạch | Đọc lại hồ sơ, kiểm tra chỉ định/toa và kết luận khi đủ điều kiện |

5. Bấm **Gửi yêu cầu bàn giao**. Yêu cầu chuyển **Chờ tiếp nhận**; trách nhiệm vẫn thuộc B.
6. Trong phiên C, mở cùng trang và bấm **Tải lại**. Kiểm tra đúng bệnh nhân, SBAR, nguồn B và đích C.
7. Trước khi nhận, C chỉ xem tóm tắt được gửi cho mình; mở toàn bộ ca bị 403 là đúng.
8. C bấm **Tiếp nhận bàn giao**, đọc nội dung hộp xác nhận, nhập **Ghi nhận quyết định**, bấm **Xác nhận**.
9. Khi thành công, yêu cầu **Đã tiếp nhận**. C bấm **Tiếp tục ca đã nhận**.
10. Trang khám phải hiện **Bác sĩ phụ trách: BS Bàn giao C · Khởi tạo: BS Bàn giao A**. B mở/sửa ca sau thời điểm này bị từ chối.

## Tiếp tục làm việc sau khi nhận

1. C xem ca, hồ sơ liên quan và chỉ định/toa đã có; tác giả chỉ định/toa cũ giữ nguyên.
2. Toa nháp của A chỉ đọc, có nút **Xem toa nháp**. Toa riêng đã có của C được thao tác theo quyền/trạng thái; C không ký/sửa/hủy thay A.
3. Nếu cần toa mới, chọn thuốc từ danh mục thật và lưu dưới tên bác sĩ đang phụ trách; không đổi tác giả toa cũ.
4. Lưu ghi chú/toa nháp, tải lại để đối chiếu DB đã lưu thật.
5. Mobile mở **Hành trình đang diễn ra**: vẫn cùng phiếu DAL-001, không đặt lại lịch/check-in. Màn Đang khám đối chiếu bác sĩ hiện tại và Phòng 08 - Da liễu; tải lại nếu cần.
6. Chỉ khi đủ điều kiện chuyên môn mới hoàn tất theo luồng khám. Bệnh nhân bàn giao 02 là ví dụ ca không CLS đã hoàn tất sau nhận bàn giao.

## Từ chối hoặc hủy

1. Bác sĩ nhận bấm **Từ chối**, nhập lý do và xác nhận. Yêu cầu **Đã từ chối**, bác sĩ cũ vẫn phụ trách.
2. Người gửi/người khởi tạo bấm **Hủy yêu cầu**, nhập lý do và xác nhận. Yêu cầu **Đã hủy**, không còn nút nhận.
3. Điều phối chuyên môn đang có quyền cùng khoa/phòng cũng được hủy yêu cầu chờ mắc kẹt; lịch sử ghi đúng người hủy và lý do, không chuyển trách nhiệm.
4. Yêu cầu đã từ chối/hủy không được tiếp nhận lại. Muốn tiếp tục, tạo một yêu cầu hợp lệ mới.

## ADMIN cấp quyền và điều phối ngoại lệ

1. ADMIN vào `/admin/handovers`, chọn đúng bác sĩ C tại ROOM-08 từ danh mục và nhập lý do, bấm **Cấp quyền điều phối**.
2. C tải lại trang bàn giao. Ngoài ca của mình, C thấy các ca mở cùng khoa/phòng để khởi tạo ngoại lệ và các yêu cầu chờ cùng phạm vi để hủy khi cần.
3. Khi B không thể tự gửi, C chọn ca đang thuộc B, chọn bác sĩ đủ điều kiện khác, nhập lý do/SBAR và gửi.
4. Hệ thống ghi rõ **Tiếp quản ngoại lệ do điều phối chuyên môn khởi tạo**, nguồn B, người khởi tạo C; không giả danh B.
5. Bác sĩ nhận vẫn phải tự xác nhận như luồng thường. ADMIN/C không nhận thay người đó.
6. ADMIN có thể **Thu hồi quyền** kèm lý do. Nếu quyền bị thu hồi trước khi nhận yêu cầu ngoại lệ, yêu cầu đó không được chấp nhận; hủy và tạo lại từ người có thẩm quyền.

Đổi phân công phòng ở quản lý cơ sở không chuyển chủ ca. Server đối chiếu phòng thật của phiếu Queue cả lúc gửi và nhận. Một phòng nhiều bác sĩ cần cấu hình đúng một bác sĩ chính nhận lịch mới; cấu hình đó không thay lịch/ca đang có.

## Khôi phục mẫu để chạy lại

1. Nếu C đang phụ trách, C gửi ca về B.
2. B đăng nhập phiên riêng, đọc lại nội dung và tự xác nhận tiếp nhận.
3. Kiểm tra mobile vẫn cùng hành trình; B đọc được các ghi chú/toa trước mà tác giả không đổi.
4. Nếu vừa thử thu hồi quyền C, ADMIN cấp lại có lý do để giữ bộ demo hiện tại.
5. Đăng xuất các phiên dùng để kiểm tra. Lịch sử gửi/nhận/từ chối/hủy giữ lại; không xóa/reset bằng SQL.

Trang bàn giao dùng **Tải lại thủ công**, chưa gửi thông báo đẩy. Khi 409, kiểm tra ca đã đóng/đổi chủ, yêu cầu đang chờ, người nhận bận hoặc phân công/quyền điều phối đã thay đổi. 503 nghĩa là chưa xác minh được authority/Directory/Queue; không cho đi tiếp bằng cách bỏ kiểm tra. Xem thêm [trang xử lý lỗi](09-ngoai-le-va-xu-ly-loi.md).

Hoàn tất Appointment sau commit Consultation có retry idempotent nhưng chưa có durable outbox tự khôi phục mọi lỗi liên dịch vụ. Ca DAL lịch sử này không đại diện bộ E2E CXK mới; CLS/phát thuốc của CXK đã kiểm tra, thanh toán thật vẫn chưa nghiệm thu.

**R3:** bàn giao hỗ trợ cả ca không hẹn đang mở/chờ CLS, đối chiếu phòng từ phiếu gốc. Bác sĩ nhận vẫn phải cùng khoa/phòng và tự chấp nhận. Tác giả toa/chỉ định giữ nguyên; toa nháp của người giao chỉ đọc, bác sĩ nhận lập toa của mình. Không cần tạo Appointment để bàn giao. Xem [trang20](20-ho-so-va-hanh-trinh-lam-sang-r3.md).
