"""Create and restore portable MySQL plus report-volume backups for Compose deployments."""
from __future__ import annotations

import argparse
import hashlib
import json
import subprocess
from datetime import datetime, timezone
from pathlib import Path
from typing import Sequence


DATABASE_FILE = "database.sql"
REPORTS_FILE = "reports.tar"
MANIFEST_FILE = "manifest.json"
REPORTS_DIR = "/var/lib/career-ability/reports"
DATABASE_NAME = '${MYSQL_DATABASE:-career_ability}'


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    subparsers = parser.add_subparsers(dest="command", required=True)
    for name in ("create", "restore"):
        command = subparsers.add_parser(name)
        command.add_argument("--compose-file", required=True)
        command.add_argument("--directory", required=True, type=Path)
        command.add_argument("--timeout-seconds", type=int, default=180)
    return parser.parse_args()


def compose(compose_file: str, command: Sequence[str], *, input_data: bytes | None = None,
            timeout_seconds: int = 180) -> subprocess.CompletedProcess[bytes]:
    completed = subprocess.run(
        ["docker", "compose", "-f", compose_file, *command],
        input=input_data,
        capture_output=True,
        timeout=timeout_seconds,
        check=False,
    )
    if completed.returncode != 0:
        stdout = completed.stdout.decode("utf-8", errors="replace")
        stderr = completed.stderr.decode("utf-8", errors="replace")
        raise RuntimeError(f"docker compose {' '.join(command)} failed:\n{stdout}\n{stderr}")
    return completed


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def create_backup(compose_file: str, directory: Path, timeout_seconds: int) -> dict[str, object]:
    directory.mkdir(parents=True, exist_ok=True)
    database_path = directory / DATABASE_FILE
    reports_path = directory / REPORTS_FILE
    dump = compose(
        compose_file,
        (
            "exec", "-T", "mysql", "sh", "-c",
            'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysqldump --single-transaction --routines --events '
            f'--set-gtid-purged=OFF -uroot "{DATABASE_NAME}"',
        ),
        timeout_seconds=timeout_seconds,
    )
    database_path.write_bytes(dump.stdout)
    reports = compose(
        compose_file,
        ("exec", "-T", "backend", "tar", "-C", REPORTS_DIR, "-cf", "-", "."),
        timeout_seconds=timeout_seconds,
    )
    reports_path.write_bytes(reports.stdout)
    manifest: dict[str, object] = {
        "format": 1,
        "created_at": datetime.now(timezone.utc).isoformat(),
        "database": {"file": DATABASE_FILE, "sha256": sha256(database_path), "bytes": database_path.stat().st_size},
        "reports": {"file": REPORTS_FILE, "sha256": sha256(reports_path), "bytes": reports_path.stat().st_size},
    }
    (directory / MANIFEST_FILE).write_text(
        json.dumps(manifest, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    return manifest


def load_manifest(directory: Path) -> dict[str, object]:
    manifest_path = directory / MANIFEST_FILE
    if not manifest_path.is_file():
        raise RuntimeError(f"backup manifest is missing: {manifest_path}")
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    if manifest.get("format") != 1:
        raise RuntimeError("unsupported backup manifest format")
    for key in ("database", "reports"):
        item = manifest.get(key)
        if not isinstance(item, dict):
            raise RuntimeError(f"backup manifest has no {key} entry")
        file_name = item.get("file")
        expected_hash = item.get("sha256")
        if not isinstance(file_name, str) or not isinstance(expected_hash, str):
            raise RuntimeError(f"backup manifest has an invalid {key} entry")
        actual_path = directory / file_name
        if not actual_path.is_file() or sha256(actual_path) != expected_hash:
            raise RuntimeError(f"backup integrity check failed for {actual_path}")
    return manifest


def restore_backup(compose_file: str, directory: Path, timeout_seconds: int) -> dict[str, object]:
    manifest = load_manifest(directory)
    database = manifest["database"]
    reports = manifest["reports"]
    assert isinstance(database, dict) and isinstance(reports, dict)
    database_path = directory / str(database["file"])
    reports_path = directory / str(reports["file"])
    compose(
        compose_file,
        (
            "exec", "-T", "mysql", "sh", "-c",
            f'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot "{DATABASE_NAME}"',
        ),
        input_data=database_path.read_bytes(),
        timeout_seconds=timeout_seconds,
    )
    compose(
        compose_file,
        (
            "exec", "-T", "backend", "sh", "-c",
            f"mkdir -p {REPORTS_DIR} && find {REPORTS_DIR} -mindepth 1 -delete && tar -C {REPORTS_DIR} -xf -",
        ),
        input_data=reports_path.read_bytes(),
        timeout_seconds=timeout_seconds,
    )
    return manifest


def main() -> int:
    args = parse_args()
    if args.command == "create":
        result = create_backup(args.compose_file, args.directory, args.timeout_seconds)
    else:
        result = restore_backup(args.compose_file, args.directory, args.timeout_seconds)
    print(json.dumps(result, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
