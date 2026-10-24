"""Authenticated internal JSON inference API; no UI, upload paths or model downloads."""
import argparse
import base64
import binascii
import hmac
import json
import os
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

from careflow_voice_core.inference import infer, SCHEMA, catalog_entries
from careflow_voice_core.catalog import Catalog
from .runtime import LocalRuntime

MIMES={'audio/wav','audio/x-wav','audio/webm','audio/ogg','audio/mp4','audio/mpeg','video/webm','audio/flac'}
MAX_AUDIO=20*1024*1024


class BoundedHTTPServer(ThreadingHTTPServer):
    def __init__(self,*args,**kwargs):
        self.connections=threading.BoundedSemaphore(8)
        super().__init__(*args,**kwargs)
    def process_request(self,request,address):
        if not self.connections.acquire(blocking=False):
            try:request.sendall(b'HTTP/1.1 429 Too Many Requests\r\nContent-Length: 0\r\nConnection: close\r\n\r\n')
            finally:self.shutdown_request(request)
            return
        try:super().process_request(request,address)
        except Exception:self.connections.release();raise
    def process_request_thread(self,request,address):
        try:super().process_request_thread(request,address)
        finally:self.connections.release()


class Engine:
    def __init__(self,runtime,token):
        if not token or len(token)<32:raise ValueError('Worker service token must contain at least 32 characters')
        self.runtime=runtime;self.token=token;self.slot=threading.BoundedSemaphore(1)

    def authorized(self,header):return hmac.compare_digest(header or '', 'Bearer '+self.token)

    def run(self,data):
        if not self.slot.acquire(blocking=False):return 429,{'error':{'code':'WORKER_BUSY'}}
        try:
            started=time.perf_counter();audio_timings={}
            if data.get('schemaVersion')!=SCHEMA:raise ValueError('UNSUPPORTED_SCHEMA')
            text=data.get('transcript');audio=data.get('audioBase64')
            if (text is not None)==(audio is not None):raise ValueError('EXACTLY_ONE_INPUT_REQUIRED')
            if text is not None and not isinstance(text,str):raise ValueError('INVALID_TRANSCRIPT')
            if audio is not None:
                if not self.runtime.ready:return 503,{'error':{'code':'MODEL_UNAVAILABLE'}}
                if data.get('audioMimeType') not in MIMES:raise ValueError('UNSUPPORTED_AUDIO_FORMAT')
                try:payload=base64.b64decode(audio,validate=True)
                except (ValueError,TypeError,binascii.Error):raise ValueError('INVALID_AUDIO') from None
                if not payload or len(payload)>MAX_AUDIO:return 413,{'error':{'code':'AUDIO_TOO_LARGE'}}
                context=Catalog(catalog_entries(data.get('medicines'),data.get('catalogVersion'))).vocabulary()
                transcription=self.runtime.transcribe(payload,context=context)
                text=transcription['text'];audio_timings={k:v for k,v in transcription.items() if k!='text'}
            extraction_started=time.perf_counter()
            result=infer(text,data.get('medicines'),data.get('catalogVersion'),data.get('transcriptRevision',1))
            result['modelInfo'].update(self.runtime.info if audio is not None else {'asrModel':None,'backend':'text'})
            result['modelInfo']['contextUsed']=audio is not None and bool(context)
            result['timings']={**audio_timings,'extractionMs':round((time.perf_counter()-extraction_started)*1000,2),
                               'totalMs':round((time.perf_counter()-started)*1000,2)}
            return 200,result
        except ValueError as error:
            code=str(error) if str(error).isupper() and len(str(error))<80 else 'INVALID_INPUT'
            return 422,{'error':{'code':code}}
        except Exception:
            return 503,{'error':{'code':'INFERENCE_FAILED'}}
        finally:self.slot.release()


def handler(engine):
    class Handler(BaseHTTPRequestHandler):
        def log_message(self,*args):pass
        def setup(self):
            super().setup();self.connection.settimeout(15)
        def send(self,status,value):
            payload=json.dumps(value,ensure_ascii=False).encode()
            self.send_response(status);self.send_header('Content-Type','application/json; charset=utf-8')
            self.send_header('Cache-Control','no-store');self.send_header('Content-Length',str(len(payload)))
            self.end_headers();self.wfile.write(payload)
        def do_GET(self):
            if self.path=='/health/live':return self.send(200,{'status':'UP'})
            if not engine.authorized(self.headers.get('Authorization')):return self.send(401,{'error':{'code':'UNAUTHORIZED'}})
            if self.path=='/health/ready':return self.send(200 if engine.runtime.ready else 503,{'ready':engine.runtime.ready})
            self.send(404,{'error':{'code':'NOT_FOUND'}})
        def do_POST(self):
            if not engine.authorized(self.headers.get('Authorization')):return self.send(401,{'error':{'code':'UNAUTHORIZED'}})
            if self.path!='/internal/v1/voice/infer':return self.send(404,{'error':{'code':'NOT_FOUND'}})
            if self.headers.get('Content-Type','').split(';')[0]!='application/json':return self.send(415,{'error':{'code':'JSON_REQUIRED'}})
            try:
                size=int(self.headers.get('Content-Length','0'))
                if not 0<size<=32*1024*1024:return self.send(413,{'error':{'code':'REQUEST_TOO_LARGE'}})
                data=json.loads(self.rfile.read(size))
                if not isinstance(data,dict):raise ValueError()
            except (ValueError,TimeoutError):return self.send(400,{'error':{'code':'INVALID_JSON'}})
            status,value=engine.run(data);self.send(status,value)
    return Handler


def main():
    parser=argparse.ArgumentParser();parser.add_argument('--host',default='127.0.0.1');parser.add_argument('--port',type=int,default=8770)
    args=parser.parse_args();runtime=LocalRuntime(os.getenv('VOICE_MODEL_MANIFEST'))
    engine=Engine(runtime,os.getenv('VOICE_WORKER_TOKEN',''))
    if runtime.manifest_path:
        try:runtime.initialize()
        except Exception:print('Model initialization failed; readiness remains unavailable.',flush=True)
    server=BoundedHTTPServer((args.host,args.port),handler(engine))
    certificate=os.getenv('VOICE_TLS_CERT')
    if certificate:
        import ssl
        context=ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
        context.minimum_version=ssl.TLSVersion.TLSv1_2
        context.load_cert_chain(certificate,os.environ['VOICE_TLS_KEY'])
        context.load_verify_locations(cafile=os.environ['VOICE_TLS_CA'])
        context.verify_mode=ssl.CERT_REQUIRED
        server.socket=context.wrap_socket(server.socket,server_side=True)
    try:server.serve_forever()
    finally:server.server_close();runtime.close()


if __name__=='__main__':main()
