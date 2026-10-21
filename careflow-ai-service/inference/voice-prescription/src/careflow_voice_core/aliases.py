from __future__ import annotations

import json
from pathlib import Path


def read_aliases(path: Path) -> list[dict]:
    if not path.is_file():
        return []
    data = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(data, dict) or data.get("schema_version") != 1 or not isinstance(data.get("entries"), list):
        raise ValueError("Alias file sai schema.")
    for row in data["entries"]:
        if not isinstance(row, dict) or any(not isinstance(row.get(field), str) or not row[field].strip()
                                            for field in ("canonical_name", "heard", "kind", "status", "provenance")):
            raise ValueError("Alias thiếu tên chuẩn, cách nghe hoặc provenance.")
        if row["kind"] not in ("asr_error", "spoken_variant", "spelling"):
            raise ValueError("Alias kind phải là asr_error/spoken_variant/spelling.")
        if row["status"] not in ("proposed", "confirmed_intent", "reviewed", "rejected"):
            raise ValueError("Alias status không hợp lệ.")
    return data["entries"]
