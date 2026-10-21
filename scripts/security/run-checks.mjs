import assert from 'node:assert/strict';
import {readFile,open} from 'node:fs/promises';
import {spawn,execFileSync,execFile} from 'node:child_process';
import {promisify} from 'node:util';
import https from 'node:https';
if(!process.argv.includes('--audit-db'))throw Error('--audit-db required; creates real audit accounts/data.');
const root=new URL('../../',import.meta.url),privateDir='/tmp/careflow-dangkhoii-audit/security-hardening',tls=privateDir+'/tls';
const config=await(await fetch('http://localhost:3000/__emulator/config')).json();assert.equal(config.gateway,'https://localhost:18080');
const state=JSON.parse(await readFile('/tmp/careflow-dangkhoii-audit/state.json','utf8'));
const source=state.services['careflow-identity-service'];
assert.equal(execFileSync('sh',['-c','lsof -t -iTCP:18193 -sTCP:LISTEN || true'],{encoding:'utf8'}).trim(),'','Expiry probe port is occupied');
const [ca,cert,key]=await Promise.all(['ca.crt','api-gateway.crt','api-gateway.key'].map(name=>readFile(tls+'/'+name)));
const log=await open(privateDir+'/security-expiry-probe.log','a');
const probe=spawn(process.env.CAREFLOW_JAVA || '/Library/Java/JavaVirtualMachines/temurin-25.jdk/Contents/Home/bin/java',[
 '-XX:ActiveProcessorCount=2','-Xmx256m','-jar',source.jar,'--server.port=18193','--server.address=127.0.0.1','--jwt.expiration=3000','--eureka.client.enabled=false','--spring.datasource.hikari.maximum-pool-size=2','--spring.datasource.hikari.minimum-idle=0',
],{env:{...process.env,...state.environment,...source.environment},stdio:['ignore',log.fd,log.fd]});
function health(){return new Promise(resolve=>{const request=https.get('https://localhost:18193/actuator/health',{ca,cert,key,rejectUnauthorized:true,timeout:1000},response=>{response.resume();resolve(response.statusCode===200);});request.on('error',()=>resolve(false));request.on('timeout',()=>request.destroy());});}
const run=promisify(execFile);
try {
 let ready=false;
 for(let i=0;i<100;i++){assert.equal(probe.exitCode,null,'Real expiry probe exited');if(await health()){ready=true;break;}await new Promise(resolve=>setTimeout(resolve,250));}assert.ok(ready);
 const environment={...process.env,NODE_EXTRA_CA_CERTS:tls+'/ca.crt',CAREFLOW_SECURITY_TLS_DIR:tls,CAREFLOW_SECURITY_TEST_URL:'https://localhost:18080',CAREFLOW_SECURITY_PATIENT:'https://localhost:18082',CAREFLOW_SECURITY_RESULTS:'/tmp/careflow-security-primary-http.json'};
 const hardening=await run(process.execPath,[new URL('./hardening-smoke.mjs',import.meta.url).pathname,'--audit-db'],{cwd:root,env:environment,timeout:120000,maxBuffer:1024*1024});process.stdout.write(hardening.stdout);
 const flags=['--audit-db',...(process.argv.includes('--identity-outage')?['--identity-outage']:[])];
 const lifecycle=await run(process.execPath,[new URL('./lifecycle-smoke.mjs',import.meta.url).pathname,...flags],{cwd:root,env:environment,timeout:120000,maxBuffer:1024*1024});process.stdout.write(lifecycle.stdout);
}finally {
 if(probe.exitCode===null && probe.signalCode===null)probe.kill('SIGTERM');await log.close();
}
