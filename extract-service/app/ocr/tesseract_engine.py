from collections import OrderedDict

import pytesseract
from PIL import Image, ImageOps

from .base import EnginePage


class TesseractEngine:
    name = "tesseract"

    def __init__(self, langs: str):
        self.langs = langs

    def version(self) -> str:
        return str(pytesseract.get_tesseract_version())

    @staticmethod
    def _prepare(image: Image.Image) -> Image.Image:
        img = ImageOps.exif_transpose(image).convert("L")
        if img.width < 1000:  # tiny scans OCR badly; upscale before recognition
            scale = 1000 / img.width
            img = img.resize((1000, int(img.height * scale)), Image.LANCZOS)
        return ImageOps.autocontrast(img)

    def recognize(self, image: Image.Image) -> EnginePage:
        data = pytesseract.image_to_data(
            self._prepare(image),
            lang=self.langs,
            config="--psm 6",
            output_type=pytesseract.Output.DICT,
        )
        lines: "OrderedDict[tuple[int, int, int], list[str]]" = OrderedDict()
        confs: list[float] = []
        for i, word in enumerate(data["text"]):
            word = word.strip()
            conf = float(data["conf"][i])
            if not word or conf < 0:
                continue
            key = (data["block_num"][i], data["par_num"][i], data["line_num"][i])
            lines.setdefault(key, []).append(word)
            confs.append(conf)
        text = "\n".join(" ".join(words) for words in lines.values())
        confidence = (sum(confs) / len(confs) / 100.0) if confs else 0.0
        return EnginePage(text=text, confidence=round(confidence, 4))
