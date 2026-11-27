"""Scoring of one pipeline result against ground truth, and the summary over a run.

Pure functions on plain dicts: an `InvoiceResult` dumped with `model_dump(mode="json")` (money as strings) and the
`expected` block of a ground-truth file (money as strings; floats are accepted and read via their text). Money is
compared as Decimal, never as float.

Definitions used in docs/evaluation.md:

* field accuracy: share of documents whose value equals the ground truth (text fields compared case-, space- and
  punctuation-insensitively; money exactly to the cent; dates and currency exactly). A pipeline error counts as wrong.
* line items: exact when every printed row is matched one-to-one (same total to the cent and similar description)
  and nothing extra was extracted.
* auto-validated: the document would go through without a human, i.e. none of the review triggers the backend
  applies to a fresh extraction fired: the service's deterministic validation is not `valid`, the model asked for
  review, prompt injection was suspected, or a key field has confidence below 0.6. (The backend additionally holds
  invoices of unverified vendors and possible duplicates; both depend on the organisation's history, not on the
  document, and are left out here.)
* false valid: auto-validated although at least one scored value is wrong. The number that matters most: those
  errors reach approval without anyone looking.
"""
from __future__ import annotations

import difflib
import re
from dataclasses import dataclass
from decimal import Decimal, InvalidOperation
from typing import Any, Iterable

# The fields the evaluation reports on, in report order. line_items is scored separately.
FIELDS = ["vendor", "invoice_number", "invoice_date", "currency", "subtotal", "tax", "total"]
# Also scored (a wrong value makes the document wrong) but not part of the headline table.
EXTRA_FIELDS = ["document_type", "due_date"]
MONEY_FIELDS = {"subtotal", "tax", "total"}
TEXT_FIELDS = {"vendor", "invoice_number"}

# Mirrors the backend (InvoiceValidator.KEY_CONFIDENCE_FIELDS / LOW_CONFIDENCE_THRESHOLD).
KEY_CONFIDENCE_FIELDS = ("vendor", "invoice_number", "invoice_date", "total")
LOW_CONFIDENCE = 0.6

CENT = Decimal("0.01")


def dec(value: Any) -> Decimal | None:
    if value is None or value == "":
        return None
    try:
        return Decimal(str(value))
    except InvalidOperation:
        return None


def norm_text(value: Any) -> str:
    return re.sub(r"[^0-9a-zа-яёäöüß]", "", str(value).casefold()) if value is not None else ""


def field_correct(field: str, expected: Any, predicted: Any) -> bool:
    if expected is None or predicted is None:
        return expected is None and predicted is None
    if field in MONEY_FIELDS:
        e, p = dec(expected), dec(predicted)
        return e is not None and p is not None and abs(e - p) < CENT
    if field in TEXT_FIELDS:
        return norm_text(expected) == norm_text(predicted)
    return str(expected).strip().upper() == str(predicted).strip().upper()


def match_items(expected: list[dict], predicted: list[dict]) -> tuple[int, int, int]:
    """Greedy one-to-one matching: same total (to the cent) and description similarity >= 0.7. Returns tp, fp, fn."""
    used: set[int] = set()
    tp = 0
    for e in expected:
        et = dec(e.get("total"))
        best, best_ratio = None, 0.0
        for i, p in enumerate(predicted):
            pt = dec(p.get("total"))
            if i in used or et is None or pt is None or abs(et - pt) >= CENT:
                continue
            ratio = difflib.SequenceMatcher(None, norm_text(e.get("description")), norm_text(p.get("description"))).ratio()
            if ratio >= 0.7 and ratio > best_ratio:
                best, best_ratio = i, ratio
        if best is not None:
            used.add(best)
            tp += 1
    return tp, len(predicted) - tp, len(expected) - tp


def review_triggers(result: dict) -> list[str]:
    """Why a person would have to look at this result; empty means auto-validated."""
    reasons = []
    status = result["validation"]["status"]
    if status != "valid":
        codes = sorted({i["code"] for i in result["validation"]["issues"] if i.get("severity") == "error"})
        reasons.append(f"validation {status}" + (f" ({', '.join(codes)})" if codes else ""))
    ex = result["extraction"]
    if ex.get("needs_review"):
        reasons.append("model asked for review")
    if result.get("injection_suspected"):
        reasons.append("prompt injection suspected")
    low = [f for f in KEY_CONFIDENCE_FIELDS if float(ex.get("confidence", {}).get(f, 1.0)) < LOW_CONFIDENCE]
    if low:
        reasons.append("low confidence: " + ", ".join(low))
    return reasons


def score_document(expected: dict, result: dict, seconds: float) -> dict:
    """Compares one result with its ground truth."""
    ex = result["extraction"]
    fields = {f: field_correct(f, expected.get(f), ex.get(f)) for f in FIELDS + EXTRA_FIELDS}
    tp, fp, fn = match_items(expected.get("line_items", []), ex.get("line_items", []))
    line_items_exact = fp == 0 and fn == 0
    wrong = [f for f, ok in fields.items() if not ok] + ([] if line_items_exact else ["line_items"])
    triggers = review_triggers(result)
    auto = not triggers
    return {
        "fields": fields,
        "line_items": {"tp": tp, "fp": fp, "fn": fn, "exact": line_items_exact},
        "wrong": wrong,
        "all_correct": not wrong,
        "auto_validated": auto,
        "review_triggers": triggers,
        "false_valid": auto and bool(wrong),
        "seconds": round(seconds, 2),
        "model_ms": result["usage"]["latency_ms"],
        "cost_usd": str(dec(result["usage"]["cost_usd"]) or Decimal(0)),
        "input_tokens": result["usage"]["input_tokens"],
        "output_tokens": result["usage"]["output_tokens"],
        "source": result["source"],
        "validation": result["validation"]["status"],
        "predicted": {f: ex.get(f) for f in FIELDS + EXTRA_FIELDS},
    }


def error_document(expected: dict, message: str, seconds: float) -> dict:
    """A document the pipeline could not process: every field counts as wrong, every row as missed, nothing was
    auto-validated (the backend marks it failed and a person handles it)."""
    return {"error": message, "fields": {f: False for f in FIELDS + EXTRA_FIELDS},
            "line_items": {"tp": 0, "fp": 0, "fn": len(expected.get("line_items", [])), "exact": False}, "wrong": FIELDS + EXTRA_FIELDS + ["line_items"],
            "all_correct": False, "auto_validated": False, "review_triggers": ["pipeline error"], "false_valid": False,
            "seconds": round(seconds, 2), "model_ms": 0, "cost_usd": "0", "input_tokens": 0, "output_tokens": 0}


def _share(part: int, whole: int) -> float:
    return part / whole if whole else 0.0


def _prf(tp: int, fp: int, fn: int) -> dict:
    p = tp / (tp + fp) if tp + fp else (1.0 if fn == 0 else 0.0)
    r = tp / (tp + fn) if tp + fn else 1.0
    return {"precision": p, "recall": r, "f1": 2 * p * r / (p + r) if p + r else 0.0, "tp": tp, "fp": fp, "fn": fn}


def _group(docs: list[dict]) -> dict:
    n = len(docs)
    return {"documents": n, "all_correct": sum(d["all_correct"] for d in docs),
            "auto_validated": sum(d["auto_validated"] for d in docs), "false_valid": sum(d["false_valid"] for d in docs)}


def summarize(docs: list[dict]) -> dict:
    """docs: score_document / error_document results, each with `id`, `tags` and `synthetic` added."""
    n = len(docs)
    processed = [d for d in docs if "error" not in d]
    accuracy = {f: _share(sum(d["fields"][f] for d in docs), n) for f in FIELDS}
    accuracy["line_items"] = _share(sum(d["line_items"]["exact"] for d in docs), n)
    items = _prf(sum(d["line_items"]["tp"] for d in docs), sum(d["line_items"]["fp"] for d in docs),
                 sum(d["line_items"]["fn"] for d in docs))
    expected_rows = items["tp"] + items["fn"]
    auto = [d for d in docs if d["auto_validated"]]
    wrong = [d for d in docs if not d["all_correct"]]
    costs = [dec(d["cost_usd"]) or Decimal(0) for d in processed]
    total_cost = sum(costs, Decimal(0))
    tags = sorted({t for d in docs for t in d.get("tags", [])})
    return {
        "documents": n,
        "processed": len(processed),
        "errors": n - len(processed),
        "field_accuracy": accuracy,
        "extra_field_accuracy": {f: _share(sum(d["fields"][f] for d in docs), n) for f in EXTRA_FIELDS},
        "line_item_rows": items | {"expected_rows": expected_rows},
        "all_correct": sum(d["all_correct"] for d in docs),
        "auto_validated": len(auto),
        "auto_validated_share": _share(len(auto), n),
        "needs_review": n - len(auto),
        "needs_review_share": _share(n - len(auto), n),
        "false_valid": sum(d["false_valid"] for d in docs),
        "false_valid_ids": [d["id"] for d in docs if d["false_valid"]],
        "wrong_documents": len(wrong),
        "wrong_documents_caught": sum(not d["auto_validated"] for d in wrong),
        "correct_documents_sent_to_review": sum(d["all_correct"] and not d["auto_validated"] for d in docs),
        "avg_seconds": _share(sum(d["seconds"] for d in processed), len(processed)),
        "avg_model_seconds": _share(sum(d["model_ms"] for d in processed), len(processed)) / 1000,
        "max_seconds": max((d["seconds"] for d in processed), default=0.0),
        "total_cost_usd": str(total_cost),
        "avg_cost_usd": str((total_cost / len(processed)).quantize(Decimal("0.000001")) if processed else Decimal(0)),
        "by_tag": {t: _group([d for d in docs if t in d.get("tags", [])]) for t in tags},
        "by_origin": {k: _group(v) for k, v in (("synthetic", [d for d in docs if d.get("synthetic", True)]),
                                                ("real", [d for d in docs if not d.get("synthetic", True)])) if v},
    }


@dataclass
class SpendCap:
    """Stops the run before the next document would push the spend past the cap (estimated from the average so far)."""

    cap_usd: Decimal
    spent: Decimal = Decimal(0)
    runs: int = 0

    def record(self, cost: Decimal) -> None:
        self.spent += cost
        self.runs += 1

    def allows_next(self) -> bool:
        expected_next = self.spent / self.runs if self.runs else Decimal(0)
        return self.spent + expected_next <= self.cap_usd


def tag_counts(docs: Iterable[dict]) -> dict[str, int]:
    counts: dict[str, int] = {}
    for d in docs:
        for t in d.get("tags", []):
            counts[t] = counts.get(t, 0) + 1
    return dict(sorted(counts.items()))
