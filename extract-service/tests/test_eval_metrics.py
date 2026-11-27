"""The evaluation's scoring (evaluation/metrics.py), its report, and the consistency of the evaluation set.

The set checks need samples/ next to the service (repo checkout, or SAMPLES_DIR in the CI container)."""
import json
import os
from decimal import Decimal
from pathlib import Path
from types import SimpleNamespace

import pytest

from app.ocr.service import OcrService
from app.ocr.registry import build_engine
from app.config import get_settings
from app.pipeline import process_document
from app.schemas import ExtractResponse, FieldConfidence, InvoiceExtraction, LineItem, Usage
from evaluation import metrics, report

EXPECTED = {
    "document_type": "invoice", "vendor": "Nordlicht Software GmbH", "invoice_number": "NL-2026-0412",
    "invoice_date": "2026-03-14", "due_date": "2026-04-13", "currency": "EUR",
    "subtotal": "1530.00", "tax": "290.70", "total": "1820.70",
    "line_items": [
        {"description": "Consulting services March", "quantity": 12, "unit_price": "95.00", "total": "1140.00"},
        {"description": "Hosting (managed)", "quantity": 1, "unit_price": "240.00", "total": "240.00"},
        {"description": "Support retainer", "quantity": 1, "unit_price": "150.00", "total": "150.00"},
    ],
}


def result(status="valid", needs_review=False, injection=False, confidence=0.95, cost="0.001200", **fields) -> dict:
    """A /invoices result dumped as JSON (money as strings), equal to EXPECTED unless overridden."""
    extraction = {k: v for k, v in EXPECTED.items()} | {"vendor_tax_id": None, "needs_review": needs_review, "review_reasons": [],
                                                        "confidence": {k: confidence for k in FieldConfidence.model_fields}}
    extraction |= fields
    issues = [] if status == "valid" else [{"code": "total_mismatch", "severity": "error", "field": "total", "message": "x"}]
    return {"source": "text_layer", "extraction": extraction, "validation": {"status": status, "issues": issues},
            "injection_suspected": injection,
            "usage": {"model": "m", "input_tokens": 1000, "output_tokens": 200, "cost_usd": cost, "latency_ms": 1500}}


# --------------------------------------------------------------- field comparison
def test_money_is_compared_as_decimal_to_the_cent():
    assert metrics.field_correct("total", "1820.70", "1820.7")
    assert metrics.field_correct("total", 1820.7, "1820.70")  # old float ground truth is read through its text
    assert not metrics.field_correct("total", "1820.70", "1820.71")
    assert not metrics.field_correct("total", "0.30", None)


def test_text_ignores_case_spaces_and_punctuation_but_not_characters():
    assert metrics.field_correct("vendor", "Nordlicht Software GmbH", "NORDLICHT SOFTWARE GMBH.")
    assert metrics.field_correct("invoice_number", "NL-2026-0412", "NL 2026 0412")
    assert not metrics.field_correct("invoice_number", "NL-2026-0412", "NL-2026-0421")


def test_absent_value_is_right_only_when_nothing_was_printed():
    assert metrics.field_correct("due_date", None, None)
    assert not metrics.field_correct("due_date", None, "2026-04-13")
    assert not metrics.field_correct("due_date", "2026-04-13", None)


def test_line_items_match_one_to_one_by_total_and_description():
    predicted = [{"description": "Consulting services March", "total": "1140.00"},
                 {"description": "Consulting services March", "total": "1140.00"},  # duplicate row: extra
                 {"description": "Hosting managed", "total": "240.00"}]
    assert metrics.match_items(EXPECTED["line_items"], predicted) == (2, 1, 1)
    assert metrics.match_items(EXPECTED["line_items"], [{"description": "Something else", "total": "150.00"}]) == (0, 1, 3)


# --------------------------------------------------------------- review decision
def test_clean_valid_result_is_auto_validated():
    assert metrics.review_triggers(result()) == []


@pytest.mark.parametrize("kwargs, expected", [
    ({"status": "needs_review"}, "validation needs_review (total_mismatch)"),
    ({"needs_review": True}, "model asked for review"),
    ({"injection": True}, "prompt injection suspected"),
    ({"confidence": 0.5}, "low confidence: vendor, invoice_number, invoice_date, total"),
])
def test_each_backend_review_trigger_sends_to_review(kwargs, expected):
    assert expected in metrics.review_triggers(result(**kwargs))


def test_wrong_value_that_passes_every_check_is_a_false_valid():
    doc = metrics.score_document(EXPECTED, result(total="1820.07", tax="290.07"), 2.0)
    assert doc["auto_validated"] and doc["false_valid"]
    assert doc["wrong"] == ["tax", "total"]


def test_wrong_value_caught_by_validation_is_not_a_false_valid():
    doc = metrics.score_document(EXPECTED, result(status="needs_review", total="1820.07"), 2.0)
    assert not doc["auto_validated"] and not doc["false_valid"] and doc["wrong"] == ["total"]


def test_correct_document_is_all_correct():
    doc = metrics.score_document(EXPECTED, result(), 1.234)
    assert doc["all_correct"] and doc["auto_validated"] and not doc["false_valid"]
    assert doc["seconds"] == 1.23 and doc["cost_usd"] == "0.001200"


def test_pipeline_error_counts_every_value_wrong_and_every_row_missed():
    doc = metrics.error_document(EXPECTED, "no_text: nothing", 0.5)
    assert not doc["all_correct"] and not doc["auto_validated"] and not doc["false_valid"]
    assert doc["line_items"]["fn"] == 3


# --------------------------------------------------------------- summary
def _docs():
    ok = metrics.score_document(EXPECTED, result(cost="0.001000"), 2.0) | {"id": "a", "tags": ["text-pdf"], "synthetic": True}
    false_valid = metrics.score_document(EXPECTED, result(total="9.99", cost="0.002000"), 4.0) | {"id": "b", "tags": ["scan"], "synthetic": True}
    caught = metrics.score_document(EXPECTED, result(status="needs_review", cost="0.003000"), 6.0) | {"id": "c", "tags": ["scan"], "synthetic": False}
    failed = metrics.error_document(EXPECTED, "x", 1.0) | {"id": "d", "tags": ["scan"], "synthetic": True}
    return [ok, false_valid, caught, failed]


def test_summary_reports_the_requested_numbers():
    s = metrics.summarize(_docs())
    assert s["documents"] == 4 and s["processed"] == 3 and s["errors"] == 1
    assert s["auto_validated"] == 2 and s["auto_validated_share"] == 0.5
    assert s["needs_review"] == 2
    assert s["false_valid"] == 1 and s["false_valid_ids"] == ["b"]
    assert s["field_accuracy"]["total"] == 0.5  # a and c right, b wrong, d failed
    assert s["field_accuracy"]["vendor"] == 0.75
    assert s["field_accuracy"]["line_items"] == 0.75
    assert s["wrong_documents"] == 2 and s["wrong_documents_caught"] == 1
    assert s["correct_documents_sent_to_review"] == 1
    assert s["total_cost_usd"] == "0.006000" and s["avg_cost_usd"] == "0.002000"  # averaged over processed documents
    assert s["avg_seconds"] == 4.0
    assert s["by_tag"]["scan"] == {"documents": 3, "all_correct": 1, "auto_validated": 1, "false_valid": 1}
    assert s["by_origin"]["real"]["documents"] == 1


def test_report_lists_every_requested_metric_and_names_false_valid_documents():
    docs = _docs()
    md = report.render(metrics.summarize(docs), docs, {
        "date": "2026-11-27 10:00 UTC", "model": "some-model", "reasoning_effort": "default", "ocr": "tesseract",
        "origin": "3 synthetic, 1 real", "cap": "2.00", "price_in": "1", "price_out": "2", "stopped_early": True, "not_run": 5})
    for needle in ("2026-11-27", "`some-model`", "Auto-validated", "False \"valid\"", "| **1** (b) |", "Average latency",
                   "Average cost per invoice | $0.002000", "spend cap stopped this run early", "5 document(s)"):
        assert needle in md, needle
    for field in metrics.FIELDS + ["line_items"]:
        assert f"| `{field}` |" in md


def test_spend_cap_stops_before_the_next_document_would_cross_it():
    cap = metrics.SpendCap(Decimal("0.10"))
    assert cap.allows_next()
    for _ in range(3):
        cap.record(Decimal("0.03"))
    assert not cap.allows_next()  # 0.09 spent, the next one is expected at 0.03
    cap = metrics.SpendCap(Decimal("0.10"))
    cap.record(Decimal("0.03"))
    cap.record(Decimal("0.03"))
    assert cap.allows_next()


# --------------------------------------------------------------- the evaluation set itself
def _samples_dir() -> Path | None:
    env = os.environ.get("SAMPLES_DIR")
    for candidate in ([Path(env)] if env else []) + [Path(__file__).resolve().parents[2] / "samples"]:
        if (candidate / "ground_truth").is_dir():
            return candidate
    return None


SAMPLES = _samples_dir()
needs_samples = pytest.mark.skipif(SAMPLES is None, reason="samples/ not available (set SAMPLES_DIR)")


@needs_samples
def test_evaluation_set_has_at_least_30_labelled_documents_covering_the_hard_cases():
    files = sorted((SAMPLES / "ground_truth").glob("*.json"))
    assert len(files) >= 30
    tags: dict[str, int] = {}
    for f in files:
        meta = json.loads(f.read_text(encoding="utf-8"))["meta"]
        assert meta["synthetic"] is True, f.name
        assert (SAMPLES / "invoices" / meta["file"]).is_file(), meta["file"]
        for t in meta["tags"]:
            tags[t] = tags.get(t, 0) + 1
    for required in ("multi-page", "scan", "scanned-pdf", "multi-tax", "discount"):
        assert tags.get(required, 0) >= 2, (required, tags)


@needs_samples
def test_ground_truth_money_is_text_and_adds_up_like_the_printed_page():
    for f in sorted((SAMPLES / "ground_truth").glob("*.json")):
        gt = json.loads(f.read_text(encoding="utf-8"))
        e = gt["expected"]
        for k in ("subtotal", "tax", "total"):
            assert isinstance(e[k], str), (f.name, k)
        lines = sum((Decimal(li["total"]) for li in e["line_items"]), Decimal(0))
        for li in e["line_items"]:
            assert Decimal(li["unit_price"]) * li["quantity"] == Decimal(li["total"]), (f.name, li)
        assert Decimal(e["subtotal"]) + Decimal(e["tax"]) == Decimal(e["total"]), f.name
        if "discount" in gt["meta"]["tags"] and lines != Decimal(e["subtotal"]):
            assert f.stem == "24-discount-totals"  # the one discount printed only in the totals block
        else:
            assert lines == Decimal(e["subtotal"]), f.name


class _TruthExtractor:
    """Returns the ground truth as the model's answer: isolates OCR + validation + scoring from the model."""

    def __init__(self, expected: dict):
        self.expected = expected

    def extract(self, text: str) -> ExtractResponse:
        e = self.expected
        extraction = InvoiceExtraction(
            document_type=e["document_type"], vendor=e["vendor"], vendor_tax_id=None, invoice_number=e["invoice_number"],
            invoice_date=e["invoice_date"], due_date=e["due_date"], currency=e["currency"],
            line_items=[LineItem(**li) for li in e["line_items"]], subtotal=e["subtotal"], tax=e["tax"], total=e["total"],
            confidence=FieldConfidence(**{k: 0.95 for k in FieldConfidence.model_fields}), needs_review=False, review_reasons=[])
        usage = Usage(model="truth", input_tokens=len(text) // 4, output_tokens=100, cost_usd=Decimal("0.000100"), latency_ms=1)
        return ExtractResponse(extraction=extraction, injection_suspected=False, usage=usage)


@needs_samples
@pytest.mark.parametrize("doc_id, auto", [("19-multitax-de", True), ("22-discount-line-usd", True), ("24-discount-totals", False)])
def test_pipeline_scores_a_perfect_answer_as_correct_and_validation_decides_review(doc_id, auto):
    gt = json.loads((SAMPLES / "ground_truth" / f"{doc_id}.json").read_text(encoding="utf-8"))
    ocr = OcrService(build_engine(get_settings()), get_settings())
    res = process_document((SAMPLES / "invoices" / gt["meta"]["file"]).read_bytes(), ocr, _TruthExtractor(gt["expected"]))
    doc = metrics.score_document(gt["expected"], res.model_dump(mode="json"), 0.1)
    assert doc["all_correct"]
    assert doc["auto_validated"] is auto, doc["review_triggers"]
