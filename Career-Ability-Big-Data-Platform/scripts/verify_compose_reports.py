"""Verify report generation, font availability, and report-volume persistence in Compose."""
from __future__ import annotations

import argparse
import hashlib
import json
import os
import subprocess
import tempfile
import time
from pathlib import Path
from typing import Any
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen


PUBLIC_FONT_PATH = "/usr/share/fonts/noto/NotoSansSC.ttf"
PUBLIC_TITLE = "CI中文报告验收"


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--compose-file", required=True)
    parser.add_argument("--base-url", default="http://127.0.0.1:8080")
    parser.add_argument("--username", default=os.getenv("INITIAL_ADMIN_USERNAME", ""))
    parser.add_argument("--password", default=os.getenv("INITIAL_ADMIN_PASSWORD", ""))
    parser.add_argument("--timeout-seconds", type=int, default=180)
    return parser.parse_args()


def request_json(method: str, url: str, payload: dict[str, Any] | None = None,
                 token: str | None = None) -> dict[str, Any]:
    headers = {"Accept": "application/json"}
    data = None
    if payload is not None:
        data = json.dumps(payload).encode("utf-8")
        headers["Content-Type"] = "application/json"
    if token:
        headers["Authorization"] = f"Bearer {token}"
    request = Request(url, data=data, headers=headers, method=method)
    try:
        with urlopen(request, timeout=20) as response:
            return json.loads(response.read().decode("utf-8"))
    except HTTPError as error:
        body = error.read().decode("utf-8", errors="replace")
        raise RuntimeError(f"{method} {url} returned HTTP {error.code}: {body}") from error
    except URLError as error:
        raise RuntimeError(f"{method} {url} failed: {error.reason}") from error


def response_data(response: dict[str, Any], description: str) -> Any:
    if response.get("code") != 200 or "data" not in response:
        raise RuntimeError(f"{description} returned an unexpected response: {response}")
    return response["data"]


def login(base_url: str, username: str, password: str) -> str:
    data = response_data(request_json(
        "POST", f"{base_url}/api/auth/login", {"username": username, "password": password}), "login")
    token = data.get("accessToken") if isinstance(data, dict) else None
    if not isinstance(token, str) or not token:
        raise RuntimeError("login response does not contain an accessToken")
    return token


def report_template_id(base_url: str, token: str) -> int:
    templates = response_data(request_json("GET", f"{base_url}/api/reports/templates", token=token), "templates")
    if not isinstance(templates, list) or not templates:
        raise RuntimeError("no enabled report templates are available")
    template_id = templates[0].get("id") if isinstance(templates[0], dict) else None
    if not isinstance(template_id, int) or template_id < 1:
        raise RuntimeError("report template does not contain a valid id")
    return template_id


def generate_report(base_url: str, token: str, template_id: int) -> int:
    record = response_data(request_json(
        "POST", f"{base_url}/api/reports/generate", {"templateId": template_id, "title": PUBLIC_TITLE}, token),
        "report generation")
    record_id = record.get("id") if isinstance(record, dict) else None
    if not isinstance(record_id, int) or record_id < 1:
        raise RuntimeError("report generation response does not contain a valid id")
    return record_id


def wait_for_completion(base_url: str, token: str, record_id: int, timeout_seconds: int) -> None:
    deadline = time.monotonic() + timeout_seconds
    while time.monotonic() < deadline:
        record = response_data(request_json(
            "GET", f"{base_url}/api/reports/{record_id}/status", token=token), "report status")
        status = record.get("status") if isinstance(record, dict) else None
        if status == "COMPLETED":
            return
        if status == "FAILED":
            raise RuntimeError(f"report generation failed: {record.get('errorMsg')}")
        time.sleep(2)
    raise RuntimeError(f"report {record_id} did not complete within {timeout_seconds} seconds")


def download_report(base_url: str, token: str, record_id: int) -> bytes:
    request = Request(
        f"{base_url}/api/reports/{record_id}/download",
        headers={"Authorization": f"Bearer {token}", "Accept": "application/pdf"},
        method="GET")
    try:
        with urlopen(request, timeout=30) as response:
            content = response.read()
    except HTTPError as error:
        body = error.read().decode("utf-8", errors="replace")
        raise RuntimeError(f"report download returned HTTP {error.code}: {body}") from error
    if len(content) < 512 or not content.startswith(b"%PDF-"):
        raise RuntimeError("report download is not a non-empty PDF")
    return content


def compose(args: argparse.Namespace, *command: str) -> subprocess.CompletedProcess[str]:
    completed = subprocess.run(
        ["docker", "compose", "-f", args.compose_file, *command],
        check=False,
        capture_output=True,
        text=True,
        timeout=args.timeout_seconds + 60)
    if completed.returncode != 0:
        raise RuntimeError(
            f"docker compose {' '.join(command)} failed:\n{completed.stdout}\n{completed.stderr}")
    return completed


def assert_font_available(args: argparse.Namespace) -> None:
    completed = compose(
        args,
        "exec", "-T", "backend", "sh", "-c",
        f"test -r {PUBLIC_FONT_PATH} && fc-match --format='%{{file}}' 'Noto Sans SC'")
    if completed.stdout.strip() != PUBLIC_FONT_PATH:
        raise RuntimeError(f"Noto Sans SC font is not selected from {PUBLIC_FONT_PATH}: {completed.stdout}")


def recreate_backend(args: argparse.Namespace) -> None:
    compose(
        args,
        "up", "-d", "--wait", "--wait-timeout", str(args.timeout_seconds),
        "--force-recreate", "--no-deps", "backend")


def assert_chinese_text_extractable(pdf_path: Path) -> None:
    try:
        completed = subprocess.run(
            ["pdftotext", "-enc", "UTF-8", str(pdf_path), "-"],
            check=False,
            capture_output=True,
            text=True,
            timeout=30)
    except FileNotFoundError as error:
        raise RuntimeError("pdftotext is required for the Compose report verification") from error
    if completed.returncode != 0:
        raise RuntimeError(f"pdftotext failed: {completed.stderr}")
    if PUBLIC_TITLE not in completed.stdout:
        raise RuntimeError("the generated PDF does not contain extractable Chinese report text")


def main() -> int:
    args = parse_args()
    if not args.username or not args.password:
        raise SystemExit("--username and --password (or INITIAL_ADMIN_* environment variables) are required")

    assert_font_available(args)
    token = login(args.base_url, args.username, args.password)
    record_id = generate_report(args.base_url, token, report_template_id(args.base_url, token))
    wait_for_completion(args.base_url, token, record_id, args.timeout_seconds)
    first_download = download_report(args.base_url, token, record_id)

    recreate_backend(args)
    assert_font_available(args)
    token = login(args.base_url, args.username, args.password)
    wait_for_completion(args.base_url, token, record_id, args.timeout_seconds)
    second_download = download_report(args.base_url, token, record_id)
    if hashlib.sha256(first_download).digest() != hashlib.sha256(second_download).digest():
        raise RuntimeError("report content changed after backend recreation")

    with tempfile.TemporaryDirectory() as directory:
        report_path = Path(directory) / "report.pdf"
        report_path.write_bytes(second_download)
        assert_chinese_text_extractable(report_path)
        print(json.dumps({
            "report_id": record_id,
            "title": PUBLIC_TITLE,
            "pdf_bytes": report_path.stat().st_size,
            "sha256": hashlib.sha256(second_download).hexdigest(),
            "font": PUBLIC_FONT_PATH,
            "chinese_text_extractable": True,
            "backend_recreated": True,
        }, ensure_ascii=False, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
