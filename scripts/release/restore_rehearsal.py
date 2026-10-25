#!/usr/bin/env python3
"""Back up the owned audit DBs and restore into fresh scratch databases only."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import time
from urllib.parse import urlparse


def run(args, **options):
    return subprocess.run(args, check=True, capture_output=True, **options)


def sql(container, user, database, statement):
    return run(['docker', 'exec', container, 'psql', '-X', '-qAt', '-v', 'ON_ERROR_STOP=1',
                '-U', user, '-d', database, '-c', statement], text=True).stdout.strip()


def fingerprint(container, user, database, snapshot=None):
    begin = "BEGIN ISOLATION LEVEL REPEATABLE READ READ ONLY; SET TIME ZONE 'UTC';"
    if snapshot:
        begin = "BEGIN ISOLATION LEVEL REPEATABLE READ READ ONLY; SET TRANSACTION SNAPSHOT '" + snapshot + "'; SET TIME ZONE 'UTC';"
    tables = sql(container, user, database, begin + "SELECT quote_ident(n.nspname)||'.'||quote_ident(c.relname) FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace WHERE c.relkind='r' AND n.nspname NOT IN ('pg_catalog','information_schema') AND n.nspname NOT LIKE 'pg_toast%' ORDER BY 1; COMMIT;").splitlines()
    statements = [begin]
    for table in tables:
        statements.append("SELECT json_build_object('table', '" + table.replace("'", "''") + "', 'rows', count(*), 'digest', md5(coalesce(string_agg(md5(row_to_json(t)::text), '' ORDER BY md5(row_to_json(t)::text)),''))) FROM " + table + " t;")
    statements.append('COMMIT;')
    return [json.loads(line) for line in sql(container, user, database, '\n'.join(statements)).splitlines()]


def owned_databases(state):
    result = {}
    for name, info in state['services'].items():
        if name in ('careflow-api-gateway', 'careflow-eureka-server'):
            continue
        config = json.loads(info['environment']['SPRING_APPLICATION_JSON'])
        value = config.get('spring', {}).get('datasource', {}).get('url') or info['environment'].get('SPRING_DATASOURCE_URL', '')
        address = urlparse(value.removeprefix('jdbc:'))
        database = address.path.lstrip('/')
        if address.hostname != '127.0.0.1' or address.port != 15432 or not re.fullmatch(r'careflow_[a-z0-9_]+', database):
            raise ValueError('Refusing a database outside the owned loopback audit stack: ' + name)
        result[name] = database
    return result


def referenced_files(value):
    if isinstance(value,dict):
        for item in value.values():
            yield from referenced_files(item)
    elif isinstance(value,list):
        for item in value:
            yield from referenced_files(item)
    elif isinstance(value,str):
        if value.startswith(('{','[')):
            try:
                yield from referenced_files(json.loads(value))
            except json.JSONDecodeError:
                pass
        if value.startswith(('/','file:/')):
            source=Path(value.removeprefix('file:'))
            if source.is_file() and source.stat().st_size<10*1024*1024:
                yield source


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--state-file', type=Path, required=True)
    parser.add_argument('--backup-dir', type=Path, required=True)
    parser.add_argument('--report', type=Path, required=True)
    parser.add_argument('--keep-clones', action='store_true')
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[2]
    backup = args.backup_dir.resolve()
    if backup.is_relative_to(root) or backup.exists():
        raise ValueError('Backup directory must be new and outside the source checkout')
    os.umask(0o077)
    backup.mkdir(parents=True, mode=0o700)
    state = json.loads(args.state_file.read_text())
    mapping = owned_databases(state)
    user = state['environment']['SPRING_DATASOURCE_USERNAME']
    container = 'careflow-dangkhoii-auth-audit-postgres'
    clones, checks, created = {}, [], []
    prefix = 'careflow_r7_restore_' + str(int(time.time())) + '_'
    shutil.copy2(args.state_file, backup / 'state.json')
    (backup / 'state.json').chmod(0o600)
    try:
        for index, database in enumerate(sorted(set(mapping.values()))):
            holder = subprocess.Popen(['docker', 'exec', '-i', container, 'psql', '-X', '-qAt', '-v', 'ON_ERROR_STOP=1', '-U', user, '-d', database], stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
            try:
                holder.stdin.write('BEGIN ISOLATION LEVEL REPEATABLE READ READ ONLY;\nSELECT pg_export_snapshot();\n')
                holder.stdin.flush()
                snapshot = holder.stdout.readline().strip()
                if not re.fullmatch(r'[0-9A-Fa-f]+-[0-9A-Fa-f]+-[0-9]+', snapshot):
                    raise RuntimeError('Could not export a consistent source snapshot')
                expected = fingerprint(container, user, database, snapshot)
                archive = backup / (database + '.dump')
                with archive.open('xb') as output:
                    subprocess.run(['docker', 'exec', container, 'pg_dump', '-U', user, '-Fc', '--no-owner', '--no-acl', '--snapshot=' + snapshot, database], stdout=output, stderr=subprocess.PIPE, check=True)
            finally:
                if holder.poll() is None:
                    holder.communicate('ROLLBACK;\n', timeout=20)
            clone = prefix + str(index)
            if clone in mapping.values():
                raise ValueError('Scratch database overlaps live database')
            run(['docker', 'exec', container, 'createdb', '-U', user, '-T', 'template0', clone])
            created.append(clone)
            with archive.open('rb') as data:
                subprocess.run(['docker', 'exec', '-i', container, 'pg_restore', '-U', user, '-d', clone, '--no-owner', '--no-acl', '--exit-on-error'], stdin=data, stdout=subprocess.PIPE, stderr=subprocess.PIPE, check=True)
            actual = fingerprint(container, user, clone)
            if actual != expected:
                raise RuntimeError('Restored table content differs from its consistent snapshot: ' + database)
            clones[database] = clone
            checks.append({'database': database, 'tables': len(actual), 'rows': sum(row['rows'] for row in actual), 'archiveSha256': hashlib.sha256(archive.read_bytes()).hexdigest(), 'status': 'PASS'})
            print('Verified restore:', database, len(actual), 'tables', flush=True)
        audit = args.state_file.resolve().parent
        security = audit / 'security-hardening'
        if security.exists():
            shutil.copytree(security / 'tls', backup / 'tls')
        key_files = backup / 'content-keys'
        key_files.mkdir()
        key_manifest = {}
        for source in referenced_files(state):
            if str(source) not in key_manifest:
                target = key_files / (hashlib.sha256(str(source).encode()).hexdigest()+'.key')
                shutil.copy2(source,target)
                target.chmod(0o600)
                key_manifest[str(source)] = target.name
        (backup/'key-files.json').write_text(json.dumps(key_manifest,indent=2)+'\n')
        binaries = backup / 'binaries'
        binaries.mkdir()
        for name, info in state['services'].items():
            if info.get('jar') and Path(info['jar']).is_file():
                shutil.copy2(info['jar'], binaries / (name + '.jar'))
        uploads = audit / 'uploads'
        file_count = 0
        if uploads.exists():
            for source in uploads.rglob('*'):
                if source.is_symlink():
                    raise ValueError('Refusing a symlink in patient storage')
                if source.is_file():
                    target = backup / 'uploads' / source.relative_to(uploads)
                    target.parent.mkdir(parents=True, exist_ok=True)
                    shutil.copy2(source, target)
                    if hashlib.sha256(source.read_bytes()).digest() != hashlib.sha256(target.read_bytes()).digest():
                        raise RuntimeError('Attachment backup hash mismatch')
                    file_count += 1
        report = {'status': 'PASS', 'scope': 'Owned local PostgreSQL consistent snapshots; scratch restores, table counts and content hashes; no restore over live databases', 'databases': checks, 'attachmentFilesVerified': file_count, 'contentKeyFilesBackedUp': len(key_manifest), 'fullProductionDR': False}
        private = {'sourceToClone': clones, 'serviceDatabases': mapping, 'backup': str(backup), 'databases': checks}
        (backup / 'restore-state.json').write_text(json.dumps(private, indent=2) + '\n')
        args.report.parent.mkdir(parents=True, exist_ok=True)
        args.report.write_text(json.dumps(report, indent=2) + '\n')
    finally:
        if not args.keep_clones:
            for name in created:
                if name.startswith(prefix) and name not in mapping.values():
                    run(['docker', 'exec', container, 'dropdb', '-U', user, name])


if __name__ == '__main__':
    try:
        main()
    except (ValueError, RuntimeError, subprocess.SubprocessError, OSError) as error:
        print('Restore rehearsal failed:', type(error).__name__, file=sys.stderr)
        sys.exit(1)
