/* Non-extractable, per-device/per-Gateway signing keys. No private keys in localStorage. */
(() => {
  'use strict';
  const params = new URLSearchParams(location.search);
  const name = `careflow.dpop.${params.get('scope') || 'standalone'}.${params.get('device') || 'browser'}`;
  let pending;
  const bytes = value => new TextEncoder().encode(value);
  const base64 = value => { let text=''; for (const c of new Uint8Array(value)) text+=String.fromCharCode(c); return btoa(text).replaceAll('+','-').replaceAll('/','_').replaceAll('=',''); };
  const encode = value => base64(bytes(JSON.stringify(value)));
  async function keys() {
    if (!isSecureContext || !crypto.subtle) throw new Error('Cần HTTPS hoặc localhost để bảo vệ khóa đăng nhập.');
    const db = await new Promise((resolve,reject) => {
      const request = indexedDB.open(name,1);
      request.onupgradeneeded=()=>request.result.createObjectStore('keys');
      request.onsuccess=()=>resolve(request.result); request.onerror=()=>reject(request.error);
    });
    const read = await new Promise((resolve,reject) => {
      const request=db.transaction('keys').objectStore('keys').get('signing');
      request.onsuccess=()=>resolve(request.result);request.onerror=()=>reject(request.error);
    });
    if (read) { db.close();return read; }
    const generated=await crypto.subtle.generateKey({name:'ECDSA',namedCurve:'P-256'},false,['sign','verify']);
    // Resolve simultaneous tabs with a single read-write transaction; never overwrite an existing key.
    return new Promise((resolve,reject)=>{
      const transaction=db.transaction('keys','readwrite'), store=transaction.objectStore('keys'), request=store.get('signing');let selected;
      request.onsuccess=()=>{selected=request.result || generated;if(!request.result)store.put(selected,'signing');};
      transaction.oncomplete=()=>{db.close();resolve(selected);};transaction.onerror=()=>{db.close();reject(transaction.error);};
    });
  }
  window.careflowProof = async ({method,path,token}) => {
    const pair=await (pending ||= keys().catch(error=>{pending=null;throw error;}));
    const url=new URL(path,location.origin);url.search='';url.hash='';
    if(url.origin!==location.origin)throw new Error('Không ký request đến môi trường khác.');
    const publicKey=await crypto.subtle.exportKey('jwk',pair.publicKey);
    const claims={jti:crypto.randomUUID(),htm:method.toUpperCase(),htu:url.href,iat:Math.floor(Date.now()/1000)};
    if(token)claims.ath=base64(await crypto.subtle.digest('SHA-256',bytes(token)));
    const content=encode({typ:'dpop+jwt',alg:'ES256',jwk:publicKey})+'.'+encode(claims);
    const signature=await crypto.subtle.sign({name:'ECDSA',hash:'SHA-256'},pair.privateKey,bytes(content));
    return content+'.'+base64(signature);
  };
})();
