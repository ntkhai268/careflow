from __future__ import annotations

import re
import time
import unicodedata

from .catalog import Catalog, Mention, is_drug_term
from .models import FIELDS, Evidence, Extraction, Item
from .text import NUMBER_PATTERN, STRENGTH_PATTERN, fold, key, number, strength

QUANTITY = re.compile(
    rf"\b(?:so luong|luong|tong cong|tong|(?:chi\s+)?cap|(?:chi\s+)?lay)\s*[:=]?\s*({NUMBER_PATTERN})\s*(vien|goi|ong|chai|lo|hop|ml)?\b"
)
QUANTITY_PRESCRIBED = re.compile(
    rf"\b(?:dinh\s+)?ke\s+({NUMBER_PATTERN})\s*(vien|goi|ong|chai|lo|hop|ml|thanh vien)\b"
)
QUANTITY_CORRECTION = re.compile(
    rf"\b(?:a khong|sua thanh|dinh chinh)\s+(?:so luong\s+)?({NUMBER_PATTERN})\s*(vien|goi|ong|chai|lo|hop|ml)\b"
)
DOSE = re.compile(rf"\bmoi lan\s+(?:(?:uong|dung)\s+)?{NUMBER_PATTERN}\s*(?:vien|goi|ong|ml|giot)\b")
FREQUENCY = re.compile(rf"\b(?:moi\s+)?ngay\s+(?:dung\s+)?{NUMBER_PATTERN}\s+lan\b|\bmoi\s+{NUMBER_PATTERN}\s+gio\b")
AS_NEEDED = re.compile(
    r"\b(?:(?:dung|uong)\s+khi\s+|khi\s+can\b)[^.;\n]*?(?=\s+(?:toi\s+(?:dinh\s+)?ke|so luong|luong|chi lay|chi cap|moi lan|cac lan|khong uong|a\s)|$)"
)
TIMING = re.compile(r"\b(?:vao\s+)?buoi\s+(?:sang|trua|chieu|toi)(?:\s*(?:,\s*|va\s+)(?:vao\s+)?(?:buoi\s+)?(?:sang|trua|chieu|toi))*\b")
MIN_INTERVAL = re.compile(rf"\b(?:cac lan (?:uong|dung)\s+)?cach nhau\s+it nhat\s+{NUMBER_PATTERN}\s*(?:gio|tieng)\b")
MAX_DAILY_DOSE = re.compile(rf"\bkhong\s+(?:uong|dung)\s+qua\s+{NUMBER_PATTERN}\s*(?:vien|goi|ong|ml|giot)\s+trong\s+(?:vong\s+)?(?:24|hai muoi bon)\s*(?:gio|tieng)\b")
SCHEDULE = re.compile(r"\b(?:buoi\s+)?(?:sang|trua|chieu|toi)\s+(?:va\s+)?(?:buoi\s+)?(?:sang|trua|chieu|toi)\b")
CONTINUATION = re.compile(r"^\s*(?:thuoc\b|uong\b|moi lan\b|moi ngay\b|ngay\b|sang\b|toi\b|lieu\b|so luong\b|luong\b|tong\b|cap\b|lay\b|chi dung\b|cac lan\b|khong\s+(?:uong|dung)\s+qua\b)")
CONTINUATION = re.compile(CONTINUATION.pattern + rf"|^\s*(?:trong\b|dung\s+trong\b|nay\s+{NUMBER_PATTERN}\s+lan\b|{NUMBER_PATTERN}\s*(?:vien|goi|ong|ml)\b)")
DURATION = re.compile(rf"\b(?:trong|dung trong)\s+(?P<count>{NUMBER_PATTERN})\s+(?P<period>ngay|tuan|thang)\b")
NUMBERED_MEDICINE = re.compile(rf"\b(?:thuoc\s+)?thu\s+(?:nhat|nhi|tu|{NUMBER_PATTERN})\s+la\b")
FORM = re.compile(r"\b(?:vien nang|vien nen|hon dich|dung dich|siro)\b")
ROUTE = re.compile(r"\b(?:duong uong|duong boi|duong tiem|uong|boi ngoai da|nho mat|nho mui)\b")
HISTORY = re.compile(r"\b(?:tung|tien su|truoc day|trong ho so|dang dung|dang uong|benh nhan ke|di ung|da dung|da uong)\b")
NEGATION = re.compile(r"\b(?:khong(?: duoc)? (?:ke|dung|uong|cho)(?!\s+qua\b)|chua ke|huy|bo thuoc|ngung|dung ke|dung uong)\b")
UNCERTAIN = re.compile(r"\b(?:neu|co nen|can nhac|hay la|co the|du dinh|chua chac)\b")
REPLACE = re.compile(r"\b(?:doi|thay)\b.*?\b(?:sang|thanh|bang)\b")
CORRECTION = re.compile(r"\b(?:a khong|sua thanh|dinh chinh|sua lai|dinh lai|chi lay|chi cap)\b")
BOUNDARY = re.compile(
    r"[;\n]+|(?<!\d)[.!?]+(?!\d)|,(?=\s*(?:cho|ke|them|doi|huy|khong ke)\b)"
)
# ASR may omit all punctuation. Split at explicit discourse transitions, while
# keeping dose/quantity corrections inside their medication's span.
DISCOURSE_BOUNDARY = re.compile(
    r"\b(?:dau tien\s+(?:toi\s+)?ke|tiep theo\s+(?:la|toi ke)|"
    r"(?:lan nay|bay gio)\s+(?:toi\s+)?ke|"
    r"(?:a\s+)?toi thay trong ho so|(?:nhung\s+)?lan nay\s+(?:toi\s+)?khong ke|"
    r"(?:de\s+)?toi kiem tra lai don(?:\s+mot chut)?|"
    r"(?:toi\s+)?(?:nhac|doc) lai don)\b"
)
RECAP = re.compile(r"\b(?:kiem tra lai don|nhac lai don|doc lai don)\b")
BARE_QUANTITY = re.compile(rf"\s*[, :]?\s*({NUMBER_PATTERN})\s*(vien|goi|ong|chai|lo|hop|ml)\b")
DISPLAY_FORMS = {"vien nang": "viên nang", "vien nen": "viên nén", "hon dich": "hỗn dịch", "dung dich": "dung dịch", "siro": "siro"}
DISPLAY_UNITS = {"vien": "viên", "goi": "gói", "ong": "ống", "chai": "chai", "lo": "lọ", "hop": "hộp", "ml": "ml"}
DISPLAY_ROUTES = {"duong uong": "uống", "uong": "uống", "duong boi": "bôi", "boi ngoai da": "bôi ngoài da", "duong tiem": "tiêm", "nho mat": "nhỏ mắt", "nho mui": "nhỏ mũi"}


def _set(item: Item, field: str, value, transcript: str, start: int, end: int) -> None:
    setattr(item, field, value)
    item.evidence[field] = Evidence.at(transcript, start, end)


def _conflict(item: Item, field: str, spans: list[Evidence], message: str):
    setattr(item, field, None)
    item.evidence.pop(field, None)
    item.field_conflicts[field] = spans
    if message not in item.issues:
        item.issues.append(message)


def parse_fields(item: Item, transcript: str, start: int, end: int, *, recap: bool = False) -> None:
    text = transcript[start:end]
    folded = fold(text)
    for field, pattern in (
        ("strength", STRENGTH_PATTERN), ("quantity", QUANTITY), ("dosage_form", FORM),
        ("dose", DOSE), ("frequency", FREQUENCY), ("route", ROUTE), ("duration", DURATION),
        ("timing", TIMING), ("min_interval", MIN_INTERVAL), ("max_daily_dose", MAX_DAILY_DOSE),
    ):
        matches = list(pattern.finditer(folded))
        if field == "strength":
            # A dose expressed in mg/ml is not the product's strength.
            field_starts = [m.start() for p in (QUANTITY, DOSE, FREQUENCY, DURATION, ROUTE)
                            if (m := p.search(folded))]
            if field_starts:
                matches = [m for m in matches if m.end() <= min(field_starts)]
        if field == "quantity":
            matches.extend(QUANTITY_CORRECTION.finditer(folded))
            matches.extend(QUANTITY_PRESCRIBED.finditer(folded))
            # The correction regex and ordinary quantity regex can cover the
            # same numeric token. That is one value, not a conflicting pair.
            unique = {}
            for match in sorted(matches, key=lambda m: m.start()):
                unique.setdefault((match.start(1), match.end(1)), match)
            matches = list(unique.values())
            # Bare counts are accepted only directly after a strength in an
            # explicitly introduced recap, never inferred from a dose/duration.
            if recap and (initial_strength := STRENGTH_PATTERN.match(folded.lstrip())):
                offset = len(folded) - len(folded.lstrip()) + initial_strength.end()
                if bare := BARE_QUANTITY.match(folded, offset):
                    matches.append(bare)
            matches.sort(key=lambda match: match.start())
        if field == "frequency":
            matches.extend(AS_NEEDED.finditer(folded))
            matches.sort(key=lambda match: match.start())
            # 'khi cần' restates PRN; retain the explicit symptom condition.
            specific_prn = [m for m in matches if m.re is AS_NEEDED and _prn_condition(m[0])]
            if specific_prn:
                matches = [m for m in matches if _prn_condition(m[0]) != ""]
        if not matches:
            continue
        selected = matches[-1]
        if len(matches) > 1 and not CORRECTION.search(folded[matches[-2].end():selected.end()]):
            values = {field_value(field, m[0]) for m in matches}
            if len(values) > 1:
                _conflict(item, field, [Evidence.at(transcript, start + m.start(), start + m.end()) for m in matches],
                          f"Nhiều giá trị {field} chưa có lệnh sửa rõ ràng.")
                continue
        value = text[selected.start():selected.end()].strip()
        if field == "strength":
            value = strength(value)
        elif field == "quantity":
            value = number(selected[1])
            if value <= 0:
                item.issues.append("Số lượng phải lớn hơn 0.")
                continue
            if selected[2] and selected[2] in DISPLAY_UNITS:
                _set(item, "quantity_unit", DISPLAY_UNITS[selected[2]], transcript,
                     start + selected.start(2), start + selected.end(2))
            else:
                item.quantity_unit = None
                item.evidence.pop("quantity_unit", None)
            if re.search(r"\bthanh vien\b", folded[selected.start():selected.end() + 15]):
                item.issues.append("ASR ghi 'thành viên' ở số lượng; cần nghe lại và xác nhận đơn vị cấp.")
        elif field == "duration" and selected["period"] == "ngay":
            days = number(selected["count"])
            if days > 0 and days.is_integer():
                value = f"{int(days)} ngày"
        elif field == "dosage_form":
            value = DISPLAY_FORMS[selected[0]]
        elif field == "route":
            value = DISPLAY_ROUTES[selected[0]]
        previous = item.evidence.get(field)
        prn_expansion = False
        if field == "frequency" and previous and field not in item.field_conflicts:
            old_prn, new_prn = _prn_condition(str(item.frequency)), _prn_condition(str(value))
            if old_prn is not None and new_prn is not None:
                if old_prn and (not new_prn or old_prn == new_prn):
                    continue
                prn_expansion = not old_prn and bool(new_prn)
        if previous and not prn_expansion and field_value(field, str(getattr(item, field))) != field_value(field, str(value)) and field not in item.field_conflicts:
            bridge = fold(transcript[previous.end:start + selected.end()])
            if not CORRECTION.search(bridge):
                _conflict(item, field, [previous, Evidence.at(transcript, start + selected.start(), start + selected.end())],
                          f"Thông tin {field} giữa các câu chưa thống nhất; cần xác nhận.")
                continue
        if field in item.field_conflicts:
            continue
        _set(item, field, value, transcript, start + selected.start(), start + selected.end())
    schedule = SCHEDULE.search(folded)
    frequency_ev = item.evidence.get("frequency")
    if schedule and frequency_ev and re.search(r"\b(?:mot|1)\s+lan\b", fold(frequency_ev.text)):
        _conflict(item, "frequency", [frequency_ev, Evidence.at(transcript, start + schedule.start(), start + schedule.end())],
                  "Tần suất mâu thuẫn: một lần/ngày nhưng lại nói hai thời điểm; cần xác nhận.")


def _clauses(transcript: str):
    start = 0
    folded = fold(transcript)
    boundaries = [(m.start(), m.end()) for m in BOUNDARY.finditer(folded)]
    boundaries.extend((m.start(), m.start()) for m in DISCOURSE_BOUNDARY.finditer(folded))
    # Numbered names delimit medicines even when ASR misses punctuation.
    # Keep negative/history/hypothetical wording with its medicine.
    base_boundaries = list(boundaries)
    for mention in NUMBERED_MEDICINE.finditer(folded):
        previous = max((end for _, end in base_boundaries if end <= mention.start()), default=0)
        prefix = folded[previous:mention.start()]
        if STRENGTH_PATTERN.search(prefix) and not any(p.search(prefix) for p in (HISTORY, NEGATION, UNCERTAIN)):
            boundaries.append((mention.start(), mention.start()))
    for begin, end in sorted(boundaries):
        if begin < start:
            continue
        if transcript[start:begin].strip():
            yield start, begin
        start = end
    if transcript[start:].strip():
        yield start, len(transcript)


def _prn_condition(value: str) -> str | None:
    folded = fold(value).strip(" ,:;.")
    match = re.fullmatch(r"(?:(?:chi\s+)?(?:dung|uong)\s+)?khi\s+(.+)", folded)
    if not match:
        return None
    condition = match[1]
    if condition == "can":
        return ""
    # Addressing the patient does not change the explicitly spoken condition.
    return re.sub(r"^(?:(?:benh nhan|chi|anh|em|ban|ong|ba)\s+)?(?:bi\s+)?", "", condition)


def field_value(field: str, value: str):
    """Compare equivalent number spellings without changing their evidence."""
    if field == "strength":
        return strength(value)
    folded = fold(value).strip()
    if field == "timing":
        return re.sub(r"^vao\s+", "", folded)
    if field == "dose":
        folded = re.sub(r"\bmoi lan\s+(?:uong|dung)\s+", "moi lan ", folded)
    if field == "frequency" and not FREQUENCY.fullmatch(folded):
        condition = _prn_condition(value)
        return ("prn", condition) if condition is not None else folded
    return re.sub(NUMBER_PATTERN, lambda m: f"{number(m[0]):g}", folded) if field in (
        "dose", "frequency", "duration", "min_interval", "max_daily_dose"
    ) else folded


def merge_recap(previous: Item, recap: Item) -> None:
    for field in FIELDS:
        if field == "quantity_unit":
            continue
        value = getattr(recap, field)
        if field in recap.field_conflicts:
            _conflict(previous, field, recap.field_conflicts[field], f"Đọc lại đơn có thông tin {field} mâu thuẫn; cần xác nhận.")
        elif value is not None and field not in previous.field_conflicts:
            old = getattr(previous, field)
            if field == "frequency" and old:
                old_prn, new_prn = _prn_condition(str(old)), _prn_condition(str(value))
                if old_prn is not None and new_prn is not None:
                    if not old_prn and new_prn:
                        previous.frequency = value
                        previous.evidence[field] = recap.evidence[field]
                    elif old_prn and new_prn and old_prn != new_prn:
                        _conflict(previous, field, [previous.evidence[field], recap.evidence[field]],
                                  "Điều kiện dùng khi cần được đọc lại khác lời; cần đối chiếu transcript và xác nhận.")
                    continue
            if old is not None and field_value(field, str(old)) != field_value(field, str(value)):
                _conflict(previous, field, [previous.evidence[field], recap.evidence[field]],
                          f"Đọc lại đơn khác {field} đã kê; cần xác nhận.")
            elif old is None:
                setattr(previous, field, value)
                previous.evidence[field] = recap.evidence[field]
        if field == "quantity" and previous.quantity is not None and recap.quantity_unit:
            if previous.quantity_unit is None:
                previous.quantity_unit = recap.quantity_unit
                previous.evidence["quantity_unit"] = recap.evidence["quantity_unit"]
            elif previous.quantity_unit != recap.quantity_unit:
                _conflict(previous, "quantity_unit", [previous.evidence["quantity_unit"], recap.evidence["quantity_unit"]],
                          "Đơn vị cấp khi đọc lại không thống nhất; cần xác nhận.")


def _same_name(left: Item, right: str, catalog: Catalog) -> bool:
    # Aliases are equivalent for removal, but ingredients are never substituted for brands.
    left_name = catalog.resolve_name(left.spoken_name)[0] or left.spoken_name
    right_name = catalog.resolve_name(right)[0] or right
    a = {e["id"] for e in catalog.names.get(key(left_name), [])}
    b = {e["id"] for e in catalog.names.get(key(right_name), [])}
    equivalent = (catalog.generic_spellings.get(key(left_name)) is not None
                  and catalog.generic_spellings.get(key(left_name)) == catalog.generic_spellings.get(key(right_name)))
    return bool(a & b) or key(left.spoken_name) == key(right) or equivalent


# A medicine can be dictated without an action word or numbered introduction.
NAME_STOP_WORDS = set("thuoc thu la ke them cho toi chi anh em benh nhan moi lan uong dung lieu ham luong so tong cap lay vien goi ong chai lo hop ngay tuan thang buoi sang trua chieu vao va trong duong mot hai ba bon tu nam sau bay tam chin nhat nhi".split())


def medicine_mentions(clause: str, catalog: Catalog) -> list[Mention]:
    found = catalog.mentions(clause)
    folded = fold(clause)
    candidates = []
    for dosage in STRENGTH_PATTERN.finditer(folded):
        tokens = list(re.finditer(r"(?<![a-z0-9-])[a-z][a-z0-9-]*", folded[:dosage.start()]))
        cursor = dosage.start()
        name_tokens = []
        for token in reversed(tokens):
            gap = folded[token.end():cursor]
            if (gap.strip(" ,:\t") if not name_tokens else gap.strip()) or token[0] in NAME_STOP_WORDS:
                break
            name_tokens.append(token)
            cursor = token.start()
        if not name_tokens or len(name_tokens) > 5:
            continue
        begin, end = name_tokens[-1].start(), name_tokens[0].end()
        name = clause[begin:end]
        if len(name) <= 65 and is_drug_term(name):
            candidates.append((begin, end, dosage.end()))
    for index, (begin, end, dosage_end) in enumerate(candidates):
        field_end = candidates[index+1][0] if index+1 < len(candidates) else len(clause)
        fields = folded[dosage_end:field_end]
        # A bare medicine line may continue with its frequency in the next sentence.
        if folded[:begin].strip() and not (FREQUENCY.search(fields) or AS_NEEDED.search(fields)):
            continue
        if not any(begin < old.end() and end > old.start() for old in found):
            found.append(Mention(clause[begin:end], begin, end))
    return sorted(found, key=lambda mention: mention.start())


class RulesExtractor:
    name = "rules-v3"

    def __init__(self, catalog: Catalog):
        self.catalog = catalog

    def extract(self, transcript: str) -> Extraction:
        started = time.perf_counter()
        transcript = unicodedata.normalize("NFC", transcript.strip())
        if not transcript or len(transcript) > 12000:
            raise ValueError("Transcript cần có 1–12.000 ký tự.")
        items: list[Item] = []
        warnings: list[str] = []
        continuation_target: Item | None = None
        recap_mode = False
        for start, end in _clauses(transcript):
            clause = transcript[start:end]
            folded = fold(clause)
            if RECAP.search(folded):
                recap_mode = True
            elif DISCOURSE_BOUNDARY.match(folded.lstrip()) and not HISTORY.search(folded) and not NEGATION.search(folded):
                recap_mode = False
            mentions = medicine_mentions(clause, self.catalog)
            if HISTORY.search(folded) or UNCERTAIN.search(folded):
                warnings.append(f"Đoạn tiền sử/giả định chưa đưa vào nháp: {clause.strip()}")
                continuation_target = None
                continue
            if NEGATION.search(folded):
                for mention in mentions:
                    items = [i for i in items if not _same_name(i, mention[0], self.catalog)]
                if not mentions:
                    warnings.append(f"Chưa xác định được thuốc trong câu phủ định/hủy: {clause.strip()}")
                continuation_target = None
                continue
            replacing = REPLACE.search(folded)
            if replacing:
                if len(mentions) >= 2:
                    target = mentions[0][0]
                    old = [i for i in items if _same_name(i, target, self.catalog)]
                    if len(old) != 1:
                        warnings.append("Lệnh đổi thuốc chưa xác định duy nhất một dòng cũ.")
                        continue
                    items.remove(old[0])
                    mentions = mentions[1:]
                elif len(mentions) == 1 and len(items) == 1:
                    items.clear()
                else:
                    warnings.append(f"Cần chỉ rõ thuốc được thay thế: {clause.strip()}")
                    continue
            if not mentions:
                spoken_strength = STRENGTH_PATTERN.search(folded)
                prescribed_unknown = re.search(
                    rf"\b(?:ke|them|(?:thuoc\s+)?thu\s+(?:nhat|nhi|tu|{NUMBER_PATTERN})\s+la)\s+(?:cho\s+(?:chi|anh|em|benh nhan)\s+)?(?:thuoc\s+)?(?P<name>[a-z][a-z -]{{1,64}}?)\s*$",
                    folded[:spoken_strength.start()],
                ) if spoken_strength else None
                if prescribed_unknown:
                    name_start, name_end = prescribed_unknown.span("name")
                    item = Item(spoken_name=clause[name_start:name_end].strip())
                    item.evidence["spoken_name"] = Evidence.at(transcript, start + name_start, start + name_end)
                    item.issues.append("Tên thuốc chưa khớp danh mục; cần kiểm tra tên và hàm lượng trước khi lưu.")
                    parse_fields(item, transcript, start + name_end, end)
                    items.append(item)
                    continuation_target = item
                    continue
                correction_target = continuation_target if continuation_target in items else items[0] if len(items) == 1 else None
                if CORRECTION.search(folded) and correction_target is not None and (QUANTITY.search(folded) or QUANTITY_CORRECTION.search(folded)):
                    parse_fields(correction_target, transcript, start, end)
                elif continuation_target is not None and continuation_target in items and CONTINUATION.search(folded):
                    parse_fields(continuation_target, transcript, start, end)
                else:
                    warnings.append(f"Chưa hiểu đoạn; cần kiểm tra thủ công: {clause.strip()}")
                    continuation_target = None
                continue
            prefix = folded[:mentions[0].start()]
            continued = continuation_target is not None and continuation_target in items and CONTINUATION.search(prefix)
            if continued:
                parse_fields(continuation_target, transcript, start, start + mentions[0].start())
            remainder = re.sub(r"\b(?:dau tien|tiep theo|benh nhan|bac si|toi|chi|anh|em|ban|la|cho|ke|them|thuoc|a|thoi|doi|thay|sang|thanh|bang)\b|[\s,:-]", "", prefix)
            if remainder and not continued and not RECAP.search(prefix):
                warnings.append(f"Có phần trước tên thuốc chưa được hiểu: {clause[:mentions[0].start()].strip()}")
            last_target = None
            for index, mention in enumerate(mentions):
                field_end = mentions[index + 1].start() if index + 1 < len(mentions) else len(clause)
                item = Item(spoken_name=clause[mention.start():mention.end()])
                item.evidence["spoken_name"] = Evidence.at(transcript, start + mention.start(), start + mention.end())
                parse_fields(item, transcript, start + mention.end(), start + field_end, recap=recap_mode)
                previous = [i for i in items if _same_name(i, item.spoken_name, self.catalog)]
                if recap_mode and len(previous) == 1:
                    merge_recap(previous[0], item)
                    last_target = previous[0]
                    continue
                # Never attach two occurrences of the same drug to one line implicitly.
                if any(_same_name(i, item.spoken_name, self.catalog) for i in items):
                    item.issues.append("Tên thuốc xuất hiện nhiều dòng; cần kiểm tra trùng/sửa chỉ định.")
                items.append(item)
                last_target = item
            continuation_target = last_target if len(mentions) == 1 else None
        for item in items:
            self.catalog.match(item)
            missing = [label for field, label in (("strength", "hàm lượng"), ("quantity", "số lượng"),
                       ("quantity_unit", "đơn vị cấp"), ("dose", "liều mỗi lần"), ("frequency", "tần suất"),
                       ("route", "đường dùng")) if getattr(item, field) is None]
            if missing:
                item.issues.append("Chưa nghe rõ/chưa nói: " + ", ".join(missing) + ".")
        return Extraction(transcript, items, warnings, self.name,
                          round((time.perf_counter() - started) * 1000, 2))
