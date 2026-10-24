import copy
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[3]


def module(name, path):
    spec = importlib.util.spec_from_file_location(name, ROOT / path)
    value = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(value)
    return value


prepare = module('voice_docker', 'scripts/voice/prepare_docker.py')
demo = module('voice_demo', 'scripts/mac-demo/demo.py')


class PrivateConfigTest(unittest.TestCase):
    def setUp(self):
        self.config = {'server': {'port': 18093, 'address': '127.0.0.1', 'ssl': {'client-auth': 'need', 'trust-store': 'file:/private/tmp/careflow-dangkhoii-audit/security-hardening/tls/truststore.p12'}},
                       'careflow': {'voice': {'jdbc-url': 'jdbc:postgresql://127.0.0.1:15432/audit',
                                              'worker-url': 'https://localhost:18370', 'content-key': 'test-key'}},
                       'security': {'dpop': {'public-origins': 'http://localhost:3000,http://localhost:5173'}},
                       'spring': {'cloud': {'instances': [{'uri': 'https://localhost:18085'}]}}}
        self.state = {'environment': {'SPRING_RABBITMQ_USERNAME': 'test', 'SPRING_RABBITMQ_PASSWORD': 'test'},
                      'services': {'careflow-ai-service': {'environment': {'SPRING_APPLICATION_JSON': json.dumps(self.config)}}}}

    def test_maps_dependencies_but_preserves_origins_keys_and_original_state(self):
        original = copy.deepcopy(self.state)
        result = prepare.container_config(self.state)
        self.assertEqual(result['careflow']['voice']['worker-url'], 'https://ai-service:18370')
        self.assertEqual(result['careflow']['voice']['jdbc-url'], 'jdbc:postgresql://host.docker.internal:15432/audit')
        self.assertEqual(result['careflow']['voice']['content-key'], 'test-key')
        self.assertEqual(result['security'], self.config['security'])
        self.assertEqual(result['server']['ssl']['trust-store'], 'file:/run/careflow/tls/truststore.p12')
        self.assertEqual(result['spring']['cloud']['instances'][0]['uri'], 'https://careflow-notification-service:18085')
        self.assertEqual(self.state, original)

    def test_refuses_shared_database_or_plain_http_dependency(self):
        for field, value in [('jdbc-url', 'jdbc:postgresql://100.116.233.60:5432/shared'),
                             ('worker-url', 'http://localhost:18370')]:
            with self.subTest(field=field):
                config = copy.deepcopy(self.config)
                config['careflow']['voice'][field] = value
                self.state['services']['careflow-ai-service']['environment']['SPRING_APPLICATION_JSON'] = json.dumps(config)
                with self.assertRaises(ValueError):
                    prepare.container_config(self.state)

    def test_private_output_cannot_be_in_checkout(self):
        with self.assertRaises(ValueError):
            prepare.prepare(Path('/not-read'), ROOT / 'private-output')


class OwnedContainerTest(unittest.TestCase):
    def test_changed_image_owner_or_public_binding_never_starts_a_container(self):
        with tempfile.TemporaryDirectory() as directory:
            audit = Path(directory)
            (audit / 'security-hardening').mkdir()
            (audit / 'security-hardening/ai-container.json').write_text(json.dumps({'name': 'careflow-r7-ai-api', 'image_id': 'sha256:expected'}))
            base = {'Image': 'sha256:expected', 'State': {'Running': False},
                    'Config': {'Labels': {'com.careflow.owner': 'dangkhoii', 'com.careflow.component': 'voice-ai-api'}},
                    'HostConfig': {'PortBindings': {'8092/tcp': [{'HostIp': '127.0.0.1', 'HostPort': '18093'}]}}}
            for kind in ['image', 'owner', 'binding']:
                obj = copy.deepcopy(base)
                if kind == 'image': obj['Image'] = 'sha256:other'
                if kind == 'owner': obj['Config']['Labels']['com.careflow.owner'] = 'other'
                if kind == 'binding': obj['HostConfig']['PortBindings']['8092/tcp'][0]['HostIp'] = '0.0.0.0'
                with self.subTest(kind=kind), patch.object(demo, 'AUDIT', audit), patch.object(demo, 'run') as run:
                    run.return_value.returncode = 0
                    run.return_value.stdout = json.dumps([obj])
                    with self.assertRaises(RuntimeError): demo.ai_container(True)
                    self.assertEqual(run.call_count, 1)


if __name__ == '__main__':
    unittest.main()
