"""Apply forward-only MySQL migrations and provision runtime database users."""
from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import time
from dataclasses import dataclass
from pathlib import Path
from typing import Iterable

import pymysql


IDENTIFIER = re.compile(r"^[A-Za-z0-9_]+$")
DROP_TABLE = re.compile(r"\bDROP\s+TABLE\b", re.IGNORECASE)


@dataclass(frozen=True)
class Settings:
    host: str
    port: int
    database: str
    root_password: str
    app_user: str
    app_password: str
    migrator_user: str
    migrator_password: str
    migrations_dir: Path
    lock_timeout_seconds: int


def parse_args() -> Settings:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--host", default=os.getenv("MYSQL_HOST", "127.0.0.1"))
    parser.add_argument("--port", type=int, default=int(os.getenv("MYSQL_PORT", "3306")))
    parser.add_argument("--database", default=os.getenv("MYSQL_DATABASE", "career_ability"))
    parser.add_argument("--root-password", default=os.getenv("MYSQL_ROOT_PASSWORD", ""))
    parser.add_argument("--app-user", default=os.getenv("MYSQL_APP_USER", "career_app"))
    parser.add_argument("--app-password", default=os.getenv("MYSQL_APP_PASSWORD", ""))
    parser.add_argument("--migrator-user", default=os.getenv("MYSQL_MIGRATOR_USER", "career_migrator"))
    parser.add_argument("--migrator-password", default=os.getenv("MYSQL_MIGRATOR_PASSWORD", ""))
    parser.add_argument("--migrations-dir", type=Path, default=Path(os.getenv("MIGRATIONS_DIR", "/sql/migrations")))
    parser.add_argument(
        "--lock-timeout-seconds",
        type=int,
        default=int(os.getenv("MIGRATION_LOCK_TIMEOUT_SECONDS", "60")),
    )
    args = parser.parse_args()
    settings = Settings(
        host=args.host,
        port=args.port,
        database=args.database,
        root_password=args.root_password,
        app_user=args.app_user,
        app_password=args.app_password,
        migrator_user=args.migrator_user,
        migrator_password=args.migrator_password,
        migrations_dir=args.migrations_dir,
        lock_timeout_seconds=args.lock_timeout_seconds,
    )
    validate(settings)
    return settings


def validate(settings: Settings) -> None:
    for label, value in (
        ("MYSQL_DATABASE", settings.database),
        ("MYSQL_APP_USER", settings.app_user),
        ("MYSQL_MIGRATOR_USER", settings.migrator_user),
    ):
        if not IDENTIFIER.fullmatch(value):
            raise SystemExit(f"{label} must contain only letters, digits, and underscores")
    if not settings.root_password:
        raise SystemExit("MYSQL_ROOT_PASSWORD is required")
    if not settings.app_password:
        raise SystemExit("MYSQL_APP_PASSWORD is required")
    if not settings.migrator_password:
        raise SystemExit("MYSQL_MIGRATOR_PASSWORD is required")
    if not settings.migrations_dir.is_dir():
        raise SystemExit(f"migrations directory does not exist: {settings.migrations_dir}")


def quote_identifier(identifier: str) -> str:
    if not IDENTIFIER.fullmatch(identifier):
        raise ValueError(f"invalid SQL identifier: {identifier}")
    return f"`{identifier}`"


def root_connection(settings: Settings) -> pymysql.connections.Connection:
    return pymysql.connect(
        host=settings.host,
        port=settings.port,
        user="root",
        password=settings.root_password,
        charset="utf8mb4",
        autocommit=True,
    )


def migrator_connection(settings: Settings) -> pymysql.connections.Connection:
    return pymysql.connect(
        host=settings.host,
        port=settings.port,
        user=settings.migrator_user,
        password=settings.migrator_password,
        database=settings.database,
        charset="utf8mb4",
        autocommit=False,
    )


def provision_database(settings: Settings) -> None:
    database = quote_identifier(settings.database)
    with root_connection(settings) as connection:
        with connection.cursor() as cursor:
            cursor.execute(
                f"CREATE DATABASE IF NOT EXISTS {database} "
                "DEFAULT CHARACTER SET utf8mb4 DEFAULT COLLATE utf8mb4_unicode_ci"
            )
            cursor.execute(f"CREATE USER IF NOT EXISTS %s@'%%' IDENTIFIED BY %s",
                           (settings.app_user, settings.app_password))
            cursor.execute(f"ALTER USER %s@'%%' IDENTIFIED BY %s", (settings.app_user, settings.app_password))
            cursor.execute(f"CREATE USER IF NOT EXISTS %s@'%%' IDENTIFIED BY %s",
                           (settings.migrator_user, settings.migrator_password))
            cursor.execute(
                f"ALTER USER %s@'%%' IDENTIFIED BY %s",
                (settings.migrator_user, settings.migrator_password),
            )
            cursor.execute(
                f"GRANT SELECT, INSERT, UPDATE, DELETE, EXECUTE, SHOW VIEW ON {database}.* TO %s@'%%'",
                (settings.app_user,),
            )
            cursor.execute(
                "GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX, REFERENCES, "
                f"LOCK TABLES, SHOW VIEW ON {database}.* TO %s@'%%'",
                (settings.migrator_user,),
            )
            cursor.execute(f"CREATE TABLE IF NOT EXISTS {database}.schema_migrations ("
                           "version VARCHAR(128) NOT NULL PRIMARY KEY,"
                           "filename VARCHAR(255) NOT NULL,"
                           "checksum_sha256 CHAR(64) NOT NULL,"
                           "applied_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,"
                           "execution_ms INT NOT NULL"
                           ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci")


def migration_files(directory: Path) -> list[Path]:
    files = sorted(path for path in directory.glob("*.sql") if path.is_file())
    if not files:
        raise RuntimeError(f"no migration SQL files found in {directory}")
    return files


def checksum(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def normalize_sql(sql: str, database: str) -> str:
    if DROP_TABLE.search(sql):
        raise RuntimeError("migration contains DROP TABLE, which is forbidden for release upgrades")
    return re.sub(
        r"(?im)^\s*USE\s+career_ability\s*;",
        f"USE {quote_identifier(database)};",
        sql,
    )


def split_statements(sql: str) -> Iterable[str]:
    buffer: list[str] = []
    in_single = False
    in_double = False
    escape = False
    for char in sql:
        buffer.append(char)
        if escape:
            escape = False
            continue
        if char == "\\":
            escape = True
            continue
        if char == "'" and not in_double:
            in_single = not in_single
            continue
        if char == '"' and not in_single:
            in_double = not in_double
            continue
        if char == ";" and not in_single and not in_double:
            statement = "".join(buffer).strip()
            buffer = []
            if statement:
                yield statement
    remainder = "".join(buffer).strip()
    if remainder:
        yield remainder


def migration_record(cursor: pymysql.cursors.Cursor, version: str) -> tuple[str] | None:
    cursor.execute("SELECT checksum_sha256 FROM schema_migrations WHERE version = %s", (version,))
    return cursor.fetchone()


def execute_migration(cursor: pymysql.cursors.Cursor, path: Path, settings: Settings) -> int:
    started_at = time.perf_counter()
    sql = normalize_sql(path.read_text(encoding="utf-8"), settings.database)
    for statement in split_statements(sql):
        cursor.execute(statement)
    return int((time.perf_counter() - started_at) * 1000)


def apply_migrations(settings: Settings) -> dict[str, object]:
    provision_database(settings)
    applied: list[str] = []
    skipped: list[str] = []
    with migrator_connection(settings) as connection:
        lock_name = f"{settings.database}:schema_migrations"
        with connection.cursor() as cursor:
            cursor.execute("SELECT GET_LOCK(%s, %s)", (lock_name, settings.lock_timeout_seconds))
            if cursor.fetchone()[0] != 1:
                raise RuntimeError(f"could not acquire MySQL migration lock {lock_name}")
            try:
                for path in migration_files(settings.migrations_dir):
                    version = path.stem
                    digest = checksum(path)
                    existing = migration_record(cursor, version)
                    if existing:
                        if existing[0] != digest:
                            raise RuntimeError(f"checksum changed for already-applied migration {path.name}")
                        skipped.append(path.name)
                        continue
                    elapsed_ms = execute_migration(cursor, path, settings)
                    cursor.execute(
                        "INSERT INTO schema_migrations(version, filename, checksum_sha256, execution_ms) "
                        "VALUES (%s, %s, %s, %s)",
                        (version, path.name, digest, elapsed_ms),
                    )
                    connection.commit()
                    applied.append(path.name)
            finally:
                cursor.execute("SELECT RELEASE_LOCK(%s)", (lock_name,))
                connection.commit()
    return {
        "database": settings.database,
        "migrations_dir": str(settings.migrations_dir),
        "applied": applied,
        "skipped": skipped,
    }


def main() -> int:
    result = apply_migrations(parse_args())
    print(json.dumps(result, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
