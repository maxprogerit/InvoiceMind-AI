FROM python:3.11-slim AS base
ENV PYTHONDONTWRITEBYTECODE=1 PYTHONUNBUFFERED=1
RUN apt-get update \
 && apt-get install -y --no-install-recommends \
      tesseract-ocr tesseract-ocr-eng tesseract-ocr-deu tesseract-ocr-rus poppler-utils \
 && rm -rf /var/lib/apt/lists/*
WORKDIR /srv
COPY requirements.txt requirements-paddle.txt ./
RUN pip install --no-cache-dir -r requirements.txt

# Optional second OCR engine:  docker build --build-arg WITH_PADDLE=1 ...   (adds ~1 GB; OCR_ENGINE=paddle selects it)
ARG WITH_PADDLE=0
RUN if [ "$WITH_PADDLE" = "1" ]; then \
      apt-get update \
      && apt-get install -y --no-install-recommends libgl1 libglib2.0-0 libgomp1 \
      && rm -rf /var/lib/apt/lists/* \
      && pip install --no-cache-dir -r requirements-paddle.txt; \
    fi

FROM base AS runtime
RUN useradd --create-home --uid 10001 app
COPY app ./app
USER app
# Fetch the PaddleOCR models while building, so the container needs no internet at run time.
ARG WITH_PADDLE=0
ARG PADDLE_LANG=de
ENV PADDLE_PDX_DISABLE_MODEL_SOURCE_CHECK=True
RUN if [ "$WITH_PADDLE" = "1" ]; then python -m app.ocr.paddle_engine "$PADDLE_LANG"; fi
EXPOSE 8000
HEALTHCHECK --interval=15s --timeout=5s --start-period=10s --retries=5 \
  CMD python -c "import urllib.request,sys; sys.exit(0 if urllib.request.urlopen('http://127.0.0.1:8000/health', timeout=4).status==200 else 1)"
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000"]
