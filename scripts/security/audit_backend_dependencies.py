#!/usr/bin/env python3
"""Scan a CycloneDX Maven SBOM against OSV; never transmit source or runtime configuration."""
import concurrent.futures
import datetime
import json
from pathlib import Path
import subprocess
import sys
from check_backend_surface import check

ROOT = Path(__file__).resolve().parents[2]

def query(path, payload=None):
    args = ['curl', '--fail', '--silent', '--show-error', '--max-time', '40', 'https://api.osv.dev/v1/' + path]
    if payload is not None:
        args += ['-H', 'Content-Type: application/json', '--data-binary', '@-']
    result = subprocess.run(args, input=json.dumps(payload) if payload is not None else None,
                            text=True, capture_output=True, check=True)
    return json.loads(result.stdout)

def main():
    if len(sys.argv) != 2:
        raise SystemExit('Usage: audit_backend_dependencies.py path/to/bom.json')
    surface = check(ROOT)
    if surface:
        raise SystemExit('\n'.join(surface))
    bom = json.loads(Path(sys.argv[1]).read_text())
    queries = [{'package': {'ecosystem': 'Maven', 'name': c['group'] + ':' + c['name']}, 'version': c['version']}
               for c in bom['components'] if c.get('purl', '').startswith('pkg:maven/') and c.get('group') != 'com.careflow']
    results = []
    for start in range(0, len(queries), 100):
        batch = queries[start:start + 100]
        results += [dict(query=q, **r) for q, r in zip(batch, query('querybatch', {'queries': batch})['results'])]
    ids = {v['id'] for r in results for v in r.get('vulns', [])}
    with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:
        details = dict(zip(sorted(ids), pool.map(lambda key: query('vulns/' + key), sorted(ids))))
    conditions = json.loads((ROOT / '.planning/security/dependency-conditions.json').read_text())
    active_conditions = conditions['advisories'] if datetime.date.today() <= datetime.date.fromisoformat(conditions['expires']) else {}
    failed = False
    for result in results:
        for vuln in result.get('vulns', []):
            aid = vuln['id']
            severity = details[aid].get('database_specific', {}).get('severity', 'UNKNOWN')
            condition = active_conditions.get(aid)
            print(result['query']['package']['name'], result['query']['version'], aid, severity,
                  'CONDITIONAL_FEATURE_ABSENT' if condition else '')
            if not condition and severity in ('HIGH', 'CRITICAL', 'UNKNOWN'):
                failed = True
    print(f'Queried {len(queries)} Maven coordinates; conditional review expires {conditions["expires"]}')
    return int(failed)

if __name__ == '__main__':
    try:
        sys.exit(main())
    except (subprocess.CalledProcessError, ValueError, KeyError, OSError) as failure:
        print(f'Dependency scan could not complete: {type(failure).__name__}', file=sys.stderr)
        sys.exit(2)
