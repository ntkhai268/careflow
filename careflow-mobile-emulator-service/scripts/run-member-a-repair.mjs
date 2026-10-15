import assert from 'node:assert/strict';
import {readFile,open} from 'node:fs/promises';
import {spawn,execFileSync} from 'node:child_process';

if(!process.argv.includes('--audit-db'))throw Error('Use npm run test:member-a -- --audit-db; this creates real audit data.');
const base='http://localhost:3000';
const config=await (await fetch(base+'/__emulator/config')).json();
assert.equal(config.gateway,'http://127.0.0.1:18080');
const state=JSON.parse(await readFile('/tmp/careflow-dangkhoii-audit/state.json','utf8'));
const definitions=[
  {name:'expiry',source:'careflow-identity-service',port:18093,extra:{JWT_EXPIRATION_MS:'5000',SPRING_APPLICATION_JSON:'{"jwt":{"expiration":5000}}',EUREKA_CLIENT_ENABLED:'false'}},
  {name:'concurrency',source:'careflow-queue-service',port:18094,extra:{EUREKA_INSTANCE_INSTANCE_ID:'careflow-queue-concurrency-probe:18094'}},
];
const started=[];
try{
  for(const definition of definitions){
    const existingKey=definition.name==='expiry'?'careflow-identity-expiry-probe':'careflow-queue-concurrency-probe';
    const existing=state.services[existingKey];
    let reachable=false;
    try{reachable=(await fetch(`http://127.0.0.1:${definition.port}/actuator/health`,{signal:AbortSignal.timeout(1000)})).status===200;}catch{}
    if(reachable){
      assert.ok(existing,'Port is occupied by a process not owned by the audit.');
      const command=execFileSync('ps',['-p',String(existing.pid),'-o','command='],{encoding:'utf8'});
      assert.ok(command.includes(existing.jar),'Existing probe PID ownership mismatch.');
      continue;
    }
    const source=state.services[definition.source];
    const log=await open(new URL(`../.runtime/member-a-${definition.name}-probe.log`,import.meta.url),'a');
    const child=spawn('/Library/Java/JavaVirtualMachines/temurin-25.jdk/Contents/Home/bin/java',[
      '-XX:ActiveProcessorCount=2','-Xmx256m','-jar',source.jar,
    ],{env:{...process.env,...state.environment,...source.environment,...definition.extra,SERVER_PORT:String(definition.port),SERVER_ADDRESS:'127.0.0.1'},stdio:['ignore',log.fd,log.fd]});
    child.on('error',error=>{console.error(`Probe ${definition.name}: ${error.message}`);});
    started.push({child,log});
    let healthy=false;
    for(let attempt=0;attempt<100;attempt++){
      assert.equal(child.exitCode,null,`${definition.name} probe exited; inspect its local log.`);
      try{healthy=(await fetch(`http://127.0.0.1:${definition.port}/actuator/health`,{signal:AbortSignal.timeout(1000)})).status===200;}catch{}
      if(healthy)break;
      await new Promise(resolve=>setTimeout(resolve,250));
    }
    assert.ok(healthy,`${definition.name} probe did not become healthy.`);
  }
  await import('./member-a-repair-smoke.mjs');
}finally{
  for(const {child,log} of started){
    if(child.exitCode===null && child.signalCode===null){
      child.kill('SIGTERM');
      for(let attempt=0;attempt<100 && child.exitCode===null && child.signalCode===null;attempt++)await new Promise(resolve=>setTimeout(resolve,100));
      if(child.exitCode===null && child.signalCode===null)child.kill('SIGKILL');
    }
    await log.close();
  }
}
