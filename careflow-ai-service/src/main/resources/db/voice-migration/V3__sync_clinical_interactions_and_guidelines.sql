-- V3: Chỉnh sửa nullable cho ai_clinical_interactions & Seed Approved Guidelines Bộ Y Tế

ALTER TABLE ai_clinical_interactions ALTER COLUMN doctor_id DROP NOT NULL;
ALTER TABLE ai_clinical_interactions ALTER COLUMN user_question DROP NOT NULL;
ALTER TABLE ai_clinical_interactions ALTER COLUMN prompt_snapshot DROP NOT NULL;
ALTER TABLE ai_clinical_interactions ALTER COLUMN service_mode DROP NOT NULL;

CREATE OR REPLACE FUNCTION sync_ai_interaction_doctor()
RETURNS TRIGGER AS $$
BEGIN
    IF NEW.doctor_id IS NULL AND NEW.actor_id IS NOT NULL THEN
        NEW.doctor_id := NEW.actor_id;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_sync_ai_doctor ON ai_clinical_interactions;
CREATE TRIGGER trg_sync_ai_doctor
BEFORE INSERT OR UPDATE ON ai_clinical_interactions
FOR EACH ROW EXECUTE FUNCTION sync_ai_interaction_doctor();

-- Seed Approved Clinical Guidelines từ Bộ Y Tế cho Khoa Nội Tổng Quát và Hô Hấp
INSERT INTO ai_guideline_sources (
    id, title, revision, department_code, source_url, content, content_hash, status, synthetic, imported_by, reviewer_id, reviewed_at, created_at
) VALUES 
(
    'e0000001-0000-0000-0000-000000000001',
    'Hướng dẫn chẩn đoán và điều trị viêm phổi mắc phải cộng đồng ở người lớn',
    'QĐ 4800/QĐ-BYT',
    'NOI_TONG_QUAT',
    'https://kcb.vn/van-ban/quyet-dinh-so-4800-qd-byt-nam-2020.html',
    'Chẩn đoán viêm phổi cộng đồng (CAP) dựa trên hội chứng đông đặc phổi, sốt, ho khạc đờm mủ, ran ẩm nổ trên phổi và tổn thương thâm nhiễm mới trên X-quang phổi. Đánh giá mức độ nặng theo thang điểm CURB-65. Phác đồ điều trị ngoại trú cho người lớn không có bệnh đồng mắc: Lựa chọn đầu tay Amoxicillin 500mg-1g uống 3 lần/ngày, hoặc phối hợp Amoxicillin/Clavulanate 1000mg/62.5mg ngày 2 lần kết hợp Macrolide (Azithromycin 500mg ngày đầu, 250mg các ngày sau) hoặc Doxycycline 100mg ngày 2 lần. Thời gian dùng kháng sinh tối thiểu 5-7 ngày và ngưng sau khi hết sốt 48-72 giờ.',
    encode(sha256('Chẩn đoán viêm phổi cộng đồng (CAP) dựa trên hội chứng đông đặc phổi, sốt, ho khạc đờm mủ, ran ẩm nổ trên phổi và tổn thương thâm nhiễm mới trên X-quang phổi. Đánh giá mức độ nặng theo thang điểm CURB-65. Phác đồ điều trị ngoại trú cho người lớn không có bệnh đồng mắc: Lựa chọn đầu tay Amoxicillin 500mg-1g uống 3 lần/ngày, hoặc phối hợp Amoxicillin/Clavulanate 1000mg/62.5mg ngày 2 lần kết hợp Macrolide (Azithromycin 500mg ngày đầu, 250mg các ngày sau) hoặc Doxycycline 100mg ngày 2 lần. Thời gian dùng kháng sinh tối thiểu 5-7 ngày và ngưng sau khi hết sốt 48-72 giờ.'::bytea), 'hex'),
    'APPROVED',
    false,
    'd0000001-0000-0000-0000-000000000001',
    '11111111-1111-1111-1111-111111111111',
    now(),
    now()
),
(
    'e0000001-0000-0000-0000-000000000002',
    'Hướng dẫn chẩn đoán và điều trị tăng huyết áp ở người lớn',
    'QĐ 5968/QĐ-BYT',
    'NOI_TONG_QUAT',
    'https://kcb.vn/van-ban/quyet-dinh-so-5968-qd-byt-nam-2019.html',
    'Chẩn đoán tăng huyết áp khi huyết áp tâm thu >= 140 mmHg và/hoặc huyết áp tâm trương >= 90 mmHg đo tại phòng khám. Phân độ THA theo độ 1 (140-159/90-99), độ 2 (160-179/100-109), độ 3 (>=180/>=110). Mục tiêu điều trị huyết áp < 130/80 mmHg cho đa số bệnh nhân. Phác đồ điều trị ban đầu khuyến cáo phối hợp 2 thuốc liều thấp cố định (viên kết hợp đơn): Ức chế men chuyển (ACEi như Enalapril 5-10mg) hoặc Chẹn thụ thể AT1 (ARB như Losartan 50mg, Telmisartan 40mg) kết hợp với Chẹn kênh Canxi Dihydropyridine (CCB như Amlodipine 5mg) hoặc Lợi tiểu Thiazide/Thiazide-like.',
    encode(sha256('Chẩn đoán tăng huyết áp khi huyết áp tâm thu >= 140 mmHg và/hoặc huyết áp tâm trương >= 90 mmHg đo tại phòng khám. Phân độ THA theo độ 1 (140-159/90-99), độ 2 (160-179/100-109), độ 3 (>=180/>=110). Mục tiêu điều trị huyết áp < 130/80 mmHg cho đa số bệnh nhân. Phác đồ điều trị ban đầu khuyến cáo phối hợp 2 thuốc liều thấp cố định (viên kết hợp đơn): Ức chế men chuyển (ACEi như Enalapril 5-10mg) hoặc Chẹn thụ thể AT1 (ARB như Losartan 50mg, Telmisartan 40mg) kết hợp với Chẹn kênh Canxi Dihydropyridine (CCB như Amlodipine 5mg) hoặc Lợi tiểu Thiazide/Thiazide-like.'::bytea), 'hex'),
    'APPROVED',
    false,
    'd0000001-0000-0000-0000-000000000001',
    '11111111-1111-1111-1111-111111111111',
    now(),
    now()
),
(
    'e0000001-0000-0000-0000-000000000003',
    'Hướng dẫn chẩn đoán và điều trị đái tháo đường típ 2',
    'QĐ 5481/QĐ-BYT',
    'NOI_TONG_QUAT',
    'https://kcb.vn/van-ban/quyet-dinh-so-5481-qd-byt-nam-2020.html',
    'Chẩn đoán ĐTĐ típ 2 khi Glucose huyết tương lúc đói >= 7.0 mmol/L (126 mg/dL) hoặc HbA1c >= 6.5%. Mục tiêu chung HbA1c < 7.0%. Điều trị ban đầu lựa chọn Metformin khởi đầu 500mg-850mg uống trong hoặc sau bữa ăn, tăng dần liều tối đa 2000mg/ngày. Nếu bệnh nhân có kèm bệnh tim mạch do xơ vữa, suy tim hoặc bệnh thận mạn: ưu tiên bổ sung ức chế SGLT2 (Dapagliflozin, Empagliflozin) hoặc đồng vận thụ thể GLP-1.',
    encode(sha256('Chẩn đoán ĐTĐ típ 2 khi Glucose huyết tương lúc đói >= 7.0 mmol/L (126 mg/dL) hoặc HbA1c >= 6.5%. Mục tiêu chung HbA1c < 7.0%. Điều trị ban đầu lựa chọn Metformin khởi đầu 500mg-850mg uống trong hoặc sau bữa ăn, tăng dần liều tối đa 2000mg/ngày. Nếu bệnh nhân có kèm bệnh tim mạch do xơ vữa, suy tim hoặc bệnh thận mạn: ưu tiên bổ sung ức chế SGLT2 (Dapagliflozin, Empagliflozin) hoặc đồng vận thụ thể GLP-1.'::bytea), 'hex'),
    'APPROVED',
    false,
    'd0000001-0000-0000-0000-000000000001',
    '11111111-1111-1111-1111-111111111111',
    now(),
    now()
),
(
    'e0000001-0000-0000-0000-000000000004',
    'Danh mục tương tác thuốc chống chỉ định trong thực hành lâm sàng',
    'QĐ 5948/QĐ-BYT',
    'NOI_TONG_QUAT',
    'https://kcb.vn/van-ban/quyet-dinh-so-5948-qd-byt-nam-2021.html',
    'Tương tác thuốc chống chỉ định: Tuyệt đối không phối hợp Simvastatin liều cao với các chất ức chế mạnh CYP3A4 như Clarithromycin, Erythromycin, Itraconazole, Ketoconazole do làm tăng vọt nồng độ statin trong huyết tương, nguy cơ tiêu cơ vân cấp và suy thận cấp. Không phối hợp đồng thời thuốc ức chế men chuyển (ACEi) và chẹn thụ thể Angiotensin II (ARB) do tăng nguy cơ tụt huyết áp, suy thận và tăng kali máu nghiêm trọng.',
    encode(sha256('Tương tác thuốc chống chỉ định: Tuyệt đối không phối hợp Simvastatin liều cao với các chất ức chế mạnh CYP3A4 như Clarithromycin, Erythromycin, Itraconazole, Ketoconazole do làm tăng vọt nồng độ statin trong huyết tương, nguy cơ tiêu cơ vân cấp và suy thận cấp. Không phối hợp đồng thời thuốc ức chế men chuyển (ACEi) và chẹn thụ thể Angiotensin II (ARB) do tăng nguy cơ tụt huyết áp, suy thận và tăng kali máu nghiêm trọng.'::bytea), 'hex'),
    'APPROVED',
    false,
    'd0000001-0000-0000-0000-000000000001',
    '11111111-1111-1111-1111-111111111111',
    now(),
    now()
)
ON CONFLICT (department_code, title, revision) DO UPDATE SET 
    content = EXCLUDED.content,
    content_hash = EXCLUDED.content_hash,
    status = 'APPROVED',
    reviewer_id = EXCLUDED.reviewer_id,
    reviewed_at = EXCLUDED.reviewed_at;
