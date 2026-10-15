"""Compares the OCR engines on the scanned samples, without any LLM (no API key, no cost).

For every scan in samples/ it checks how much of the ground truth the OCR text still contains: invoice number, vendor,
the three amounts and every line-item description (fuzzy match). A value the OCR lost cannot be extracted by any model,
so this isolates the OCR stage. Run it in the ai-service image built with PaddleOCR:

    docker build --build-arg WITH_PADDLE=1 -t invoicemind-ai-paddle ai-service
    docker run --rm -v "$PWD/samples:/samples:ro" -v "$PWD/eval:/eval:ro" -v "$PWD/docs:/out" \
        -e PYTHONPATH=/srv -w /srv invoicemind-ai-paddle python /eval/ocr_compare.py

Writes docs/OCR-COMPARISON.md.
"""
import argparse
import difflib
import json
import re
import sys
import time
from datetime import date
from pathlib import Path

from app.config import get_settings
from app.extraction.grounding import amount_in_text, invoice_number_in_text, vendor_in_text
from app.ocr.paddle_engine import PaddleEngine
from app.ocr.service import OcrService
from app.ocr.tesseract_engine import TesseractEngine


def norm(text: str) -> str:
    return re.sub(r"[^0-9a-zа-яё]", "", str(text).casefold())


def item_found(description: str, text: str) -> bool:
    target = norm(description)
    if not target:
        return True
    haystack = norm(text)
    if target in haystack:
        return True
    window = len(target)
    best = 0.0
    step = max(1, window // 4)
    for start in range(0, max(1, len(haystack) - window + 1), step):
        best = max(best, difflib.SequenceMatcher(None, target, haystack[start:start + window]).ratio())
        if best >= 0.8:
            return True
    return False


def score(text: str, expected: dict) -> dict[str, bool | float]:
    items = expected["line_items"]
    found = [item_found(i["description"], text) for i in items]
    return {
        "invoice_number": invoice_number_in_text(expected["invoice_number"], text),
        "vendor": vendor_in_text(expected["vendor"], text),
        "subtotal": amount_in_text(abs(expected["subtotal"]), text),
        "tax": expected["tax"] == 0 or amount_in_text(abs(expected["tax"]), text),
        "total": amount_in_text(abs(expected["total"]), text),
        "items": sum(found) / len(found) if found else 1.0,
    }


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--samples", default="/samples")
    ap.add_argument("--out", default="/out")
    ap.add_argument("--paddle-lang", default="de")
    args = ap.parse_args()

    settings = get_settings()
    engines = {"tesseract": TesseractEngine(settings.ocr_langs), "paddleocr": PaddleEngine(args.paddle_lang)}
    services = {name: OcrService(engine, settings) for name, engine in engines.items()}

    scans = []
    for tf in sorted((Path(args.samples) / "ground_truth").glob("*.json")):
        gt = json.loads(tf.read_text(encoding="utf-8"))
        if gt["meta"]["kind"] == "scan":
            scans.append((tf.stem, Path(args.samples) / "invoices" / gt["meta"]["file"], gt["expected"], gt["meta"]))
    fields = ["invoice_number", "vendor", "subtotal", "tax", "total", "items"]
    rows = []
    totals = {n: {f: 0.0 for f in fields} | {"confidence": 0.0, "seconds": 0.0, "perfect": 0} for n in engines}
    for sid, path, expected, meta in scans:
        row = {"id": sid, "layout": meta["layout"]}
        for name, service in services.items():
            started = time.perf_counter()
            result = service.process(path.read_bytes())
            seconds = time.perf_counter() - started
            s = score(result.text, expected)
            row[name] = {"score": s, "confidence": result.confidence, "seconds": seconds}
            for f in fields:
                totals[name][f] += float(s[f])
            totals[name]["confidence"] += result.confidence
            totals[name]["seconds"] += seconds
            totals[name]["perfect"] += all(float(s[f]) == 1.0 for f in fields)
        rows.append(row)
        print(sid, {n: {f: round(float(v), 2) for f, v in row[n]["score"].items()} for n in engines})

    n = len(scans)
    pct = lambda x: f"{100 * x / n:.0f}%"
    md = [
        "# OCR engine comparison (measured)",
        "",
        f"Generated {date.today()} by `eval/ocr_compare.py`: **{n} scanned samples** (rotation, noise, blur; English, German), "
        "OCR only, no LLM. A cell is the share of documents in which the ground-truth value is still present in the OCR text "
        "(amounts in any common number format, invoice number ignoring punctuation, vendor by name tokens, line-item "
        "descriptions by fuzzy match >= 0.8). A value lost here cannot be recovered by the model.",
        "",
        f"Engines: Tesseract `{settings.ocr_langs}` (`{engines['tesseract'].version()}`) and PaddleOCR `{args.paddle_lang}` "
        f"(`{engines['paddleocr'].version()}`), both on CPU, same 250 dpi rendering.",
        "",
        "| Field | Tesseract | PaddleOCR |",
        "|---|---|---|",
    ]
    for f in fields:
        label = "line-item descriptions (mean share found)" if f == "items" else f.replace("_", " ")
        md.append(f"| {label} | {pct(totals['tesseract'][f])} | {pct(totals['paddleocr'][f])} |")
    md += [
        f"| **every checked value present** | **{totals['tesseract']['perfect']} / {n}** | **{totals['paddleocr']['perfect']} / {n}** |",
        f"| mean OCR confidence (engine's own, not comparable across engines) | {totals['tesseract']['confidence'] / n:.2f} | {totals['paddleocr']['confidence'] / n:.2f} |",
        f"| mean time per document | {totals['tesseract']['seconds'] / n:.1f} s | {totals['paddleocr']['seconds'] / n:.1f} s |",
        "",
        "## Per document",
        "",
        "| Sample | Layout | Tesseract: missing | PaddleOCR: missing |",
        "|---|---|---|---|",
    ]
    for r in rows:
        def missing(name):
            gone = [f for f in fields if float(r[name]["score"][f]) < 1.0]
            return ", ".join(gone) or "-"
        md.append(f"| {r['id']} | {r['layout']} | {missing('tesseract')} | {missing('paddleocr')} |")
    md += [
        "",
        "## Reading the result",
        "",
        "- Small, synthetic and clean-ish: the 16 samples were generated for this repository, so treat the numbers as "
        "indicative, not as a benchmark of either engine on real-world scans.",
        "- PaddleOCR here uses the `de` model (German, which also reads English); Russian needs `PADDLE_LANG=ru`, "
        "and the two models cannot be mixed in one pass, whereas Tesseract handles `eng+deu+rus` together.",
        "- Which engine to run is a deployment choice (`OCR_ENGINE`), the image has to be built with `WITH_PADDLE=1`.",
    ]
    out = Path(args.out) / "OCR-COMPARISON.md"
    out.write_text("\n".join(md) + "\n", encoding="utf-8")
    print("wrote", out)
    return 0


if __name__ == "__main__":
    sys.exit(main())
