/* Browser-only adapters. Tokens remain inside this iframe and never enter logs. */
(() => {
  'use strict';
  let token = '', socket, reconnect, heartbeat, callback = () => {}, control = () => {}, generation = 0;
  const reportSession = authenticated => window.parent.postMessage({type: 'careflow:session', authenticated}, location.origin);
  const report = state => window.parent.postMessage({type: 'careflow:socket', state}, location.origin);
  const stop = () => {
    generation++;
    clearTimeout(reconnect); clearInterval(heartbeat);
    if (socket) { socket.onclose = null; socket.close(); socket = null; }
  };
  function connect() {
    if (!token || socket) return;
    const current = generation;
    let buffer = '';
    socket = new WebSocket(`${location.protocol === 'https:' ? 'wss' : 'ws'}://${location.host}/ws/notifications`, ['v12.stomp']);
    report('connecting');
    socket.onopen = async () => {
      try {
        const proof = await window.careflowProof({method:'GET',path:'/ws/notifications',token});
        if (current !== generation || socket?.readyState !== WebSocket.OPEN) return;
        socket.send(`CONNECT\naccept-version:1.2\nheart-beat:10000,10000\nAuthorization:Bearer ${token}\nDPoP:${proof}\n\n\0`);
      } catch { report('error'); socket?.close(); }
    };
    socket.onmessage = event => {
      if (typeof event.data !== 'string') return;
      buffer += event.data;
      let end;
      while ((end = buffer.indexOf('\0')) >= 0) {
        const frame = buffer.slice(0, end).replace(/^\s+/, '');
        buffer = buffer.slice(end + 1);
        if (frame.startsWith('CONNECTED')) {
          socket.send('SUBSCRIBE\nid:careflow-inbox\ndestination:/user/queue/notifications\nack:auto\n\n\0');
          clearInterval(heartbeat);
          heartbeat = setInterval(() => { if (socket?.readyState === WebSocket.OPEN) socket.send('\n'); }, 10000);
          report('connected');
        } else if (frame.startsWith('MESSAGE')) { callback(); }
        else if (frame.startsWith('ERROR')) { report('error'); socket.close(); }
      }
    };
    socket.onerror = () => report('error');
    socket.onclose = () => {
      clearInterval(heartbeat); socket = null;
      report(token ? 'reconnecting' : 'disconnected');
      if (token && generation === current) reconnect = setTimeout(connect, 10000);
    };
  }
  async function files(imageOnly) {
    return new Promise((resolve, reject) => {
      const input = document.createElement('input');
      input.type = 'file'; input.multiple = !imageOnly;
      input.accept = imageOnly ? 'image/jpeg,image/png' : '.png,.jpg,.jpeg,.doc,.docx,.pdf';
      input.oncancel = () => { input.remove(); resolve([]); };
      input.onchange = async () => {
        try {
          const chosen = Array.from(input.files ?? []);
          if (chosen.reduce((n, f) => n + f.size, 0) > 20 * 1024 * 1024) throw new Error('Tổng tệp tối đa 20 MB.');
          const result = await Promise.all(chosen.map(async file => {
            const bytes = new Uint8Array(await file.arrayBuffer());
            let binary = '';
            for (let i = 0; i < bytes.length; i += 8192) binary += String.fromCharCode(...bytes.subarray(i, i + 8192));
            return {name: file.name, base64: btoa(binary)};
          }));
          resolve(result);
        } catch (error) { reject(error); }
        finally { input.remove(); }
      };
      input.style.display = 'none'; document.body.append(input); input.click();
    });
  }
  window.careflowListenNotifications = fn => { callback = fn; };
  window.careflowListenControls = fn => { control = fn; };
  window.addEventListener('message', event => {
    if (event.origin !== location.origin || event.source !== window.parent || event.data?.type !== 'careflow:control') return;
    if (['home','appointments','notifications','back','logout'].includes(event.data.command)) control(event.data.command);
  });
  window.careflowBrowser = async (method, payload) => {
    try {
      const data = JSON.parse(payload);
      let result = null;
      if (method === 'session') {
        const next = data.token || '';
        if (next !== token) { stop(); token = next; reportSession(Boolean(token)); if (token) connect(); else report('disconnected'); }
      } else if (method === 'dpop') result = await window.careflowProof(data);
      else if (method === 'passkeyCreate' || method === 'passkeyGet') {
        if (!window.isSecureContext || !window.PublicKeyCredential) throw new Error('Trình duyệt này không hỗ trợ passkey. Hãy dùng localhost/HTTPS và trình duyệt mới.');
        const options = typeof data.options === 'string' ? JSON.parse(data.options) : data.options;
        const decode = value => Uint8Array.from(atob(value.replace(/-/g, '+').replace(/_/g, '/').padEnd(Math.ceil(value.length / 4) * 4, '=')), c => c.charCodeAt(0));
        const encode = value => btoa(String.fromCharCode(...new Uint8Array(value))).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
        const pk = options.publicKey;
        pk.challenge = decode(pk.challenge);
        if (pk.user) pk.user.id = decode(pk.user.id);
        for (const item of [...(pk.allowCredentials || []), ...(pk.excludeCredentials || [])]) item.id = decode(item.id);
        let credential;
        try { credential = method === 'passkeyCreate' ? await navigator.credentials.create(options) : await navigator.credentials.get(options); }
        catch (error) {
          if (error.name === 'NotAllowedError') throw new Error('Chưa xác nhận passkey, đã hủy hoặc thiết bị không có khóa phù hợp.');
          if (error.name === 'InvalidStateError') throw new Error('Passkey này đã được thiết lập cho tài khoản.');
          throw error;
        }
        if (!credential) throw new Error('Không nhận được passkey.');
        const r = credential.response;
        result = {id: credential.id, rawId: encode(credential.rawId), type: credential.type,
          clientExtensionResults: credential.getClientExtensionResults(),
          response: {clientDataJSON: encode(r.clientDataJSON), ...(r.attestationObject ? {attestationObject: encode(r.attestationObject), transports: r.getTransports?.() || []} :
            {authenticatorData: encode(r.authenticatorData), signature: encode(r.signature), userHandle: r.userHandle ? encode(r.userHandle) : null})}};
      }
      else if (method === 'download') {
        const bytes = Uint8Array.from(atob(data.base64), c => c.charCodeAt(0));
        const url = URL.createObjectURL(new Blob([bytes], {type: data.contentType || 'application/octet-stream'}));
        const link = document.createElement('a'); link.href = url; link.download = data.name || 'tai-lieu';
        document.body.append(link); link.click(); link.remove();
        setTimeout(() => URL.revokeObjectURL(url), 30000);
      }
      else if (method === 'files') result = await files(data.imageOnly);
      else if (method === 'position') {
        result = await new Promise((resolve, reject) => {
          if (!navigator.geolocation) return reject(new Error('Trình duyệt không hỗ trợ định vị.'));
          navigator.geolocation.getCurrentPosition(p => resolve({latitude: p.coords.latitude, longitude: p.coords.longitude, accuracy: p.coords.accuracy}),
            () => reject(new Error('Không lấy được vị trí. Cho phép định vị hoặc nhập tọa độ kiểm thử.')),
            {enableHighAccuracy: true, timeout: 15000, maximumAge: 0});
        });
      } else throw new Error('Thao tác trình duyệt không được hỗ trợ.');
      return JSON.stringify({data: result});
    } catch (error) { return JSON.stringify({error: error.message || String(error)}); }
  };
  window.addEventListener('pagehide', stop);
  window.addEventListener('flutter-first-frame', () => {
    document.getElementById('loading')?.remove();
    window.parent.postMessage({type: 'careflow:ready'}, location.origin);
  });
})();
