import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
import {createHmac,randomUUID} from 'node:crypto';
import {writeFile} from 'node:fs/promises';
const require=createRequire(import.meta.url);
const {chromium}=require(process.env.PLAYWRIGHT_MODULE || '/Users/dangkhoii/Library/Caches/ms-playwright-go/1.57.0/package');
const base=process.env.CAREFLOW_SECURITY_BROWSER_URL || 'http://localhost:3000';
assert.match(base,/^http:\/\/localhost:300[01]$/);
const username='browser_mfa_'+randomUUID().replaceAll('-','').slice(0,10),password='Password123@',checks=[],errors=[];
const registered=await fetch(base+'/api/auth/register',{method:'POST',headers:{'content-type':'application/json'},body:JSON.stringify({username,email:username+'@example.test',password})});assert.equal(registered.status,201);
const browser=await chromium.launch({headless:true,executablePath:process.env.CHROME_PATH || '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome'});
const context=await browser.newContext({viewport:{width:1700,height:1250}}),page=await context.newPage();page.setDefaultTimeout(30000);
let connected=0,tokens;
page.on('pageerror',e=>errors.push(e.message));
page.on('websocket',ws=>ws.on('framereceived',event=>{if(typeof event.payload==='string' && event.payload.startsWith('CONNECTED'))connected++;}));
await page.addInitScript(()=>{const q=new URLSearchParams(location.search),device=q.get('device'),scope=q.get('scope');if(device && scope)localStorage.setItem(`careflow.${scope}.${device}.preferences.has_seen_onboarding`,'true');});
function passed(name){checks.push({name,status:'PASS'});console.log('PASS',name);}
function code(secret,step){const a='ABCDEFGHIJKLMNOPQRSTUVWXYZ234567';let bits='';for(const c of secret)bits+=a.indexOf(c).toString(2).padStart(5,'0');const b=[];for(let i=0;i+8<=bits.length;i+=8)b.push(parseInt(bits.slice(i,i+8),2));const t=Buffer.alloc(8);t.writeBigUInt64BE(BigInt(step));const h=createHmac('sha1',Buffer.from(b)).update(t).digest();return String((h.readUInt32BE(h[h.length-1]&15)&0x7fffffff)%1000000).padStart(6,'0');}
async function input(frame,index,value){await frame.locator('input').nth(index).click();await page.waitForTimeout(150);await frame.locator('input:focus').fill(value);}
async function login(frame,factor=''){
 await input(frame,0,username);await input(frame,1,password);
 const [first]=await Promise.all([page.waitForResponse(r=>new URL(r.url()).pathname==='/api/auth/login' && r.request().headers()['x-emulator-device']==='phone-01'),frame.getByRole('button',{name:'Đăng nhập',exact:true}).click()]);
 let response=first;
 if(factor){
  assert.equal(first.status(),401);assert.match((await first.json()).message,/^MFA_REQUIRED:/);
  await frame.getByRole('button',{name:'Dùng mã khôi phục',exact:true}).click();
  await frame.locator('input[aria-label="Nhập mã khôi phục đã lưu"]').click();await page.waitForTimeout(100);await frame.locator('input:focus').fill(factor);
  [response]=await Promise.all([page.waitForResponse(r=>new URL(r.url()).pathname==='/api/auth/login' && r.request().headers()['x-emulator-device']==='phone-01'),frame.getByRole('button',{name:'Xác nhận đăng nhập',exact:true}).click()]);
 }
 assert.equal(response.status(),200);tokens=(await response.json()).data;
 await page.waitForFunction(()=>document.querySelector('[data-device-id="phone-01"] .device-session').classList.contains('authenticated'));
}

async function call(frame,path,method='GET',body=null,token=tokens.accessToken){return frame.evaluate(async args=>{const [path,method,body,token]=args,proof=await window.careflowProof({method,path,token});const response=await fetch(path,{method,headers:{'content-type':'application/json',...(token?{Authorization:'Bearer '+token}:{}),DPoP:proof},...(body?{body:JSON.stringify(body)}:{})});return {status:response.status};},[path,method,body,token]);}
try{
 await page.goto(base);await page.waitForSelector('[data-device-id="phone-01"] iframe');
 const frame=page.frames().find(f=>new URL(f.url()).searchParams.get('device')==='phone-01');
 await frame.locator('flt-semantics-placeholder').evaluate(e=>e.click());
 await login(frame);passed('browser logs in using its real bound key');
 for(let i=0;i<40 && !connected;i++)await page.waitForTimeout(250);
 assert.ok(connected>0);passed('real STOMP connection validates client proof');
 const extractable=await frame.evaluate(async()=>{const q=new URLSearchParams(location.search),name=`careflow.dpop.${q.get('scope')}.${q.get('device')}`;const db=await new Promise((resolve,reject)=>{const request=indexedDB.open(name,1);request.onsuccess=()=>resolve(request.result);request.onerror=()=>reject(request.error);});return new Promise((resolve,reject)=>{const request=db.transaction('keys').objectStore('keys').get('signing');request.onsuccess=()=>{db.close();resolve(request.result.privateKey.extractable);};request.onerror=()=>reject(request.error);});});
 assert.equal(extractable,false);passed('browser private key is non-extractable and persists in IndexedDB');
 await frame.evaluate(()=>{location.hash='#/?tab=4';});
 await page.waitForTimeout(500);
 const placeholder=frame.locator('flt-semantics-placeholder');if(await placeholder.count())await placeholder.evaluate(e=>e.click());
 await frame.getByText('Bảo mật đăng nhập',{exact:true}).click();
 await input(frame,0,password);
 const [setup]=await Promise.all([page.waitForResponse(r=>new URL(r.url()).pathname==='/api/auth/mfa/setup'),frame.getByRole('button',{name:'Thiết lập xác thực hai bước',exact:true}).click()]);
 assert.equal(setup.status(),200);const secret=(await setup.json()).data.secret;
 await input(frame,1,code(secret,Math.floor(Date.now()/30000)));
 const [confirm]=await Promise.all([page.waitForResponse(r=>new URL(r.url()).pathname==='/api/auth/mfa/confirm'),frame.getByRole('button',{name:'Xác nhận bật MFA',exact:true}).click()]);
 assert.equal(confirm.status(),200);const recovery=(await confirm.json()).data.recoveryCodes;
 passed('real UI enrolls and confirms TOTP through Identity');
 assert.equal((await call(frame,'/api/auth/me')).status,401);passed('enabling MFA invalidates the previous browser session');
 await frame.getByRole('button',{name:'Đã lưu, đăng nhập lại',exact:true}).click();
 await login(frame,recovery[0]);passed('browser logs in with password and one-use recovery code');
 const other=page.frames().find(f=>new URL(f.url()).searchParams.get('device')==='phone-02');
 const wrong=await other.evaluate(async token=>window.careflowProof({method:'GET',path:'/api/auth/me',token}),tokens.accessToken);
 const status=await frame.evaluate(async({token,proof})=>(await fetch('/api/auth/me',{headers:{Authorization:'Bearer '+token,DPoP:proof}})).status,{token:tokens.accessToken,proof:wrong});
 assert.equal(status,401);passed('another phone key cannot use the logged-in phone token');
 assert.equal((await call(frame,'/api/auth/logout','POST',{refreshToken:tokens.refreshToken},'')).status,200);
 assert.deepEqual(errors,[]);passed('browser MFA flow has no uncaught JavaScript errors');
}finally{
 await writeFile(new URL('../.runtime/security-browser-results.json',import.meta.url),JSON.stringify({checkedAt:new Date().toISOString(),mock:false,checks,errors},null,2));
 await browser.close();
}
