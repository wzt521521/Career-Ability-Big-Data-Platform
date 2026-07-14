"""Import CSV or Excel job data into the durable Redis raw queue."""
from __future__ import annotations

import hashlib
import json
import re
import sys
from datetime import datetime
from pathlib import Path
from typing import Any, Mapping

import pandas as pd

from config import (
    DEFAULT_CSV_PATH,
    RAW_DEDUPE_SET,
    RAW_QUEUE,
    REDIS_DB,
    REDIS_HOST,
    REDIS_PORT,
)


PIPELINE_DIR = Path(__file__).resolve().parent
RAW_ENQUEUE_SCRIPT = """
local inserted = redis.call('SADD', KEYS[2], ARGV[1])
if inserted == 1 then
    redis.call('LPUSH', KEYS[1], ARGV[2])
end
return inserted
"""


def load_city_mapping() -> dict[str, dict[str, str]]:
    """Load the city mapping relative to this module, not the current shell."""
    try:
        with (PIPELINE_DIR / "city_mapping.json").open("r", encoding="utf-8") as source:
            return json.load(source)
    except FileNotFoundError:
        print("[WARN] city_mapping.json not found; city enrichment is disabled")
        return {}


def _is_value(value: Any) -> bool:
    if value is None:
        return False
    if isinstance(value, (list, tuple, set, dict)):
        return bool(value)
    return not pd.isna(value) and str(value).strip() != ""


def _first_value(row: Mapping[str, Any], *names: str) -> Any:
    for name in names:
        value = row.get(name)
        if _is_value(value):
            return value
    return ""


def _optional_text(value: Any) -> str | None:
    return str(value).strip() if _is_value(value) else None


def normalize_city(raw: Any, mapping: Mapping[str, Mapping[str, str]]) -> dict[str, str | None]:
    if not _is_value(raw):
        return {"city": None, "province": None, "tier": None}
    city = str(raw).strip()
    if city in mapping:
        mapped = mapping[city]
        return {"city": city, "province": mapped.get("province"), "tier": mapped.get("tier")}
    return {"city": city, "province": None, "tier": "\u5176\u4ed6"}


def parse_salary(raw: Any) -> dict[str, int | None]:
    """Parse common monthly salary strings into thousands of CNY."""
    if not _is_value(raw):
        return {"min": None, "max": None}

    value = str(raw).strip().lower()
    if any(token in value for token in ("negotiable", "open", "\u9762\u8bae")):
        return {"min": 0, "max": 0}
    value = re.sub(r"(?i)(k|/month|\u6bcf\u6708|\u5143|\uffe5|\$|\s)", "", value)
    parts = re.split(r"[-~\u2013\u2014\u5230\u81f3]", value)
    try:
        numbers = [float(part) for part in parts]
    except ValueError:
        return {"min": None, "max": None}
    if len(numbers) not in (1, 2):
        return {"min": None, "max": None}
    if len(numbers) == 1:
        numbers *= 2
    low, high = numbers
    if low > 100:
        low /= 1000
    if high > 100:
        high /= 1000
    return {"min": int(low), "max": int(high)}


def make_job_id(row: Mapping[str, Any], index: int) -> str:
    for column in ("Job ID", "job_id", "jobId", "\ufeffjobId", "id", "ID", "index"):
        value = row.get(column)
        if _is_value(value):
            return str(value).strip()
    return f"import-{index:06d}"


def _split_values(value: Any) -> list[str]:
    if not _is_value(value):
        return []
    if isinstance(value, list):
        return [str(item).strip() for item in value if _is_value(item)]
    return [item.strip() for item in re.split(r"[|,\uff0c]", str(value)) if item.strip()]


def row_to_json(row: Mapping[str, Any], index: int, city_map: Mapping[str, Mapping[str, str]]) -> dict[str, Any]:
    """Convert one common job-source row to the pipeline contract."""
    title = _first_value(row, "Job Title", "job_title", "Title", "title", "position")
    company_name = _first_value(
        row, "Company", "Company Name", "company", "company_name", "Employer", "companyName"
    )
    location = _first_value(row, "Location", "location", "City", "city", "Region", "region")
    salary_min = _first_value(row, "salaryMin", "Salary Min")
    salary_max = _first_value(row, "salaryMax", "Salary Max")
    salary = parse_salary(
        f"{salary_min}-{salary_max}"
        if _is_value(salary_min) and _is_value(salary_max)
        else _first_value(row, "Salary", "salary", "Salary Range", "Avg Salary", "Avg Salary(K)")
    )
    city = normalize_city(location, city_map)
    province = _first_value(row, "province", "Province")
    tier = _first_value(row, "cityTier", "CityTier")
    if not city["province"] and _is_value(province):
        city["province"] = str(province).strip()
    if not city["tier"] and _is_value(tier):
        city["tier"] = str(tier).strip()

    publish_date = _first_value(row, "Publish Date", "publish_date", "Date", "Post Date", "publishDate")
    if _is_value(publish_date):
        try:
            publish_date = str(pd.to_datetime(publish_date).date())
        except (TypeError, ValueError):
            publish_date = str(publish_date)[:10]
    else:
        publish_date = None

    return {
        "jobId": make_job_id(row, index),
        "title": str(title).strip() if _is_value(title) else "",
        "company": {
            "name": str(company_name).strip() if _is_value(company_name) else "",
            "size": _optional_text(_first_value(row, "Company Size", "company_size", "Size", "companySize")),
            "industry": _optional_text(_first_value(row, "Industry", "industry", "Sector")),
            "type": _optional_text(_first_value(row, "Company Type", "company_type", "Type", "companyType")),
        },
        "salary": salary,
        "city": city["city"],
        "province": city["province"],
        "cityTier": city["tier"],
        "education": _optional_text(_first_value(row, "Qualification", "education", "Education", "Degree")),
        "experience": _optional_text(_first_value(row, "Experience", "experience", "Seniority")),
        "skills": _split_values(_first_value(row, "Skills", "skills", "Key Skills", "Technologies")),
        "welfare": _split_values(_first_value(row, "Welfare", "welfare", "Benefits")),
        "description": _optional_text(_first_value(
            row, "Job Description", "job_description", "Description", "description", "Job_Description"
        )),
        "publishDate": publish_date,
        "sourceUrl": _optional_text(_first_value(row, "source_url", "Source URL", "URL", "url", "sourceUrl")) or "",
        "crawlTime": datetime.now().strftime("%Y-%m-%dT%H:%M:%S"),
    }


def compute_source_md5(job: Mapping[str, Any]) -> str:
    identity = f"{job.get('jobId')}|{job.get('sourceUrl') or ''}"
    return hashlib.md5(identity.encode("utf-8")).hexdigest()


def enqueue_raw_job(
    redis_client: Any,
    job: dict[str, Any],
    raw_queue: str = RAW_QUEUE,
    raw_dedupe_set: str = RAW_DEDUPE_SET,
) -> bool:
    """Atomically add one source record only once per ``source_md5`` identity."""
    source_md5 = compute_source_md5(job)
    job["sourceMd5"] = source_md5
    payload = json.dumps(job, ensure_ascii=False, sort_keys=True)
    inserted = redis_client.eval(
        RAW_ENQUEUE_SCRIPT,
        2,
        raw_queue,
        raw_dedupe_set,
        source_md5,
        payload,
    )
    return bool(inserted)


def read_source_file(file_path: str | Path) -> pd.DataFrame:
    path = Path(file_path)
    if path.suffix.lower() == ".csv":
        return pd.read_csv(path, encoding="utf-8-sig")
    if path.suffix.lower() in {".xlsx", ".xls"}:
        return pd.read_excel(path)
    raise ValueError(f"Unsupported source file format: {path}")


def import_file(
    file_path: str | Path,
    redis_client: Any,
    city_map: Mapping[str, Mapping[str, str]] | None = None,
    raw_queue: str = RAW_QUEUE,
    raw_dedupe_set: str = RAW_DEDUPE_SET,
) -> dict[str, int]:
    """Normalize and enqueue a source file, returning deterministic counters."""
    dataframe = read_source_file(file_path)
    mapping = load_city_mapping() if city_map is None else city_map
    result = {"total": len(dataframe), "enqueued": 0, "duplicate": 0, "skipped": 0}
    for index, (_, row) in enumerate(dataframe.iterrows()):
        job = row_to_json(row, index, mapping)
        if not job["title"] or not job["company"]["name"]:
            result["skipped"] += 1
            continue
        if enqueue_raw_job(redis_client, job, raw_queue, raw_dedupe_set):
            result["enqueued"] += 1
        else:
            result["duplicate"] += 1
    return result


def main() -> None:
    import redis

    file_path = sys.argv[1] if len(sys.argv) > 1 else DEFAULT_CSV_PATH
    client = redis.Redis(host=REDIS_HOST, port=REDIS_PORT, db=REDIS_DB, decode_responses=True)
    try:
        client.ping()
        result = import_file(file_path, client)
        print(
            "[INFO] source rows={total} enqueued={enqueued} duplicates={duplicate} skipped={skipped}".format(
                **result
            )
        )
        print(f"[INFO] raw queue={RAW_QUEUE} pending={client.llen(RAW_QUEUE)}")
    except ValueError as error:
        print(f"[ERROR] {error}")
        raise SystemExit(1) from error
    finally:
        client.close()


if __name__ == "__main__":
    main()
