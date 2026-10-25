"""Run explicit synthetic contract cases; never report this as clinical accuracy."""
import argparse
import json
from pathlib import Path
from careflow_voice_core.inference import infer


def evaluate(path):
    results=[]
    for line in Path(path).open():
        row=json.loads(line);request=row['request']
        actual=infer(request['transcript'],request['medicines'],request['catalogVersion'],request['transcriptRevision'])
        values=[{key:item[key] for key in ('spokenName','medicineCode','quantity','quantityUnit')} for item in actual['items']]
        results.append(dict(id=row['id'],passed=values==row['expectedItems'] and actual['prescriptionDraft']['canSaveWithoutCorrection']==row['canSaveWithoutCorrection']))
    return dict(evaluationKind='synthetic_contract_fixtures_not_clinical_benchmark',samples=len(results),passed=sum(r['passed'] for r in results),results=results,humanTestSamples=0)


if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('fixtures');p.add_argument('--output',type=Path);a=p.parse_args()
    result=evaluate(a.fixtures)
    if a.output:a.output.write_text(json.dumps(result,indent=2)+'\n')
    print(json.dumps(result));raise SystemExit(0 if result['samples']==result['passed'] else 1)
