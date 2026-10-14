"""Turns raw model output into a trustworthy InvoiceExtraction.
Everything the model said is treated as untrusted and re-checked here."""
import re
from datetime import date

from ..schemas import FieldConfidence, InvoiceExtraction
from .grounding import (
    amount_in_text,
    invoice_number_in_text,
    vendor_in_text,
    year_in_text,
)

KEY_FIELDS = ("vendor", "invoice_number", "invoice_date", "total")
LOW_CONFIDENCE = 0.6


def _clamp(value: float) -> float:
    return max(0.0, min(1.0, float(value)))


def _valid_iso_date(value: str | None) -> str | None:
    if not value:
        return None
    if not re.fullmatch(r"\d{4}-\d{2}-\d{2}", value):
        return None
    try:
        return date.fromisoformat(value).isoformat()
    except ValueError:
        return None


def finalize(raw: InvoiceExtraction, text: str, injection_suspected: bool) -> InvoiceExtraction:
    data = raw.model_copy(deep=True)
    reasons = [r.strip()[:200] for r in data.review_reasons if r.strip()][:10]
    conf = {k: _clamp(v) for k, v in data.confidence.model_dump().items()}

    for field in ("invoice_date", "due_date"):
        original = getattr(data, field)
        cleaned = _valid_iso_date(original)
        if original and cleaned is None:
            reasons.append(f"invalid_{field}_format")
            conf[field] = 0.0
        setattr(data, field, cleaned)

    if data.currency is not None:
        cur = data.currency.strip().upper()
        if re.fullmatch(r"[A-Z]{3}", cur):
            data.currency = cur
        else:
            reasons.append("invalid_currency_code")
            data.currency, conf["currency"] = None, 0.0

    for name in ("subtotal", "tax", "total"):
        value = getattr(data, name)
        if value is not None and (value < 0 or value != value or value > 1e12):
            reasons.append(f"implausible_{name}")
            setattr(data, name, None)
            conf[name] = 0.0

    # Grounding: every key value must appear in the document text.
    checks = {
        "vendor": vendor_in_text(data.vendor, text),
        "invoice_number": invoice_number_in_text(data.invoice_number, text),
        "total": amount_in_text(data.total, text),
        "subtotal": amount_in_text(data.subtotal, text),
        "tax": amount_in_text(data.tax, text),
        "invoice_date": year_in_text(data.invoice_date, text),
        "due_date": year_in_text(data.due_date, text),
    }
    for name, ok in checks.items():
        if not ok:
            reasons.append(f"ungrounded_{name}")
            conf[name] = min(conf[name], 0.2)

    if injection_suspected:
        reasons.append("prompt_injection_suspected")

    data.needs_review = (
        data.needs_review
        or injection_suspected
        or any(not ok for ok in checks.values())
        or any(conf[f] < LOW_CONFIDENCE for f in KEY_FIELDS)
        or any(getattr(data, f) is None for f in KEY_FIELDS)
    )
    data.review_reasons = list(dict.fromkeys(reasons))  # de-duplicate, keep order
    data.confidence = FieldConfidence(**conf)
    return data
