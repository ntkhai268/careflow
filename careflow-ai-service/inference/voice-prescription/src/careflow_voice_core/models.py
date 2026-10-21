from __future__ import annotations

from dataclasses import asdict, dataclass, field
from typing import Any

FIELDS = ("strength", "dosage_form", "quantity", "quantity_unit", "dose", "frequency", "route", "duration",
          "timing", "min_interval", "max_daily_dose")


@dataclass
class Evidence:
    text: str
    start: int
    end: int

    @classmethod
    def at(cls, transcript: str, start: int, end: int) -> Evidence:
        if not 0 <= start < end <= len(transcript):
            raise ValueError("Mốc bằng chứng không hợp lệ.")
        return cls(transcript[start:end], start, end)


@dataclass
class Item:
    spoken_name: str
    normalized_name: str | None = None
    name_suggestions: list[dict[str, Any]] = field(default_factory=list)
    field_conflicts: dict[str, list[Evidence]] = field(default_factory=dict)
    strength: str | None = None
    dosage_form: str | None = None
    quantity: float | None = None
    quantity_unit: str | None = None
    dose: str | None = None
    frequency: str | None = None
    route: str | None = None
    duration: str | None = None
    timing: str | None = None
    min_interval: str | None = None
    max_daily_dose: str | None = None
    evidence: dict[str, Evidence] = field(default_factory=dict)
    catalog_id: str | None = None
    match_status: str = "unmatched"
    candidates: list[dict[str, Any]] = field(default_factory=list)
    issues: list[str] = field(default_factory=list)

    def to_dict(self) -> dict[str, Any]:
        return asdict(self)


@dataclass
class Extraction:
    transcript: str
    items: list[Item]
    warnings: list[str]
    provider: str
    elapsed_ms: float = 0

    def to_dict(self) -> dict[str, Any]:
        return asdict(self)
