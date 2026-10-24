import pytest

from careflow_voice_core.inference import infer

CATALOG = [dict(code='MED001', name='Paracetamol 500mg', unit='viên', active=True)]


def test_specific_prn_survives_bare_when_needed_and_equivalent_recap():
    text = (
        'Kê Paracetamol 500 miligam. '
        'Thuốc này dùng khi chị bị đau đầu hoặc sốt. '
        'Mỗi lần uống một viên khi cần, các lần uống cách nhau ít nhất 6 tiếng. '
        'Không uống quá 6 viên trong vòng 24 giờ. '
        'Đọc lại đơn. Paracetamol 500 miligam, 6 viên, chỉ dùng khi đau đầu hoặc sốt.'
    )
    result = infer(text, CATALOG, 'test-prn')
    item = result['items'][0]
    assert item['medicineCode'] == 'MED001'
    assert item['frequency'] == 'dùng khi chị bị đau đầu hoặc sốt'
    assert item['maxDailyDose'] == 'Không uống quá 6 viên trong vòng 24 giờ'
    assert item['quantity'] == 6
    assert item['fieldConflicts'] == {}
    assert result['prescriptionDraft']['items'][0]['duration'] is None
    for evidence in item['evidence'].values():
        assert result['transcript'][evidence['start']:evidence['end']] == evidence['text']


@pytest.mark.parametrize('bare', ['khi cần,', 'uống khi cần,', 'dùng khi cần'])
def test_generic_prn_can_be_expanded_by_an_explicit_recap(bare):
    result = infer(
        'Kê Paracetamol 500mg, ' + bare + ' số lượng sáu viên. '
        'Đọc lại đơn. Paracetamol 500mg, dùng khi đau đầu hoặc sốt.',
        CATALOG, 'test-prn',
    )
    item = result['items'][0]
    assert item['medicineCode'] == 'MED001'
    assert item['frequency'] == 'dùng khi đau đầu hoặc sốt'
    assert item['fieldConflicts'] == {}


@pytest.mark.parametrize('first,recap', [
    ('dùng khi đau đầu', 'dùng khi đau bụng'),
    ('ngày hai lần', 'dùng khi đau đầu'),
    ('dùng khi đau đầu hoặc sốt', 'dùng khi đau đầu'),
])
def test_different_explicit_conditions_and_fixed_vs_prn_remain_conflicts(first, recap):
    result = infer(
        f'Kê Paracetamol 500mg, {first}. Đọc lại đơn. Paracetamol 500mg, {recap}.',
        CATALOG, 'test-prn',
    )
    assert 'frequency' in result['items'][0]['fieldConflicts']
    assert result['prescriptionDraft']['items'] == []


def test_equivalent_fixed_frequency_recap_does_not_hide_dose_conflict():
    result = infer(
        'Kê Paracetamol 500mg, mỗi lần một viên, ngày hai lần. '
        'Đọc lại đơn. Paracetamol 500mg, mỗi lần hai viên, ngày 2 lần.',
        CATALOG, 'test-prn',
    )
    assert 'dose' in result['items'][0]['fieldConflicts']
    assert result['prescriptionDraft']['items'] == []
