"""TEST DOUBLE - never deployed. Used by docker-compose.e2e.yml for UI end-to-end tests.

Runs the real ai-service (real OCR: text layer + Tesseract) but swaps the OpenAI extractor for a
deterministic, regex-based one that understands the layout of the generated sample invoices.
That keeps the whole UI flow testable in CI without an API key or API cost. Real extraction quality
is measured separately by eval/run_eval.py against the real LLM.
"""
import re
from contextlib import asynccontextmanager
from datetime import date

from app import main as real
from app.extraction.postprocess import finalize
from app.schemas import ExtractResponse, FieldConfidence, InvoiceExtraction, LineItem, Usage

NUM = r"-?[\d.,]+"


def _to_float(raw: str) -> float | None:
    s = raw.strip().replace(" ", "")
    if "," in s and "." in s:
        s = s.replace(",", "") if s.rfind(".") > s.rfind(",") else s.replace(".", "").replace(",", ".")
    elif "," in s:
        s = s.replace(",", ".") if len(s.split(",")[-1]) <= 2 else s.replace(",", "")
    try:
        return float(s)
    except ValueError:
        return None


def _find(pattern: str, text: str, flags=re.IGNORECASE) -> str | None:
    m = re.search(pattern, text, flags)
    return m.group(1).strip() if m else None


class RulesExtractorForTests:
    def extract(self, text: str) -> ExtractResponse:
        lines = [ln.strip() for ln in text.splitlines() if ln.strip()]
        vendor = lines[0] if lines else None
        number = _find(r"invoice\s*(?:no|number|#)[:.]?\s*([A-Za-z0-9\-/]+)", text)
        inv_date = _find(r"^\s*(?:invoice\s+date|date|issued)[:.]?\s*(\d{4}-\d{2}-\d{2})", text, re.IGNORECASE | re.MULTILINE)
        due = _find(r"^\s*due\s+date[:.]?\s*(\d{4}-\d{2}-\d{2})", text, re.IGNORECASE | re.MULTILINE)
        ml = re.IGNORECASE | re.MULTILINE
        subtotal = _to_float(_find(rf"^\s*(?:subtotal|net|zwischensumme)\s+({NUM})\s*$", text, ml) or "")
        tax = _to_float(_find(rf"^\s*(?:vat|tax|mwst|ust)\b[^\n]*?\s({NUM})\s*$", text, ml) or "")
        total_raw = _find(rf"^\s*(?:grand\s+)?total\b\s*(?:[A-Za-z]{{3}})?\s+({NUM})\s*$", text, ml)
        total = _to_float(total_raw or "")
        currency = _find(r"^\s*(?i:total)\s+([A-Z]{3})\b", text, re.MULTILINE)

        items: list[LineItem] = []
        for ln in lines:
            m = re.match(rf"^(?:\d+\s+)?([^\d].*?)\s+({NUM})\s+({NUM})\s+({NUM})$", ln)
            if not m:
                continue
            qty, unit, tot = _to_float(m.group(2)), _to_float(m.group(3)), _to_float(m.group(4))
            # a real row satisfies quantity x unit price = total; this keeps IBANs and footers out
            if None not in (qty, unit, tot) and abs(qty * unit - tot) < 0.02:
                items.append(LineItem(description=m.group(1).strip(), quantity=qty, unit_price=unit, total=tot))

        def conf(value) -> float:
            return 0.0 if value is None else 0.9

        extraction = InvoiceExtraction(
            vendor=vendor, invoice_number=number, invoice_date=inv_date, due_date=due, currency=currency,
            line_items=items, subtotal=subtotal, tax=tax, total=total,
            confidence=FieldConfidence(vendor=conf(vendor), invoice_number=conf(number), invoice_date=conf(inv_date),
                                       due_date=conf(due), currency=conf(currency), line_items=conf(items or None),
                                       subtotal=conf(subtotal), tax=conf(tax), total=conf(total)),
            needs_review=False, review_reasons=[],
        )
        injection = bool(re.search(r"ignore (all )?previous instructions", text, re.IGNORECASE))
        final = finalize(extraction, text, injection)
        return ExtractResponse(
            extraction=final, injection_suspected=injection,
            usage=Usage(model="rules-for-tests", input_tokens=len(text) // 4, output_tokens=200, cost_usd=0.0, latency_ms=1),
        )


_original = real.app.router.lifespan_context


@asynccontextmanager
async def _lifespan(app):
    async with _original(app):
        app.state.extractor = RulesExtractorForTests()
        yield


real.app.router.lifespan_context = _lifespan
app = real.app
_ = date  # keep import used for readers extending the rules
