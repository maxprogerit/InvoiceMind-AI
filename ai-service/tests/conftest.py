import os
from types import SimpleNamespace

os.environ.setdefault("AI_SERVICE_TOKEN", "test-token")
os.environ["OPENAI_PRICE_INPUT_PER_1M"] = "0.10"
os.environ["OPENAI_PRICE_OUTPUT_PER_1M"] = "0.50"

import pytest  # noqa: E402
from fastapi.testclient import TestClient  # noqa: E402

from app.extraction.extractor import OpenAIExtractor  # noqa: E402
from app.main import app  # noqa: E402
from app.config import get_settings  # noqa: E402
from app.schemas import FieldConfidence, InvoiceExtraction, LineItem  # noqa: E402

HEADERS = {"X-Internal-Token": "test-token"}


class FakeOpenAIClientForTests:
    """Test double for openai.OpenAI. Records every `responses.parse` call and returns
    whatever `behaviour(kwargs)` produces (a response object or raises an exception)."""

    def __init__(self, behaviour):
        self.calls: list[dict] = []
        outer = self

        class _Responses:
            def parse(self, **kwargs):
                outer.calls.append(kwargs)
                return behaviour(kwargs)

        self.responses = _Responses()


def make_extraction(**overrides) -> InvoiceExtraction:
    base = dict(
        vendor="ACME GmbH",
        invoice_number="INV-0042",
        invoice_date="2026-03-14",
        due_date="2026-04-13",
        currency="EUR",
        line_items=[LineItem(description="Consulting", quantity=10, unit_price=100.0, total=1000.0)],
        subtotal=1000.0,
        tax=190.0,
        total=1190.0,
        confidence=FieldConfidence(
            vendor=0.98, invoice_number=0.99, invoice_date=0.95, due_date=0.9,
            currency=0.97, line_items=0.9, subtotal=0.95, tax=0.95, total=0.99,
        ),
        needs_review=False,
        review_reasons=[],
    )
    base.update(overrides)
    return InvoiceExtraction(**base)


def make_response(parsed, input_tokens=1000, output_tokens=200, status="completed", output=None):
    return SimpleNamespace(
        status=status,
        output=output or [],
        output_parsed=parsed,
        usage=SimpleNamespace(input_tokens=input_tokens, output_tokens=output_tokens),
    )


INVOICE_TEXT = """ACME GmbH
Rechnung / Invoice INV-0042
Date: 2026-03-14   Due: 2026-04-13
Consulting  10 x 100.00  1,000.00
Subtotal 1,000.00
VAT 19% 190.00
Total EUR 1,190.00
"""


@pytest.fixture
def client():
    with TestClient(app) as c:
        yield c


@pytest.fixture
def with_model(client):
    """Install a fake model behaviour; returns the fake client for call inspection."""

    def install(behaviour):
        fake = FakeOpenAIClientForTests(behaviour)
        app.state.extractor = OpenAIExtractor(fake, get_settings())
        return fake

    yield install
    app.state.extractor = None
