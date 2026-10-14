from dataclasses import dataclass
from typing import Protocol

from PIL import Image


@dataclass(frozen=True)
class EnginePage:
    text: str
    confidence: float  # 0..1


class OcrEngine(Protocol):
    """Swap point for other OCR backends (e.g. PaddleOCR): implement this and
    register it in `app.ocr.registry.build_engine`."""

    name: str

    def recognize(self, image: Image.Image) -> EnginePage: ...

    def version(self) -> str: ...
