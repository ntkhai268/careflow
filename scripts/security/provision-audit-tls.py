"""Generate/renew only local audit TLS material. Nothing is written to the repository."""
import argparse,json,os,subprocess
from pathlib import Path
parser=argparse.ArgumentParser()
parser.add_argument('--audit-db',action='store_true',required=True)
parser.add_argument('--renew-certificates',action='store_true')
args=parser.parse_args()
base=Path('/tmp/careflow-dangkhoii-audit/security-hardening');config=json.loads((base/'private-config.json').read_text())
folder=base/'tls';folder.mkdir(mode=0o700,exist_ok=True)
env={**os.environ,'CAREFLOW_TLS_PASSWORD':config['tlsPassword']}
def run(arguments):subprocess.run(arguments,env=env,check=True,stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
if not (folder/'ca.crt').exists() or args.renew_certificates:
 (folder/'ca.cnf').write_text('[req]\ndistinguished_name=dn\nx509_extensions=ca_ext\nprompt=no\n[dn]\nCN=CareFlow Audit CA\n[ca_ext]\nbasicConstraints=critical,CA:TRUE\nkeyUsage=critical,keyCertSign,cRLSign\n')
 if not (folder/'ca.key').exists():run(['openssl','genpkey','-algorithm','RSA','-pkeyopt','rsa_keygen_bits:2048','-out',str(folder/'ca.key')])
 run(['openssl','req','-new','-x509','-key',str(folder/'ca.key'),'-out',str(folder/'ca.crt'),'-days','365','-config',str(folder/'ca.cnf')])
for name in config['callers']:
 if (folder/(name+'.p12')).exists() and not args.renew_certificates:continue
 if not (folder/(name+'.key')).exists():run(['openssl','genpkey','-algorithm','RSA','-pkeyopt','rsa_keygen_bits:2048','-out',str(folder/(name+'.key'))])
 run(['openssl','req','-new','-key',str(folder/(name+'.key')),'-out',str(folder/(name+'.csr')),'-subj','/CN='+name])
 ext=folder/(name+'.ext');ext.write_text('basicConstraints=critical,CA:FALSE\nkeyUsage=critical,digitalSignature,keyEncipherment\nextendedKeyUsage=serverAuth,clientAuth\nsubjectAltName=DNS:localhost,DNS:'+name+',DNS:careflow-'+name+',IP:127.0.0.1\n')
 run(['openssl','x509','-req','-in',str(folder/(name+'.csr')),'-CA',str(folder/'ca.crt'),'-CAkey',str(folder/'ca.key'),'-CAcreateserial','-out',str(folder/(name+'.crt')),'-days','90','-sha256','-extfile',str(ext)])
 run(['openssl','pkcs12','-export','-name',name,'-inkey',str(folder/(name+'.key')),'-in',str(folder/(name+'.crt')),'-certfile',str(folder/'ca.crt'),'-out',str(folder/(name+'.p12')),'-passout','env:CAREFLOW_TLS_PASSWORD'])
store=folder/'truststore.p12'
if store.exists() and args.renew_certificates:store.unlink()
if not store.exists():run(['/Library/Java/JavaVirtualMachines/temurin-25.jdk/Contents/Home/bin/keytool','-importcert','-noprompt','-alias','careflow-audit','-file',str(folder/'ca.crt'),'-keystore',str(store),'-storetype','PKCS12','-storepass:env','CAREFLOW_TLS_PASSWORD'])
for path in folder.iterdir():os.chmod(path,0o600)
print('Audit certificates ready. Leaf validity: 90 days. Restart owned audit services to load renewed certificates.')
