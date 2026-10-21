#!/usr/bin/env python3
"""Keep backend REST-only while the conditional Spring 6 advisories are being migrated."""
from pathlib import Path
import re
import sys

FORBIDDEN = re.compile(r'\b(XsltView|XsltViewResolver|SseEmitter|ServerSentEvent|FragmentsRendering|RouterFunctions|RouterFunction)\b')

def check(root):
    findings = []
    for path in root.glob('careflow-*/src/main/java/**/*.java'):
        for number, line in enumerate(path.read_text().splitlines(), 1):
            if FORBIDDEN.search(line):
                findings.append(f'{path.relative_to(root)}:{number}: conditional Spring surface requires a patched framework and security review')
    return findings

if __name__ == '__main__':
    findings = check(Path(__file__).resolve().parents[2])
    print('\n'.join(findings) if findings else 'Backend conditional Spring surfaces: absent')
    sys.exit(bool(findings))
