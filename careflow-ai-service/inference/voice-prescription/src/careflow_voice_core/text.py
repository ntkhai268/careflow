from __future__ import annotations

import math
import re
import unicodedata


def fold(text: str) -> str:
    # Vietnamese precomposed characters preserve their offsets when accents are removed.
    return "".join(
        ch for ch in unicodedata.normalize("NFD", text.lower().replace("đ", "d"))
        if unicodedata.category(ch) != "Mn"
    )


def key(text: str) -> str:
    return " ".join(re.findall(r"[a-z0-9µ]+", fold(text)))


NUMBER_WORDS = {
    "khong": 0, "mot": 1, "hai": 2, "ba": 3, "bon": 4, "tu": 4,
    "nam": 5, "lam": 5, "sau": 6, "bay": 7, "tam": 8, "chin": 9,
}
NUMBER_PATTERN = r"(?:\d+(?:[.,]\d+)?|(?:khong|mot|hai|ba|bon|tu|nam|lam|sau|bay|tam|chin|muoi|tram|nghin|ngan|linh|le|phay)(?:\s+(?:khong|mot|hai|ba|bon|tu|nam|lam|sau|bay|tam|chin|muoi|tram|nghin|ngan|linh|le|phay))*)"


def number(text: str) -> float:
    text = fold(text).strip()
    if re.fullmatch(r"\d+(?:[.,]\d+)?", text):
        value = float(text.replace(",", "."))
        if not math.isfinite(value):
            raise ValueError("Giá trị số quá lớn.")
        return value
    if " phay " in text:
        left, right = text.split(" phay ", 1)
        digits = right.split()
        if not digits or any(x not in NUMBER_WORDS for x in digits):
            raise ValueError("Số thập phân không rõ.")
        return number(left) + float("0." + "".join(str(NUMBER_WORDS[x]) for x in digits))
    total = group = current = 0
    seen = False
    pending_digit = False
    for token in text.split():
        if token in NUMBER_WORDS:
            if pending_digit:
                raise ValueError("Chuỗi chữ số chưa rõ nghĩa.")
            current = NUMBER_WORDS[token]
            pending_digit = True
            seen = True
        elif token == "muoi":
            group += (current if pending_digit else 1) * 10
            current = 0
            pending_digit = False
            seen = True
        elif token == "tram":
            if not pending_digit:
                raise ValueError("Thiếu chữ số trước hàng trăm.")
            group += current * 100
            current = 0
            pending_digit = False
            seen = True
        elif token in ("nghin", "ngan"):
            total += (group + current or 1) * 1000
            group = current = 0
            pending_digit = False
        elif token not in ("linh", "le"):
            raise ValueError("Số không rõ.")
    if not seen:
        raise ValueError("Không có số.")
    return float(total + group + current)


UNIT_PATTERN = r"(?:miligam|miligram|mi\s*li\s*gam|microgam|microgram|mcg|µg|mg|gam|g|mililit|milil[it]+|mi\s*li\s*lit|ml|iu)"


def unit(text: str) -> str:
    value = key(text).replace(" ", "")
    return {
        "miligam": "mg", "miligram": "mg", "microgam": "mcg", "microgram": "mcg",
        "µg": "mcg", "gam": "g", "mililit": "ml", "mililít": "ml",
    }.get(value, value)


STRENGTH_PATTERN = re.compile(
    rf"(?<!\w)({NUMBER_PATTERN})\s*({UNIT_PATTERN})(?!\w)"
    rf"(?:\s*[/+]\s*({NUMBER_PATTERN})?\s*({UNIT_PATTERN})(?!\w))?"
)


def strength(text: str) -> str | None:
    matched = STRENGTH_PATTERN.search(fold(text))
    if not matched:
        return None
    result = f"{number(matched[1]):g} {unit(matched[2])}"
    if matched[4]:
        separator = "/" if "/" in matched[0] else " + "
        denominator = f"{number(matched[3]):g} " if matched[3] else ""
        result += separator + denominator + unit(matched[4])
    return result
