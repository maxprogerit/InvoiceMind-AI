"""POST /invoices: the single call the backend makes per document (file -> validated structured JSON)."""
import json

import pytest

from app.schemas import InvoiceExtraction

from .conftest import HEADERS, INVOICE_TEXT, make_extraction, make_response
from .test_ocr import png_bytes, scanned_pdf_bytes, text_pdf

requires_tesseract = pytest.mark.skipif(
    __import__("shutil").which("tesseract") is None, reason="Tesseract is not installed"
)

INVOICE_LINES = INVOICE_TEXT.strip().splitlines()


def post(client, data: bytes, name="invoice.pdf", headers=HEADERS):
    return client.post("/invoices", files={"file": (name, data)}, headers=headers)


def test_requires_the_internal_token(client):
    assert post(client, text_pdf(INVOICE_LINES), headers={}).status_code == 401
    assert post(client, text_pdf(INVOICE_LINES), headers={"X-Internal-Token": "wrong-token"}).status_code == 401


def test_without_a_model_key_it_refuses_instead_of_inventing_data(client):
    resp = post(client, text_pdf(INVOICE_LINES))
    assert resp.status_code == 503
    assert resp.json()["error"]["code"] == "llm_not_configured"


def test_text_pdf_is_extracted_and_validated_in_one_call(client, with_model):
    fake = with_model(lambda kw: make_response(make_extraction()))
    resp = post(client, text_pdf(INVOICE_LINES))
    assert resp.status_code == 200, resp.text
    body = resp.json()
    assert body["source"] == "text_layer"
    assert body["validation"] == {"status": "valid", "issues": []}
    assert body["extraction"]["total"] == "1190.00"
    assert len(fake.calls) == 1


def test_money_travels_as_decimal_strings_never_json_numbers(client, with_model):
    with_model(lambda kw: make_response(make_extraction()))
    raw = post(client, text_pdf(INVOICE_LINES)).text
    body = json.loads(raw)
    for key in ("subtotal", "tax", "total"):
        assert isinstance(body["extraction"][key], str)
    item = body["extraction"]["line_items"][0]
    assert all(isinstance(item[k], str) for k in ("quantity", "unit_price", "total"))
    assert isinstance(body["usage"]["cost_usd"], str)


def test_the_schema_given_to_the_model_asks_for_strings_for_money():
    schema = InvoiceExtraction.model_json_schema()
    assert schema["properties"]["total"]["type"] == ["string", "null"]
    line = schema["$defs"]["LineItem"]["properties"]
    assert line["unit_price"]["type"] == ["string", "null"]


def test_wrong_arithmetic_is_reported_by_code_not_by_the_model(client, with_model):
    # The model claims everything is fine; the deterministic check disagrees.
    with_model(lambda kw: make_response(make_extraction(total="1500.00", needs_review=False)))
    body = post(client, text_pdf(INVOICE_LINES)).json()
    assert body["validation"]["status"] == "needs_review"
    assert "total_mismatch" in [i["code"] for i in body["validation"]["issues"]]


def test_unsupported_file_is_rejected_before_the_model_is_called(client, with_model):
    fake = with_model(lambda kw: make_response(make_extraction()))
    resp = post(client, b"MZ\x90\x00 this is an executable", name="invoice.pdf")
    assert resp.status_code == 415
    assert fake.calls == []


@requires_tesseract
def test_scan_reports_ocr_as_its_source(client, with_model):
    with_model(lambda kw: make_response(make_extraction()))
    assert post(client, png_bytes(), name="scan.png").json()["source"] == "ocr"
    assert post(client, scanned_pdf_bytes(), name="scan.pdf").json()["source"] == "ocr"
