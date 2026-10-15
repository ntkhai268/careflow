import assert from 'node:assert/strict';
import {randomUUID} from 'node:crypto';
import {mkdir,writeFile} from 'node:fs/promises';

if (!process.argv.includes('--audit-db')) {
  console.error('Chỉ chạy trên DB audit riêng: npm run test:api -- --audit-db');
  process.exit(1);
}
const base = 'http://localhost:3000';
const cases = [];
async function call(name,path,{status=200,token,method='GET',body,headers={}}={}) {
  const response = await fetch(base+path,{
    method,headers:{Origin:base,...(token ? {Authorization:`Bearer ${token}`} : {}),...(body ? {'Content-Type':'application/json'} : {}),...headers},
    body:body ? JSON.stringify(body) : undefined,signal:AbortSignal.timeout(25000),
  });
  assert.equal(response.status,status,`${name}: expected ${status}, got ${response.status}`);
  const payload = await response.json();
  cases.push({case:name,status:response.status,result:'PASS'});
  return path.startsWith('/__emulator/') ? payload : payload.data;
}
const config = await call('simulator-config','/__emulator/config');
assert.equal(config.gateway,'http://127.0.0.1:18080','Chỉ cho phép Gateway audit 127.0.0.1:18080');
const health = await call('gateway-health','/__emulator/health');
assert.ok(health.reachable && health.status===200,'Gateway audit phải hoạt động');
const suffix = randomUUID().replaceAll('-','').slice(0,12);
const username = `simulator${suffix}`;
const password = process.env.CAREFLOW_AUDIT_PASSWORD || 'Password123@';
let token,refreshToken,adminRefresh,appointment,cancelled=false;
try {
  const user = await call('register-synthetic-patient','/api/auth/register',{method:'POST',status:201,
    body:{username,email:`${username}@example.test`,password}});
  await call('wrong-password-is-rejected','/api/auth/login',{method:'POST',status:401,body:{usernameOrEmail:username,password:'wrong-password'}});
  const auth = await call('patient-login','/api/auth/login',{method:'POST',body:{usernameOrEmail:username,password}});
  token=auth.accessToken;refreshToken=auth.refreshToken;
  assert.ok(token && refreshToken);
  const me = await call('verified-current-user','/api/auth/me',{token});
  assert.equal(me.id,user.id);
  const profile = await call('create-patient-profile','/api/patients',{method:'POST',status:201,token,
    body:{userId:user.id,fullName:`Synthetic emulator ${suffix}`,gender:'OTHER'}});
  const profiles = await call('mobile-patient-profiles',`/api/patients/user/${user.id}/profiles`,{token});
  assert.ok(profiles.some(p=>p.id===profile.id));
  const departments = await call('real-department-catalog','/api/appointments/departments',{token});
  assert.ok(departments.length>0);
  const day = new Intl.DateTimeFormat('en-CA',{timeZone:'Asia/Ho_Chi_Minh',year:'numeric',month:'2-digit',day:'2-digit'}).format(new Date(Date.now()+86400000));
  const slots = await call('real-slot-capacity',`/api/appointments/time-slots/availability?department=NOI_TONG_QUAT&date=${day}`,{token});
  const slot = slots.find(s=>s.available && s.remaining>0);
  assert.ok(slot,'Không có ca trống trong ngày kiểm thử');
  const payload = {patientId:profile.id,patientName:profile.fullName,department:'NOI_TONG_QUAT',
    appointmentDate:day,timeSlot:slot.timeSlot,reason:'Synthetic mobile emulator API verification'};
  const key = randomUUID();
  appointment = await call('book-real-appointment','/api/appointments',{status:201,method:'POST',token,body:payload,headers:{'Idempotency-Key':key}});
  assert.equal(appointment.ownerUserId,user.id);
  const retry = await call('booking-retry-is-idempotent','/api/appointments',{status:201,method:'POST',token,body:payload,headers:{'Idempotency-Key':key}});
  assert.equal(retry.id,appointment.id);
  const ticketPath = `/api/queues/tickets/appointment/${appointment.id}`;
  // Poll for asynchronous outbox delivery; only the final assertion is recorded.
  for (let attempt=0;attempt<50;attempt++) {
    const response = await fetch(base+ticketPath,{headers:{Origin:base,Authorization:`Bearer ${token}`},signal:AbortSignal.timeout(10000)});
    await response.arrayBuffer();
    if (response.status===200) break;
    assert.equal(response.status,404,'Queue ticket polling failed');
    await new Promise(r=>setTimeout(r,200));
  }
  const ticket = await call('queue-ticket-created-by-real-event',ticketPath,{token});
  assert.equal(ticket.appointmentId,appointment.id);
  assert.equal(ticket.status,'TICKET_ISSUED');
  await call('ticket-requires-authentication',ticketPath,{status:401});
  await call('checkin-requires-valid-qr','/api/queues/check-in',{status:401,method:'POST',token,
    body:{appointmentId:appointment.id,checkInQrToken:'invalid-qr',latitude:10.7769,longitude:106.7009,accuracyMeters:10}});
  const admin = await call('admin-login-for-hospital-qr','/api/auth/login',{method:'POST',body:{usernameOrEmail:'admin',password}});
  adminRefresh=admin.refreshToken;
  const qr = await call('server-issued-hospital-qr','/api/queues/hospital-check-in-qr',{token:admin.accessToken});
  await call('future-booking-cannot-check-in-today','/api/queues/check-in',{status:409,method:'POST',token,
    body:{appointmentId:appointment.id,checkInQrToken:qr.qrToken,latitude:10.7769,longitude:106.7009,accuracyMeters:10}});
  const unchanged = await call('denied-checkin-keeps-ticket-issued',ticketPath,{token});
  assert.equal(unchanged.status,'TICKET_ISSUED');
  const result = await call('cancel-synthetic-booking',`/api/appointments/${appointment.id}/cancel`,{method:'PUT',token});
  assert.equal(result.status,'CANCELLED');cancelled=true;
  await call('logout-patient','/api/auth/logout',{method:'POST',body:{refreshToken}});
  refreshToken=null;
  await call('revoked-session-is-rejected','/api/auth/me',{status:401,token});
  await call('logout-qr-admin','/api/auth/logout',{method:'POST',body:{refreshToken:adminRefresh}});
  adminRefresh=null;
  const report={gateway:config.gateway,via:base,mode:'real',passed:cases.length,cases,
    limitation:'Booking uses a real available slot tomorrow. Check-in denial for invalid QR/wrong day is verified; successful same-day check-in is not claimed. Synthetic account/profile retained in audit; booking cancelled.'};
  await mkdir(new URL('../.runtime/',import.meta.url),{recursive:true});
  await writeFile(new URL('../.runtime/live-api-results.json',import.meta.url),JSON.stringify(report,null,2)+'\n');
  console.log(`${cases.length}/${cases.length} kiểm tra HTTP qua giả lập đạt; báo cáo .runtime/live-api-results.json`);
} finally {
  if (appointment && !cancelled && token) await fetch(base+`/api/appointments/${appointment.id}/cancel`,{method:'PUT',headers:{Origin:base,Authorization:`Bearer ${token}`}}).catch(()=>{});
  for (const refresh of [refreshToken,adminRefresh].filter(Boolean)) await fetch(base+'/api/auth/logout',{
    method:'POST',headers:{Origin:base,'Content-Type':'application/json'},body:JSON.stringify({refreshToken:refresh})}).catch(()=>{});
}
