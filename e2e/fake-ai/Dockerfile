# TEST DOUBLE image: real OCR + rule-based extractor (see e2e_main.py). Build context is the repo root.
FROM python:3.11-slim
ENV PYTHONDONTWRITEBYTECODE=1 PYTHONUNBUFFERED=1
RUN apt-get update \
 && apt-get install -y --no-install-recommends tesseract-ocr tesseract-ocr-eng tesseract-ocr-deu tesseract-ocr-rus poppler-utils \
 && rm -rf /var/lib/apt/lists/*
WORKDIR /srv
COPY ai-service/requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
COPY ai-service/app ./app
COPY e2e/fake-ai/e2e_main.py .
RUN useradd --create-home --uid 10001 app
USER app
EXPOSE 8000
HEALTHCHECK --interval=15s --timeout=5s --start-period=10s --retries=5 \
  CMD python -c "import urllib.request,sys; sys.exit(0 if urllib.request.urlopen('http://127.0.0.1:8000/health', timeout=4).status==200 else 1)"
CMD ["uvicorn", "e2e_main:app", "--host", "0.0.0.0", "--port", "8000"]
