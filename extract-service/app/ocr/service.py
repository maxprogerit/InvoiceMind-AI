import io
import logging

import pdfplumber
from pdf2image import convert_from_bytes
from PIL import Image, ImageSequence, UnidentifiedImageError

from ..config import Settings
from ..errors import ApiError
from ..tracing import tracer
from ..schemas import OcrPage, OcrResponse
from .base import OcrEngine
from .sniff import ALLOWED, PDF, sniff_mime

logger = logging.getLogger(__name__)


class OcrService:
    """Text-native PDFs use their text layer; scanned pages/images go through the engine."""

    def __init__(self, engine: OcrEngine, settings: Settings):
        self.engine = engine
        self.settings = settings
        Image.MAX_IMAGE_PIXELS = settings.max_image_pixels  # decompression-bomb guard

    def process(self, data: bytes) -> OcrResponse:
        with tracer.start_as_current_span("ocr.process") as span:
            span.set_attribute("document.bytes", len(data))
            result = self._process(data)
            span.set_attribute("document.mime", result.mime_type)
            span.set_attribute("ocr.engine", result.engine)
            span.set_attribute("ocr.pages", result.page_count)
            span.set_attribute("ocr.confidence", result.confidence)
            return result

    def _process(self, data: bytes) -> OcrResponse:
        if len(data) > self.settings.max_upload_bytes:
            raise ApiError(413, "file_too_large", "File exceeds the size limit")
        mime = sniff_mime(data)
        if mime not in ALLOWED:
            raise ApiError(415, "unsupported_type", "Only PDF, PNG, JPEG, TIFF and WEBP files are accepted")
        try:
            pages = self._pdf(data) if mime == PDF else self._image(data)
        except ApiError:
            raise
        except (UnidentifiedImageError, Image.DecompressionBombError, OSError) as exc:
            raise ApiError(422, "unreadable_file", f"File could not be decoded: {type(exc).__name__}") from exc
        except Exception as exc:  # pdfminer/poppler raise many types for corrupt PDFs
            logger.warning("document decode failed", extra={"fields": {"error": type(exc).__name__}})
            raise ApiError(422, "unreadable_file", "File could not be decoded") from exc

        total_chars = sum(len(p.text) for p in pages) or 1
        confidence = sum(p.confidence * len(p.text) for p in pages) / total_chars if any(p.text for p in pages) else 0.0
        return OcrResponse(
            text="\n\n".join(p.text for p in pages).strip(),
            pages=pages,
            page_count=len(pages),
            confidence=round(confidence, 4),
            engine=self.engine.name,
            mime_type=mime,
        )

    # ------------------------------------------------------------------ pdf
    def _pdf(self, data: bytes) -> list[OcrPage]:
        pages: list[OcrPage] = []
        with pdfplumber.open(io.BytesIO(data)) as pdf:
            count = len(pdf.pages)
            if count == 0:
                raise ApiError(422, "unreadable_file", "PDF has no pages")
            if count > self.settings.max_pages:
                raise ApiError(413, "too_many_pages", f"PDF has {count} pages, limit is {self.settings.max_pages}")
            for idx, page in enumerate(pdf.pages, start=1):
                layer = (page.extract_text() or "").strip()
                if len(layer) >= self.settings.min_text_chars_per_page:
                    pages.append(OcrPage(page=idx, text=layer, confidence=1.0, method="text-layer"))
                    continue
                images = convert_from_bytes(data, dpi=self.settings.ocr_dpi, first_page=idx, last_page=idx)
                result = self.engine.recognize(images[0])
                pages.append(OcrPage(page=idx, text=result.text, confidence=result.confidence, method="ocr"))
        return pages

    # ---------------------------------------------------------------- image
    def _image(self, data: bytes) -> list[OcrPage]:
        pages: list[OcrPage] = []
        with Image.open(io.BytesIO(data)) as img:
            img.load()
            for idx, frame in enumerate(ImageSequence.Iterator(img), start=1):
                if idx > self.settings.max_pages:
                    raise ApiError(413, "too_many_pages", f"Image has more than {self.settings.max_pages} frames")
                result = self.engine.recognize(frame.copy())
                pages.append(OcrPage(page=idx, text=result.text, confidence=result.confidence, method="ocr"))
        return pages
