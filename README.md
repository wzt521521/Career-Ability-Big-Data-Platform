# 邬至涛 + 张博源 + 黄健熙 + 王致远

## 分工协作规则

1. **分支开发：** 每位成员必须从 `main` 分支创建自己的功能分支（命名规范：`feature/<模块名>-<姓名缩写>`），禁止直接在 `main` 上提交代码。
2. **拉取最新：** 开发前先 `git pull` 拉取远程最新代码，避免冲突。
3. **提交规范：** Commit message 遵循约定式提交（Conventional Commits），格式：`<type>(<scope>): <描述>`，如 `feat(report): 新增PDF报告生成接口`。
4. **推送与合并：** 功能完成后推送到远程分支，提交 Pull Request 由组内 Code Review 后再合并到 `main`。
5. **及时同步：** 每天收工前将当天代码推到远程，日报进度到协作群，确保进度透明。

> 严格遵循上述 Git 协作流程，养成良好的团队开发习惯。

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
# 1. 启动基础服务
docker-compose up -d mysql redis

# 2. 初始化数据库
# 执行 sql/init.sql 建表

# 3. 导入公开数据集
cd data-pipeline && python import_data.py --source ../data/sample_jobs.csv

# 4. 运行 ETL 清洗
python etl_clean.py

# 5. 启动后端
cd backend && mvn spring-boot:run

# 6. 启动前端
cd frontend && npm install && npm run dev
```

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
