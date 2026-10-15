from ..config import Settings
from .base import OcrEngine
from .tesseract_engine import TesseractEngine


def build_engine(settings: Settings) -> OcrEngine:
    if settings.ocr_engine == "tesseract":
        return TesseractEngine(settings.ocr_langs)
    if settings.ocr_engine == "paddle":
        try:
            from .paddle_engine import PaddleEngine
        except ImportError as exc:
            raise ValueError(
                "OCR_ENGINE=paddle needs PaddleOCR: build the ai-service image with WITH_PADDLE=1 "
                "(or pip install -r requirements-paddle.txt)"
            ) from exc
        return PaddleEngine(settings.paddle_lang)
    raise ValueError(f"Unsupported OCR_ENGINE '{settings.ocr_engine}'. Implemented: tesseract, paddle.")
