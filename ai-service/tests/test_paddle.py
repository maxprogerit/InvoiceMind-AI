"""PaddleOCR engine. The grouping logic is pure Python and always tested; the engine itself runs only when the
image was built with WITH_PADDLE=1 (it downloads models on first use when they are not baked in)."""
import importlib.util

import pytest
from PIL import Image, ImageDraw, ImageFont

from app.ocr.paddle_engine import group_lines
from app.ocr.registry import build_engine

from .conftest import HEADERS

has_paddle = importlib.util.find_spec("paddleocr") is not None
needs_paddle = pytest.mark.skipif(not has_paddle, reason="PaddleOCR not installed (build with WITH_PADDLE=1)")


# ------------------------------------------------------ line grouping (no OCR needed)
def box(left, top, right, bottom, text):
    return (left, top, right, bottom, text)


def test_segments_of_a_table_row_become_one_line_in_reading_order():
    boxes = [
        box(700, 100, 800, 130, "1,000.00"),     # right-most column first on purpose
        box(60, 102, 260, 131, "Consulting"),
        box(400, 99, 440, 129, "10"),
        box(520, 101, 610, 130, "100.00"),
        box(60, 160, 300, 190, "Hosting (managed)"),
        box(700, 161, 800, 191, "240.00"),
    ]
    assert group_lines(boxes) == ["Consulting  10  100.00  1,000.00", "Hosting (managed)  240.00"]


def test_lines_are_ordered_top_to_bottom_and_blank_segments_dropped():
    boxes = [box(10, 300, 200, 330, "Total EUR 1190.00"), box(10, 10, 200, 40, "ACME GmbH"), box(10, 12, 20, 38, "  ")]
    assert group_lines(boxes) == ["ACME GmbH", "Total EUR 1190.00"]


def test_no_boxes_no_lines():
    assert group_lines([]) == []


def test_slightly_tilted_rows_stay_together_but_distinct_rows_do_not_merge():
    tilted = [box(60, 100, 200, 130, "Item"), box(500, 112, 600, 142, "9.99")]  # 12 px lower at the right edge
    next_row = [box(60, 150, 200, 180, "Next"), box(500, 162, 600, 192, "1.00")]
    assert group_lines(tilted + next_row) == ["Item  9.99", "Next  1.00"]


def test_registry_explains_how_to_get_the_paddle_engine(monkeypatch):
    from app.config import Settings

    settings = Settings(ai_service_token="test-token", ocr_engine="paddle")
    if has_paddle:
        pytest.skip("covered by the real-engine tests below")
    with pytest.raises(ValueError, match="WITH_PADDLE=1"):
        build_engine(settings)


def test_unknown_engines_are_refused():
    from app.config import Settings

    with pytest.raises(ValueError, match="Unsupported"):
        build_engine(Settings(ai_service_token="test-token", ocr_engine="abbyy"))


# --------------------------------------------------------------- the real engine
@pytest.fixture(scope="module")
def engine():
    from app.ocr.paddle_engine import PaddleEngine

    return PaddleEngine("latin")


def invoice_image() -> Image.Image:
    img = Image.new("RGB", (1500, 800), "white")
    draw = ImageDraw.Draw(img)
    font = ImageFont.load_default(size=40)
    draw.text((60, 40), "ACME GmbH", fill="black", font=font)
    draw.text((60, 120), "Invoice No: INV-0042", fill="black", font=font)
    draw.text((60, 200), "Date: 2026-03-14", fill="black", font=font)
    draw.text((60, 320), "Consulting services", fill="black", font=font)
    draw.text((760, 320), "10", fill="black", font=font)
    draw.text((960, 320), "100.00", fill="black", font=font)
    draw.text((1230, 320), "1,000.00", fill="black", font=font)
    draw.text((760, 500), "Total EUR 1,190.00", fill="black", font=font)
    return img


@needs_paddle
def test_paddle_reads_a_synthetic_invoice_with_row_shaped_output(engine):
    page = engine.recognize(invoice_image())
    text = page.text
    assert "ACME" in text and "INV-0042" in text and "2026-03-14" in text and "1,190.00" in text
    row = next(line for line in text.splitlines() if "Consulting" in line)
    assert row.index("Consulting") < row.index("10") < row.index("100.00") < row.index("1,000.00")  # one line, left to right
    assert 0.7 <= page.confidence <= 1.0
    assert engine.name == "paddleocr" and engine.version()


@needs_paddle
def test_paddle_is_usable_through_the_normal_ocr_service(monkeypatch):
    import io

    from app.config import Settings
    from app.ocr.service import OcrService

    settings = Settings(ai_service_token="test-token", ocr_engine="paddle", paddle_lang="latin")
    service = OcrService(build_engine(settings), settings)
    buf = io.BytesIO()
    invoice_image().save(buf, "PNG")
    result = service.process(buf.getvalue())
    assert result.engine == "paddleocr" and result.pages[0].method == "ocr"
    assert "INV-0042" in result.text


@needs_paddle
def test_the_http_endpoint_works_with_paddle(client, monkeypatch):
    import io

    from app.main import app
    from app.ocr.service import OcrService
    from app.config import get_settings

    settings = get_settings()
    original = app.state.ocr
    app.state.ocr = OcrService(build_engine(Settings_with(settings)), settings)
    try:
        buf = io.BytesIO()
        invoice_image().save(buf, "PNG")
        resp = client.post("/ocr", files={"file": ("scan.png", buf.getvalue(), "image/png")}, headers=HEADERS)
        assert resp.status_code == 200
        assert resp.json()["engine"] == "paddleocr" and "INV-0042" in resp.json()["text"]
    finally:
        app.state.ocr = original


def Settings_with(settings):
    return settings.model_copy(update={"ocr_engine": "paddle", "paddle_lang": "latin"})
