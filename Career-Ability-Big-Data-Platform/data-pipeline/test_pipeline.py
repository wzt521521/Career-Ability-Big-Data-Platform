"""Unit tests for the data-pipeline transformations.

These tests deliberately exercise only in-process code.  Redis and MySQL
coverage lives in ``test_pipeline_integration.py`` and is selected explicitly
with ``pytest -m integration``.
"""
from __future__ import annotations

import json
import sys
from pathlib import Path

import pytest


PIPELINE_DIR = Path(__file__).resolve().parent
if str(PIPELINE_DIR) not in sys.path:
    sys.path.insert(0, str(PIPELINE_DIR))

from etl_clean import (  # noqa: E402
    claim_batch,
    check_duplicate,
    compute_md5,
    extract_skills,
    insert_position,
    normalize_city,
    normalize_education,
    normalize_experience,
    normalize_salary,
    process_batch,
    recover_inflight_messages,
    touch_heartbeat,
)
from import_data import (  # noqa: E402
    compute_source_md5,
    enqueue_raw_job,
    parse_salary,
    read_source_file,
    row_to_json,
)
from config import build_queue_keys  # noqa: E402


class RecordingCursor:
    """Minimal DB-API cursor double for query-shape unit tests."""

    def __init__(self, result=(0,), lastrowid=42):
        self.result = result
        self.lastrowid = lastrowid
        self.calls = []

    def execute(self, statement, params=()):
        self.calls.append((statement, params))

    def fetchone(self):
        return self.result


@pytest.mark.parametrize(
    ("salary", "expected"),
    [
        (None, {"min": None, "max": None}),
        ({"min": 15, "max": 25}, {"min": 15, "max": 25}),
        ({"min": 25, "max": 15}, {"min": 15, "max": 25}),
        ({"min": 200, "max": 400}, {"min": 16, "max": 33}),
        ({"min": "invalid", "max": "data"}, {"min": None, "max": None}),
    ],
)
def test_normalize_salary(salary, expected):
    assert normalize_salary(salary) == expected


@pytest.mark.parametrize(
    ("raw", "expected"),
    [
        ("fresh graduate", "\u5e94\u5c4a"),
        ("5-10 years", "5-10\u5e74"),
        ("14 years of experience", "10\u5e74\u4ee5\u4e0a"),
        ("4 years", "3-5\u5e74"),
        (None, None),
    ],
)
def test_normalize_experience(raw, expected):
    assert normalize_experience(raw) == expected


@pytest.mark.parametrize(
    ("raw", "expected"),
    [
        ("master", "\u7855\u58eb"),
        ("bachelor", "\u672c\u79d1"),
        ("high school", "\u4e0d\u9650"),
        ("unknown", None),
        (None, None),
    ],
)
def test_normalize_education(raw, expected):
    assert normalize_education(raw) == expected


def test_normalize_city_uses_mapping_and_preserves_unknown_city():
    city_map = {"Shanghai": {"province": "Shanghai", "tier": "tier-1"}}

    assert normalize_city("Shanghai", None, city_map) == ("Shanghai", "Shanghai", "tier-1")
    assert normalize_city("Unmapped", None, city_map) == ("Unmapped", None, "\u5176\u4ed6")
    assert normalize_city(None, None, city_map) == (None, None, None)


def test_extract_skills_normalizes_aliases_and_case():
    skills = extract_skills(
        "Python engineer",
        "Build services with Spring Boot and SQL.",
        ["PYTHON"],
        {"python", "sql"},
        {"spring boot": "Spring Boot"},
    )

    assert skills == ["Python", "Spring Boot", "sql"]


def test_compute_md5_is_stable_for_a_source_identity():
    assert compute_md5("job-1", "https://example.test/jobs/1") == "222e34b46928b431e844780cb5ad86bc"
    assert compute_md5("job-1", "https://example.test/jobs/1") != compute_md5(
        "job-2", "https://example.test/jobs/1"
    )


@pytest.mark.parametrize(
    ("raw", "expected"),
    [
        ("8K-15K", {"min": 8, "max": 15}),
        ("15000", {"min": 15, "max": 15}),
        ("negotiable", {"min": 0, "max": 0}),
        ("not-a-salary", {"min": None, "max": None}),
    ],
)
def test_parse_salary(raw, expected):
    assert parse_salary(raw) == expected


def test_row_to_json_maps_a_source_row_without_io():
    row = {
        "Job Title": "Data Engineer",
        "Company": "Example Co",
        "Location": "Shanghai",
        "Salary": "12K-20K",
        "Experience": "3-5 years",
        "Qualification": "bachelor",
        "Skills": "Python|SQL",
        "source_url": "https://example.test/jobs/42",
    }
    city_map = {"Shanghai": {"province": "Shanghai", "tier": "tier-1"}}

    job = row_to_json(row, 42, city_map)

    assert job["jobId"].endswith("000042")
    assert job["title"] == "Data Engineer"
    assert job["company"]["name"] == "Example Co"
    assert job["salary"] == {"min": 12, "max": 20}
    assert job["city"] == "Shanghai"
    assert job["province"] == "Shanghai"
    assert job["cityTier"] == "tier-1"
    assert job["skills"] == ["Python", "SQL"]


def test_repository_fixture_meets_csv_to_etl_acceptance_volume():
    fixture = PIPELINE_DIR.parent / "data" / "kaggle_jobs_500.csv"
    dataframe = read_source_file(fixture)
    jobs = [
        row_to_json(row, index, {})
        for index, (_, row) in enumerate(dataframe.iterrows())
    ]
    valid = [job for job in jobs if job["title"] and job["company"]["name"]]

    assert len(dataframe) >= 500
    assert len(valid) >= 400
    assert len({compute_source_md5(job) for job in valid}) == len(valid)


def test_database_helpers_use_bound_parameters_and_json_payloads():
    cursor = RecordingCursor(result=(0,))
    job = {
        "jobId": "job-1",
        "title": "Data Engineer",
        "company": {"name": "Example Co"},
        "salary": {"min": 15, "max": 25},
        "skills": ["Python"],
        "welfare": ["Remote"],
        "sourceUrl": "https://example.test/jobs/1",
    }

    assert check_duplicate(cursor, "source-md5") is False
    insert_position(cursor, job, 42, "source-md5")

    select_statement, select_params = cursor.calls[0]
    insert_statement, insert_params = cursor.calls[1]
    assert "source_md5 = %s" in select_statement
    assert select_params == ("source-md5",)
    assert "INSERT INTO job_position" in insert_statement
    assert insert_params[0:3] == ("job-1", "Data Engineer", 42)
    assert insert_params[-1] == "source-md5"


class FakeIntegrityError(Exception):
    pass


class MemoryRedis:
    """Small Redis model for exercising queue state transitions offline."""

    def __init__(self):
        self.lists = {}
        self.sets = {}

    def lpush(self, key, *values):
        queue = self.lists.setdefault(key, [])
        for value in values:
            queue.insert(0, value)
        return len(queue)

    def rpop(self, key):
        queue = self.lists.setdefault(key, [])
        return queue.pop() if queue else None

    def rpoplpush(self, source, destination):
        value = self.rpop(source)
        if value is not None:
            self.lpush(destination, value)
        return value

    def brpoplpush(self, source, destination, timeout=0):
        del timeout
        return self.rpoplpush(source, destination)

    def lrem(self, key, count, value):
        queue = self.lists.setdefault(key, [])
        removed = 0
        for index, candidate in enumerate(list(queue)):
            if candidate == value:
                del queue[index]
                removed += 1
                if count and removed >= count:
                    break
        return removed

    def llen(self, key):
        return len(self.lists.setdefault(key, []))

    def eval(self, _script, _keys_count, queue_key, set_key, member, payload):
        members = self.sets.setdefault(set_key, set())
        if member in members:
            return 0
        members.add(member)
        self.lpush(queue_key, payload)
        return 1


class MemoryDatabase:
    def __init__(self):
        self.positions = {}
        self.job_ids = set()
        self.companies = {}
        self.next_company_id = 1
        self.commits = 0
        self.rollbacks = 0

    def cursor(self):
        return MemoryCursor(self)

    def commit(self):
        self.commits += 1

    def rollback(self):
        self.rollbacks += 1


class MemoryCursor:
    def __init__(self, database):
        self.database = database
        self.result = None
        self.lastrowid = None

    def execute(self, statement, params=()):
        compact = " ".join(statement.split()).upper()
        if compact.startswith("SELECT COUNT(*) FROM JOB_POSITION"):
            self.result = (int(params[0] in self.database.positions),)
        elif compact.startswith("SELECT ID FROM JOB_COMPANY"):
            company_id = self.database.companies.get(params[0])
            self.result = (company_id,) if company_id else None
        elif compact.startswith("INSERT INTO JOB_COMPANY"):
            name = params[0]
            self.lastrowid = self.database.next_company_id
            self.database.next_company_id += 1
            self.database.companies[name] = self.lastrowid
        elif compact.startswith("INSERT INTO JOB_POSITION"):
            source_md5 = params[-1]
            job_id = params[0]
            if source_md5 in self.database.positions or job_id in self.database.job_ids:
                raise FakeIntegrityError(1062, "duplicate key")
            self.database.positions[source_md5] = params
            self.database.job_ids.add(job_id)
        elif compact.startswith(("SAVEPOINT", "ROLLBACK TO SAVEPOINT")):
            return
        else:
            raise AssertionError(f"Unexpected statement: {statement}")

    def fetchone(self):
        return self.result

    def close(self):
        return None


def _pipeline_job(job_id="job-1", source_url="https://example.test/jobs/1"):
    return {
        "jobId": job_id,
        "title": "Data Engineer",
        "company": {"name": "Example Co"},
        "salary": {"min": 15, "max": 25},
        "city": "Shanghai",
        "skills": ["Python"],
        "sourceUrl": source_url,
    }


def test_queue_keys_are_namespaced_without_changing_production_defaults():
    keys = build_queue_keys("pytest:run-1")
    assert keys.raw == "pytest:run-1:queue:raw-job-data"
    assert keys.processing == "pytest:run-1:queue:processing-job-data"
    assert keys.failed == "pytest:run-1:queue:failed-job-data"
    assert keys.raw_dedupe == "pytest:run-1:dedupe:raw-job-data"


def test_import_enqueue_is_atomic_and_idempotent_for_source_identity():
    redis_client = MemoryRedis()
    keys = build_queue_keys("pytest:import")
    job = _pipeline_job()

    assert enqueue_raw_job(redis_client, job, keys.raw, keys.raw_dedupe) is True
    assert enqueue_raw_job(redis_client, job, keys.raw, keys.raw_dedupe) is False
    assert redis_client.llen(keys.raw) == 1
    assert json.loads(redis_client.lists[keys.raw][0])["sourceMd5"] == compute_source_md5(job)


def test_processing_batch_is_idempotent_isolates_errors_and_recovers_messages(tmp_path):
    redis_client = MemoryRedis()
    database = MemoryDatabase()
    keys = build_queue_keys("pytest:batch")
    valid_one = _pipeline_job("job-1", "https://example.test/jobs/1")
    invalid_job_id = _pipeline_job("job-1", "https://example.test/jobs/conflict")
    valid_two = _pipeline_job("job-2", "https://example.test/jobs/2")
    heartbeats = []

    redis_client.lpush(keys.raw, json.dumps(valid_one), json.dumps(invalid_job_id), "not-json", json.dumps(valid_two))
    stats = process_batch(
        database,
        redis_client,
        {"Shanghai": {"province": "Shanghai", "tier": "tier-1"}},
        {"python"},
        {},
        raw_queue=keys.raw,
        processing_queue=keys.processing,
        cleaned_queue=keys.cleaned,
        failed_queue=keys.failed,
        cleaned_dedupe_set=keys.cleaned_dedupe,
        batch_size=10,
        timeout=0,
        heartbeat=lambda: heartbeats.append("beat"),
    )

    assert (stats.success, stats.error, stats.null_discard, stats.cleaned) == (2, 1, 1, 2)
    assert len(database.positions) == 2
    assert database.commits == 1
    assert redis_client.llen(keys.processing) == 0
    assert redis_client.llen(keys.failed) == 2
    assert redis_client.llen(keys.cleaned) == 2
    assert len(heartbeats) == 4

    redis_client.lpush(keys.raw, json.dumps(valid_one))
    replay = process_batch(
        database,
        redis_client,
        {},
        set(),
        {},
        raw_queue=keys.raw,
        processing_queue=keys.processing,
        cleaned_queue=keys.cleaned,
        failed_queue=keys.failed,
        cleaned_dedupe_set=keys.cleaned_dedupe,
        timeout=0,
    )
    assert replay.duplicate == 1
    assert len(database.positions) == 2
    assert redis_client.llen(keys.cleaned) == 2

    redis_client.lpush(keys.raw, json.dumps(valid_two))
    assert len(claim_batch(redis_client, keys.raw, keys.processing, timeout=0)) == 1
    assert redis_client.llen(keys.processing) == 1
    assert recover_inflight_messages(redis_client, keys.raw, keys.processing) == 1
    assert redis_client.llen(keys.processing) == 0
    assert redis_client.llen(keys.raw) == 1

    heartbeat = tmp_path / "health" / "etl-heartbeat"
    touch_heartbeat(heartbeat)
    assert heartbeat.read_text(encoding="ascii")
