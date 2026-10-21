import json
import unicodedata
from threading import Thread
from http.server import ThreadingHTTPServer
import urllib.request
import urllib.error

import pytest
from careflow_voice_core.inference import infer,SCHEMA
from careflow_voice_worker.server import Engine,handler

CATALOG=[dict(code='PARA',name='Paracetamol',unit='viên',active=True),
         dict(code='LORA',name='Loratadin',unit='viên',active=True)]

@pytest.mark.parametrize('text',[
 'Không kê Paracetamol.', 'Trong hồ sơ trước đây có dùng Paracetamol.',
 'Nếu cần thì kê Paracetamol.', 'Cho Paracetamol. Hủy Paracetamol.'
])
def test_negative_history_hypothetical_cancel_do_not_create_order(text):
    r=infer(text,CATALOG,'hospital-v1')
    assert r['items']==[]
    assert not r['prescriptionDraft']['canSaveWithoutCorrection']

def test_correction_does_not_carry_old_quantity_to_new_drug():
    r=infer('Cho Paracetamol, số lượng năm viên. À không số lượng sáu viên.',CATALOG,'v1')
    assert len(r['items'])==1 and r['items'][0]['quantity']==6
    r=infer('Cho Paracetamol, số lượng năm viên. Đổi Paracetamol sang Loratadin.',CATALOG,'v1')
    assert len(r['items'])==1 and r['items'][0]['spokenName']=='Loratadin'
    assert r['items'][0]['quantity'] is None

def test_unicode_evidence_and_revision_are_bound_to_nfc_transcript():
    r=infer(unicodedata.normalize('NFD','😀 Cho Paracetamol, số lượng sáu viên.'),CATALOG,'v1',3)
    assert unicodedata.is_normalized('NFC',r['transcript'])
    for item in r['items']:
        for e in item['evidence'].values():
            assert r['transcript'][e['start']:e['end']]==e['text']
            assert e['transcriptRevision']==3 and e['transcriptHash']==r['transcriptHash']

def test_no_rounding_or_invented_duration_and_unknown_source_fields_retained():
    r=infer('Cho Paracetamol 500 mg, số lượng 1,5 viên, dùng trong hai tuần.',CATALOG,'v1')
    p=r['prescriptionDraft'];assert p['items'][0]['quantity'] is None and p['items'][0]['duration'] is None
    codes={b['code'] for b in p['blockers']}
    assert 'QUANTITY_REQUIRES_INTEGER' in codes and 'DURATION_REQUIRES_EXPLICIT_INTEGER_DAYS' in codes
    assert p['items'][0]['unmappedFields']['strength']=='500 mg'

def test_ambiguous_and_phonetic_do_not_auto_select_medicine_code():
    catalog=CATALOG+[dict(code='PARA2',name='Paracetamol',unit='viên',active=True)]
    r=infer('Cho Paracetamol.',catalog,'v1');assert r['items'][0]['medicineCode'] is None
    assert len(r['items'][0]['candidates'])==2
    r=infer('Cho bà rasi tamôn.',CATALOG,'v1')
    assert all(item['medicineCode'] is None for item in r['items'])

def test_no_private_runtime_training_tts_imports():
    import sys
    assert not any(name.startswith(('careflow.audio_factory','careflow.training_lab','edge_tts')) for name in sys.modules)

def test_quantity_unit_mismatch_is_not_silently_converted():
    r=infer('Cho Paracetamol, số lượng hai gói.',CATALOG,'v1')
    assert r['items'][0]['quantity']==2 and r['items'][0]['quantityUnit']=='gói'
    assert r['prescriptionDraft']['items'][0]['quantity'] is None
    assert 'QUANTITY_UNIT_CONFLICT' in {b['code'] for b in r['prescriptionDraft']['blockers']}

class Runtime:
    ready=False
    info={}
    def transcribe(self,*args):raise AssertionError('must not invoke ASR for text')

def test_worker_auth_health_and_text_over_real_http():
    engine=Engine(Runtime(),'x'*32)
    server=ThreadingHTTPServer(('127.0.0.1',0),handler(engine));t=Thread(target=server.serve_forever,daemon=True);t.start()
    base=f'http://127.0.0.1:{server.server_port}'
    try:
        request=urllib.request.Request(base+'/internal/v1/voice/infer',data=b'{}',headers={'Content-Type':'application/json'},method='POST')
        with pytest.raises(urllib.error.HTTPError) as e:urllib.request.urlopen(request)
        assert e.value.code==401
        req=urllib.request.Request(base+'/health/ready',headers={'Authorization':'Bearer '+'x'*32})
        with pytest.raises(urllib.error.HTTPError) as e:urllib.request.urlopen(req)
        assert e.value.code==503
        payload=dict(schemaVersion=SCHEMA,transcript='Cho Paracetamol, số lượng sáu viên.',medicines=CATALOG,catalogVersion='v1')
        request=urllib.request.Request(base+'/internal/v1/voice/infer',data=json.dumps(payload).encode(),
            headers={'Authorization':'Bearer '+'x'*32,'Content-Type':'application/json'},method='POST')
        with urllib.request.urlopen(request) as response:r=json.load(response)
        assert r['items'][0]['medicineCode']=='PARA'
        assert r['prescriptionDraft']['canSaveWithoutCorrection']
        assert engine.run(dict(payload,audioBase64='AA=='))[0]==422
        engine.slot.acquire()
        try:assert engine.run(payload)[0]==429
        finally:engine.slot.release()
    finally:server.shutdown();server.server_close();t.join()
