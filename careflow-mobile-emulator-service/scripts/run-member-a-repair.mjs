import assert from 'node:assert/strict';
import {readFile,open} from 'node:fs/promises';
import {spawn,execFileSync} from 'node:child_process';
import https from 'node:https';
if(!process.argv.includes('--audit-db'))throw Error('Explicit --audit-db required; this writes only audit data.');
const config=await (await fetch('http://localhost:3000/__emulator/config')).json();
assert.equal(config.gateway,'https://localhost:18080');
const state=JSON.parse(await readFile('/tmp/careflow-dangkhoii-audit/state.json','utf8'));
const tls='/tmp/careflow-dangkhoii-audit/security-hardening/tls';
const [ca,cert,key]=await Promise.all(['ca.crt','api-gateway.crt','api-gateway.key'].map(name=>readFile(tls+'/'+name)));
const definitions=[{source:'careflow-identity-service',port:18093,extra:['--jwt.expiration=5000']},{source:'careflow-queue-service',port:18094,extra:[]}],started=[];
function health(port){return new Promise(resolve=>{const request=https.get('https://localhost:'+port+'/actuator/health',{ca,cert,key,rejectUnauthorized:true,timeout:1000},response=>{response.resume();resolve(response.statusCode===200);});request.on('error',()=>resolve(false));request.on('timeout',()=>request.destroy());});}
try {
 for(const definition of definitions){
  const occupied=execFileSync('sh',['-c',`lsof -t -iTCP:${definition.port} -sTCP:LISTEN || true`],{encoding:'utf8'}).trim();assert.equal(occupied,'','Audit probe port already occupied');
  const source=state.services[definition.source];
  const log=await open(new URL(`../.runtime/security-probe-${definition.port}.log`,import.meta.url),'a');
  const child=spawn(process.env.CAREFLOW_JAVA || '/Library/Java/JavaVirtualMachines/temurin-25.jdk/Contents/Home/bin/java',[
   '-XX:ActiveProcessorCount=2','-Xmx256m','-jar',source.jar,`--server.port=${definition.port}`,'--server.address=127.0.0.1','--eureka.client.enabled=false',
   '--spring.datasource.hikari.maximum-pool-size=2','--spring.datasource.hikari.minimum-idle=0',...definition.extra,
  ],{env:{...process.env,...state.environment,...source.environment},stdio:['ignore',log.fd,log.fd]});started.push({child,log});
  let ready=false;
  for(let i=0;i<100;i++){assert.equal(child.exitCode,null,'Owned audit probe exited');if(await health(definition.port)){ready=true;break;}await new Promise(resolve=>setTimeout(resolve,250));}
  assert.ok(ready,'Owned TLS probe must become healthy');
 }
 await import('./member-a-repair-smoke.mjs');
}finally{
 for(const {child,log} of started){
  if(child.exitCode===null && child.signalCode===null)child.kill('SIGTERM');
  await log.close();
 }
}
