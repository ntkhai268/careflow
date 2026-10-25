/* Real audit HTTP transport. Adds client proof; never substitutes backend responses. */
import {generateKeyPairSync,sign,createHash,randomUUID} from 'node:crypto';
import {readFile} from 'node:fs/promises';
import https from 'node:https';
const users=new Map(),access=new Map(),refresh=new Map();
let material;
function key(){const pair=generateKeyPairSync('ec',{namedCurve:'prime256v1'});return {pair,jwk:pair.publicKey.export({format:'jwk'})};}
export function proofFor(token,method,path,owner) {
 const selected=owner || access.get(token);if(!selected)throw Error('No audit client key for token');
 const encode=value=>Buffer.from(JSON.stringify(value)).toString('base64url');
 const claims={jti:randomUUID(),htm:method,htu:'http://localhost:3000'+path,iat:Math.floor(Date.now()/1000),...(token?{ath:createHash('sha256').update(token).digest('base64url')}: {})};
 const text=encode({typ:'dpop+jwt',alg:'ES256',jwk:selected.jwk})+'.'+encode(claims);
 return text+'.'+sign('sha256',Buffer.from(text),{key:selected.pair.privateKey,dsaEncoding:'ieee-p1363'}).toString('base64url');
}
export async function auditFetch(input,options={}) {
 let url=new URL(String(input));
 if(!['localhost','127.0.0.1'].includes(url.hostname))throw Error('Audit transport accepts loopback only');
 if(url.port==='3000' && !url.pathname.startsWith('/api/'))return fetch(url,options);
 if(url.port!=='3000') {
  if(!/^18(?:08[0-9]|09[0-4]|193)$/.test(url.port))throw Error('Unexpected audit service port');
  url.protocol='https:';url.hostname='localhost';
 }
 const headers=new Headers(options.headers),method=(options.method||'GET').toUpperCase();
 const token=headers.get('Authorization')?.replace(/^Bearer /,'');
 let body={};try{body=JSON.parse(options.body||'{}');}catch{}
 let selected=token?access.get(token):null;
 if(url.pathname==='/api/auth/login') {
  const username=String(body.usernameOrEmail||'');if(!users.has(username))users.set(username,key());selected=users.get(username);
 }else if(body.refreshToken)selected=refresh.get(body.refreshToken);
 selected ||= key();
 headers.set('DPoP',proofFor(token,method,url.pathname,selected));
 let response;
 if(url.protocol==='https:') {
  const dir='/tmp/careflow-dangkhoii-audit/security-hardening/tls';
  const [ca,cert,privateKey]=await(material ||= Promise.all(['ca.crt','api-gateway.crt','api-gateway.key'].map(name=>readFile(dir+'/'+name))));
  response=await new Promise((resolve,reject)=>{
   const request=https.request(url,{method,headers:Object.fromEntries(headers),ca,cert,key:privateKey,rejectUnauthorized:true,timeout:25000},incoming=>{
    const chunks=[];incoming.on('data',chunk=>chunks.push(chunk));incoming.on('end',()=>resolve(new Response(Buffer.concat(chunks),{status:incoming.statusCode,headers:Object.fromEntries(Object.entries(incoming.headers).filter(([,value])=>typeof value==='string'))})));
   });request.on('error',reject);request.on('timeout',()=>request.destroy(Error('Audit request timed out')));request.end(options.body);
  });
 }else response=await fetch(url,{...options,headers});
 if(response.ok && ['/api/auth/login','/api/auth/refresh'].includes(url.pathname)) {
  const data=(await response.clone().json()).data;
  if(data?.accessToken){access.set(data.accessToken,selected);refresh.set(data.refreshToken,selected);}
 }
 return response;
}
