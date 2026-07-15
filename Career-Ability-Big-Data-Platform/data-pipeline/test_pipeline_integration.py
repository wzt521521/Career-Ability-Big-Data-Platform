"""Opt-in service tests for the isolated Redis/MySQL data pipeline.

Run with ``python -m pytest -m integration`` only after explicitly supplying
``PIPELINE_TEST_MYSQL_DATABASE`` and ``PIPELINE_TEST_REDIS_DB``.  The suite
never truncates tables, flushes Redis, or touches the application database.
"""
from __future__ import annotations

import json
import os
import re
import sys
import uuid
from dataclasses import dataclass
from pathlib import Path

import pytest


PIPELINE_DIR = Path(__file__).resolve().parent
if str(PIPELINE_DIR) not in sys.path:
    sys.path.insert(0, str(PIPELINE_DIR))

from config import (  # noqa: E402
    MYSQL_DATABASE,
    MYSQL_HOST,
    MYSQL_PASSWORD,
    MYSQL_PORT,
    MYSQL_USER,
    REDIS_DB,
    REDIS_HOST,
    REDIS_PASSWORD,
    REDIS_PORT,
    QueueKeys,
    build_queue_keys,
)
from etl_clean import (  # noqa: E402
    claim_batch,
    compute_md5,
    process_batch,
    recover_inflight_messages,
)
from import_data import enqueue_raw_job  # noqa: E402


pytestmark = pytest.mark.integration


@dataclass
class IsolatedPipeline:
    connection: object
    redis_client: object
    keys: QueueKeys
    run_id: str


def _test_database_name() -> str:
    database = os.getenv("PIPELINE_TEST_MYSQL_DATABASE")
    if not database:
        pytest.fail("Set PIPELINE_TEST_MYSQL_DATABASE to a dedicated MySQL database.")
    if not re.fullmatch(r"[A-Za-z0-9_]+", database):
        pytest.fail("PIPELINE_TEST_MYSQL_DATABASE may contain only letters, digits, and underscores.")
    if database == MYSQL_DATABASE:
        pytest.fail("PIPELINE_TEST_MYSQL_DATABASE must not equal MYSQL_DATABASE.")
    return database


def _test_redis_db() -> int:
    configured = os.getenv("PIPELINE_TEST_REDIS_DB")
    if configured is None:
        pytest.fail("Set PIPELINE_TEST_REDIS_DB to a dedicated Redis logical database.")
    try:
        database = int(configured)
    except ValueError:
        pytest.fail("PIPELINE_TEST_REDIS_DB must be an integer.")
    if database == REDIS_DB:
        pytest.fail("PIPELINE_TEST_REDIS_DB must not equal REDIS_DB.")
    return database


def _connect_test_database(database: str):
    import pymysql

    admin_connection = pymysql.connect(
        host=MYSQL_HOST,
        port=MYSQL_PORT,
        user=MYSQL_USER,
        password=MYSQL_PASSWORD,
        charset="utf8mb4",
    )
    try:
        with admin_connection.cursor() as cursor:
            cursor.execute(
                f"CREATE DATABASE IF NOT EXISTS `{database}` "
                "CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci"
            )
        admin_connection.commit()
    finally:
        admin_connection.close()

    connection = pymysql.connect(
        host=MYSQL_HOST,
        port=MYSQL_PORT,
        user=MYSQL_USER,
        password=MYSQL_PASSWORD,
        database=database,
        charset="utf8mb4",
    )
    with connection.cursor() as cursor:
        cursor.execute(
            """
            CREATE TABLE IF NOT EXISTS job_company (
                id BIGINT AUTO_INCREMENT PRIMARY KEY,
                company_name VARCHAR(200) NOT NULL,
                company_size VARCHAR(50) DEFAULT NULL,
                industry VARCHAR(100) DEFAULT NULL,
                company_type VARCHAR(50) DEFAULT NULL,
                UNIQUE KEY uq_job_company_name (company_name)
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """
        )
        cursor.execute(
            """
            CREATE TABLE IF NOT EXISTS job_position (
                id BIGINT AUTO_INCREMENT PRIMARY KEY,
                job_id VARCHAR(100) NOT NULL UNIQUE,
                title VARCHAR(200) NOT NULL,
                company_id BIGINT DEFAULT NULL,
                salary_min INT DEFAULT NULL,
                salary_max INT DEFAULT NULL,
                city VARCHAR(50) DEFAULT NULL,
                province VARCHAR(50) DEFAULT NULL,
                city_tier VARCHAR(20) DEFAULT NULL,
                education VARCHAR(20) DEFAULT NULL,
                experience VARCHAR(20) DEFAULT NULL,
                skills JSON DEFAULT NULL,
                welfare JSON DEFAULT NULL,
                description TEXT DEFAULT NULL,
                publish_date DATE DEFAULT NULL,
                source_url VARCHAR(500) DEFAULT NULL,
                source_md5 VARCHAR(32) NOT NULL,
                UNIQUE KEY uq_job_position_source_md5 (source_md5),
                CONSTRAINT fk_job_position_company
                    FOREIGN KEY (company_id) REFERENCES job_company(id) ON DELETE SET NULL
            ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """
        )
    connection.commit()
    return connection


@pytest.fixture
def isolated_pipeline():
    import redis

    test_database = _test_database_name()
    test_redis_db = _test_redis_db()
    run_id = uuid.uuid4().hex
    prefix = os.getenv("PIPELINE_TEST_REDIS_PREFIX", "pipeline:test").strip(":")
    if not prefix:
        pytest.fail("PIPELINE_TEST_REDIS_PREFIX must not be empty.")
    keys = build_queue_keys(f"{prefix}:{run_id}")
    redis_client = redis.Redis(
        host=REDIS_HOST,
        port=REDIS_PORT,
        password=REDIS_PASSWORD,
        db=test_redis_db,
        decode_responses=True,
    )
    connection = None
    try:
        redis_client.ping()
        connection = _connect_test_database(test_database)
        yield IsolatedPipeline(connection, redis_client, keys, run_id)
    finally:
        # Only named keys for this test run are removed.  Never use FLUSHDB.
        redis_client.delete(
            keys.raw,
            keys.processing,
            keys.cleaned,
            keys.failed,
            keys.raw_dedupe,
            keys.cleaned_dedupe,
        )
        redis_client.close()
        if connection is not None:
            company_prefix = f"pytest-company-{run_id}%"
            source_prefix = f"https://pipeline.integration.test/{run_id}/%"
            try:
                with connection.cursor() as cursor:
                    cursor.execute("DELETE FROM job_position WHERE source_url LIKE %s", (source_prefix,))
                    cursor.execute("DELETE FROM job_company WHERE company_name LIKE %s", (company_prefix,))
                connection.commit()
            finally:
                connection.close()


def _job(run_id: str, number: int) -> dict:
    return {
        "jobId": f"pytest-job-{run_id}-{number}",
        "title": "Data Engineer",
        "company": {"name": f"pytest-company-{run_id}", "size": "50-99"},
        "salary": {"min": 180, "max": 300},
        "city": "Shanghai",
        "education": "bachelor",
        "experience": "5-10 years",
        "skills": ["Python"],
        "welfare": ["Remote"],
        "description": "Python and SQL data pipeline.",
        "publishDate": "2026-07-14T00:00:00",
        "sourceUrl": f"https://pipeline.integration.test/{run_id}/{number}",
    }


def _persisted_count(pipeline: IsolatedPipeline, jobs: list[dict]) -> int:
    source_md5s = [compute_md5(job["jobId"], job["sourceUrl"]) for job in jobs]
    placeholders = ",".join(["%s"] * len(source_md5s))
    with pipeline.connection.cursor() as cursor:
        cursor.execute(
            f"SELECT COUNT(*) FROM job_position WHERE source_md5 IN ({placeholders})",
            source_md5s,
        )
        return cursor.fetchone()[0]


def test_batch_transaction_idempotency_and_error_isolation(isolated_pipeline: IsolatedPipeline):
    pipeline = isolated_pipeline
    first = _job(pipeline.run_id, 1)
    second = _job(pipeline.run_id, 2)
    assert enqueue_raw_job(pipeline.redis_client, first, pipeline.keys.raw, pipeline.keys.raw_dedupe)
    assert enqueue_raw_job(pipeline.redis_client, second, pipeline.keys.raw, pipeline.keys.raw_dedupe)
    assert not enqueue_raw_job(pipeline.redis_client, first, pipeline.keys.raw, pipeline.keys.raw_dedupe)
    pipeline.redis_client.lpush(pipeline.keys.raw, "{not-json")

    result = process_batch(
        pipeline.connection,
        pipeline.redis_client,
        {"Shanghai": {"province": "Shanghai", "tier": "tier-1"}},
        {"python", "sql"},
        {},
        raw_queue=pipeline.keys.raw,
        processing_queue=pipeline.keys.processing,
        cleaned_queue=pipeline.keys.cleaned,
        failed_queue=pipeline.keys.failed,
        cleaned_dedupe_set=pipeline.keys.cleaned_dedupe,
        batch_size=10,
        timeout=1,
    )

    assert result.success == 2
    assert result.null_discard == 1
    assert result.cleaned == 2
    assert _persisted_count(pipeline, [first, second]) == 2
    assert pipeline.redis_client.llen(pipeline.keys.processing) == 0
    assert pipeline.redis_client.llen(pipeline.keys.cleaned) == 2
    assert pipeline.redis_client.llen(pipeline.keys.failed) == 1

    # Bypass import dedupe to model replay after an interrupted acknowledgment.
    pipeline.redis_client.lpush(pipeline.keys.raw, json.dumps(first))
    replay = process_batch(
        pipeline.connection,
        pipeline.redis_client,
        {},
        set(),
        {},
        raw_queue=pipeline.keys.raw,
        processing_queue=pipeline.keys.processing,
        cleaned_queue=pipeline.keys.cleaned,
        failed_queue=pipeline.keys.failed,
        cleaned_dedupe_set=pipeline.keys.cleaned_dedupe,
        timeout=1,
    )
    assert replay.duplicate == 1
    assert _persisted_count(pipeline, [first, second]) == 2
    assert pipeline.redis_client.llen(pipeline.keys.cleaned) == 2


def test_unacknowledged_processing_message_is_recovered(isolated_pipeline: IsolatedPipeline):
    pipeline = isolated_pipeline
    job = _job(pipeline.run_id, 3)
    pipeline.redis_client.lpush(pipeline.keys.raw, json.dumps(job))

    assert len(claim_batch(pipeline.redis_client, pipeline.keys.raw, pipeline.keys.processing, timeout=1)) == 1
    assert pipeline.redis_client.llen(pipeline.keys.raw) == 0
    assert pipeline.redis_client.llen(pipeline.keys.processing) == 1
    assert recover_inflight_messages(pipeline.redis_client, pipeline.keys.raw, pipeline.keys.processing) == 1
    assert pipeline.redis_client.llen(pipeline.keys.raw) == 1
    assert pipeline.redis_client.llen(pipeline.keys.processing) == 0

    result = process_batch(
        pipeline.connection,
        pipeline.redis_client,
        {},
        set(),
        {},
        raw_queue=pipeline.keys.raw,
        processing_queue=pipeline.keys.processing,
        cleaned_queue=pipeline.keys.cleaned,
        failed_queue=pipeline.keys.failed,
        cleaned_dedupe_set=pipeline.keys.cleaned_dedupe,
        timeout=1,
    )
    assert result.success == 1
    assert _persisted_count(pipeline, [job]) == 1
