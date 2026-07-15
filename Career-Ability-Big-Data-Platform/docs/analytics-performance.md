# Analytics Query Performance Baseline

PositionSearchPerformanceTest loads 10,000 representative records into the H2 test database and
measures 25 city-filtered, publish-date-sorted page queries after one warm-up. Its 2,000 ms gate is
a local regression signal only; it is not evidence of a MySQL execution plan. It is opt-in locally:

```powershell
.\mvnw.cmd -B -DincludePerformanceTests=true -Dtest=PositionSearchPerformanceTest test
```

The required MySQL 8 CI gate is data-pipeline/scripts/verify_analytics_mysql_performance.py.
It creates 10,000 isolated test rows, runs EXPLAIN FORMAT=JSON, asserts
idx_jp_city_publish_date, then records 40 pageable search durations and enforces P95 below the
configured 1,000 ms limit. Its representative query is:

```sql
EXPLAIN SELECT id
FROM job_position
WHERE city = '上海'
ORDER BY publish_date DESC, id DESC
LIMIT 20;
```

The MySQL plan must select idx_jp_city_publish_date. The corresponding production indexes are installed by
sql/migrations/20260714_analytics_query_indexes.sql; clean deployments receive the same indexes
from sql/init.sql.

The pageable position list stays database-filtered and database-sorted. Analytics dashboard
responses are cache-backed and their expensive seven-dimension recalculation is persisted by the
daily job, rather than being invoked by each page navigation.
