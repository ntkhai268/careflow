import test from 'node:test';
import assert from 'node:assert/strict';
import {defaultFleet,restoreFleet,deviceNamespace,deviceUrl,deviceStorageKeys} from '../web/fleet-state.js';

test('opens three independent real-API devices by default',()=>{
  const fleet=defaultFleet();assert.equal(fleet.devices.length,3);assert.equal(new Set(fleet.devices.map(item=>item.id)).size,3);assert.ok(fleet.devices.every(item=>item.mode==='real' && !item.paused));
});
test('retains names, scenario, pauses and zoom without trusting invalid layout data',()=>{
  const fleet=defaultFleet();fleet.devices[1].name='Bệnh nhân B';fleet.devices[1].scenarioId='booked';fleet.devices[2].paused=true;fleet.zoom=0.8;fleet.layout=2;
  assert.deepEqual(restoreFleet(JSON.stringify(fleet)),fleet);
  assert.deepEqual(restoreFleet('{broken'),defaultFleet());
  fleet.devices[1].id=fleet.devices[0].id;assert.deepEqual(restoreFleet(JSON.stringify(fleet)),defaultFleet());
});
test('old Demo settings cannot enable mock journeys in a real-data workspace',()=>{
  const fleet=defaultFleet();fleet.devices[0].mode='demo';
  assert.equal(restoreFleet(JSON.stringify(fleet)).devices[0].mode,'real');
  assert.equal(new URL(deviceUrl(fleet.devices[0],'0123456789abcdef'),'http://localhost').searchParams.get('demo'),'false');
});
test('each device and backend produce a separate storage namespace and launch URL',()=>{
  const [first,second]=defaultFleet().devices;
  const scope='0123456789abcdef',other='abcdef0123456789';
  assert.notEqual(deviceNamespace(scope,first.id),deviceNamespace(scope,second.id));assert.notEqual(deviceNamespace(scope,first.id),deviceNamespace(other,first.id));
  const url=new URL(deviceUrl(first,scope),'http://localhost:3000');assert.equal(url.searchParams.get('device'),first.id);assert.equal(url.searchParams.get('scope'),scope);assert.equal(url.searchParams.get('demo'),'false');assert.equal(url.hash,'#/splash');
  assert.throws(()=>deviceNamespace(scope,'bad.id'));
});
test('removing a device deletes its own token and preferences across environments only',()=>{
  const keys=['careflow.0123456789abcdef.phone-01.secure.jwt_token','careflow.0123456789abcdef.phone-01.preferences.patient','careflow.abcdef0123456789.phone-01.secure','careflow.0123456789abcdef.phone-02.secure.jwt_token','FlutterSecureStorage.jwt_token','flutter.legacy','careflow.fleet.v1'];
  assert.deepEqual(deviceStorageKeys(keys,'phone-01'),keys.slice(0,3));assert.deepEqual(deviceStorageKeys(keys,'bad.id'),[]);
});

test('old automatic sizing migrates to compact cards while retaining devices',()=>{
  const legacy=defaultFleet();legacy.zoom='auto';delete legacy.layout;
  const updated=restoreFleet(JSON.stringify(legacy));
  assert.equal(updated.zoom,0.75);assert.equal(updated.layout,'auto');
  assert.deepEqual(updated.devices,legacy.devices);
});
test('card sizing accepts the full 50–100 percent range and rejects invalid values',()=>{
  for(const zoom of [0.5,0.6,0.75,0.83,1])assert.equal(restoreFleet(JSON.stringify({...defaultFleet(),zoom})).zoom,zoom);
  for(const zoom of [0.49,1.01,'0.5',null])assert.equal(restoreFleet(JSON.stringify({...defaultFleet(),zoom})).zoom,0.75);
});
