"""Local model provisioning and one owning model thread; no trainer imports."""
import array
from concurrent.futures import ThreadPoolExecutor, TimeoutError as FutureTimeout
import hashlib
import json
from pathlib import Path
import shutil
import subprocess
import tempfile
import time
import wave


def digest(path):
    with Path(path).open('rb') as file:return hashlib.file_digest(file,'sha256').hexdigest()


class LocalRuntime:
    def __init__(self, manifest_path):
        self.manifest_path=Path(manifest_path).resolve() if manifest_path else None
        self.executor=ThreadPoolExecutor(max_workers=1,thread_name_prefix='voice-model')
        self.session=None;self.info={};self.ready=False

    def initialize(self):
        return self.executor.submit(self._initialize).result()

    def _initialize(self):
        from mlx_qwen3_asr import Session,load_model
        from mlx_lm.tuner.lora import LoRALinear
        if not self.manifest_path or not shutil.which('ffmpeg'):raise ValueError('MODEL_UNAVAILABLE')
        manifest=json.loads(self.manifest_path.read_text());root=self.manifest_path.parent
        if manifest.get('schemaVersion')!=1 or manifest.get('backend')!='mlx':raise ValueError('INVALID_MODEL_MANIFEST')
        for name,proof in manifest['files'].items():
            file=(root/name).resolve()
            if not file.is_relative_to(root) or digest(file)!=proof:raise ValueError('MODEL_CHECKSUM_MISMATCH')
        base=(root/manifest['baseDirectory']).resolve();adapter=(root/manifest['adapterDirectory']).resolve()
        if not base.is_relative_to(root) or not adapter.is_relative_to(root):raise ValueError('INVALID_MODEL_PATH')
        required={str(p.relative_to(root)) for folder in (base,adapter) for p in folder.rglob('*') if p.is_file()}
        if not required or not required.issubset(manifest['files']):raise ValueError('INCOMPLETE_MODEL_CHECKSUMS')
        config=json.loads((adapter/'adapter_config.json').read_text())
        if config['base_model']!=manifest['modelId'] or config.get('base_model_revision')!=manifest['baseRevision']:
            raise ValueError('INCOMPATIBLE_ADAPTER')
        model,_=load_model(str(base));model.freeze()
        lora=config['lora']
        for layer in model.model.layers[-lora['layers']:]:
            for field in ('q_proj','v_proj'):
                setattr(layer.self_attn,field,LoRALinear.from_base(getattr(layer.self_attn,field),r=lora['rank'],scale=lora['scale'],dropout=0))
        model.load_weights(str(adapter/'adapters.safetensors'),strict=False);model.eval()
        self.session=Session(model=model,tokenizer_model=str(base))
        self.info=dict(asrModel=manifest['modelId'],baseRevision=manifest['baseRevision'],
                       releaseId=manifest['releaseId'],adapterSha256=digest(adapter/'adapters.safetensors'),backend='mlx')
        self.ready=True

    def transcribe(self, payload, context=''):
        try:return self.executor.submit(self._transcribe,payload,context).result(timeout=150)
        except FutureTimeout:
            # A running native model cannot be safely killed as a Python thread.
            # Fail readiness and admit no further audio until an operator restarts it.
            self.ready=False
            raise RuntimeError('MODEL_DEADLINE_EXCEEDED') from None

    def _transcribe(self,payload,context):
        if not self.ready:raise RuntimeError('MODEL_UNAVAILABLE')
        started=time.perf_counter()
        with tempfile.TemporaryDirectory(prefix='careflow-voice-') as directory:
            source=Path(directory)/'input.audio';target=Path(directory)/'normalized.wav'
            source.write_bytes(payload)
            try:
                subprocess.run(['ffmpeg','-hide_banner','-loglevel','error','-nostdin','-protocol_whitelist','file,pipe',
                    '-i',str(source),'-t','181','-ac','1','-ar','16000','-c:a','pcm_s16le',str(target)],
                    check=True,capture_output=True,timeout=30)
            except subprocess.CalledProcessError:raise ValueError('INVALID_AUDIO') from None
            except subprocess.TimeoutExpired:raise ValueError('AUDIO_DECODE_TIMEOUT') from None
            with wave.open(str(target)) as wav:
                duration=wav.getnframes()/16000
                samples=array.array('h',wav.readframes(wav.getnframes()))
            if not .15<=duration<=180 or not samples or max(map(abs,samples))<20:raise ValueError('INVALID_AUDIO')
            decoded=time.perf_counter()
            text=self.session.transcribe(str(target),language='Vietnamese',context=context,return_timestamps=False).text.strip()
            if not text:raise ValueError('EMPTY_TRANSCRIPT')
            return {'text':text,'decodeMs':round((decoded-started)*1000,2),
                    'asrMs':round((time.perf_counter()-decoded)*1000,2),'audioDurationSeconds':round(duration,2)}

    def close(self):self.executor.shutdown(wait=True)
