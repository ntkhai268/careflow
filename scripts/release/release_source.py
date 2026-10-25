"""Stable source identity; generated reports and user-guide edits do not change binaries."""
import hashlib
import json
from pathlib import Path
import subprocess


def source_fingerprint(root):
    listed=subprocess.check_output(['git','ls-files','-z','--cached','--others','--exclude-standard'],cwd=root).decode().split('\0')
    sources={}
    for name in sorted(set(filter(None,listed))):
        path=Path(root)/name
        if path.is_file() and not name.startswith(('.planning/','huong-dan-su-dung/')):
            sources[name]=hashlib.sha256(path.read_bytes()).hexdigest()
    fingerprint=hashlib.sha256(json.dumps(sources,sort_keys=True,separators=(',',':')).encode()).hexdigest()
    return fingerprint,sources
