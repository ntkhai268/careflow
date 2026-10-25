import assert from 'node:assert/strict';
import {generateKeyPairSync,sign,createHash,createHmac,randomUUID} from 'node:crypto';
import {writeFile,readFile} from 'node:fs/promises';
import https from 'node:https';
if(!process.argv.includes('--audit-db'))throw Error('Explicit --audit-db required');
const base=process.env.CAREFLOW_SECURITY_TEST_URL || 'http://127.0.0.1:18181';
assert.match(base,/^(http:\/\/127\.0\.0\.1|https:\/\/localhost):18[01][0-9]{2}$/);
const origin='http://localhost:3001',checks=[];
const sha=value=>createHash('sha256').update(value).digest('base64url');
function client(){const keys=generateKeyPairSync('ec',{namedCurve:'prime256v1'}),pub=keys.publicKey.export({format:'jwk'});return {keys,pub,jkt:sha(JSON.stringify({crv:'P-256',kty:'EC',x:pub.x,y:pub.y}))};}
function proof(c,method,path,token,extra={}){
 const header=Buffer.from(JSON.stringify({typ:'dpop+jwt',alg:'ES256',jwk:c.pub})).toString('base64url');
 const payload=Buffer.from(JSON.stringify({jti:randomUUID(),htm:method,htu:origin+path,iat:Math.floor(Date.now()/1000),...(token?{ath:sha(token)}:{}),...extra})).toString('base64url');
 const content=header+'.'+payload;return content+'.'+sign('sha256',Buffer.from(content),{key:c.keys.privateKey,dsaEncoding:'ieee-p1363'}).toString('base64url');
}
async function request(c,method,path,body,token,options={}){
 const headers={'content-type':'application/json',...(token?{Authorization:'Bearer '+token}:{}),...(!options.noProof?{DPoP:options.proof || proof(c,method,path,token,options.claims)}:{}),...options.headers};
 let r;
 const url=(options.base||base)+path;
 if(process.env.CAREFLOW_SECURITY_TLS_DIR && url.startsWith('https:')) {
  const dir=process.env.CAREFLOW_SECURITY_TLS_DIR;
  assert.equal(dir,'/tmp/careflow-dangkhoii-audit/security-hardening/tls');
  const [ca,cert,key]=await Promise.all(['ca.crt','api-gateway.crt','api-gateway.key'].map(name=>readFile(dir+'/'+name)));
  r=await new Promise((resolve,reject)=>{
   const req=https.request(url,{method,headers,ca,cert,key,rejectUnauthorized:true},response=>{
    const chunks=[];response.on('data',chunk=>chunks.push(chunk));response.on('end',()=>resolve({status:response.statusCode,json:async()=>JSON.parse(Buffer.concat(chunks).toString())}));
   });req.on('error',reject);req.end(body?JSON.stringify(body):undefined);
  });
 }else r=await fetch(url,{method,headers,...(body?{body:JSON.stringify(body)}:{})});
 let json;try{json=await r.json();}catch{json={};}return {status:r.status,json};
}
function check(name,actual,expected){assert.equal(actual,expected,name);checks.push({name,status:'PASS'});console.log('PASS',name);}
function code(secret,step){const alphabet='ABCDEFGHIJKLMNOPQRSTUVWXYZ234567';let bits='';for(const s of secret)bits+=alphabet.indexOf(s).toString(2).padStart(5,'0');const bytes=[];for(let i=0;i+8<=bits.length;i+=8)bytes.push(parseInt(bits.slice(i,i+8),2));const count=Buffer.alloc(8);count.writeBigUInt64BE(BigInt(step));const h=createHmac('sha1',Buffer.from(bytes)).update(count).digest(),offset=h[h.length-1]&15;return String((h.readUInt32BE(offset)&0x7fffffff)%1000000).padStart(6,'0');}
const c=client(),other=client(),username='secure_'+randomUUID().replaceAll('-','').slice(0,16),password='Password123@';
let tokens;
const login=(extra={},who=c)=>request(who,'POST','/api/auth/login',{usernameOrEmail:username,password,...extra});
try{
 check('real registration',(await request(c,'POST','/api/auth/register',{username,email:username+'@example.test',password})).status,201);
 check('login requires cryptographic proof',(await request(c,'POST','/api/auth/login',{usernameOrEmail:username,password},null,{noProof:true})).status,401);
 let r=await login();check('bound login succeeds',r.status,200);tokens=r.json.data;
 const parts=tokens.accessToken.split('.'),header=JSON.parse(Buffer.from(parts[0],'base64url')),claims=JSON.parse(Buffer.from(parts[1],'base64url'));
 check('Identity signs RS256',header.alg,'RS256');check('JWT audience',claims.aud.includes('careflow-api'),true);check('JWT binds client public key',claims.cnf.jkt,c.jkt);
 check('direct request with proof',(await request(c,'GET','/api/auth/me',null,tokens.accessToken)).status,200);
 check('stolen token without proof rejected',(await request(c,'GET','/api/auth/me',null,tokens.accessToken,{noProof:true})).status,401);
 check('stolen token with attacker key rejected',(await request(other,'GET','/api/auth/me',null,tokens.accessToken)).status,401);
 check('wrong method rejected',(await request(c,'GET','/api/auth/me',null,tokens.accessToken,{claims:{htm:'POST'}})).status,401);
 check('wrong URI rejected',(await request(c,'GET','/api/auth/me',null,tokens.accessToken,{claims:{htu:origin+'/api/auth/mfa'}})).status,401);
 check('expired proof rejected',(await request(c,'GET','/api/auth/me',null,tokens.accessToken,{claims:{iat:Math.floor(Date.now()/1000)-120}})).status,401);
 const once=proof(c,'GET','/api/auth/me',tokens.accessToken);
 check('first proof accepted',(await request(c,'GET','/api/auth/me',null,tokens.accessToken,{proof:once})).status,200);
 if(process.env.CAREFLOW_SECURITY_SECONDARY){check('same proof rejected by another Identity instance',(await request(c,'GET','/api/auth/me',null,tokens.accessToken,{proof:once,base:process.env.CAREFLOW_SECURITY_SECONDARY})).status,401);}
 check('replayed proof rejected',(await request(c,'GET','/api/auth/me',null,tokens.accessToken,{proof:once})).status,401);
 check('refresh rejects wrong client key',(await request(other,'POST','/api/auth/refresh',{refreshToken:tokens.refreshToken})).status,401);
 const old=tokens;
 r=await request(c,'POST','/api/auth/refresh',{refreshToken:tokens.refreshToken});check('bound refresh rotation',r.status,200);tokens=r.json.data;
 check('rotation revokes old access token',(await request(c,'GET','/api/auth/me',null,old.accessToken)).status,401);
 check('wrong-key refresh did not revoke valid session',(await request(c,'GET','/api/auth/me',null,tokens.accessToken)).status,200);
 if(process.env.CAREFLOW_SECURITY_PATIENT){
  r=await request(c,'POST','/api/patients',{userId:tokens.user.id,fullName:'Security Audit Patient'},tokens.accessToken);if(r.status!==201)console.log('Patient create rejection message:',r.json.message);check('Gateway creates owned patient with bound token',r.status,201);
  const patient=r.json.data.id,path='/api/patients/'+patient,direct=process.env.CAREFLOW_SECURITY_PATIENT;
  check('Gateway reads owned patient',(await request(c,'GET',path,null,tokens.accessToken)).status,200);
  check('direct Patient verifies proof independently',(await request(c,'GET',path,null,tokens.accessToken,{base:direct})).status,200);
  check('direct Patient rejects stolen token without proof',(await request(c,'GET',path,null,tokens.accessToken,{base:direct,noProof:true})).status,401);
  check('direct Patient rejects attacker key',(await request(other,'GET',path,null,tokens.accessToken,{base:direct})).status,401);
  check('forged caller credentials rejected',(await request(c,'GET',path,null,tokens.accessToken,{base:direct,headers:{'X-Careflow-Caller':'appointment-service','X-Careflow-Caller-Key':'forged'}})).status,401);
  check('patient cannot create profile for another user',(await request(c,'POST','/api/patients',{userId:randomUUID(),fullName:'Must Not Exist'},tokens.accessToken)).status,403);
  if(process.env.CAREFLOW_SECURITY_TLS_DIR){
   const config=JSON.parse(await readFile('/tmp/careflow-dangkhoii-audit/security-hardening/private-config.json','utf8'));
   check('valid certificate cannot impersonate another registered service',(await request(c,'GET','/api/auth/session',null,tokens.accessToken,{base:base.includes(':18080')?'https://localhost:18081':'https://localhost:18195',headers:{'X-Careflow-Caller':'patient-service','X-Careflow-Caller-Key':config.callers['patient-service'].secret}})).status,401);
   check('valid caller certificate and key cannot exceed caller scope',(await request(c,'GET',path,null,tokens.accessToken,{base:direct,noProof:true,headers:{'X-Careflow-Caller':'api-gateway','X-Careflow-Caller-Key':config.callers['api-gateway'].secret}})).status,403);
   check('public Gateway strips private caller credentials and still requires client proof',(await request(c,'GET','/api/auth/session',null,tokens.accessToken,{noProof:true,headers:{'X-Careflow-Caller':'api-gateway','X-Careflow-Caller-Key':config.callers['api-gateway'].secret}})).status,401);
  }

 }

 check('MFA setup requires password',(await request(c,'POST','/api/auth/mfa/setup',{password:'wrong'},tokens.accessToken)).status,401);
 r=await request(c,'POST','/api/auth/mfa/setup',{password},tokens.accessToken);check('real MFA enrollment',r.status,200);const secret=r.json.data.secret;
 const now=Math.floor(Date.now()/30000);
 r=await request(c,'POST','/api/auth/mfa/confirm',{password,otp:code(secret,now)},tokens.accessToken);check('confirm real TOTP',r.status,200);const recovery=r.json.data.recoveryCodes;assert.equal(recovery.length,10);
 check('MFA enrollment revokes previous access',(await request(c,'GET','/api/auth/me',null,tokens.accessToken)).status,401);
 r=await login();check('password alone cannot bypass enabled MFA',r.status,401);assert.match(r.json.message,/MFA_REQUIRED/);
 check('used enrollment TOTP cannot log in',(await login({otp:code(secret,now)})).status,401);
 r=await login({otp:code(secret,now+1)});check('password and fresh TOTP login',r.status,200);tokens=r.json.data;
 r=await request(c,'GET','/api/auth/mfa',null,tokens.accessToken);check('enabled MFA status',r.json.data.enabled,true);assert.ok(!('secret' in r.json.data));
 check('enabled MFA secret cannot be replaced',(await request(c,'POST','/api/auth/mfa/setup',{password},tokens.accessToken)).status,409);
 r=await login({recoveryCode:recovery[0]});check('one-use recovery login',r.status,200);tokens=r.json.data;
 if(process.env.CAREFLOW_SECURITY_SECONDARY){
  const parallel=await Promise.all([login({recoveryCode:recovery[2]}),request(c,'POST','/api/auth/login',{usernameOrEmail:username,password,recoveryCode:recovery[2]},null,{base:process.env.CAREFLOW_SECURITY_SECONDARY})]);
  check('one recovery succeeds across two Identity instances',parallel.filter(r=>r.status===200).length,1);
  check('one concurrent recovery is rejected',parallel.filter(r=>r.status===401).length,1);
 }
 check('recovery code replay rejected',(await login({recoveryCode:recovery[0]})).status,401);
 check('MFA disable requires password and factor',(await request(c,'POST','/api/auth/mfa/disable',{password},tokens.accessToken)).status,401);
 r=await request(c,'POST','/api/auth/mfa/disable',{password,recoveryCode:recovery[1]},tokens.accessToken);check('controlled MFA disable',r.status,200);
 check('disable revokes previous access',(await request(c,'GET','/api/auth/me',null,tokens.accessToken)).status,401);
 r=await login();check('login after controlled disable',r.status,200);tokens=r.json.data;
 check('wrong-key logout rejected',(await request(other,'POST','/api/auth/logout',{refreshToken:tokens.refreshToken})).status,401);
 check('correct bound logout',(await request(c,'POST','/api/auth/logout',{refreshToken:tokens.refreshToken})).status,200);
 check('logged-out token rejected',(await request(c,'GET','/api/auth/me',null,tokens.accessToken)).status,401);
 console.log('RESULT',checks.length,'checks passed');
}finally{
 await writeFile(process.env.CAREFLOW_SECURITY_RESULTS || '/tmp/careflow-security-hardening-http.json',JSON.stringify({checkedAt:new Date().toISOString(),base,passed:checks.length,checks},null,2));
}
