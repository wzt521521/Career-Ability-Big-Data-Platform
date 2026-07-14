"""Long-running, recoverable Redis-to-MySQL ETL worker."""
from __future__ import annotations

import hashlib
import json
import re
import signal
import time
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Callable, Mapping

from config import (
    BATCH_SIZE,
    CLEANED_DEDUPE_SET,
    CLEANED_QUEUE,
    ETL_HEARTBEAT_FILE,
    ETL_HEARTBEAT_INTERVAL_SECONDS,
    FAILED_QUEUE,
    MYSQL_DATABASE,
    MYSQL_HOST,
    MYSQL_PASSWORD,
    MYSQL_PORT,
    MYSQL_USER,
    PROCESSING_QUEUE,
    RAW_QUEUE,
    REDIS_DB,
    REDIS_HOST,
    REDIS_PORT,
)


PIPELINE_DIR = Path(__file__).resolve().parent
LOG_INTERVAL = 100
CLEANED_ENQUEUE_SCRIPT = """
local inserted = redis.call('SADD', KEYS[2], ARGV[1])
if inserted == 1 then
    redis.call('LPUSH', KEYS[1], ARGV[2])
end
return inserted
"""


@dataclass
class BatchStats:
    claimed: int = 0
    success: int = 0
    duplicate: int = 0
    null_discard: int = 0
    error: int = 0
    cleaned: int = 0


def load_city_mapping() -> dict[str, dict[str, str]]:
    try:
        with (PIPELINE_DIR / "city_mapping.json").open("r", encoding="utf-8") as source:
            return json.load(source)
    except FileNotFoundError:
        print("[WARN] city_mapping.json not found; city enrichment is disabled")
        return {}


def load_skill_dict() -> tuple[set[str], dict[str, str]]:
    try:
        with (PIPELINE_DIR / "skill_dict.json").open("r", encoding="utf-8") as source:
            data = json.load(source)
    except FileNotFoundError:
        print("[WARN] skill_dict.json not found; skill extraction is disabled")
        return set(), {}

    keywords = {
        str(keyword).lower()
        for category in data.get("categories", {}).values()
        for keyword in category
    }
    aliases = {str(alias).lower(): str(target) for alias, target in data.get("aliases", {}).items()}
    return keywords, aliases


def normalize_salary(salary_obj: Mapping[str, Any] | None) -> dict[str, int | None]:
    if not salary_obj:
        return {"min": None, "max": None}
    try:
        low = int(salary_obj.get("min")) if salary_obj.get("min") is not None else None
        high = int(salary_obj.get("max")) if salary_obj.get("max") is not None else None
    except (TypeError, ValueError):
        return {"min": None, "max": None}
    if low and low > 80:
        low //= 12
    if high and high > 80:
        high //= 12
    if low is not None and high is not None and low > high:
        low, high = high, low
    return {"min": low, "max": high}


def normalize_city(
    city_name: Any, province_name: Any, city_map: Mapping[str, Mapping[str, str]]
) -> tuple[str | None, str | None, str | None]:
    raw = city_name or province_name
    if not raw:
        return None, None, None
    raw = str(raw).strip()
    if raw in city_map:
        mapped = city_map[raw]
        return raw, mapped.get("province"), mapped.get("tier")
    simple = raw.replace("\u5e02", "").replace("City", "").strip()
    if simple in city_map:
        mapped = city_map[simple]
        return simple, mapped.get("province"), mapped.get("tier")
    with_city = raw + "\u5e02"
    if with_city in city_map:
        mapped = city_map[with_city]
        return with_city, mapped.get("province"), mapped.get("tier")
    return raw, None, "\u5176\u4ed6"


def normalize_education(raw: Any) -> str | None:
    if not raw:
        return None
    value = str(raw).strip().lower()
    if any(token in value for token in ("\u535a\u58eb", "phd", "doctorate", "doctoral")):
        return "\u535a\u58eb"
    if any(token in value for token in ("\u7855\u58eb", "master", "msc", "mba", "graduate")):
        return "\u7855\u58eb"
    if any(token in value for token in ("\u672c\u79d1", "bachelor", "bs", "ba", "undergraduate")):
        return "\u672c\u79d1"
    if any(token in value for token in ("\u5927\u4e13", "associate", "college", "diploma", "\u4e13\u79d1")):
        return "\u5927\u4e13"
    if any(token in value for token in ("\u9ad8\u4e2d", "\u4e2d\u4e13", "high school", "secondary", "\u4e0d\u9650", "any")):
        return "\u4e0d\u9650"
    return None


def normalize_experience(raw: Any) -> str | None:
    if not raw:
        return None
    value = str(raw).strip().lower()
    if any(
        token in value
        for token in ("\u5e94\u5c4a", "fresh", "graduate", "\u6bd5\u4e1a\u751f", "\u5b9e\u4e60", "intern", "\u65e0\u7ecf\u9a8c", "entry")
    ):
        return "\u5e94\u5c4a"
    if any(token in value for token in ("\u4e0d\u9650", "any", "\u7ecf\u9a8c\u4e0d\u9650")):
        return "\u4e0d\u9650"
    if any(token in value for token in ("5-10", "\u4e94\u5230\u5341", "5\u523010")):
        return "5-10\u5e74"
    if any(token in value for token in ("3-5", "\u4e09\u5230\u4e94", "3\u52305", "\u4e09\u81f3\u4e94")):
        return "3-5\u5e74"
    if any(token in value for token in ("1-3", "\u4e00\u5230\u4e09", "1\u52303", "\u4e00\u81f3\u4e09")):
        return "1-3\u5e74"
    if "\u5341\u5e74" in value or any(token in value for token in ("senior", "ten")):
        return "10\u5e74\u4ee5\u4e0a"
    if "\u4e94\u5e74" in value:
        return "5-10\u5e74"
    if "\u4e09\u5e74" in value or "\u56db\u5e74" in value:
        return "3-5\u5e74"
    if any(token in value for token in ("\u4e00\u5e74", "\u4e24\u5e74", "\u4e8c\u5e74")):
        return "1-3\u5e74"
    numbers = [int(number) for number in re.findall(r"\d+", value)]
    if not numbers:
        return None
    maximum = max(numbers)
    if maximum >= 10:
        return "10\u5e74\u4ee5\u4e0a"
    if maximum >= 5:
        return "5-10\u5e74"
    if maximum >= 3:
        return "3-5\u5e74"
    if maximum >= 1:
        return "1-3\u5e74"
    return None


def extract_skills(
    title: Any,
    description: Any,
    skills_arr: Any,
    keywords: set[str],
    aliases: Mapping[str, str],
) -> list[str]:
    text = f"{title or ''} {description or ''}".lower()
    matched: set[str] = set()
    if isinstance(skills_arr, list):
        for skill in skills_arr:
            value = str(skill).strip()
            lowered = value.lower()
            if lowered in aliases:
                matched.add(aliases[lowered])
            elif lowered in keywords:
                matched.add(lowered)
            elif value:
                matched.add(value)
    matched.update(keyword for keyword in keywords if keyword in text)
    for alias, target in aliases.items():
        if (" " in alias or len(alias) > 5) and alias in text:
            matched.add(target)

    preserve_case = {
        "ios", "macos", "ipados", "tvos", "watchos", "visionos", "php", "api", "sql", "nosql", "json",
        "xml", "html", "css", "aws", "gcp", "ci/cd", "nlp", "cv", "llm", "gpt", "bert", "k8s", "grpc",
        "restful", "http", "https", "tcp/ip", "dns", "vpn", "cdn", "jwt", "oauth", "oauth2", "sam", "csrf",
        "xss", "ssrf", "ddos", "cors", "ssl", "tls", "waf", "ids", "ips", "jvm", "gc", "juc", "jdbc", "jpa",
        "mvc", "mvp", "mvvm", "oop", "tdd", "bdd", "ddd", "uml", "erd", "etl", "elt", "saas", "paas", "iaas",
        "faas", "baas", "sre", "aiops",
    }
    normalized = []
    for value in matched:
        if not value or len(value) > 30:
            continue
        lowered = value.lower()
        if lowered in preserve_case:
            normalized.append(lowered)
        elif value.islower() or value.isupper():
            normalized.append(value.title())
        else:
            normalized.append(value)
    return sorted(set(normalized), key=str.lower)


def compute_md5(job_id: Any, source_url: Any) -> str:
    return hashlib.md5(f"{job_id}|{source_url or ''}".encode("utf-8")).hexdigest()


def get_or_create_company(cursor: Any, company_obj: Mapping[str, Any], cache: dict[str, int] | None = None) -> int | None:
    name = str(company_obj.get("name") or "").strip()
    if not name:
        return None
    if cache is not None and name in cache:
        return cache[name]
    cursor.execute("SELECT id FROM job_company WHERE company_name = %s", (name,))
    row = cursor.fetchone()
    if row:
        company_id = row[0]
    else:
        cursor.execute(
            """INSERT INTO job_company (company_name, company_size, industry, company_type)
               VALUES (%s, %s, %s, %s)""",
            (
                name,
                company_obj.get("size") or None,
                company_obj.get("industry") or None,
                company_obj.get("type") or None,
            ),
        )
        company_id = cursor.lastrowid
    if cache is not None:
        cache[name] = company_id
    return company_id


def check_duplicate(cursor: Any, source_md5: str) -> bool:
    if not source_md5:
        return False
    cursor.execute("SELECT COUNT(*) FROM job_position WHERE source_md5 = %s", (source_md5,))
    return cursor.fetchone()[0] > 0


def insert_position(cursor: Any, job: Mapping[str, Any], company_id: int | None, source_md5: str) -> None:
    publish_date = job.get("publishDate") or None
    if isinstance(publish_date, str) and len(publish_date) > 10:
        publish_date = publish_date[:10]
    cursor.execute(
        """INSERT INTO job_position
           (job_id, title, company_id, salary_min, salary_max,
            city, province, city_tier, education, experience,
            skills, welfare, description, publish_date, source_url, source_md5)
           VALUES (%s,%s,%s,%s,%s, %s,%s,%s,%s,%s, %s,%s,%s,%s, %s,%s)""",
        (
            job.get("jobId"),
            job.get("title"),
            company_id,
            job.get("salary", {}).get("min"),
            job.get("salary", {}).get("max"),
            job.get("city"),
            job.get("province"),
            job.get("cityTier"),
            job.get("education"),
            job.get("experience"),
            json.dumps(job.get("skills") or [], ensure_ascii=False),
            json.dumps(job.get("welfare") or [], ensure_ascii=False),
            job.get("description"),
            publish_date,
            job.get("sourceUrl"),
            source_md5,
        ),
    )


def normalize_job(
    job: dict[str, Any], city_map: Mapping[str, Mapping[str, str]], keywords: set[str], aliases: Mapping[str, str]
) -> tuple[dict[str, Any] | None, str | None]:
    company = job.get("company")
    if not isinstance(company, dict):
        return None, "company must be an object"
    title = str(job.get("title") or "").strip()
    company_name = str(company.get("name") or "").strip()
    if not title or not company_name:
        return None, "title and company.name are required"
    job["title"] = title
    company["name"] = company_name
    job["salary"] = normalize_salary(job.get("salary"))
    job["city"], job["province"], job["cityTier"] = normalize_city(
        job.get("city"), job.get("province"), city_map
    )
    job["education"] = normalize_education(job.get("education"))
    job["experience"] = normalize_experience(job.get("experience"))
    job["skills"] = extract_skills(job.get("title"), job.get("description"), job.get("skills"), keywords, aliases)
    job["sourceMd5"] = compute_md5(job.get("jobId"), job.get("sourceUrl"))
    return job, None


def touch_heartbeat(path: str | Path = ETL_HEARTBEAT_FILE) -> None:
    """Record liveness for Docker health checks during idle and busy periods."""
    target = Path(path)
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(datetime.now(timezone.utc).isoformat(), encoding="ascii")


def recover_inflight_messages(
    redis_client: Any, raw_queue: str = RAW_QUEUE, processing_queue: str = PROCESSING_QUEUE
) -> int:
    """Atomically return unacknowledged messages from a previous worker run."""
    recovered = 0
    while redis_client.rpoplpush(processing_queue, raw_queue) is not None:
        recovered += 1
    return recovered


def claim_batch(
    redis_client: Any,
    raw_queue: str = RAW_QUEUE,
    processing_queue: str = PROCESSING_QUEUE,
    batch_size: int = BATCH_SIZE,
    timeout: int = 5,
) -> list[str]:
    """Move a batch to the processing queue before any database work starts."""
    first = redis_client.brpoplpush(raw_queue, processing_queue, timeout=timeout)
    if first is None:
        return []
    messages = [first]
    while len(messages) < batch_size:
        message = redis_client.rpoplpush(raw_queue, processing_queue)
        if message is None:
            break
        messages.append(message)
    return messages


def _is_data_error(error: Exception) -> bool:
    code = error.args[0] if error.args else None
    return code in {1048, 1062, 1264, 1366, 1406, 1451, 1452, 3819}


def _publish_cleaned(
    redis_client: Any, job: Mapping[str, Any], cleaned_queue: str, cleaned_dedupe_set: str
) -> bool:
    payload = json.dumps(job, ensure_ascii=False, sort_keys=True)
    inserted = redis_client.eval(
        CLEANED_ENQUEUE_SCRIPT,
        2,
        cleaned_queue,
        cleaned_dedupe_set,
        job["sourceMd5"],
        payload,
    )
    return bool(inserted)


def _publish_failure(redis_client: Any, failed_queue: str, raw: str, reason: str) -> None:
    redis_client.lpush(
        failed_queue,
        json.dumps(
            {"raw": raw, "reason": reason, "failedAt": datetime.now(timezone.utc).isoformat()},
            ensure_ascii=False,
        ),
    )


def process_batch(
    connection: Any,
    redis_client: Any,
    city_map: Mapping[str, Mapping[str, str]],
    keywords: set[str],
    aliases: Mapping[str, str],
    *,
    raw_queue: str = RAW_QUEUE,
    processing_queue: str = PROCESSING_QUEUE,
    cleaned_queue: str = CLEANED_QUEUE,
    failed_queue: str = FAILED_QUEUE,
    cleaned_dedupe_set: str = CLEANED_DEDUPE_SET,
    batch_size: int = BATCH_SIZE,
    timeout: int = 5,
    heartbeat: Callable[[], None] | None = None,
) -> BatchStats:
    """Process one recoverable Redis batch in one MySQL transaction.

    Messages are acknowledged only after the database transaction and their
    downstream queue action complete.  A database outage leaves the messages
    in ``processing_queue`` for ``recover_inflight_messages`` on retry/restart.
    """
    messages = claim_batch(redis_client, raw_queue, processing_queue, batch_size, timeout)
    stats = BatchStats(claimed=len(messages))
    if not messages:
        return stats

    outcomes: list[tuple[str, str, dict[str, Any] | None, str | None]] = []
    cursor = connection.cursor()
    company_cache: dict[str, int] = {}
    try:
        for index, raw in enumerate(messages):
            if heartbeat is not None:
                heartbeat()
            try:
                parsed = json.loads(raw)
            except (TypeError, json.JSONDecodeError):
                outcomes.append((raw, "discard", None, "invalid JSON payload"))
                continue
            if not isinstance(parsed, dict):
                outcomes.append((raw, "discard", None, "JSON payload must be an object"))
                continue
            job, validation_error = normalize_job(parsed, city_map, keywords, aliases)
            if validation_error:
                outcomes.append((raw, "discard", None, validation_error))
                continue

            savepoint = f"etl_record_{index}"
            cursor.execute(f"SAVEPOINT {savepoint}")
            try:
                if check_duplicate(cursor, job["sourceMd5"]):
                    outcomes.append((raw, "duplicate", job, None))
                    continue
                company_id = get_or_create_company(cursor, job["company"], company_cache)
                insert_position(cursor, job, company_id, job["sourceMd5"])
                outcomes.append((raw, "success", job, None))
            except Exception as error:
                cursor.execute(f"ROLLBACK TO SAVEPOINT {savepoint}")
                # A company inserted inside this savepoint was rolled back too.
                # Remove a possibly stale cache entry before the next record.
                company_cache.pop(str(job["company"].get("name") or "").strip(), None)
                if check_duplicate(cursor, job["sourceMd5"]):
                    outcomes.append((raw, "duplicate", job, None))
                elif _is_data_error(error):
                    outcomes.append((raw, "error", None, str(error)))
                else:
                    raise
        connection.commit()
    except Exception:
        connection.rollback()
        raise
    finally:
        cursor.close()

    for raw, outcome, job, reason in outcomes:
        if outcome in {"success", "duplicate"}:
            if _publish_cleaned(redis_client, job, cleaned_queue, cleaned_dedupe_set):
                stats.cleaned += 1
            if outcome == "success":
                stats.success += 1
            else:
                stats.duplicate += 1
        else:
            _publish_failure(redis_client, failed_queue, raw, reason or "unclassified record error")
            if outcome == "discard":
                stats.null_discard += 1
            else:
                stats.error += 1
        redis_client.lrem(processing_queue, 1, raw)
    return stats


running = True
stats = BatchStats()


def handle_signal(_signal: int, _frame: Any) -> None:
    global running
    running = False


def main() -> None:
    global stats
    import pymysql
    import redis

    city_map = load_city_mapping()
    keywords, aliases = load_skill_dict()
    redis_client = redis.Redis(host=REDIS_HOST, port=REDIS_PORT, db=REDIS_DB, decode_responses=True)
    connection = pymysql.connect(
        host=MYSQL_HOST,
        port=MYSQL_PORT,
        user=MYSQL_USER,
        password=MYSQL_PASSWORD,
        database=MYSQL_DATABASE,
        charset="utf8mb4",
    )
    signal.signal(signal.SIGINT, handle_signal)
    signal.signal(signal.SIGTERM, handle_signal)
    touch_heartbeat()
    recovered = recover_inflight_messages(redis_client)
    if recovered:
        print(f"[INFO] recovered {recovered} unacknowledged messages")

    try:
        while running:
            touch_heartbeat()
            try:
                batch = process_batch(
                    connection,
                    redis_client,
                    city_map,
                    keywords,
                    aliases,
                    heartbeat=touch_heartbeat,
                )
                for field in ("claimed", "success", "duplicate", "null_discard", "error", "cleaned"):
                    setattr(stats, field, getattr(stats, field) + getattr(batch, field))
                if batch.claimed and stats.claimed % LOG_INTERVAL < batch.claimed:
                    print(f"[INFO] claimed={stats.claimed} success={stats.success} duplicate={stats.duplicate} error={stats.error}")
            except Exception as error:
                connection.rollback()
                recovered = recover_inflight_messages(redis_client)
                print(f"[ERROR] batch rolled back; recovered={recovered}; reason={error}")
                time.sleep(1)
            touch_heartbeat()
            if not running:
                break
            time.sleep(min(ETL_HEARTBEAT_INTERVAL_SECONDS, 1.0))
    finally:
        connection.close()
        redis_client.close()


if __name__ == "__main__":
    main()
