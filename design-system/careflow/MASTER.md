# CareFlow Mobile Design System

> Nguồn chuẩn cho Patient Mobile. Khi làm một màn hình, đọc file này trước,
> sau đó áp dụng override trong `pages/<page>.md` nếu có.

**Sản phẩm:** Ứng dụng hỗ trợ hành trình khám tại bệnh viện công Việt Nam  
**Nền tảng:** Flutter, Android/iOS  
**Đối tượng:** Bệnh nhân đa độ tuổi, gồm người lớn tuổi và người ít thành thạo công nghệ  
**Ngữ cảnh:** Thao tác nhanh trong bệnh viện đông người, có áp lực thời gian và kết nối không ổn định

## 1. Nguyên tắc trải nghiệm

1. **Task-first:** ưu tiên việc bệnh nhân cần làm ngay: đặt khám, xem phiếu, tìm phòng, theo dõi lượt, xem chỉ định và kết quả.
2. **Một trạng thái chính mỗi màn hình:** luôn trả lời rõ “Tôi đang ở bước nào?” và “Tôi cần làm gì tiếp theo?”.
3. **Appointment khác Visit Ticket:** lịch đặt khám là giao dịch đặt chỗ; phiếu khám là tài liệu vận hành được Queue Service cấp.
4. **Không dùng màu làm tín hiệu duy nhất:** mọi trạng thái phải có nhãn và icon đi kèm.
5. **Ngôn ngữ đời thường:** tránh thuật ngữ kỹ thuật như event, projection, queue entry hoặc encounter trên mobile.
6. **Progressive disclosure:** thông tin quan trọng xuất hiện trước; chi tiết hành chính/lâm sàng mở rộng khi cần.
7. **Tin cậy hơn trang trí:** không dùng gradient neon, glassmorphism, hiệu ứng 3D hoặc animation phô diễn.

## 2. Hướng hình ảnh

**Phong cách:** Accessible & Ethical + Minimalism + Trust & Authority.  
**Độ biến thiên:** 3/10 — nhất quán, dễ đoán.  
**Chuyển động:** 3/10 — chỉ dùng để phản hồi thao tác và duy trì liên tục không gian.  
**Mật độ:** 5/10 — đủ thoáng để đọc nhưng không lãng phí màn hình.

### Màu semantic

| Token | Màu | Vai trò |
|---|---:|---|
| `primary` | `#0277A8` | CTA chính, điều hướng đang chọn |
| `onPrimary` | `#FFFFFF` | Nội dung trên primary |
| `primaryContainer` | `#DDF3FC` | Khối thông tin chính, selected state |
| `secondary` | `#087F6A` | Hành động phụ liên quan sức khỏe |
| `success` | `#1B7F4B` | Hoàn tất, đã xác nhận |
| `warning` | `#A86100` | Đang chờ, cần chú ý |
| `error` | `#BA1A1A` | Lỗi, hủy, cảnh báo nguy hiểm |
| `info` | `#1D5FA7` | Thông tin hướng dẫn |
| `background` | `#F5F8FA` | Nền ứng dụng |
| `surface` | `#FFFFFF` | Card, sheet, input |
| `surfaceMuted` | `#EDF2F5` | Khối phụ/disabled |
| `textPrimary` | `#17242D` | Nội dung chính |
| `textSecondary` | `#4D626E` | Nội dung phụ, vẫn bảo đảm tương phản |
| `border` | `#D5E0E6` | Border/divider |

- CTA chính dùng `primary`; không dùng success green làm CTA mặc định.
- Status có nền tint nhẹ, icon và text đậm; không hiển thị chữ màu nhạt trực tiếp trên nền trắng.
- Light mode là mặc định. Dark mode chỉ phát hành sau khi được kiểm thử độc lập.

## 3. Typography

**Heading:** Be Vietnam Pro  
**Body:** Be Vietnam Pro, fallback Noto Sans/system sans-serif

| Style | Size/line | Weight | Dùng cho |
|---|---:|---:|---|
| Display | 30/38 | 700 | Số thứ tự, thông tin cần nhìn từ xa |
| Headline | 24/32 | 700 | Tiêu đề màn hình |
| Title large | 20/28 | 600 | Tiêu đề section/card |
| Title medium | 17/24 | 600 | Item title |
| Body large | 16/24 | 400 | Nội dung chính |
| Body medium | 14/21 | 400 | Nội dung phụ |
| Label | 14/20 | 600 | Button/chip |
| Caption | 12/18 | 500 | Metadata không trọng yếu |

- Hỗ trợ text scaling ít nhất 200%; không khóa `textScaleFactor`.
- Body quan trọng không nhỏ hơn 14sp; nội dung hướng dẫn ưu tiên 16sp.
- Không dùng ALL CAPS cho câu dài.

## 4. Layout và spacing

- Grid 4/8dp: `4, 8, 12, 16, 24, 32, 48`.
- Gutter điện thoại: 16dp; màn hình >=600dp: 24–32dp và giới hạn content width.
- Card radius 16dp; input/button radius 12dp; chip radius 999dp.
- Card mặc định dùng border 1dp và shadow rất nhẹ; không lồng nhiều card có shadow.
- Mọi màn hình tôn trọng `SafeArea`; nội dung cuộn phải có bottom inset cho navigation/CTA.

## 5. Component contract

### Button

- Cao tối thiểu 48dp, hit target tối thiểu 48×48dp trên Android.
- Mỗi màn hình tối đa một primary CTA nổi bật.
- Loading giữ nguyên kích thước button, khóa double submit và có nhãn trạng thái.
- Disabled phải giảm nhấn mạnh và thực sự không nhận tap.

### Card/list item

- Toàn item có thể tap khi dẫn tới cùng một đích; không đặt nhiều vùng tap mơ hồ.
- Có pressed/ripple feedback trong 80–150ms.
- Dùng `ListView.builder` và `ValueKey` cho danh sách động.

### Form

- Label luôn hiển thị; placeholder chỉ là ví dụ.
- Error đặt ngay dưới field bằng tiếng Việt và nói cách khắc phục.
- Dùng bàn phím đúng kiểu dữ liệu và giữ CTA nhìn thấy khi bàn phím mở.

### Status

- Cấu trúc: icon + nhãn trạng thái + mô tả hành động tiếp theo.
- Skeleton cho tải lần đầu; refresh giữ dữ liệu cũ nếu có.
- Empty state nói rõ nguyên nhân và cung cấp một hành động phù hợp.
- Error kỹ thuật không được hiển thị nguyên văn `DioException` cho bệnh nhân.

## 6. Navigation và kiến trúc thông tin

Bottom navigation tối đa 5 mục:

1. **Trang chủ** — việc cần làm và hành trình đang diễn ra.
2. **Lịch khám** — lịch sắp tới, lịch sử và phiếu khám liên quan.
3. **Hồ sơ** — thông tin bệnh nhân, hồ sơ tải lên, lịch sử y tế.
4. **Thông báo** — inbox thật từ Notification Service.
5. **Tài khoản** — bảo mật, thiết bị, hỗ trợ và đăng xuất.

- “Phiếu khám” không phải một silo tách rời lịch khám; nó là tài liệu con của Appointment/Journey.
- Khi có hành trình đang hoạt động, Trang chủ hiển thị một status card lớn dẫn thẳng đến bước hiện tại.
- Back phải quay lại đúng ngữ cảnh trước đó; route hành trình hỗ trợ deep link theo appointment ID.

## 7. Motion và feedback

- Thời lượng 150–300ms, dùng easing Material mặc định.
- Chỉ animate opacity/position nhỏ cho chuyển trạng thái; không animate layout lớn.
- Haptic chỉ dùng cho xác nhận quan trọng, check-in hoặc lỗi cần chú ý; không rung mỗi tap.
- Tôn trọng reduced motion.

## 8. Accessibility bắt buộc

- Contrast: text thường >=4.5:1; glyph lớn >=3:1.
- Icon-only button phải có tooltip/semantic label.
- Focus order trùng thứ tự thị giác; nhóm nội dung phức tạp bằng `Semantics`.
- Touch target >=48×48dp và khoảng cách giữa hai mục tiêu >=8dp.
- Kiểm thử TalkBack, text scaling 200%, màn hình hẹp 360–375px và landscape.
- QR luôn có mã/chỉ dẫn dạng chữ làm phương án thay thế.

## 9. Anti-patterns

- Dịch vụ giả hoặc chưa hỗ trợ xuất hiện như thể có thể thao tác.
- Grid 4 cột với nhãn dài và hit target nhỏ.
- App bar xanh phủ toàn màn hình, gradient quá sáng hoặc bóng đổ nặng.
- Chữ xám nhạt dưới 4.5:1, body 12sp, icon không nhãn.
- Chỉ báo tiến trình dài nhưng không nói bước tiếp theo.
- Hiển thị dữ liệu demo trong production hoặc trộn mock với API mà không có ranh giới.
- Dùng emoji làm icon hệ thống.

## 10. Definition of Done cho một màn hình

- Dùng token theme, không hardcode màu/font tùy màn hình.
- Có loading, empty, error, success và offline/retry hợp lý.
- Touch target, semantics và text scaling đạt yêu cầu.
- Không overflow ở 360px và text scale 200%.
- Widget test cho trạng thái chính và hành động quan trọng.
- `flutter analyze` sạch; toàn bộ test pass.
