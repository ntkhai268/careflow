import test from 'node:test';
import assert from 'node:assert/strict';
import vm from 'node:vm';
import {readFile} from 'node:fs/promises';
const source=await readFile(new URL('../app/web/browser.js',import.meta.url),'utf8');
function fixture(){
  const sockets=[],posts=[],timeouts=[],intervals=[];
  const location={origin:'http://127.0.0.1:5173',host:'127.0.0.1:5173',protocol:'http:'};
  class WebSocket{
    static OPEN=1;
    constructor(url,protocols){this.url=url;this.protocols=protocols;this.sent=[];this.readyState=1;sockets.push(this);}
    send(value){this.sent.push(value);}
    close(){this.closed=true;this.onclose?.();}
  }
  const listeners={};
  const window={careflowProof:async()=> 'fixture-proof',parent:{postMessage:(data,origin)=>posts.push({data,origin})},addEventListener(name,fn){listeners[name]=fn;}};
  const navigator={geolocation:{getCurrentPosition(callback){callback({coords:{latitude:10.8,longitude:106.7,accuracy:12}});}}};
  const context={window,location,WebSocket,navigator,document:{},setTimeout(fn,ms){const t={fn,ms};timeouts.push(t);return t;},clearTimeout(t){if(t)t.cleared=true;},setInterval(fn,ms){const t={fn,ms};intervals.push(t);return t;},clearInterval(t){if(t)t.cleared=true;},Uint8Array,btoa};
  vm.runInNewContext(source,context);
  const call=async(method,data={})=>JSON.parse(await window.careflowBrowser(method,JSON.stringify(data)));
  return {window,sockets,posts,timeouts,intervals,call,navigator,document:context.document,listeners};
}
test('authenticates STOMP with bearer and subscribes only to personal inbox',async()=>{
  const f=fixture();await f.call('session',{token:'private-jwt'});
  const socket=f.sockets[0];assert.equal(socket.url,'ws://127.0.0.1:5173/ws/notifications');
  await socket.onopen();assert.match(socket.sent[0],/Authorization:Bearer private-jwt/);
  socket.onmessage({data:'CONNECTED\nversion:1.2\n\n\0'});
  assert.match(socket.sent[1],/destination:\/user\/queue\/notifications/);
  assert.ok(f.posts.every(item=>!JSON.stringify(item).includes('private-jwt')));
  assert.equal(f.posts.at(-1).data.state,'connected');
});
test('buffers partial and multiple STOMP frames and refreshes on real messages',async()=>{
  const f=fixture();let refreshes=0;f.window.careflowListenNotifications(()=>refreshes++);
  await f.call('session',{token:'jwt'});const socket=f.sockets[0];
  socket.onmessage({data:'\nMESS'});assert.equal(refreshes,0);
  socket.onmessage({data:'AGE\n\n{}\0MESSAGE\n\n{}\0'});assert.equal(refreshes,2);
});
test('reuses socket for unchanged token, rotates connection and closes on logout',async()=>{
  const f=fixture();await f.call('session',{token:'old'});await f.call('session',{token:'old'});assert.equal(f.sockets.length,1);
  await f.call('session',{token:'new'});assert.equal(f.sockets[0].closed,true);assert.equal(f.sockets.length,2);
  await f.call('session',{token:''});assert.equal(f.sockets[1].closed,true);assert.equal(f.posts.at(-1).data.state,'disconnected');
});
test('reconnects on transport failure and sends protocol heartbeat',async()=>{
  const f=fixture();await f.call('session',{token:'jwt'});const socket=f.sockets[0];
  socket.onmessage({data:'CONNECTED\n\n\0'});f.intervals[0].fn();assert.equal(socket.sent.at(-1),'\n');
  socket.onclose();assert.equal(f.intervals[0].cleared,true);assert.equal(f.timeouts.at(-1).ms,10000);
  f.timeouts.at(-1).fn();assert.equal(f.sockets.length,2);
});
test('returns browser GPS without changing coordinates or accuracy',async()=>{
  const f=fixture();assert.deepEqual((await f.call('position')).data,{latitude:10.8,longitude:106.7,accuracy:12});
});
test('reports denied location and unknown methods without pretending success',async()=>{
  const f=fixture();f.navigator.geolocation.getCurrentPosition=(_,reject)=>reject();
  assert.match((await f.call('position')).error,/vị trí/);assert.match((await f.call('unsupported')).error,/không được hỗ trợ/);
});

test('file picker encodes exact binary bytes and keeps filenames',async()=>{
  const f=fixture();
  const input={files:[{name:'résultat.pdf',size:5,arrayBuffer:async()=>new Uint8Array([0,1,127,128,255]).buffer}],style:{},remove(){},click(){queueMicrotask(()=>this.onchange());}};
  f.document.createElement=()=>input;f.document.body={append(){}};
  const result=await f.call('files');
  assert.equal(result.data[0].name,'résultat.pdf');
  assert.deepEqual([...Buffer.from(result.data[0].base64,'base64')],[0,1,127,128,255]);
});
test('cancelling file picker returns an empty selection',async()=>{
  const f=fixture();const input={style:{},remove(){},click(){queueMicrotask(()=>this.oncancel());}};
  f.document.createElement=()=>input;f.document.body={append(){}};
  assert.deepEqual((await f.call('files')).data,[]);
});

test('accepts device controls from its actual parent at the same origin only',()=>{
  const f=fixture(),commands=[];f.window.careflowListenControls(command=>commands.push(command));
  const data={type:'careflow:control',command:'appointments'};
  f.listeners.message({origin:'https://external.example',source:f.window.parent,data});
  f.listeners.message({origin:'http://127.0.0.1:5173',source:{},data});
  f.listeners.message({origin:'http://127.0.0.1:5173',source:f.window.parent,data:{...data,command:'arbitrary'}});
  assert.deepEqual(commands,[]);
  f.listeners.message({origin:'http://127.0.0.1:5173',source:f.window.parent,data});
  assert.deepEqual(commands,['appointments']);
});
