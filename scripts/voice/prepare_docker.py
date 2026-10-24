#!/usr/bin/env python3
"""Prepare a private Docker config for the existing, owned Mac audit runtime."""
import argparse
import copy
import json
import os
from pathlib import Path
import shutil
from urllib.parse import urlsplit

ROOT = Path(__file__).resolve().parents[2]
AUDIT = Path('/tmp/careflow-dangkhoii-audit')
HOSTS = {18081: 'careflow-identity-service', 18082: 'careflow-patient-service',
         18083: 'careflow-appointment-service', 18084: 'careflow-queue-service',
         18085: 'careflow-notification-service', 18088: 'careflow-emr-service', 18089: 'careflow-lab-service',
         18086: 'careflow-consultation-service', 18087: 'careflow-prescription-service',
         18090: 'careflow-hospital-directory-service', 18370: 'ai-service'}


def container_config(state):
    info = state['services']['careflow-ai-service']
    env = {**state['environment'], **info['environment']}
    config = copy.deepcopy(json.loads(env['SPRING_APPLICATION_JSON']))
    if (config['server']['port'] != 18093 or config['server']['address'] != '127.0.0.1'
            or config['server']['ssl'].get('client-auth') != 'need'):
        raise ValueError('Expected the owned loopback AI runtime with required mTLS')
    tls = str(AUDIT / 'security-hardening/tls')

    def convert(value, key=''):
        if isinstance(value, dict):
            return {name: convert(item, name) for name, item in value.items()}
        if isinstance(value, list):
            return [convert(item, key) for item in value]
        if not isinstance(value, str):
            return value
        if value.startswith('jdbc:postgresql://'):
            if not value.startswith('jdbc:postgresql://127.0.0.1:15432/'):
                raise ValueError('Refusing a non-audit database')
            return value.replace('127.0.0.1:15432', 'host.docker.internal:15432', 1)
        if value.startswith(('http://', 'https://')) and key in ('url', 'uri', 'identity-url', 'consultation-url', 'prescription-url', 'worker-url'):
            parsed = urlsplit(value)
            if parsed.hostname not in ('127.0.0.1', 'localhost') or parsed.scheme != 'https' or parsed.port not in HOSTS:
                raise ValueError('Refusing an unexpected AI dependency URL')
            return value.replace(parsed.netloc, HOSTS[parsed.port] + ':' + str(parsed.port), 1)
        for prefix in (str(Path(tls).resolve()), tls):
            value = value.replace(prefix + '/', '/run/careflow/tls/')
        return value

    config = convert(config)
    config['server'].update(address='0.0.0.0', port=8092)
    config.setdefault('eureka', {}).setdefault('client', {})['enabled'] = False
    config.setdefault('spring', {})['rabbitmq'] = {
        'host': 'host.docker.internal', 'port': 15673,
        'username': env['SPRING_RABBITMQ_USERNAME'], 'password': env['SPRING_RABBITMQ_PASSWORD']}
    return config


def prepare(state_file, output):
    output = output.expanduser().resolve()
    if output.is_relative_to(ROOT) or output.exists():
        raise ValueError('Use a new private directory outside the repository')
    if state_file.resolve() != (AUDIT / 'state.json').resolve():
        raise ValueError('This helper accepts only the owned Mac audit state')
    state = json.loads(state_file.read_text())
    config = container_config(state)
    original = json.loads(state['services']['careflow-ai-service']['environment']['SPRING_APPLICATION_JSON'])
    assert config['careflow']['voice']['content-key'] == original['careflow']['voice']['content-key']
    output.mkdir(parents=True, mode=0o700)
    output.chmod(0o700)
    tls_out = output / 'tls'
    tls_out.mkdir(mode=0o755)
    for name in ['ca.crt', 'ai-service.crt', 'ai-service.key', 'ai-service.p12', 'truststore.p12']:
        shutil.copyfile(AUDIT / 'security-hardening/tls' / name, tls_out / name)
        # The enclosing private directory is 0700; only this selected folder is mounted.
        (tls_out / name).chmod(0o444)
    config_file = output / 'runtime.yml'
    config_file.write_text(json.dumps(config, ensure_ascii=False, indent=2) + '\n')
    config_file.chmod(0o444)
    env_file = output / 'compose.env'
    env_file.write_text(f'CAREFLOW_AI_CONFIG={config_file}\nCAREFLOW_AI_TLS_DIR={tls_out}\nCAREFLOW_AI_PORT=18093\n')
    env_file.chmod(0o600)
    return env_file


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--state-file', type=Path, default=AUDIT / 'state.json')
    parser.add_argument('--output-dir', type=Path, required=True)
    args = parser.parse_args()
    os.umask(0o077)
    print('Prepared private Compose environment:', prepare(args.state_file, args.output_dir))


if __name__ == '__main__':
    main()
