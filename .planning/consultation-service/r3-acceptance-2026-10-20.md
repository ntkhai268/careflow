# R3 — Biên bản triển khai và kiểm chứng, 20/10/2026

**Kết luận:** đã triển khai R3-01–06 và kiểm chứng backend/web trên stack local thật; native đã qua regression và build/ký iOS1.0.0(18). **Chưa nghiệm thu trọn vẹn R3 trên điện thoại**: iPhone hiện có tunnel `unavailable`, chưa xác nhận cài/mở bản18 và thao tác picker/xem/chia sẻ. Bản17 đã cài ở R2. Không coi unit/widget/API là bằng chứng cảm biến hoặc UI iOS vật lý.

Bằng chứng tổng hợp: [results.json](r3-evidence-2026-10-20/results.json), [hash artifact](r3-evidence-2026-10-20/artifacts.json). Hướng dẫn thao tác duy nhất: [trang20](../../huong-dan-su-dung/20-ho-so-va-hanh-trinh-lam-sang-r3.md). Tài liệu này ghi kết quả/giới hạn kiểm thử, không thay hướng dẫn.

## Phiên bản và môi trường

- Backend/web: worktree `security-integration`, nhánh `feature/dangkhoii/security-integration`, HEAD `5a8c6e5` cộng thay đổi chưa commit từ các đợt trước và R3.
- Native: worktree `patient-mobile-ios-login`, nhánh `feature/dangkhoii/patient-mobile-ios-login`, HEAD `e5fc9c1` cộng thay đổi chưa commit. Chỉ dùng native từ worktree này; không lấy bản cũ ở worktree backend.
- Đã pull `origin/main` tại hai worktree trước triển khai; không có cập nhật cần nhận. Không add/commit/push và không sửa nhánh main.
- Stack local thuộc phiên làm việc, PostgreSQL Docker/loopback15432, RabbitMQ15673; không reset dữ liệu máy chủ DB chung. Có backup source, state private, artifact và bốn DB trước R3. Không sửa SQL trạng thái lâm sàng để ép test đạt.
- Dữ liệu nghiệm thu đều tổng hợp **SYNTHETIC**, không dùng CCCD/bệnh án thật. Credentials chỉ ở tài liệu tài khoản chuẩn; token/khóa, cấu hình private, ID thiết bị và fixture đầy đủ không đưa vào bằng chứng Git.
- Các jar Appointment/Queue/Consultation/Prescription/Lab mới đã chạy trên stack; 12 tiến trình kiểm tra health qua mTLS đạt. Global environment và vật liệu khóa caller/mail giữ nguyên. Thay đổi cấu hình private chỉ gồm exact caller scopes cho hai lệnh liên service và publisher confirms/returns RabbitMQ; không đổi Resend key hoặc dịch vụ định danh.

## Kết quả theo yêu cầu

| Mã | Đã triển khai/kiểm chứng | Còn giới hạn |
|---|---|---|
| R3-01 | Web bác sĩ liệt kê/mở bản ghi và tải PDF bằng phiên có quyền. PDF hợp lệ tải từ nút web khớp SHA-256; không token trong URL. STAFF/bác sĩ ngoài ca bị chặn; sau bàn giao A mất quyền, B đọc được. Lỗi và danh sách trống hiển thị khác nhau. | Upload nguồn ở lần E2E dùng API multipart thật; chưa chứng minh picker iPhone bản18. Không tuyên bố có hệ thống quét malware riêng ngoài kiểm tra tệp hiện có. |
| R3-02 | Native gửi ngày `yyyy-MM-dd`, tên hồ sơ từ API, giới hạn PDF/JPEG/PNG, tiến trình/hủy. Guard phản hồi/cache theo tài khoản; bổ sung lịch sử/chi tiết khám không hẹn lấy ca thật và lab endpoint của bệnh nhân. Giữ luồng mở/chia sẻ đã có. | Cài bản18, chọn tệp, mở/chia sẻ/hủy/mất mạng/mở lại app/đổi tài khoản trên máy thật còn chờ. Android R3 chưa build/nghiệm thu. |
| R3-03 | Không CLS và CBC có CLS: chẩn đoán/sinh hiệu lưu, chờ kết quả đúng ca, kết quả phát hành tạo lượt review từ phiếu gốc. Resume sớm bị409. Bác sĩ phải chủ động xác nhận đọc; thiếu chẩn đoán/toa nháp của mình/kết quả chưa đọc chặn hoàn tất. | Đã kiểm chứng một order CBC; mọi tổ hợp nhiều order, nhiều vòng CLS và chẩn đoán hình ảnh chưa được suy thành đạt. |
| R3-04 | Chọn ngày/slot thật theo bác sĩ/phân công, bỏ mặc định khoa/09:00. Web ký toa có tái khám, tạo tái khám riêng, reload vẫn thấy lịch. PATIENT đọc lịch đúng ca nguồn/người phụ trách. Inbox có FOLLOW_UP_SCHEDULED và APPOINTMENT_CONFIRMED của lịch. | Nhìn thấy lịch/inbox qua API không chứng minh UI bản18 hoặc push/background. Push/reminder thuộc R4. |
| R3-05 | Chữ ký và outbox cùng transaction; retry tái khám riêng giữ toa CONFIRMED. Finalizer báo từng bước Queue/Appointment còn chờ và cho đối soát lại. Tests PostgreSQL kiểm tra rollback, replay, concurrent confirm, timeout và stable event ID. Runtime broker giao toàn bộ sự kiện R3 đã sinh. | Lỗi dependency/broker có chủ đích trong DB tests dùng mocks điều khiển; không ghi thành đã chaos-test mọi lỗi mạng/process trên cả stack. |
| R3-06 | Bàn giao ca không hẹn đang chờ CLS qua API thật; người nhận resume cùng ID. A mất quyền ca/lab/hồ sơ; B không sửa toa nháp của A; tác giả chỉ định/toa cũ giữ nguyên. Tái khám tạo ca mới cho B và hoàn tất đúng lịch/phiếu. | Mọi biến thể thay đổi phân công/điều phối đồng thời ngoài regression còn cần mở rộng nghiệm thu. |

## Quyết định và schema

- API enum toa vẫn **CONFIRMED**, không đổi thành SIGNED. `release` xác nhận toa trước rồi thử lịch; không giữ bearer token cho tác vụ nền. Retry luôn kiểm tra trách nhiệm bác sĩ hiện tại. Toa DISPENSED được coi là đã phát hành và không bị mở sửa.
- Prescription: Flyway V1 baseline an toàn cho DB cũ, V2 thêm kế hoạch/trạng thái tái khám và outbox. Consultation: V6 outbox. Lab: V3 nguồn phiếu/audit/version/outbox. DBML ba service đã xuất từ PostgreSQL test đã chạy migration: Prescription4bảng, Consultation6, Lab4, có notes constraints/indexes. Không thêm bảng/logic EMR hoặc Lab vào Prescription.
- Queue không chọn “lượt gần nhất của bệnh nhân” để tạo review. Lab giữ nguồn phiếu đã xác minh; dữ liệu cũ chỉ fallback đúng phiếu của lịch gốc. Endpoint đồng bộ cho bác sĩ hiện tại hỗ trợ đối soát, không đoán source.
- Bàn giao walk-in lấy metadata tối thiểu từ phiếu gốc, vẫn xác minh phòng/quyền qua Directory. Endpoint bắt đầu khám hiện có giữ yêu cầu lượt đã gọi và ngày hợp lệ; không nới nó để phục vụ bàn giao chờ CLS.
- Lệnh hoàn tất Appointment riêng kiểm tra ca COMPLETED, patient/source/doctor trước cập nhật. Scope caller dùng exact endpoint; không cấp wildcard status toàn hệ thống.
- Consumer quầy thuốc quyết định hoàn tất lượt sau PrescriptionDispensed; không ép `QueueEntry/complete` để vượt guard này. Outbox dùng message bền vững, broker confirm/mandatory route, stable event ID và giao ít nhất một lần; không tuyên bố exactly-once toàn mạng.

## Kiểm thử và bằng chứng

| Nhóm | Kết quả |
|---|---|
| Build backend | Common và các module thay đổi build/package đạt. Kiểm thử cuối Consultation102pass/7skip điều kiện; Queue75pass/7skip; Lab81pass gồm5PG; Prescription113pass gồm5PG ở lần có fixture; Appointment regression đạt. Các lượt test khác nhau không cộng lặp thành tổng duy nhất. |
| PostgreSQL R3 | 5PrescriptionRelease +2ConsultationOutbox +2LabOutbox +3LabCreation =12pass. DB cô lập thật; có kiểm tra trạng thái ORM/version/transaction, không chỉ mock repository. |
| Regression mới | 4completion guard +4finalization +6walk-in handover +3review-source đạt. Bộ34handover cũ vẫn đạt. |
| Flutter | Toàn bộ250pass,15skip (14HTTPfixture có điều kiện và1pharmacy có sẵn); không tính skip là pass. Nhóm10test R3 upload/history/form đạt; analyzer12hạng mục sửa/mới không lỗi. |
| iOS | Release1.0.0(18) build thành công, codesign verify đạt; lib stage khớp source native. Artifact lưu private, không ghi đã cài. |
| Web | TypeScript đạt, production build Next webpack ở thư mục cô lập đạt. Live `.next` của web demo không bị build đè. |
| API không CLS | 8nhóm kiểm tra đạt: upload/quyền, guard, ký/lịch, replay, ca tái khám mới, quầy thuốc. Có phần phục hồi được ghi rõ bên dưới. |
| API CLS/bàn giao | 5nhóm chuẩn bị và5nhóm đối soát cuối đạt: gốc/review đúng ca, thu hồi quyền, bảo toàn tác giả, tái khám B, quầy thuốc và patient results. |
| Web thật | Tải PDF, xác nhận đọc, tạo/ký toa với slot, hoàn tất ca/review; tái khám riêng tạo/reload/hủy bằng quyền bệnh nhân. Không chặn request để dựng response. |
| Broker/inbox | PrescriptionIssued2 +Dispensed2, ConsultationCompleted4, LabReady1 +ResultsAvailable1: pending0 tại lần kiểm tra. Inbox của bệnh nhân có hai loại thông báo đúng lịch nguồn, statusDELIVERED ở hệ thống; đây không phải xác nhận OS đã đẩy tới iPhone. |

Các logs đầy đủ/fixture và backup chứa cấu hình cá nhân được giữ private ngoài Git. `results.json` lưu assertion đã che dữ liệu cùng giới hạn; hash artifact cho phép đối chiếu bản đang chạy mà không cung cấp secrets.

## Lỗi phát hiện, sửa và cách nghiệm thu tiếp

1. Lệnh cập nhật Appointment ban đầu bị caller scope chặn: dấu`*` giữa path không được CallerRegistry hỗ trợ. No-CLS trả partial thật, không báo toàn bộ thành công. Trong chẩn đoán đã dùng một lệnh bác sĩ được xác thực cập nhật lịch trực tiếp; sau đó thay bằng endpoint completion riêng có exact scope và finalizer đối soát đạt. **Nhánh CLS/tái khám mới sau bản sửa hoàn tất qua finalizer từ đầu**, không có cập nhật SQL. Không che phần can thiệp API ở nhánh đầu.
2. Lab source event cũ thiếu phiếu gốc; Queue có fallback theo bệnh nhân. Đã bỏ đoán source, thêm nguồn/audit/outbox, actual CBC sinh đúng lượt review gắn ca walk-in.
3. Thêm optimistic version lộ lỗi UUID gán tay trước JPA persist khiến order mới bị coi detached. Đã để ORM sinh ID, bổ sung JPA/PostgreSQL regression3case, rebuild/restart Lab và tạo order thật đạt.
4. Bàn giao trước đó đòi Appointment, khiến walk-in422. Đã dùng phiếu gốc và xác minh phòng; legacy queue không có departmentCode vẫn cần match phòng thực và Directory cùng khoa.6case mới và luồng thật A→B đạt.
5. Một số harness thất bại vì lazy ListView, thiếu fixture phiên/quyền mới, khác hoa/thường của text, kỳ vọng SIGNED thay vì CONFIRMED và cố lặp update/start vào ca/phiếu đã hoàn tất. Đã sửa harness theo contract thật, tiếp tục trên các ID đã tạo, không tạo lại/reset dữ liệu. Bằng chứng không đổi các lỗi này thành lỗi sản phẩm hoặc bỏ qua guard409 hợp lệ.
6. Production build mặc định Turbopack trong bản copy cô lập không resolve symlink node_modules ngoài workspace; build với webpack đạt, không sửa source/config ứng dụng để lách lỗi môi trường build.

## Rà phạm vi và phần còn mở

GitNexus đã được index lại ở hai worktree và detect-changes. Toàn diff backend224file/419symbol/836flow, mứcCRITICAL; native42file/180symbol/9flow, mứcHIGH tại lần rà. Đây là diff tích hợp gồm R1/R2 và thay đổi từ trước, **không phải tất cả do R3**. Đã đối chiếu manifest hash tại đầu R3 để tách phần thay đổi mới: [delta R3](r3-evidence-2026-10-20/r3-delta-manifest.json) gồm81file backend/web/tài liệu và14file native, không bao gồm chính manifest sinh ra. Whitespace của phần R3 đạt; một trailing-space ở tài liệu R0 ngoài phạm vi giữ nguyên hash, không sửa dọn kèm. Công cụ chưa phân giải được một số Java methods và cắt bớt flow; đã kiểm tra trực tiếp controller/Feign/Spring caller, không coi UNKNOWN là an toàn hoặc không dùng. Các vùng rủi ro cao đã cảnh báo trước sửa.

Còn mở trước khi chốt nghiệm thu R3 đầy đủ: cài/mở bản18 khi iPhone sẵn sàng; thử picker PDF/ảnh, xem/chia sẻ/hủy/mất mạng, đổi tài khoản và lịch sử/kết quả/lịch tái khám trên máy. R1/R2 các kiểm tra vật lý chưa xác nhận vẫn giữ; Android chưa nghiệm thu R3; push R4, Analytics R5 và AI R6 tiếp tục theo kế hoạch. Chưa phát hành production.
