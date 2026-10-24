#!/usr/bin/env python3
"""Fingerprint the dirty source candidate, built backend/web, and native artifacts."""
import argparse
import hashlib
import json
from pathlib import Path
import subprocess
from release_source import source_fingerprint

MODULES=['api-gateway','identity','patient','emr','appointment','queue','analytics','consultation','prescription','lab','notification','ai','hospital-directory','eureka-server']


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def tree_digest(folder,excluded=()):
    records=[]
    for path in sorted(folder.rglob('*')):
        relative=path.relative_to(folder)
        if path.is_file() and not any(part in excluded for part in relative.parts):
            records.append((relative.as_posix(),digest(path)))
    return hashlib.sha256(json.dumps(records,separators=(',',':')).encode()).hexdigest(),len(records)


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output',type=Path,required=True)
    parser.add_argument('--ios-app',type=Path,required=True)
    parser.add_argument('--android-apk',type=Path,required=True)
    args=parser.parse_args()
    root=Path(__file__).resolve().parents[2]
    fingerprint,sources=source_fingerprint(root)
    artifacts={}
    for short in MODULES:
        name='careflow-'+short+('' if short in ['api-gateway','eureka-server'] else '-service')
        path=root/name/'target'/(name+'-1.0.0-SNAPSHOT.jar')
        if not path.is_file():
            raise FileNotFoundError('Missing backend artifact: '+name)
        artifacts[name]={'sha256':digest(path),'bytes':path.stat().st_size}
    web=root/'frontend/doctor-web/.next'
    artifacts['doctor-web']={'buildId':(web/'BUILD_ID').read_text().strip(),'treeSha256':tree_digest(web,('cache','diagnostics','trace'))[0]}
    artifacts['patient-ios']={'treeSha256':tree_digest(args.ios_app)[0]}
    artifacts['patient-android']={'sha256':digest(args.android_apk),'bytes':args.android_apk.stat().st_size}
    result={'candidateId':'r7-'+fingerprint[:16],'baseCommit':subprocess.check_output(['git','rev-parse','HEAD'],cwd=root,text=True).strip(),
            'branch':subprocess.check_output(['git','branch','--show-current'],cwd=root,text=True).strip(),'sourceTreeSha256':fingerprint,
            'sourceFileCount':len(sources),'sourceFiles':sources,'artifacts':artifacts,'committed':False,'productionRelease':False}
    args.output.parent.mkdir(parents=True,exist_ok=True)
    args.output.write_text(json.dumps(result,indent=2)+'\n')
    print(result['candidateId']+': '+str(len(artifacts))+' built artifacts fingerprinted')


if __name__=='__main__':
    main()
