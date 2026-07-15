# v1.0.0 发行测试报告

> 本文件记录本分支当前具备的验收项目。最终 RC 和正式 Release 必须以 GitHub Actions 产物和提交 SHA 更新结果。

## 1. 环境

- 分支：`feat/release-integration-hardening`
- 目标基线：`release/1.0.0-integration`
- Java：17
- Node.js：20
- Python：3.10
- MySQL：8.0.40
- Redis：7.4.2-alpine
- Docker Compose：GitHub Actions runner 提供

## 2. 自动化门禁

| 门禁 | 命令或 CI job | 目标 |
| --- | --- | --- |
| 后端 | `backend` job / `./mvnw -B verify` | 编译、单测、JaCoCo 推荐服务门槛 |
| 前端 | `frontend` job | `npm ci`、coverage、lint、build |
| 数据管道 | `data-pipeline` job | 无外部副作用单测与覆盖率 |
| MySQL/Redis 集成 | `integration` job | 专用库和 Redis DB 的集成测试、MySQL 查询计划/P95 |
| Compose 全链路 | `compose` job | 五服务健康、ETL、HTTP E2E、报告、备份恢复、性能、浏览器截图、OpenAPI |
| 安全 | `security` job | secrets、依赖审计、SBOM、许可证、镜像扫描 |

## 2.1 当前本机验证记录

执行时间：2026-07-15。

| 项目 | 结果 |
| --- | --- |
| 后端 | `.\mvnw.cmd -B verify` 通过；134 tests，0 failures，0 errors，2 skipped；JaCoCo check 通过 |
| 前端 | `npm ci`、`npm run test:coverage`、`npm run lint`、`npm run build` 通过；54 tests |
| 数据管道 | `python -m pytest -m "not integration"` 通过；28 passed，2 deselected；coverage gate 通过 |
| Python 发行脚本 | `py_compile` 通过 |
| YAML | `.github/workflows/ci.yml`、`docker-compose.yml`、`docker-compose.dev.yml` 解析通过 |
| secrets | `python scripts/scan_secrets.py --include-history` 通过，无高置信 secrets |
| 空白 | `git diff --check` 通过 |
| Compose | 本机无 Docker CLI，真实起栈、镜像、浏览器截图与备份恢复以 GitHub Actions `compose`/`security` job 为权威 |

## 3. Compose 验收脚本

| 脚本 | 覆盖点 |
| --- | --- |
| `data-pipeline/scripts/verify_compose_pipeline.py` | CSV -> Redis raw queue -> ETL -> MySQL -> cleaned queue |
| `scripts/verify_compose_release.py` | 五角色、权限负向、画像、推荐 TOP20、报告、API Key 双认证/限流/owner 绑定、禁用用户即时失效 |
| `scripts/verify_compose_reports.py` | Noto Sans SC、中文 PDF 文本抽取、报告卷持久化、后端重建后 SHA 一致 |
| `scripts/verify_compose_backup_restore.py` | 数据库和报告卷备份、manifest hash、恢复一致性 |
| `scripts/verify_compose_performance.py` | 岗位分页、统计、推荐冷/热缓存、报告生成性能 |
| `scripts/verify_compose_browser.py` | 1440/768/390 三视口真实浏览器截图、控制台错误和失败请求检查 |

## 4. 当前限制

- 本机环境无 Docker daemon 时，Compose、镜像和浏览器验收以 GitHub Actions 结果为权威。
- Word/Excel 报告导出不属于 `v1.0.0` 范围，已进入 `v1.1` backlog。
- Spring Boot 2.7.18 保留在 `v1.0.0`，升级路线见 `SECURITY.md`。

## 5. RC 更新要求

创建 `v1.0.0-rc.N` 后，必须补充：

- RC tag 与提交 SHA。
- 所有 CI job 的通过链接。
- `compose` job 上传的 OpenAPI JSON、截图和日志。
- `security` job 上传的 SBOM、许可证清单和扫描结果。
- 性能脚本输出 JSON。
- 已知限制与回滚记录。
