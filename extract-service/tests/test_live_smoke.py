"""Live smoke test against the real OpenAI API. Skipped unless both are set:

    LIVE_OPENAI_SMOKE=1  OPENAI_API_KEY=sk-...  pytest -q tests/test_live_smoke.py

Everything else in this suite uses a fake model; this one test spends real tokens (one small invoice, a few
thousand tokens) to prove that the schema is accepted by the API and the answer survives validation.
"""
import os

import pytest

from app.config import Settings
from app.extraction.extractor import OpenAIExtractor
from app.validation import validate_invoice

from .conftest import INVOICE_TEXT

pytestmark = pytest.mark.skipif(
    os.environ.get("LIVE_OPENAI_SMOKE") != "1" or not os.environ.get("OPENAI_API_KEY"),
    reason="live smoke test: set LIVE_OPENAI_SMOKE=1 and OPENAI_API_KEY",
)


def test_real_model_extracts_the_sample_invoice_exactly():
    settings = Settings(extract_service_token="live-smoke-token", openai_api_key=os.environ["OPENAI_API_KEY"],
                        openai_model=os.environ.get("OPENAI_MODEL", "gpt-5.5"))
    result = OpenAIExtractor.from_settings(settings).extract(INVOICE_TEXT)
    ex = result.extraction
    assert ex.vendor and "ACME" in ex.vendor
    assert ex.invoice_number == "INV-0042"
    assert ex.currency == "EUR"
    assert str(ex.total) in {"1190", "1190.0", "1190.00"}
    assert validate_invoice(ex).status.value == "valid"
    assert result.usage.input_tokens > 0
