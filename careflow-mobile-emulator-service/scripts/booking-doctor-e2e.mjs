/* Real browser booking/check-in/doctor journey against the owned local audit stack. */
import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
import {readFile,writeFile} from 'node:fs/promises';
import {AuditClient} from '../../scripts/security/audit-client.mjs';
const {chromium}=createRequire(import.meta.url)(process.env.PLAYWRIGHT_MODULE || '/Users/dangkhoii/Library/Caches/ms-playwright-go/1.57.0/package');
if(!process.argv.includes('--audit-db'))throw Error('--audit-db required: this creates a real appointment/queue/consultation.');
assert.equal((await(await fetch('http://localhost:3000/__emulator/config')).json()).gateway,'https://localhost:18080','Only the owned local audit stack is accepted');
const account=JSON.parse(await readFile(new URL('../.runtime/e2e-noi-account.json',import.meta.url),'utf8'));
const base='http://localhost:3000',checks=[],requests=[],errors=[];
const patient=new AuditClient(),doctor=new AuditClient(),staff=new AuditClient(),otherDoctor=new AuditClient();
let appointment,ticket,consultation,web;
function pass(name){checks.push({name,status:'PASS'});console.log('PASS '+name);}
async function call(client,path,options={},status=200){const r=await client.request(path,options);assert.equal(r.status,status,path+' '+(r.status!==status?JSON.stringify(r.json):''));return r.json.data;}
await patient.login(account.username);await doctor.login('an@careflow.vn');await staff.login('staff');await otherDoctor.login('doctor');
const browser=await chromium.launch({headless:true,executablePath:process.env.CHROME_PATH || '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome'}),context=await browser.newContext({viewport:{width:1700,height:1250}}),page=await context.newPage();page.setDefaultTimeout(20000);
page.on('pageerror',e=>errors.push(e.message));page.on('response',r=>{const path=new URL(r.url()).pathname;if(path.startsWith('/api/'))requests.push({path,method:r.request().method(),status:r.status()});});
await page.addInitScript(()=>{const q=new URLSearchParams(location.search),d=q.get('device'),s=q.get('scope');if(d&&s)localStorage.setItem(`careflow.${s}.${d}.preferences.has_seen_onboarding`,'true');});
let mobile;
try {
 await page.goto(base);await page.waitForSelector('[data-device-id="phone-01"] iframe');mobile=page.frames().find(f=>new URL(f.url()).searchParams.get('device')==='phone-01');await mobile.locator('flt-semantics-placeholder').evaluate(e=>e.click());
 for(const [label,value] of [['Nhập tên đăng nhập hoặc email',account.username],['Nhập mật khẩu',account.password]]){await mobile.locator(`input[aria-label="${label}"]`).click();await page.waitForTimeout(100);await mobile.locator('input:focus').fill(value);}
 await mobile.getByRole('button',{name:'Đăng nhập',exact:true}).click();await page.waitForFunction(()=>document.querySelector('[data-device-id="phone-01"] .device-session').classList.contains('authenticated'));pass('real mobile login');
 const existing=await call(patient,'/api/appointments/patient/'+account.patientId);
 appointment=existing.find(a=>a.department==='NOI_TONG_QUAT' && a.status==='CONFIRMED');
 if(!appointment){
  await mobile.getByRole('button',{name:'Đặt lịch khám',exact:true}).click();await mobile.getByRole('button',{name:new RegExp(account.fullName)}).click();
  const slotsResponse=page.waitForResponse(r=>new URL(r.url()).pathname==='/api/appointments/time-slots/availability');
  await mobile.getByText('Nội tổng quát',{exact:true}).click();const slots=(await(await slotsResponse).json()).data;
  const now=new Date(),minute=Number(new Intl.DateTimeFormat('en-GB',{timeZone:'Asia/Ho_Chi_Minh',hour:'2-digit',minute:'2-digit',hour12:false}).format(now).slice(0,2))*60+Number(new Intl.DateTimeFormat('en-GB',{timeZone:'Asia/Ho_Chi_Minh',hour:'2-digit',minute:'2-digit',hour12:false}).format(now).slice(3));
  const chosen=slots.find(s=>s.available && Number(s.timeSlot.slice(0,2))*60+Number(s.timeSlot.slice(3,5))>=minute && Number(s.timeSlot.slice(0,2))*60+Number(s.timeSlot.slice(3,5))<=minute+45);assert.ok(chosen,'Need an actual available slot within the hospital check-in window.');
  await mobile.getByRole('button',{name:new RegExp('^'+chosen.timeSlot)}).click();await mobile.getByRole('button',{name:'Tiếp tục',exact:true}).click();
  const booked=page.waitForResponse(r=>new URL(r.url()).pathname==='/api/appointments' && r.request().method()==='POST');
  await mobile.getByRole('button',{name:'Xác nhận đặt khám',exact:true}).click();const response=await booked;assert.equal(response.status(),201);appointment=(await response.json()).data;pass('browser creates real Internal Medicine appointment 201');
  await mobile.getByRole('button',{name:'Xem phiếu khám',exact:true}).click();
 }else await mobile.evaluate(id=>{location.hash='#/appointment/'+id;},appointment.id);
 assert.equal(appointment.roomId,'ROOM-01');assert.equal(appointment.doctorId,doctor.tokens.user.id);pass('real Directory assigns ROOM-01 and Internal Medicine doctor');
 await mobile.getByRole('button',{name:'Xem phiếu khám',exact:true}).waitFor();pass('appointment details render without false missing-ticket error');
 await mobile.getByRole('button',{name:'Xem phiếu khám',exact:true}).click();
 ticket=await call(patient,'/api/queues/tickets/appointment/'+appointment.id);
 await mobile.locator('[aria-label*="'+ticket.ticketCode+'"]').first().waitFor();pass('mobile renders broker-created real ticket and number');
 await writeFile(new URL('../.runtime/e2e-noi-visit.json',import.meta.url),JSON.stringify({username:account.username,fullName:account.fullName,patientId:account.patientId,appointmentId:appointment.id,ticketId:ticket.ticketId,ticketCode:ticket.ticketCode,date:appointment.appointmentDate,timeSlot:appointment.timeSlot,doctor:'an@careflow.vn'},null,2)+'\n',{mode:0o600});
 if(ticket.status==='TICKET_ISSUED') {
 let queue=await call(doctor,'/api/queues/rooms/ROOM-01/active');assert.ok(!queue.entries.some(e=>e.appointmentId===appointment.id));pass('un-checked-in appointment is absent from active doctor queue');
 await call(doctor,'/api/consultations',{method:'POST',body:{appointmentId:appointment.id,patientId:account.patientId,doctorId:doctor.tokens.user.id}},409);pass('cannot start consultation before being called');
 await call(otherDoctor,'/api/queues/rooms/ROOM-01/active',{},403);pass('doctor from another room cannot read Internal Medicine queue');
 const session=Number(appointment.timeSlot.slice(0,2))<12?'MORNING':'AFTERNOON';const qr=await call(staff,`/api/queues/rooms/ROOM-01/check-in-qr?date=${appointment.appointmentDate}&session=${session}`);
 const admin=new AuditClient();await admin.login('admin');const location=await call(admin,'/api/queues/hospital-check-in-config');await call(admin,'/api/auth/logout',{method:'POST',body:{refreshToken:admin.tokens.refreshToken},token:null});
 await mobile.getByRole('button',{name:'Quét QR check-in tại bệnh viện',exact:true}).click();
 async function fill(label,value){const field=mobile.locator(`textarea[aria-label="${label}"], input[aria-label="${label}"]`);await field.click();await page.waitForTimeout(100);await mobile.locator('input:focus,textarea:focus').fill(String(value));}
 await fill('Nội dung QR check-in',qr.qrToken);await fill('Vĩ độ (latitude)',location.latitude);await fill('Kinh độ (longitude)',location.longitude);await fill('Độ chính xác (m)',10);
 const checked=page.waitForResponse(r=>new URL(r.url()).pathname==='/api/queues/check-in' && r.request().method()==='POST');await mobile.getByRole('button',{name:'Xác nhận check-in',exact:true}).click();assert.equal((await checked).status(),200);pass('browser check-in validates actual hospital QR and submitted test coordinates');
 queue=await call(doctor,'/api/queues/rooms/ROOM-01/active');const entry=queue.entries.find(e=>e.appointmentId===appointment.id);assert.ok(entry);assert.equal(entry.queueStatus,'CHECKED_IN');pass('check-in makes the same ticket visible to assigned doctor');
 } else pass('resume same actual patient after the already verified browser check-in');
 const queueAfter=await call(doctor,'/api/queues/rooms/ROOM-01/active');const entry=queueAfter.entries.find(e=>e.appointmentId===appointment.id);assert.ok(entry);
 await call(otherDoctor,'/api/consultations',{method:'POST',body:{appointmentId:appointment.id,patientId:account.patientId,doctorId:otherDoctor.tokens.user.id}},403);pass('different-room doctor cannot create this patient consultation');
 web=await context.newPage();web.setDefaultTimeout(20000);web.on('response',r=>{const path=new URL(r.url()).pathname;if(path.startsWith('/api/')){requests.push({path,method:r.request().method(),status:r.status()});console.log('DOCTOR API',r.status(),path);}});web.on('pageerror',e=>errors.push(e.message));await web.goto('http://localhost:5173/login');await web.locator('#email').fill('an@careflow.vn');await web.locator('#password').fill(account.password);await web.locator('button[type=submit]').click();await web.waitForURL('**/dashboard/**');await web.goto('http://localhost:5173/dashboard/queue');
 const row=web.locator('div.px-3.py-3').filter({hasText:account.patientId});await row.waitFor();pass('actual Doctor Web renders the checked-in Internal Medicine patient');
 if(entry.queueStatus==='CHECKED_IN') {
  const [called]=await Promise.all([web.waitForResponse(r=>new URL(r.url()).pathname===`/api/queues/entries/${entry.entryId}/call`),row.getByRole('button',{name:'Gọi số',exact:true}).click()]);assert.equal(called.status(),200);pass('assigned doctor calls patient from the actual web UI');
 }
 if(entry.queueStatus!=='IN_PROGRESS') {
  const [createdResponse]=await Promise.all([web.waitForResponse(r=>new URL(r.url()).pathname==='/api/consultations' && r.request().method()==='POST'),row.getByRole('button',{name:'Bắt đầu khám',exact:true}).click()]);assert.equal(createdResponse.status(),201);consultation=(await createdResponse.json()).data;await web.waitForURL('**/consultation/**');pass('Doctor Web starts actual consultation for called queue entry');
 } else {
  const list=await call(patient,'/api/consultations/appointment/'+appointment.id);consultation=list.find(c=>c.status==='IN_PROGRESS');assert.ok(consultation);await row.getByRole('button',{name:'Vào khám',exact:true}).click();await web.waitForURL('**/consultation/**');pass('Doctor Web reopens same actual consultation');
 }
 const consultations=await call(patient,'/api/consultations/appointment/'+appointment.id);assert.ok(consultations.some(c=>c.id===consultation.id));pass('patient reads consultation through scoped service delegation');
 for(const path of ['/api/patients/'+account.patientId+'/health-records','/api/emr/patients/'+account.patientId+'/summary','/api/prescriptions/patient/'+account.patientId]){await call(patient,path);pass('positive RSA resource '+path.split('/').slice(1,3).join('/'));}
 assert.ok(!requests.some(r=>r.status===401));pass('successful real mobile journey has no spurious 401 or refresh loop');
 assert.deepEqual(errors,[]);pass('both real interfaces have no uncaught JavaScript errors');
 await page.screenshot({path:new URL('../.runtime/e2e-noi-mobile.png',import.meta.url).pathname,fullPage:true});await web.screenshot({path:new URL('../.runtime/e2e-noi-doctor.png',import.meta.url).pathname,fullPage:true});
} catch(error){console.error(error.stack);if(web)console.error('DOCTOR SCREEN',await web.locator('body').innerText());if(mobile)console.error('MOBILE SCREEN',await mobile.locator('body').innerText());await page.screenshot({path:new URL('../.runtime/e2e-noi-failure.png',import.meta.url).pathname,fullPage:true});throw error;}
finally {
 for(const client of [patient,doctor,staff,otherDoctor])if(client.tokens)await client.request('/api/auth/logout',{method:'POST',body:{refreshToken:client.tokens.refreshToken},token:null});
 await writeFile(new URL('../.runtime/booking-doctor-e2e-results.json',import.meta.url),JSON.stringify({checkedAt:new Date().toISOString(),mock:false,checks,requests,errors,appointmentId:appointment?.id,consultationId:consultation?.id},null,2)+'\n');await browser.close();
}
