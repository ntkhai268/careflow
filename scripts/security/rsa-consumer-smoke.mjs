/* Positive and negative real RSA/session/proof checks on all former HMAC consumers. */
import assert from 'node:assert/strict';
import {readFile,writeFile} from 'node:fs/promises';
import {sign} from 'node:crypto';
import {AuditClient} from './audit-client.mjs';
if(!process.argv.includes('--audit-db'))throw Error('--audit-db required');
assert.equal((await(await fetch('http://localhost:3000/__emulator/config')).json()).gateway,'https://localhost:18080');
const account=JSON.parse(await readFile(new URL('../../careflow-mobile-emulator-service/.runtime/e2e-noi-account.json',import.meta.url),'utf8'));
const visit=JSON.parse(await readFile(new URL('../../careflow-mobile-emulator-service/.runtime/e2e-noi-visit.json',import.meta.url),'utf8'));
const client=new AuditClient(),attacker=new AuditClient(),other=new AuditClient(),checks=[];
function check(name,actual,expected){assert.equal(actual,expected,name);checks.push({name,status:'PASS',actual});console.log('PASS '+name);}
check('real owner login',(await client.login(account.username)).status,200);check('other patient login',(await other.login('mobile_case02')).status,200);
const resources=[['HealthRecords',18082,`/api/patients/${account.patientId}/health-records`],['Consultation',18086,`/api/consultations/appointment/${visit.appointmentId}`],['EMR',18088,`/api/emr/patients/${account.patientId}/summary`],['Prescription',18087,`/api/prescriptions/patient/${account.patientId}`]];
const token=client.tokens.accessToken,[head,payload]=token.split('.'),claims=JSON.parse(Buffer.from(payload,'base64url'));delete claims.sub;
const invalid=head+'.'+Buffer.from(JSON.stringify(claims)).toString('base64url');
const privateKey=await readFile('/tmp/careflow-dangkhoii-audit/security-hardening/jwt-private.pem');
const missingSubject=invalid+'.'+sign('RSA-SHA256',Buffer.from(invalid),privateKey).toString('base64url');
try {
 for(const [name,port,path] of resources){
  check(name+' valid RSA via Gateway',(await client.request(path)).status,200);
  check(name+' valid bound token directly over mTLS',(await client.request(path,{base:'https://localhost:'+port})).status,200);
  check(name+' wrong device key denied',(await attacker.request(path,{token})).status,401);
  check(name+' missing proof denied',(await client.request(path,{noProof:true})).status,401);
  check(name+' valid RSA signature with missing subject denied',(await client.request(path,{base:'https://localhost:'+port,token:missingSubject})).status,401);
  const status=(await other.request(path)).status;
  check(name+' other patient denied',status,403);
 }
 check('owner logout',(await client.request('/api/auth/logout',{method:'POST',body:{refreshToken:client.tokens.refreshToken},token:null})).status,200);
 for(const [name,,path] of resources)check(name+' revoked session denied',(await client.request(path)).status,401);
} finally {
 await other.request('/api/auth/logout',{method:'POST',body:{refreshToken:other.tokens.refreshToken},token:null});
 await writeFile(new URL('../../.planning/security/rsa-consumer-results.json',import.meta.url),JSON.stringify({checkedAt:new Date().toISOString(),mock:false,checks},null,2)+'\n');
}
console.log(checks.length+' actual RSA consumer checks passed.');
