# 同学A — 邬至涛 — 数据管道与基础设施 环境要求

> **职责范围：** Docker 容器编排、MySQL 数据库建表与种子数据、Redis 队列配置、Python ETL 脚本（数据导入 + 清洗入库）、Nginx 反向代理、采集管理模块（少量 Spring Boot）。

---

## 一、全局通用环境

| 工具 | 版本要求 | 用途 | 验证命令 |
|------|----------|------|----------|
| **Git** | >= 2.30 | 版本控制 | `git --version` |
| **Docker** | >= 24.0 | 容器运行时 | `docker --version` |
| **Docker Compose** | >= v2.20 | 多容器编排 | `docker compose version` |
| **Postman** 或 **Apifox** | 最新版 | 接口调试 | 启动后能发送请求即可 |
| **VS Code** | 最新版 | 通用编辑器 | — |
| **Navicat** 或 **DBeaver** | 任意 | 数据库可视化管理 | 能连接 `localhost:3306` 即可 |
| **Redis Insight** 或 `redis-cli` | 任意 | Redis 队列监控 | 能连接 `localhost:6379` 即可 |

### 项目仓库克隆

```bash
git clone <仓库地址>
cd 实训项目
```

### Docker 环境验证

```bash
docker run --rm hello-world
```

---

## 二、专属基础环境

| 工具 | 版本要求 | 用途 | 验证命令 |
|------|----------|------|----------|
| **Python** | >= 3.10 | ETL 脚本运行 | `python --version` |
| **pip** | >= 23.0 | Python 包管理 | `pip --version` |
| **JDK** | >= 11（建议 17） | Spring Boot 采集管理模块（少量 Java） | `java --version` |
| **Maven** | >= 3.8 | 采集模块构建 | `mvn --version` |

---

## 三、Python 依赖包

在 `data-pipeline/` 目录下创建 `requirements.txt`：

```
redis>=5.0
pandas>=2.0
openpyxl>=3.1
pymysql>=1.1
```

安装与验证：

```bash
cd data-pipeline
pip install -r requirements.txt
python -c "import redis; import pandas; import pymysql; print('OK')"
```

---

## 四、技术栈总览

### 4.1 基础设施（亲自搭建）

| 组件 | 版本 | 部署方式 | 用途 |
|------|------|---------|------|
| **MySQL** | 8.0 | Docker 容器 | 业务数据 + 采集配置持久化 |
| **Redis** | 7 | Docker 容器（alpine） | List 消息队列 + 缓存 |
| **Nginx** | latest | Docker 容器（alpine） | 前端 SPA + API 反向代理 |

### 4.2 数据处理

| 层面 | 技术 | 用途 |
|------|------|------|
| 脚本语言 | Python 3.10+ | CSV 导入、ETL 清洗、技能提取 |
| 消息队列 | Redis 7 List（LPUSH / BRPOP） | `queue:raw-job-data` → `queue:cleaned-job-data` |
| 数据持久化 | PyMySQL → MySQL 8.0 | 清洗后数据写入 job_position / job_company |

### 4.3 采集管理（少量 Java）

| 层面 | 技术 | 用途 |
|------|------|------|
| 应用框架 | Spring Boot 2.7+ | 采集任务管理 |
| 持久层 | Spring Data JPA | collect_source / collect_task / collect_log 表 ORM |
| 容器化 | Docker + Docker Compose | 5 容器一键部署（mysql / redis / backend / frontend / python-etl） |

---

## 五、开发工具

| 工具 | 用途 |
|------|------|
| **PyCharm** 或 **VS Code** | Python 脚本开发 |
| **IntelliJ IDEA** | Spring Boot 采集管理模块 |
| **Navicat / DBeaver** | 建表验证、数据抽查 |
| **Redis Insight** 或 `redis-cli` | Redis List 队列长度监控 |
| **Docker Desktop** | 容器管理与日志查看 |

---

## 六、数据管道目录结构

```
data-pipeline/
├── requirements.txt            # redis, pandas, openpyxl, pymysql
├── config.py                   # Redis/MySQL 连接配置（从环境变量读取）
├── import_data.py              # CSV 导入脚本（读取 CSV → 标准化 JSON → LPUSH Redis）
├── etl_clean.py                # ETL 清洗脚本（常驻进程，BRPOP → 清洗 → 写入 MySQL）
├── skill_dict.json             # 500+ 技术关键词词典
└── city_mapping.json           # 城市 → 省份 → 层级映射表（30+ 城市）
```

---

## 七、环境验证清单

- [ ] `python --version` ≥ 3.10，`pip --version` 正常
- [ ] `java --version` 输出 11 或 17，`mvn --version` 正常
- [ ] `docker compose up mysql redis` 两个容器能 healthy 启动
- [ ] Navicat 能连上 `localhost:3306`，库 `career_ability` 存在
- [ ] `redis-cli -h localhost -p 6379 PING` 返回 `PONG`
- [ ] `pip install -r data-pipeline/requirements.txt` 全部成功
- [ ] `python import_data.py` 能将 >= 500 条样本数据推入 Redis List
- [ ] `redis-cli LLEN queue:raw-job-data` 返回值 >= 500
- [ ] ETL 常驻进程启动后，`SELECT COUNT(*) FROM job_position` 返回值 >= 400
- [ ] `docker compose up` 一键启动后 5 个容器 STATUS 均为 Up (healthy)
- [ ] 浏览器访问 `http://localhost` 能看到前端页面（等 B 同学部署后验证）
