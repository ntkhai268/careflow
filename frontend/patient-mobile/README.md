# CareFlow Patient Mobile

## Chạy ứng dụng

Từ thư mục `frontend/patient-mobile`, cài dependency bằng `flutter pub get`, sau
đó chọn một chế độ:

```text
flutter run --dart-define=DEMO_MODE=true
flutter run --dart-define=DEMO_MODE=false
flutter run --dart-define=DEMO_MODE=true --dart-define=API_BASE_URL=<gateway>
```

- `DEMO_MODE=true` bật hành trình khám mô phỏng. Đăng nhập, chọn hồ sơ bệnh
  nhân, rồi tạo hoặc mở một lịch hẹn thật từ API. Các bước sau lịch hẹn được
  lưu cục bộ, tách biệt theo tài khoản và bệnh nhân.
- `DEMO_MODE=false` tắt dữ liệu và điều khiển mô phỏng. Khi backend hành trình
  chưa sẵn sàng, ứng dụng hiển thị trạng thái chưa khả dụng.
- `API_BASE_URL` thay gateway cho các API thật. Nếu bỏ qua, ứng dụng dùng
  gateway mặc định trong `ApiConfig`.

Đăng nhập/đăng ký, hồ sơ bệnh nhân, lịch hẹn và hồ sơ sức khỏe luôn gọi API thật
qua gateway, kể cả khi `DEMO_MODE=true`. Chỉ phần hành trình sau khi lịch hẹn API
đã tạo hoặc tải thành công mới dùng adapter mô phỏng cục bộ.

## Luồng thanh toán Mobile MVP

Trong `DEMO_MODE=true`, Mobile mô phỏng hai thời điểm thanh toán theo quyết định
mới nhất trên `develop`:

1. Khi đặt lịch, bệnh nhân trả trước phí khám thường (fixture hiện tại:
   150.000 VND) bằng online mock hoặc chọn tiền mặt tại bệnh viện.
2. Sau khi bác sĩ kết luận/kê toa, hệ thống lập **quyết toán cuối lượt khám**:
   tổng hợp phí khám, xét nghiệm và thuốc rồi trừ khoản đã trả trước.

Xét nghiệm được đưa thẳng vào hàng đợi, không có màn hình thanh toán xét nghiệm
riêng. Toa thuốc được đưa vào hàng đợi nhà thuốc, không có màn hình thanh toán
tiền thuốc riêng. Chỉ trạng thái `PAYMENT_DUE` chặn phát thuốc; `SETTLED`,
`REFUND_PENDING` và `REFUNDED` đều cho phép phát thuốc.

Trong demo, màn hình **Quyết toán lượt khám** mô phỏng số tiền còn phải trả,
online mock/tiền mặt, hoàn khoản dư và xác nhận đã nhận thuốc. Khi
`DEMO_MODE=false`, ứng dụng không ghi biên lai thanh toán giả trên thiết bị và
chỉ hiển thị hướng dẫn thanh toán theo bệnh viện cho đến khi Visit Settlement
Service được kết nối.

## Chạy hành trình khám đầy đủ

Trong chế độ demo, vào tab lịch hẹn, mở một lịch hẹn và chọn **Hành trình khám**.
Thẻ **Điều khiển mô phỏng** chỉ cung cấp sự kiện hợp lệ tiếp theo của nhân viên
hoặc bác sĩ. Nút ngữ cảnh cho phép mở phiếu khám, hàng đợi, khám bệnh, xét
nghiệm, đọc kết quả, quyết toán hoặc kết quả lượt khám.

Nút **Đặt lại hành trình** yêu cầu xác nhận và chỉ xóa dữ liệu của lịch hẹn đang
hoạt động.

Từ Trang chủ, mở **Kết quả khám và toa thuốc** để xem danh sách các lượt khám đã
hoàn tất. Mỗi lượt hiển thị tóm tắt chẩn đoán, toa thuốc và chỉ định xét nghiệm;
chọn **Xem kết quả và toa thuốc** để mở chi tiết lượt khám.

## Kiểm tra và build APK demo

```text
flutter test --no-pub
flutter analyze --no-pub
flutter build apk --debug --dart-define=DEMO_MODE=true
```

APK debug được tạo tại `build/app/outputs/flutter-apk/app-debug.apk`.

## Chạy hybrid demo với Gateway local và Android thật

Hướng dẫn đầy đủ nằm tại
[`../../docs/local-patient-mobile-demo.md`](../../docs/local-patient-mobile-demo.md).

```text
adb reverse tcp:8080 tcp:8080
flutter run \
  --dart-define=DEMO_MODE=true \
  --dart-define=API_BASE_URL=http://127.0.0.1:8080/api \
  --dart-define=WS_BASE_URL=ws://127.0.0.1:8080/ws
```
