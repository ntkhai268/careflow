/* Actual local API checks. No HTTP interception or fabricated backend responses. */
import assert from 'node:assert/strict';
import https from 'node:https';
import {readFile,writeFile} from 'node:fs/promises';
import {AuditClient} from '../security/audit-client.mjs';
const checks=[],clients=[];
function check(name,actual,expected){assert.equal(actual,expected,name);checks.push({name,result:'PASS',actual});console.log('PASS '+name);}
const config=await (await fetch('http://localhost:3000/__emulator/config')).json();check('emulator uses real HTTPS Gateway',config.gateway,'https://localhost:18080');
try {
 for(const [username,role] of [['mobile_case01','PATIENT'],['mobile_case02','PATIENT'],['mobile_case03','PATIENT'],['doctor','DOCTOR'],['an@careflow.vn','DOCTOR'],['staff','STAFF'],['lab','LAB_TECHNICIAN'],['admin','ADMIN']]){
  const client=new AuditClient();clients.push(client);
  const login=await client.login(username);check('real login '+username,login.status,200);check('actual role '+username,login.json.data.user.role,role);
  check('active session '+username,(await client.request('/api/auth/me')).status,200);
 }
 const client=clients[0],other=new AuditClient(),token=client.tokens.accessToken;
 check('token copied to different key denied',(await other.request('/api/auth/me',{token})).status,401);
 check('token without cryptographic proof denied',(await client.request('/api/auth/me',{noProof:true})).status,401);
 const proof=client.proof('GET','/api/auth/me',token);
 check('new proof accepted',(await client.request('/api/auth/me',{proof})).status,200);
 check('same proof replay denied',(await client.request('/api/auth/me',{proof})).status,401);
 check('other patient profile denied',(await clients[1].request('/api/patients/user/'+client.tokens.user.id+'/profiles')).status,403);
 check('private Patient rejects forged user headers',(await client.request('/api/patients/user/'+client.tokens.user.id+'/profiles',{base:'https://localhost:18082',token:null,noProof:true,headers:{'X-User-Id':client.tokens.user.id,'X-User-Role':'ADMIN'}})).status,401);
 const ca=await readFile('/tmp/careflow-dangkhoii-audit/security-hardening/tls/ca.crt');
 const refused=await new Promise(resolve=>{const request=https.get('https://localhost:18081/actuator/health',{ca,rejectUnauthorized:true,timeout:3000},response=>{response.resume();resolve(false);});request.on('error',()=>resolve(true));request.on('timeout',()=>request.destroy());});
 check('private Identity requires actual client TLS certificate',refused,true);
 check('real logout',(await client.request('/api/auth/logout',{method:'POST',body:{refreshToken:client.tokens.refreshToken},token:null})).status,200);
 check('logged-out access denied',(await client.request('/api/auth/me')).status,401);
 check('independent second phone still active',(await clients[1].request('/api/auth/me')).status,200);
} finally {
 for(const client of clients)if(client.tokens)await client.request('/api/auth/logout',{method:'POST',body:{refreshToken:client.tokens.refreshToken},token:null});
 await writeFile(new URL('../../.planning/security/mac-demo-api-results.json',import.meta.url),JSON.stringify({checkedAt:new Date().toISOString(),mock:false,gateway:config.gateway,checks},null,2)+'\n');
}
console.log(checks.length+' actual API/security checks passed.');
