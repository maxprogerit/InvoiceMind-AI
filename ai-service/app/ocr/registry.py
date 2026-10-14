from ..config import Settings
from .base import OcrEngine
from .tesseract_engine import TesseractEngine


def build_engine(settings: Settings) -> OcrEngine:
    if settings.ocr_engine == "tesseract":
        return TesseractEngine(settings.ocr_langs)
    raise ValueError(
        f"Unsupported OCR_ENGINE '{settings.ocr_engine}'. Implemented: tesseract. "
        "PaddleOCR can be added by implementing app.ocr.base.OcrEngine."
    )
