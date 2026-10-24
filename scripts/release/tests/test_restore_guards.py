import importlib.util
import json
from pathlib import Path
import unittest

spec=importlib.util.spec_from_file_location('restore',Path(__file__).resolve().parents[1]/'restore_rehearsal.py')
restore=importlib.util.module_from_spec(spec)
spec.loader.exec_module(restore)


class RestoreGuardTest(unittest.TestCase):
    def state(self,url):
        return {'services':{'careflow-patient-service':{'environment':{'SPRING_APPLICATION_JSON':json.dumps({'spring':{'datasource':{'url':url}}})}}}}

    def test_shared_remote_database_is_rejected_before_any_dump_or_restore(self):
        with self.assertRaises(ValueError):
            restore.owned_databases(self.state('jdbc:postgresql://100.116.233.60:5432/careflow_patient'))

    def test_unexpected_local_database_names_are_rejected(self):
        for value in ['postgres','careflow_patient;DROP DATABASE postgres','../careflow_patient']:
            with self.assertRaises(ValueError):
                restore.owned_databases(self.state('jdbc:postgresql://127.0.0.1:15432/'+value))

    def test_expected_audit_endpoint_is_accepted(self):
        self.assertEqual(restore.owned_databases(self.state('jdbc:postgresql://127.0.0.1:15432/careflow_patient')),{'careflow-patient-service':'careflow_patient'})
