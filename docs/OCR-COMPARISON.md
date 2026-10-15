# OCR engine comparison (measured)

Generated 2026-10-15 by `eval/ocr_compare.py`: **5 scanned samples** (rotation, noise, blur; English, German), OCR only, no LLM. A cell is the share of documents in which the ground-truth value is still present in the OCR text (amounts in any common number format, invoice number ignoring punctuation, vendor by name tokens, line-item descriptions by fuzzy match >= 0.8). A value lost here cannot be recovered by the model.

Engines: Tesseract `eng+deu+rus` (`5.5.0`) and PaddleOCR `de` (`3.7.0`), both on CPU, same 250 dpi rendering.

| Field | Tesseract | PaddleOCR |
|---|---|---|
| invoice number | 100% | 100% |
| vendor | 100% | 100% |
| subtotal | 100% | 100% |
| tax | 100% | 100% |
| total | 100% | 100% |
| line-item descriptions (mean share found) | 100% | 100% |
| **every checked value present** | **5 / 5** | **5 / 5** |
| mean OCR confidence (engine's own, not comparable across engines) | 0.87 | 0.99 |
| mean time per document | 0.8 s | 31.1 s |

## Per document

| Sample | Layout | Tesseract: missing | PaddleOCR: missing |
|---|---|---|---|
| 08-classic-eur-scan | classic | - | - |
| 09-modern-usd-scan | modern | - | - |
| 10-german-scan | german | - | - |
| 14-low-quality-scan | modern | - | - |
| 16-german-scan-2 | classic | - | - |

## Reading the result

- Small, synthetic and clean-ish: the 16 samples were generated for this repository, so treat the numbers as indicative, not as a benchmark of either engine on real-world scans.
- PaddleOCR here uses the `de` model (German, which also reads English); Russian needs `PADDLE_LANG=ru`, and the two models cannot be mixed in one pass, whereas Tesseract handles `eng+deu+rus` together.
- Which engine to run is a deployment choice (`OCR_ENGINE`), the image has to be built with `WITH_PADDLE=1`.
- **What the numbers do and do not say:** on these five scans both engines found every value, so the set is too easy to tell
  them apart on accuracy. The one clear difference is speed: PaddleOCR on CPU needed about 40 times longer per document
  (the mean includes the first call's warm-up), and the image grows by roughly a gigabyte. A harder, real-world scan set
  would be needed to decide on accuracy; Tesseract stays the default for that reason.
- Found while integrating: PaddleOCR's oneDNN CPU path fails on this build ("ConvertPirAttribute2RuntimeAttribute"), so the
  engine runs with `enable_mkldnn=False`; and `lang` takes language codes (`de`, `en`, `ru` ...), not script families.
