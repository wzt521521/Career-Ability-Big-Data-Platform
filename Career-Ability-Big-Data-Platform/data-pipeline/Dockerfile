FROM python:3.10.15-slim-bookworm
WORKDIR /app
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
COPY . .
HEALTHCHECK --interval=20s --timeout=5s --start-period=30s --retries=5 CMD python -c "import os, sys, time; path = os.getenv('ETL_HEARTBEAT_FILE', '/tmp/etl-heartbeat'); sys.exit(0 if os.path.exists(path) and time.time() - os.path.getmtime(path) < 90 else 1)"
CMD ["python", "etl_clean.py"]
