"""Money and quantities: `Decimal` in Python, JSON *strings* on the wire, never binary floats.

Ported from InvoiceExtract. A JSON number is parsed as a float by most clients (0.1 + 0.2 problem), so the
schema handed to the model asks for strings like "1190.00", Pydantic turns them into `Decimal`, and the
response to the backend serialises them back as strings, which Java reads into `BigDecimal` exactly.
"""
from decimal import Decimal, InvalidOperation
from typing import Annotated, Any, Optional

from pydantic import BeforeValidator, PlainSerializer, WithJsonSchema


def to_decimal(value: Any) -> Any:
    """Normalise model output before Decimal parsing; '' and whitespace mean 'absent'."""
    if value is None:
        return None
    if isinstance(value, bool):
        return value  # let Pydantic reject it
    if isinstance(value, float):
        # Never silently keep a binary float: go through repr to get the shortest exact text.
        return Decimal(repr(value))
    if isinstance(value, int):
        return Decimal(value)
    if isinstance(value, str):
        cleaned = value.strip()
        if cleaned == "":
            return None
        try:
            parsed = Decimal(cleaned)
        except InvalidOperation:
            return value  # Pydantic raises a proper validation error
        return parsed if parsed.is_finite() else value
    return value


Money = Annotated[
    Optional[Decimal],
    BeforeValidator(to_decimal),
    PlainSerializer(lambda v: None if v is None else format(v, "f"), return_type=Optional[str], when_used="always"),
    WithJsonSchema({"type": ["string", "null"], "description": 'Plain decimal string such as "1190.00", or null.'}),
]


def usd_cost(input_tokens: int, output_tokens: int, price_in_per_1m, price_out_per_1m, places: int = 6) -> Decimal:
    """Estimated cost from token counts and per-million prices, exact to `places` decimals."""
    total = Decimal(input_tokens) * Decimal(price_in_per_1m) + Decimal(output_tokens) * Decimal(price_out_per_1m)
    return (total / Decimal(1_000_000)).quantize(Decimal(1).scaleb(-places))


# A non-null decimal (costs) serialised in plain notation: "0.00000060", never "6.0E-7".
Cost = Annotated[Decimal, PlainSerializer(lambda v: format(v, "f"), return_type=str, when_used="always")]
