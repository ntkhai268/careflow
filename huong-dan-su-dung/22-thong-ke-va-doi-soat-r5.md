# 22 — Thống kê lượt khám và đối soát R5

[Quay về mục lục](README.md) · [Tài khoản demo](01-tai-khoan-va-du-lieu.md)

## Phạm vi phiên bản

Ngày21/10/2026, backend/web ở worktree `security-integration`, HEAD `5a8c6e5` cộng thay đổi R0–R5 chưa commit. R5 đã chạy trên stack kiểm thử Mac với PostgreSQL/RabbitMQ thật; không triển khai lên remote/production. R5 không cần cài lại iPhone. Bản iOS1.0.0(20) vẫn giữ nguyên; nghiệm thu push Firebase/APNs của R4 còn mở.

Analytics Service độc lập nhận tín hiệu sự kiện và đọc snapshot tối thiểu qua API riêng của Appointment, Queue, Consultation. Đây là số liệu vận hành thực lấy từ DB của môi trường đang mở; môi trường test còn chứa các lượt demo/nghiệm thu trước đây, không phải báo cáo hoạt động của bệnh viện thật. Bộ seed riêng của Analytics chỉ dùng khi bật demo rõ ràng, luôn có nhãn tổng hợp, không trộn vào REAL.

## Điều kiện bắt đầu

- Mở đúng stack theo [chuẩn bị môi trường](00-chuan-bi.md). Có Analytics ở cổng nội bộ HTTPS18091, Gateway18080 và Hospital Web5173. Trên Mac này, chạy `/usr/bin/python3 scripts/mac-demo/demo.py status`:15 đầu kiểm tra phải OK, gồm Analytics, Identity phụ và hai giao diện.
- Dùng tài khoản ADMIN tại trang01 trong hồ sơ trình duyệt riêng. DOCTOR/STAFF/PATIENT/LAB_TECHNICIAN không có quyền xem hoặc chạy đối soát/dựng lại.
- Sau thay đổi source ở checkout khác phải build/deploy đúng bộ backend/web; chỉ sao chép tài liệu không làm xuất hiện tính năng.

## Xem báo cáo

1. Mở http://localhost:5173/admin/analytics hoặc chọn **Thống kê lượt khám** ở menu quản trị.
2. Chọn **Từ ngày**, **Đến ngày**, tối đa93 ngày gồm hai đầu. Mặc định7 ngày theo múi giờ Việt Nam. Chọn khoa/phòng từ danh mục thật; gõ để tìm, không nhập giá trị mới. Đổi khoa sẽ bỏ lựa chọn phòng trước đó.
3. Bấm **Xem báo cáo**. Kiểm tra số đặt/đến/hoàn tất/hủy/từng lỡ, lượt không hẹn, thời gian chờ, biểu đồ theo ngày và bảng khoa/phòng.
4. Đọc **Độ mới và chất lượng dữ liệu**: lần đối soát đủ của từng nguồn, snapshot gần nhất, số mẫu bị loại và ca thiếu liên kết. Cảnh báo xuất hiện khi nguồn chưa SYNCED hoặc lần đối soát đủ đã quá3 phút. Số hiện có khi đó chỉ là dữ liệu đã nhận, không chứng minh nguồn đã đầy đủ.
5. Lặp lại bằng khoảng ngày/khoa/phòng khác. Một khoảng thực sự trống hiển thị “Không có lượt”; lỗi kết nối có thông báo và **Tải lại báo cáo**, không đổi lỗi thành số0. Truy cập cùng URL bằng vai trò khác phải thấy trang403, không có bảng số liệu.

## Đọc đúng các chỉ số

| Chỉ số | Ý nghĩa |
|---|---|
| Đặt/hủy | Một lịch trong ngày khám đã chọn; tỷ lệ hủy chia tổng lịch, mẫu số0 hiển thị dấu gạch |
| Đến khám | Phiếu INITIAL đã check-in; tách có hẹn/không hẹn. Phiếu WAITING của ngày mai không phải lượt đến hôm nay |
| Hoàn tất | Ca COMPLETED, gộp theo lượt ban đầu; lượt đọc kết quả và bàn giao không cộng thêm một ca |
| Lượt từng lỡ / lần lỡ | Một phiếu INITIAL từng lỡ chỉ tính một lượt; số lần lỡ có thể lớn hơn, gồm cả lượt đọc kết quả |
| Chờ ban đầu/đọc kết quả | Khoảng hợp lệ đến bắt đầu phục vụ. Sau lỡ/xếp lại dùng mốc chờ mới; không phải tổng mọi lần chờ |
| Thời gian toàn ca | Hoàn tất trừ bắt đầu, có cả thời gian CLS; không phải thời gian bác sĩ thao tác trực tiếp |
| P50/P95 | Phân vị nội suy tuyến tính từ mẫu hợp lệ; hiển thị cùng số mẫu, không suy từ giá trị0 |

Ca qua nửa đêm vẫn thuộc ngày lượt ban đầu nếu liên kết còn. Thiếu liên kết dùng ngày ca và báo chất lượng. Mốc thời gian âm/thiếu bị loại khỏi trung bình, không giả thành0 phút. Analytics giữ snapshot đã quan sát, nhưng không phục hồi được lịch sử nguồn bị xóa trước lần đầu quan sát.

## Đối soát và dựng lại

1. ADMIN bấm **Đối soát nguồn** để đọc lại snapshot phân trang của ba nguồn. Không sửa lịch, hàng đợi, bệnh án hoặc toa. Đồng bộ chạy nền mặc định mỗi60 giây; sự kiện giúp cập nhật sớm, không cam kết thời gian giao tuyệt đối.
2. Chỉ coi nguồn đã đầy đủ khi SYNCED và lần đối soát đủ còn mới. PARTIAL là còn trang; DEGRADED là đọc nguồn lỗi. Giữ số cũ khi nguồn lỗi; khôi phục service rồi đối soát lại. Nếu có lượt đối soát đang chạy, chờ hoàn tất rồi thử lại.
3. Bấm **Dựng lại từ nhật ký** khi cần tái tạo projection từ lịch sử Analytics đã chấp nhận. Thao tác có audit ADMIN; không xóa journal/receipt hoặc sửa nguồn. Sau đó cùng bộ lọc phải cho cùng kết quả nếu không có hoạt động nghiệp vụ mới.
4. Rebuild không thay thế backfill: bản ghi chưa từng được Analytics nhận chỉ có thể được bổ sung từ nguồn còn giữ bản ghi. Không reset Queue hoặc sửa SQL để ép số đúng.

## Cấu hình môi trường mới — dành cho vận hành

Dùng `docker-compose.security.yml`; không chạy lệnh khởi động production từ Mac test. Cần database riêng, user DB chỉ có quyền Analytics, tài khoản RabbitMQ với quyền các queue/binding Analytics, certificate client/server CN `analytics-service`, truststore/CA và secret riêng `ANALYTICS_CALLER_KEY`. Khóa này chỉ dùng cho Analytics và quyền export của ba nguồn, không thay Resend/JWT hoặc khóa service khác.

Cấp `ANALYTICS_DB_*`, `ANALYTICS_RABBITMQ_*`, `ANALYTICS_TLS_PASSWORD` và các biến bảo mật chung trong môi trường private. Compose secure gán export key cho ba nguồn và giữ `ANALYTICS_DEMO_ENABLED=false`. Identity `SECURITY_CALLERS_JSON` phải đăng ký caller `analytics-service`; Gateway và Analytics có scope `GET:/api/analytics/**`, `POST:/api/analytics/reconcile`, `POST:/api/analytics/rebuild`. ADMIN vẫn được kiểm tra từ phiên đã xác minh. Không công khai `/internal/analytics/records`, không cấp quyền đọc DB nguồn trực tiếp cho Analytics. Không đưa các giá trị secret/config private lên Git.

Mac hiện có dùng `/usr/bin/python3` trong hai launcher `.command` để phù hợp chuỗi chứng thư của bộ runtime test. Không tắt xác minh CA/hostname. Sao lưu mới bằng `/usr/bin/python3 scripts/mac-demo/demo.py backup` sau khi cập nhật runtime. Launcher mở artifact đã được chuẩn bị, không tự tạo cấu hình/credential hoặc build service mới.

## Nghiệm thu và giới hạn

Đã kiểm tra PostgreSQL thật, dataset biết trước, trùng/đến muộn/cạnh tranh consumer, cursor tiếp tục sau lỗi, rebuild/restart, sự kiện đặt/hủy lịch thực, chín chỉ số đối chiếu độc lập với nguồn, API sai quyền và8 ca browser. Lỗi nguồn xác định được thử bằng adapter giả trong giao dịch PostgreSQL; lỗi UI503 được chặn response có chủ đích chỉ trong kiểm thử. Đây không phải thao tác ngắt hệ thống production.

Báo cáo và bằng chứng: [biên bản R5](../.planning/analytics-service/r5-acceptance-2026-10-21.md). Chưa nghiệm thu tải lớn, chạy nhiều máy, build/chạy image production hoặc reboot/restore toàn Mac. Không cần giấy tờ hay dữ liệu thật để thử R5; dùng tài khoản trang01 và tạo lượt mới qua giao diện/API có quyền, chỉ hủy lịch thử vừa tạo khi dọn bài kiểm tra.
