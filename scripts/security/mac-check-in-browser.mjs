/* Real, staff-assisted Mac check-in. Changes an existing audit ticket; never seeds or resets DB. */
import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
import {writeFile} from 'node:fs/promises';
import {AuditClient} from './audit-client.mjs';
if(!process.argv.includes('--audit-db'))throw Error('--audit-db is required; this checks in a real existing audit ticket.');
assert.equal((await(await fetch('http://localhost:3000/__emulator/config')).json()).gateway,'https://localhost:18080');
const {chromium}=createRequire(import.meta.url)(process.env.PLAYWRIGHT_MODULE || '/Users/dangkhoii/Library/Caches/ms-playwright-go/1.57.0/package');
const username=process.env.CAREFLOW_DEMO_PATIENT || 'mobile_case03',password=process.env.CAREFLOW_AUDIT_PASSWORD || 'Password123@';
const client=new AuditClient(),checks=[],requests=[],errors=[];
const login=await client.login(username,password);assert.equal(login.status,200);
const browser=await chromium.launch({headless:true,executablePath:process.env.CHROME_PATH || '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome'});
function pass(name){checks.push({name,status:'PASS'});console.log('PASS '+name);}
function observe(page){page.setDefaultTimeout(25000);page.on('pageerror',e=>errors.push(e.message));page.on('response',r=>{const path=new URL(r.url()).pathname;if(path.startsWith('/api/'))requests.push({path,method:r.request().method(),status:r.status()});});}
async function loginWeb(page,username){await page.goto('http://localhost:5173/login');await page.locator('#email').fill(username);await page.locator('#password').fill(password);await page.locator('button[type=submit]').click();await page.waitForURL(url=>!url.pathname.startsWith('/login'));}
let appointment,ticket;
try {
 const profiles=await client.request('/api/patients/user/'+client.tokens.user.id+'/profiles');assert.equal(profiles.status,200);
 const date=new Intl.DateTimeFormat('en-CA',{timeZone:'Asia/Ho_Chi_Minh'}).format(new Date());
 const candidates=[];
 for(const p of profiles.json.data){
  const appointments=await client.request('/api/appointments/patient/'+p.id);assert.equal(appointments.status,200);
  for(const a of appointments.json.data.filter(a=>a.appointmentDate===date && a.department==='NOI_TONG_QUAT' && a.status==='CONFIRMED')){
   const response=await client.request('/api/queues/tickets/appointment/'+a.id);
   if(response.status===200 && ['TICKET_ISSUED','CHECKED_IN'].includes(response.json.data.status))candidates.push({appointment:a,ticket:response.json.data});
  }
 }
 assert.equal(candidates.length,1,'Need exactly one valid Internal Medicine ticket today for this audit patient; book normally first.');
 ({appointment,ticket}=candidates[0]);pass('select actual current-day ticket; no mock, booking or reset');
 const mobileContext=await browser.newContext({viewport:{width:1700,height:1250}}),mobilePage=await mobileContext.newPage();observe(mobilePage);
 await mobilePage.addInitScript(()=>{const q=new URLSearchParams(location.search),d=q.get('device'),s=q.get('scope');if(d&&s)localStorage.setItem(`careflow.${s}.${d}.preferences.has_seen_onboarding`,'true');});
 await mobilePage.goto('http://localhost:3000');await mobilePage.getByRole('button',{name:'Luồng khám trên Mac',exact:true}).click();
 await mobilePage.locator('#demo-dialog').waitFor({state:'visible'});assert.equal(await mobilePage.locator('#demo-dialog a').count(),2);await mobilePage.getByRole('button',{name:'Đóng hướng dẫn trình diễn',exact:true}).click();pass('emulator exposes Mac E2E guide and real staff/doctor routes');
 await mobilePage.waitForSelector('[data-device-id="phone-03"] iframe');const mobile=mobilePage.frames().find(f=>new URL(f.url()).searchParams.get('device')==='phone-03');
 await mobile.locator('flt-semantics-placeholder').evaluate(e=>e.click());
 for(const [label,value] of [['Nhập tên đăng nhập hoặc email',username],['Nhập mật khẩu',password]]){await mobile.locator(`input[aria-label="${label}"]`).click();await mobilePage.waitForTimeout(100);await mobile.locator('input:focus').fill(value);}
 await mobile.getByRole('button',{name:'Đăng nhập',exact:true}).click();await mobilePage.waitForFunction(()=>document.querySelector('[data-device-id="phone-03"] .device-session').classList.contains('authenticated'));pass('real patient logs in on phone 03');
 const staffContext=await browser.newContext({permissions:['clipboard-read','clipboard-write']}),staffPage=await staffContext.newPage();observe(staffPage);await loginWeb(staffPage,'staff');await staffPage.waitForURL('**/staff/checkin');
 await staffPage.getByRole('button',{name:'Sao chép nội dung QR',exact:true}).waitFor();await staffPage.getByRole('button',{name:'Sao chép nội dung QR',exact:true}).click();await staffPage.getByRole('button',{name:'Đã sao chép nội dung QR',exact:true}).waitFor();
 const copied=await staffPage.evaluate(()=>navigator.clipboard.readText());await staffPage.getByText('Nội dung QR để nhập trên web giả lập',{exact:true}).click();assert.equal(await staffPage.getByRole('textbox',{name:'Nội dung QR bệnh viện',exact:true}).inputValue(),copied);assert.equal(copied.split('.').length,3);pass('Staff Web copies actual backend-signed QR; manual-copy fallback is available');
 await staffPage.getByText('Nội dung QR để nhập trên web giả lập',{exact:true}).click();
 const doctorContext=await browser.newContext(),doctorPage=await doctorContext.newPage();observe(doctorPage);await loginWeb(doctorPage,'an@careflow.vn');await doctorPage.goto('http://localhost:5173/dashboard/queue');
 if(ticket.status==='TICKET_ISSUED'){
  const q=await doctorPage.waitForResponse(r=>new URL(r.url()).pathname==='/api/queues/rooms/ROOM-01/active');const active=(await q.json()).data.entries;assert.ok(!active.some(e=>e.appointmentId===appointment.id));pass('un-checked-in patient is absent from real doctor queue');
 }
 await staffPage.getByPlaceholder('Nhập mã phiếu khám',{exact:true}).fill(ticket.ticketCode);
 const [checked]=await Promise.all([staffPage.waitForResponse(r=>new URL(r.url()).pathname==='/api/queues/check-in' && r.request().method()==='POST'),staffPage.getByRole('button',{name:'Hỗ trợ check-in',exact:true}).click()]);assert.equal(checked.status(),200);const entry=(await checked.json()).data;assert.equal(entry.appointmentId,appointment.id);assert.equal(entry.queueStatus,'CHECKED_IN');pass('actual staff-assisted intake writes CHECKED_IN through API');
 const refreshed=doctorPage.waitForResponse(r=>new URL(r.url()).pathname==='/api/queues/rooms/ROOM-01/active');await doctorPage.reload();const active=(await(await refreshed).json()).data.entries;assert.ok(active.some(e=>e.appointmentId===appointment.id && e.queueStatus==='CHECKED_IN'));await doctorPage.getByText(appointment.patientName,{exact:true}).first().waitFor();pass('same actual patient appears on independently authenticated Doctor Web');
 assert.equal((await staffPage.evaluate(()=>JSON.parse(localStorage.getItem('careflow_user')).role)),'STAFF');assert.equal((await doctorPage.evaluate(()=>JSON.parse(localStorage.getItem('careflow_user')).role)),'DOCTOR');pass('staff and doctor sessions stay isolated');
 await mobile.evaluate(id=>{location.hash='#/appointment/'+id;},appointment.id);await mobile.getByRole('button',{name:'Xem phiếu khám',exact:true}).click();await mobile.locator('[aria-label*="'+ticket.ticketCode+'"]').first().waitFor();
 const actualTicket=await client.request('/api/queues/tickets/appointment/'+appointment.id);assert.equal(actualTicket.status,200);assert.equal(actualTicket.json.data.status,'CHECKED_IN');pass('patient reads same checked-in ticket from real Queue');
 assert.ok(active.some(e=>e.queueNumber==='NOI-010' && e.queueStatus==='IN_PROGRESS'),'Existing demo consultation must be preserved');pass('existing active doctor consultation preserved; no forced call/start');
 assert.ok(!requests.some(r=>r.status===401));assert.deepEqual(errors,[]);pass('real three-role browser flow has no spurious 401 or JavaScript errors');
 await doctorPage.screenshot({path:'/tmp/careflow-mac-checkin-doctor.png',fullPage:true});await staffPage.screenshot({path:'/tmp/careflow-mac-checkin-staff.png',fullPage:true});
 console.log('Actual checked-in ticket',ticket.ticketCode,'doctor queue count',active.length);
} finally {
 await client.request('/api/auth/logout',{method:'POST',body:{refreshToken:client.tokens.refreshToken},token:null});
 await writeFile(new URL('../../.planning/security/mac-check-in-browser-results.json',import.meta.url),JSON.stringify({checkedAt:new Date().toISOString(),mock:false,checks,requests,errors,appointmentId:appointment?.id,ticketCode:ticket?.ticketCode},null,2)+'\n');await browser.close();
}
