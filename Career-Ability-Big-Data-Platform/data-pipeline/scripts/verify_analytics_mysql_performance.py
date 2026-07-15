"""C-05 MySQL 8 query-plan and P95 gate for the pageable position search."""

from __future__ import annotations

import math
import os
import time

import pymysql


ROW_COUNT = 10_000
QUERY_RUNS = 40
P95_LIMIT_SECONDS = float(os.getenv("ANALYTICS_SEARCH_P95_SECONDS", "1.0"))


def connect() -> pymysql.connections.Connection:
    return pymysql.connect(
        host=os.getenv("MYSQL_HOST", "127.0.0.1"),
        port=int(os.getenv("MYSQL_PORT", "3306")),
        user=os.getenv("MYSQL_USER", "root"),
        password=os.getenv("MYSQL_PASSWORD", ""),
        database=os.getenv("MYSQL_DATABASE", "career_ability_test"),
        charset="utf8mb4",
        autocommit=False,
    )


def prepare_schema(cursor: pymysql.cursors.Cursor) -> None:
    cursor.execute("DROP TABLE IF EXISTS job_position")
    cursor.execute("DROP TABLE IF EXISTS job_company")
    cursor.execute(
        """
        CREATE TABLE job_company (
            id BIGINT AUTO_INCREMENT PRIMARY KEY,
            company_name VARCHAR(200) NOT NULL,
            industry VARCHAR(100),
            UNIQUE KEY uq_analytics_perf_company (company_name),
            INDEX idx_jc_industry (industry)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
        """
    )
    cursor.execute(
        """
        CREATE TABLE job_position (
            id BIGINT AUTO_INCREMENT PRIMARY KEY,
            job_id VARCHAR(100) NOT NULL,
            title VARCHAR(200) NOT NULL,
            company_id BIGINT,
            salary_min INT,
            salary_max INT,
            city VARCHAR(50),
            education VARCHAR(20),
            publish_date DATE,
            source_md5 VARCHAR(32),
            UNIQUE KEY uq_analytics_perf_job (job_id),
            UNIQUE KEY uq_analytics_perf_source (source_md5),
            INDEX idx_jp_city_publish_date (city, publish_date, id),
            INDEX idx_jp_education_publish_date (education, publish_date),
            INDEX idx_jp_company_publish_date (company_id, publish_date),
            CONSTRAINT fk_analytics_perf_company FOREIGN KEY (company_id) REFERENCES job_company(id)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
        """
    )
    cursor.execute(
        "INSERT INTO job_company(company_name, industry) VALUES (%s, %s)",
        ("analytics-performance-company", "互联网"),
    )
    company_id = cursor.lastrowid
    rows = [
        (
            f"analytics-perf-{index}",
            "Java工程师" if index % 2 == 0 else "数据工程师",
            company_id,
            10 + index % 20,
            20 + index % 20,
            "上海" if index % 4 == 0 else "杭州",
            "本科" if index % 3 == 0 else "硕士",
            f"2026-{1 + index % 6:02d}-{1 + index % 28:02d}",
            f"{index:032x}",
        )
        for index in range(ROW_COUNT)
    ]
    cursor.executemany(
        """
        INSERT INTO job_position(
            job_id, title, company_id, salary_min, salary_max, city, education, publish_date, source_md5
        ) VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s)
        """,
        rows,
    )


def verify_plan_and_latency(cursor: pymysql.cursors.Cursor) -> float:
    query = (
        "SELECT id FROM job_position "
        "WHERE city = %s ORDER BY publish_date DESC, id DESC LIMIT 20"
    )
    cursor.execute("EXPLAIN FORMAT=JSON " + query, ("上海",))
    explain = cursor.fetchone()[0]
    if "idx_jp_city_publish_date" not in explain:
        raise AssertionError(f"MySQL did not select idx_jp_city_publish_date:\n{explain}")

    cursor.execute(query, ("上海",))
    cursor.fetchall()
    durations = []
    for _ in range(QUERY_RUNS):
        started = time.perf_counter()
        cursor.execute(query, ("上海",))
        cursor.fetchall()
        durations.append(time.perf_counter() - started)
    durations.sort()
    p95 = durations[math.ceil(len(durations) * 0.95) - 1]
    if p95 >= P95_LIMIT_SECONDS:
        raise AssertionError(
            f"MySQL pageable search P95 {p95 * 1000:.2f}ms exceeds {P95_LIMIT_SECONDS * 1000:.0f}ms"
        )
    return p95


def main() -> None:
    connection = connect()
    try:
        with connection.cursor() as cursor:
            prepare_schema(cursor)
            connection.commit()
            p95 = verify_plan_and_latency(cursor)
            print(
                f"MySQL 8 analytics search verified: rows={ROW_COUNT}, "
                f"runs={QUERY_RUNS}, p95_ms={p95 * 1000:.2f}, index=idx_jp_city_publish_date"
            )
    finally:
        connection.close()


if __name__ == "__main__":
    main()
