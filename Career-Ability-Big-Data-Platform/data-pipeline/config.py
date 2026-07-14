"""Environment-driven connection, queue, and worker settings."""
from __future__ import annotations

import os
from dataclasses import dataclass


REDIS_HOST = os.getenv("REDIS_HOST", "localhost")
REDIS_PORT = int(os.getenv("REDIS_PORT", "6379"))
REDIS_DB = int(os.getenv("REDIS_DB", "0"))
REDIS_KEY_PREFIX = os.getenv("REDIS_KEY_PREFIX", "").strip(":")

MYSQL_HOST = os.getenv("MYSQL_HOST", "localhost")
MYSQL_PORT = int(os.getenv("MYSQL_PORT", "3306"))
MYSQL_USER = os.getenv("MYSQL_USER", "root")
MYSQL_PASSWORD = os.getenv("MYSQL_PASSWORD", "root")
MYSQL_DATABASE = os.getenv("MYSQL_DATABASE", "career_ability")

DEFAULT_CSV_PATH = os.getenv("CSV_PATH", "../data/test_sample.csv")
BATCH_SIZE = int(os.getenv("BATCH_SIZE", "100"))
ETL_HEARTBEAT_FILE = os.getenv("ETL_HEARTBEAT_FILE", "/tmp/etl-heartbeat")
ETL_HEARTBEAT_INTERVAL_SECONDS = float(
    os.getenv("ETL_HEARTBEAT_INTERVAL_SECONDS", "5")
)


@dataclass(frozen=True)
class QueueKeys:
    """All Redis keys used by one isolated pipeline namespace."""

    raw: str
    processing: str
    cleaned: str
    failed: str
    raw_dedupe: str
    cleaned_dedupe: str


def build_queue_keys(prefix: str = "") -> QueueKeys:
    """Return queue and dedupe keys under ``prefix`` without changing defaults."""
    namespace = prefix.strip(":")

    def key(name: str) -> str:
        return f"{namespace}:{name}" if namespace else name

    return QueueKeys(
        raw=key("queue:raw-job-data"),
        processing=key("queue:processing-job-data"),
        cleaned=key("queue:cleaned-job-data"),
        failed=key("queue:failed-job-data"),
        raw_dedupe=key("dedupe:raw-job-data"),
        cleaned_dedupe=key("dedupe:cleaned-job-data"),
    )


QUEUE_KEYS = build_queue_keys(REDIS_KEY_PREFIX)
RAW_QUEUE = QUEUE_KEYS.raw
PROCESSING_QUEUE = QUEUE_KEYS.processing
CLEANED_QUEUE = QUEUE_KEYS.cleaned
FAILED_QUEUE = QUEUE_KEYS.failed
RAW_DEDUPE_SET = QUEUE_KEYS.raw_dedupe
CLEANED_DEDUPE_SET = QUEUE_KEYS.cleaned_dedupe
