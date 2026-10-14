"""Deterministic grounding: values the model returns must be traceable to the OCR text.
This is a cheap guard against hallucination and against injected "facts"."""
import re

_WS = re.compile(r"[\s  ]+")


def normalize(text: str) -> str:
    return _WS.sub(" ", text).strip().lower()


def _alnum(text: str) -> str:
    return re.sub(r"[^0-9a-zа-яё]", "", text.lower())


def number_variants(value: float) -> set[str]:
    """Textual forms an amount commonly takes on an invoice."""
    cents = f"{abs(value):.2f}"
    whole, frac = cents.split(".")
    groups: list[str] = []
    w = whole
    while len(w) > 3:
        groups.insert(0, w[-3:])
        w = w[:-3]
    groups.insert(0, w)
    variants = {f"{whole}.{frac}", f"{whole},{frac}"}
    for sep_thousand, sep_dec in ((",", "."), (".", ","), (" ", ","), (" ", "."), ("'", "."), ("", ".")):
        variants.add(f"{sep_thousand.join(groups)}{sep_dec}{frac}")
    if frac == "00":
        variants.add(whole)
        variants.add(",".join(groups))
        variants.add(".".join(groups))
        variants.add(" ".join(groups))
    return variants


def amount_in_text(value: float | None, text: str) -> bool:
    if value is None:
        return True  # nothing to ground
    norm = normalize(text)
    for v in number_variants(value):
        if re.search(rf"(?<![0-9]){re.escape(v)}(?![0-9])", norm):
            return True
    return False


def invoice_number_in_text(number: str | None, text: str) -> bool:
    if not number:
        return True
    if normalize(number) in normalize(text):
        return True
    return _alnum(number) in _alnum(text)


def vendor_in_text(vendor: str | None, text: str) -> bool:
    if not vendor:
        return True
    norm = normalize(text)
    tokens = [t for t in re.split(r"[^\w]+", vendor.lower()) if len(t) >= 3]
    return any(t in norm for t in tokens) if tokens else normalize(vendor) in norm


def year_in_text(date_iso: str | None, text: str) -> bool:
    if not date_iso:
        return True
    return date_iso[:4] in text
