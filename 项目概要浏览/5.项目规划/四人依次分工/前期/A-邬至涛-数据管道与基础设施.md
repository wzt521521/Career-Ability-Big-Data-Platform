# A（邬至涛）前期开工计划 — 数据管道与基础设施

> 你是整条链路的起点。前期一共 7 天，你的活最重、最关键。下面按天拆开，每天有明确的交付物。

---

## 第1天：建表 + Docker 跑通 MySQL 和 Redis

**上午：写 SQL**

- 照着前期规划文档第三节的表结构，写出完整的 `sql/init.sql`
- 包含：17 张表的 DDL + 5 个角色的种子数据 + 城市层级映射种子数据
- 放到 `sql/init.sql`

**下午：Docker Compose**

- 写 `docker-compose.yml`，先只跑 MySQL 和 Redis 两个容器
- 验证：`docker compose up -d` 后，用 `docker exec` 进去确认表都建好了、Redis 能 ping 通
- 这一步完成，你就**解除了 BCD 三人的第一个阻塞点**

**今天的交付物：**
- `sql/init.sql` 可执行、表全部建成功
- MySQL 容器 healthy，Redis 容器 healthy
- **B、C、D 明天可以开工写 JPA 实体了**

---

## 第2天：Redis 就绪 + Python 导入脚本

**上午：Redis 配置**

- 写 `redis/redis.conf`（持久化 + 内存策略）
- 确认两个 List Key 可供读写：`queue:raw-job-data`、`queue:cleaned-job-data`
- 配合 Docker Compose 挂载配置文件

**下午：Python 导入脚本**

- 建 `data-pipeline/` 目录，写 `config.py` + `import_data.py` + `requirements.txt`
- 逻辑不复杂：pandas 读 CSV → 每行转 JSON → `LPUSH` 进 Redis
- 先用一个几十条的小 CSV 测通
- 写 `city_mapping.json`（30+ 城市）和 `skill_dict.json`（500+ 技能词典）的初版

**今天的交付物：**
- Redis 配置生效，两个队列 Key 就绪
- `import_data.py` 能跑通（小规模数据验证）
- 词典文件初版完成

---

## 第3天：ETL 清洗脚本

**全天：etl_clean.py**

这是你前期最复杂的活。对照前期规划文档 7.3 节逐项实现：

1. `BRPOP` 阻塞消费 `queue:raw-job-data`
2. 空值校验（title/company.name 为空则丢弃）
3. 薪资标准化（`8K-15K` 正则提取、年薪转月薪、面议置零）
4. 城市标准化（查 city_mapping.json 补省份和层级）
5. 学历/经验标准化（模糊匹配到枚举值）
6. 技能提取（skill_dict.json 在 title+description 中匹配）
7. MD5 去重（jobId + sourceUrl）
8. 写 MySQL（先 INSERT company，拿到 company_id 再 INSERT position）

**验证方式：** 用小 CSV 导入 → 查 Redis List → ETL 消费 → 查 MySQL 确认数据入表

**今天的交付物：**
- `etl_clean.py` 完整可运行
- 小规模数据从 CSV 到 MySQL 全链路跑通
- **这是你前期的核心里程碑**

---

## 第4天：Spring Boot 采集模块 + Nginx

**上午：采集管理后端**

- 在 `backend/module-collect/` 下建实体类（CollectSource / CollectTask / CollectLog）
- 写 Controller + Service + Repository 的基础 CRUD
- 暂时不做定时调度和重试（那是中期的活），前期只需数据源配置和任务的基础接口

**下午：Nginx 配置 + 前端容器化**

- 写 `nginx/nginx.conf`（前端 SPA + `/api/` 代理 + Gzip）
- 写 Spring Boot 的 Dockerfile（多阶段构建）
- 写 Vue 前端的 Dockerfile（多阶段构建）
- 更新 `docker-compose.yml` 加入 backend、frontend、python-etl 三个容器

**今天的交付物：**
- 采集模块后端骨架可启动、接口可调
- 5 个容器的 Compose 文件完整
- `docker compose up` 全栈启动成功

---

## 第5天：采集管理前端 + Logback 日志

**上午：采集管理 Vue 页面**

- 在 B 已搭好的前端骨架基础上，写采集管理的两个页面：
  - 任务状态面板（表格：任务名/状态/上次运行/下次运行/操作按钮）
  - 监控图表（简单 ECharts：日采集量折线图、成功率饼图）
- 对接自己的后端接口

**下午：日志 + 收尾**

- 配置 Logback 结构化日志（JSON 格式输出）
- 写 Python ETL 的 Dockerfile（`python:3.10-slim` + pip install + CMD）
- 整理目录结构，确认所有文件到位

**今天的交付物：**
- 采集管理前端页面可交互
- 日志配置生效
- 所有容器化文件就绪

---

## 第6天：准备样本数据集

**全天：找数据 + 验证质量**

这是决定项目成败的关键一步：

1. 去 Kaggle 搜索 "job posting dataset" 或 "data scientist jobs"，挑一个字段齐全的
2. 至少 500 条，最好覆盖多个城市和岗位类型
3. 关键字段不能缺：岗位名、公司名、薪资（可以是范围或文本）、城市、学历、经验
4. 用 `import_data.py` 跑一遍 → ETL 清洗 → 查 MySQL 看字段正确率
5. 人工抽查 50 条，重点看：技能提取是否合理、薪资解析是否离谱、城市标准化对不对
6. 根据抽查结果调整 `skill_dict.json` 和 `city_mapping.json`

**今天的交付物：**
- 一份 >= 500 条的样本数据集确认可用
- 词典文件根据实际数据优化完毕
- MySQL `job_position` 表中有 >= 400 条清洗后数据

---

## 第7天：全链路联调 + 验收

**全天：配合 BCD 联调**

对照前期验收清单的 12 项，逐条过：

1. MySQL 17 张表 → `docker exec` 验证
2. Redis List 数据量 → `LLEN` >= 500
3. ETL 入库量 → `SELECT COUNT(*)` >= 400
4. 5 容器 healthy → `docker ps`
5-7. B 的注册/登录/Token 接口 → Postman
8-9. C 的岗位查询接口 → Postman
10. D 的画像接口 → Postman
11-12. B 的前端登录流程 → 浏览器实测

**今天的交付物：12 项验收全部通过 = 前期完成。**

---

## 你与其他人的协作节点

```
第1天结束 ──→ BCD 拿到建表脚本，开始写 JPA 实体
第2天结束 ──→ C 测通岗位查询（用你导入的小规模数据）
第4天结束 ──→ 全员统一用 Docker 开发
第6天结束 ──→ 样本数据就位，C 的接口有真实数据可查
第7天     ──→ 四人全链路联调
```

**关键原则：每完成一天的工作，立刻在群里同步交付物，不要攒到最后。你的每一步都是别人开工的前提。**
