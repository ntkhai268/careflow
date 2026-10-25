import {auditFetch as fetch} from './bound-http.mjs';
import assert from 'node:assert/strict';
import {createHash} from 'node:crypto';
import {mkdir, writeFile} from 'node:fs/promises';

if (!process.argv.includes('--audit-db')) throw new Error('Chạy npm run dataset:seed -- --audit-db trên DB audit riêng.');
const base = 'http://localhost:3000';
const password = 'Password123@';
const definitions = [
  {id:'new-patient', deviceId:'phone-01', username:'mobile_case01', fullName:'Mẫu Mobile 01', title:'Bệnh nhân mới', description:'Có hồ sơ thật, chưa có lịch khám. Thử đặt lịch và chọn khung giờ từ API.', expectedStatus:null},
  {id:'booked', deviceId:'phone-02', username:'mobile_case02', fullName:'Mẫu Mobile 02', title:'Đã đặt lịch', description:'Có lịch xác nhận và phiếu khám do sự kiện backend tạo. Xem lịch và phiếu; chỉ check-in đúng ngày khám.', expectedStatus:'CONFIRMED'},
  {id:'cancelled', deviceId:'phone-03', username:'mobile_case03', fullName:'Mẫu Mobile 03', title:'Đã hủy lịch', description:'Đã đặt rồi hủy bằng API thật. Xem lịch đã hủy và thử đặt lịch mới.', expectedStatus:'CANCELLED'},
];
const checks = [], sessions = [], scenarios = [];
async function api(path, {method='GET', token, body, statuses=[200], headers={}}={}) {
  const response = await fetch(base+path, {method, headers:{Origin:base, ...headers,
    ...(token ? {Authorization:`Bearer ${token}`} : {}), ...(body ? {'Content-Type':'application/json'} : {})},
    body:body ? JSON.stringify(body) : undefined, signal:AbortSignal.timeout(25000)});
  const payload = await response.json();
  assert.ok(statuses.includes(response.status), `${method} ${path}: HTTP ${response.status}: ${payload.message || ''}`);
  checks.push({method,path,status:response.status});
  return {status:response.status, data:path.startsWith('/__emulator/') ? payload : payload.data};
}
const config = (await api('/__emulator/config')).data;
assert.equal(config.gateway,'https://localhost:18080','Chỉ được ghi dữ liệu vào Gateway audit cục bộ.');
const health = (await api('/__emulator/health')).data;
assert.ok(health.reachable && health.status===200);
async function ticketFor(appointmentId, token, expectedStatus) {
  for (let attempt=0;attempt<80;attempt++) {
    const result = await api(`/api/queues/tickets/appointment/${appointmentId}`, {token,statuses:[200,404]});
    if (result.status===200 && result.data.status===expectedStatus) return result.data;
    await new Promise(resolve=>setTimeout(resolve,250));
  }
  throw new Error(`Phiếu khám chưa đạt ${expectedStatus}; kiểm tra luồng sự kiện thật.`);
}
try {
  for (const definition of definitions) {
    const {username,fullName,expectedStatus} = definition;
    await api('/api/auth/register', {method:'POST',statuses:[201,409],body:{username,email:`${username}@example.test`,password}});
    const auth = (await api('/api/auth/login',{method:'POST',body:{usernameOrEmail:username,password}})).data;
    sessions.push(auth.refreshToken);
    const token = auth.accessToken;
    const me = (await api('/api/auth/me',{token})).data;
    assert.equal(me.username,username); assert.equal(me.role,'PATIENT');
    let profiles = (await api(`/api/patients/user/${me.id}/profiles`,{token})).data;
    let profile = profiles.find(item=>item.fullName===fullName);
    if (!profile) profile = (await api('/api/patients',{method:'POST',statuses:[201],token,
      body:{userId:me.id,fullName,gender:'OTHER',dateOfBirth:'2000-01-01'}})).data;
    assert.equal(profile.userId,me.id);
    const appointments = (await api(`/api/appointments/patient/${profile.id}`,{token})).data;
    const marker = `Real mobile dataset: ${definition.id}`;
    let appointment = appointments.find(item=>item.reason===marker), ticket = null;
    if (expectedStatus && !appointment) {
      const departments = (await api('/api/appointments/departments',{token})).data;
      assert.ok(departments.some(item=>item.code==='NOI_TONG_QUAT' || item.department==='NOI_TONG_QUAT' || item==='NOI_TONG_QUAT'), 'Khoa Nội phải có trong danh mục thật.');
      let booking;
      for (let offset=1;offset<=7 && !booking;offset++) {
        const date = new Intl.DateTimeFormat('en-CA',{timeZone:'Asia/Ho_Chi_Minh',year:'numeric',month:'2-digit',day:'2-digit'}).format(new Date(Date.now()+offset*86400000));
        const slots = (await api(`/api/appointments/time-slots/availability?department=NOI_TONG_QUAT&date=${date}`,{token})).data;
        const slot = slots.find(item=>item.available && item.remaining>0);
        if (slot) booking={date,slot:slot.timeSlot};
      }
      assert.ok(booking,'Không có khung giờ khám thật còn trống trong 7 ngày tới.');
      const key = createHash('sha256').update(`${profile.id}:${definition.id}:${booking.date}`).digest('hex');
      appointment = (await api('/api/appointments',{method:'POST',statuses:[201],token,headers:{'Idempotency-Key':key},body:{
        patientId:profile.id,patientName:profile.fullName,department:'NOI_TONG_QUAT',appointmentDate:booking.date,timeSlot:booking.slot,reason:marker}})).data;
    }
    if (appointment) {
      assert.equal(appointment.ownerUserId,me.id); assert.equal(appointment.patientId,profile.id);
      if (expectedStatus==='CANCELLED' && appointment.status==='CONFIRMED') {
        await ticketFor(appointment.id,token,'TICKET_ISSUED');
        appointment = (await api(`/api/appointments/${appointment.id}/cancel`,{method:'PUT',token})).data;
      }
      assert.equal(appointment.status,expectedStatus,'Ca đã thay đổi do thao tác thực tế; giữ dữ liệu và kiểm tra lại, không tự reset.');
      ticket = await ticketFor(appointment.id,token,expectedStatus==='CANCELLED' ? 'CANCELLED' : 'TICKET_ISSUED');
      assert.equal(ticket.appointmentId,appointment.id);
    } else assert.equal(appointments.length,0,'Bệnh nhân mới đã có lịch; không tự xóa dữ liệu người dùng.');
    scenarios.push({...definition,password,userId:me.id,patientId:profile.id,
      appointment:appointment ? {id:appointment.id,date:appointment.appointmentDate,timeSlot:appointment.timeSlot,status:appointment.status,department:appointment.department,room:appointment.roomDisplayName} : null,
      ticket:ticket ? {id:ticket.id,queueNumber:ticket.queueNumber,status:ticket.status} : null});
    console.log(`${definition.deviceId}: ${username} — ${definition.title} — dữ liệu API đã kiểm tra`);
  }
  // Verify ownership using a second independent session. No fixture/interception.
  const second = (await api('/api/auth/login',{method:'POST',body:{usernameOrEmail:definitions[1].username,password}})).data;
  sessions.push(second.refreshToken);
  await api(`/api/patients/user/${scenarios[0].userId}/profiles`,{token:second.accessToken,statuses:[403]});
  await mkdir(new URL('../.runtime/',import.meta.url),{recursive:true});
  await writeFile(new URL('../.runtime/mobile-dataset.json',import.meta.url),JSON.stringify({gateway:config.gateway,mode:'real',generatedAt:new Date().toISOString(),scenarios},null,2)+'\n',{mode:0o600});
} finally {
  for (const refreshToken of sessions) await api('/api/auth/logout',{method:'POST',body:{refreshToken}});
}
await writeFile(new URL('../.runtime/mobile-dataset-verification.json',import.meta.url),JSON.stringify({mode:'real',mock:false,checks,passed:checks.length},null,2)+'\n');
console.log(`${checks.length} request kiểm tra; dataset được giữ trong DB audit riêng. Không lưu token.`);
