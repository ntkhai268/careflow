import pytest

from careflow_voice_core.inference import infer

CATALOG = [dict(code='MED001', name='Paracetamol 500mg', unit='viên', active=True)]


@pytest.mark.parametrize('instruction', [
    'Đầu tiên, tôi kê cho chị thuốc Loratadin 10 miligam, số lượng 5 viên.',
    'Kê Loratadin 10mg, số lượng năm viên.',
    'Thêm thuốc Loratadin 10mg, số lượng năm viên.',
])
def test_explicit_unknown_prescribed_medicine_is_not_silently_omitted(instruction):
    result = infer(instruction + ' Kê Paracetamol 500mg, số lượng sáu viên.', CATALOG, 'v1')
    assert len(result['items']) == 2
    unknown = result['items'][0]
    assert unknown['spokenName'] == 'Loratadin'
    assert unknown['medicineCode'] is None
    assert unknown['strength'] == '10 mg'
    assert len(result['prescriptionDraft']['items']) == 1
    assert any(b['itemIndex'] == 0 and b['code'] == 'CATALOG_SELECTION_REQUIRED'
               for b in result['prescriptionDraft']['blockers'])
    assert not result['prescriptionDraft']['canSaveWithoutCorrection']
    for evidence in unknown['evidence'].values():
        assert result['transcript'][evidence['start']:evidence['end']] == evidence['text']


@pytest.mark.parametrize('instruction', [
    'Trước đây có kê thuốc Loratadin 10mg.',
    'Không kê thuốc Loratadin 10mg.',
    'Nếu cần thì kê thuốc Loratadin 10mg.',
    'Tôi uống thuốc Loratadin 10mg.',
    'Mỗi lần uống 10mg.',
])
def test_unknown_history_negation_hypothetical_and_nonprescribing_prose_are_not_orders(instruction):
    result = infer(instruction, CATALOG, 'v1')
    assert result['items'] == []
    assert result['prescriptionDraft']['items'] == []


def test_unknown_first_drug_does_not_break_quantity_correction_for_the_next_drug():
    text = (
        'Đầu tiên tôi kê cho chị thuốc Loratadin 10mg, số lượng năm viên. '
        'Tiếp theo là Paracetamol 500mg. Thuốc này dùng khi chị bị đau đầu hoặc sốt. '
        'Tôi định kê 10 viên... À thôi, sửa lại nhé, chỉ lấy 6 viên thôi. '
        'Mỗi lần uống một viên khi cần, các lần uống cách nhau ít nhất 6 tiếng. '
        'Không uống quá 6 viên trong vòng 24 giờ. '
        'Đọc lại đơn. Paracetamol 500mg, 6 viên, chỉ dùng khi đau đầu hoặc sốt.'
    )
    result = infer(text, CATALOG, 'v1')
    assert len(result['items']) == 2
    assert result['items'][0]['medicineCode'] is None
    para = result['items'][1]
    assert para['medicineCode'] == 'MED001'
    assert para['quantity'] == 6
    assert para['dose'] == 'Mỗi lần uống một viên'
    assert para['frequency'] == 'dùng khi chị bị đau đầu hoặc sốt'
    assert para['maxDailyDose'] == 'Không uống quá 6 viên trong vòng 24 giờ'
    assert para['fieldConflicts'] == {}
