// Real existing patient and real APIs only. Does not mutate clinical data.
import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
import {writeFile} from 'node:fs/promises';
import {AuditClient} from './audit-client.mjs';
const expectError=process.argv.includes('--expect-error');
assert.equal((await(await fetch('http://localhost:3000/__emulator/config')).json()).gateway,'https://localhost:18080');
const {chromium}=createRequire(import.meta.url)('/Users/dangkhoii/Library/Caches/ms-playwright-go/1.57.0/package');
const browser=await chromium.launch({headless:true,executablePath:'/Applications/Google Chrome.app/Contents/MacOS/Google Chrome'});
const requests=[],checks=[];
try {
 const context=await browser.newContext({viewport:{width:1700,height:1250}}),page=await context.newPage();page.setDefaultTimeout(30000);
 page.on('response',r=>{const path=new URL(r.url()).pathname;if(path.startsWith('/api/'))requests.push({path,method:r.request().method(),status:r.status()});});
 await page.addInitScript(()=>{const q=new URLSearchParams(location.search),d=q.get('device'),s=q.get('scope');if(d&&s)localStorage.setItem(`careflow.${s}.${d}.preferences.has_seen_onboarding`,'true');});
 await page.goto('http://localhost:3000');await page.waitForSelector('[data-device-id="phone-01"] iframe');
 const frame=page.frames().find(f=>new URL(f.url()).searchParams.get('device')==='phone-01');await frame.locator('flt-semantics-placeholder').evaluate(e=>e.click());
 await frame.getByRole('button',{name:'Đăng nhập',exact:true}).waitFor();
 for(const [i,value] of ['mobile_noi_c1d9dc','Password123@'].entries()){await frame.locator('input').nth(i).click();await page.waitForTimeout(100);await frame.locator('input:focus').fill(value);}
 await frame.getByRole('button',{name:'Đăng nhập',exact:true}).click();
 await page.waitForFunction(()=>document.querySelector('[data-device-id="phone-01"] .device-session').classList.contains('authenticated'));
 if(expectError){
  await frame.locator('[aria-label*="Không thể tải hành trình khám."]').first().waitFor();
  assert.ok(requests.some(r=>r.path.startsWith('/api/labs/orders/consultation/')&&r.status===403));checks.push('Original real Home failure reproduced: doctor-only lab request returns 403');
 }else{
  await frame.locator('[aria-label*="Hành trình đang diễn ra"]').first().waitFor();
  await page.waitForTimeout(500);
  await page.screenshot({path:'/tmp/careflow-home-after.png',fullPage:true});
  await writeFile('/tmp/careflow-home-after-semantics.html',await frame.content());
  assert.ok((await frame.content()).includes('Đang khám bệnh'));
  assert.equal(await frame.locator('[aria-label*="Không thể tải hành trình khám."]').count(),0);
  checks.push('Original patient restores the active journey without a red error');
  assert.ok(requests.some(r=>r.path==='/api/labs/orders/patient/f4e9929c-abd6-44ba-bf75-13741708e4ae'&&r.status===200));
  assert.ok(!requests.some(r=>r.path.startsWith('/api/labs/orders/consultation/')));checks.push('Journey reads real patient Lab API, never the doctor-only endpoint');
  assert.ok(requests.some(r=>r.path==='/api/consultations/appointment/65b8e77c-6396-4941-b30f-300e652fd8c6'&&r.status===200));checks.push('Original ongoing consultation is restored');
  const client=new AuditClient();assert.equal((await client.login('mobile_noi_c1d9dc')).status,200);
  try {
   assert.equal((await client.request('/api/labs/orders/consultation/5f0a2c7b-ec39-4e39-9083-bcf694df27cd')).status,403);
   checks.push('Doctor-only lab API still refuses the patient');
   assert.equal((await client.request('/api/labs/orders/patient/c0e4fd4a-112c-4d7b-8cb9-282eda4506c0')).status,403);
   checks.push('Another patient lab history is still forbidden');
   const consultation=await client.request('/api/consultations/5f0a2c7b-ec39-4e39-9083-bcf694df27cd');
   assert.equal(consultation.status,200);assert.equal(consultation.json.data.status,'IN_PROGRESS');
   const ticket=await client.request('/api/queues/tickets/appointment/65b8e77c-6396-4941-b30f-300e652fd8c6');
   assert.equal(ticket.status,200);assert.equal(ticket.json.data.status,'IN_PROGRESS');assert.equal(ticket.json.data.ticketCode,'NOI-010');
   checks.push('Real NOI-010 and consultation remain IN_PROGRESS without clinical mutations');
  }finally{await client.request('/api/auth/logout',{method:'POST',body:{refreshToken:client.tokens.refreshToken}});}
 }
 await page.waitForTimeout(500);await page.screenshot({path:expectError?'/tmp/careflow-home-before.png':'/tmp/careflow-home-after.png',fullPage:true});
 await page.locator('[data-device-id="phone-01"]').getByRole('button',{name:'Đăng xuất riêng máy này',exact:true}).click();await page.locator('#confirm-accept').click();await frame.getByRole('button',{name:'Đăng nhập',exact:true}).waitFor();
 checks.push('Only the browser test session is logged out');
}finally{
 await browser.close();await writeFile(new URL('../../.planning/security/home-journey-'+(expectError?'before':'after')+'.json',import.meta.url),JSON.stringify({checkedAt:new Date().toISOString(),mock:false,checks,requests},null,2)+'\n');
}
for(const check of checks)console.log('PASS '+check);
