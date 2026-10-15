import test from 'node:test';
import assert from 'node:assert/strict';
import http from 'node:http';
import net from 'node:net';
import {once} from 'node:events';
import {createEmulator, normalizeGateway} from '../../server.mjs';

async function listen(server) {
  server.listen(0,'127.0.0.1'); await once(server,'listening');
  return `http://127.0.0.1:${server.address().port}`;
}
async function setup(t, handler) {
  const backend = http.createServer(handler ?? ((req,res) => { res.writeHead(200,{'Content-Type':'application/json'}); res.end('{"status":"UP"}'); }));
  const connections = new Set();
  const track = socket => { connections.add(socket); socket.on('close', () => connections.delete(socket)); };
  backend.on('connection', track);
  const upstream = await listen(backend);
  const emulator = createEmulator({gateway: upstream});
  emulator.on('connection', track);
  const url = await listen(emulator);
  t.after(async () => {
    for (const socket of connections) socket.destroy();
    backend.closeAllConnections(); emulator.closeAllConnections();
    await Promise.all([new Promise(r => backend.close(r)),new Promise(r => emulator.close(r))]);
  });
  return {backend,emulator,url,upstream};
}

test('gateway URL rejects credentials, paths, protocols and queries', () => {
  assert.equal(normalizeGateway('http://127.0.0.1:8080/api/'),'http://127.0.0.1:8080');
  for (const value of ['ftp://example.com','https://u:p@example.com','https://example.com/private','https://example.com/?token=secret']) assert.throws(() => normalizeGateway(value));
});

test('serves shell and compiled Flutter, blocks source and hidden files', async t => {
  const {url} = await setup(t);
  assert.equal((await fetch(url)).status,200);
  const mobile = await fetch(url+'/mobile/');
  assert.equal(mobile.status,200); assert.match(await mobile.text(),/flutter_bootstrap/);
  for (const path of ['/server.mjs','/app/lib/main.dart','/.env','/mobile/.env','/mobile/%2e%2e%2fpubspec.yaml']) assert.equal((await fetch(url+path)).status,404,path);
  const wasm = await fetch(url+'/mobile/canvaskit/canvaskit.wasm');
  assert.equal(wasm.status,200); assert.equal(wasm.headers.get('content-type'),'application/wasm');
});

test('forwards exact API request and JWT, strips impersonation headers and redacts activity', async t => {
  let received;
  const id = 'f0000001-0000-0000-0000-000000000001';
  const {url} = await setup(t, async (req,res) => {
    let data=''; for await (const chunk of req) data+=chunk;
    received={headers:req.headers,path:req.url,method:req.method,data};
    res.writeHead(201,{'Content-Type':'application/json'});res.end('{"status":201,"data":{"ok":true}}');
  });
  const response = await fetch(`${url}/api/patients/${id}/health-records?private=hidden`, {method:'POST',headers:{Authorization:'Bearer test-secret','X-User-Id':'forged','X-User-Role':'ADMIN','Content-Type':'application/json'},body:'{"name":"private"}'});
  assert.equal(response.status,201);
  assert.equal(received.headers.authorization,'Bearer test-secret');
  assert.equal(received.headers['x-user-id'],undefined); assert.equal(received.headers['x-user-role'],undefined);
  assert.equal(received.path,`/api/patients/${id}/health-records?private=hidden`);
  assert.equal(received.data,'{"name":"private"}');
  const activity = await (await fetch(url+'/__emulator/activity')).text();
  for (const sensitive of ['test-secret','forged',id,'hidden','private']) assert.ok(!activity.includes(sensitive));
  assert.match(activity,/:id/);
});

test('streams multipart body intact without changing boundary', async t => {
  let got;
  const {url} = await setup(t, async (req,res) => {
    let data=''; for await (const chunk of req) data+=chunk;
    got={data,contentType:req.headers['content-type']};res.end('{}');
  });
  const form=new FormData();form.set('image',new Blob(['png-binary']), 'id.png');
  await fetch(url+'/api/auth/ekyc',{method:'POST',body:form});
  assert.match(got.contentType,/multipart\/form-data; boundary=/);
  assert.match(got.data,/name="image"; filename="id.png"/);assert.match(got.data,/png-binary/);
});

test('keeps upstream authorization failures and messages', async t => {
  const {url} = await setup(t, (req,res) => {res.writeHead(403,{'Content-Type':'application/json'});res.end('{"status":403,"message":"Forbidden"}');});
  const response = await fetch(url+'/api/queues/me/status');
  assert.equal(response.status,403);assert.equal((await response.json()).message,'Forbidden');
});

test('rejects cross-origin config changes and API requests', async t => {
  const {url,upstream} = await setup(t);
  const response = await fetch(url+'/__emulator/config',{method:'POST',headers:{Origin:'https://external.example','Content-Type':'application/json'},body:JSON.stringify({gateway:'https://example.com'})});
  assert.equal(response.status,403);assert.equal((await (await fetch(url+'/__emulator/config')).json()).gateway,upstream);
  assert.equal((await fetch(url+'/api/auth/me',{headers:{'Sec-Fetch-Site':'cross-site'}})).status,403);
});

test('switches gateway and clears request activity', async t => {
  const {url} = await setup(t);
  await fetch(url+'/api/auth/me');
  const response = await fetch(url+'/__emulator/config',{method:'POST',headers:{'Content-Type':'application/json',Origin:url},body:JSON.stringify({gateway:'http://127.0.0.1:9/api'})});
  assert.equal(response.status,200);assert.deepEqual(await (await fetch(url+'/__emulator/activity')).json(),[]);
  const failed = await fetch(url+'/api/auth/me');assert.equal(failed.status,502);assert.match((await failed.json()).message,/Gateway/);
  assert.equal((await (await fetch(url+'/__emulator/health')).json()).reachable,false);
});

test('reports reachable health response',async t => {
  const {url} = await setup(t);
  const response = await (await fetch(url+'/__emulator/health')).json();
  assert.equal(response.reachable,true);assert.equal(response.status,200);
});

test('proxies WebSocket upgrade, browser origin, and STOMP bytes unchanged', async t => {
  const {url,backend} = await setup(t);
  let headers;
  backend.on('upgrade',(req,socket,head) => {
    headers = req.headers;
    socket.write('HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\n\r\n');
    if (head.length) socket.write(head);
    socket.on('data',data => socket.write(data));
    socket.on('error',()=>{});
  });
  const address=new URL(url);
  const socket=net.connect({host:address.hostname,port:Number(address.port)});
  t.after(() => socket.destroy());
  await once(socket,'connect');
  const first=once(socket,'data');
  socket.write(`GET /ws/notifications HTTP/1.1\r\nHost: ${address.host}\r\nOrigin: ${url}\r\nConnection: Upgrade\r\nUpgrade: websocket\r\nSec-WebSocket-Key: dGVzdC1rZXktdGVzdC1rZXk=\r\nSec-WebSocket-Version: 13\r\n\r\n`);
  assert.match((await first)[0].toString(),/101 Switching Protocols/);
  assert.equal(headers.origin,url);
  const echo=once(socket,'data');socket.write('STOMP-BYTES');assert.equal((await echo)[0].toString(),'STOMP-BYTES');
  socket.destroy();
});
