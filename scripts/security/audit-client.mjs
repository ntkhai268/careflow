import {generateKeyPairSync,sign,createHash,randomUUID} from 'node:crypto';
import https from 'node:https';
import {readFile} from 'node:fs/promises';
const tlsDir='/tmp/careflow-dangkhoii-audit/security-hardening/tls';
let material;
export class AuditClient {
 constructor(base='http://localhost:3000') { this.base=base;this.keys=generateKeyPairSync('ec',{namedCurve:'prime256v1'});this.publicKey=this.keys.publicKey.export({format:'jwk'}); }
 proof(method,path,token,extra={}) {
  const encode=value=>Buffer.from(JSON.stringify(value)).toString('base64url');
  const claims={jti:randomUUID(),htm:method,htu:'http://localhost:3000'+path.split(/[?#]/)[0],iat:Math.floor(Date.now()/1000),...(token?{ath:createHash('sha256').update(token).digest('base64url')}:{}),...extra};
  const text=encode({typ:'dpop+jwt',alg:'ES256',jwk:this.publicKey})+'.'+encode(claims);
  return text+'.'+sign('sha256',Buffer.from(text),{key:this.keys.privateKey,dsaEncoding:'ieee-p1363'}).toString('base64url');
 }
 async request(path,{method='GET',body,token=this.tokens?.accessToken,base=this.base,headers={},proof,noProof=false}={}) {
  if(!/^https?:\/\/(localhost|127\.0\.0\.1):(?:3000|18[01][0-9]{2})$/.test(base))throw Error('Audit client only accepts owned loopback endpoints');
  const options={method,headers:{'content-type':'application/json',...(token?{Authorization:'Bearer '+token}:{}),...(!noProof?{DPoP:proof||this.proof(method,path,token)}:{}),...headers}};
  if(base.startsWith('https:')) {
   const [ca,cert,key]=await(material ||= Promise.all(['ca.crt','api-gateway.crt','api-gateway.key'].map(name=>readFile(tlsDir+'/'+name))));
   return new Promise((resolve,reject)=>{const request=https.request(base+path,{...options,ca,cert,key,rejectUnauthorized:true},response=>{
    const chunks=[];response.on('data',chunk=>chunks.push(chunk));response.on('end',()=>{try{resolve({status:response.statusCode,json:JSON.parse(Buffer.concat(chunks).toString())});}catch(error){reject(error);}});
   });request.on('error',reject);request.end(body?JSON.stringify(body):undefined);});
  }
  const response=await fetch(base+path,{...options,...(body?{body:JSON.stringify(body)}:{})});
  return {status:response.status,json:await response.json()};
 }
 async login(username,password='Password123@',extra={},base=this.base) {
  const response=await this.request('/api/auth/login',{method:'POST',body:{usernameOrEmail:username,password,...extra},token:null,base});
  if(response.status===200)this.tokens=response.json.data;return response;
 }
 async socket() {
  const ws=new WebSocket('wss://localhost:18080/ws/notifications',['v12.stomp']);
  ws.addEventListener('close',()=>{ws.closed=true;});
  await new Promise((resolve,reject)=>{
   const timer=setTimeout(()=>{ws.close();reject(Error('Real STOMP connect timed out'));},10000);
   ws.addEventListener('open',()=>ws.send(`CONNECT\naccept-version:1.2\nheart-beat:0,0\nAuthorization:Bearer ${this.tokens.accessToken}\nDPoP:${this.proof('GET','/ws/notifications',this.tokens.accessToken)}\n\n\0`));
   ws.addEventListener('message',event=>{if(String(event.data).startsWith('CONNECTED')){clearTimeout(timer);resolve();}else if(String(event.data).startsWith('ERROR')){clearTimeout(timer);reject(Error('STOMP rejected connection'));}});
   ws.addEventListener('error',()=>{clearTimeout(timer);reject(Error('STOMP transport error'));});
  });return ws;
 }
}
