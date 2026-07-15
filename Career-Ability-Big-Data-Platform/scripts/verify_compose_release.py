"""Exercise the release-critical HTTP flow against a running Compose stack."""
from __future__ import annotations

import argparse
import json
import os
import time
import uuid
from dataclasses import dataclass
from typing import Any
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen


@dataclass(frozen=True)
class HttpResult:
    status: int
    payload: dict[str, Any] | None


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--base-url", default="http://127.0.0.1:8080")
    parser.add_argument("--username", default=os.getenv("INITIAL_ADMIN_USERNAME", ""))
    parser.add_argument("--password", default=os.getenv("INITIAL_ADMIN_PASSWORD", ""))
    parser.add_argument("--timeout-seconds", type=int, default=180)
    return parser.parse_args()


def request_json(
    method: str,
    url: str,
    payload: dict[str, Any] | None = None,
    token: str | None = None,
    api_key: str | None = None,
) -> HttpResult:
    headers = {"Accept": "application/json"}
    data = None
    if payload is not None:
        data = json.dumps(payload).encode("utf-8")
        headers["Content-Type"] = "application/json"
    if token:
        headers["Authorization"] = f"Bearer {token}"
    if api_key:
        headers["X-API-Key"] = api_key
    request = Request(url, data=data, headers=headers, method=method)
    try:
        with urlopen(request, timeout=25) as response:
            raw = response.read().decode("utf-8")
            return HttpResult(response.status, json.loads(raw) if raw else None)
    except HTTPError as error:
        raw = error.read().decode("utf-8", errors="replace")
        try:
            decoded = json.loads(raw) if raw else None
        except json.JSONDecodeError:
            decoded = None
        return HttpResult(error.code, decoded)
    except URLError as error:
        raise RuntimeError(f"{method} {url} failed: {error.reason}") from error


def successful(result: HttpResult, description: str) -> Any:
    if result.status != 200 or not result.payload or result.payload.get("code") != 200:
        raise RuntimeError(f"{description} failed: HTTP {result.status}, body={result.payload}")
    return result.payload.get("data")


def expect_status(result: HttpResult, expected: set[int], description: str) -> None:
    if result.status not in expected:
        raise RuntimeError(
            f"{description} expected HTTP {sorted(expected)}, got {result.status}: {result.payload}")


def login(base_url: str, username: str, password: str) -> str:
    response = successful(
        request_json(
            "POST",
            f"{base_url}/api/auth/login",
            {"username": username, "password": password},
        ),
        f"login for {username}",
    )
    token = response.get("accessToken") if isinstance(response, dict) else None
    if not isinstance(token, str) or not token:
        raise RuntimeError(f"login for {username} did not return an access token")
    return token


def create_user(base_url: str, admin_token: str, username: str, role_code: str) -> int:
    response = successful(
        request_json(
            "POST",
            f"{base_url}/api/admin/users",
            {
                "username": username,
                "password": "ReleasePass!123",
                "realName": role_code.lower(),
                "email": f"{username}@example.invalid",
                "college": "release-test-college",
                "roleCodes": [role_code],
            },
            token=admin_token,
        ),
        f"create {role_code} user",
    )
    user_id = response.get("id") if isinstance(response, dict) else None
    if not isinstance(user_id, int) or user_id < 1:
        raise RuntimeError(f"create {role_code} user did not return an id")
    return user_id


def wait_for_report(base_url: str, token: str, report_id: int, timeout_seconds: int) -> None:
    deadline = time.monotonic() + timeout_seconds
    while time.monotonic() < deadline:
        record = successful(
            request_json("GET", f"{base_url}/api/reports/{report_id}/status", token=token),
            "report status",
        )
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

    run_id = uuid.uuid4().hex[:8]
    users: dict[str, tuple[str, int, str]] = {}
    for role_code, key in (
        ("ROLE_ANALYST", "analyst"),
        ("ROLE_TEACHER", "teacher"),
        ("ROLE_COLLEGE_ADMIN", "college"),
        ("ROLE_STUDENT", "student"),
    ):
        username = f"rel_{key}_{run_id}"
        user_id = create_user(base_url, admin_token, username, role_code)
        users[key] = (username, user_id, login(base_url, username, "ReleasePass!123"))

    analyst_token = users["analyst"][2]
    teacher_token = users["teacher"][2]
    college_token = users["college"][2]
    student_name, student_id, student_token = users["student"]

    expect_status(
        request_json("GET", f"{base_url}/api/recommend?page=1&size=1"),
        {401},
        "anonymous recommendation request",
    )
    expect_status(
        request_json(
            "POST",
            f"{base_url}/api/admin/users",
            {"username": "bad_user", "password": "ReleasePass!123", "roleCodes": ["ROLE_STUDENT"]},
            token=teacher_token,
        ),
        {403},
        "teacher user-management request",
    )

    successful(
        request_json(
            "PUT",
            f"{base_url}/api/profile",
            {
                "major": "Computer Science",
                "skills": ["Java", "MySQL", "Python"],
                "education": "本科",
                "preferredCity": "北京,上海",
                "salaryMin": 8,
                "salaryMax": 20,
            },
            token=student_token,
        ),
        "student profile update",
    )
    recommendations = successful(
        request_json("GET", f"{base_url}/api/recommend?page=1&size=20", token=student_token),
        "student recommendations",
    )
    if not isinstance(recommendations, dict) or recommendations.get("total", 0) > 20:
        raise RuntimeError(f"recommendations violate the TOP20 contract: {recommendations}")

    templates = successful(
        request_json("GET", f"{base_url}/api/reports/templates", token=analyst_token),
        "analyst report templates",
    )
    if not isinstance(templates, list) or not templates:
        raise RuntimeError("no report templates are available for the analyst")
    template_id = templates[0].get("id")
    if not isinstance(template_id, int):
        raise RuntimeError("report template has no numeric id")
    report = successful(
        request_json(
            "POST",
            f"{base_url}/api/reports/generate",
            {"templateId": template_id, "title": f"release-e2e-{run_id}"},
            token=analyst_token,
        ),
        "analyst report generation",
    )
    report_id = report.get("id") if isinstance(report, dict) else None
    if not isinstance(report_id, int):
        raise RuntimeError("report generation did not return an id")
    wait_for_report(base_url, analyst_token, report_id, args.timeout_seconds)
    expect_status(
        request_json("GET", f"{base_url}/api/reports/{report_id}/status", token=college_token),
        {403, 404},
        "cross-user report status request",
    )

    api_key_response = successful(
        request_json(
            "POST",
            f"{base_url}/api/admin/api-keys",
            {"appName": f"release-e2e-{run_id}", "rateLimit": 1},
            token=analyst_token,
        ),
        "analyst API key creation",
    )
    api_key = api_key_response.get("apiKey") if isinstance(api_key_response, dict) else None
    if not isinstance(api_key, str) or not api_key:
        raise RuntimeError("API key creation did not return the secret exactly once")
    successful(
        request_json(
            "GET",
            f"{base_url}/api/open/v1/positions?page=1&size=1",
            token=analyst_token,
            api_key=api_key,
        ),
        "open recruitment request with matching credentials",
    )
    expect_status(
        request_json(
            "GET",
            f"{base_url}/api/open/v1/positions?page=1&size=1",
            token=student_token,
            api_key=api_key,
        ),
        {403},
        "open API owner mismatch",
    )
    expect_status(
        request_json(
            "GET",
            f"{base_url}/api/open/v1/skills/hot",
            token=analyst_token,
            api_key=api_key,
        ),
        {429},
        "API key rate limit",
    )
    expect_status(
        request_json(
            "GET",
            f"{base_url}/api/open/v1/positions?page=1&size=1",
            token=analyst_token,
            api_key="invalid-key",
        ),
        {401},
        "invalid API key",
    )

    successful(
        request_json(
            "PATCH",
            f"{base_url}/api/admin/users/{student_id}/status",
            {"status": 0},
            token=admin_token,
        ),
        "disable student",
    )
    expect_status(
        request_json("GET", f"{base_url}/api/profile", token=student_token),
        {401, 403},
        "disabled-user token request",
    )

    print(json.dumps({
        "admin": args.username,
        "roles_exercised": ["ROLE_ADMIN", "ROLE_ANALYST", "ROLE_TEACHER", "ROLE_COLLEGE_ADMIN", "ROLE_STUDENT"],
        "student": student_name,
        "report_id": report_id,
        "api_key_rate_limit": 1,
        "recommendation_top20_verified": True,
        "cross_user_report_denied": True,
        "disabled_user_denied": True,
    }, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
