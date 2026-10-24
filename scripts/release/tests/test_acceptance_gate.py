import copy
import hashlib
import importlib.util
import tempfile
import unittest
from pathlib import Path

spec=importlib.util.spec_from_file_location('gate',Path(__file__).resolve().parents[1]/'acceptance_gate.py')
gate=importlib.util.module_from_spec(spec)
spec.loader.exec_module(gate)


class AcceptanceGateTest(unittest.TestCase):
    def setUp(self):
        self.directory=tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.root=Path(self.directory.name)
        data=b'synthetic test evidence'
        (self.root/'evidence.json').write_bytes(data)
        self.document={'candidateId':'synthetic-candidate','cases':[
            {'id':key,'status':'PASS','scope':'synthetic gate unit test','expected':'test assertion','actual':'test assertion',
             'candidateId':'synthetic-candidate','hardwareVerified':True,'professionalReviewVerified':True,
             'evidence':[{'path':'evidence.json','sha256':hashlib.sha256(data).hexdigest()}]}
            for key in sorted(gate.REQUIRED)]}

    def test_complete_synthetic_contract_can_pass(self):
        self.assertTrue(gate.evaluate(self.document,self.root)['fullAcceptance'])

    def test_missing_or_duplicated_case_cannot_pass(self):
        self.document['cases'][-1]=copy.deepcopy(self.document['cases'][0])
        self.assertFalse(gate.evaluate(self.document,self.root)['fullAcceptance'])

    def test_partial_hardware_and_clinical_checks_are_not_automatic_passes(self):
        for identifier,field in [('E02','hardwareVerified'),('E16','professionalReviewVerified')]:
            document=copy.deepcopy(self.document)
            next(case for case in document['cases'] if case['id']==identifier)[field]=False
            self.assertFalse(gate.evaluate(document,self.root)['fullAcceptance'])

    def test_tampered_or_missing_evidence_is_rejected(self):
        (self.root/'evidence.json').write_bytes(b'changed evidence')
        self.assertFalse(gate.evaluate(self.document,self.root)['fullAcceptance'])
        (self.root/'evidence.json').unlink()
        self.assertFalse(gate.evaluate(self.document,self.root)['fullAcceptance'])

    def test_evidence_from_another_candidate_is_rejected(self):
        self.document['cases'][0]['candidateId']='old-release'
        self.assertFalse(gate.evaluate(self.document,self.root)['fullAcceptance'])

    def test_evidence_paths_cannot_escape_the_evidence_directory(self):
        self.document['cases'][0]['evidence'][0]['path']='../private.json'
        self.assertFalse(gate.evaluate(self.document,self.root)['fullAcceptance'])

    def test_known_failures_keep_acceptance_open(self):
        self.document['cases'][0]['status']='FAIL'
        self.assertFalse(gate.evaluate(self.document,self.root)['fullAcceptance'])


if __name__=='__main__':
    unittest.main()
