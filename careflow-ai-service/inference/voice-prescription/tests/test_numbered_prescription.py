"""Synthetic dictation regression: preserve every explicitly numbered medicine."""
import pytest
from careflow_voice_core.inference import infer
CATALOG=[dict(code='PARA',name='Paracetamol 500mg',unit='viên',active=True),dict(code='VITC',name='Vitamin C',unit='viên',active=True)]
TRANSCRIPT=('Đây là qua thuốc mấu để kiểm thử hệ thống. Thuốc thứ nhất là Vitamin C 500mg, mỗi lần uống một viên, ngày một lần, vào buổi sáng, dùng trong năm ngày, tổng số lượng năm viên. '
'Thuốc thứ hai là Cetirizin 10mg, mỗi lần bốn. 1 viên, ngày một lần, vào buổi tối, dùng trong ba ngày, tổng số lượng ba viên, '
 'thuốc thứ ba là Paracetamol 500mg, mỗi lần uống một viên. Này hai lần, và buổi sáng và buổi tối, dùng trong ba ngày, tổng số lượng sáu viên. '
 'Thuốc thứ tư là Loratadine 10 mg, mỗi lần uống một viên, ngày một lần, và buổi sáng, dùng trong ba ngày. Tổng số lượng ba viên')
def test_four_numbered_drugs_keep_names_strengths_days_and_separate_quantities():
 r=infer(TRANSCRIPT,CATALOG,'v1')
 assert [x['spokenName'] for x in r['items']]==['Vitamin C','Cetirizin','Paracetamol','Loratadine']
 assert [x['strength'] for x in r['items']]==['500 mg','10 mg','500 mg','10 mg']
 assert [x['quantity'] for x in r['items']]==[5,3,6,3]
 assert [x['duration'] for x in r['items']]==['5 ngày','3 ngày','3 ngày','3 ngày']
 assert r['items'][1]['dose'] is None
 assert r['items'][2]['frequency'] is None
 assert r['items'][2]['timing']=='buổi sáng và buổi tối'
 assert r['items'][1]['medicineCode'] is None and r['items'][3]['medicineCode'] is None
 for item in r['items']:
  for e in item['evidence'].values():assert r['transcript'][e['start']:e['end']]==e['text']
@pytest.mark.parametrize('sep',[', ',' '])
def test_unknown_numbered_drugs_without_catalog_or_punctuation(sep):
 r=infer('Thuốc thứ hai là Cetirizin 10mg, số lượng ba viên'+sep+'thuốc thứ ba là Loratadine 10mg, số lượng bốn viên.',CATALOG,'v1')
 assert [x['spokenName'] for x in r['items']]==['Cetirizin','Loratadine'];assert [x['quantity'] for x in r['items']]==[3,4]
@pytest.mark.parametrize('prefix',['Trước đây có kê','Không kê','Nếu cần thì kê'])
def test_numbered_history_negation_hypothetical_are_not_new_orders(prefix):
 assert infer(prefix+' thuốc thứ hai là Cetirizin 10mg, số lượng ba viên.',CATALOG,'v1')['items']==[]
def test_one_daily_dose_with_two_times_remains_a_conflict():
 r=infer('Kê Paracetamol 500mg, mỗi lần một viên, ngày một lần, buổi sáng và buổi tối.',CATALOG,'v1')
 assert r['items'][0]['frequency'] is None and 'frequency' in r['items'][0]['fieldConflicts']
def test_duration_weeks_fractional_days_or_conflicting_days_are_not_invented():
 r=infer('Thuốc thứ nhất là Cetirizin 10mg, dùng trong hai tuần.',CATALOG,'v1');assert 'tuần' in r['items'][0]['duration']
 r=infer('Thuốc thứ nhất là Cetirizin 10mg, dùng trong 1,5 ngày.',CATALOG,'v1');assert r['items'][0]['duration']=='dùng trong 1,5 ngày'
 r=infer('Thuốc thứ nhất là Cetirizin 10mg, dùng trong ba ngày, dùng trong năm ngày.',CATALOG,'v1');assert r['items'][0]['duration'] is None

LATEST_TRANSCRIPT=('Đây là toa thuốc mẫu để kiểm thử hệ thống. Thuốc thứ nhất là Vitamin C 500mg, mỗi lần uống một viên, ngày một lần, và buổi sáng, dùng trong năm ngày, tổng số lượng năm viên. '
 'Thuốc thứ hai là Cetirizin 10mg, mỗi lần uống một viên, ngày một lần, vào buổi tối, dùng trong ba ngày, tổng số lượng ba viên. '
 'Thuốc thứ ba là Paracetamol 500mg, mỗi lần uống một viên. Ngày hai lần, vào buổi sáng và buổi tối, dùng trong ba ngày, tổng số lượng sáu viên. '
 'Hốt thứ tư là Loratadin 10mg, mỗi lần cố một viên, ngày một lần, vào buổi sáng, dùng trong ba ngày. Tổng số lượng ba viên.')

def test_wrong_leading_word_does_not_drop_fourth_medicine():
 r=infer(LATEST_TRANSCRIPT,CATALOG,'v1')
 assert [x['spokenName'] for x in r['items']]==['Vitamin C','Cetirizin','Paracetamol','Loratadin']
 assert [x['quantity'] for x in r['items']]==[5,3,6,3]
 assert [x['duration'] for x in r['items']]==['5 ngày','3 ngày','3 ngày','3 ngày']
 assert r['items'][3]['strength']=='10 mg' and r['items'][3]['dose'] is None
 assert r['items'][3]['medicineCode'] is None
 for item in r['items']:
  for e in item['evidence'].values():assert r['transcript'][e['start']:e['end']]==e['text']

@pytest.mark.parametrize('lead',['Thuốc','Hốt','Hột',''])
def test_ordinal_cue_does_not_depend_on_exact_leading_noun(lead):
 r=infer(f'{lead} thứ tư là Loratadin 10mg, số lượng ba viên.',CATALOG,'v1')
 assert len(r['items'])==1 and r['items'][0]['spokenName']=='Loratadin' and r['items'][0]['strength']=='10 mg'

@pytest.mark.parametrize('prefix',['Không kê','Trước đây có kê','Nếu cần thì kê'])
def test_wrong_leading_word_still_preserves_negative_context(prefix):
 assert infer(prefix+' hốt thứ tư là Loratadin 10mg, số lượng ba viên.',CATALOG,'v1')['items']==[]

BARE_TRANSCRIPT=LATEST_TRANSCRIPT.replace('Thuốc thứ nhất là ','').replace('Thuốc thứ hai là ','').replace('Thuốc thứ ba là ','').replace('Hốt thứ tư là ','')

def test_name_strength_frequency_without_any_ordinal_keeps_all_four_drugs():
 r=infer(BARE_TRANSCRIPT,CATALOG,'v1')
 assert [x['spokenName'] for x in r['items']]==['Vitamin C','Cetirizin','Paracetamol','Loratadin']
 assert [x['quantity'] for x in r['items']]==[5,3,6,3]
 assert [x['duration'] for x in r['items']]==['5 ngày','3 ngày','3 ngày','3 ngày']
 assert r['items'][3]['dose'] is None

@pytest.mark.parametrize('joiner',[', và ',' '])
def test_bare_unknown_names_in_same_clause_keep_separate_frequencies(joiner):
 r=infer('Cetirizin 10mg, ngày một lần, số lượng ba viên'+joiner+'Loratadin 10mg, ngày hai lần, số lượng bốn viên.',CATALOG,'v1')
 assert [x['spokenName'] for x in r['items']]==['Cetirizin','Loratadin']
 assert [x['quantity'] for x in r['items']]==[3,4]
 assert [x['frequency'] for x in r['items']]==['ngày một lần','ngày hai lần']

@pytest.mark.parametrize('prefix',['Không kê','Trước đây có kê','Nếu cần thì kê','Đang dùng'])
def test_bare_unknown_name_still_respects_context(prefix):
 assert infer(prefix+' Cetirizin 10mg, ngày một lần.',CATALOG,'v1')['items']==[]

@pytest.mark.parametrize('text',['Mỗi lần uống 10mg, ngày một lần.','Liều dùng 10mg ngày một lần.','Hàm lượng 10mg, ngày một lần.'])
def test_strength_or_dose_without_medicine_name_does_not_create_a_drug(text):
 assert infer(text,CATALOG,'v1')['items']==[]

def test_bare_name_can_continue_into_next_sentence_without_losing_frequency():
 r=infer('Cetirizin 10mg. Ngày một lần, buổi tối, số lượng ba viên.',CATALOG,'v1')
 assert r['items'][0]['spokenName']=='Cetirizin' and r['items'][0]['frequency']=='Ngày một lần'
