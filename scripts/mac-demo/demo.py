#!/usr/bin/env python3
"""Launch the existing real Mac demo, preserving healthy processes and private state."""
import argparse
import fcntl
import gzip
import hashlib
import json
import os
from pathlib import Path
import shutil
import ssl
import subprocess
import sys
import time
import urllib.request

ROOT = Path(__file__).resolve().parents[2]
AUDIT = Path('/tmp/careflow-dangkhoii-audit')
SAVED = Path.home() / 'Library/Application Support/CareFlow/mac-demo'
STATE = AUDIT / 'state.json'
NAMES = ['identity-service', 'hospital-directory-service', 'patient-service', 'appointment-service',
         'queue-service', 'consultation-service', 'prescription-service', 'emr-service', 'lab-service',
         'notification-service', 'analytics-service', 'ai-service', 'api-gateway']
PORTS = dict(zip(['api-gateway', 'identity-service', 'patient-service', 'appointment-service',
                  'queue-service', 'notification-service', 'consultation-service', 'prescription-service',
                  'emr-service', 'lab-service', 'hospital-directory-service'], range(18080, 18091)))
PORTS['analytics-service'] = 18091
PORTS['identity-session-secondary'] = 18092
PORTS['ai-service'] = 18093
CONTAINERS = ['careflow-dangkhoii-auth-audit-postgres', 'careflow-dangkhoii-auth-audit-rabbitmq']


def run(args, **kwargs):
    return subprocess.run(args, text=True, capture_output=True, **kwargs)


def private_json(path, value):
    path.parent.mkdir(parents=True, exist_ok=True, mode=0o700)
    temporary = path.with_suffix('.new')
    with open(temporary, 'w', opener=lambda p, flags: os.open(p, flags, 0o600)) as stream:
        json.dump(value, stream)
    temporary.replace(path)
    path.chmod(0o600)


def health(url, context=None):
    try:
        with urllib.request.urlopen(url, timeout=4, context=context) as response:
            payload = response.read()
            return response.status == 200 and (not url.endswith('/__emulator/health') or
                   (json.loads(payload).get('reachable') is True and json.loads(payload).get('status') == 200))
    except Exception:
        return False


def tls_context():
    folder = AUDIT / 'security-hardening/tls'
    context = ssl.create_default_context(cafile=str(folder / 'ca.crt'))
    context.load_cert_chain(str(folder / 'api-gateway.crt'), str(folder / 'api-gateway.key'))
    return context


def validate(state):
    for name, info in state['services'].items():
        short = name.removeprefix('careflow-')
        if short not in PORTS:
            continue
        Path(info['jar']).resolve().relative_to(AUDIT.resolve())
        env = {**state['environment'], **info['environment']}
        settings = json.loads(env['SPRING_APPLICATION_JSON'])
        server = settings['server']
        if server.get('address') != '127.0.0.1' or server.get('port') != PORTS[short] or not server['ssl'].get('enabled'):
            raise RuntimeError('Expected loopback HTTPS configuration: ' + name)
        if short != 'api-gateway':
            urls = [env.get('SPRING_DATASOURCE_URL', ''), settings.get('spring', {}).get('datasource', {}).get('url', '')]
            if not urls[0].startswith('jdbc:postgresql://127.0.0.1:15432/') or any(v and not v.startswith('jdbc:postgresql://127.0.0.1:15432/') for v in urls):
                raise RuntimeError('Refusing a non-audit database: ' + name)
            if server['ssl'].get('client-auth') != 'need':
                raise RuntimeError('Private service must require client TLS: ' + name)
            if env.get('SPRING_RABBITMQ_HOST', '127.0.0.1') != '127.0.0.1':
                raise RuntimeError('Refusing a non-local broker: ' + name)


def snapshot():
    state = json.loads(STATE.read_text())
    validate(state)
    selected = [STATE]
    selected.extend(Path(v['jar']) for k, v in state['services'].items() if k.removeprefix('careflow-') in PORTS)
    private = AUDIT / 'security-hardening'
    selected.extend(private / name for name in ['private-config.json', 'jwt-private.pem', 'jwt-public.pem'])
    selected.append(private / 'ai-container.json')
    selected.extend((private / 'tls').glob('*'))
    selected.extend((AUDIT / 'uploads').rglob('*'))
    manifest = {}
    for path in selected:
        if not path.is_file():
            continue
        relative = path.resolve().relative_to(AUDIT.resolve())
        destination = SAVED / 'runtime' / relative
        destination.parent.mkdir(parents=True, exist_ok=True, mode=0o700)
        shutil.copyfile(path, destination)
        destination.chmod(0o600)
        manifest[str(relative)] = hashlib.sha256(destination.read_bytes()).hexdigest()
    for name in ['launch.json', 'mobile-dataset.json']:
        source = ROOT / 'careflow-mobile-emulator-service/.runtime' / name
        if source.exists():
            target = SAVED / name
            shutil.copyfile(source, target)
            target.chmod(0o600)
    private_json(SAVED / 'manifest.json', manifest)
    print('Đã lưu cấu hình, khóa, JAR và tệp đính kèm ngoài Git. Không thay dữ liệu DB.', flush=True)


def restore_missing():
    manifest_file = SAVED / 'manifest.json'
    if not manifest_file.exists():
        if not STATE.exists():
            raise RuntimeError('Chưa có runtime trên Mac này. Cần chuẩn bị audit stack trước; đây không phải bộ cài máy mới.')
        snapshot()
    manifest = json.loads(manifest_file.read_text())
    for name, expected in manifest.items():
        relative = Path(name)
        if relative.is_absolute() or '..' in relative.parts:
            raise RuntimeError('Invalid saved path')
        target = AUDIT / relative
        if target.exists():
            continue
        source = SAVED / 'runtime' / relative
        if hashlib.sha256(source.read_bytes()).hexdigest() != expected:
            raise RuntimeError('Saved runtime checksum failed')
        target.parent.mkdir(parents=True, exist_ok=True, mode=0o700)
        shutil.copyfile(source, target)
        target.chmod(0o600)
    runtime = ROOT / 'careflow-mobile-emulator-service/.runtime'
    runtime.mkdir(exist_ok=True, mode=0o700)
    for name in ['launch.json', 'mobile-dataset.json']:
        target = runtime / name
        if not target.exists() and (SAVED / name).exists():
            shutil.copyfile(SAVED / name, target)
            target.chmod(0o600)


def listener(port):
    value = run(['lsof', '-t', f'-iTCP:{port}', '-sTCP:LISTEN']).stdout.split()
    pids = set(value)
    if len(pids) > 1:
        raise RuntimeError('Nhiều tiến trình dùng cổng ' + str(port))
    return int(value[0]) if value else None


def wait_ready(url, child=None, context=None):
    for _ in range(120):
        if child is not None and child.poll() is not None:
            raise RuntimeError('Tiến trình đã dừng; xem log riêng tại ' + str(SAVED / 'logs'))
        if health(url, context):
            return
        time.sleep(0.25)
    raise RuntimeError('Chưa sẵn sàng: ' + url)


def spawn(command, cwd, env, label):
    logs = SAVED / 'logs'
    logs.mkdir(exist_ok=True, mode=0o700)
    with open(logs / (label + '.log'), 'ab', opener=lambda p, flags: os.open(p, flags, 0o600)) as log:
        return subprocess.Popen(command, cwd=cwd, env=env, stdin=subprocess.DEVNULL,
                                stdout=log, stderr=subprocess.STDOUT, start_new_session=True)


def ai_container(start_if_stopped=False):
    descriptor = AUDIT / 'security-hardening/ai-container.json'
    if not descriptor.exists():
        return None
    expected = json.loads(descriptor.read_text())
    if expected.get('name') != 'careflow-r7-ai-api' or not expected.get('image_id', '').startswith('sha256:'):
        raise RuntimeError('Invalid owned AI container descriptor')
    result = run(['docker', 'inspect', expected['name']])
    if result.returncode:
        raise RuntimeError('Không tìm thấy container AI đã provision; không tự tạo hoặc thay image.')
    container = json.loads(result.stdout)[0]
    labels = container['Config'].get('Labels') or {}
    bindings = container['HostConfig'].get('PortBindings') or {}
    if (container['Image'] != expected['image_id'] or labels.get('com.careflow.owner') != 'dangkhoii'
            or labels.get('com.careflow.component') != 'voice-ai-api'
            or bindings.get('8092/tcp') != [{'HostIp': '127.0.0.1', 'HostPort': '18093'}]):
        raise RuntimeError('Container AI không khớp image/owner/cổng loopback đã ghi nhận.')
    running = container['State']['Running']
    if not running and start_if_stopped:
        if run(['docker', 'start', expected['name']]).returncode:
            raise RuntimeError('Không khởi động được container AI đã provision')
        running = True
    if running and start_if_stopped:
        wait_ready('https://localhost:18093/actuator/health', context=tls_context())
    return running


def start():
    restore_missing()
    if run(['docker', 'info']).returncode:
        raise RuntimeError('Hãy mở Docker Desktop, đợi Docker sẵn sàng rồi chạy lại.')
    for name in CONTAINERS:
        result = run(['docker', 'start', name])
        if result.returncode:
            raise RuntimeError('Không khởi động được container hiện có: ' + name + '. Không xóa volume/container.')
    for _ in range(80):
        database = run(['docker', 'exec', CONTAINERS[0], 'pg_isready', '-U', 'careflow_audit'])
        broker = run(['docker', 'exec', CONTAINERS[1], 'rabbitmq-diagnostics', '-q', 'ping'])
        if database.returncode == broker.returncode == 0:
            break
        time.sleep(0.25)
    else:
        raise RuntimeError('PostgreSQL/RabbitMQ chưa sẵn sàng')
    state = json.loads(STATE.read_text())
    validate(state)
    context = tls_context()
    java = shutil.which('java') or '/Library/Java/JavaVirtualMachines/temurin-25.jdk/Contents/Home/bin/java'
    for short in [*NAMES, 'identity-session-secondary']:
        name = 'careflow-' + short
        if short == 'ai-service' and name not in state['services']:
            print('Bỏ qua AI: môi trường chưa được provision R6.', flush=True)
            continue
        info = state['services'][name]
        port = PORTS[short]
        if short == 'ai-service' and ai_container(start_if_stopped=True) is not None:
            print('Đang chạy: ai-service (Docker)', flush=True)
            continue
        current = listener(port)
        url = f'https://localhost:{port}/actuator/health'
        if current:
            command = run(['ps', '-p', str(current), '-o', 'command=']).stdout
            if info['jar'] not in command or not health(url, context):
                raise RuntimeError('Cổng có tiến trình khác hoặc service lỗi: ' + str(port) + '. Không tự dừng nó.')
            info['pid'] = current
            print('Đang chạy: ' + short, flush=True)
            continue
        child = spawn([java, '-XX:ActiveProcessorCount=2', '-Xmx384m', '-jar', info['jar']], ROOT,
                      {**os.environ, **state['environment'], **info['environment']}, short)
        info['pid'] = child.pid
        private_json(STATE, state)
        wait_ready(url, child, context)
        print('Đã mở: ' + short, flush=True)
    private_json(STATE, state)
    for label, port, directory, command in [
        ('mobile-emulator', 3000, ROOT / 'careflow-mobile-emulator-service', [shutil.which('node'), 'server.mjs']),
        ('hospital-web', 5173, ROOT / 'frontend/doctor-web', [shutil.which('node'), 'node_modules/next/dist/bin/next', 'start', '--hostname', '127.0.0.1', '--port', '5173']),
    ]:
        if port == 3000 and not (directory / 'app/build/web/main.dart.js').exists():
            print('Bỏ qua giả lập web: chưa build; backend và app iPhone hoạt động độc lập.', flush=True)
            continue
        current = listener(port)
        if current:
            cwd = run(['lsof', '-a', '-p', str(current), '-d', 'cwd', '-Fn']).stdout.splitlines()
            if 'n' + str(directory) not in cwd:
                raise RuntimeError('Cổng giao diện thuộc source khác: ' + str(port))
            wait_ready(f'http://localhost:{port}/')
        else:
            required = directory / ('app/build/web/main.dart.js' if port == 3000 else '.next/BUILD_ID')
            if not required.exists():
                raise RuntimeError('Chưa có bản build giao diện: ' + label)
            env = {**os.environ, 'NODE_EXTRA_CA_CERTS': str(AUDIT / 'security-hardening/tls/ca.crt'),
                   'CAREFLOW_GATEWAY_URL': 'https://localhost:18080', 'CAREFLOW_GATEWAY': 'https://localhost:18080',
                   'CAREFLOW_WEB_ORIGIN': 'http://localhost:5173',
                   'CAREFLOW_CA_FILE': str(AUDIT / 'security-hardening/tls/ca.crt'), 'PORT': str(port)}
            child = spawn(command, directory, env, label)
            current = child.pid
            wait_ready(f'http://localhost:{port}/', child)
        private_json(AUDIT / 'security-hardening' / (label + '.json'), {'pid': current, 'cwd': str(directory)})
    emulator_built = (ROOT / 'careflow-mobile-emulator-service/app/build/web/main.dart.js').exists()
    if emulator_built and not health('http://localhost:3000/__emulator/health'):
        raise RuntimeError('Giao diện mở nhưng chưa kết nối được Gateway')
    print('\nSẵn sàng: http://localhost:5173/login' + (' và http://localhost:3000' if emulator_built else ''), flush=True)


def status():
    if not STATE.exists():
        print('Runtime chưa có trong /tmp. Chạy start để phục hồi bản đã lưu.')
        return False
    state = json.loads(STATE.read_text())
    validate(state)
    context = tls_context()
    ready = True
    for name, port in PORTS.items():
        if name == 'ai-service' and 'careflow-ai-service' not in state['services']:
            print('TẮT  ai-service: chưa provision R6', flush=True)
            continue
        container_running = ai_container() if name == 'ai-service' else None
        ok = container_running is not False and health(f'https://localhost:{port}/actuator/health', context)
        ready &= ok
        print(('OK   ' if ok else 'LỖI  ') + name + ' :' + str(port) + (' (Docker)' if container_running is not None else ''), flush=True)
    for name, url in [('Mobile → Gateway', 'http://localhost:3000/__emulator/health'),
                      ('Hospital Web', 'http://localhost:5173/login')]:
        if name == 'Mobile → Gateway' and not (ROOT / 'careflow-mobile-emulator-service/app/build/web/main.dart.js').exists():
            print('TẮT  giả lập web: chưa build; kiểm tra iPhone riêng theo hướng dẫn11.', flush=True)
            continue
        ok = health(url)
        ready &= ok
        print(('OK   ' if ok else 'LỖI  ') + name, flush=True)
    return ready


def backup():
    snapshot()
    folder = SAVED / 'backups'
    folder.mkdir(exist_ok=True, mode=0o700)
    target = folder / time.strftime('postgres-%Y%m%d-%H%M%S.sql.gz')
    with open(target, 'xb', opener=lambda p, flags: os.open(p, flags, 0o600)) as raw:
        with gzip.GzipFile(fileobj=raw, mode='wb') as compressed:
            child = subprocess.Popen(['docker', 'exec', CONTAINERS[0], 'pg_dumpall', '-U', 'careflow_audit'],
                                     stdout=subprocess.PIPE, stderr=subprocess.PIPE)
            shutil.copyfileobj(child.stdout, compressed)
            child.stderr.read()
            if child.wait():
                raise RuntimeError('DB backup thất bại; kiểm tra container PostgreSQL')
    with gzip.open(target, 'rb') as check:
        while check.read(1024 * 1024):
            pass
    print('Đã sao lưu PostgreSQL thật: ' + str(target), flush=True)
    print('Không tự restore/xóa/reset dữ liệu. Giữ nguyên Docker volumes.', flush=True)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('action', choices=['start', 'status', 'backup'])
    parser.add_argument('--open', action='store_true', help='Open both local web interfaces after startup')
    args = parser.parse_args()
    SAVED.mkdir(parents=True, exist_ok=True, mode=0o700)
    SAVED.chmod(0o700)
    try:
        with (SAVED / 'launcher.lock').open('a') as lock:
            fcntl.flock(lock, fcntl.LOCK_EX)
            if args.action == 'start':
                start()
                if args.open:
                    subprocess.run(['open', 'http://localhost:3000', 'http://localhost:5173/login'], check=True)
            elif args.action == 'status':
                sys.exit(0 if status() else 1)
            else:
                backup()
    except (RuntimeError, OSError, ValueError, KeyError) as error:
        # Do not dump state/environment or raw subprocess output: they contain private credentials.
        print('CareFlow demo: ' + str(error), file=sys.stderr)
        sys.exit(1)
