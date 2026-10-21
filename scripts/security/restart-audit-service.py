import json, os, signal, subprocess, sys, time, shutil, urllib.request, fcntl
from pathlib import Path
base=Path('/tmp/careflow-dangkhoii-audit'); state_path=base/'state.json'
state_lock=open(base/'state.lock','a')
fcntl.flock(state_lock,fcntl.LOCK_EX)
state=json.loads(state_path.read_text())
if len(sys.argv)!=3 or sys.argv[1]!='--audit-db':raise SystemExit('Use --audit-db <owned-service-name>')
name=sys.argv[2]
if name not in {'careflow-identity-service'}:raise SystemExit('Only Identity restart is used by this audit regression')
worktree=str(Path(__file__).resolve().parents[2]);port='18081' 
info=state['services'][name]
pid=info['pid']
command=subprocess.run(['ps','-p',str(pid),'-o','command='],capture_output=True,text=True).stdout
if command:
 if info['jar'] not in command: raise RuntimeError('Stored PID no longer belongs to the audit jar')
 os.kill(pid,signal.SIGTERM)
 for _ in range(40):
  if not subprocess.run(['ps','-p',str(pid),'-o','pid='],capture_output=True,text=True).stdout.strip():break
  time.sleep(.2)
 else:os.kill(pid,signal.SIGKILL)
jar=base/(name+'-verified-'+str(time.time_ns())+'.jar')
shutil.copyfile(Path(worktree)/name/'target'/(name+'-1.0.0-SNAPSHOT.jar'),jar)
env={**os.environ, **state['environment'], **info['environment']}
with open(base/(name+'-host.log'),'wb') as log:
 p=subprocess.Popen(['/Library/Java/JavaVirtualMachines/temurin-25.jdk/Contents/Home/bin/java','-XX:ActiveProcessorCount=2','-Xmx384m','-jar',str(jar)],env=env,stdout=log,stderr=subprocess.STDOUT,start_new_session=True)
info['pid']=p.pid;info['jar']=str(jar)
state_path.write_text(json.dumps(state));os.chmod(state_path,0o600)
import ssl
settings=json.loads(env.get('SPRING_APPLICATION_JSON','{}'))
secure=settings.get('server',{}).get('ssl',{}).get('enabled',False)
health_context=None
if secure:
 tls=base/'security-hardening/tls';health_context=ssl.create_default_context(cafile=str(tls/'ca.crt'))
 identity='identity-service' if name=='careflow-identity-service' else name.removeprefix('careflow-')
 health_context.load_cert_chain(str(tls/(identity+'.crt')),str(tls/(identity+'.key')))
ready=False
for _ in range(120):
 if p.poll() is not None:raise RuntimeError('Audit JVM exited; inspect dedicated log')
 try:
  with urllib.request.urlopen(('https://localhost:' if secure else 'http://127.0.0.1:')+port+'/actuator/health',timeout=1,context=health_context) as r:
   if r.status==200: ready=True;break
 except Exception:pass
 time.sleep(.25)
if not ready:raise RuntimeError('Audit service health check did not become ready')
print(name,'ready',port,'pid',p.pid)
