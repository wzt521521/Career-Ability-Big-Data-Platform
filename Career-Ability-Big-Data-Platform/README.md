# 职业能力大数据服务平台 - 运行说明

本目录是实际运行工程，包含数据采集与清洗、岗位分析、认证与 RBAC、系统管理、开放 API 和前端控制台。

## 1. 环境要求

- JDK 17 与 Maven 3.8+
- Node.js 20 或更高 LTS 版本
- MySQL 8.0
- Redis 7
- Docker Compose（容器部署时需要）

## 2. 环境变量

复制环境变量模板：

```powershell
Copy-Item .env.example .env
```

`.env` 只用于本地运行，不得提交。生产环境必须覆盖 `MYSQL_ROOT_PASSWORD` 和 `JWT_SECRET`；JWT 密钥至少为 32 字节随机字符串。仓库中的默认值仅用于本地开发。

## 3. 本地启动

先启动 MySQL 和 Redis：

```powershell
docker compose up -d mysql redis
```

构建并启动后端：

```powershell
cd backend
$env:SPRING_DATASOURCE_URL = "jdbc:mysql://localhost:3307/career_ability?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai"
$env:SPRING_DATASOURCE_USERNAME = "root"
$env:SPRING_DATASOURCE_PASSWORD = "local-development-password"
$env:JWT_SECRET = "replace-with-at-least-32-random-bytes"
mvn -B clean package
mvn spring-boot:run
```

另开终端启动前端：

```powershell
cd frontend
npm ci
npm run dev
```

访问地址：

| 服务 | 地址 |
| --- | --- |
| 前端开发服务器 | `http://127.0.0.1:5173` |
| 后端 | `http://localhost:8080` |
| Swagger UI | `http://localhost:8080/swagger-ui/index.html` |
| 健康检查 | `http://localhost:8080/actuator/health` |

初始化管理员为 `admin`，本地开发初始密码为 `admin123`。首次登录后应立即修改密码，生产环境不得继续使用该初始凭据。

## 4. Docker 启动

完成 `.env` 配置后启动完整基础环境：

```powershell
docker compose up -d --build --wait
```

仓库样本 `data/kaggle_jobs_500.csv` 包含 520 条源记录，并以只读卷挂载到
`python-etl` 容器的 `/data`。在 ETL worker 持续运行时，使用下列命令导入并验证
CSV -> Redis raw queue -> ETL -> MySQL -> cleaned queue 链路：

```powershell
docker compose exec -T python-etl python scripts/verify_compose_pipeline.py --csv /data/kaggle_jobs_500.csv --timeout-seconds 180
```

该验证不清空 Redis 或 MySQL 数据，只导入并检查该样本对应的幂等记录；成功结果至少包含
500 条有效源记录、400 条 MySQL 岗位记录和 400 条 cleaned 去重记录。

Compose 保留 `mysql`、`redis`、`backend`、`frontend` 和 `python-etl` 服务。前端地址为 `http://localhost`，MySQL 映射到宿主机 `3307` 端口。

`sql/init.sql` 只会在空 MySQL 数据卷首次创建时执行，也会重建全部表，不能用于已有数据库升级。已有数据库部署本版本时执行：

```powershell
mysql -h localhost -P 3307 -u root -p career_ability < sql/migrations/20260711_hjx_auth_upgrade.sql
mysql -h localhost -P 3307 -u root -p career_ability < sql/migrations/20260714_pipeline_source_md5_unique.sql
```

## 5. 本机质量门禁

```powershell
cd backend
.\mvnw.cmd -B clean verify

cd ..\frontend
npm ci
npm run test:coverage
npm run lint
npm run build

cd ..\data-pipeline
py -3.10 -m venv .venv
.\.venv\Scripts\python.exe -m pip install --upgrade pip
.\.venv\Scripts\python.exe -m pip install -r requirements-test.txt
.\.venv\Scripts\python.exe -m pytest
.\.venv\Scripts\python.exe scripts/check_coverage.py coverage.xml

cd ..
docker compose config --quiet
```

默认 Python 测试不会连接或清理 Redis/MySQL。`pytest -m integration` 仅在提供专用
`PIPELINE_TEST_MYSQL_DATABASE`、有建库权限的测试账号及 Redis 连接参数时执行；测试只创建最小
测试表、清理其 UUID Redis key 和自身生成的数据，绝不运行 `TRUNCATE` 或 `sql/init.sql`。

当前覆盖率质量门槛：推荐服务行 75% / 分支 60%，前端全局 statements/lines 30%、functions 35%、
branches 65%，推荐页行 75% / 分支 70%，报告页行 70% / 分支 50%，数据管道行/分支均为 50%。

提交前运行上述命令和 `git diff --check`，确认 `git status --short` 只包含本次任务文件；所有变更均须经
PR 进入受保护分支。

Postman 集合位于 `docs/postman/黄健熙-认证与开放API.postman_collection.json`。

## 6. 模块结构

```text
backend/src/main/java/com/career/platform/
├── auth/               JWT、用户、角色、权限和操作审计
├── openapi/            API Key、双认证、限流与调用统计
├── common/             统一响应、异常、安全上下文和数据范围契约
├── analytics/          岗位与多维统计分析
└── collection/         数据采集管理

frontend/src/
├── api/                接口访问层
├── stores/             登录用户状态
├── utils/              Token、权限和 Axios 公共逻辑
├── views/auth/         登录、注册和个人资料
├── views/system/       用户、角色和操作日志
├── views/open-api/     API Key、调用统计和 Swagger
└── views/              数据大屏、岗位、采集等业务页面

data-pipeline/          Python 数据导入、清洗与技能提取
sql/init.sql            仅用于空数据库的全量结构与开发种子数据
sql/migrations/         已有数据库的非破坏性升级脚本
```

详细接入方式和验收边界见 `docs/黄健熙模块接入与验收.md`。
