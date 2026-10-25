"""Validate private --dart-define-from-file input without printing its contents."""
import json
import pathlib
import sys

if len(sys.argv) != 2:
    raise SystemExit("Usage: python3 tool/check_release_config.py <private-build-config.json>")
configuration = json.loads(pathlib.Path(sys.argv[1]).read_text())
for name in ("DEMO_MODE", "USE_MOCK_AUTH"):
    if str(configuration.get(name, False)).lower() == "true":
        raise SystemExit("Release configuration rejected: " + name)
print("Release configuration verified: journey demo and mock authentication disabled")
