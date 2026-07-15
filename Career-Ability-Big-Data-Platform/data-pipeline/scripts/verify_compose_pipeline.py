"""Exercise the repository CSV through the running Compose ETL service.

Run this inside the ``python-etl`` container while its normal ``etl_clean.py``
worker remains running.  It does not delete queues, Redis sets, or MySQL rows.
"""
from __future__ import annotations

import argparse
import json
import sys
import time
from pathlib import Path


PIPELINE_DIR = Path(__file__).resolve().parents[1]
if str(PIPELINE_DIR) not in sys.path:
    sys.path.insert(0, str(PIPELINE_DIR))

from config import (  # noqa: E402
    CLEANED_DEDUPE_SET,
    MYSQL_DATABASE,
    MYSQL_HOST,
    MYSQL_PASSWORD,
    MYSQL_PORT,
    MYSQL_USER,
    RAW_DEDUPE_SET,
    REDIS_DB,
    REDIS_HOST,
    REDIS_PASSWORD,
    REDIS_PORT,
)
from import_data import compute_source_md5, import_file, load_city_mapping, read_source_file, row_to_json  # noqa: E402


def source_md5s(csv_path: Path) -> list[str]:
    dataframe = read_source_file(csv_path)
    city_map = load_city_mapping()
    hashes = []
    for index, (_, row) in enumerate(dataframe.iterrows()):
        job = row_to_json(row, index, city_map)
        if job["title"] and job["company"]["name"]:
            hashes.append(compute_source_md5(job))
    return hashes


def persisted_count(connection, hashes: list[str]) -> int:
    if not hashes:
        return 0
    placeholders = ",".join(["%s"] * len(hashes))
    with connection.cursor() as cursor:
        cursor.execute(
            f"SELECT COUNT(*) FROM job_position WHERE source_md5 IN ({placeholders})",
            hashes,
        )
        return cursor.fetchone()[0]


def cleaned_count(redis_client, hashes: list[str]) -> int:
    return sum(1 for value in hashes if redis_client.sismember(CLEANED_DEDUPE_SET, value))


def raw_dedupe_count(redis_client, hashes: list[str]) -> int:
    return sum(1 for value in hashes if redis_client.sismember(RAW_DEDUPE_SET, value))


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--csv", default="/data/kaggle_jobs_500.csv")
    parser.add_argument("--timeout-seconds", type=int, default=120)
    parser.add_argument("--poll-seconds", type=float, default=1.0)
    parser.add_argument("--min-raw", type=int, default=500)
    parser.add_argument("--min-valid", type=int, default=400)
    args = parser.parse_args()

    csv_path = Path(args.csv)
    if not csv_path.is_file():
        raise SystemExit(f"CSV fixture is not readable: {csv_path}")

    import pymysql
    import redis

    redis_client = redis.Redis(
        host=REDIS_HOST,
        port=REDIS_PORT,
        password=REDIS_PASSWORD,
        db=REDIS_DB,
        decode_responses=True,
    )
    connection = pymysql.connect(
        host=MYSQL_HOST,
        port=MYSQL_PORT,
        user=MYSQL_USER,
        password=MYSQL_PASSWORD,
        database=MYSQL_DATABASE,
        charset="utf8mb4",
        autocommit=True,
    )
    try:
        redis_client.ping()
        hashes = source_md5s(csv_path)
        import_result = import_file(csv_path, redis_client)
        raw_count = import_result["enqueued"] + import_result["duplicate"]
        raw_durable = raw_dedupe_count(redis_client, hashes)
        if raw_count < args.min_raw or raw_durable < args.min_raw:
            raise RuntimeError(
                f"raw source verification failed: rows={raw_count}, durable={raw_durable}, "
                f"expected at least {args.min_raw}"
            )

        deadline = time.monotonic() + args.timeout_seconds
        persisted = cleaned = 0
        while time.monotonic() < deadline:
            persisted = persisted_count(connection, hashes)
            cleaned = cleaned_count(redis_client, hashes)
            if persisted >= args.min_valid and cleaned >= args.min_valid:
                break
            time.sleep(args.poll_seconds)

        report = {
            "csv": str(csv_path),
            "raw_source_rows": raw_count,
            "raw_dedupe_records": raw_durable,
            "enqueued": import_result["enqueued"],
            "duplicates": import_result["duplicate"],
            "persisted_mysql_rows": persisted,
            "cleaned_messages": cleaned,
        }
        print(json.dumps(report, ensure_ascii=False, sort_keys=True))
        if persisted < args.min_valid or cleaned < args.min_valid:
            raise RuntimeError(
                f"ETL timed out: persisted={persisted}, cleaned={cleaned}, required={args.min_valid}"
            )
        return 0
    finally:
        connection.close()
        redis_client.close()


if __name__ == "__main__":
    raise SystemExit(main())
