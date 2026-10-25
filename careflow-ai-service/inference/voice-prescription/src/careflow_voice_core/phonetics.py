"""Conservative Vietnamese-reading similarity, not a probability of drug identity."""
from __future__ import annotations

import difflib
import re

from .text import key


def phonetic_key(text: str) -> str:
    text = re.sub(r"[^a-z]", "", key(text))
    text = text.replace("ph", "f").replace("qu", "k")
    text = re.sub(r"c(?=[eiy])", "s", text)
    text = text.replace("ch", "k").replace("c", "k").replace("x", "ks")
    text = text.translate(str.maketrans({"b": "p", "z": "s", "y": "i", "d": "t"}))
    text = re.sub(r"(.)\1+", r"\1", text)
    # Heuristic tolerance for final l/n substitutions; every phonetic match needs review.
    text = re.sub(r"l$", "n", text)
    return text


def similarity(spoken: str, name: str) -> float:
    left, right = phonetic_key(spoken), phonetic_key(name)
    if min(len(left), len(right)) < 6 or min(len(left), len(right)) / max(len(left), len(right)) < 0.65:
        return 0.0
    return difflib.SequenceMatcher(None, left, right, autojunk=False).ratio()
