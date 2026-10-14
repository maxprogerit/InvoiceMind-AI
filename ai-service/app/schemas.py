"""API contracts.

`InvoiceExtraction` is the contract handed to the model through OpenAI Structured
Outputs, so it follows the strict-mode rules: every field is required, optional
values are `X | None`, and there are no defaults or free-form dicts.
"""
from typing import Literal

from pydantic import BaseModel, Field

DocumentType = Literal["invoice", "credit_note"]


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
    document_type: DocumentType
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


# ---------------------------------------------------------------- knowledge / RAG ----
class EmbedRequest(BaseModel):
    texts: list[str] = Field(min_length=1, max_length=64)


class EmbedResponse(BaseModel):
    vectors: list[list[float]]
    model: str
    dimensions: int
    input_tokens: int
    cost_usd: float


class Passage(BaseModel):
    id: int
    title: str = Field(max_length=300)
    text: str = Field(max_length=4000)


class ExplainRequest(BaseModel):
    question: str = Field(min_length=1, max_length=1000)
    facts: str = Field(max_length=6000)  # what the system knows about the invoice (validation issues etc.)
    passages: list[Passage] = Field(max_length=10)


class ExplainResult(BaseModel):
    """Structured-output contract for the model: strict-mode friendly (no defaults)."""

    answer: str
    cited_passage_ids: list[int]
    insufficient_context: bool


class ExplainResponse(BaseModel):
    answer: str
    citations: list[int]
    insufficient_context: bool
    injection_suspected: bool
    usage: Usage
