"""Content sniffing by magic bytes - the declared Content-Type/extension is never trusted."""

PDF = "application/pdf"
PNG = "image/png"
JPEG = "image/jpeg"
TIFF = "image/tiff"
WEBP = "image/webp"

ALLOWED = {PDF, PNG, JPEG, TIFF, WEBP}


def sniff_mime(data: bytes) -> str | None:
    head = data[:16]
    if head.startswith(b"%PDF-"):
        return PDF
    if head.startswith(b"\x89PNG\r\n\x1a\n"):
        return PNG
    if head.startswith(b"\xff\xd8\xff"):
        return JPEG
    if head[:4] in (b"II*\x00", b"MM\x00*"):
        return TIFF
    if head[:4] == b"RIFF" and head[8:12] == b"WEBP":
        return WEBP
    return None
