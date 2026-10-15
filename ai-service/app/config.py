from functools import lru_cache

from pydantic import Field
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=None, extra="ignore")

    # Internal auth: the backend sends this in X-Internal-Token.
    ai_service_token: str = Field(min_length=8)

    # OpenAI
    openai_api_key: str = ""
    openai_model: str = "gpt-6-luna"
    openai_reasoning_effort: str = ""  # empty -> parameter is not sent
    openai_timeout_seconds: float = 60.0
    openai_max_retries: int = 3
    openai_max_output_tokens: int = 4000
    openai_price_input_per_1m: float = 0.0
    openai_price_output_per_1m: float = 0.0
    openai_embedding_model: str = "text-embedding-3-small"
    embedding_dimensions: int = 1536  # must match vector(1536) in the backend schema
    openai_embedding_price_per_1m: float = 0.0

    # OCR
    ocr_engine: str = "tesseract"  # tesseract | paddle (the latter needs the image built with WITH_PADDLE=1)
    paddle_lang: str = "de"  # PaddleOCR language code: de (German, also reads English), en, ru (Russian), ... one model per language
    ocr_langs: str = "eng+deu+rus"
    ocr_dpi: int = 250
    min_text_chars_per_page: int = 40  # below this a PDF page is treated as scanned
    max_pages: int = 20
    max_upload_bytes: int = 15 * 1024 * 1024
    max_image_pixels: int = 60_000_000

    # Extraction input guard
    max_text_chars: int = 40_000

    log_level: str = "INFO"

    # Tracing: set TRACING_ENABLED=true and OTEL_EXPORTER_OTLP_ENDPOINT (e.g. http://jaeger:4318) to export spans
    tracing_enabled: bool = False
    otel_exporter_otlp_endpoint: str = ""


@lru_cache
def get_settings() -> Settings:
    return Settings()
