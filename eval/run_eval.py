"""Accuracy, review rate, latency and cost of the /invoices pipeline on the evaluation set.

Runs in-process inside the extract-service image through `app.pipeline.process_document`, the code behind
POST /invoices (OCR -> model with grounding -> deterministic validation), with the real model:

    docker compose --profile eval run --rm eval

Documents: the synthetic set in samples/ (ground truth in samples/ground_truth/) plus, when present, real redacted
invoices in samples/real/ (same layout). Writes docs/evaluation.md and eval/results/latest.json.

Spend cap: EVAL_MAX_COST_USD (default 2.00). The run stops before the next document would exceed it, estimated
from the average cost so far, and the report says how many documents were not run. The cap needs the token prices
(OPENAI_PRICE_INPUT_PER_1M / OPENAI_PRICE_OUTPUT_PER_1M); without them every cost reads 0 and the run refuses to start.

EVAL_EXTRACTOR=rules swaps the model for the rule-based test double (no key, no cost) to check the script itself;
it writes eval/results/smoke-test.json and never the report, so made-up numbers cannot reach the documentation.
"""
import argparse
import json
import os
import sys
import time
from datetime import datetime, timezone
from decimal import Decimal, InvalidOperation
from pathlib import Path

from app.config import get_settings
from app.errors import ApiError
from app.extraction.extractor import OpenAIExtractor
from app.ocr.registry import build_engine
from app.ocr.service import OcrService
from app.pipeline import process_document
from evaluation import metrics, report


def load_set(root: Path, origin: str) -> list[dict]:
    out = []
    for tf in sorted((root / "ground_truth").glob("*.json")):
        gt = json.loads(tf.read_text(encoding="utf-8"))
        meta = gt["meta"]
        out.append({"id": tf.stem if origin == "synthetic" else f"real/{tf.stem}", "path": root / "invoices" / meta["file"],
                    "expected": gt["expected"], "tags": meta.get("tags", [meta.get("kind", "?")]),
                    "synthetic": bool(meta.get("synthetic", origin == "synthetic"))})
    return out


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--samples", default="/samples")
    ap.add_argument("--out", default="/out", help="directory that receives evaluation.md")
    ap.add_argument("--results", default="/eval/results")
    ap.add_argument("--only", default="", help="comma-separated document ids (prefix match) to run")
    args = ap.parse_args()

    settings = get_settings()
    try:
        cap = Decimal(os.environ.get("EVAL_MAX_COST_USD", "2.00"))
    except InvalidOperation:
        print("EVAL_MAX_COST_USD must be a number such as 2.00", file=sys.stderr)
        return 2
    rules = os.environ.get("EVAL_EXTRACTOR") == "rules"
    if rules:
        sys.path.insert(0, "/e2e")
        from e2e_main import RulesExtractorForTests  # noqa: PLC0415 - test double, smoke runs only

        extractor, model = RulesExtractorForTests(), "rules-for-tests"
    else:
        extractor = OpenAIExtractor.from_settings(settings)
        if extractor is None:
            print("OPENAI_API_KEY is not set: cannot evaluate the real pipeline.", file=sys.stderr)
            return 2
        if not (Decimal(str(settings.openai_price_input_per_1m)) > 0 and Decimal(str(settings.openai_price_output_per_1m)) > 0):
            print("Set OPENAI_PRICE_INPUT_PER_1M and OPENAI_PRICE_OUTPUT_PER_1M (USD per 1M tokens of the model) "
                  "so the spend cap EVAL_MAX_COST_USD can work.", file=sys.stderr)
            return 2
        model = settings.openai_model

    samples = Path(args.samples)
    items = load_set(samples, "synthetic") + (load_set(samples / "real", "real") if (samples / "real" / "ground_truth").is_dir() else [])
    if args.only:
        wanted = [w.strip() for w in args.only.split(",") if w.strip()]
        items = [i for i in items if any(i["id"].startswith(w) for w in wanted)]
    if not items:
        print(f"No ground truth found under {samples}", file=sys.stderr)
        return 2

    ocr = OcrService(build_engine(settings), settings)
    spend = metrics.SpendCap(cap)
    docs, not_run = [], []
    for item in items:
        if not spend.allows_next():
            not_run.append(item["id"])
            continue
        started = time.perf_counter()
        try:
            result = process_document(item["path"].read_bytes(), ocr, extractor).model_dump(mode="json")
            doc = metrics.score_document(item["expected"], result, time.perf_counter() - started)
            spend.record(Decimal(doc["cost_usd"]))
        except ApiError as e:
            doc = metrics.error_document(item["expected"], f"{e.code}: {e.message}", time.perf_counter() - started)
        doc |= {"id": item["id"], "tags": item["tags"], "synthetic": item["synthetic"]}
        docs.append(doc)
        verdict = "ERROR" if "error" in doc else ("FALSE-VALID" if doc["false_valid"] else ("ok" if doc["all_correct"] else "diff"))
        print(f"[{item['id']}] {verdict:<11} auto={doc['auto_validated']!s:<5} wrong={doc['wrong']} "
              f"{doc['seconds']}s ${doc['cost_usd']} triggers={doc['review_triggers']}")
    if not_run:
        print(f"\nSpend cap ${cap} reached after ${spend.spent}: {len(not_run)} document(s) not run: {', '.join(not_run)}")

    summary = metrics.summarize(docs)
    origin = ", ".join(f"{g['documents']} {k}" for k, g in summary["by_origin"].items())
    run = {"date": datetime.now(timezone.utc).strftime("%Y-%m-%d %H:%M UTC"), "model": model,
           "reasoning_effort": settings.openai_reasoning_effort or "default",
           "ocr": f"{settings.ocr_engine} ({settings.ocr_langs})", "origin": origin, "cap": str(cap),
           "price_in": str(settings.openai_price_input_per_1m), "price_out": str(settings.openai_price_output_per_1m),
           "stopped_early": bool(not_run), "not_run": len(not_run)}
    payload = {"run": run, "summary": summary, "not_run": not_run, "documents": docs}
    print(json.dumps({k: summary[k] for k in ("documents", "auto_validated", "needs_review", "false_valid", "all_correct",
                                              "avg_seconds", "avg_cost_usd", "total_cost_usd")}, indent=2))

    res_dir = Path(args.results)
    res_dir.mkdir(parents=True, exist_ok=True)
    if rules:
        (res_dir / "smoke-test.json").write_text(json.dumps(payload, indent=2, ensure_ascii=False), encoding="utf-8")
        (res_dir / "smoke-test.md").write_text("> SMOKE TEST of the script with the rule-based test double. NOT a measurement "
                                               "of the product; never copy these numbers.\n\n" + report.render(summary, docs, run),
                                               encoding="utf-8")
        print("\nSMOKE TEST with the rule-based test double: docs/evaluation.md was NOT written "
              f"(preview in {res_dir / 'smoke-test.md'}).")
        return 0
    (res_dir / "latest.json").write_text(json.dumps(payload, indent=2, ensure_ascii=False), encoding="utf-8")
    (Path(args.out) / "evaluation.md").write_text(report.render(summary, docs, run), encoding="utf-8")
    print(f"\nWrote {Path(args.out) / 'evaluation.md'} and {res_dir / 'latest.json'}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
