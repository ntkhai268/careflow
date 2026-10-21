// Focused regression for refresh denial, account races and offline behavior.
const fs=require('node:fs'),path=require('node:path'),vm=require('node:vm'),assert=require('node:assert/strict');
const root=path.resolve(__dirname,'../..'),ts=require(path.join(root,'frontend/doctor-web/node_modules/typescript'));
function load(file,dependencies,globals={}) {
 const code=ts.transpileModule(fs.readFileSync(path.join(root,file),'utf8'),{compilerOptions:{module:ts.ModuleKind.CommonJS,target:ts.ScriptTarget.ES2022}}).outputText;
 const module={exports:{}};vm.runInNewContext(code,{module,exports:module.exports,require:x=>dependencies[x],process:{env:{}},...globals});return module.exports;
}
(async()=>{
 let respond,events=0;
 const memory=load('frontend/doctor-web/src/lib/session-memory.ts',{});
 const api=load('frontend/doctor-web/src/lib/api.ts',{'./session-memory':memory,'./dpop':{createProof:async()=> 'fixture'},'./error-utils':{getErrorMessage:e=>e.message}},{navigator:{},Event:class Event{constructor(type){this.type=type;}},window:{dispatchEvent:event=>{assert.equal(event.type,'careflow:session-expired');events++;}},fetch:async()=>new Promise(resolve=>{respond=resolve;}),console});
 async function reply(status){while(!respond)await new Promise(setImmediate);const done=respond;respond=undefined;done({ok:false,status,json:async()=>({message:'denied'})});}
 memory.setAccessToken('active');let pending=api.refreshAccessToken();await reply(401);await assert.rejects(pending);assert.equal(memory.getAccessToken(),null);assert.equal(events,1);
 memory.setAccessToken('active');pending=api.refreshAccessToken();await reply(403);await assert.rejects(pending);assert.equal(memory.getAccessToken(),null);assert.equal(events,2);
 memory.setAccessToken('old');pending=api.refreshAccessToken();while(!respond)await new Promise(setImmediate);memory.setAccessToken('new-account');await reply(401);await assert.rejects(pending);assert.equal(memory.getAccessToken(),'new-account');assert.equal(events,2);
 pending=api.refreshAccessToken();await reply(503);await assert.rejects(pending);assert.equal(memory.getAccessToken(),'new-account');assert.equal(events,2);
 console.log('Web expiry: 401/403 clear session, stale denial preserves new account, 503 preserves retryable state.');
})().catch(error=>{console.error(error);process.exitCode=1;});
