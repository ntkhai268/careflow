#!/usr/bin/env python3
"""Run all backend regressions with opt-in PostgreSQL tests on the owned test DBs."""
import argparse
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import xml.etree.ElementTree as ET


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--audit-state', type=Path)
    parser.add_argument('--db-user')
    parser.add_argument('--password-env', default='CAREFLOW_TEST_DB_PASSWORD')
    parser.add_argument('--postgres-container', default='careflow-dangkhoii-auth-audit-postgres')
    parser.add_argument('--output-dir', type=Path, required=True)
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[2]
    if args.audit_state:
        settings = json.loads(args.audit_state.read_text())['environment']
        user, password = settings['SPRING_DATASOURCE_USERNAME'], settings['SPRING_DATASOURCE_PASSWORD']
    else:
        user, password = args.db_user, os.environ.get(args.password_env)
    if not user or not password:
        parser.error('Provide --audit-state or --db-user and the password environment variable')
    args.output_dir.mkdir(parents=True, exist_ok=True, mode=0o700)
    databases = set()
    for path in root.glob('careflow-*/src/test/**/*.java'):
        databases.update(re.findall(r'jdbc:postgresql://127\.0\.0\.1:15432/(careflow_[a-z0-9_]+_test)', path.read_text()))
    urls = {'RECOVERY_TEST_JDBC':'careflow_email_recovery_test', 'DOCUMENT_TEST_JDBC':'careflow_document_identity_test',
            'PHASE2_TEST_JDBC':'careflow_account_lifecycle_test', 'R1_TEST_JDBC':'careflow_r1_administration_test',
            'R1_DIRECTORY_TEST_JDBC':'careflow_r1_directory_persistence_test', 'PHASE1_CATALOG_JDBC':'careflow_phase1_catalog_test'}
    databases.update(urls.values())
    for name in sorted(databases):
        result = subprocess.run(['docker','exec',args.postgres_container,'psql','-U',user,'-d','postgres','-tAc',
            "SELECT 1 FROM pg_database WHERE datname='"+name+"'"],check=True,capture_output=True,text=True)
        if result.stdout.strip() != '1':
            subprocess.run(['docker','exec',args.postgres_container,'createdb','-U',user,name],check=True,capture_output=True)
    env = {**os.environ,'RECOVERY_TEST_USER':user,'RECOVERY_TEST_PASSWORD':password,'PHASE1_DB_USER':user,'PHASE1_DB_PASSWORD':password}
    for phase in range(2,7):
        env.update({f'R{phase}_DB_TESTS':'1',f'R{phase}_DB_USER':user,f'R{phase}_DB_PASSWORD':password})
    for key,name in urls.items():
        env[key]='jdbc:postgresql://127.0.0.1:15432/'+name
    with (args.output_dir/'backend.log').open('w') as log:
        os.chmod(log.name,0o600)
        process = subprocess.run(['mvn','-B','-Dnet.bytebuddy.experimental=true','clean','verify'],cwd=root,env=env,stdout=log,stderr=subprocess.STDOUT)
    modules = {}
    for folder in sorted(root.glob('careflow-*')):
        suites=[ET.parse(path).getroot().attrib for path in folder.glob('target/surefire-reports/TEST-*.xml')]
        if suites:
            modules[folder.name]={key:sum(int(suite.get(key,0)) for suite in suites) for key in ['tests','failures','errors','skipped']}
    summary={'status':'PASS' if process.returncode==0 else 'FAIL','postgresTestsEnabled':True,'modules':modules}
    (args.output_dir/'backend-results.json').write_text(json.dumps(summary,indent=2)+'\n')
    print(json.dumps(summary,indent=2))
    return process.returncode


if __name__ == '__main__':
    sys.exit(main())
