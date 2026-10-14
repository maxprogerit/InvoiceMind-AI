import hashlib
import logging
import time

import openai
from pydantic import ValidationError

from ..config import Settings
from ..errors import ApiError
from ..logging_setup import log
from ..schemas import ExtractResponse, InvoiceExtraction, Usage
from .injection import detect_injection
from .postprocess import finalize
from .prompt import build_system_prompt, new_nonce, wrap_document

logger = logging.getLogger(__name__)


class OpenAIExtractor:
    """Calls the OpenAI Responses API with Structured Outputs (`responses.parse` +
    `text_format`). The model gets no tools; its output is re-validated in `finalize`."""

    def __init__(self, client, settings: Settings):
        self.client = client
        self.settings = settings

    @classmethod
    def from_settings(cls, settings: Settings) -> "OpenAIExtractor | None":
        if not settings.openai_api_key:
            return None
        client = openai.OpenAI(
            api_key=settings.openai_api_key,
            timeout=settings.openai_timeout_seconds,
            max_retries=settings.openai_max_retries,  # SDK retries 408/409/429/5xx with backoff
        )
        return cls(client, settings)

    def extract(self, text: str) -> ExtractResponse:
        s = self.settings
        text = text[: s.max_text_chars]
        injection = detect_injection(text)
        nonce = new_nonce()
        kwargs: dict = {}
        if s.openai_reasoning_effort:
            kwargs["reasoning"] = {"effort": s.openai_reasoning_effort}

        started = time.perf_counter()
        try:
            response = self.client.responses.parse(
                model=s.openai_model,
                input=[
                    {"role": "system", "content": build_system_prompt(nonce)},
                    {"role": "user", "content": wrap_document(text, nonce)},
                ],
                text_format=InvoiceExtraction,
                max_output_tokens=s.openai_max_output_tokens,
                **kwargs,
            )
        except openai.APITimeoutError as exc:
            raise ApiError(504, "llm_timeout", "The language model timed out") from exc
        except openai.RateLimitError as exc:
            raise ApiError(503, "llm_rate_limited", "The language model is rate limited, retry later") from exc
        except openai.AuthenticationError as exc:
            logger.error("OpenAI authentication failed - check OPENAI_API_KEY")
            raise ApiError(503, "llm_misconfigured", "The language model credentials are invalid") from exc
        except openai.APIConnectionError as exc:
            raise ApiError(502, "llm_unreachable", "The language model could not be reached") from exc
        except openai.APIStatusError as exc:
            logger.error("OpenAI error status=%s", exc.status_code)
            raise ApiError(502, "llm_error", f"The language model returned HTTP {exc.status_code}") from exc
        except ValidationError as exc:
            raise ApiError(502, "invalid_model_output", "Model output did not match the schema") from exc
        latency_ms = int((time.perf_counter() - started) * 1000)

        parsed = self._read(response)
        usage = getattr(response, "usage", None)
        in_tok = int(getattr(usage, "input_tokens", 0) or 0)
        out_tok = int(getattr(usage, "output_tokens", 0) or 0)
        cost = (in_tok * s.openai_price_input_per_1m + out_tok * s.openai_price_output_per_1m) / 1_000_000

        final = finalize(parsed, text, injection)
        log(
            logger,
            "extraction done",
            text_chars=len(text),
            text_sha256=hashlib.sha256(text.encode()).hexdigest()[:12],
            model=s.openai_model,
            input_tokens=in_tok,
            output_tokens=out_tok,
            latency_ms=latency_ms,
            needs_review=final.needs_review,
            injection_suspected=injection,
        )
        return ExtractResponse(
            extraction=final,
            injection_suspected=injection,
            usage=Usage(
                model=s.openai_model,
                input_tokens=in_tok,
                output_tokens=out_tok,
                cost_usd=round(cost, 6),
                latency_ms=latency_ms,
            ),
        )

    @staticmethod
    def _read(response) -> InvoiceExtraction:
        if getattr(response, "status", "completed") == "incomplete":
            raise ApiError(502, "incomplete_output", "The model stopped before finishing the answer")
        for output in getattr(response, "output", []) or []:
            if getattr(output, "type", None) != "message":
                continue
            for item in output.content:
                if getattr(item, "type", None) == "refusal":
                    raise ApiError(422, "model_refused", "The model refused to process this document")
        parsed = getattr(response, "output_parsed", None)
        if parsed is None:
            raise ApiError(502, "invalid_model_output", "The model returned no structured output")
        return parsed
