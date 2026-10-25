export const FLEET_KEY = 'careflow.fleet.v1';
export const MAX_DEVICES = 8;
export const DEVICE_WIDTH = 390;
export const DEVICE_HEIGHT = 780;
export function validDeviceId(value) { return typeof value === 'string' && /^[a-z0-9-]{1,64}$/.test(value); }
export function defaultFleet() {
  return {version: 1, zoom: 0.75, layout: 'auto', devices: [1,2,3].map(number => ({id:`phone-0${number}`,name:`Điện thoại 0${number}`,mode:'real',paused:false}))};
}
export function restoreFleet(raw) {
  try {
    const data = JSON.parse(raw);
    if (data.version !== 1 || !Array.isArray(data.devices) || data.devices.length < 1 || data.devices.length > MAX_DEVICES) return defaultFleet();
    const seen = new Set();
    const devices = data.devices.map(item => {
      if (!validDeviceId(item.id) || seen.has(item.id)) throw new Error('Invalid device ID');
      seen.add(item.id);
      return {id:item.id,name:typeof item.name === 'string' && item.name.trim() ? item.name.trim().slice(0,32) : 'Điện thoại',mode:'real',paused:item.paused === true,...(['new-patient','booked','cancelled'].includes(item.scenarioId) ? {scenarioId:item.scenarioId} : {})};
    });
    const zoom = typeof data.zoom === 'number' && data.zoom >= 0.5 && data.zoom <= 1 ? data.zoom : 0.75;
    return {version:1,zoom,layout:['auto',2,3].includes(data.layout) ? data.layout : 'auto',devices};
  } catch { return defaultFleet(); }
}
export function deviceNamespace(scope, id) {
  if (!/^[a-f0-9]{16}$/.test(scope) || !validDeviceId(id)) throw new Error('Thiết bị hoặc môi trường không hợp lệ.');
  return `careflow.${scope}.${id}`;
}
export function deviceUrl(device, scope) {
  deviceNamespace(scope, device.id);
  return `/mobile/?${new URLSearchParams({device:device.id,scope,demo:'false'})}#/splash`;
}
export function deviceStorageKeys(keys, id) {
  if (!validDeviceId(id)) return [];
  return [...keys].filter(key => /^careflow\.[a-f0-9]{16}\./.test(key) && key.split('.')[2] === id);
}
