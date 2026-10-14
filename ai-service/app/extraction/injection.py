"""Heuristic prompt-injection detector. It never blocks processing on its own: a hit
forces `needs_review` and is surfaced to the backend. The real defences are the system
prompt, strict schema output, no tools, and the backend's deterministic validation."""
import re

_PATTERNS = [
    r"ignore (all |any |the )?(previous|prior|above|earlier) (instructions|prompts?|rules)",
    r"disregard (all |any |the )?(previous|prior|above|earlier|your) (instructions|prompts?|rules)",
    r"forget (everything|all|your) (above|previous|instructions)",
    r"(reveal|show|print|repeat) (me )?(your|the) (system )?(prompt|instructions)",
    r"you are now\b",
    r"\bnew instructions?\s*:",
    r"\bsystem\s*(prompt|message)\s*:",
    r"(^|\n)\s*(assistant|system)\s*:",
    r"act as (an? )?(different|new)",
    r"(set|mark|output) (the )?(total|amount|needs_review)\b.{0,40}\b(to|as)\b",
    r"игнорируй (все )?(предыдущие|прежние) (инструкции|указания)",
    r"ignoriere (alle )?(vorherigen|obigen) (anweisungen|instruktionen)",
]
_COMPILED = [re.compile(p, re.IGNORECASE) for p in _PATTERNS]


def detect_injection(text: str) -> bool:
    return any(p.search(text) for p in _COMPILED)
