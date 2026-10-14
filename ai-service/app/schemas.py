"""API contracts.

`InvoiceExtraction` is the contract handed to the model through OpenAI Structured
Outputs, so it follows the strict-mode rules: every field is required, optional
values are `X | None`, and there are no defaults or free-form dicts.
"""
from pydantic import BaseModel, Field


class LineItem(BaseModel):
    description: str
    quantity: float | None
    unit_price: float | None
    total: float | None


class FieldConfidence(BaseModel):
    vendor: float
    invoice_number: float
    invoice_date: float
    due_date: float
    currency: float
    line_items: float
    subtotal: float
    tax: float
    total: float


class InvoiceExtraction(BaseModel):
    vendor: str | None
    invoice_number: str | None
    invoice_date: str | None
    due_date: str | None
    currency: str | None
    line_items: list[LineItem]
    subtotal: float | None
    tax: float | None
    total: float | None
    confidence: FieldConfidence
    needs_review: bool
    review_reasons: list[str]


# ---------------------------------------------------------------- OCR ----
class OcrPage(BaseModel):
    page: int
    text: str
    confidence: float = Field(ge=0, le=1)
    method: str  # "text-layer" | "ocr"


class OcrResponse(BaseModel):
    text: str
    pages: list[OcrPage]
    page_count: int
    confidence: float = Field(ge=0, le=1)
    engine: str
    mime_type: str


# ------------------------------------------------------------ extract ----
class ExtractRequest(BaseModel):
    text: str = Field(min_length=1)


class Usage(BaseModel):
    model: str
    input_tokens: int
    output_tokens: int
    cost_usd: float
    latency_ms: int


class ExtractResponse(BaseModel):
    extraction: InvoiceExtraction
    injection_suspected: bool
    usage: Usage
