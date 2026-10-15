import {FLEET_KEY, MAX_DEVICES, DEVICE_WIDTH, DEVICE_HEIGHT, restoreFleet, deviceUrl, deviceStorageKeys} from './fleet-state.js';
const $ = id => document.getElementById(id);
let fleet = restoreFleet(localStorage.getItem(FLEET_KEY));
let config, dataset, focusId = null, filterId = 'all', healthBusy = false, activityBusy = false, gatewayBusy = false;
let activityItems = [], pendingConfirmation;
const views = new Map(), selected = new Set(fleet.devices.map(device => device.id));
const cardSizer = new ResizeObserver(entries => {
  for (const {target} of entries) target.parentElement.style.setProperty('--card-height', `${target.offsetHeight}px`);
});
const socketLabels = {connecting:'Realtime đang kết nối',connected:'Realtime đã kết nối',error:'Realtime chưa khả dụng',reconnecting:'Realtime đang kết nối lại',disconnected:'Realtime chưa kết nối'};
function save() { localStorage.setItem(FLEET_KEY, JSON.stringify(fleet)); }
function note(message) { $('workspace-note').textContent = message; }
function deviceById(id) { return fleet.devices.find(device => device.id === id); }
async function loadDataset() {
  try {
    const response = await fetch('/__emulator/dataset'); const result = await response.json();
    if (!response.ok) throw new Error(result.message);
    dataset = result;
    $('dataset-note').textContent = '3 ca mẫu đã lưu trong DB audit riêng. Các thao tác trên máy sẽ thay đổi dữ liệu thật.';
  } catch (error) { dataset = null; $('dataset-note').textContent = error.message; }
  for (const view of views.values()) renderScenario(view);
}
function renderScenario(view) {
  const panel = view.scenario;
  const summary=document.createElement('summary');summary.textContent='Tài khoản kiểm thử';panel.replaceChildren(summary);
  panel.hidden=!dataset;
  if (!dataset) return;
  const id = view.device.scenarioId || dataset.scenarios.find(item=>item.deviceId===view.device.id)?.id;
  const scenario = dataset.scenarios.find(item=>item.id===id);
  const choices = document.createElement('div'); choices.className = 'scenario-choices'; choices.setAttribute('aria-label','Chọn ca mẫu');
  for (const item of dataset.scenarios) choices.append(button(item.title,`Xem tài khoản: ${item.title}`,()=>{
    view.device.scenarioId=item.id; save(); renderScenario(view);
  },item.id===id ? 'selected' : ''));
  panel.append(choices);
  if (!scenario) { panel.append('Chọn ca để xem tài khoản, sau đó đăng nhập trên máy này.'); return; }
  const description = document.createElement('p'); description.textContent=scenario.description;
  const credentials = document.createElement('div'); credentials.className='scenario-credentials';
  for (const [label,value] of [['Tài khoản',scenario.username],['Mật khẩu',scenario.password]]) {
    const row=document.createElement('div'); const name=document.createElement('span'); name.textContent=`${label}: `;
    const code=document.createElement('code'); code.textContent=value; row.append(name,code); credentials.append(row);
  }
  panel.append(description,credentials);
  if (scenario.appointment) { const date=document.createElement('p'); date.textContent=`Lịch đã tạo: ${scenario.appointment.date} · ${scenario.appointment.timeSlot}. Xem trạng thái hiện tại trong ứng dụng.`; panel.append(date); }
}
function button(text, title, handler, className = '') {
  const element = document.createElement('button'); element.type = 'button'; element.textContent = text; element.title = title; element.setAttribute('aria-label',title); element.className = className; element.onclick = handler; return element;
}
function stateLabel(view) { return view.device.paused ? 'Đã tạm dừng' : !view.ready ? 'Đang mở ứng dụng…' : view.authenticated ? 'Đã đăng nhập' : 'Chưa đăng nhập'; }
function updateView(view) {
  const device = view.device;
  view.name.value = device.name;
  view.card.setAttribute('aria-label', device.name);
  view.frame.title = `CareFlow · ${device.name}`;
  view.checkbox.checked = selected.has(device.id);
  view.card.classList.toggle('is-selected', selected.has(device.id));
  view.checkbox.setAttribute('aria-label', `Chọn ${device.name}`);
  view.remove.setAttribute('aria-label', `Gỡ ${device.name}`);
  view.card.dataset.status = device.paused ? 'paused' : !view.ready ? 'loading' : view.authenticated ? 'authenticated' : 'signed-out';
  view.pause.textContent = device.paused ? 'Tiếp tục' : 'Tạm dừng';
  view.pause.setAttribute('aria-label',`${device.paused ? 'Tiếp tục' : 'Tạm dừng'} ${device.name}`);
  view.status.textContent = stateLabel(view);
  view.status.className = `device-session${view.authenticated && !device.paused ? ' authenticated' : ''}`;
  view.socket.title = socketLabels[view.socketState] || 'Realtime chưa kết nối';
  view.socket.classList.toggle('connected', view.socketState === 'connected' && !device.paused);
  view.card.classList.toggle('paused', device.paused);
  view.overlay.hidden = !device.paused;
  view.frame.style.visibility = device.paused ? 'hidden' : '';
  view.card.hidden = Boolean(focusId && focusId !== device.id);
  view.slot.hidden = view.card.hidden;
  view.focus.textContent = focusId === device.id ? 'Thu nhỏ' : 'Phóng to';
  view.focus.setAttribute('aria-label',focusId === device.id ? 'Thu nhỏ máy' : `Phóng to ${device.name}`);
}
function loadDevice(view) {
  view.ready = false; view.authenticated = false; view.socketState = 'disconnected';
  updateView(view);
  if (!config?.built || view.device.paused) { view.frame.src = 'about:blank'; return; }
  view.frame.src = deviceUrl(view.device, config.storageScope);
}
function clearDeviceStorage(id) {
  for (const key of deviceStorageKeys(Object.keys(localStorage),id)) localStorage.removeItem(key);
}
function confirmAction(title, description, action, accept = 'Gỡ máy') {
  pendingConfirmation = action; $('confirm-title').textContent = title; $('confirm-description').textContent = description; $('confirm-accept').textContent = accept; $('confirm-dialog').showModal();
}
$('confirm-cancel').onclick = () => { pendingConfirmation = null; $('confirm-dialog').close(); };
$('confirm-dialog').addEventListener('cancel', () => { pendingConfirmation = null; });
$('confirm-accept').onclick = () => { const action = pendingConfirmation; pendingConfirmation = null; $('confirm-dialog').close(); action?.(); };
function removeDevice(id) {
  if (fleet.devices.length === 1) { note('Giữ lại ít nhất một điện thoại trong workspace.'); return; }
  const device = deviceById(id);
  confirmAction(`Gỡ ${device.name}?`, 'Máy sẽ đóng và xóa phiên, dữ liệu cục bộ của riêng máy này. Hồ sơ và lịch khám trên backend được giữ nguyên.', () => {
    const view = views.get(id); view.frame.src = 'about:blank'; cardSizer.unobserve(view.card); view.slot.remove(); views.delete(id);
    clearDeviceStorage(id); selected.delete(id); fleet.devices = fleet.devices.filter(item => item.id !== id);
    if (focusId === id) focusId = null;
    if (filterId === id) filterId = 'all';
    save(); updateWorkspace(); note(`Đã gỡ ${device.name}.`);
  });
}
function togglePause(view) {
  view.device.paused = !view.device.paused;
  loadDevice(view); save(); updateWorkspace();
  note(view.device.paused ? `${view.device.name} đã tạm dừng; phiên được lưu riêng để tiếp tục sau.` : `Đang mở lại ${view.device.name}.`);
}
function focusDevice(id) { focusId = focusId === id ? null : id; updateWorkspace(); }
function sendControl(id, command) {
  const view = views.get(id);
  if (!view || view.device.paused || !view.ready) return false;
  view.frame.contentWindow.postMessage({type:'careflow:control',command}, location.origin); return true;
}
function makeDevice(device) {
  const slot = document.createElement('div'); slot.className = 'device-slot';
  const card = document.createElement('article'); card.className = 'device-card'; card.dataset.deviceId = device.id;
  const header = document.createElement('div'); header.className = 'device-card-header';
  const checkbox = document.createElement('input'); checkbox.type = 'checkbox'; checkbox.checked = selected.has(device.id); checkbox.setAttribute('aria-label',`Chọn ${device.name}`);
  checkbox.onchange = () => { if (checkbox.checked) selected.add(device.id); else selected.delete(device.id); updateWorkspace(); };
  const name = document.createElement('input'); name.className = 'device-name'; name.value = device.name; name.maxLength = 32; name.setAttribute('aria-label',`Tên máy ${device.id}`);
  name.onchange = () => { device.name = name.value.trim() || 'Điện thoại'; save(); updateWorkspace(); };
  name.onkeydown = event => { if (event.key === 'Enter') name.blur(); };
  const focus = button('Phóng to',`Phóng to ${device.name}`,() => focusDevice(device.id),'icon-button');
  const remove = button('Gỡ',`Gỡ ${device.name}`,() => removeDevice(device.id),'icon-button remove-device');
  header.append(checkbox,name,focus,remove);
  const meta = document.createElement('div'); meta.className = 'device-meta';
  const status = document.createElement('span'); status.className = 'device-session';
  const socket = document.createElement('span'); socket.className = 'socket-dot'; socket.setAttribute('aria-label','Trạng thái realtime');
  meta.append(socket,status);
  const viewport = document.createElement('div'); viewport.className = 'device-viewport';
  const phone = document.createElement('div'); phone.className = 'phone';
  const bar = document.createElement('div'); bar.className = 'device-status';
  const time = document.createElement('span'); time.className = 'device-time';
  const island = document.createElement('span'); island.className = 'device-island';
  const battery = document.createElement('span'); battery.textContent = '▥ ◔ ▰'; battery.setAttribute('aria-hidden','true');
  bar.append(time,island,battery);
  const frame = document.createElement('iframe'); frame.width=String(DEVICE_WIDTH); frame.height=String(DEVICE_HEIGHT); frame.src = 'about:blank'; frame.allow = 'geolocation; camera; clipboard-read; clipboard-write';
  const indicator = document.createElement('div'); indicator.className = 'home-indicator';
  phone.append(bar,frame,indicator); viewport.append(phone);
  const overlay = document.createElement('div'); overlay.className = 'paused-overlay'; overlay.hidden = true;
  const pauseIcon = document.createElement('span'); pauseIcon.textContent = 'Ⅱ';
  const pauseText = document.createElement('strong'); pauseText.textContent = 'Máy đang tạm dừng';
  const resume = button('▶ Tiếp tục','Tiếp tục máy',() => togglePause(views.get(device.id)),'primary'); overlay.append(pauseIcon,pauseText,resume); viewport.append(overlay);
  const controls = document.createElement('div'); controls.className = 'device-controls';
  const back = button('← Quay lại','Quay lại trên máy này',() => sendControl(device.id,'back'));
  const home = button('Trang chủ','Trang chủ trên máy này',() => sendControl(device.id,'home'));
  const reload = button('Tải lại','Tải lại riêng máy này',() => loadDevice(views.get(device.id)));
  const pause = button('Tạm dừng',`Tạm dừng ${device.name}`,() => togglePause(views.get(device.id)));
  const logout = button('Đăng xuất','Đăng xuất riêng máy này',() => {
    if (!views.get(device.id).authenticated) { note(`${device.name} chưa đăng nhập.`); return; }
    confirmAction(`Đăng xuất ${device.name}?`,'Chỉ phiên của điện thoại này sẽ đăng xuất.',() => sendControl(device.id,'logout'),'Đăng xuất');
  });
  const scenario=document.createElement('details'); scenario.className='device-scenario'; scenario.setAttribute('aria-label','Tài khoản và ca mẫu');
  controls.append(back,home,reload,pause,logout); card.append(header,meta,controls,viewport,scenario);
  const view = {device,slot,card,checkbox,name,focus,remove,frame,status,socket,pause,overlay,time,scenario,ready:false,authenticated:false,socketState:'disconnected'};
  views.set(device.id,view); slot.append(card); $('device-grid').append(slot); cardSizer.observe(card); renderScenario(view); loadDevice(view); return view;
}
function applyZoom() {
  const grid=$('device-grid'), width=grid.clientWidth, cardWidth=DEVICE_WIDTH+16+34, gap=16;
  const scale=Math.min(focusId?1:fleet.zoom,width/cardWidth);
  const maxColumns=Math.max(1,Math.floor((width+gap)/(cardWidth*scale+gap)));
  const columns=focusId?1:Math.min(fleet.layout==='auto'?maxColumns:fleet.layout,maxColumns,fleet.devices.length);
  grid.style.setProperty('--columns',String(columns));
  grid.style.setProperty('--zoom',String(scale));
  $('card-scale').value=String(Math.round(fleet.zoom*100));
  $('card-scale-value').value=`${Math.round(fleet.zoom*100)}%`;
  for(const option of document.querySelectorAll('[data-layout]')) {
    const value=option.dataset.layout==='auto'?'auto':Number(option.dataset.layout),active=value===fleet.layout;
    option.classList.toggle('selected',active);option.setAttribute('aria-pressed',String(active));
    option.disabled=value!=='auto' && value>maxColumns;
  }
}
function updateWorkspace() {
  const count = fleet.devices.length;
  $('device-count').textContent = `${count} / ${MAX_DEVICES} máy`;
  $('running-count').textContent = `${fleet.devices.filter(device => !device.paused).length} máy đang mở`;
  $('selection-count').textContent = `Đã chọn ${selected.size} máy`;
  $('select-all').checked = selected.size === count; $('select-all').indeterminate = selected.size > 0 && selected.size < count;
  $('add-device').disabled = count >= MAX_DEVICES || gatewayBusy || !config?.built;
  $('exit-focus').hidden = !focusId;
  $('device-grid').classList.toggle('focused',Boolean(focusId));
  for (const control of document.querySelectorAll('.batch-actions button')) control.disabled = selected.size === 0 || gatewayBusy;
  for (const view of views.values()) updateView(view);
  applyZoom(); renderFilters(); renderActivity(); clock();
}
function renderFilters() {
  const filters = [button('Tất cả máy','Nhật ký tất cả máy',() => {filterId='all';renderFilters();renderActivity();},filterId === 'all' ? 'selected' : '')];
  for (const device of fleet.devices) filters.push(button(device.name,`Nhật ký ${device.name}`,() => {filterId=device.id;renderFilters();renderActivity();},filterId === device.id ? 'selected' : ''));
  $('activity-filters').replaceChildren(...filters);
}
function renderActivity() {
  const items = filterId === 'all' ? activityItems : activityItems.filter(item => item.device === filterId);
  if (!items.length) {
    const empty = document.createElement('div'); empty.className = 'empty';
    const icon = document.createElement('span'); icon.textContent = '⇄'; const label = document.createElement('strong'); label.textContent = 'Chưa có request cho lựa chọn này';
    const help = document.createElement('p'); help.textContent = 'Thao tác trên điện thoại để quan sát API.'; empty.append(icon,label,help); $('activity').replaceChildren(empty); return;
  }
  $('activity').replaceChildren(...items.map(item => {
    const row = document.createElement('div'); row.className = 'request';
    const main = document.createElement('div'); main.className = 'request-main';
    const name = document.createElement('span'); name.className = 'request-device'; name.textContent = deviceById(item.device)?.name || (item.device ? 'Máy đã gỡ' : 'Kết nối khác');
    const status = document.createElement('span'); status.className = `request-status${item.status >= 400 ? ' error' : ''}`; status.textContent = item.status; main.append(name,status);
    const path = document.createElement('div'); path.className = 'request-path'; path.textContent = `${item.method} ${item.path}`;
    const time = document.createElement('div'); time.className = 'request-time'; time.textContent = `${new Date(item.time).toLocaleTimeString('vi-VN')} · ${item.ms} ms`; row.append(main,path,time); return row;
  }));
}
async function activity() {
  if (activityBusy || !$('activity-dialog').open) return;
  activityBusy = true;
  try { activityItems = await (await fetch('/__emulator/activity')).json(); renderActivity(); }
  catch { /* Gateway panel exposes connection errors. */ }
  finally { activityBusy = false; }
}
async function health() {
  if (healthBusy) return; healthBusy = true;
  $('health-badge').textContent = 'Đang kiểm tra Gateway'; $('health-badge').className = 'badge muted';
  try {
    const result = await (await fetch('/__emulator/health')).json();
    const healthy = result.reachable && result.status >= 200 && result.status < 300;
    $('health-badge').textContent = healthy ? 'Gateway đã kết nối' : result.reachable ? `HTTP ${result.status}` : 'Chưa kết nối';
    $('health-badge').className = `badge ${healthy ? 'good' : 'bad'}`;
    $('connection-note').textContent = healthy ? `Gateway phản hồi ${result.ms} ms. Từng dịch vụ được kiểm tra khi bạn sử dụng chức năng tương ứng.` : result.message || `Gateway phản hồi HTTP ${result.status}. Kiểm tra backend.`;
  } catch { $('health-badge').textContent='Lỗi kết nối'; $('health-badge').className='badge bad'; $('connection-note').textContent='Không kết nối được máy chủ giả lập. Kiểm tra npm start.'; }
  finally { healthBusy=false; }
}
$('gateway-form').onsubmit = async event => {
  event.preventDefault(); if (gatewayBusy) return;
  gatewayBusy=true; const submit=event.submitter; if(submit)submit.disabled=true;
  try {
    const response = await fetch('/__emulator/config',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({gateway:$('gateway').value.trim()})});
    const result = await response.json(); if(!response.ok)throw new Error(result.message);
    config = await (await fetch('/__emulator/config')).json(); $('gateway').value=config.gateway;
    await loadDataset();
    // Stop every old iframe before loading sessions in the new Gateway scope.
    for(const view of views.values())view.frame.src='about:blank';
    for(const view of views.values())loadDevice(view);
    activityItems=[]; renderActivity(); await health();
    note('Các máy đã kết nối Gateway được chọn. Phiên được lưu riêng theo từng máy và môi trường.');
    $('connection-dialog').close();
  } catch(error){$('connection-note').textContent=error.message;}
  finally{gatewayBusy=false;if(submit)submit.disabled=false;updateWorkspace();}
};
$('check-health').onclick=health;
$('show-connection').onclick=()=>$('connection-dialog').showModal();
$('close-connection').onclick=()=>$('connection-dialog').close();
$('show-help').onclick=()=>$('help-dialog').showModal();
$('close-help').onclick=()=>$('help-dialog').close();
$('add-device').onclick=()=>{
  if(fleet.devices.length>=MAX_DEVICES)return;
  let number=1;while(fleet.devices.some(item=>item.name===`Điện thoại ${String(number).padStart(2,'0')}`))number++;
  const device={id:`phone-${crypto.randomUUID()}`,name:`Điện thoại ${String(number).padStart(2,'0')}`,mode:'real',paused:false};
  fleet.devices.push(device);selected.add(device.id);focusId=null;makeDevice(device);save();updateWorkspace();note(`Đã thêm ${device.name} với phiên đăng nhập mới.`);
};
$('card-scale').oninput=()=>{fleet.zoom=Number($('card-scale').value)/100;focusId=null;save();updateWorkspace();};
for(const option of document.querySelectorAll('[data-layout]'))option.onclick=()=>{fleet.layout=option.dataset.layout==='auto' ? 'auto' : Number(option.dataset.layout);save();applyZoom();};
$('select-all').onchange=()=>{selected.clear();if($('select-all').checked)for(const device of fleet.devices)selected.add(device.id);updateWorkspace();};
for(const control of document.querySelectorAll('[data-command]'))control.onclick=()=>{
  let applied=0;for(const id of selected)if(sendControl(id,control.dataset.command))applied++;
  note(`Đã gửi thao tác đến ${applied} máy. Máy tạm dừng hoặc chưa mở xong không nhận thao tác.`);
};
$('reload-selected').onclick=()=>{let applied=0;for(const id of selected){const view=views.get(id);if(!view.device.paused){loadDevice(view);applied++;}}note(`Đang tải lại ${applied} máy; phiên đăng nhập của từng máy được giữ riêng.`);};
$('exit-focus').onclick=()=>{focusId=null;updateWorkspace();};
$('show-activity').onclick=()=>{$('activity-dialog').showModal();activity();};
$('close-activity').onclick=()=>$('activity-dialog').close();
$('show-connection').onclick=()=>$('connection-dialog').showModal();
$('close-connection').onclick=()=>$('connection-dialog').close();
$('show-help').onclick=()=>$('help-dialog').showModal();
$('close-help').onclick=()=>$('help-dialog').close();
window.addEventListener('message',event=>{
  if(event.origin!==location.origin || !event.data || typeof event.data.type!=='string')return;
  const view=[...views.values()].find(item=>item.frame.contentWindow===event.source);
  if(!view || view.device.paused)return;
  if(event.data.type==='careflow:socket')view.socketState=Object.hasOwn(socketLabels,event.data.state)?event.data.state:'disconnected';
  else if(event.data.type==='careflow:session')view.authenticated=event.data.authenticated===true;
  else if(event.data.type==='careflow:ready')view.ready=true;
  else return;
  updateView(view);
});
function clock(){const value=new Date().toLocaleTimeString('vi-VN',{hour:'2-digit',minute:'2-digit'});for(const view of views.values())view.time.textContent=value;}
try{
  const response=await fetch('/__emulator/config');if(!response.ok)throw new Error('Không đọc được cấu hình.');config=await response.json();
  $('gateway').value=config.gateway;$('build-note').hidden=config.built;
  await loadDataset();
  for(const device of fleet.devices)makeDevice(device);
  updateWorkspace();save();health();
}catch(error){$('connection-note').textContent=`${error.message} Kiểm tra npm start rồi tải lại trang.`;}
new ResizeObserver(applyZoom).observe($('device-grid'));
window.addEventListener('resize',applyZoom);
setInterval(clock,30000);setInterval(activity,2000);setInterval(health,30000);
