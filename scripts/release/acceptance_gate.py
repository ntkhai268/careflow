#!/usr/bin/env python3
"""Validate E01–E18 evidence and keep incomplete acceptance distinct from passing tests."""
import argparse
import hashlib
import json
from pathlib import Path
import sys

REQUIRED={f'E{number:02}' for number in range(1,19)}
DEVICE_CASES={'E01','E02','E03','E04','E05','E06','E07','E11','E12'}
STATUSES={'PASS','PARTIAL','BLOCKED','NOT_RUN','FAIL'}


def evaluate(document, base):
    reasons=[]
    cases=document.get('cases',[])
    if len(cases)!=18 or {case.get('id') for case in cases}!=REQUIRED:
        reasons.append('Exactly one record for each of E01–E18 is required')
    if not document.get('candidateId'):
        reasons.append('Missing candidateId')
    for case in cases:
        identifier=case.get('id','unknown')
        if case.get('status') not in STATUSES:
            reasons.append(identifier+': invalid status')
            continue
        if case['status']!='PASS':
            reasons.append(identifier+': '+case['status'])
            continue
        if not case.get('scope') or not case.get('actual') or not case.get('expected'):
            reasons.append(identifier+': missing scope or expected/actual result')
        if case.get('candidateId')!=document.get('candidateId'):
            reasons.append(identifier+': evidence belongs to another candidate')
        evidence=case.get('evidence',[])
        if not evidence:
            reasons.append(identifier+': no evidence')
        for item in evidence:
            relative=Path(item.get('path',''))
            path=(base/relative).resolve()
            if relative.is_absolute() or not path.is_relative_to(base.resolve()) or not path.is_file():
                reasons.append(identifier+': evidence file missing or outside evidence directory')
            elif hashlib.sha256(path.read_bytes()).hexdigest()!=item.get('sha256'):
                reasons.append(identifier+': evidence hash mismatch')
        if identifier in DEVICE_CASES and case.get('hardwareVerified') is not True:
            reasons.append(identifier+': physical device/OS verification missing')
        if identifier=='E16' and case.get('professionalReviewVerified') is not True:
            reasons.append('E16: professional clinical review missing')
    return {'fullAcceptance':not reasons,'candidateId':document.get('candidateId'),'reasons':reasons}


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('cases',type=Path)
    parser.add_argument('--output',type=Path,required=True)
    parser.add_argument('--verify-source',action='store_true')
    args=parser.parse_args()
    result=evaluate(json.loads(args.cases.read_text()),args.cases.parent)
    if args.verify_source:
        from release_source import source_fingerprint
        fingerprint,_=source_fingerprint(Path(__file__).resolve().parents[2])
        if result['candidateId']!='r7-'+fingerprint[:16]:
            result['reasons'].append('Current source differs from the accepted candidate')
            result['fullAcceptance']=False
    args.output.parent.mkdir(parents=True,exist_ok=True)
    args.output.write_text(json.dumps(result,indent=2,ensure_ascii=False)+'\n')
    print('Full acceptance: '+('PASS' if result['fullAcceptance'] else 'OPEN'))
    return 0 if result['fullAcceptance'] else 2


if __name__=='__main__':
    sys.exit(main())
