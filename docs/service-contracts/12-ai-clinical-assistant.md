# AI Clinical Assistant Service Contract

> Contract ID: `CF-SVC-12` | Version: `1.0` | Module: `careflow-ai-service`

## 1. Trách nhiệm và giới hạn an toàn

AI Clinical Assistant:

- tóm tắt EMR cho bác sĩ;
- gợi ý chẩn đoán phân biệt/phác đồ tham khảo;
- cảnh báo thông tin cần kiểm tra, dị ứng hoặc dữ liệu còn thiếu;
- trả bằng chứng tham chiếu đến dữ liệu CareFlow đã cung cấp.

AI không tự chẩn đoán, không tự tạo lab order, không tự kê/xác nhận toa và không tự hoàn tất
consultation. Mọi output là gợi ý; bác sĩ là người quyết định và nhập dữ liệu chính thức.

## 2. Chế độ MVP

Service phải hỗ trợ một trong hai adapter nhưng giữ cùng contract:

```text
MOCK_RULE_BASED
LLM_PROVIDER
```

`MOCK_RULE_BASED` là đủ cho demo nếu output deterministic, có nhãn mock và không tuyên bố là AI y tế
đã được kiểm định. Provider key chỉ nằm trong secret/environment.

## 3. HTTP API

| Method và path | Quyền | Mục đích |
|---|---|---|
| `POST /api/ai/clinical-suggestions` | Assigned `DOCTOR` | Gợi ý cho một consultation |
| `POST /api/ai/clinical-chat` | Assigned `DOCTOR` | Hỏi tiếp trong context consultation |
| `GET /api/ai/interactions/{interactionId}` | Doctor đã tạo hoặc auditor | Xem audit metadata/output |

Suggestion request:

```json
{
  "consultationId": "35df361e-f4b3-4113-ad0d-0ed853fe61fc",
  "question": "Gợi ý các khả năng cần cân nhắc và dữ liệu còn thiếu"
}
```

Không nhận toàn bộ EMR do browser gửi. Service dùng `consultationId`, xác minh assignment rồi lấy
context đã lọc từ EMR/Consultation.

Suggestion response `data`:

```json
{
  "interactionId": "17df5248-fe50-499f-b12a-015f38de8df7",
  "mode": "MOCK_RULE_BASED",
  "summary": "Bệnh nhân nữ 34 tuổi, đau đầu kèm buồn nôn...",
  "suggestions": [
    {
      "type": "DIFFERENTIAL_DIAGNOSIS",
      "text": "Cân nhắc migraine không aura",
      "evidenceRefs": [
        {
          "sourceType": "CONSULTATION",
          "sourceId": "35df361e-f4b3-4113-ad0d-0ed853fe61fc",
          "field": "symptoms"
        }
      ]
    }
  ],
  "missingInformation": ["Thời lượng mỗi cơn", "Yếu tố khởi phát"],
  "warnings": [],
  "disclaimer": "Chỉ là gợi ý hỗ trợ. Bác sĩ chịu trách nhiệm quyết định lâm sàng.",
  "model": {
    "provider": "mock",
    "name": "careflow-rule-based-v1",
    "version": "1"
  },
  "generatedAt": "2026-08-18T04:20:00Z"
}
```

Chat request:

```json
{
  "consultationId": "35df361e-f4b3-4113-ad0d-0ed853fe61fc",
  "parentInteractionId": "17df5248-fe50-499f-b12a-015f38de8df7",
  "message": "Có dấu hiệu cảnh báo nào cần hỏi thêm?"
}
```

## 4. Context, privacy và prompt safety

- Chỉ lấy field cần cho câu hỏi; không lấy CCCD, địa chỉ, số BHYT.
- Không gửi file/ảnh ra provider nếu chưa có contract và consent riêng.
- Prompt/output không ghi vào application log.
- Lưu audit metadata: doctor, patient/consultation reference, model/version, thời gian, latency,
  correlation ID và hash của input; policy quyết định có lưu full text hay không.
- Input từ hồ sơ được coi là dữ liệu, không phải instruction; chống prompt injection từ uploaded note.
- Timeout/failure trả lỗi có kiểm soát; Doctor Web vẫn khám bình thường không cần AI.

## 5. Guardrail bắt buộc

- Mọi response có disclaimer.
- Không có action tự động gọi Prescription/Lab/Consultation write API.
- Evidence ref phải trỏ đến context thật; không tạo nguồn giả.
- Nếu thiếu context, trả `missingInformation`, không bịa.
- Không tạo liều thuốc cụ thể từ dữ liệu không đủ hoặc dùng output như toa.
- UI phải yêu cầu bác sĩ chủ động copy/chọn; không auto-save vào diagnosis.

## 6. Event

Exchange: `ai.exchange`.

Publish `AiSuggestionGenerated` v1 cho Analytics/Audit, payload không chứa prompt/output:

```json
{
  "interactionId": "17df5248-fe50-499f-b12a-015f38de8df7",
  "consultationId": "35df361e-f4b3-4113-ad0d-0ed853fe61fc",
  "doctorId": "8c9153aa-7d79-489a-a98f-2b75dd33fb94",
  "mode": "MOCK_RULE_BASED",
  "modelName": "careflow-rule-based-v1",
  "latencyMs": 120,
  "generatedAt": "2026-08-18T04:20:00Z"
}
```

## 7. Mock cho Doctor Web

- Deterministic fixture: success có evidence, missing context, timeout, provider unavailable và blocked
  unsafe request.
- UI không được parse free text để tự tạo prescription/order.
- Mock response luôn có `mode`, `model`, `disclaimer` và `interactionId`.
- Service thật và mock phải cùng schema.

## 8. Definition of Done

### `CONTRACT_READY`

- Chốt input bằng consultation ID, response/evidence, guardrail, privacy và failure behavior.
- Doctor Web có deterministic fixtures.

### `FUNCTIONAL_READY`

- Mock adapter chạy; authorization/assignment/context filtering hoạt động.
- Test thiếu dữ liệu, injection text, timeout, disclaimer và không có write side effect.
- Audit metadata không lộ PII/prompt trong log.

### `INTEGRATION_READY`

- Đọc đúng EMR/Consultation theo quyền; provider failure không chặn khám.
- Event analytics không chứa clinical content.
- Doctor phải chủ động lưu mọi dữ liệu được tham khảo.

### `DEMO_READY`

- Doctor mở chatbot trong consultation và nhận gợi ý có evidence/disclaimer.
- Có thể demo provider lỗi nhưng luồng khám vẫn hoàn tất.
- Audit chỉ ra ai yêu cầu, model/version nào và lúc nào; AI không tự đổi hồ sơ/toa/order.
