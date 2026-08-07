# CareFlow Patient Mobile

## Chạy ứng dụng

Từ thư mục `frontend/patient-mobile`, cài dependency bằng `flutter pub get`, sau
đó chọn một trong các chế độ:

```text
flutter run --dart-define=DEMO_MODE=true
flutter run --dart-define=DEMO_MODE=false
flutter run --dart-define=DEMO_MODE=true --dart-define=API_BASE_URL=<gateway>
```

- `DEMO_MODE=true` bật hành trình khám mô phỏng. Đăng nhập, chọn hồ sơ bệnh
  nhân, rồi tạo hoặc mở một lịch hẹn thật từ API. Phiếu khám và các bước tiếp
  theo của lịch hẹn đó được lưu cục bộ, tách biệt theo tài khoản và bệnh nhân.
- `DEMO_MODE=false` tắt toàn bộ dữ liệu và điều khiển hành trình mô phỏng. Khi
  backend hành trình chưa sẵn sàng, ứng dụng hiển thị thông báo không khả dụng
  và không tự chuyển sang dữ liệu demo.
- `API_BASE_URL` đổi gateway cho các API thật. Nếu không truyền giá trị này,
  ứng dụng dùng gateway mặc định trong `ApiConfig`.

Đăng nhập/đăng ký, hồ sơ bệnh nhân, lịch hẹn và hồ sơ sức khỏe luôn gọi API
thật qua gateway, kể cả khi `DEMO_MODE=true`. Chỉ phần hành trình sau khi lịch
hẹn API đã được tạo hoặc tải thành công mới dùng mô phỏng cục bộ.

## Chạy hành trình khám đầy đủ

Trong chế độ demo, vào tab lịch hẹn, mở một lịch hẹn và chọn **Hành trình
khám**. Thẻ **Điều khiển mô phỏng** xuất hiện trong màn hình **Hành trình
khám** và chỉ cung cấp sự kiện hợp lệ tiếp theo của nhân viên hoặc bác sĩ.
Dùng nút ngữ cảnh phía trên thẻ để mở phiếu khám, hàng đợi, khám bệnh, xét
nghiệm, đọc kết quả hoặc kết quả lượt khám. Ở bước chờ thanh toán, mở
**Xem xét nghiệm** và chọn một phương thức thanh toán, sau đó quay lại màn hình
hành trình để tiếp tục mô phỏng.

Nút **Đặt lại hành trình** yêu cầu xác nhận và chỉ xóa hành trình của lịch hẹn
đang hoạt động. Dữ liệu của tài khoản, bệnh nhân và lịch hẹn khác không bị xóa.

Từ Trang chủ, mở **Kết quả khám và toa thuốc** để xem danh sách các lượt khám đã
hoàn tất. Mỗi lượt hiển thị tóm tắt chẩn đoán, toa thuốc và chỉ định xét nghiệm;
chọn **Xem kết quả và toa thuốc** để mở chi tiết lượt khám.

## Kiểm tra và build APK demo

```text
flutter test
flutter analyze
flutter build apk --debug --dart-define=DEMO_MODE=true
```

APK debug được tạo tại `build/app/outputs/flutter-apk/app-debug.apk`.

## Chạy hybrid demo với Gateway local và điện thoại Android thật

Hướng dẫn đầy đủ nằm tại
[`../../docs/local-patient-mobile-demo.md`](../../docs/local-patient-mobile-demo.md).

Lệnh chạy nhanh sau khi năm container core đã `UP`:

```text
adb reverse tcp:8080 tcp:8080
flutter run \
  --dart-define=DEMO_MODE=true \
  --dart-define=API_BASE_URL=http://127.0.0.1:8080/api \
  --dart-define=WS_BASE_URL=ws://127.0.0.1:8080/ws
```

`DEMO_MODE=true` không mock Auth, Patient, khoa, ca khám hoặc Appointment. Không
truyền `API_BASE_URL` sẽ dùng Gateway public mặc định, không phải Gateway local.
