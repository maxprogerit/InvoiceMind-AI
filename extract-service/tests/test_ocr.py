import io
import shutil

import pytest
from PIL import Image, ImageDraw, ImageFont
from reportlab.lib.pagesizes import A4
from reportlab.pdfgen import canvas

from .conftest import HEADERS

needs_tesseract = pytest.mark.skipif(shutil.which("tesseract") is None, reason="tesseract binary not installed")
needs_poppler = pytest.mark.skipif(shutil.which("pdftoppm") is None, reason="poppler not installed")

LINES = ["ACME GmbH", "Invoice INV-0042", "Date 2026-03-14", "Total EUR 1240.00"]


def text_pdf(lines=LINES, pages=1) -> bytes:
    buf = io.BytesIO()
    c = canvas.Canvas(buf, pagesize=A4)
    for _ in range(pages):
        y = 780
        for line in lines:
            c.setFont("Helvetica", 14)
            c.drawString(60, y, line + " - billing details and payment terms apply to this invoice")
            y -= 28
        c.showPage()
    c.save()
    return buf.getvalue()


def raster(lines=LINES) -> Image.Image:
    img = Image.new("RGB", (1240, 700), "white")
    draw = ImageDraw.Draw(img)
    font = ImageFont.load_default(size=44)
    y = 40
    for line in lines:
        draw.text((60, y), line, fill="black", font=font)
        y += 90
    return img


def png_bytes() -> bytes:
    buf = io.BytesIO()
    raster().save(buf, "PNG")
    return buf.getvalue()


def scanned_pdf_bytes() -> bytes:
    buf = io.BytesIO()
    raster().save(buf, "PDF", resolution=150)
    return buf.getvalue()


def call(client, data: bytes, name="x.bin", headers=HEADERS):
    return client.post("/ocr", files={"file": (name, data, "application/octet-stream")}, headers=headers)


def test_requires_token(client):
    assert call(client, text_pdf(), headers={}).status_code == 401


def test_text_native_pdf_uses_text_layer_without_ocr(client):
    resp = call(client, text_pdf(pages=2), "invoice.pdf")
    assert resp.status_code == 200
    body = resp.json()
    assert body["page_count"] == 2 and body["mime_type"] == "application/pdf"
    assert {p["method"] for p in body["pages"]} == {"text-layer"}
    assert "INV-0042" in body["text"] and body["confidence"] == 1.0


@needs_tesseract
def test_png_is_recognised_by_tesseract(client):
    body = call(client, png_bytes(), "scan.png").json()
    assert body["pages"][0]["method"] == "ocr" and body["engine"] == "tesseract"
    assert "INV-0042" in body["text"].replace(" ", "")
    assert "1240" in body["text"]
    assert 0.5 < body["confidence"] <= 1.0


@needs_tesseract
@needs_poppler
def test_scanned_pdf_falls_back_to_ocr(client):
    body = call(client, scanned_pdf_bytes(), "scan.pdf").json()
    assert {p["method"] for p in body["pages"]} == {"ocr"}
    assert "INV-0042" in body["text"].replace(" ", "")


def test_executable_disguised_as_pdf_is_rejected_by_content(client):
    resp = call(client, b"MZ\x90\x00" + b"\x00" * 200, "invoice.pdf")
    assert resp.status_code == 415 and resp.json()["error"]["code"] == "unsupported_type"


def test_corrupt_pdf_is_unprocessable(client):
    resp = call(client, b"%PDF-1.4\nthis is not a real pdf", "broken.pdf")
    assert resp.status_code == 422 and resp.json()["error"]["code"] == "unreadable_file"


def test_page_limit_enforced(client):
    resp = call(client, text_pdf(pages=21), "long.pdf")
    assert resp.status_code == 413 and resp.json()["error"]["code"] == "too_many_pages"


def test_size_limit_enforced(client):
    resp = call(client, b"%PDF-" + b"0" * (15 * 1024 * 1024 + 10), "huge.pdf")
    assert resp.status_code == 413 and resp.json()["error"]["code"] == "file_too_large"


def test_health_reports_engine(client):
    body = client.get("/health").json()
    assert body["ocr_engine"] == "tesseract" and "openai_configured" in body
