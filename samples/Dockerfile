FROM python:3.11-slim
RUN apt-get update && apt-get install -y --no-install-recommends poppler-utils fonts-dejavu-core && rm -rf /var/lib/apt/lists/*
RUN pip install --no-cache-dir reportlab pillow pdf2image
WORKDIR /work
COPY generate.py .
CMD ["python", "generate.py"]
