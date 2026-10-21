/* Real regression for the existing staff “Gọi lại” button; advances one audit ticket to CALLED. */
import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
import {writeFile} from 'node:fs/promises';
import {AuditClient} from './audit-client.mjs';
if(!process.argv.includes('--audit-db'))throw Error('--audit-db required; consumes the three allowed calls on an existing checked-in audit ticket.');
assert.equal((await(await fetch('http://localhost:3000/__emulator/config')).json()).gateway,'https://localhost:18080');
const {chromium}=createRequire(import.meta.url)(process.env.PLAYWRIGHT_MODULE || '/Users/dangkhoii/Library/Caches/ms-playwright-go/1.57.0/package');
const ticketCode=process.env.CAREFLOW_RECALL_TICKET || 'NOI-002',checks=[],requests=[],errors=[];
const browser=await chromium.launch({headless:true,executablePath:process.env.CHROME_PATH || '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome'}),page=await browser.newPage({viewport:{width:1440,height:1050}});page.setDefaultTimeout(25000);
const staff=new AuditClient();assert.equal((await staff.login('staff')).status,200);
function pass(name){checks.push({name,status:'PASS'});console.log('PASS '+name);}
page.on('pageerror',e=>errors.push(e.message));page.on('response',r=>{const path=new URL(r.url()).pathname;if(path.startsWith('/api/'))requests.push({path,method:r.request().method(),status:r.status()});});
try {
 const before=await staff.request('/api/queues/rooms/ROOM-01/active');assert.equal(before.status,200);const ticket=before.json.data.entries.find(e=>e.queueNumber===ticketCode);assert.ok(ticket,'Need an existing checked-in real ticket in ROOM-01');assert.equal(ticket.queueStatus,'CHECKED_IN');assert.equal(ticket.callAttempts,0);
 await page.goto('http://localhost:5173/login');await page.locator('#email').fill('staff');await page.locator('#password').fill('Password123@');await page.locator('button[type=submit]').click();await page.waitForURL('**/staff/checkin');
 const row=page.locator('tbody tr').filter({hasText:ticketCode}),button=row.getByRole('button',{name:'Gọi lại',exact:true});await button.waitFor();assert.equal(await button.isEnabled(),true);assert.equal(await row.getByRole('button',{name:'Vắng mặt',exact:true}).isDisabled(),true);pass('checked-in ticket retains enabled Gọi lại label; miss is unavailable before call');
 const active=page.locator('tbody tr').filter({hasText:'NOI-010'});await active.getByText('Đang khám',{exact:true}).waitFor();assert.equal(await active.getByRole('button',{name:'Gọi lại',exact:true}).isDisabled(),true);assert.equal(await active.getByRole('button',{name:'Vắng mặt',exact:true}).isDisabled(),true);pass('in-progress clinical case cannot be recalled or marked missed');
 for(const [action,attempt] of [['call',1],['recall',2],['recall',3]]){
  const refreshed=page.waitForResponse(r=>new URL(r.url()).pathname==='/api/queues/rooms/ROOM-01/active');
  const [response]=await Promise.all([page.waitForResponse(r=>new URL(r.url()).pathname.startsWith('/api/queues/entries/'+ticket.entryId+'/') && r.request().method()==='POST'),button.click()]);
  assert.equal(new URL(response.url()).pathname,'/api/queues/entries/'+ticket.entryId+'/'+action);assert.equal(response.status(),200);const entry=(await response.json()).data;assert.equal(entry.queueStatus,'CALLED');assert.equal(entry.callAttempts,attempt);await refreshed;
  await row.getByText('Đã gọi số',{exact:true}).waitFor();pass(`same Gọi lại button routes to /${action} and persists call attempt ${attempt}`);
 }
 await row.getByTitle('Đã gọi đủ 3 lần; hãy đánh dấu vắng mặt nếu bệnh nhân không đến.').waitFor();assert.equal(await button.isDisabled(),true);assert.equal(await row.getByRole('button',{name:'Vắng mặt',exact:true}).isEnabled(),true);pass('three-call cap disables recall and permits the supported missed action');
 const after=await staff.request('/api/queues/rooms/ROOM-01/active');const entry=after.json.data.entries.find(e=>e.queueNumber===ticketCode);assert.equal(entry.queueStatus,'CALLED');assert.equal(entry.callAttempts,3);assert.ok(after.json.data.entries.some(e=>e.queueNumber==='NOI-010' && e.queueStatus==='IN_PROGRESS'));pass('real database state retained; existing consultation left in progress');
 assert.ok(!requests.some(r=>r.status>=400));assert.deepEqual(errors,[]);pass('fixed browser journey has no failing API request or uncaught JavaScript error');
 await row.screenshot({path:'/tmp/careflow-staff-recall-fixed.png'});
} finally {
 await staff.request('/api/auth/logout',{method:'POST',body:{refreshToken:staff.tokens.refreshToken},token:null});
 await writeFile(new URL('../../.planning/security/staff-recall-browser-results.json',import.meta.url),JSON.stringify({checkedAt:new Date().toISOString(),mock:false,ticketCode,checks,requests,errors},null,2)+'\n');await browser.close();
}
