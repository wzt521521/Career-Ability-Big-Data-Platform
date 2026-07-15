# Data Pipeline

The pipeline keeps the ETL worker running continuously and imports source
files through a separate one-shot command.  It is designed to be safe to
restart and safe to replay.

## Compose acceptance flow

With `python-etl` running `python etl_clean.py` and the repository `data/`
directory mounted at `/data` read-only, run the importer in a second process:

```powershell
docker compose exec -T python-etl python import_data.py /data/kaggle_jobs_500.csv
docker compose exec -T python-etl python scripts/verify_compose_pipeline.py --csv /data/kaggle_jobs_500.csv --timeout-seconds 120
```

`kaggle_jobs_500.csv` contains 520 source rows, all with unique source
identities.  The verifier exits non-zero unless it observes at least 500 valid
source rows, 400 persisted MySQL rows, and 400 durable cleaned-queue records.
It does not stop the worker or delete any records.

## Reliability boundaries

- `source_md5 = md5(job_id + "|" + source_url)` is the MySQL uniqueness
  boundary and is also used by Redis dedupe sets.
- Import uses a Redis Lua operation to add a source identity and its raw queue
  message atomically. Re-running the same CSV is idempotent.
- The worker atomically moves messages from `raw` to `processing` before work.
  A batch is acknowledged only after its MySQL transaction and downstream
  queue action complete. Startup returns unacknowledged processing messages to
  `raw`.
- Record-level data errors are sent to `failed`; valid records in the same
  batch still commit. Connection and transaction failures leave the batch for
  retry.
- The worker writes an ISO-8601 timestamp to `ETL_HEARTBEAT_FILE` (default
  `/tmp/etl-heartbeat`) while idle and while processing. A container health
  check can reject a stale file without changing worker behavior.

`REDIS_KEY_PREFIX` namespaces all six pipeline keys. Production defaults retain
the existing queue names. Each test run uses a different explicit namespace.

## Isolated integration test

The default test command is offline and never opens Redis or MySQL. Service
tests are explicit and require a separate database plus a different Redis DB:

```powershell
$env:PIPELINE_TEST_MYSQL_DATABASE = "career_ability_pipeline_test"
$env:PIPELINE_TEST_REDIS_DB = "15"
$env:PIPELINE_TEST_REDIS_PREFIX = "pipeline:test"
py -3 -m pytest -m integration
```

The integration suite rejects a database equal to `MYSQL_DATABASE` or a Redis
DB equal to `REDIS_DB`. It creates only missing test tables, uses a UUID key
prefix per run, and deletes only those Redis keys plus rows/companies carrying
that run's UUID. It never runs `TRUNCATE`, `FLUSHDB`, or `sql/init.sql`.
