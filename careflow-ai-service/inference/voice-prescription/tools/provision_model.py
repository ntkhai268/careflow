"""Build an offline, checksum-bound local release outside the source repository."""
import argparse
import ctypes
import hashlib
import json
import os
from pathlib import Path
import shutil
import sys


def digest(path):
    with path.open('rb') as file:return hashlib.file_digest(file,'sha256').hexdigest()


def copy(source,target):
    target.parent.mkdir(parents=True,exist_ok=True)
    if sys.platform=='darwin':
        libc=ctypes.CDLL(None,use_errno=True)
        if libc.clonefile(os.fsencode(source),os.fsencode(target),0)==0:return
    shutil.copy2(source,target)


def provision(base,adapter,output,release_id):
    base,adapter,output=map(lambda p:Path(p).resolve(),(base,adapter,output))
    output.mkdir(parents=True,exist_ok=True)
    if any(output.iterdir()):raise ValueError('Use an empty release directory')
    config=json.loads((adapter/'adapter_config.json').read_text())
    if not config.get('base_model_revision') or digest(base/'config.json')!=config.get('base_config_sha256'):
        raise ValueError('Base config does not match the evaluated adapter')
    for source,folder in ((base,'base'),(adapter,'adapter')):
        for file in source.rglob('*'):
            if file.is_file():copy(file,output/folder/file.relative_to(source))
    files={str(file.relative_to(output)):digest(file) for file in output.rglob('*') if file.is_file()}
    manifest=dict(schemaVersion=1,backend='mlx',releaseId=release_id,modelId=config['base_model'],
                  baseRevision=config['base_model_revision'],baseDirectory='base',adapterDirectory='adapter',
                  files=files,decode=dict(language='Vietnamese',context='',maxNewTokens=768),
                  supportedPlatform='macOS Apple Silicon',dependencyLock='uv.lock',
                  licenseStatus='Verify upstream model, tokenizer, adapter and distribution terms before sharing',
                  clinicalValidation=False)
    (output/'model-manifest.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2)+'\n')
    return manifest


if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__)
    for name in ('base','adapter','output','release-id'):p.add_argument('--'+name,required=True)
    a=p.parse_args();m=provision(a.base,a.adapter,a.output,a.release_id)
    print(json.dumps({'releaseId':m['releaseId'],'files':len(m['files']),'manifest':str(Path(a.output)/'model-manifest.json')}))
