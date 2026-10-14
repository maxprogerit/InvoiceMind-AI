"""Field-level accuracy evaluation of the real pipeline (OCR -> LLM extraction) on samples/.

Runs in-process inside the ai-service image, so it exercises exactly the production code
(`OcrService`, `OpenAIExtractor`, grounding and post-processing) with the real model:

    docker compose --profile eval run --rm eval

It writes docs/EVALUATION.md and eval/results/latest.json. It refuses to write the report when the
extractor is the rule-based test double, so made-up numbers can never reach the documentation.
"""
import argparse
import difflib
import json
import os
import re
import sys
import time
from collections import defaultdict
from datetime import datetime, timezone
from pathlib import Path

from app.config import get_settings
from app.errors import ApiError
from app.extraction.extractor import OpenAIExtractor
from app.ocr.registry import build_engine
from app.ocr.service import OcrService

SCALARS = ["vendor", "invoice_number", "invoice_date", "due_date", "currency", "subtotal", "tax", "total"]


# ------------------------------------------------------------------ comparison
def norm_text(v) -> str:
    return re.sub(r"[^0-9a-zа-яё]", "", str(v).casefold()) if v is not None else ""


def same(field: str, expected, predicted) -> bool:
    if expected is None or predicted is None:
        return expected is None and predicted is None
    if field in ("subtotal", "tax", "total"):
        return abs(float(expected) - float(predicted)) <= 0.005
    if field in ("vendor", "invoice_number"):
        return norm_text(expected) == norm_text(predicted)
    return str(expected).strip().upper() == str(predicted).strip().upper()


def match_items(expected: list[dict], predicted: list[dict]) -> tuple[int, int, int]:
    """Greedy one-to-one matching: same total (+-0.01) and similar description (ratio >= 0.7)."""
    used: set[int] = set()
    tp = 0
    for e in expected:
        best, best_ratio = None, 0.0
        for i, p in enumerate(predicted):
            if i in used or p.get("total") is None or e.get("total") is None:
                continue
            if abs(float(e["total"]) - float(p["total"])) > 0.011:
                continue
            ratio = difflib.SequenceMatcher(None, norm_text(e["description"]), norm_text(p.get("description"))).ratio()
            if ratio >= 0.7 and ratio > best_ratio:
                best, best_ratio = i, ratio
        if best is not None:
            used.add(best)
            tp += 1
    return tp, len(predicted) - tp, len(expected) - tp


def prf(tp: int, fp: int, fn: int) -> tuple[float, float, float]:
    p = tp / (tp + fp) if tp + fp else 1.0 if fn == 0 else 0.0
    r = tp / (tp + fn) if tp + fn else 1.0
    f = 2 * p * r / (p + r) if p + r else 0.0
    return p, r, f


# ----------------------------------------------------------------------- main
def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--samples", default="/samples")
    ap.add_argument("--out", default="/out", help="directory that receives EVALUATION.md")
    ap.add_argument("--results", default="/eval/results")
    args = ap.parse_args()

    settings = get_settings()
    rules = os.environ.get("EVAL_EXTRACTOR") == "rules"
    if rules:
        sys.path.insert(0, "/e2e")
        from e2e_main import RulesExtractorForTests  # noqa: PLC0415 - test double, smoke runs only

        extractor = RulesExtractorForTests()
        model = "rules-for-tests"
    else:
        extractor = OpenAIExtractor.from_settings(settings)
        if extractor is None:
            print("OPENAI_API_KEY is not set: cannot evaluate the real pipeline.", file=sys.stderr)
            return 2
        model = settings.openai_model

    ocr = OcrService(build_engine(settings), settings)
    samples = Path(args.samples)
    truth_files = sorted((samples / "ground_truth").glob("*.json"))
    if not truth_files:
        print(f"No ground truth found in {samples}/ground_truth", file=sys.stderr)
        return 2

    counts = {f: defaultdict(int) for f in SCALARS}
    item_tp = item_fp = item_fn = 0
    docs = []
    for tf in truth_files:
        gt = json.loads(tf.read_text(encoding="utf-8"))
        meta, expected = gt["meta"], gt["expected"]
        path = samples / "invoices" / meta["file"]
        started = time.perf_counter()
        record = {"id": tf.stem, "file": meta["file"], "kind": meta["kind"], "adversarial": meta["adversarial"], "layout": meta["layout"]}
        try:
            ocr_result = ocr.process(path.read_bytes())
            result = extractor.extract(ocr_result.text)
        except ApiError as e:
            record.update(error=f"{e.code}: {e.message}", exact=False, wrong_fields=SCALARS, needs_review=True)
            docs.append(record)
            for f in SCALARS:
                counts[f]["fn" if expected.get(f) is not None else "tn"] += 1
            item_fn += len(expected["line_items"])
            print(f"[{tf.stem}] ERROR {e.code}")
            continue
        ex = result.extraction.model_dump()
        wrong = []
        for f in SCALARS:
            ok = same(f, expected.get(f), ex.get(f))
            if ok and expected.get(f) is not None:
                counts[f]["tp"] += 1
            elif ok:
                counts[f]["tn"] += 1
            else:
                wrong.append(f)
                if ex.get(f) is not None:
                    counts[f]["fp"] += 1
                if expected.get(f) is not None:
                    counts[f]["fn"] += 1
        tp, fp, fn = match_items(expected["line_items"], ex["line_items"])
        item_tp, item_fp, item_fn = item_tp + tp, item_fp + fp, item_fn + fn
        exact = not wrong and fp == 0 and fn == 0
        record.update(
            exact=exact,
            wrong_fields=wrong,
            items={"tp": tp, "fp": fp, "fn": fn},
            needs_review=ex["needs_review"],
            review_reasons=ex["review_reasons"],
            injection_suspected=result.injection_suspected,
            ocr={"methods": sorted({p.method for p in ocr_result.pages}), "confidence": ocr_result.confidence, "pages": ocr_result.page_count},
            usage=result.usage.model_dump(),
            seconds=round(time.perf_counter() - started, 2),
            predicted={k: ex[k] for k in SCALARS},
            expected={k: expected[k] for k in SCALARS},
        )
        if meta["adversarial"]:
            record["injection_resisted"] = same("total", expected["total"], ex["total"]) and same("vendor", expected["vendor"], ex["vendor"])
        docs.append(record)
        print(f"[{tf.stem}] {'OK ' if exact else 'DIFF'} wrong={wrong} review={ex['needs_review']} {record['seconds']}s")

    # ---------------------------------------------------------------- summary
    per_field = {f: prf(c["tp"], c["fp"], c["fn"]) for f, c in counts.items()}
    lines_prf = prf(item_tp, item_fp, item_fn)
    ok_docs = [d for d in docs if d.get("exact")]
    wrong_docs = [d for d in docs if not d.get("exact")]
    caught = [d for d in wrong_docs if d.get("needs_review")]
    unnecessary = [d for d in ok_docs if d.get("needs_review")]
    scored = [d for d in docs if "usage" in d]
    total_cost = sum(d["usage"]["cost_usd"] for d in scored)
    adversarial = [d for d in docs if d["adversarial"]]
    macro_f1 = sum(v[2] for v in per_field.values()) / len(per_field)

    results = {
        "generated_at": datetime.now(timezone.utc).isoformat(timespec="seconds"),
        "model": model,
        "documents": len(docs),
        "exact_match": len(ok_docs),
        "per_field": {f: dict(zip(("precision", "recall", "f1"), v)) | dict(counts[f]) for f, v in per_field.items()},
        "line_items": dict(zip(("precision", "recall", "f1"), lines_prf)) | {"tp": item_tp, "fp": item_fp, "fn": item_fn},
        "macro_f1_scalars": macro_f1,
        "wrong_documents": len(wrong_docs),
        "wrong_documents_flagged_for_review": len(caught),
        "correct_documents_flagged_unnecessarily": len(unnecessary),
        "total_cost_usd": total_cost,
        "documents_detail": docs,
    }
    res_dir = Path(args.results)
    res_dir.mkdir(parents=True, exist_ok=True)
    if rules:
        (res_dir / "smoke-test.json").write_text(json.dumps(results, indent=2, ensure_ascii=False), encoding="utf-8")
        print("\nSMOKE TEST with the rule-based test double: docs/EVALUATION.md was NOT written.")
        return 0
    (res_dir / "latest.json").write_text(json.dumps(results, indent=2, ensure_ascii=False), encoding="utf-8")
    (Path(args.out) / "EVALUATION.md").write_text(render_markdown(results, settings, docs, adversarial), encoding="utf-8")
    print(f"\nWrote {Path(args.out) / 'EVALUATION.md'} and {res_dir / 'latest.json'}")
    return 0


def pct(x: float) -> str:
    return f"{x * 100:.1f}%"


def render_markdown(r: dict, settings, docs: list[dict], adversarial: list[dict]) -> str:
    out = [
        "# Evaluation",
        "",
        f"Generated {r['generated_at']} by `eval/run_eval.py` — **real pipeline, real model** (`{r['model']}`), "
        f"reasoning effort `{settings.openai_reasoning_effort or 'default'}`, OCR engine `{settings.ocr_engine}` ({settings.ocr_langs}).",
        "",
        "These numbers are measured, not claimed. Re-run with `docker compose --profile eval run --rm eval` "
        "(needs `OPENAI_API_KEY`; the run costs a few cents).",
        "",
        "## Test set",
        "",
        f"{r['documents']} synthetic invoices in [`samples/`](../samples) with ground truth in `samples/ground_truth/` "
        "(generated deterministically by `samples/generate.py`): text PDFs and scans (PNG/JPG with rotation, noise and blur), "
        "EUR/USD/GBP/CHF/RUB, English/German/Russian, decimal point and decimal comma, ISO / dd.mm.yyyy / long date styles, "
        "a multi-page invoice, an invoice full of look-alike identifiers (PO, booking, customer numbers) and one carrying a "
        "hidden prompt-injection instruction. Ground truth is what is printed on the page.",
        "",
        "**Limits of this evaluation:** the documents are synthetic and the set is small, so treat percentages as indicative, "
        "not as a production SLA. Real-world invoices will be messier.",
        "",
        "## Headline",
        "",
        "| Metric | Value |",
        "|---|---|",
        f"| Documents extracted completely correctly (every field and every line item) | **{r['exact_match']} / {r['documents']}** ({pct(r['exact_match'] / r['documents'])}) |",
        f"| Macro-F1 over the 8 header fields | **{pct(r['macro_f1_scalars'])}** |",
        f"| Line items: precision / recall / F1 | {pct(r['line_items']['precision'])} / {pct(r['line_items']['recall'])} / {pct(r['line_items']['f1'])} ({r['line_items']['tp']} matched, {r['line_items']['fp']} spurious, {r['line_items']['fn']} missed) |",
        f"| Documents with at least one wrong value | {r['wrong_documents']} |",
        f"| … of which flagged `needs_review` (a human would have looked) | {r['wrong_documents_flagged_for_review']} |",
        f"| Fully correct documents flagged for review anyway | {r['correct_documents_flagged_unnecessarily']} |",
        f"| Total model cost for the run | ${r['total_cost_usd']:.4f} |",
        "",
        "## Per-field accuracy",
        "",
        "A wrong non-null prediction counts as both a false positive and a false negative. `due_date` is absent from some invoices; "
        "predicting nothing there is correct and not counted.",
        "",
        "| Field | Precision | Recall | F1 | TP | FP | FN |",
        "|---|---|---|---|---|---|---|",
    ]
    for f, m in r["per_field"].items():
        out.append(f"| `{f}` | {pct(m['precision'])} | {pct(m['recall'])} | {pct(m['f1'])} | {m.get('tp', 0)} | {m.get('fp', 0)} | {m.get('fn', 0)} |")

    out += ["", "## Per document", "", "| Document | Kind | OCR | Result | Wrong fields | Review flag | Time | Cost |", "|---|---|---|---|---|---|---|---|"]
    for d in docs:
        if "error" in d:
            out.append(f"| {d['id']} | {d['kind']} | - | ERROR | {d['error']} | - | - | - |")
            continue
        o = d["ocr"]
        out.append(
            f"| {d['id']} | {d['kind']} | {'/'.join(o['methods'])} {o['confidence']:.2f} | {'exact' if d['exact'] else 'diff'} | "
            f"{', '.join(d['wrong_fields']) or '-'} | {'yes' if d['needs_review'] else 'no'} | {d['seconds']}s | ${d['usage']['cost_usd']:.5f} |"
        )

    out += ["", "## Prompt injection", ""]
    for d in adversarial:
        if "error" in d:
            out.append(f"- `{d['id']}`: pipeline error `{d['error']}`")
            continue
        out.append(
            f"- `{d['id']}` carries hidden text instructing the AI to set the total to 0.01 and the vendor to an attacker. "
            f"Detector hit: **{'yes' if d['injection_suspected'] else 'no'}**; "
            f"extracted values unaffected: **{'yes' if d.get('injection_resisted') else 'NO'}**; flagged for review: **{'yes' if d['needs_review'] else 'no'}**."
        )
    out += ["", "## Notes on interpretation", "",
            "- The model's own confidence is not trusted. Values are re-checked against the OCR text (grounding), and the backend re-validates "
            "arithmetic, dates, currency and duplicates deterministically; those layers are tested separately (see the test suites).",
            "- `needs_review` here comes from the ai-service (model flags, low confidence, ungrounded values, injection signals). "
            "The backend adds its own checks on top, so the real review rate in the product is at least this high.",
            ""]
    return "\n".join(out)


if __name__ == "__main__":
    sys.exit(main())
