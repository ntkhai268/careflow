"""Roll the already-owned loopback audit stack to the reviewed build; never touch remote DBs."""
import argparse, json, os, shutil, signal, subprocess, time, ssl, urllib.request, fcntl
from pathlib import Path

parser=argparse.ArgumentParser()
parser.add_argument('--audit-db',action='store_true',required=True)
parser.add_argument('--state-file',default='/tmp/careflow-dangkhoii-audit/state.json')
args=parser.parse_args()
state_path=Path(args.state_file).resolve()
if state_path!=Path('/private/tmp/careflow-dangkhoii-audit/state.json'):raise SystemExit('Only the existing private loopback audit state is accepted')
base=state_path.parent;private_dir=base/'security-hardening';source=Path(__file__).resolve().parents[2]
private=json.loads((private_dir/'private-config.json').read_text());tls=private_dir/'tls'
# Follow-up ownership checks read the exact source consultation under the user's authority.
if 'GET:/api/consultations/**' not in private['callers']['appointment-service']['scopes']:
 private['callers']['appointment-service']['scopes'].append('GET:/api/consultations/**')
ports={'identity-service':18081,'patient-service':18082,'appointment-service':18083,'queue-service':18084,'notification-service':18085,'consultation-service':18086,'prescription-service':18087,'emr-service':18088,'lab-service':18089,'hospital-directory-service':18090}
order=['careflow-identity-service','careflow-identity-session-secondary','careflow-hospital-directory-service','careflow-patient-service','careflow-appointment-service','careflow-queue-service','careflow-consultation-service','careflow-prescription-service','careflow-emr-service','careflow-lab-service','careflow-notification-service','careflow-api-gateway']
with (base/'state.lock').open('a') as lock:
 fcntl.flock(lock,fcntl.LOCK_EX)
 state=json.loads(state_path.read_text())
 for service in order:
  module='careflow-identity-service' if service=='careflow-identity-session-secondary' else service
  assert (source/module/'target'/(module+'-1.0.0-SNAPSHOT.jar')).is_file(),module
  info=state['services'][service]
  command=subprocess.run(['ps','-p',str(info['pid']),'-o','command='],capture_output=True,text=True).stdout
  if command and info['jar'] not in command:raise RuntimeError('PID ownership mismatch: '+service)
 # Every consumer is restarted with the same Common build; known old HMAC validators are stopped.
 for service in reversed(order):
  info=state['services'][service]
  command=subprocess.run(['ps','-p',str(info['pid']),'-o','command='],capture_output=True,text=True).stdout
  if command:os.kill(info['pid'],signal.SIGTERM)
 for service in order:
  info=state['services'][service]
  for _ in range(60):
   command=subprocess.run(['ps','-p',str(info['pid']),'-o','command='],capture_output=True,text=True).stdout
   if not command:break
   time.sleep(.1)
  else:raise RuntimeError('Owned service did not stop: '+service)
 for service in order:
  module='careflow-identity-service' if service=='careflow-identity-session-secondary' else service
  name={'careflow-api-gateway':'api-gateway'}.get(module,module.removeprefix('careflow-'))
  port=18092 if service=='careflow-identity-session-secondary' else 18080 if name=='api-gateway' else ports[name]
  info=state['services'][service]
  jar=private_dir/(service+'-hardened-'+str(time.time_ns())+'.jar')
  shutil.copyfile(source/module/'target'/(module+'-1.0.0-SNAPSHOT.jar'),jar)
  original={**state['environment'],**info['environment']}
  config=json.loads(original.get('SPRING_APPLICATION_JSON','{}'))
  for k,v in list(original.items()):
   for _,target in ports.items():v=v.replace('http://127.0.0.1:'+str(target),'https://localhost:'+str(target))
   original[k]=v
  config=json.loads(original.get('SPRING_APPLICATION_JSON','{}'))
  config['security']={'jwt':{'private-key-file':str(private_dir/'jwt-private.pem'),'public-key-file':str(private_dir/'jwt-public.pem'),'key-id':'audit-rsa-2026','audience':'careflow-api'},'callers-json':json.dumps(private['callers']),'caller':{'name':name,'key':private['callers'][name]['secret'],'allowed-hosts':','.join(['localhost','127.0.0.1',*ports.keys(),*['careflow-'+n for n in ports]])},'audit':{'allow-loopback-http':False},'dpop':{'public-origins':'http://localhost:3000,http://localhost:3001,http://localhost:5173,https://localhost:18080'},'internal':{'tls':{'enabled':True,'key-store':str(tls/(name+'.p12')),'key-store-password':private['tlsPassword'],'trust-store':str(tls/'truststore.p12'),'trust-store-password':private['tlsPassword']}}}
  # No signing key is sent to consumer JVMs.
  if name!='identity-service':
   config['security'].pop('jwt');config['security'].pop('callers-json')
  config['jwt']={'expiration':900000}
  config['server']={'port':port,'address':'127.0.0.1','ssl':{'enabled':True,'key-store':'file:'+str(tls/(name+'.p12')),'key-store-type':'PKCS12','key-store-password':private['tlsPassword'],'trust-store':'file:'+str(tls/'truststore.p12'),'trust-store-type':'PKCS12','trust-store-password':private['tlsPassword'],'client-auth':'none' if name=='api-gateway' else 'need','enabled-protocols':['TLSv1.3','TLSv1.2']}}
  config['app']={**config.get('app',{}),'identity-service':{'url':'https://localhost:18081'}}
  config['eureka']={'client':{'enabled':False}}
  spring=config.setdefault('spring',{});spring.setdefault('datasource',{}).setdefault('hikari',{}).update({'maximum-pool-size':3,'minimum-idle':0,'idle-timeout':10000})
  cloud=spring.setdefault('cloud',{});cloud.setdefault('discovery',{}).setdefault('client',{}).setdefault('simple',{})['instances']={n:[{'uri':'https://localhost:'+str(p)}] for n,p in ports.items()}
  config['notification']={'allowed-origins':'http://localhost:3000,http://localhost:3001,http://localhost:5173'}
  if name=='api-gateway':
   gateway_config=cloud.setdefault('gateway',{})
   legacy_gateway={key:gateway_config.pop(key) for key in list(gateway_config) if key!='server'}
   gateway=gateway_config.setdefault('server',{}).setdefault('webflux',legacy_gateway)
   for route in gateway.get('routes',[]):
    for p in ports.values():route['uri']=route['uri'].replace('ws://127.0.0.1:'+str(p),'wss://localhost:'+str(p))
   gateway.setdefault('discovery',{}).setdefault('locator',{})['enabled']=False
   gateway['globalcors']={'add-to-simple-url-handler-mapping':True,'cors-configurations':{'[/**]':{'allowedOrigins':['http://localhost:3000','http://localhost:3001','http://localhost:5173'],'allowedMethods':['GET','POST','PUT','PATCH','DELETE','OPTIONS'],'allowedHeaders':'*'}}}
   gateway.setdefault('httpclient',{})['ssl']={'key-store':'file:'+str(tls/'api-gateway.p12'),'key-store-password':private['tlsPassword'],'key-password':private['tlsPassword'],'key-store-type':'PKCS12','trusted-x509-certificates':['file:'+str(tls/'ca.crt')]}
  original.update({'SPRING_APPLICATION_JSON':json.dumps(config),'SERVER_PORT':str(port),'SERVER_ADDRESS':'127.0.0.1','EUREKA_CLIENT_ENABLED':'false','APP_IDENTITY_SERVICE_URL':'https://localhost:18081'})
  # These are explicit loopback DB URLs from the existing audit state, not compose/remote defaults.
  if module!='careflow-api-gateway':assert '127.0.0.1:15432/' in original.get('SPRING_DATASOURCE_URL',''),module
  with (base/(service+'-host.log')).open('wb') as log:
   child=subprocess.Popen(['/Library/Java/JavaVirtualMachines/temurin-25.jdk/Contents/Home/bin/java','-XX:ActiveProcessorCount=2','-Xmx384m','-jar',str(jar)],env={**os.environ,**original},stdin=subprocess.DEVNULL,stdout=log,stderr=subprocess.STDOUT,start_new_session=True)
  info.update({'pid':child.pid,'jar':str(jar),'environment':{**info['environment'],'SPRING_APPLICATION_JSON':json.dumps(config),'APP_IDENTITY_SERVICE_URL':'https://localhost:18081','SERVER_PORT':str(port),'SERVER_ADDRESS':'127.0.0.1','EUREKA_CLIENT_ENABLED':'false'}})
  # Retain rewritten provider URLs for subsequent owned restarts.
  for key in info['environment']:
   if key in original and key!='SPRING_APPLICATION_JSON':info['environment'][key]=original[key]
  state_path.write_text(json.dumps(state));os.chmod(state_path,0o600)
  context=ssl.create_default_context(cafile=str(tls/'ca.crt'));context.load_cert_chain(str(tls/(name+'.crt')),str(tls/(name+'.key')))
  for _ in range(160):
   if child.poll() is not None:raise RuntimeError('Service exited; inspect private log: '+service)
   try:
    with urllib.request.urlopen('https://localhost:'+str(port)+'/actuator/health',context=context,timeout=1) as response:
     if response.status==200:break
   except Exception:pass
   time.sleep(.25)
  else:raise RuntimeError('TLS readiness did not become healthy: '+service)
  print(service,'HTTPS/mTLS ready',port,flush=True)
