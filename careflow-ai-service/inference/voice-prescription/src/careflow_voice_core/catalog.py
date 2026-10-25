from __future__ import annotations

import csv
import difflib
import json
import re
from dataclasses import dataclass
from datetime import date
from pathlib import Path

from .models import Item
from .aliases import read_aliases
from .phonetics import phonetic_key, similarity
from .text import STRENGTH_PATTERN, fold, key, strength


@dataclass
class Mention:
    text: str
    begin: int
    finish: int
    normalized_name: str | None = None
    suggestions: list[dict] | None = None

    def start(self):
        return self.begin

    def end(self):
        return self.finish

    def __getitem__(self, index):
        if index != 0:
            raise IndexError(index)
        return fold(self.text)


NON_NAMES = {
    "moi", "luong", "thuoc", "dung", "vien", "goi", "chai", "ong", "lan", "ngay",
    "tuan", "thang", "sang", "toi", "chieu", "trua", "lieu", "chi", "cho", "ke",
    "cap", "lay", "hoat chat", "thanh phan", "ta duoc", "ham luong", "moi goi", "moi chai",
}


def is_drug_term(text: str) -> bool:
    name = key(text)
    return bool(name) and name not in NON_NAMES and not re.search(
        r"\b(?:moi|chua|tuong duong|thanh phan|ta duoc)\b", name
    )


def ingredient_term(text: str) -> str | None:
    text = re.sub(r"\([^)]*\)", "", text).strip()
    # Composition prose describes a container/volume before naming the substance.
    # Never truncate 'Mỗi 5 ml chứa ...' at its first number and index 'Mỗi'.
    if re.match(r"^moi\b", fold(text)):
        contains = re.search(r"\bchua\b\s*:?\s*", fold(text))
        if not contains:
            return None
        text = text[contains.end():].strip()
    equivalent = re.search(r"\btuong duong\b\s*(?:voi\s*)?", fold(text))
    if equivalent:
        text = text[equivalent.end():].strip()
    if re.search(r"[;,+/]", text):
        return None
    embedded = STRENGTH_PATTERN.search(fold(text))
    if embedded:
        text = text[:embedded.start()].strip()
    return text if len(text) <= 65 and is_drug_term(text) else None


def search_terms(entry: dict) -> list[str]:
    """Derive names from the actual product/ingredient fields, never make up product records."""
    terms = [entry["name"], *entry.get("aliases", [])]
    raw_ingredient = re.sub(r"\([^)]*\)", "", entry["ingredient"]).strip()
    combination = bool(re.search(r"[;,+/]", raw_ingredient))
    ingredient = ingredient_term(entry["ingredient"])
    if ingredient:
        terms.append(ingredient)
    if not combination:
        base = re.split(r"\d", entry["name"], maxsplit=1)[0].strip(" -(")
        if len(base) >= 4:
            terms.append(base)
    return list(dict.fromkeys(" ".join(term.split()) for term in terms
                             if isinstance(term, str) and is_drug_term(term)))


LEADING = re.compile(
    r"^\s*(?:(?:a thoi|a khong|benh nhan|khong duoc ke|khong ke|khong dung|khong uong|"
    r"cho|ke|them|thuoc|doi|thay|sang|thanh|bang|huy|ngung|bo|tung|dang|da|uong)\b[\s,:-]*)+"
)
FIELD_BOUNDARY = re.compile(r"\b(?:so luong|tong cong|tong|cap|lay|moi lan|ngay|trong|duong|vien nang|vien nen|hon dich|dung dich)\b")


class Catalog:
    def __init__(self, entries: list[dict]):
        self.entries = entries
        if not isinstance(self.entries, list) or not self.entries:
            raise ValueError("Catalog phải là danh sách có ít nhất một mục.")
        ids = set()
        self.names: dict[str, list[dict]] = {}
        self.display_names: dict[str, str] = {}
        spellings = set()
        for entry in self.entries:
            for field in ("id", "name", "source", "checked_at", "status"):
                if not isinstance(entry.get(field), str) or not entry[field].strip():
                    raise ValueError(f"Catalog thiếu trường {field}.")
            if entry["status"] not in ("demo", "unverified", "active", "withdrawn", "expired"):
                raise ValueError("Trạng thái catalog không hợp lệ.")
            date.fromisoformat(entry["checked_at"])
            if entry["id"] in ids:
                raise ValueError("Catalog có ID trùng.")
            ids.add(entry["id"])
            if not isinstance(entry.get("aliases", []), list):
                raise ValueError("Aliases phải là danh sách.")
            for name in search_terms(entry):
                if not isinstance(name, str) or not name.strip():
                    raise ValueError("Tên/alias phải là văn bản không trống.")
                self.names.setdefault(key(name), []).append(entry)
                self.display_names.setdefault(key(name), name.capitalize() if name.isupper() else name)
                # Strength remains a separate spoken field, even if present in a product title.
                if not re.search(r"\d", name) and len(name) <= 65:
                    spellings.add(fold(name))
        self.alias_rows = []
        self.generic_spellings = {}
        self.confirmed_aliases = {}
        self.equivalent_names = {}
        self.pattern = re.compile(r"(?<![a-z0-9])(?:" + "|".join(re.escape(x) for x in sorted(spellings, key=len, reverse=True)) + r")(?![a-z0-9])")
        self.phonetic_names = {name: self.display_names[name] for name in self.names
                               if 6 <= len(phonetic_key(name)) <= 24 and not re.search(r"\d", name)}
        self.equivalent_names: dict[str, set[str]] = {}
        for name in self.names:
            # Treat doubled-letter orthography as a candidate expansion, without selecting a product.
            spelling = re.sub(r"([a-z])\1+", r"\1", name)
            self.equivalent_names.setdefault(spelling, set()).add(name)

    @property
    def is_demo(self) -> bool:
        return any(entry["status"] == "demo" for entry in self.entries)

    def rank_names(self, text: str, limit: int = 5) -> list[dict]:
        ranked = [(similarity(text, name), display) for name, display in self.phonetic_names.items()]
        ranked.sort(key=lambda pair: (-pair[0], not any(key(pair[1]) == key(e["name"]) for e in self.names[key(pair[1])]), pair[1]))
        unique = []
        seen = set()
        for score, name in ranked:
            identity = frozenset(entry["id"] for entry in self.names[key(name)])
            if identity not in seen:
                seen.add(identity)
                unique.append((score, name))
        return [{"name": name, "score": round(score, 4), "method": "vietnamese_phonetic"}
                for score, name in unique[:limit] if score >= 0.78]

    def resolve_name(self, text: str) -> tuple[str | None, list[dict]]:
        if key(text) in self.names:
            return self.display_names[key(text)], []
        if key(text) in self.confirmed_aliases:
            names = sorted(self.confirmed_aliases[key(text)])
            return (names[0] if len(names) == 1 else None), [
                {"name": name, "score": 1.0, "method": "confirmed_alias_intent"} for name in names
            ]
        ranked = self.rank_names(text)
        if not ranked or ranked[0]["score"] < 0.84:
            return None, ranked
        margin = ranked[0]["score"] - ranked[1]["score"] if len(ranked) > 1 else 1
        return (ranked[0]["name"] if margin >= 0.07 else None), ranked

    def mentions(self, text: str) -> list[Mention]:
        folded = fold(text)
        found = [Mention(text[m.start():m.end()], m.start(), m.end()) for m in self.pattern.finditer(folded)]
        # Search only the name position of a dictation clause, not arbitrary conversational n-grams.
        boundaries = [0, *[m.end() for m in re.finditer(r"\bva\b", folded)]]
        for begin in boundaries:
            region = folded[begin:]
            lead = LEADING.match(region)
            start = begin + (lead.end() if lead else len(region) - len(region.lstrip(" ,:-")))
            tail = folded[start:]
            stops = [m.start() for p in (FIELD_BOUNDARY, STRENGTH_PATTERN) if (m := p.search(tail))]
            end = start + (min(stops) if stops else len(tail))
            tokens = list(re.finditer(r"[a-z]+", folded[start:end]))[:8]
            choices = []
            for token in tokens:
                stop = start + token.end()
                if any(start < m.end() and stop > m.start() for m in found):
                    continue
                spoken = text[start:stop]
                ranked = self.rank_names(spoken)
                if ranked and ranked[0]["score"] >= 0.84:
                    choices.append((ranked[0]["score"], stop, spoken, ranked))
            if choices:
                _, stop, spoken, ranked = max(choices, key=lambda choice: (choice[0], choice[1]))
                normalized, _ = self.resolve_name(spoken)
                found.append(Mention(spoken, start, stop, normalized, ranked))
        return sorted(found, key=lambda mention: mention.start())

    def match(self, item: Item) -> Item:
        exact = self.names.get(key(item.spoken_name), [])
        phonetic = not bool(exact)
        if phonetic:
            normalized, suggestions = self.resolve_name(item.spoken_name)
            item.normalized_name = normalized
            item.name_suggestions = suggestions
            if normalized:
                exact = self.names.get(key(normalized), [])
            elif suggestions:
                # Keep close alternatives visible, never select a winner when the margin is small.
                best = suggestions[0]["score"]
                exact = [entry for proposal in suggestions if best - proposal["score"] < 0.07
                         for entry in self.names.get(key(proposal["name"]), [])]
        if exact and key(item.normalized_name or item.spoken_name) in self.names:
            matching_name = item.normalized_name or item.spoken_name
            spelling = re.sub(r"([a-z])\1+", r"\1", key(matching_name))
            exact = [entry for name in self.equivalent_names.get(spelling, {key(matching_name)})
                     for entry in self.names.get(name, [])]
        candidates = {entry["id"]: entry for entry in exact}
        candidates = list(candidates.values())
        if not candidates:
            similar = difflib.get_close_matches(key(item.spoken_name), self.names, n=5, cutoff=0.65)
            item.candidates = list({e["id"]: e for name in similar for e in self.names[name]}.values())[:5]
            item.match_status = "unmatched"
            item.issues.append("Tên chưa khớp chính xác; không tự chọn ứng viên gần giống.")
            return item
        if item.strength:
            target_strength = strength(item.strength)
            candidates = [e for e in candidates if target_strength and (not e.get("strength") or strength(e["strength"]) == target_strength)]
        if item.dosage_form:
            candidates = [e for e in candidates if (not e.get("dosage_form") or key(e["dosage_form"]) == key(item.dosage_form))]
        item.candidates = candidates
        if phonetic and candidates:
            item.match_status = ("alias_review" if key(item.spoken_name) in self.confirmed_aliases else "phonetic_review") if item.normalized_name else "ambiguous_name"
            item.issues.append("Tên gợi ý theo âm đọc; cần xác nhận tên và sản phẩm, không phải xác suất đúng.")
        elif len(candidates) == 1:
            selected = candidates[0]
            if selected["status"] in ("withdrawn", "expired"):
                item.match_status = "unavailable"
                item.issues.append("Mục catalog đã thu hồi/hết hiệu lực; cần kiểm tra.")
            elif selected["status"] in ("demo", "unverified"):
                item.match_status = "unverified"
                item.issues.append("Mục catalog dùng thử, chưa phải sản phẩm đã xác minh." if selected["status"] == "demo"
                                   else "Dữ liệu có nguồn web; cần kiểm tra trạng thái lưu hành trước khi dùng.")
            else:
                item.catalog_id = selected["id"]
                item.match_status = "matched"
        elif candidates:
            item.match_status = "ambiguous"
            item.issues.append("Có nhiều sản phẩm phù hợp; cần xác nhận sản phẩm.")
        else:
            item.match_status = "incompatible"
            item.issues.append("Tên có trong catalog nhưng hàm lượng/dạng bào chế chưa khớp.")
        return item

    def vocabulary(self, limit: int = 50) -> str:
        # Independent of the expected transcript or evaluation labels.
        names = sorted(self.display_names.values(), key=lambda name: (-len(self.names[key(name)]), len(name)))
        names = [name for name in names if not re.search(r"\d", name) and len(name) <= 40
                 and any(entry["status"] not in ("withdrawn", "expired") for entry in self.names[key(name)])]
        return " ".join(names[:min(limit, 50)])[:4000]


def import_csv(source: Path, destination: Path) -> int:
    if source.resolve() == destination.resolve():
        raise ValueError("File nguồn và đích phải khác nhau.")
    with source.open(encoding="utf-8-sig", newline="") as handle:
        rows = list(csv.DictReader(handle))
    entries = []
    for row in rows:
        row["aliases"] = [name.strip() for name in row.get("aliases", "").split("|") if name.strip()]
        row["status"] = row.get("status") or "unverified"
        entries.append(row)
    destination.parent.mkdir(parents=True, exist_ok=True)
    temporary = destination.with_suffix(".pending.json")
    try:
        temporary.write_text(json.dumps(entries, ensure_ascii=False, indent=2), encoding="utf-8")
        Catalog(temporary)
        temporary.replace(destination)
    finally:
        temporary.unlink(missing_ok=True)
    return len(entries)
