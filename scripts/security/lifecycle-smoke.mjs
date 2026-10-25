import assert from 'node:assert/strict';
import {randomUUID} from 'node:crypto';
import {readFile,writeFile} from 'node:fs/promises';
import {execFile} from 'node:child_process';
import {promisify} from 'node:util';
import {AuditClient} from './audit-client.mjs';
if(!process.argv.includes('--audit-db'))throw Error('--audit-db required');
const run=promisify(execFile),cases=[],sockets=[];
const check=(name,value,expected)=>{assert.equal(value,expected,name);cases.push({name,status:'PASS'});console.log('PASS',name);};
const wait=ms=>new Promise(resolve=>setTimeout(resolve,ms));
const first=new AuditClient(),second=new AuditClient(),admin=new AuditClient();
const username='lifecycle_'+randomUUID().replaceAll('-','').slice(0,12),password='Password123@';
let stopped=false;
try {
 check('create real lifecycle account',(await first.request('/api/auth/register',{method:'POST',body:{username,email:username+'@example.test',password}})).status,201);
 check('first client authenticates',(await first.login(username)).status,200);
 check('second client authenticates',(await second.login(username)).status,200);
 check('administrator authenticates',(await admin.login('admin')).status,200);
 const user=first.tokens.user.id;
 check('patient cannot assign own ADMIN role',(await first.request('/api/users/'+user+'/role',{method:'PATCH',body:{role:'ADMIN'},headers:{'X-User-Role':'ADMIN'}})).status,403);
 const proof=first.proof('GET','/api/auth/me',first.tokens.accessToken);
 check('first Identity accepts proof',(await first.request('/api/auth/me',{base:'https://localhost:18081',proof})).status,200);
 check('second Identity rejects the same proof',(await first.request('/api/auth/me',{base:'https://localhost:18092',proof})).status,401);
 const failures=await Promise.all(Array.from({length:5},()=>first.login(username,'incorrect-password')));
 check('five concurrent wrong passwords rejected',failures.filter(value=>value.status===401).length,5);
 check('account is locked after concurrent failures',(await first.login(username)).status,403);
 check('auto-lock revokes first session',(await first.request('/api/auth/me')).status,401);
 check('auto-lock revokes second session',(await second.request('/api/auth/me')).status,401);
 check('administrator unlocks account',(await admin.request('/api/users/'+user+'/status',{method:'PATCH',body:{status:'ACTIVE'}})).status,200);
 check('unlock does not revive old access',(await first.request('/api/auth/me')).status,401);
 check('user can reauthenticate after unlock',(await first.login(username)).status,200);
 const socket=await first.socket();sockets.push(socket);check('real bound WebSocket connects',socket.readyState,WebSocket.OPEN);
 check('administrator changes role',(await admin.request('/api/users/'+user+'/role',{method:'PATCH',body:{role:'DOCTOR'}})).status,200);
 check('old role token rejected',(await first.request('/api/auth/me')).status,401);
 for(let i=0;i<40&&!socket.closed;i++)await wait(100);
 check('role change closes existing socket',socket.closed,true);
 check('new login receives current role',(await first.login(username)).json.data.user.role,'DOCTOR');
 check('disable account',(await admin.request('/api/users/'+user+'/status',{method:'PATCH',body:{status:'DISABLED'}})).status,200);
 check('disabled user access rejected',(await first.request('/api/auth/me')).status,401);
 check('disabled user cannot log in',(await first.login(username)).status,403);
 check('reenable account',(await admin.request('/api/users/'+user+'/status',{method:'PATCH',body:{status:'ACTIVE'}})).status,200);
 check('reenable cannot revive token',(await first.request('/api/auth/me')).status,401);
 await first.login(username);await second.login(username);
 check('logout-all succeeds',(await first.request('/api/auth/logout-all',{method:'POST',body:{}})).status,200);
 check('logout-all revokes first key/session',(await first.request('/api/auth/me')).status,401);
 check('logout-all revokes second key/session',(await second.request('/api/auth/me')).status,401);
 const expiry=new AuditClient(process.env.CAREFLOW_SECURITY_EXPIRY_URL || 'https://localhost:18193');
 check('real short-TTL Identity issues token',(await expiry.login(username)).status,200);
 check('short-TTL token initially accepted',(await expiry.request('/api/auth/me',{base:'http://localhost:3000'})).status,200);
 await wait(3500);
 check('expired real access token rejected',(await expiry.request('/api/auth/me',{base:'http://localhost:3000'})).status,401);
 for(const [port,path] of [[18082,'/api/patients/user/'+user],[18083,'/api/appointments/patient/'+randomUUID()],[18084,'/api/queues/entries'],[18085,'/api/notifications'],[18086,'/api/consultations/'+randomUUID()],[18087,'/api/prescriptions/'+randomUUID()],[18088,'/api/emr/records/patient/'+randomUUID()],[18089,'/api/labs/orders/patient/'+randomUUID()],[18090,'/api/directory/staff/assignments']]) {
  check('direct service '+port+' rejects forged identity without user token',(await first.request(path,{base:'https://localhost:'+port,token:null,noProof:true,headers:{'X-User-Id':user,'X-User-Role':'ADMIN'}})).status,401);
 }
 if(process.argv.includes('--identity-outage')) {
 await first.login(username);
 const outageSocket=await first.socket();sockets.push(outageSocket);
 const state=JSON.parse(await readFile('/tmp/careflow-dangkhoii-audit/state.json','utf8')),info=state.services['careflow-identity-service'];
 const command=(await run('ps',['-p',String(info.pid),'-o','command='])).stdout;assert.ok(command.includes(info.jar));
 process.kill(info.pid,'SIGTERM');stopped=true;await wait(1000);
 check('Gateway fails closed when Identity is down',(await first.request('/api/auth/me')).status,503);
 check('direct Patient fails closed when Identity is down',(await first.request('/api/patients/user/'+user,{base:'https://localhost:18082'})).status,503);
 for(let i=0;i<40&&!outageSocket.closed;i++)await wait(100);
 check('Identity outage closes idle notification socket',outageSocket.closed,true);
 }
 console.log('RESULT',cases.length,'lifecycle checks passed');
}finally{
 if(stopped){
  await run('python3',[new URL('./restart-audit-service.py',import.meta.url).pathname,'--audit-db','careflow-identity-service'],{timeout:60000});
  check('same active token works after Identity recovery',(await first.request('/api/auth/me')).status,200);
 }
 for(const socket of sockets)socket.close();
 for(const client of [first,second,admin])if(client.tokens)await client.request('/api/auth/logout',{method:'POST',body:{refreshToken:client.tokens.refreshToken},token:null}).catch(()=>{});
 await writeFile('/tmp/careflow-security-lifecycle.json',JSON.stringify({checkedAt:new Date().toISOString(),mock:false,cases},null,2));
}
