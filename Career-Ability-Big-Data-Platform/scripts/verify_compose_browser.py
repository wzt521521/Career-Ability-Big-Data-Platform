"""Run real-browser release smoke checks and capture screenshots."""
from __future__ import annotations

import argparse
import json
import os
from pathlib import Path

from playwright.sync_api import Page, sync_playwright


VIEWPORTS = {
    "desktop": {"width": 1440, "height": 900},
    "tablet": {"width": 768, "height": 1024},
    "mobile": {"width": 390, "height": 844},
}

PAGES = [
    "/dashboard",
    "/positions",
    "/recommend",
    "/report",
    "/collect/sources",
    "/system/users",
]


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base-url", default=os.getenv("FRONTEND_BASE_URL", "http://127.0.0.1"))
    parser.add_argument("--username", default=os.getenv("INITIAL_ADMIN_USERNAME", ""))
    parser.add_argument("--password", default=os.getenv("INITIAL_ADMIN_PASSWORD", ""))
    parser.add_argument("--output-dir", type=Path, default=Path("browser-screenshots"))
    parser.add_argument("--timeout-ms", type=int, default=30000)
    return parser.parse_args()


def login(page: Page, base_url: str, username: str, password: str) -> None:
    page.goto(f"{base_url}/login", wait_until="networkidle")
    page.locator("input[autocomplete='username']").fill(username)
    page.locator("input[autocomplete='current-password']").fill(password)
    page.locator(".submit-button").click()
    page.wait_for_url("**/dashboard", wait_until="networkidle")


def assert_clean(messages: list[str], failed_requests: list[str]) -> None:
    if failed_requests:
        raise RuntimeError("browser smoke check saw failed requests: " + "; ".join(failed_requests[:10]))
    severe = [message for message in messages if not (
        "ResizeObserver loop" in message or "favicon" in message
    )]
    if severe:
        raise RuntimeError("browser smoke check saw console errors: " + "; ".join(severe[:10]))


def main() -> int:
    args = parse_args()
    if not args.username or not args.password:
        raise SystemExit("INITIAL_ADMIN_USERNAME and INITIAL_ADMIN_PASSWORD are required")
    args.output_dir.mkdir(parents=True, exist_ok=True)
    results: list[dict[str, object]] = []
    with sync_playwright() as playwright:
        browser = playwright.chromium.launch()
        try:
            for name, viewport in VIEWPORTS.items():
                context = browser.new_context(viewport=viewport)
                page = context.new_page()
                console_errors: list[str] = []
                failed_requests: list[str] = []
                page.on("console", lambda message: console_errors.append(message.text) if message.type == "error" else None)
                page.on("requestfailed", lambda request: failed_requests.append(request.url))
                page.set_default_timeout(args.timeout_ms)
                login(page, args.base_url.rstrip("/"), args.username, args.password)
                for route in PAGES:
                    page.goto(f"{args.base_url.rstrip('/')}{route}", wait_until="networkidle")
                    page.screenshot(path=args.output_dir / f"{name}-{route.strip('/').replace('/', '-')}.png", full_page=True)
                assert_clean(console_errors, failed_requests)
                results.append({
                    "viewport": name,
                    "width": viewport["width"],
                    "height": viewport["height"],
                    "pages": PAGES,
                    "screenshots": len(PAGES),
                })
                context.close()
        finally:
            browser.close()
    print(json.dumps({"viewports": results, "output_dir": str(args.output_dir)}, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
