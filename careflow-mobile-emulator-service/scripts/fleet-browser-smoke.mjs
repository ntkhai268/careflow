import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
import {mkdir, writeFile} from 'node:fs/promises';
const require=createRequire(import.meta.url);
let playwright;
try { playwright=require('playwright'); }
catch { playwright=require(process.env.PLAYWRIGHT_MODULE || '/Users/dangkhoii/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright'); }
const browser=await playwright.chromium.launch({executablePath:process.env.CHROME_PATH || '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome',headless:true});
const context=await browser.newContext({viewport:{width:1440,height:1050}});
const page=await context.newPage();page.setDefaultTimeout(25000);
const errors=[],requests=[],cases=[],tokens=new Map();
const users={alpha:{id:'a0000001-0000-0000-0000-000000000001',username:'alpha',email:'alpha@example.test',role:'PATIENT'},beta:{id:'a0000002-0000-0000-0000-000000000002',username:'beta',email:'beta@example.test',role:'PATIENT'}};
const profile=user=>({id:user.id.replace(/^a/,'f'),userId:user.id,fullName:`QA ${user.username}`,gender:'OTHER',dateOfBirth:'2000-01-01'});
function passed(name){cases.push({case:name,result:'PASS'});console.log(`PASS ${name}`);}
function card(id){return page.locator(`[data-device-id="${id}"]`);}
function frame(id){return page.frames().find(item=>new URL(item.url()).searchParams.get('device')===id);}
async function ready(id){await page.waitForFunction(device=>{const item=[...document.querySelectorAll('.device-card')].find(el=>el.dataset.deviceId===device);return item && !item.querySelector('.device-session').textContent.includes('Đang mở');},id);}
async function semantics(child){const placeholder=child.locator('flt-semantics-placeholder');if(await placeholder.count())await placeholder.evaluate(el=>el.click());}
async function login(id,name){
  const child=frame(id);await semantics(child);
  const patientLoaded=page.waitForResponse(response=>new URL(response.url()).pathname===`/api/patients/user/${users[name].id}` && response.status()===200);
  patientLoaded.catch(()=>{});
  const username=child.locator('input[aria-label="Nhập tên đăng nhập hoặc email"]');
  await username.click();await username.pressSequentially(name,{delay:70});
  await page.waitForTimeout(200);
  const password=child.locator('input[aria-label="Nhập mật khẩu"]');
  await password.click();await password.pressSequentially('FixturePassword123',{delay:30});
  await page.waitForTimeout(200);
  await child.getByRole('button',{name:'Đăng nhập',exact:true}).click();
  await page.waitForFunction(device=>document.querySelector(`[data-device-id="${device}"] .device-session`).classList.contains('authenticated'),id);
  await patientLoaded;
}
page.on('pageerror',error=>errors.push(error.message));
await page.addInitScript(()=>{
  const query=new URLSearchParams(location.search),device=query.get('device'),scope=query.get('scope');
  if(device && scope)localStorage.setItem(`careflow.${scope}.${device}.preferences.has_seen_onboarding`,'true');
});
// All patient operations in this browser context use fixtures. No CareFlow
// account/session/database is modified by this browser verification.
await context.route('**/api/**',async route=>{
  const request=route.request(),path=new URL(request.url()).pathname;
  const headers=request.headers(),token=(headers.authorization || '').replace('Bearer ','');
  requests.push({path,device:headers['x-emulator-device'],user:tokens.get(token)?.username});
  const respond=(data,status=200)=>route.fulfill({status,json:{status,message:'QA fixture',data,timestamp:new Date().toISOString()}});
  if(path==='/api/auth/login'){
    const user=users[request.postDataJSON().usernameOrEmail];assert.ok(user);
    tokens.set(`qa-token-${user.username}`,user);
    return respond({accessToken:`qa-token-${user.username}`,refreshToken:`qa-refresh-${user.username}`,user});
  }
  if(path==='/api/auth/logout'){
    const name=request.postDataJSON().refreshToken.replace('qa-refresh-','');tokens.delete(`qa-token-${name}`);return respond(null);
  }
  const user=tokens.get(token);if(!user)return respond(null,401);
  if(path==='/api/auth/me')return respond(user);
  if(path.includes('/patients/user/'))return respond(path.endsWith('/profiles')?[profile(user)]:profile(user));
  return respond([]);
});
if(context.routeWebSocket)await context.routeWebSocket('**/ws/notifications',socket=>socket.close());
await page.route('**/__emulator/activity',route=>route.fulfill({json:[
  {id:2,device:'phone-02',method:'GET',path:'/api/patients/user/:id',status:200,ms:3,time:new Date().toISOString()},
  {id:1,device:'phone-01',method:'POST',path:'/api/auth/login',status:200,ms:5,time:new Date().toISOString()},
]}));
try{
  await page.goto('http://localhost:3000/',{waitUntil:'networkidle'});
  await Promise.all(['phone-01','phone-02','phone-03'].map(ready));
  assert.equal(await page.locator('.device-card').count(),3);
  for(const id of ['phone-01','phone-02','phone-03'])assert.deepEqual(await frame(id).evaluate(()=>({width:innerWidth,height:innerHeight})),{width:390,height:780});
  passed('three concurrent Flutter instances keep full 390×780 input viewports');
  assert.equal(await page.getByRole('button',{name:/demo/i}).count(),0);
  for(const id of ['phone-01','phone-02','phone-03'])assert.equal(new URL(frame(id).url()).searchParams.get('demo'),'false');
  passed('workspace exposes only real API devices, with no Demo controls');
  await login('phone-01','alpha');await login('phone-02','beta');
  assert.equal(await card('phone-03').locator('.authenticated').count(),0);
  const scope=new URL(frame('phone-01').url()).searchParams.get('scope');
  const storageKeys=await page.evaluate(()=>Object.keys(localStorage));
  for(const id of ['phone-01','phone-02'])assert.ok(storageKeys.includes(`careflow.${scope}.${id}.secure.jwt_token`));
  const scoped=requests.filter(item=>item.user);
  assert.ok(scoped.some(item=>item.device==='phone-01' && item.user==='alpha'));
  assert.ok(scoped.some(item=>item.device==='phone-02' && item.user==='beta'));
  assert.ok(!scoped.some(item=>item.device==='phone-01' && item.user==='beta' || item.device==='phone-02' && item.user==='alpha'));
  passed('two distinct logins, encrypted tokens, API headers and patient profiles stay per device');
  await frame('phone-02').evaluate(()=>{window.__qaStillMounted=true;});
  const setScale=async value=>page.locator('#card-scale').fill(String(value));
  await setScale(100);
  const fullSize=await card('phone-02').boundingBox();
  await setScale(50);
  const halfSize=await card('phone-02').boundingBox();
  assert.ok(Math.abs(halfSize.width/fullSize.width-0.5)<0.01);
  assert.ok(Math.abs(halfSize.height/fullSize.height-0.5)<0.01);
  assert.equal(Math.round(halfSize.width),220);
  for(const id of ['phone-01','phone-02','phone-03'])assert.deepEqual(await frame(id).evaluate(()=>({width:innerWidth,height:innerHeight})),{width:390,height:780});
  assert.equal(await frame('phone-02').evaluate(()=>window.__qaStillMounted),true);
  await setScale(75);
  assert.equal(await page.locator('#card-scale-value').textContent(),'75%');
  passed('50–100 percent scales the whole card without blank space, remounts or changed mobile viewports');
  await page.locator('[data-layout="2"]').click();
  assert.equal(await page.locator('#device-grid').evaluate(el=>getComputedStyle(el).getPropertyValue('--columns').trim()),'2');
  await page.locator('[data-layout="3"]').click();
  assert.equal(await frame('phone-02').evaluate(()=>window.__qaStillMounted),true);
  await page.locator('#show-connection').click();assert.ok(await page.locator('#connection-dialog').isVisible());await page.locator('#close-connection').click();
  await page.locator('#show-help').click();assert.ok(await page.locator('#help-dialog').isVisible());await page.locator('#close-help').click();
  passed('readable column controls and connection/help dialogs do not reload a phone');
  await page.locator('#add-device').click();assert.equal(await page.locator('.device-card').count(),4);
  assert.equal(await frame('phone-02').evaluate(()=>window.__qaStillMounted),true);
  passed('adding a phone leaves existing phones mounted');
  await card('phone-01').getByRole('button',{name:'Tạm dừng Điện thoại 01',exact:true}).click();
  assert.ok(await card('phone-01').locator('.paused-overlay').isVisible());
  assert.equal(await frame('phone-02').evaluate(()=>window.__qaStillMounted),true);
  await card('phone-01').locator('.device-controls').getByRole('button',{name:'Tiếp tục Điện thoại 01',exact:true}).click();
  await ready('phone-01');await page.waitForFunction(()=>document.querySelector('[data-device-id="phone-01"] .device-session').classList.contains('authenticated'));
  passed('pausing and resuming one phone retains its own login without reloading peers');
  await page.locator('#select-all').uncheck();await card('phone-01').locator('input[type=checkbox]').check();
  await page.locator('[data-command=appointments]').click();
  await page.waitForFunction(()=>[...document.querySelectorAll('iframe')].find(el=>el.closest('.device-card').dataset.deviceId==='phone-01').contentWindow.location.hash.includes('tab=1'));
  assert.ok(!frame('phone-02').url().includes('tab=1'));
  passed('group controls affect selected phones only');
  await card('phone-01').getByRole('button',{name:'Đăng xuất riêng máy này',exact:true}).click();await page.locator('#confirm-accept').click();
  await page.waitForFunction(()=>!document.querySelector('[data-device-id="phone-01"] .device-session').classList.contains('authenticated'));
  assert.equal(await card('phone-02').locator('.authenticated').count(),1);assert.ok(tokens.has('qa-token-beta'));assert.ok(!tokens.has('qa-token-alpha'));
  passed('logout revokes only the chosen phone session');
  await page.waitForTimeout(600);
  await page.screenshot({path:'.runtime/fleet-desktop.png',fullPage:true});
  await page.reload({waitUntil:'networkidle'});await ready('phone-02');
  await page.waitForFunction(()=>document.querySelector('[data-device-id="phone-02"] .device-session').classList.contains('authenticated'));
  assert.equal(await page.locator('.device-card').count(),4);assert.equal(await card('phone-01').locator('.authenticated').count(),0);
  assert.equal(await page.locator('#card-scale').inputValue(),'75');
  passed('workspace reload restores device layout and the remaining account independently');
  await card('phone-03').getByRole('button',{name:'Gỡ Điện thoại 03',exact:true}).click();await page.locator('#confirm-accept').click();
  assert.equal(await page.locator('.device-card').count(),3);
  const afterRemove=await page.evaluate(()=>Object.keys(localStorage));assert.ok(!afterRemove.some(key=>key.startsWith(`careflow.${scope}.phone-03.`)));assert.ok(afterRemove.includes(`careflow.${scope}.phone-02.secure.jwt_token`));
  passed('removing a phone clears its local data while preserving another account');
  await page.locator('#show-activity').click();await page.locator('#activity-filters').getByRole('button',{name:'Nhật ký Điện thoại 02',exact:true}).click();
  assert.equal(await page.locator('#activity .request').count(),1);assert.match(await page.locator('#activity').innerText(),/GET \/api\/patients/);await page.locator('#close-activity').click();
  passed('API inspector filters activity by device');
  await page.setViewportSize({width:390,height:900});await card('phone-01').getByRole('button',{name:'Phóng to Điện thoại 01',exact:true}).click();
  await page.waitForTimeout(500);assert.ok(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));
  assert.equal(await page.locator('.device-card:visible').count(),1);
  await page.screenshot({path:'.runtime/fleet-mobile.png',fullPage:true});
  passed('focused phone fits a 390px browser without horizontal page overflow');
  assert.deepEqual(errors,[]);passed('browser reports no uncaught JavaScript errors');
  await mkdir('.runtime',{recursive:true});await writeFile('.runtime/fleet-browser-results.json',JSON.stringify({fixtureBackend:true,realDatabaseWrites:false,cases,errors},null,2));
}catch(error){
  await page.screenshot({path:'.runtime/fleet-browser-failure.png',fullPage:true});
  const screens=await Promise.all(page.frames().filter(item=>item.url().includes('/mobile/')).map(async item=>({url:item.url(),text:await item.locator('body').innerText()})));
  await writeFile('.runtime/fleet-browser-failure.json',JSON.stringify({message:error.message,cases,errors,requests,screens},null,2));
  throw error;
}finally{await browser.close();}
