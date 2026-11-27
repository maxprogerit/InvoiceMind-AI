"""File in, validated structured JSON out. The one code path behind POST /invoices, shared with the evaluation
(eval/run_eval.py) so the measured numbers are those of the code that runs in production."""
from __future__ import annotations

from typing import Protocol

from .errors import ApiError
from .ocr.service import OcrService
from .schemas import ExtractResponse, InvoiceResult, OcrResponse
from .validation import validate_invoice


class Extractor(Protocol):
    def extract(self, text: str) -> ExtractResponse: ...


def text_source(ocr: OcrResponse) -> str:
    methods = {p.method for p in ocr.pages}
    if methods == {"text-layer"}:
        return "text_layer"
    if methods == {"ocr"}:
        return "ocr"
    return "mixed"


def process_document(data: bytes, ocr: OcrService, extractor: Extractor) -> InvoiceResult:
    """OCR (text layer or Tesseract per page) -> model extraction with grounding -> deterministic validation."""
    ocr_result = ocr.process(data)
    if not ocr_result.text.strip():
        raise ApiError(422, "no_text", "No text could be read from the document")
    extracted = extractor.extract(ocr_result.text)
    return InvoiceResult(
        source=text_source(ocr_result),
        ocr=ocr_result,
        extraction=extracted.extraction,
        validation=validate_invoice(extracted.extraction),
        injection_suspected=extracted.injection_suspected,
        usage=extracted.usage,
    )
