"""Verify backup/restore integrity for a running release Compose stack."""
from __future__ import annotations

import argparse
import json
import subprocess
import sys
import tempfile
from pathlib import Path
from typing import Sequence


COUNT_SQL = """
SELECT 'sys_user', COUNT(*) FROM sys_user
UNION ALL SELECT 'job_position', COUNT(*) FROM job_position
UNION ALL SELECT 'report_record', COUNT(*) FROM report_record
UNION ALL SELECT 'sys_role_permission', COUNT(*) FROM sys_role_permission
UNION ALL SELECT 'api_key', COUNT(*) FROM api_key
UNION ALL SELECT 'api_call_log', COUNT(*) FROM api_call_log
UNION ALL SELECT 'schema_migrations', COUNT(*) FROM schema_migrations
ORDER BY 1
"""

MUTATION_SQL = """
INSERT INTO sys_operation_log(
    username, module, operation, description, method, status
) VALUES (
    'backup-restore-mutation', 'release', 'mutation', 'must disappear after restore', 'verify', 1
)
"""

MUTATION_COUNT_SQL = """
SELECT COUNT(*) FROM sys_operation_log
WHERE username = 'backup-restore-mutation'
"""


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--compose-file", required=True)
    parser.add_argument("--timeout-seconds", type=int, default=240)
    parser.add_argument("--backup-directory", type=Path)
    return parser.parse_args()


def run(command: Sequence[str], *, input_data: bytes | None = None, timeout_seconds: int = 240) -> bytes:
    completed = subprocess.run(
        command,
        input=input_data,
        capture_output=True,
        timeout=timeout_seconds,
        check=False,
    )
    if completed.returncode != 0:
        stdout = completed.stdout.decode("utf-8", errors="replace")
        stderr = completed.stderr.decode("utf-8", errors="replace")
        raise RuntimeError(f"{' '.join(command)} failed:\n{stdout}\n{stderr}")
    return completed.stdout


def compose(compose_file: str, command: Sequence[str], *, timeout_seconds: int = 240) -> bytes:
    return run(["docker", "compose", "-f", compose_file, *command], timeout_seconds=timeout_seconds)


def mysql_query(compose_file: str, sql: str, *, timeout_seconds: int = 240) -> str:
    return run(
        ["docker", "compose", "-f", compose_file, "exec", "-T", "mysql", "sh", "-c",
         'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot -N -B "$MYSQL_DATABASE"'],
        input_data=sql.encode("utf-8"),
        timeout_seconds=timeout_seconds,
    ).decode("utf-8", errors="replace")


def parse_counts(raw: str) -> dict[str, int]:
    counts: dict[str, int] = {}
    for line in raw.splitlines():
        if not line.strip():
            continue
        name, value = line.split("\t", 1)
        counts[name] = int(value)
    return counts


def backup_command(script: Path, compose_file: str, directory: Path, command: str, timeout_seconds: int) -> dict[str, object]:
    output = run(
        [
            sys.executable,
            str(script),
            command,
            "--compose-file",
            compose_file,
            "--directory",
            str(directory),
            "--timeout-seconds",
            str(timeout_seconds),
        ],
        timeout_seconds=timeout_seconds + 30,
    ).decode("utf-8", errors="replace")
    return json.loads(output)


def verify(args: argparse.Namespace, backup_directory: Path) -> dict[str, object]:
    script = Path(__file__).with_name("compose_backup.py")
    baseline = parse_counts(mysql_query(args.compose_file, COUNT_SQL, timeout_seconds=args.timeout_seconds))
    backup_manifest = backup_command(script, args.compose_file, backup_directory, "create", args.timeout_seconds)
    mysql_query(args.compose_file, MUTATION_SQL, timeout_seconds=args.timeout_seconds)
    mutated_count = int(mysql_query(args.compose_file, MUTATION_COUNT_SQL, timeout_seconds=args.timeout_seconds).strip())
    if mutated_count < 1:
        raise RuntimeError("verification mutation was not written before restore")
    backup_command(script, args.compose_file, backup_directory, "restore", args.timeout_seconds)
    restored = parse_counts(mysql_query(args.compose_file, COUNT_SQL, timeout_seconds=args.timeout_seconds))
    if restored != baseline:
        raise RuntimeError(f"database counts changed after restore: baseline={baseline}, restored={restored}")
    mutation_after_restore = int(mysql_query(
        args.compose_file,
        MUTATION_COUNT_SQL,
        timeout_seconds=args.timeout_seconds,
    ).strip())
    if mutation_after_restore != 0:
        raise RuntimeError("post-backup mutation survived restore")
    return {
        "backup_directory": str(backup_directory),
        "baseline_counts": baseline,
        "restored_counts": restored,
        "mutation_removed": True,
        "manifest": backup_manifest,
    }


def main() -> int:
    args = parse_args()
    if args.backup_directory:
        args.backup_directory.mkdir(parents=True, exist_ok=True)
        result = verify(args, args.backup_directory)
    else:
        with tempfile.TemporaryDirectory() as directory:
            result = verify(args, Path(directory))
    print(json.dumps(result, ensure_ascii=False, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
