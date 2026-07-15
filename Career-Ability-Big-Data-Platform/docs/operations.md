# 运维与部署手册

本文描述 `v1.0.0` 发行范围内真实可运行的五服务部署：MySQL、Redis、Spring Boot 后端、Vue/Nginx 前端、Python ETL worker。

## 1. 环境准备

- Docker Engine 与 Docker Compose v2。
- 能访问镜像仓库与 GitHub Actions 所需网络。
- 生产环境必须准备 `.env`，不要提交 `.env`。

最小必填变量：

```env
MYSQL_ROOT_PASSWORD=<random-root-password>
MYSQL_APP_USER=career_app
MYSQL_APP_PASSWORD=<random-app-password>
MYSQL_MIGRATOR_USER=career_migrator
MYSQL_MIGRATOR_PASSWORD=<random-migrator-password>
REDIS_PASSWORD=<random-redis-password>
JWT_SECRET=<at-least-32-random-bytes>
CORS_ALLOWED_ORIGINS=https://your-console.example
INITIAL_ADMIN_USERNAME=<set-only-for-first-empty-bootstrap>
INITIAL_ADMIN_PASSWORD=<set-only-for-first-empty-bootstrap>
```

`INITIAL_ADMIN_PASSWORD` 只在空库首次引导时使用。管理员创建完成后删除该变量并重启后端。

## 2. 空库部署

```bash
cp .env.example .env
docker compose up -d --build --wait
docker compose exec -T python-etl python scripts/verify_compose_pipeline.py \
  --csv /data/kaggle_jobs_500.csv --timeout-seconds 180
python scripts/verify_compose_release.py --base-url http://127.0.0.1:8080
```

空 MySQL 卷首次启动时，MySQL entrypoint 执行 `sql/init.sql` 创建完整结构和开发种子数据；之后 `db-migrate` 服务创建 `schema_migrations` 并执行 `sql/migrations/*.sql` 的幂等升级脚本。

## 3. 已有库升级

`sql/init.sql` 会重建业务表，只能用于空卷。已有库升级必须通过 `db-migrate`：

```bash
docker compose up -d mysql
docker compose run --rm db-migrate
docker compose up -d --build --wait
```

迁移规则：

- `schema_migrations` 记录版本、文件名、SHA-256 和执行耗时。
- 已执行脚本 checksum 改变时中止。
- 迁移脚本禁止 `DROP TABLE`。
- 应用运行账号只授予业务读写权限，不使用 MySQL root。

## 4. 开发端口

生产 `docker-compose.yml` 不暴露 MySQL/Redis 到宿主机；如需本机调试：

```bash
docker compose -f docker-compose.yml -f docker-compose.dev.yml up -d mysql redis
```

默认开发映射：

- MySQL: `127.0.0.1:3307`
- Redis: `127.0.0.1:6379`

## 5. 备份与恢复

创建备份：

```bash
python scripts/compose_backup.py create \
  --compose-file docker-compose.yml \
  --directory backups/$(date +%Y%m%d-%H%M%S)
```

恢复备份：

```bash
python scripts/compose_backup.py restore \
  --compose-file docker-compose.yml \
  --directory backups/<backup-id>
```

备份内容：

- `database.sql`：MySQL 逻辑备份。
- `reports.tar`：报告持久化卷。
- `manifest.json`：文件大小与 SHA-256 校验。

CI 使用 `scripts/verify_compose_backup_restore.py` 验证恢复后用户、岗位、报告、权限、API Key 统计和 `schema_migrations` 计数一致，并验证备份后写入的临时变更会被恢复流程移除。

## 6. 健康检查与可观测性

- 后端 readiness: `/actuator/health/readiness`
- 后端 liveness: `/actuator/health/liveness`
- 后端 metrics: `/actuator/metrics`
- 报告存储健康项：`reportStorage`
- 结构化日志包含 `requestId`、HTTP 方法、路径、状态码和耗时。
- 异步任务指标：
  - `career.report.generation.failures`
  - `career.collection.task.failures{reason=...}`

入口代理负责 TLS 终止，并向后端传递 `X-Forwarded-Proto`、`X-Forwarded-Host` 和 `X-Forwarded-For`。

## 7. 故障处理

- 后端启动失败且提示 JWT：检查 `JWT_SECRET` 是否为空、过短或仍为开发默认值。
- 后端数据库连接失败：确认 `db-migrate` 已成功完成，且 `MYSQL_APP_PASSWORD` 与 `.env` 一致。
- Redis 连接失败：确认 `REDIS_PASSWORD` 已设置且服务健康。
- 报告生成失败：检查 `report-data` 卷、`REPORT_OUTPUT_DIR`、Noto Sans SC 字体和 `/actuator/metrics/career.report.generation.failures`。
- ETL 无心跳：查看 `python-etl` 日志和 Redis raw queue；worker 会恢复 processing queue 中未确认消息。

## 8. 卸载与清理

仅清理容器：

```bash
docker compose down --remove-orphans
```

清理容器和所有数据卷：

```bash
docker compose down -v --remove-orphans
```

执行 `down -v` 前必须确认已有可恢复备份。
