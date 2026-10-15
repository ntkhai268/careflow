import test from 'node:test';
import assert from 'node:assert/strict';
import {Readable,Writable} from 'node:stream';
import http from 'node:http';
import {once} from 'node:events';
import {createEmulator, normalizeGateway} from '../server.mjs';

// Exercise the actual HTTP handler and streaming proxy without binding a port.
// The full network integration suite is tests/integration/server.integration.mjs.
class Response extends Writable {
  constructor(){ super();this.headers={};this.statusCode=200;this.chunks=[];this.headersSent=false; }
  _write(chunk,encoding,done){this.chunks.push(Buffer.from(chunk));done();}
  setHeader(key,value){this.headers[key.toLowerCase()]=value;}
  writeHead(code,headers={}){this.statusCode=code;this.headersSent=true;for(const [k,v] of Object.entries(headers))this.setHeader(k,v);return this;}
  get text(){return Buffer.concat(this.chunks).toString();}
  get json(){return JSON.parse(this.text);}
}
async function request(server,path='/',{method='GET',headers={},body=''}={}){
  const req=Readable.from(body ? [body] : []);
  Object.assign(req,{url:path,method,headers:{host:'127.0.0.1:5173',...headers}});
  const res=new Response();const done=once(res,'finish');
  server.emit('request',req,res);await done;return res;
}
function proxyBackend(t,{status=200,response='{"data":{}}',responseHeaders={}}={}){
  let captured;
  t.mock.method(http,'request',(options,callback)=>{
    const chunks=[];
    const outgoing=new Writable({
      write(chunk,encoding,done){chunks.push(Buffer.from(chunk));done();},
      final(done){
        captured={...options,body:Buffer.concat(chunks).toString()};
        const incoming=Readable.from([response]);
        Object.assign(incoming,{statusCode:status,headers:responseHeaders});
        callback(incoming);done();
      },
    });
    return outgoing;
  });
  return ()=>captured;
}

test('normalizes gateway root and rejects unsafe or ambiguous URLs',()=>{
  assert.equal(normalizeGateway('http://127.0.0.1:8080/api/'),'http://127.0.0.1:8080');
  for(const value of ['ftp://example.com','https://u:p@example.com','https://example.com/private','https://example.com/?token=secret'])assert.throws(()=>normalizeGateway(value));
});

test('serves real shell and compiled mobile assets with proper MIME types',async()=>{
  const server=createEmulator();
  assert.match((await request(server)).text,/CareFlow/);
  assert.match((await request(server,'/mobile/')).text,/flutter_bootstrap/);
  assert.equal((await request(server,'/mobile/canvaskit/canvaskit.wasm')).headers['content-type'],'application/wasm');
  assert.equal((await request(server,'/mobile/assets/FontManifest.json')).statusCode,200);
});

test('never serves source, credentials or encoded parent-directory paths',async()=>{
  const server=createEmulator();
  for(const path of ['/server.mjs','/app/lib/main.dart','/.env','/mobile/.env','/mobile/%2e%2e%2fpubspec.yaml','/%2e%2e/server.mjs'])assert.equal((await request(server,path)).statusCode,404,path);
});

test('forwards JWT, body and query exactly, strips spoofed trust headers',async t=>{
  const received=proxyBackend(t,{status:201});const server=createEmulator();
  const result=await request(server,'/api/auth/login?x=1',{method:'POST',headers:{authorization:'Bearer secret','content-type':'application/json','x-user-id':'spoof','x-user-role':'ADMIN','x-internal-token':'spoof','cookie':'private'},body:'{"password":"hidden"}'});
  assert.equal(result.statusCode,201);
  const upstream=received();assert.equal(upstream.headers.authorization,'Bearer secret');assert.equal(upstream.body,'{"password":"hidden"}');assert.equal(upstream.path,'/api/auth/login?x=1');
  for(const key of ['x-user-id','x-user-role','x-internal-token','cookie'])assert.equal(upstream.headers[key],undefined);
});

test('preserves multipart bytes and boundary',async t=>{
  const received=proxyBackend(t);const server=createEmulator();
  const body='--test-boundary\r\nContent-Disposition: form-data; name="image"; filename="id.png"\r\nContent-Type: image/png\r\n\r\nPNG\r\n--test-boundary--';
  await request(server,'/api/auth/ekyc',{method:'POST',headers:{'content-type':'multipart/form-data; boundary=test-boundary'},body});
  assert.equal(received().body,body);assert.equal(received().headers['content-type'],'multipart/form-data; boundary=test-boundary');
});

test('preserves backend authorization errors and response envelope',async t=>{
  proxyBackend(t,{status:403,response:'{"status":403,"message":"Forbidden"}'});
  const result=await request(createEmulator(),'/api/queues/me/status');
  assert.equal(result.statusCode,403);assert.equal(result.json.message,'Forbidden');
});

test('activity omits credentials, request bodies, query strings and resource UUIDs',async t=>{
  proxyBackend(t);const server=createEmulator();const id='f0000001-0000-0000-0000-000000000001';
  await request(server,`/api/patients/${id}?private=hidden`,{headers:{authorization:'Bearer secret'}});
  const activity=(await request(server,'/__emulator/activity')).text;
  for(const value of ['secret','private','hidden',id])assert.ok(!activity.includes(value));assert.match(activity,/:id/);
});

test('rejects foreign origins and cross-site requests before proxying',async()=>{
  const server=createEmulator();
  assert.equal((await request(server,'/__emulator/config',{method:'POST',headers:{origin:'https://external.example','content-type':'application/json'},body:'{"gateway":"https://example.com"}'})).statusCode,403);
  assert.equal((await request(server,'/api/auth/me',{headers:{'sec-fetch-site':'cross-site'}})).statusCode,403);
});

test('validates gateway update, clears activity, and retains actual browser origin',async t=>{
  const received=proxyBackend(t);const server=createEmulator();
  await request(server,'/api/auth/me');
  const result=await request(server,'/__emulator/config',{method:'POST',headers:{origin:'http://127.0.0.1:5173','content-type':'application/json'},body:'{"gateway":"https://api.example.com/api"}'});
  assert.equal(result.json.gateway,'https://api.example.com');assert.deepEqual((await request(server,'/__emulator/activity')).json,[]);
  // Invalid mutations leave the last valid gateway in place.
  const invalid=await request(server,'/__emulator/config',{method:'POST',headers:{'content-type':'application/json'},body:'{"gateway":"file:///tmp"}'});
  assert.equal(invalid.statusCode,400);
  assert.equal((await request(server,'/__emulator/config')).json.gateway,'https://api.example.com');
  // Origin was not rewritten to bypass server CORS checks.
  assert.equal(received().headers.origin,undefined);
});

test('connection failures produce actionable 502 and redact upstream errors',async t=>{
  t.mock.method(http,'request',()=>{
    const outgoing=new Writable({write(chunk,encoding,done){done();},final(done){done();queueMicrotask(()=>outgoing.emit('error',new Error('sensitive internal address')));}});return outgoing;
  });
  const result=await request(createEmulator(),'/api/auth/me');assert.equal(result.statusCode,502);assert.match(result.json.message,/Gateway/);assert.ok(!result.text.includes('sensitive'));
});

test('attributes API activity to devices without forwarding the local marker upstream',async t=>{
  const received=proxyBackend(t);const server=createEmulator();
  await request(server,'/api/auth/me',{headers:{'x-emulator-device':'phone-01',authorization:'Bearer patient-one'}});
  assert.equal(received().headers['x-emulator-device'],undefined);
  await request(server,'/api/auth/me',{headers:{'x-emulator-device':'phone-02',authorization:'Bearer patient-two'}});
  const items=(await request(server,'/__emulator/activity')).json;
  assert.deepEqual(items.map(item=>item.device),['phone-02','phone-01']);
  assert.ok(!JSON.stringify(items).includes('patient-one'));
});
test('Gateway scope is stable per environment and changes when Gateway changes',async()=>{
  const server=createEmulator();
  const first=(await request(server,'/__emulator/config')).json;
  assert.match(first.storageScope,/^[a-f0-9]{16}$/);
  assert.equal((await request(server,'/__emulator/config')).json.storageScope,first.storageScope);
  await request(server,'/__emulator/config',{method:'POST',headers:{'content-type':'application/json'},body:'{"gateway":"http://127.0.0.1:9"}'});
  assert.notEqual((await request(server,'/__emulator/config')).json.storageScope,first.storageScope);
});
