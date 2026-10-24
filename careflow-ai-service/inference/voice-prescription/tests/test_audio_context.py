import base64
import io
import math
import struct
import wave

import pytest

from careflow_voice_core.inference import SCHEMA
from careflow_voice_worker.runtime import LocalRuntime
from careflow_voice_worker.server import Engine


def payload():
    return dict(schemaVersion=SCHEMA, catalogVersion='hospital-only', audioBase64=base64.b64encode(b'audio').decode(),
                audioMimeType='audio/wav', medicines=[
                    dict(code='P', name='Paracetamol 500mg', unit='viên', active=True),
                    dict(code='X', name='Withdrawn medicine', unit='viên', active=False)])


class Runtime:
    ready = True
    info = {'backend': 'test'}

    def transcribe(self, audio, context=''):
        self.context = context
        assert audio == b'audio'
        return {'text': 'Kê Paracetamol 500mg, số lượng sáu viên.', 'decodeMs': 0, 'asrMs': 0,
                'audioDurationSeconds': 1}


def test_context_uses_only_active_hospital_catalog_and_reports_whole_request_time():
    runtime = Runtime()
    status, result = Engine(runtime, 'x' * 32).run(payload())
    assert status == 200
    assert 'Paracetamol' in runtime.context
    assert 'Withdrawn' not in runtime.context
    assert result['modelInfo']['contextUsed'] is True
    assert result['items'][0]['medicineCode'] == 'P'
    assert result['timings']['totalMs'] >= result['timings']['extractionMs'] >= 0
    assert result['timings']['audioDurationSeconds'] == 1


def test_invalid_catalog_is_rejected_before_model_runs():
    class NeverRun(Runtime):
        def transcribe(self, *args, **kwargs):
            pytest.fail('Invalid catalog must not consume model capacity')
    data = payload()
    data['medicines'] = []
    assert Engine(NeverRun(), 'x' * 32).run(data)[0] == 422


def test_real_ffmpeg_decode_keeps_context_and_uses_the_prototype_decoder_defaults():
    buf = io.BytesIO()
    with wave.open(buf, 'wb') as wav:
        wav.setnchannels(1)
        wav.setsampwidth(2)
        wav.setframerate(8000)
        wav.writeframes(b''.join(struct.pack('<h', int(1000 * math.sin(i / 8))) for i in range(2000)))

    class Session:
        def transcribe(self, path, **kwargs):
            assert kwargs == dict(language='Vietnamese', context='Paracetamol', return_timestamps=False)
            with wave.open(path) as decoded:
                assert decoded.getframerate() == 16000
                assert decoded.getnchannels() == 1
            return type('Result', (), {'text': ' Paracetamol 500mg. '})()

    runtime = LocalRuntime(None)
    runtime.ready = True
    runtime.session = Session()
    try:
        result = runtime.transcribe(buf.getvalue(), context='Paracetamol')
        assert result['text'] == 'Paracetamol 500mg.'
        assert result['audioDurationSeconds'] == .25
        assert result['decodeMs'] >= 0 and result['asrMs'] >= 0
    finally:
        runtime.close()
