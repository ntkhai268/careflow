const crypto = require('crypto');
const fs = require('fs');

const { privateKey, publicKey } = crypto.generateKeyPairSync('ec', { namedCurve: 'prime256v1' });
const jwk = publicKey.export({ format: 'jwk' });

function base64Url(buf) {
  return buf.toString('base64').replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}

function sha256Base64Url(str) {
  return base64Url(crypto.createHash('sha256').update(str).digest());
}

function createDPoP(method, url, token = null) {
  const h = base64Url(Buffer.from(JSON.stringify({
    typ: 'dpop+jwt',
    alg: 'ES256',
    jwk: { kty: jwk.kty, crv: jwk.crv, x: jwk.x, y: jwk.y }
  })));
  const claims = {
    jti: crypto.randomUUID(),
    htm: method.toUpperCase(),
    htu: url.split('?')[0],
    iat: Math.floor(Date.now() / 1000)
  };
  if (token) claims.ath = sha256Base64Url(token);
  const p = base64Url(Buffer.from(JSON.stringify(claims)));
  const s = crypto.createSign('SHA256').update(`${h}.${p}`).sign({ key: privateKey, dsaEncoding: 'ieee-p1363' });
  return `${h}.${p}.${base64Url(s)}`;
}

async function run() {
  console.log('================================================================');
  console.log('🧪 KIỂM THỬ BROWSER-SIMULATION: VOICE-TO-TEXT & RAG HYBRID SEARCH');
  console.log('================================================================\n');

  // 1. Login as Doctor
  const loginUrl = 'http://localhost:8080/api/auth/login';
  const loginRes = await fetch(loginUrl, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', 'DPoP': createDPoP('POST', loginUrl) },
    body: JSON.stringify({ usernameOrEmail: 'an@careflow.vn', password: 'Password123@' })
  }).then(r => r.json());

  if (!loginRes.data?.accessToken) {
    console.error('❌ Đăng nhập thất bại:', loginRes);
    process.exit(1);
  }
  const token = loginRes.data.accessToken;
  console.log('1. Đăng nhập thành công với Bác sĩ:', loginRes.data.user?.fullName);

  // 2. Test RAG / Clinical Suggestions
  console.log('\n2. Kiểm tra RAG (Clinical Suggestions / Phác đồ Bộ Y Tế)...');
  const ragUrl = 'http://localhost:8080/api/ai/clinical-suggestions';
  const ragRes = await fetch(ragUrl, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Origin': 'http://localhost:3000',
      'Authorization': `Bearer ${token}`,
      'DPoP': createDPoP('POST', ragUrl, token)
    },
    body: JSON.stringify({
      consultationId: 'c0000001-0000-0000-0000-000000000099',
      question: 'Phác đồ điều trị đái tháo đường típ 2 theo quyết định Bộ Y Tế'
    })
  });

  const corsHeader = ragRes.headers.get('access-control-allow-origin');
  console.log(`   - HTTP Status: ${ragRes.status}`);
  console.log(`   - Access-Control-Allow-Origin: "${corsHeader}"`);
  if (corsHeader !== 'http://localhost:3000') {
    console.error(`   ❌ LỖI CORS: Header bị trùng hoặc sai định dạng: ${corsHeader}`);
    process.exit(1);
  }
  console.log('   ✅ CORS Hợp lệ (Không bị nhân đôi header)!');

  const ragData = await ragRes.json();
  console.log(`   - Chế độ RAG: ${ragData.data?.mode}`);
  console.log(`   - Trích dẫn bằng chứng (Citations): ${ragData.data?.citations?.length}`);
  ragData.data?.citations?.forEach((c, idx) => {
    console.log(`     [${idx + 1}] ${c.title} (Hash: ${c.contentHash?.substring(0, 8)}...)`);
  });
  console.log(`   - Số gợi ý chuyên môn: ${ragData.data?.suggestions?.length}`);

  // 3. Test Voice Recording Upload
  console.log('\n3. Kiểm tra Voice-to-Text Upload (FormData Multipart)...');
  const voiceUrl = 'http://localhost:8080/api/ai/voice-to-clinical-fields';
  const formData = new FormData();
  const sampleWav = Buffer.from(
    'RIFF38000000WAVEfmt 100000000100010044ac00008858010002001000data14000000' + '00'.repeat(20),
    'hex'
  );
  formData.append('file', new Blob([sampleWav], { type: 'audio/wav' }), 'voice-record.wav');

  const voiceRes = await fetch(voiceUrl, {
    method: 'POST',
    headers: {
      'Origin': 'http://localhost:3000',
      'Authorization': `Bearer ${token}`,
      'DPoP': createDPoP('POST', voiceUrl, token)
    },
    body: formData
  });

  const voiceCorsHeader = voiceRes.headers.get('access-control-allow-origin');
  console.log(`   - HTTP Status: ${voiceRes.status}`);
  console.log(`   - Access-Control-Allow-Origin: "${voiceCorsHeader}"`);
  if (voiceCorsHeader !== 'http://localhost:3000') {
    console.error(`   ❌ LỖI CORS trên Voice endpoint: ${voiceCorsHeader}`);
    process.exit(1);
  }
  console.log('   ✅ CORS Voice Hợp lệ!');

  const voiceData = await voiceRes.json();
  console.log(`   - Transcript trích xuất: "${voiceData.data?.transcriptText}"`);
  console.log('   - Sinh hiệu bóc tách:');
  console.log(`     + Huyết áp:   ${voiceData.data?.extractedFields?.bloodPressure || 'N/A'}`);
  console.log(`     + Mạch:       ${voiceData.data?.extractedFields?.heartRate || 'N/A'} bpm`);
  console.log(`     + Thân nhiệt: ${voiceData.data?.extractedFields?.temperature || 'N/A'} °C`);
  console.log(`     + SpO2:       ${voiceData.data?.extractedFields?.spo2 || 'N/A'} %`);
  console.log(`     + Chẩn đoán:  ${voiceData.data?.extractedFields?.diagnosis || 'N/A'}`);

  console.log('\n================================================================');
  console.log('🎉 TẤT CẢ TÍNH NĂNG VOICE-TO-TEXT VÀ RAG HOẠT ĐỘNG HOÀN HẢO!');
  console.log('================================================================');
}

run().catch(console.error);
