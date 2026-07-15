"""Measure release-critical HTTP latency against a running Compose backend."""
from __future__ import annotations

import argparse
import json
import os
import statistics
import time
import uuid
from typing import Any
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base-url", default="http://127.0.0.1:8080")
    parser.add_argument("--username", default=os.getenv("INITIAL_ADMIN_USERNAME", ""))
    parser.add_argument("--password", default=os.getenv("INITIAL_ADMIN_PASSWORD", ""))
    parser.add_argument("--runs", type=int, default=8)
    parser.add_argument("--timeout-seconds", type=int, default=180)
    parser.add_argument("--position-p95-ms", type=float, default=float(os.getenv("PERF_POSITION_P95_MS", "1200")))
    parser.add_argument("--stats-p95-ms", type=float, default=float(os.getenv("PERF_STATS_P95_MS", "1800")))
    parser.add_argument("--recommend-cold-ms", type=float, default=float(os.getenv("PERF_RECOMMEND_COLD_MS", "2500")))
    parser.add_argument("--recommend-hot-p95-ms", type=float, default=float(os.getenv("PERF_RECOMMEND_HOT_P95_MS", "1000")))
    parser.add_argument("--report-generation-ms", type=float, default=float(os.getenv("PERF_REPORT_GENERATION_MS", "120000")))
    return parser.parse_args()


def request_json(method: str, url: str, payload: dict[str, Any] | None = None, token: str | None = None) -> dict[str, Any]:
    headers = {"Accept": "application/json"}
    data = None
    if payload is not None:
        data = json.dumps(payload).encode("utf-8")
        headers["Content-Type"] = "application/json"
    if token:
        headers["Authorization"] = f"Bearer {token}"
    request = Request(url, data=data, headers=headers, method=method)
    try:
        with urlopen(request, timeout=30) as response:
            return json.loads(response.read().decode("utf-8"))
    except HTTPError as error:
        body = error.read().decode("utf-8", errors="replace")
        raise RuntimeError(f"{method} {url} returned HTTP {error.code}: {body}") from error
    except URLError as error:
        raise RuntimeError(f"{method} {url} failed: {error.reason}") from error


def data(response: dict[str, Any], description: str) -> Any:
    if response.get("code") != 200:
        raise RuntimeError(f"{description} failed: {response}")
    return response.get("data")


def timed(callback) -> tuple[Any, float]:
    started_at = time.perf_counter()
    result = callback()
    return result, (time.perf_counter() - started_at) * 1000


def percentile95(values: list[float]) -> float:
    if not values:
        return 0.0
    ordered = sorted(values)
    index = max(0, int(len(ordered) * 0.95 + 0.999999) - 1)
    return ordered[index]


def login(base_url: str, username: str, password: str) -> str:
    payload = data(request_json("POST", f"{base_url}/api/auth/login", {
        "username": username,
        "password": password,
    }), "login")
    token = payload.get("accessToken") if isinstance(payload, dict) else None
    if not isinstance(token, str) or not token:
        raise RuntimeError("login did not return an access token")
    return token


def create_student(base_url: str, admin_token: str) -> tuple[str, str]:
    username = f"perf_student_{uuid.uuid4().hex[:8]}"
    password = "ReleasePass!123"
    data(request_json("POST", f"{base_url}/api/admin/users", {
        "username": username,
        "password": password,
        "realName": "performance student",
        "email": f"{username}@example.invalid",
        "college": "release-test-college",
        "roleCodes": ["ROLE_STUDENT"],
    }, token=admin_token), "create performance student")
    return username, password


def prepare_profile(base_url: str, student_token: str) -> None:
    data(request_json("PUT", f"{base_url}/api/profile", {
        "major": "Computer Science",
        "skills": ["Java", "Python", "MySQL", "Redis"],
        "education": "Bachelor",
        "preferredCity": "Shanghai,Hangzhou",
        "salaryMin": 8,
        "salaryMax": 25,
    }, token=student_token), "prepare student profile")


def wait_for_report(base_url: str, token: str, report_id: int, timeout_seconds: int) -> None:
    deadline = time.monotonic() + timeout_seconds
    while time.monotonic() < deadline:
        record = data(request_json("GET", f"{base_url}/api/reports/{report_id}/status", token=token), "report status")
        status = record.get("status") if isinstance(record, dict) else None
        if status == "COMPLETED":
            return
        if status == "FAILED":
            raise RuntimeError(f"report generation failed: {record.get('errorMsg')}")
        time.sleep(2)
    raise RuntimeError(f"report {report_id} did not complete within {timeout_seconds} seconds")


def main() -> int:
    args = parse_args()
    if not args.username or not args.password:
        raise SystemExit("INITIAL_ADMIN_USERNAME and INITIAL_ADMIN_PASSWORD are required")
    base_url = args.base_url.rstrip("/")
    admin_token = login(base_url, args.username, args.password)
    student_username, student_password = create_student(base_url, admin_token)
    student_token = login(base_url, student_username, student_password)
    prepare_profile(base_url, student_token)

    position_ms: list[float] = []
    stats_ms: list[float] = []
    hot_recommend_ms: list[float] = []
    _, cold_recommend_ms = timed(lambda: data(
        request_json("GET", f"{base_url}/api/recommend?page=1&size=20", token=student_token),
        "cold recommendations",
    ))
    for _ in range(max(1, args.runs)):
        _, elapsed = timed(lambda: data(
            request_json("GET", f"{base_url}/api/positions?page=1&size=20", token=admin_token),
            "position page",
        ))
        position_ms.append(elapsed)
        _, elapsed = timed(lambda: data(
            request_json("GET", f"{base_url}/api/stats/positions", token=admin_token),
            "positions stats",
        ))
        stats_ms.append(elapsed)
        _, elapsed = timed(lambda: data(
            request_json("GET", f"{base_url}/api/recommend?page=1&size=20", token=student_token),
            "hot recommendations",
        ))
        hot_recommend_ms.append(elapsed)

    templates = data(request_json("GET", f"{base_url}/api/reports/templates", token=admin_token), "report templates")
    if not isinstance(templates, list) or not templates:
        raise RuntimeError("no report template is available")
    template_id = templates[0].get("id")
    report_payload, report_submit_ms = timed(lambda: data(request_json(
        "POST",
        f"{base_url}/api/reports/generate",
        {"templateId": template_id, "title": f"performance-{uuid.uuid4().hex[:8]}"},
        token=admin_token,
    ), "report generation"))
    report_id = report_payload.get("id") if isinstance(report_payload, dict) else None
    if not isinstance(report_id, int):
        raise RuntimeError("report generation did not return an id")
    started_at = time.perf_counter()
    wait_for_report(base_url, admin_token, report_id, args.timeout_seconds)
    report_total_ms = report_submit_ms + (time.perf_counter() - started_at) * 1000

    result = {
        "runs": args.runs,
        "position_p95_ms": percentile95(position_ms),
        "stats_p95_ms": percentile95(stats_ms),
        "recommend_cold_ms": cold_recommend_ms,
        "recommend_hot_p95_ms": percentile95(hot_recommend_ms),
        "report_generation_ms": report_total_ms,
        "position_mean_ms": statistics.mean(position_ms),
        "stats_mean_ms": statistics.mean(stats_ms),
        "recommend_hot_mean_ms": statistics.mean(hot_recommend_ms),
        "report_id": report_id,
    }
    violations = []
    if result["position_p95_ms"] > args.position_p95_ms:
        violations.append("position_p95_ms")
    if result["stats_p95_ms"] > args.stats_p95_ms:
        violations.append("stats_p95_ms")
    if result["recommend_cold_ms"] > args.recommend_cold_ms:
        violations.append("recommend_cold_ms")
    if result["recommend_hot_p95_ms"] > args.recommend_hot_p95_ms:
        violations.append("recommend_hot_p95_ms")
    if result["report_generation_ms"] > args.report_generation_ms:
        violations.append("report_generation_ms")
    if violations:
        raise RuntimeError(f"performance thresholds exceeded: {violations}; result={result}")
    print(json.dumps(result, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
