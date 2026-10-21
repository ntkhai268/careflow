// Real Gateway/Identity/PostgreSQL regression. No response stubs or virtual authenticators.
import assert from 'node:assert/strict';
import {randomUUID} from 'node:crypto';
import {writeFile} from 'node:fs/promises';
import {AuditClient} from './audit-client.mjs';
if(!process.argv.includes('--audit-db'))throw Error('--audit-db required');
assert.equal((await(await fetch('http://localhost:3000/__emulator/config')).json()).gateway,'https://localhost:18080');
const checks=[],password=process.env.CAREFLOW_AUDIT_PASSWORD;
assert.ok(password, 'Set CAREFLOW_AUDIT_PASSWORD from the private demo configuration');
async function expect(client,path,options={},status=200){const r=await client.request(path,options);assert.equal(r.status,status,`${path}: ${r.status} ${r.json.message}`);return r.json.data;}
function pass(name){checks.push({name,status:'PASS'});console.log('PASS',name);}
const username='login_methods_'+randomUUID().slice(0,8),c=new AuditClient(),answer=randomUUID();
await expect(c,'/api/auth/register',{method:'POST',token:null,body:{username,email:username+'@example.test',password}},201);
assert.equal((await c.login(username,password)).status,200);pass('Password-only login returns a real session without MFA');
await expect(c,'/api/auth/mfa/setup',{method:'POST',body:{password}},410);pass('Retired MFA API rejects enrollment with 410');
await expect(c,'/api/auth/login-methods/question',{method:'POST',body:{password:'WrongPassword1@',questionId:'memory',answer}},401);
await expect(c,'/api/auth/login-methods/question',{method:'POST',body:{password,questionId:'memory',answer}});pass('Question setup requires the current password');
const q=new AuditClient();
await expect(q,'/api/auth/login/question',{method:'POST',token:null,body:{usernameOrEmail:username,questionId:'memory',answer:'wrong answer'}},401);
await expect(q,'/api/auth/login/question',{method:'POST',token:null,body:{usernameOrEmail:username,questionId:'memory',answer:'ế'.repeat(40)}},401);
q.tokens=await expect(q,'/api/auth/login/question',{method:'POST',token:null,body:{usernameOrEmail:username.toUpperCase(),questionId:'memory',answer:'  '+answer.toUpperCase()+'  '}});
await expect(q,'/api/auth/me');pass('Wrong answer rejected; normalized correct answer establishes a proof-bound session');
const thief=new AuditClient();await expect(thief,'/api/auth/me',{token:q.tokens.accessToken},401);pass('Copied access token cannot be used with a different client key');
const start=await expect(c,'/api/auth/passkeys/login/options',{method:'POST',token:null,body:{usernameOrEmail:username}});
await expect(thief,'/api/auth/passkeys/login/finish',{method:'POST',token:null,body:{requestId:start.requestId,credential:'{}'}},401);
await expect(c,'/api/auth/passkeys/login/finish',{method:'POST',token:null,body:{requestId:start.requestId,credential:'{}'}},401);
await expect(c,'/api/auth/passkeys/login/finish',{method:'POST',token:null,body:{requestId:start.requestId,credential:'{}'}},401);pass('Passkey challenge bound to client, invalid proof rejected, consumed challenge cannot replay');
const reset=new AuditClient(),newPassword='Changed-'+randomUUID()+'aA!';
await expect(reset,'/api/auth/recovery/question',{method:'POST',token:null,body:{usernameOrEmail:username,questionId:'memory',answer,newPassword:'weak'}},400);
await expect(reset,'/api/auth/recovery/question',{method:'POST',token:null,body:{usernameOrEmail:username,questionId:'memory',answer,newPassword}});
await expect(c,'/api/auth/me',{},401);await expect(q,'/api/auth/me',{},401);
await expect(q,'/api/auth/refresh',{method:'POST',token:null,body:{refreshToken:q.tokens.refreshToken}},401);
assert.equal((await c.login(username,password)).status,401);assert.equal((await c.login(username,newPassword)).status,200);pass('Password reset enforces strength, revokes sessions/refresh tokens and replaces the password');
for(let i=0;i<5;i++)await expect(reset,'/api/auth/login/question',{method:'POST',token:null,body:{usernameOrEmail:username,questionId:'memory',answer:'incorrect secret'}},401);
assert.equal((await c.login(username,newPassword)).status,403);await expect(c,'/api/auth/me',{},401);pass('Five wrong secret answers lock the account and revoke active sessions');
await writeFile(new URL('../../.planning/identity-service/login-methods-live-results.json',import.meta.url),JSON.stringify({checkedAt:new Date().toISOString(),gateway:'https://localhost:18080',database:'real local PostgreSQL audit database',mock:false,checks,biometricHardware:'Not verified by this regression; requires real user verification'},null,2)+'\n');
console.log(checks.length+' live authentication checks passed');
