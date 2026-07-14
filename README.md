# 邬至涛 + 张博源 + 黄健熙 + 王致远

## 分工协作规则

1. **分支开发：** 每位成员必须从 `main` 分支创建自己的功能分支（命名规范：`feature/<模块名>-<姓名缩写>`），禁止直接在 `main` 上提交代码。
2. **拉取最新：** 开发前先 `git pull` 拉取远程最新代码，避免冲突。
3. **提交规范：** Commit message 遵循约定式提交（Conventional Commits），格式：`<type>(<scope>): <描述>`，如 `feat(report): 新增PDF报告生成接口`。
4. **推送与合并：** 功能完成后推送到远程分支，提交 Pull Request 由组内 Code Review 后再合并到 `main`。
5. **及时同步：** 每天收工前将当天代码推到远程，日报进度到协作群，确保进度透明。

> 严格遵循上述 Git 协作流程，养成良好的团队开发习惯。

## R1 本机质量门禁

实际运行工程位于 `Career-Ability-Big-Data-Platform/`。在提交前从该目录依次执行：

```powershell
cd Career-Ability-Big-Data-Platform/backend
.\mvnw.cmd -B clean verify

cd ../frontend
npm ci
npm run test:coverage
npm run lint
npm run build

cd ../data-pipeline
py -3.10 -m venv .venv
.\.venv\Scripts\python.exe -m pip install --upgrade pip
.\.venv\Scripts\python.exe -m pip install -r requirements-test.txt
.\.venv\Scripts\python.exe -m pytest
.\.venv\Scripts\python.exe scripts/check_coverage.py coverage.xml

cd ..
docker compose config --quiet
```

`pytest` 默认只运行无外部副作用的单元测试。MySQL 8 和 Redis 7 集成测试仅在专用测试库、临时 Redis key 和显式环境变量齐备时运行：

```powershell
$env:PIPELINE_TEST_MYSQL_DATABASE = "career_ability_pipeline_test"
$env:MYSQL_HOST = "127.0.0.1"
$env:MYSQL_PORT = "3306"
$env:MYSQL_USER = "<dedicated-test-user-with-create-database>"
$env:MYSQL_PASSWORD = "<dedicated-test-password>"
$env:REDIS_HOST = "127.0.0.1"
$env:REDIS_PORT = "6379"
$env:REDIS_DB = "0"
$env:PIPELINE_TEST_REDIS_DB = "15"
$env:PIPELINE_TEST_REDIS_PREFIX = "pipeline:test"
cd Career-Ability-Big-Data-Platform/data-pipeline
.\.venv\Scripts\python.exe -m pytest -m integration
```

提交前还必须执行 `git diff --check`，确认 `git status --short` 只包含本次任务文件，并通过 PR 合并；不得绕过受保护分支。

# 职业能力大数据服务平台

---

## 项目简介

本项目是一个面向高校的**就业数据分析与岗位推荐平台**，利用公开招聘数据，对岗位数量、薪资水平、技能需求、学历要求、地域分布等维度进行统计分析，自动生成就业分析报告，并为学生提供个性化岗位推荐。

平台核心功能模块：
- 数据采集与清洗模块
- 多维度就业数据分析模块
- 报告自动生成模块（PDF / Word / Excel）
- 个性化岗位推荐模块
- 对外开放 API

系统核心价值：分析职位数据，为学校教学改革、学生求职规划提供数据参考。

---

## 技术栈

### 后端
- **语言：** Java
- **框架：** Spring Boot 2.7+
- **安全：** Spring Security + JWT（RBAC 权限模型）
- **数据库：** MySQL 8.0
- **缓存：** Redis 7

### 前端
- **语言：** JavaScript
- **框架：** Vue 3 + Element Plus
- **可视化：** ECharts

### 数据处理（Python）
- 公开数据集导入与标准化
- ETL 数据清洗脚本
- 技能关键词提取

### 进阶选做（大数据组件）
- **消息队列：** Kafka（基础方案用 Redis List 替代）
- **分布式存储：** HDFS
- **离线计算：** Spark SQL
- **数据仓库：** Hive

---

## 开发环境

| 类别 | 工具 |
|------|------|
| 后端 IDE | IntelliJ IDEA |
| 前端 IDE | VS Code / HBuilderX |
| 数据库工具 | Navicat / SQLyog |
| 接口测试 | Postman / Apifox |
| 版本控制 | Git |
| 部署 | Docker + Docker Compose |

---

## 数据库

- MySQL 8.0（核心业务数据库）
- Redis 7（缓存 + 轻量消息队列）

---

## 部署环境

- Windows / Linux
- Docker + Docker Compose（一键启动）

---

## 快速开始

### 基础方案（5 个容器，推荐实训使用）

```bash
# 1. 进入实际运行工程并配置 .env
cd Career-Ability-Big-Data-Platform

# 2. 构建并等待 MySQL、Redis、后端、前端和 ETL 全部健康
docker compose up -d --build --wait

# 3. 导入并验证仓库 CSV 样本的端到端链路
docker compose exec -T python-etl python scripts/verify_compose_pipeline.py --csv /data/kaggle_jobs_500.csv --timeout-seconds 180
```

`data/kaggle_jobs_500.csv` 以只读卷挂载到 ETL 容器的 `/data`。验证不会截断 Redis 或 MySQL，成功时至少确认 500 条有效源记录，以及 400 条已写入 MySQL 和 cleaned 队列的岗位记录。

### 进阶方案（含大数据组件）

在基础方案之上，额外启动 Kafka、HDFS、Spark 容器，详见 `docker-compose-full.yml`。

---

## 项目结构

```
├── frontend/           # Vue 3 前端
├── backend/            # Spring Boot 后端
├── data-pipeline/      # Python 数据处理脚本
├── sql/                # 数据库初始化脚本
├── data/               # 公开数据集样本
├── docker-compose.yml  # 基础部署编排
└── docs/               # 项目文档
    ├── 需求说明书/
    └── 架构设计文档/
```
