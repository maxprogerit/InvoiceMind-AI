"""PaddleOCR engine (optional: install `requirements-paddle.txt`, build with WITH_PADDLE=1).

PaddleOCR detects text *segments*, not lines, so the segments of a table row ("Consulting", "10", "100.00",
"1,000.00") come back as separate boxes. They are regrouped into lines by vertical position and ordered left to
right, with a wide gap between neighbours, so the downstream extraction sees the same row-shaped text it gets from
Tesseract.
"""
import statistics
import threading

from PIL import Image, ImageOps

from .base import EnginePage


def group_lines(boxes: list[tuple[float, float, float, float, str]]) -> list[str]:
    """boxes: (left, top, right, bottom, text) -> text lines, top to bottom, segments left to right."""
    if not boxes:
        return []
    typical_height = statistics.median(b[3] - b[1] for b in boxes) or 1.0
    ordered = sorted(boxes, key=lambda b: (b[1] + b[3]) / 2)
    lines: list[list[tuple[float, float, float, float, str]]] = []
    centers: list[float] = []
    for box in ordered:
        centre = (box[1] + box[3]) / 2
        if lines and abs(centre - centers[-1]) <= 0.6 * typical_height:
            lines[-1].append(box)
            centers[-1] = statistics.fmean((b[1] + b[3]) / 2 for b in lines[-1])
        else:
            lines.append([box])
            centers.append(centre)
    out = []
    for line in lines:
        line.sort(key=lambda b: b[0])
        out.append("  ".join(b[4] for b in line if b[4].strip()))
    return [ln for ln in out if ln.strip()]


class PaddleEngine:
    name = "paddleocr"

    def __init__(self, lang: str = "latin"):
        # imported here so the service runs without the (large) package when Tesseract is the engine
        from paddleocr import PaddleOCR

        self.lang = lang
        self._lock = threading.Lock()  # one predictor, shared by the request threads
        self._ocr = PaddleOCR(
            lang=lang,
            use_doc_orientation_classify=False,
            use_doc_unwarping=False,
            use_textline_orientation=False,
            enable_mkldnn=False,  # the oneDNN CPU path fails on current Paddle builds ("ConvertPirAttribute2RuntimeAttribute")
        )

    def version(self) -> str:
        import paddleocr

        return str(getattr(paddleocr, "__version__", "unknown"))

    def recognize(self, image: Image.Image) -> EnginePage:
        import numpy as np  # a dependency of PaddleOCR, so only needed (and present) when this engine is used

        rgb = np.array(ImageOps.exif_transpose(image).convert("RGB"))
        with self._lock:
            result = self._ocr.predict(rgb)
        if not result:
            return EnginePage(text="", confidence=0.0)
        page = result[0]
        texts = list(page["rec_texts"])
        scores = [float(s) for s in page["rec_scores"]]
        polys = page["rec_polys"] if "rec_polys" in page else page["dt_polys"]
        boxes = []
        for text, poly in zip(texts, polys):
            xs, ys = [float(p[0]) for p in poly], [float(p[1]) for p in poly]
            boxes.append((min(xs), min(ys), max(xs), max(ys), text.strip()))
        confidence = (sum(scores) / len(scores)) if scores else 0.0
        return EnginePage(text="\n".join(group_lines(boxes)), confidence=round(confidence, 4))


if __name__ == "__main__":  # `python -m app.ocr.paddle_engine latin`: downloads the models at image build time
    import sys

    PaddleEngine(sys.argv[1] if len(sys.argv) > 1 else "latin")
    print("PaddleOCR models are ready")
