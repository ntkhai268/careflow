import assert from 'node:assert/strict';
import {randomUUID} from 'node:crypto';
import {createRequire} from 'node:module';
import {mkdir,writeFile} from 'node:fs/promises';
const require=createRequire(import.meta.url);
const {chromium}=require(process.env.PLAYWRIGHT_MODULE || '/Users/dangkhoii/Library/Caches/ms-playwright-go/1.57.0/package');
const base='http://localhost:3000',password='Password123@',checks=[],findings=[],sessions=[];
let browser,page;
const resources={};
function verify(name,condition,detail) { checks.push({case:name,result:condition?'PASS':'FAIL',...(detail?{detail}:{})});console.log(`${condition?'PASS':'FAIL'} ${name}`); }
function finding(id,severity,description,evidence){findings.push({id,severity,description,evidence});}
async function api(name,path,{method='GET',token,body,statuses=[200],headers={},direct}={}) {
  const response=await fetch((direct || base)+path,{method,headers:{Origin:base,...(token?{Authorization:`Bearer ${token}`} : {}),...(body?{'Content-Type':'application/json'}:{}),...headers},body:body?JSON.stringify(body):undefined,signal:AbortSignal.timeout(20000)});
  const raw=await response.text();let payload;
  try{payload=JSON.parse(raw);}catch{payload={};}
  const ok=statuses.includes(response.status);
  checks.push({case:name,result:ok?'PASS':'FAIL',method,path,status:response.status});
  if(!ok)throw new Error(`${name}: expected ${statuses.join('/')}, actual HTTP ${response.status}`);
  return path.startsWith('/__emulator/')?payload:payload.data;
}
async function login(username) {
  const auth=await api('login-'+username,'/api/auth/login',{method:'POST',body:{usernameOrEmail:username,password}});
  sessions.push(auth.refreshToken);return auth;
}
async function patient(label) {
  const username='auditA'+label+randomUUID().replaceAll('-','').slice(0,10);
  const user=await api('register-'+label,'/api/auth/register',{method:'POST',statuses:[201],body:{username,email:`${username}@example.test`,password,role:'ADMIN'}});
  assert.equal(user.role,'PATIENT');
  const auth=await login(username);
  const profile=await api('profile-'+label,'/api/patients',{method:'POST',statuses:[201],token:auth.accessToken,body:{userId:user.id,fullName:`Audit A ${label}`,gender:'OTHER'}});
  return {user,auth,profile,username};
}
async function waitInbox(token,predicate) {
  for(let attempt=0;attempt<40;attempt++){
    const result=await api('poll-real-inbox','/api/notifications?limit=100',{token});
    if(predicate(result))return result;
    await new Promise(resolve=>setTimeout(resolve,200));
  }
  return api('final-real-inbox','/api/notifications?limit=100',{token});
}
async function connect(id,token,destination='/user/queue/notifications') {
  return page.evaluate(({id,token,destination})=>new Promise(resolve=>{
    window.auditSockets ||= {};
    const socket=new WebSocket(`ws://${location.host}/ws/notifications`),state={socket,messages:[],frames:[],closed:false};window.auditSockets[id]=state;
    let settled=false,buffer='';
    const done=value=>{if(!settled){settled=true;clearTimeout(timer);resolve(value);}};
    const timer=setTimeout(()=>done('TIMEOUT'),3500);
    socket.onopen=()=>socket.send(`CONNECT\naccept-version:1.2\nhost:localhost\nheart-beat:0,0\n${token?`Authorization:Bearer ${token}\n`:''}\n\0`);
    socket.onmessage=event=>{
      buffer+=event.data;let end;
      while((end=buffer.indexOf('\0'))>=0){
        const frame=buffer.slice(0,end).replace(/^\n+/, '');buffer=buffer.slice(end+1);const command=frame.split('\n',1)[0];state.frames.push(command);
        if(command==='CONNECTED'){
          if(destination)socket.send(`SUBSCRIBE\nid:${id}\ndestination:${destination}\nack:auto\n\n\0`);
          done('CONNECTED');
        }
        if(command==='MESSAGE'){try{state.messages.push(JSON.parse(frame.slice(frame.indexOf('\n\n')+2)));}catch{}}
        if(command==='ERROR')done('ERROR');
      }
    };
    socket.onclose=()=>{state.closed=true;done('CLOSED');};socket.onerror=()=>done('ERROR');
  }),{id,token,destination});
}
async function messages(id){return page.evaluate(id=>window.auditSockets[id].messages,id);}
if(!process.argv.includes('--audit-db'))throw Error('Require --audit-db for an owned-process outage test.');
const {execFile}=await import('node:child_process');
const {promisify}=await import('node:util');
const run=promisify(execFile);let stopped=false;
const stopScript=`
import json,os,signal,subprocess,time
from pathlib import Path
s=json.loads(Path('/tmp/careflow-dangkhoii-audit/state.json').read_text())
i=s['services']['careflow-identity-service']
command=subprocess.check_output(['ps','-p',str(i['pid']),'-o','command='],text=True)
assert i['jar'] in command,'PID ownership mismatch'
os.kill(i['pid'],signal.SIGTERM)
for _ in range(200):
 p=subprocess.run(['lsof','-nP','-iTCP:18081','-sTCP:LISTEN'],capture_output=True,text=True)
 if not p.stdout.strip():break
 time.sleep(.1)
else:raise RuntimeError('Identity did not stop listening')
print('Owned primary Identity stopped and port closed')
`;
async function restore(){
  for(let attempt=0;attempt<2;attempt++){
    try{await run('python3',['/tmp/careflow-dangkhoii-audit/restart_owned_service.py','careflow-identity-service','/Users/dangkhoii/careflow/.worktrees/identity-active-session','18081'],{timeout:60000});stopped=false;return;}
    catch(error){if(attempt===1)throw error;}
  }
}
try{
  const config=await api('assert-audit-gateway','/__emulator/config');assert.equal(config.gateway,'http://127.0.0.1:18080');
  const account=await patient('outage');
  browser=await chromium.launch({headless:true,executablePath:process.env.CHROME_PATH || '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome'});
  page=await (await browser.newContext()).newPage();await page.goto(base+'/__emulator/config');
  assert.equal(await connect('outage-active',account.auth.accessToken),'CONNECTED');verify('actual-session-works-before-outage',true);
  stopped=true;await run('python3',['-c',stopScript],{timeout:40000});
  for(let attempt=0;attempt<120;attempt++){
    if(await page.evaluate(()=>window.auditSockets['outage-active'].closed))break;
    await page.waitForTimeout(100);
  }
  assert.ok(await page.evaluate(()=>window.auditSockets['outage-active'].closed));verify('idle-socket-closes-when-Identity-is-unavailable',true);
  assert.notEqual(await connect('outage-new',account.auth.accessToken),'CONNECTED');verify('new-socket-fails-closed-during-real-outage',true);
  await api('REST-fails-closed-during-actual-outage','/api/notifications',{token:account.auth.accessToken,statuses:[503]});
  await restore();
  await api('same-existing-session-works-after-recovery','/api/notifications',{token:account.auth.accessToken});
  assert.equal(await connect('restored',account.auth.accessToken),'CONNECTED');verify('WebSocket-recovers-with-same-valid-token',true);
}catch(error){findings.push({error:error.message});process.exitCode=1;console.error(error.message);}
finally{
  if(stopped)await restore();
  if(page)await page.evaluate(()=>Object.values(window.auditSockets || {}).forEach(state=>state.socket.close())).catch(()=>{});
  if(browser)await browser.close();
  for(const refreshToken of sessions)await api('cleanup-outage-session','/api/auth/logout',{method:'POST',body:{refreshToken}}).catch(()=>{});
  await writeFile(new URL('../.runtime/member-a-identity-outage-results.json',import.meta.url),JSON.stringify({mode:'real',mock:false,checks,findings},null,2)+'\n');
  console.log(JSON.stringify({passed:checks.filter(x=>x.result==='PASS').length,findings}));
}
