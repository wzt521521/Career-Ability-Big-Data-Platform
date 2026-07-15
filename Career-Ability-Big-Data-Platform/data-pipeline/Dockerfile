FROM python:3.10.19-slim-bookworm
WORKDIR /app
COPY requirements.txt .
RUN apt-get update \
    && apt-get upgrade -y \
    && rm -rf /var/lib/apt/lists/* \
    && python -m pip install --no-cache-dir --upgrade \
        "pip>=26.1.2" \
        "setuptools>=80.9.0" \
        "wheel>=0.46.2" \
        "jaraco.context>=6.1.0" \
    && find /usr/local/lib/python3.10/site-packages -type d \( \
        -name 'wheel-0.45.1.dist-info' -o \
        -name 'jaraco.context-5.3.0.dist-info' \
    \) -prune -exec rm -rf {} + \
    && python -m pip install --no-cache-dir -r requirements.txt \
    && python -m pip check
COPY . .
RUN useradd --system --uid 10002 --create-home --home-dir /home/app app \
    && chown -R app:app /app /home/app
USER app
HEALTHCHECK --interval=20s --timeout=5s --start-period=30s --retries=5 CMD python -c "import os, sys, time; path = os.getenv('ETL_HEARTBEAT_FILE', '/tmp/etl-heartbeat'); sys.exit(0 if os.path.exists(path) and time.time() - os.path.getmtime(path) < 90 else 1)"
CMD ["python", "etl_clean.py"]
