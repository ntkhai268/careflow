# Kiểm chứng tích hợp VNPT eKYC — 19/10/2026

> Lịch sử, đã được thay thế: VNPT ngừng từ bản15 theo yêu cầu người dùng. Adapter đã bỏ, API cũ410. Contract hiện hành: [đọc trên máy và đối chiếu trực tiếp](local-document-review-contract.md). Các số liệu phía dưới chỉ đúng tại mốc kiểm tra cũ.


## Kết quả

Đã triển khai adapter VNPT REST ở Identity và giao diện eKYC Flutter native; cài và mở **CareFlow 1.0.0 (12)** trên iPhone 11 Pro Max. Hai instance Identity của môi trường test đã chạy artifact mới, health UP qua TLS/mTLS; V15 áp dụng trên DB loopback thuộc stack test hiện có. Không thay DB remote, key Resend, cấu hình AI, chính sách Face ID hoặc quyền hồ sơ bệnh án. Không commit/stage/push.

**Chưa nghiệm thu VNPT thật.** Chưa có Token ID/Token Key/Access token và xác nhận quyền API của tài khoản Free. Provider disabled theo mặc định; chưa gửi ảnh tới VNPT hoặc phát sinh request VNPT. KYC-01 vẫn IN_PROGRESS, không coi fixture/mock là bằng chứng định danh thật.

## Thay đổi

- Mobile: Tài khoản → Xác minh danh tính; camera hai mặt căn cước và selfie; đồng ý gửi ảnh trước khi gọi; trạng thái server; không chứa khóa VNPT. Xóa ảnh camera tạm sau khi đọc, bỏ bộ nhớ ảnh khi có kết quả hoặc rời màn hình.
- Backend: ràng buộc PATIENT từ security principal, lấy owner từ phiên xác thực; đủ kiểm tra giấy tờ/OCR/liveness/face match mới VERIFIED; phản hồi thiếu bằng chứng không được pass; chỉ trả số giấy tờ đã che. Không tự bind/merge hồ sơ bệnh án.
- Giới hạn durable tối đa100 request outbound/tháng, gồm upload và lỗi; không retry/redirect; chống gửi trùng bằng khóa UUID và dấu vân tay bộ ảnh; ba yêu cầu mới/tài khoản/24giờ. Một luồng đủ các bước tối đa8 calls, không hứa100 lần xác minh miễn phí.
- Legacy endpoint một ảnh trả410; metadata OpenAPI và contract cũ được đối chiếu lại. Schema V15 và DBML đồng bộ. Không seed danh tính đã xác minh giả; bảng bằng chứng/quota chỉ phát sinh theo yêu cầu thực.
- Phát hiện qua live test: method-security denial bị exception advice chung chuyển thành500. Đã đổi controller eKYC mới sang guard kiểm tra role tường minh, trả BusinessException403; không sửa global authentication logic. Live retest403 đạt.

## Kiểm thử

| Phạm vi | Kết quả | Giới hạn |
|---|---|---|
| Maven verify Common + Identity | **185 đạt**, 0 lỗi, 0 bỏ qua | 46 Common +139 Identity; không phải chạy lại toàn bộ754 test backend đợt1 |
| Test eKYC mới trong lượt Maven | **33 đạt** |15 service,6 HTTP fixture,7 PostgreSQL,5 role/owner; đã nằm trong185 |
| PostgreSQL eKYC |7 đạt; migrate V1–V15 trên DB riêng `careflow_vnpt_ekyc_test` |Kiểm tra cạnh tranh130 reservation chỉ100 thắng, khóa yêu cầu, đổi ảnh, giới hạn24h, owner isolation và gián đoạn; không dùng remote/runtime DB để truncate |
| Flutter toàn bộ |**211 đạt,13 bỏ qua** |13 skip giữ nguyên:11 fixture cần địa chỉ riêng,1 download HTTP fixture và1 demo test cũ. Lượt này không chạy lại những fixture có điều kiện;4 widget eKYC mới đã đạt |
| Flutter analyze |0 error/warning,2 info cũ |Style info ở2 test cũ; không thêm info ở source mới |
| Live gateway TLS/mTLS |**4 nhóm đạt** |PATIENT đọc status unavailable/latest null; ADMIN403; chưa cấu hình POST503; anonymous POST401. Không gọi VNPT |
| iOS Release |Build12, codesign verify, install và launch đạt |Installed-app inventory xác nhận1.0.0/bundleVersion12. Chưa thao tác camera hoặc liveness bằng người thật |

Log cục bộ ngoài Git: `/tmp/careflow-vnpt-20261019/` (`backend-final.log`, `mobile-tests.log`, `mobile-analyze-final.log`, `live-results.json`, `ios-build.log`, `ios-install.json`, `ios-launch.json`, `ios-installed-apps.json`). Snapshot DB trước V15, state và cấu hình provider ở thư mục Developer riêng ngoài Git; không đưa nội dung khóa vào báo cáo.

## GitNexus và phạm vi

Đã pull `origin main` trên hai nhánh feature trước khi sửa; up to date. Impact upstream trước khi sửa verify/EkycService: LOW, một caller trực tiếp `AuthController.mockEkyc`; class import/injection2 điểm. UI AccountScreen.build và appRouter: UNKNOWN/không giải quyết caller theo graph; đã kiểm tra source route và widget, không coi0 edges là không có ảnh hưởng. Các endpoint/bean OpenAPI và test legacy được impact trước sửa.

Đã refresh index và chạy detect-changes ở cả hai worktree. **Cảnh báo tổng: CRITICAL backend, HIGH mobile**, do toàn bộ diff chưa commit gồm nhiều đợt bảo mật/tính năng trước (185 file/778 symbol backend;30 file/91 symbol mobile ở mốc chạy). Báo cáo tổng không cô lập thay đổi eKYC và không phải chứng nhận an toàn để commit toàn bộ worktree. Không stage/commit; các sửa source mới giới hạn Identity eKYC, entry route/menu/camera mobile và tài liệu liên quan. Các thay đổi cũ được giữ nguyên.

## Còn thiếu để nghiệm thu

Tài khoản VNPT Free và xác nhận đủ entitlement; response thật đúng contract tenant; hiệu quả passive liveness/chụp camera trên iPhone; điều kiện xử lý/lưu trữ ảnh tại VNPT. Chưa tích hợp SDK native VNPT, active liveness/video/NFC hoặc đối soát cơ sở dữ liệu căn cước. Không gọi tính năng này production-ready trước các bước đó.

Contract: [VNPT adapter](vnpt-ekyc-contract.md). Hướng dẫn người dùng duy nhất: [trang15](../../huong-dan-su-dung/15-vnpt-ekyc.md).
